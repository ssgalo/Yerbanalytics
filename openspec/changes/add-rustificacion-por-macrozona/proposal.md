# Change: add-rustificacion-por-macrozona

## Why

El plan de rustificación vivía en la configuración global, con etapas por porcentaje de apertura.
Pero la mediasombra sólo abre o cierra del todo, y cada macro-zona se rustifica cuando sus plantines
están listos para el trasplante, no todas a la vez.

## What

- Plan de rustificación **por macro-zona**, iniciado y cancelado desde el detalle de la zona: fecha
  de inicio y etapas de `dias` con una ventana diaria `abiertaDesde`–`abiertaHasta`.
- **Horario por defecto** de la mediasombra en Configuración → Umbrales, para las zonas sin plan.
- Se quita la rustificación de la configuración (vista, tipo y validación).
- **Esta etapa es sólo frontend.** El backend se adapta después.

## Fuera de alcance

- Backend y motor de reglas (pendientes).
- Historial de planes finalizados o cancelados en la vista.
