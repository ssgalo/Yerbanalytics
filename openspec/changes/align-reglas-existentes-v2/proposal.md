# Change: align-reglas-existentes-v2

> Rama: `feat/reglas-v2-align` (desde `feature/doc-openSpec-rules`). Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas` (bloque 7).

## Why

Las reglas que el motor **ya tiene** no son todas las de `reglas_v2.md`. La auditoría del 16/10/2026
(código contra documento) encontró reglas que difieren de la v2, parámetros con otro valor de fábrica y
comportamientos que la v2 pide y no existen. Si no se alinean primero, cada cambio nuevo
(`add-*`) se apoya en una base que contradice el documento que dice implementar.

## What Changes

Sólo toca lo **ya existente**. Lo que falta por completo vive en los demás cambios del índice.

1. **S-02 completo** (hoy parcial): al tercer fallo, **estado seguro** en la MZ (riego cerrado, bombas
   detenidas, mediasombra cerrada); reintento cada 15 min con el nodo fuera de servicio; parámetros de §11
   en el catálogo (espera, reintentos, reintento fuera de servicio).
2. **Confianza mínima 85 % → 70 %** (`diagnostico.confianza-minima`, rango 50–95 %), §1.3 y §11.
3. **R-02 y el tope de 12 h**: dejar el tope alineado con S-06 (ver *Decisiones abiertas* D-2).
4. **`CicloLecturaRiegoRule`** (no está en la v2): decidir si se mantiene o se retira (D-3).
5. **`StaleSensorRule`** (no está en la v2): documentar su relación con S-02/S-03 y su umbral de 90 s (D-4).
6. **Textos y trazas** que citan reglas o umbrales de la v1 (p. ej. mensajes con "85 %").
7. **Tabla de reglas heredadas**: queda registrado qué cambio retira a cada una
   (`ShadingRule`, `SupplyRule`, `DailyDoseLimitRule`, `FollowUpRule`). **Este cambio no las toca.**

## Qué NO hace

- No retira `ShadingRule`, `SupplyRule`, `DailyDoseLimitRule` ni `FollowUpRule`: dejarían al sistema sin
  mediasombra ni dosificación hasta que existan sus reemplazos (`add-mediasombra-v2`, `add-dosificacion-base`,
  `add-efectividad-y-s06`).
- No implementa S-03, S-04, S-05, S-06 ni las reglas N/F/E/O: tienen su propio cambio.
- No cambia las reglas R-01, R-03, R-04, R-05, R-06: sus parámetros coinciden con §11 (verificado).

## Impact

Backend (`engine/parametros`, `sensing`, `service/EscalamientoNodoService`, `mqtt/ComandoActuadorPublisher`),
catálogo de parámetros (valores de fábrica), dashboard sólo por el rango del parámetro de confianza.
Migración: un valor de fábrica nuevo **no** pisa uno ya editado por el agrónomo (ver design D-1).
