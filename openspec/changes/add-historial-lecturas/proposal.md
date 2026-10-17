# Change: add-historial-lecturas

> Rama: `feat/reglas-v2-historial-lecturas`. **Prerrequisito compartido** de `add-metrica-dpv`, `add-plan-nutricion`,
> `add-reglas-biotico` y `add-reglas-clorosis-estres-solar`. Índice: `reglas-v2-overview/README.md`.

## Why

Casi todas las reglas N/F de la v2 miran el **pasado de las lecturas**, no sólo la última:

| Regla | Pide |
|---|---|
| N-02 | CE media de las últimas 24 h (6 lecturas) |
| F-C1 | humedad de sustrato media de las últimas 48 h |
| F-F1 / F-F2 | DPV < 0,3 kPa en 2 lecturas seguidas (de las últimas 24 h) |
| F-P1 | DPV ≥ 2,0 kPa en alguna lectura de las últimas 72 h |
| F-S1 / F-S3 | luz ≥ 80 % (o < 60 %) y temperatura en las últimas 48 h |

Verificado en el código: **ese historial no existe.** `NurseryService.updateTelemetry` escribe cada métrica en columnas
`*Raw` de `ZonaEntity` (`zona.setHumSusRaw(...)`, etc.), pisando la anterior; solo conserva la última lectura y su hora
(`lastReadingTime`, `humSusTs`). Entre los repositorios (`Captura`, `Configuracion`, `Diagnostico`, `Historial`, `Zona`,
etc.) no hay ninguno de lecturas. El historial de eventos (`HistorialEventoEntity`) registra acciones, no mediciones.

Sin este cambio, cada regla que lo necesita inventaría su propia tabla o quedaría sin dato.

## What Changes

- Tabla `zone_reading` con una fila por lectura aceptada de una macro-zona (las 10 métricas + DPV cuando exista) y su origen.
- Servicio `ReadingHistory` único para todas las reglas: últimas N lecturas, máximo/mínimo/media en una ventana, con filtro
  opcional por franja horaria (p. ej. 10–16 h).
- Escritura en `updateTelemetry`, en la misma transacción que la lectura de la zona.
- Purga programada (retención configurable).
- Control desde el simulador: nada que accionar (no es una regla), pero la telemetría que el simulador publica alimenta la serie.

## Out of scope

- El cálculo del DPV (`add-metrica-dpv`) y las reglas que consultan el historial.
- Gráficos de tendencia en el dashboard (`Sparkline` hoy usa datos del mock; se puede enchufar después).
