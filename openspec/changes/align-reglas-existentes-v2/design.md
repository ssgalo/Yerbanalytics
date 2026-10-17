# Design: align-reglas-existentes-v2

Abreviaturas: `be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`; **v2** =
`docs-motor-reglas-e-integracion/reglas_v2.md`.

## Estado verificado (16/10/2026, lectura de código)

| Tema | Hoy | v2 | Veredicto |
|---|---|---|---|
| Parámetros de riego (15 claves) | `be/engine/parametros/ParametrosRiego.java`: fábrica y rangos de §11 | §11 Riego | **Conforme** |
| Confianza mínima | `ParametrosDiagnostico.CONFIANZA_MINIMA`: fábrica **85**, rango 50–100 | 70 %, rango 50–95 | **Difiere** |
| Escalamiento S-02 | `EscalamientoNodoService`: cuenta **pulsos** seguidos sin respuesta (1.º y 2.º `WARNING`, 3.º `CRITICAL`) | Cuenta **intentos** dentro de un mismo ciclo (1/3, 2/3, 3/3) | **Difiere** (deliberado: `add-pulso-sensado-y-pasada-diaria`) |
| Estado seguro al fallar el 3.º | `EscalamientoNodoService.registrarSinRespuesta` sólo alerta y suma; no publica ningún comando | Riego cerrado, bombas detenidas, mediasombra cerrada | **Falta** |
| Reintento con el nodo fuera de servicio | No existe (el siguiente intento es el próximo pulso) | Cada 15 min | **Falta** |
| Parámetros de S-02 en catálogo | `yerbanalytics.pulso.reintento-seg=60`, `intentos=3` en `application.properties`; no editables | §11: espera 10–300 s, reintentos 1–5 / 1–10 min, fuera de servicio 5–60 min | **Difiere** |
| Tope de R-02 | `DeficitCriticoRule` lo aplica **siempre** (`riego.exceptuado-bloqueo`) | Sólo cuando S-06 bloqueó la MZ | **Difiere** (deliberado: D9.2 de `implement-reglas-riego`) |
| `CicloLecturaRiegoRule` | Prioridad 3; su Javadoc la justifica por un nodo que "publica cada 30 s" | No existe | **Sin equivalente** |
| `StaleSensorRule` | Prioridad 1; `seguridad.antiguedad-max-lectura` fábrica 90 s | No existe como regla; S-02 dice "sin lectura válida no corren las reglas de sensores" | **Sin equivalente** (compatible) |

**Verificado después de la primera auditoría:**
- Intervalo de lectura: `SensingCadence` lo acota a **60–360 min** (240 por defecto) = 1–6 h de §11. **Conforme.**
- `CicloLecturaRiegoRule` leída completa: tiene dos guardas con claves propias (cooldown `cycle`, bypass `in-progress`).
  "Ya regó en este ciclo" corta sólo a R-01; con déficit crítico no corta a R-02. En `updateTelemetry` el motor corre
  **solo** con la primera respuesta a un pulso con motor (`pulsos.respondePulso`), por lo que la guarda protege de un
  segundo pulso con motor dentro del mismo ciclo (p. ej. `POST /api/pulsos` manual tras el programado). Su Javadoc
  habla del nodo que "publica cada 30 s": eso es del modelo anterior y hay que corregir el texto, no la lógica.
- El simulador cablea `diagnostico.confianza-minima` = 85 en `ruleConditions.ts` (`DEFAULT_THRESHOLDS`): se corrige en este cambio (tarea 7.3).

## Decisiones

### D-1 — Valores de fábrica nuevos sin pisar lo editado
`diagnostico.confianza-minima` pasa a 70 y su rango a 50–95. La sembrada del catálogo sólo escribe
fábrica cuando **no hay fila**; si ya existe una fila con 85, se respeta (la editó alguien, o es la
sembrada vieja). Para distinguir las dos situaciones, el cambio agrega un script manual
`resources/migracion-confianza-70.sql` (mismo patrón que las otras `migracion-*.sql`), que sólo baja a
70 las filas que valen exactamente 85 **y** nunca fueron editadas por un usuario. Se documenta en el
README del backend, en la lista de migraciones manuales.

