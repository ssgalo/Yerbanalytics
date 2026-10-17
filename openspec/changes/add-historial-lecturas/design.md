# Design: add-historial-lecturas

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| Escritura de lecturas | `be/service/NurseryService.updateTelemetry` (~l.527-600): asigna `zona.set<Metrica>Raw(...)` sólo para las métricas presentes en el payload; fija `lastReadingTime`; guarda la zona |
| Lectura vieja | Se ignora si `tsMs < lastReadingTime` (buffer offline del nodo) — esa lectura no debe entrar al historial |
| Lectura parcial | Una métrica ausente conserva el valor anterior (no hay "lectura vacía") |
| CE | Llega en µS/cm; se persiste en dS/m (`ContratoNodo.ceADsPorM`) |
| Pulso | `pulsos.respondePulso(zoneId, recepcionMs)` dice si la lectura responde a un pulso **con motor**; el cierre del pulso está en `PulsoSensadoService` |
| Repositorios | No hay uno de lecturas |

## Decisiones

### D-1 — Qué se guarda
Una fila por **lectura aceptada**: la que pasa el filtro de "no es anterior a la última" y, cuando exista, S-03
(`add-seguridad-lectura-hardware`). Columnas: `zona_id`, `ts` (ms, el del nodo normalizado), `recibida_ts`,
`origen`, y las 10 métricas **tal como están en la zona después de aplicar la lectura** (incluidas las que no vinieron:
se copia el valor vigente), más `dpv` (nullable). Guardar el estado completo evita reconstruir "qué valía X en ese momento"
sumando filas parciales.

### D-2 — Origen y qué cuenta como "lectura seguida"
`origen ∈ {PULSO, OTRA}`. `PULSO` = respondió a un pulso (con o sin motor). `OTRA` = lectura manual, verificación de
E-01, secuencia LECTURA, arranque del nodo. Las reglas de "N lecturas seguidas" / "media de 6 lecturas" cuentan sólo `PULSO`
(una lectura extra de verificación no debe correr la media ni "completar" dos lecturas seguidas). Los máximos/medias de
ventana temporal (48 h, 72 h) cuentan **todas**. *Requiere ver `pulsos.respondePulso` para decidir si responde también a pulsos sin motor; hoy
devuelve sólo los de con-motor: se agrega un método hermano `respondeAlgunPulso`.*

### D-3 — Consultas
`HistorialLecturas`:
- `ultimas(zonaId, n, soloPulso)` → lista cronológica.
- `maximo/minimo/media(zonaId, metrica, desde, hasta?, franjaHoraria?)` → `Optional<Double>` (vacío sin datos, nunca 0).
- `conteo(zonaId, metrica, desde)` para decidir `SIN_DATO` cuando hay menos lecturas que las exigidas.
La franja horaria se evalúa en hora local del vivero (`ZonaHorariaVivero.ZONA`).

### D-4 — Retención
Parámetro `lecturas.retencion-dias` (fábrica 14, rango 3–90) en `ConfiguracionOperativa`/catálogo; tarea programada diaria
(03:30) borra lo anterior. 14 días cubre el máximo de ventana de las reglas (72 h) con holgura para auditar.

### D-5 — Índice y volumen
Índice (`zona_id`, `ts` desc). Volumen: 6 zonas × 6 lecturas/día × 14 d ≈ 500 filas; sin problema de rendimiento.
Con el intervalo mínimo (60 min) y una lectura manual ocasional, sigue siendo trivial.

### D-6 — Migración
Entidad nueva ⇒ `ddl-auto=update` la crea; sin script manual. Un clon existente arranca con la serie vacía: las reglas dan
`SIN_DATO` hasta juntar lecturas (comportamiento ya previsto en cada spec). No se reconstruye pasado.

### D-7 — Transacción
Se escribe dentro de `updateTelemetry` (ya `@Transactional`). Si falla el guardado de la serie, **revierte la lectura**: preferible a una
serie con huecos que rompa silenciosamente "2 lecturas seguidas".

## Preguntas abiertas
- ¿Guardar también las lecturas descartadas por S-03? Default: no (quedan en el registro de alertas).
