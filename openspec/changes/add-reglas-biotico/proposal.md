# Change: add-reglas-biotico

> Rama: `feat/reglas-v2-reglas-biotico`. Cubre **F-F1**, **F-F2**, **F-P1**, **F-04** (foco) y **F-06** (tope de aplicaciones)
> de `reglas_v2` §8.5. Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`, `add-historial-lecturas`, `add-metrica-dpv`, `add-dosificacion-base`.

## Why

Hoy un sector con **daño fúngico** o **plaga foliar** se trata (o no) según `SupplyRule`: si el sector llegó a estado
`critical` y la confianza alcanza, se activa *una bomba genérica*. La v2 pide decisiones distintas:

- **F-F1 / F-P1:** aplicar fitosanitario sólo si la severidad es moderada o alta, **o** el ambiente lo favorece (aire
  saturado para el hongo; calor seco para el ácaro), **o** el diagnóstico persiste 3 capturas seguidas. Un caso leve y
  recién aparecido, con ambiente seco, **espera**.
- **F-F2:** condición predisponente al hongo **sin diagnóstico**: sólo alerta.
- **F-04:** si ≥ 3 sectores de una MZ tienen el mismo diagnóstico biótico en 72 h, tratar también a los **8 lindantes**
  de cada afectado.
- **F-06:** tope de 3 fitosanitarios en 30 días por sector (un solo producto ⇒ riesgo de resistencia).

Ninguna existe (búsqueda de `foco`, `tope`, DPV: sin resultados en el backend).

## What Changes

Cinco reglas en la rama INSUMO, todas delegando la ejecución a `ApplicationService`:

| Regla | Condición resumida | Acción | Alerta |
|---|---|---|---|
| F-F1 | Daño fúngico vigente **y** (sev ≥ moderada **o** sustrato > 80 % / DPV < 0,3 en 2 lecturas **o** 3 capturas seguidas) | Fitosanitario 100 mL | `WARNING`, `CRITICAL` si alta |
| F-F2 | DPV < 0,3 en 2 lecturas **y** 18 ≤ T ≤ 28 **y** sustrato ≥ 75 % (sin diagnóstico) | Nada | `WARNING` |
| F-P1 | Plaga foliar vigente **y** (sev ≥ moderada **o** DPV ≥ 2 en las últimas 72 h **o** 3 capturas seguidas) | Fitosanitario 100 mL | `WARNING`, `CRITICAL` si alta; `INFO` si no actúa |
| F-04 | ≥ 3 sectores de la MZ con el mismo diagnóstico biótico vigente en 72 h | Fitosanitario a los 8 lindantes (sin P-D1) | `CRITICAL` "Foco de [diagnóstico] en MZ-N" |
| F-06 | 3 fitosanitarios en 30 días en el sector | Bloquea la siguiente hasta registrar revisión | `WARNING` |

## Out of scope

- La evaluación posterior (E-03) → `add-efectividad-y-s06`.
- Clorosis y estrés solar → `add-reglas-clorosis-estres-solar`.