### D-2 — Tope de R-02 (DECISIÓN ABIERTA)
La v2 lo acota a "cuando S-06 bloquea". `implement-reglas-riego` lo dejó **siempre** porque, sin S-06,
un sensor roto que marca "seco" regaría 6 L × 6 al día. Opciones:
- (a) **Mantener siempre-activo** y anotar la desviación en la v2 (recomendada hasta que exista S-06).
- (b) Condicionarlo a S-06 — sólo seguro después de `add-efectividad-y-s06`.
Este cambio implementa **(a)** y deja en `add-efectividad-y-s06` la tarea de reevaluar.

### D-3 — `CicloLecturaRiegoRule` (DECISIÓN ABIERTA)
Dos guardas: "riego en curso" (útil siempre: no abrir una válvula abierta) y "ya regó en este ciclo".
Con el modelo de pulso la segunda protege contra un segundo pulso con motor dentro del mismo ciclo (verificado en
`NurseryService.updateTelemetry`: el motor corre con cada primera respuesta a un pulso con motor, y `POST /api/pulsos`
puede lanzar uno manual). **Recomendación: mantenerla** y corregir su Javadoc (modelo de 30 s). Un test fija el escenario
"pulso programado + pulso manual con motor en el mismo ciclo ⇒ un solo riego por sector".

### D-4 — `StaleSensorRule`
Se mantiene. Es la forma concreta de "sin lectura válida no corren las reglas de sensores" (S-02). Se
documenta en su spec que **sólo corta el riego** (`ABORT_RIEGO`): M-01 sigue por horario y M-02 con
pronóstico, como pide S-02. La verificación de que no corte la mediasombra va como test.

### D-5 — Estado seguro (S-02)
Al tercer pulso sin respuesta, `EscalamientoNodoService` publica por `ComandoActuadorPublisher` (el mismo
camino que las secuencias, para que `SeguimientoComandosService` lo vigile): cierre de todas las
válvulas de la MZ, apagado de bombas y mediasombra **cerrada**. Se ejecuta **una vez** al cruzar el
umbral, no en cada pulso siguiente. Cuando el nodo vuelve, **no** se revierte nada (sin acciones
retroactivas); decide el motor con la lectura nueva.

### D-6 — Escalamiento por pulso o por intento (DECISIÓN ABIERTA)
La v2 escala por intento; el sistema, por pulso (3 pulsos = ~8 h con pulsos de 4 h hasta declarar el nodo
caído, contra ~3 min de la v2). Es la desviación más grande de S-02 y fue deliberada. Se propone **mantener
por pulso** y cambiar la v2, porque 3 min sin respuesta no distinguen un nodo caído de un corte de Wi-Fi.
Si el equipo prefiere la v2 literal, es un cambio aparte (toca `PulsoSensadoService`).

### D-7 — Reintento a los 15 min con el nodo caído
El tick de `SensingScheduler` (5 s) agrega, para zonas con `pulsosSinRespuesta ≥ 3`, un `LEER_AHORA` cada
`sensado.reintento-fuera-servicio` min. Si responde, `reiniciar` ya limpia la cuenta y emite el `INFO`.

## Parámetros nuevos del catálogo (familia SEGURIDAD)

| Clave | Fábrica | Rango | Fuente |
|---|---|---|---|
| `sensado.espera-respuesta` | 60 s | 10–300 | §11 Telemetría |
| `sensado.reintentos` | 2 | 1–5 | §11 |
| `sensado.espera-reintento` | 1 min | 1–10 | §11 |
| `sensado.reintento-fuera-servicio` | 15 min | 5–60 | §11 |

`yerbanalytics.pulso.*` pasa a ser el valor inicial de esos parámetros. Mapeo: `intentos` = 1 + `reintentos`.
