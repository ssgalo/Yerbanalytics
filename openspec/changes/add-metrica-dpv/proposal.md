# Change: add-metrica-dpv

> Rama: `feat/reglas-v2-metrica-dpv`. Cubre **§1.4** de `reglas_v2` (déficit de presión de vapor). Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`, `add-historial-lecturas`.

## Why

F-F1, F-F2 y F-P1 deciden con el **DPV** (aire saturado < 0,3 kPa → riesgo fúngico; ≥ 2,0 kPa → calor seco,
ácaros). La v2 pide que **el backend lo calcule con cada lectura**, lo guarde como una métrica más y el front lo
muestre. Una búsqueda de `dpv`/`vapor` en el backend (`src/main`) no encontró ninguna ocurrencia: no existe.

## What Changes

- Cálculo `es = 0,6108 · e^(17,27·T/(T+237,3))`, `DPV = es · (1 − HR/100)` con la temperatura del aire y la
  humedad ambiental de la lectura.
- Persistencia en la zona junto a las otras métricas, con su historial (las reglas piden "2 lecturas seguidas",
  "alguna de las últimas 72 h").
- Estado/color con los tramos de §1.4 (< 0,3 · 0,5–1,2 · 1,2–2,0 · ≥ 2,0) y su aparición en el dashboard.
- Una consulta reutilizable `DpvHistorial` para las reglas F (lecturas seguidas, máximo en una ventana).

## Out of scope

- Las reglas que usan el DPV (`add-reglas-biotico`).
- Que el DPV sea una de las "10 métricas sensadas": es **derivada**, no viene del nodo (el contrato MQTT no cambia).
