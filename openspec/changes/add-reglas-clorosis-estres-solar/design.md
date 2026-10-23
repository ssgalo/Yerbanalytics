# Design: add-reglas-clorosis-estres-solar

## Estado verificado
- El riego para F-S1 puede apoyarse en lo que ya existe: `CalculoRiego`, `SolicitudRiego`, `ColaRiego`, `DespachoRiego`
  y las precondiciones (`PrecondicionesRiego`, R-04 y R-06 incluidas). Verificado: `SolicitudRiego` es un `record` con la regla de origen como `String` (`ActionExecutor.actualizarCola` encola cualquier `ACTIVAR_VALVULA` con su `ruleName()`), así que admite una
  `regla` de origen nueva ("F-S1"). Lo que falta es que `DespachoRiego` consulta overrides y guardas por el nombre de cada regla de riego
  existente (`FueraDeVentanaRiegoRule`, `PausaTrasAplicacionRule`, `CicloLecturaRiegoRule`, `DeficitCriticoRule`): F-S1 debe sumarse (tarea 4.1b).
- La mediasombra por MZ y su `MediasombraPedido` salen de `add-mediasombra-v2`. F-S2 es una regla más que pide CERRADA.
- Luz y temperatura de "las últimas 48 h" requieren historial de lecturas: **no existe** (verificado); se crea en `add-historial-lecturas`, prerrequisito.
- La luz es **% de un LDR**, no radiación (CLAUDE.md §2); los 80 % y 60 % de F-S1/F-S3 son sobre ese LDR.

## Decisiones

### D-1 — Ventanas de 48 h sobre el historial
Se usa `ReadingHistory.max/average(zone, metric, window)` de `add-historial-lecturas`, el mismo servicio de
DPV y CE.

### D-2 — F-C1 decide, F-C2 ejecuta
F-C1 no aplica nada: es una compuerta que registra la causa y marca el diagnóstico como "no nutricional". F-C2 corre
después y se corta si F-C1 aplicó. Se modela como en el riego: F-C1 emite `ABORT_INSUMO` (sector) y F-C2 lo respeta.

### D-3 — "Durante la rustificación"
F-C2 filtra la severidad leve cuando la MZ tiene plan `en_curso` o `finalizado` (de `add-mediasombra-v2`). Si ese
cambio no está integrado, la condición queda en falso y se completa al integrarlo (anotado en tasks).

### D-4 — F-S1 como pedido de riego especial
No es R-01: el disparo es el diagnóstico, no la humedad de la MZ. Se pide un `SolicitudRiego` con regla "F-S1" por el
mismo despacho (cola, 10 simultáneos, revalidación), dentro de la ventana de riego, con volumen de la fórmula. Respeta R-04
(saturado) y R-06 (pausa tras aplicación). Cuenta como riego para E-01.

### D-5 — F-S2
Se evalúa **al llegar la pasada diaria** (cuando hay captura nueva), por MZ. Cuenta sectores que cumplen F-S1 con su
diagnóstico de la última captura; ≥ 10 % de los sectores **con diagnóstico vigente** (no de los 100) *(pregunta abierta: ¿10 % de los
100 o de los diagnosticados? default: de los 100 sectores de la MZ, que es lo que dice la v2)*. Pide CERRADA con
`hasta = hoy + 3 días 16:00`; el calendario del plan **no** se mueve. Un solo aviso mientras la protección siga activa.

### D-6 — F-S3
Condición exacta de la v2: luz < 60 % **y** T < 30 °C durante 48 h. Si la luz/temperatura de F-S1 y F-S3 no cubren todo
el espacio (60–80 % de luz o 30–32 °C), **no actúa ninguna**: es lo que dice la v2 y se deja explícito en un test para que no
se "arregle" sin querer.

### D-7 — Parámetros
| Clave | Fábrica | Rango |
|---|---|---|
| `clorosis.humedad-asfixia` / `…-horas` | 80 % / 48 h | 70–90 / 24–72 |
| `clorosis.ce-salinidad` | 2,0 dS/m | 1,5–3,0 |
| `clorosis.ph-ferrica` | 6,5 | 6,0–7,0 |
| `estres.luz-alta` / `estres.temp-alta` | 80 % / 32 °C | 60–100 / 28–36 |
| `estres.humedad-reposicion` | 55 % | 45–65 |
| `estres.luz-baja` / `estres.temp-baja` | 60 % / 30 °C | 40–80 / 25–32 |
| `estres.pct-sectores-mz` / `estres.dias-proteccion` | 10 % / 3 d | 5–50 / 1–7 |
Restricción cruzada: luz-baja < luz-alta; temp-baja < temp-alta.

