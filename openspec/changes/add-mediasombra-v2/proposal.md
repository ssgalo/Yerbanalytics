# Change: add-mediasombra-v2

> Rama: `feat/reglas-v2-mediasombra`. Cubre **M-01**, **M-02**, **§7 (plan de rustificación)** y el principio 4
> ("gana cerrar"). Absorbe el §2 pendiente de `add-rustificacion-por-macrozona`. Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas` (pronóstico horario y clima simulado, §2). Crea el `ZoneRuleEngine`.

## Why

La mediasombra del motor no es la de la v2. `ShadingRule` (la única que mueve la malla) hoy:

- trabaja con un **porcentaje de apertura** por sector (`actuadorShade`), cuando la malla real sólo abre o
  cierra del todo y se comanda **por macro-zona**;
- sigue un plan **global** por día de ciclo desde una fecha de siembra de `application.properties`
  (`yerbanalytics.nursery.sowing-date-iso`), con etapas `diaDesde/diaHasta/aperturaPct` de
  `RustificacionEtapaEntity`;
- se protege sólo con el **índice UV** del pronóstico (parámetro `mediasombra.uv-umbral`, fábrica 7), no con
  temperatura ni luz, ni tiene la ventana 11:00–16:00;
- no tiene perfil de crecimiento (08–10), ni cierra de noche, ni el cierre por calor ≥ 35 °C, ni "gana cerrar".

El frontend ya migró al modelo nuevo (`PlanRustificacion` por MZ, `add-rustificacion-por-macrozona` §1), pero el
backend sigue en el viejo: es el "§2 Backend (pendiente)" de ese cambio.

## What Changes

- **M-01 Horario base** por MZ: sin plan ⇒ perfil de crecimiento (abierta 08–10, cerrada el resto y de noche);
  con plan activo o finalizado ⇒ horario de la etapa del día (§7). Se evalúa **en cada cambio de franja**.
- **M-02 Protección por calor y sol extremo:** entre 11:00 y 16:00, cerrar hasta las 16:00 si temperatura ≥ 35 °C,
  o luz ≥ 85 % con ≥ 32 °C, o UV pronosticado ≥ 11 con ≥ 32 °C. "Temperatura" = máx(última lectura, pronóstico de
  la hora). No se reabre antes de las 16:00. Alertas `INFO` / `WARNING` (≥ 35 °C) / `CRITICAL` (≥ 38 °C).
- **Dos estados** (cerrada/abierta). Comando = `shade SET` con `targetPct` 0 o 100: el contrato ya interpreta
  `>= umbral` como abrir (`contrato.h`), así que **el firmware no cambia**.
- **Principio 4:** si dos reglas piden estados distintos para la misma MZ, se cierra.
- **Plan de rustificación por MZ en el backend:** iniciar, cancelar, fin automático (día 46 ⇒ sigue con la etapa
  4 hasta resetear), resetear. Sólo Ingeniero Agrónomo.
- **Horario por defecto** del perfil de crecimiento en la configuración operativa (hoy sólo en el front).
- **Se retira `ShadingRule`** y la tabla `rustificacion_etapa` (script manual de baja).

## Out of scope

- F-S2 (cierre 3 días por estrés solar extendido) → `add-reglas-clorosis-estres-solar`; este cambio deja el
  punto de enganche (`MediasombraPedido`).
- Fallas de la malla (S-05) → `add-fallas-esp32`. Bloqueo de MZ (S-01) → `add-bloqueo-manual`: acá se respeta.
