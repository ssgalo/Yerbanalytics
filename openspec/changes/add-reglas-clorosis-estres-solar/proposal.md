# Change: add-reglas-clorosis-estres-solar

> Rama: `feat/reglas-v2-clorosis-estres-solar`. **Implementadas F-C1, F-C2, F-S1 y F-S3; F-S2 no** (bloqueada por la cancelación de `add-mediasombra-v2`, ver el design). Cubre **F-C1**, **F-C2**, **F-S1**, **F-S2** y **F-S3** (`reglas_v2` §8.5).
> Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`, `add-historial-lecturas`, `add-dosificacion-base`, `add-mediasombra-v2` (F-S2).

## Why

Son las dos familias de diagnóstico **no biótico**. Hoy el sistema las trata igual que cualquier otro estado crítico
(`SupplyRule`: bomba genérica). La v2 pide separar causas:

- **Clorosis** es un síntoma con varias causas. Antes de fertilizar (F-C2) se descartan las que el fertilizante no
  corrige (F-C1): asfixia radicular (sustrato > 80 % por 48 h), salinidad (CE ≥ 2,0) y clorosis férrica (pH ≥ 6,5).
- **Estrés solar** no usa químicos: se corrige con agua (F-S1: riego de reposición del sector) y sombra (F-S2: si ≥ 10 %
  de la MZ lo padece, malla cerrada 11–16 h por 3 días). F-S3 evita cerrar la malla cuando no hubo radiación alta.

Ninguna existe. La métrica de pH y CE están en el snapshot, pero ninguna regla las usa (`umbral_metrica` sólo define
estado y color).

## What Changes

| Regla | Condición resumida | Acción | Alerta |
|---|---|---|---|
| F-C1 | Clorosis vigente **y** (sustrato medio 48 h > 80 % **o** CE ≥ 2,0 **o** pH ≥ 6,5) | No aplicar fertilizante correctivo | `WARNING` con la causa |
| F-C2 | Clorosis vigente **sin** causa de F-C1 (en rustificación, sólo sev. Media/Alta) | Fertilizante 100 mL; se repite respetando el intervalo | `WARNING`; `CRITICAL` si alta |
| F-S1 | Estrés solar vigente **y** (luz ≥ 80 % entre 10–16 h en 48 h **o** T ≥ 32 °C) y sustrato < 55 % | Riego de reposición del sector (fórmula de §5, dentro de la ventana, respetando R-04 y R-06) | `WARNING`; `CRITICAL` si alta |
| F-S2 | ≥ 10 % de los sectores de la MZ cumplen F-S1 en la última captura | Mediasombra de la MZ cerrada 11–16 h por 3 días | `WARNING` "Estrés solar en el N % de la MZ: evaluar cancelar el plan de rustificación" |
| F-S3 | Estrés solar vigente **y** luz < 60 % **y** T < 30 °C en 48 h | Nada | `WARNING` "Síntoma no explicado por radiación…" |

## Out of scope

- Evaluación posterior de F-C2 (E-02) y cierre "Sano después de tratamiento" (F-05) → `add-efectividad-y-s06`.
- La lectura de pH/CE más allá de lo que ya llega (el contrato no cambia).
