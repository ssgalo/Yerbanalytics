# Design: add-bloqueo-manual

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| Regla | `be/engine/rules/ManualLockRule.java`: prioridad 0, `ABORT_ALL` con `CancelaRiego.TODAS`, bypass `lock` para el simulador |
| Modelo | `be/model/ManualLockEntity.java` (`bloqueo_manual`): `sectorId`, `zonaId`, `activatedBy`, `activatedAt`, `reason`, `active` |
| Repositorio | `be/repository/ManualLockRepository.java`; lo consulta `NurseryService` al armar el `RuleContext` |
| Endpoints | **No existen** (spec viva `bloqueo-manual` los describe) |
| Frontend | Sin botón (v2 §13). Verificado: `src/data` (repositorio y tipos) no tiene nada de bloqueos; sólo el Inspector del DAG menciona la regla |
| Contexto del motor | `NurseryService.buildRuleContext` consulta `ManualLockRepository.findBySectorIdAndActiveTrue` y `findByZonaIdAndActiveTrue` por cada sector |

## Decisiones

### D-1 — El bloqueo vive en la base, no en memoria
Ya existe la entidad; se usa. `DELETE` desactiva (`active=false`), no borra, para conservar quién y cuándo.

### D-2 — Mediasombra quieta con bloqueo de MZ
`ABORT_ALL` corta la cadena de **un sector**; la mediasombra se comanda **por MZ**. Hay que confirmar cómo
decide hoy la mediasombra por sector (`ShadingRule` evalúa por sector) y, cuando exista `add-mediasombra-v2`,
que la evaluación por MZ consulte el bloqueo de la zona. Hasta entonces el test fija el comportamiento de
`ShadingRule` y queda pendiente el de M-01/M-02 (se agrega en `add-mediasombra-v2`).

### D-3 — Cierre al activar
Al crear el bloqueo el servicio publica el cierre de válvulas y bombas del alcance por
`ComandoActuadorPublisher`. Sin eso, un riego de 8 min ya abierto seguiría hasta que el ESP32 lo corte por
tiempo; la v2 dice "riego cerrado y bombas detenidas".

### D-4 — Sin acciones retroactivas
Reanudar sólo desactiva el bloqueo. No re-encola riegos cancelados. La próxima lectura (pulso) decide.

### D-5 — Quién puede
Permiso nuevo `bloqueos.gestionar` (catálogo fijo `seguridad/Permiso.java`), sembrado para Operario,
Administrador e Ingeniero Agrónomo. Lectura bajo `bloqueos.ver`. Uno de edición no implica el de lectura
(CLAUDE.md §2), por eso son dos.

### D-6 — Alertas
`registrarAlerta` del historial con `DetalleAlerta(INFO, …)` al activar y al reanudar, con usuario y motivo.
