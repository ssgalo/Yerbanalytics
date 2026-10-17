# Design: fix-cierre-actuadores-y-dosis

## D-1 — El ack avisa por un evento de Spring, no por una dependencia
`DespachoRiego` depende (vía `ComandoActuadorPublisher`) de `SeguimientoComandosService`; si éste llamara al despacho
habría un ciclo. El seguimiento publica `ActuatorStopped(zona, sector, actuador, tipo)` y quien deriva un estado por
tiempo lo escucha (`DespachoRiego`, `SupplyDoseService`). Es síncrono, en el hilo MQTT, y no toca el flujo de las
secuencias de Demo Expo: `AckActuadorReceiver` sigue entregando el ack a ambos consumidores.

## D-2 — Qué cierra y qué no
- Válvula: sólo `cancelado`. `reemplazado` significa que otro comando más nuevo ocupó su lugar, ese sigue vigente.
  `ok` no hace falta: el plazo ya coincide con el fin.
- Bomba: cualquier ack final (ok, cancelado, error), salvo `reemplazado`.
- Hace falta el comando pendiente para saber qué actuador es (el ack no lo dice). Un `cancelado` cuyo comando se
  perdió en un reinicio no se atribuye a nadie: el riego/dosis vence por tiempo.
- `DespachoRiego` sólo actúa si ese sector figuraba regando. Un OFF de una secuencia o del estado seguro sobre una
  válvula que el despacho no abrió no deja alerta.

## D-3 — La huella del corte, y por qué es una alerta con sector real
"Regando" se reconstruye del historial; para que un corte sobreviva a un reinicio tiene que estar en él.
`HistorialService.registrarCorteRiego` escribe una fila `Alerta` `INFO`, regla `RiegoCortado`, con el `sectorId` real
(las demás alertas llevan "—" porque son de zona). `reconstruir` lee esas filas y descarta los riegos con `ts` anterior
al último corte de su sector. Un riego nuevo, posterior al corte, vuelve a contar. Alternativa descartada: una columna
nueva o una tabla de "cierres", más migración para lo que una fila de historial ya expresa.

## D-4 — Cierre de la bomba: ack y tiempo, con el estado en la base
"Dosificando" es un campo persistido del sector (a diferencia del riego), así que `SupplyDoseService` lo cierra con un
`UPDATE ... WHERE actuador_pump = 'Dosificando'` dirigido (no reescribe la fila con una copia vieja del sector).
El respaldo por tiempo barre `Dosificando` cada `yerbanalytics.insumo.cierre-revision-ms` (15 s) y compara contra la
última fila "Insumo" del sector: `ts + duracion_seg + ack-margen-ms` (90 s). `registrarInsumo(sector, ml)` guarda la
duración de la bomba (`ml × BOMBA_INSUMO_MS_POR_ML`); sin duración (dosis previas) se usa el tope del firmware (50 ml).
Un `Dosificando` sin ninguna fila "Insumo" es un estado huérfano y se cierra. Como el estado está en la base, también
limpia los que quedaron colgados antes de desplegar esto.

## D-5 — La dosis en el catálogo
`insumo.dosis-ml` (`ParametrosInsumo.DOSIS_ML`, familia INSUMO, entero, fábrica 10 = el 10 s de hoy a 1 s/ml, rango
1–50 = `LIMITE_BOMBA_ML_MAX`). `SupplyRule` lo declara y lo lee con `ev.numero`, y lo lleva en la acción como
`DetalleInsumo(ml)`; el `ActionExecutor` no lee el catálogo, sólo ejecuta. Las constantes del firmware
(`BOMBA_INSUMO_MS_POR_ML`, `LIMITE_BOMBA_ML_MAX`) se espejan en `ContratoNodo`. El simulador ya contestaba `ml`
(`simulatedAck`); sólo se agrega su test. El fixture del frontend suma el parámetro.

## D-6 — Un cancelado tardío no cierra el riego nuevo
El `cancelado` cita el comando ORIGINAL (verificado en vivo el 17/10). `ActuatorStopped` lleva el `commandId` y
`DespachoRiego` guarda en cada riego el del comando que lo abrió: sólo cierra si coinciden. Si no, un OFF seguido de una
reapertura inmediata dejaría que el ack viejo cerrara el riego nuevo y escribiera un `RiegoCortado` falso. Los riegos
reconstruidos del historial no conocen su comando y aceptan cualquiera (la regla de antes); en la práctica ya no llega
ningún ack, porque el seguimiento perdió el comando pendiente en el reinicio.

## D-7 — `@DynamicUpdate` en `SectorEntity`
La transacción de telemetría carga los sectores y hace `saveAll`; sin `@DynamicUpdate` el UPDATE reescribe todas las
columnas y podía devolver `Dosificando` después de que `cambiarBomba` confirmara. Con él sólo se escriben las columnas
que cambiaron. Queda una ventana si la propia telemetría cambia `actuador_pump` en esa transacción (sólo `ActionExecutor`,
al ordenar una dosis, y ahí la decisión es suya). Un `@Version` se descartó: haría fallar la telemetría ante cada cierre.

## Solapamiento con `add-dosificacion-base`
Ese cambio (reglas_v2 cambio 6) ya prevé `pump ON {ml}`, la dosis en el catálogo y el seguimiento por ack. Esto es un
subconjunto mínimo y compatible: `DetalleInsumo` y `insumo.dosis-ml` se convierten allí en dosis por insumo;
`SupplyDoseService` se vuelve el cierre de cada aplicación; `registrarInsumo(sector, ml)` pasa a registrar insumo y mL.
`add-dosificacion-base` retira `SupplyRule`, así que hereda este parámetro al migrar.

## Hallazgo: el comando de las 17:24 con el estado "Dosificando"
En el código no hay otro camino que vuelva `actuadorPump` a `En espera` que `TopologiaService` al regenerar. Sin los
logs no se puede decir cuál pasó; las hipótesis consistentes son un comando para *otro* sector (el estado es por
sector) o que se haya regenerado la topología / tocado la base a mano entre las 17:19 y las 17:24. Verificarlo al
reproducir en vivo con el sector exacto de cada línea de log.
