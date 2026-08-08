## ADDED Requirements

### Requirement: Regeneración de topología robusta bajo concurrencia
La regeneración de la topología SHALL reemplazar la grilla existente con un borrado en bloque
(una sentencia por tabla), de modo que sea rápido y no quede bloqueado ni falle por la
actividad concurrente de lectura o de los procesos en segundo plano (p. ej. la evaluación de
historial). La regeneración SHALL dejar cada sector sin valores históricos (offline, lecturas
en `null`).

#### Scenario: Regenerar mientras el vivero se consulta
- **WHEN** se regenera la topología mientras el panel general consulta el vivero de forma
  periódica
- **THEN** la regeneración responde sin quedar colgada ni fallar por bloqueo/optimistic
  locking, y los sectores quedan offline sin lecturas previas

#### Scenario: La robustez no depende de quién regenere
- **WHEN** la regeneración se dispara desde el panel de topología del dashboard o desde
  cualquier otro consumidor del endpoint público
- **THEN** se comporta igual, con el mismo borrado en bloque y el mismo resultado
