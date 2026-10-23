# Change: add-plan-nutricion

> Rama: `feat/reglas-v2-plan-nutricion`. Cubre **N-01** y **N-02** (`reglas_v2` §8.4). Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`, `add-historial-lecturas`, `add-dosificacion-base`. El `ZoneRuleEngine` lo crea este cambio (`add-mediasombra-v2` se canceló).

## Why

La v2 separa la nutrición de las reglas por diagnóstico: un **plan de nutrición** propio (fertilizante foliar cada 7
días en todos los sectores de la MZ, sin mirar lo que se mide) más un adelantamiento por **CE baja**. Hoy no existe
ninguna de las dos (la búsqueda de `stock|tanque|foco` no halló nada de nutrición; la única dosificación es `SupplyRule`).

## What Changes

- **N-01 Plan de nutrición:** cuando pasaron los días del intervalo de la MZ desde la última aplicación del plan,
  pedir 100 mL de fertilizante en **cada sector** de la MZ, en la próxima ventana apta. Un sector que no cumple las
  precondiciones se reintenta en la ventana siguiente. `INFO`.
- **N-02 CE baja:** si la CE media de las últimas 24 h (6 lecturas) < 0,5 dS/m **y** no hubo fertilización en la MZ
  en 5 días, adelantar N-01 a la próxima ventana apta. `INFO` "CE baja: la fertilización de base del sustrato
  podría estar agotada".
- **Intervalo del plan por MZ** (fábrica 7 días, rango 3–30), editable en el detalle de macro-zona.
- Registro de la **última aplicación del plan** por MZ para distinguirla de las correctivas (F-C2).

## Out of scope

- La maquinaria de aplicar (precondiciones, stock) → `add-dosificacion-base`.
- Espaciar la fertilización durante la rustificación: la v2 lo deja al agrónomo (alargar el intervalo de la MZ).
