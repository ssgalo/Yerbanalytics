# Change: add-rustificacion-por-macrozona

## Why

El plan de rustificación vivía en la configuración global, con etapas por porcentaje de apertura.
Pero la mediasombra sólo abre o cierra del todo, y cada macro-zona se rustifica cuando sus plantines
están listos para el trasplante, no todas a la vez. Además, la mediasombra estaba modelada como un
actuador de sector, cuando físicamente hay una por macro-zona.

## What

- Plan de rustificación **por macro-zona**, iniciado y eliminado desde el detalle de la zona: fecha
  de inicio y etapas de `dias` con una ventana diaria `abiertaDesde`–`abiertaHasta`. Pasada la última
  etapa, se sostiene la última hasta que alguien elimina el plan.
- **Horario por defecto** de la mediasombra en Configuración → Umbrales, para las zonas sin plan.
- El plan y el horario por defecto los ejecuta un servicio **fuera del motor de reglas**, que actúa sólo
  en los bordes de la ventana; las reglas pueden mover la malla igual (p. ej. cerrarla por UV).
- **La mediasombra es de la macro-zona**: se registra con la zona, su posición vive en la zona, su
  comando y su ACK van por los tópicos de zona, y sus reglas son de zona. El simulador sigue pudiendo
  abrirla y cerrarla (secuencias de Demo Expo).
- Se quita la rustificación global (vista, tipo, tabla y validación).

## Fuera de alcance

- Una regla que reabra la malla en el pulso siguiente a un cierre del motor (por ahora queda cerrada
  hasta la próxima apertura del horario).
- Historial de planes eliminados en la vista.