## Decisiones tomadas al implementar

Los nombres de clases y métodos de la spec ya estaban en inglés y se mantienen. Todo vive en `chlorosis/` (F-C1, F-C2) y `solarstress/` (F-S1, F-S3), más
`hardening/` para la rustificación.

- **F-S2 no se implementa.** Depende de `add-mediasombra-v2` (cancelado): «la mediasombra de la MZ queda cerrada de 11:00 a 16:00 durante 3 días» necesita quien
  retenga la malla por horario. Hoy sólo `ShadingRule` la mueve, sector por sector, en cada pulso (cada 4 h) por el pico de UV o por el plan global, así que una
  versión parcial cerraría la malla a destiempo (el pulso de las 10 h es antes de las 11 y el de las 14 h la cerraría hasta las 18 h) o la dejaría cerrada. Se necesita un
  dueño de la malla (estado de protección por zona + un planificador horario + que `ShadingRule` lo respete), que es justo lo que ese cambio iba a crear. Quedan sin hacer
  `estres.pct-sectores-mz`, `estres.dias-proteccion`, el escenario `SOLAR_STRESS_WIDE` y las llaves `protection` y `quorum`. **D-5 queda sin objeto.**
- **D-2, F-C1 decide y F-C2 ejecuta**: F-C1 emite `ABORT_INSUMO` + `WARNING`; el detalle `CancelPendingApplication` retira además el pedido de F-C2 que hubiera quedado
  pendiente (el `ActionExecutor` llama a `ApplicationService.cancelPending(sector, insumo, regla)`, que sólo borra si el pedido es de esa regla).
- **D-3, rustificación**: en lugar de dejar la condición en falso se usa el plan **global** (fecha de siembra + etapas de `rustificacion_etapa`, el mismo dato que lee
  `ShadingRule`) detrás de la interfaz `HardeningPhase`; «en curso o finalizado» = desde el primer día de la primera etapa. Sin fecha de siembra, con una ilegible o sin
  etapas no cuenta. Cuando llegue el plan por macro-zona se reemplaza la implementación.
- **Compuerta del diagnóstico compartida**: `engine/DiagnosisGate` + `engine/Severity` (movida desde `biotic/`) para las bióticas, la clorosis y el estrés solar.
- **D-4, F-S1 por el despacho**: la orden entra a la cola con `regla = SolarStressRule`. `DespachoRiego` ya trataba toda solicitud que no es R-02 como riego común (ventana,
  R-06, lluvia, ciclo); se sumó que el bypass `window` de F-S1 también lo salte el despacho. La regla vive en la rama de riego, después de R-01, así los cortes de
  seguridad la dejan sin alcanzar; y comprueba por su cuenta la ventana y la pausa, porque R-05 y R-06 sólo actúan cuando aplica R-01 y F-S1 riega también de 45 a 55 %.
  Si R-01 también pide riego en el mismo pulso, gana la primera orden de la cola (la de R-01).
- **Luz y temperatura (D-1)**: `SolarExposure` es la única consulta al historial; F-S3 mide la luz con la misma franja 10–16 h que F-S1 (la v2 no define otra y así la zona gris
  queda bien definida).
- **F-S3 en la rama de seguimiento**: no ordena nada y su aviso no debe depender de los cortes de riego. En la rama global el grafo de reglas lo dibujaría antes que todas las
  demás (el grafo cuelga cada rama del último nodo global).
- **F-C2 sin cooldown `interval` propio**: el intervalo mínimo lo respeta P-D2 y el cooldown que lo acorta es `interval-fertilizer` de `ApplicationPreconditions`.
- **Las 48 h y la franja 10–16 h son constantes** (la v2 no las hace configurables): sólo `clorosis.humedad-horas` es un parámetro.
- **Bug de paso**: `RuleContext.metricRaw` lanzaba `NullPointerException` ante una métrica que la zona nunca reportó (llega con `raw = null`: una sonda de CE o pH sin
  conectar); ahora la lee como ausente.
