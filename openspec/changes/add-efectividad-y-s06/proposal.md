# Change: add-efectividad-y-s06

> Rama: `feat/reglas-v2-efectividad-s06`. Cubre **E-01** (realineado), **E-02**, **E-03**, **S-06** y **F-05**
> (`reglas_v2` §4, §8.5, §9) y el registro de la **revisión humana**. Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`, `add-dosificacion-base`, `add-reglas-biotico`, `add-reglas-clorosis-estres-solar`.

## Why

La evaluación de efectividad existe pero no es la de la v2, y lo que debería hacer con el resultado no existe:

- `HistorialService.evaluarSeguimiento()` (scheduler cada 30 s) evalúa **sólo riego**, sobre la humedad de sustrato de la
  zona, con latencia y umbral que salen de la **configuración agronómica** (`getLatencyMs`, `getUmbralRecuperacion`), y
  marca "Efectiva" si `|delta| ≥ umbral`. La v2 pide **+8 puntos o más** (sólo suba; un riego que *baja* la humedad no
  es efectivo) con una **lectura de verificación** a los 30 min, que el backend pide fuera del ciclo de 4 h y que **no
  dispara reglas**. Esa lectura no existe.
- `evaluarSeguimiento()` marca `bloqueoRepeticion = true`, pero **ninguna regla lo lee** (S-06 no existe): "Sin
  efectividad" no frena nada.
- `FollowUpRule` (prio 20) llama a ese método global **por cada sector evaluado**: es un delegador sin función propia
  que además repite una consulta global por sector.
- E-02 (fertilizante correctivo, 14 días) y E-03 (fitosanitario, 7 días) no existen, ni F-05 (cierre "Sano después del
  tratamiento").

## What Changes

- **E-01 realineado:** lectura de verificación a los 30 min del riego del sector del nodo testigo (nuevo `LEER_AHORA` que
  el motor ignora); "Efectiva" si la humedad de la MZ sube ≥ 8 puntos.
- **E-02:** a los 14 días de la primera aplicación correctiva, Efectiva si el diagnóstico del sector es Sano o la
  severidad bajó ≥ 1 nivel. **E-03:** a los 7 días del fitosanitario, Efectiva si la severidad no aumentó y ningún vecino
  nuevo tiene el mismo diagnóstico.
- **S-06:** con "Sin efectividad" se bloquea la repetición autónoma (riego: toda la MZ; fertilizante correctivo: el
  sector; fitosanitario: el sector), con las excepciones de la v2 (R-02 sigue, tope 1 riego/12 h; el plan de nutrición
  sigue; la mediasombra nunca se bloquea). `CRITICAL` pidiendo revisión física.
- **Revisión humana:** acción "registrar revisión" (API + UI) que levanta el bloqueo.
- **F-05:** un sector tratado que vuelve a Sano vigente cierra el seguimiento como "Efectiva" (`INFO`).
- Se retira `FollowUpRule` y la evaluación genérica por humedad; la evaluación pasa a ser un **servicio propio** con su scheduler.
- Se revisa el tope siempre-activo de R-02 (`align-reglas-existentes-v2` D-2), ahora que S-06 existe.

## Out of scope

- Evaluar mediasombra, plan de nutrición o N-02 (la v2 los excluye expresamente).
- Hardware de caudalímetro como detector de falla hidráulica (`add-fallas-esp32`).
