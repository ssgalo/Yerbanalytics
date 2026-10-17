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
