# Spec: historial-persistencia

## ADDED Requirements

### Requirement: Registro persistente de acciones
El backend SHALL persistir un evento de historial inmutable por cada acción que el
sistema ejecuta, con su sector, macro-zona, tipo, marca temporal, cadena de
justificación (condición desencadenante, decisión, acción) y resultado.

#### Scenario: Actuación dispara un registro
- **WHEN** durante el procesamiento de telemetría un actuador transiciona a estado
  activo (electroválvula a "Regando" o bomba a "Dosificando")
- **THEN** se persiste un `historial_evento` con la condición que lo originó, la
  decisión del motor y los parámetros de la acción

#### Scenario: No se registra sin transición
- **WHEN** un actuador ya estaba activo y se mantiene activo en el siguiente ciclo
- **THEN** no se genera un nuevo evento de historial duplicado

### Requirement: Inmutabilidad del historial
El historial SHALL ser de solo inserción: el backend NO SHALL exponer operaciones de
edición ni eliminación de eventos.

#### Scenario: Sin verbos de escritura
- **WHEN** un cliente intenta modificar o borrar un evento del historial
- **THEN** el backend no ofrece ningún endpoint PUT/PATCH/DELETE para hacerlo

### Requirement: Seguimiento de efectividad post-acción
El backend SHALL evaluar automáticamente la efectividad de cada acción tras vencer su
latencia: tomar una nueva lectura de la métrica afectada, calcular el delta contra el
valor desencadenante y fijar el veredicto.

#### Scenario: Acción efectiva
- **WHEN** vence la latencia y el delta de la métrica supera el umbral de recuperación
- **THEN** el evento se marca con veredicto "Efectiva" y se registra el valor actual

#### Scenario: Acción sin efectividad
- **WHEN** vence la latencia y la métrica no mejoró según el delta esperado
- **THEN** el evento se marca como "Sin efectividad" y se bloquea la repetición
  autónoma de esa acción

### Requirement: Endpoint de consulta filtrable
El backend SHALL exponer `GET /api/historial` que devuelve los eventos ordenados del
más reciente al más antiguo, con filtros opcionales por sector, macro-zona, tipo de
acción y rango de fechas.

#### Scenario: Consulta sin filtros
- **WHEN** se invoca `GET /api/historial`
- **THEN** devuelve todos los eventos ordenados por marca temporal descendente

#### Scenario: Consulta filtrada
- **WHEN** se invoca con parámetros `sector`, `zona`, `tipo`, `desde` y/o `hasta`
- **THEN** devuelve solo los eventos que cumplen los criterios indicados
