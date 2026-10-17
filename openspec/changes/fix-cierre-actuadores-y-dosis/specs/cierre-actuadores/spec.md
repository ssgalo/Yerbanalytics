## ADDED Requirements

### Requirement: Un riego cortado desde afuera se cierra
Un ack `ERROR cancelado` de un comando de válvula SHALL cerrar el riego en curso de ese sector en el despacho y dejar
una alerta `INFO` de regla `RiegoCortado`, con el sector, en el historial. Un ack `reemplazado` SHALL NOT cerrar nada.

#### Scenario: Corte manual
- **WHEN** el sector MZ-2-003 riega y el nodo manda `ERROR cancelado` del comando de su válvula
- **THEN** su estado pasa a "Cerrada", se libera el cupo y queda la alerta "Riego cortado antes de tiempo"

#### Scenario: Reemplazado
- **WHEN** el nodo manda `ERROR reemplazado` del comando de la válvula
- **THEN** el sector sigue regando

#### Scenario: Reinicio del backend
- **WHEN** el backend se reinicia después del corte
- **THEN** el riego anterior al corte no se reconstruye como abierto, y uno posterior sí

#### Scenario: Válvula que el despacho no abrió
- **WHEN** llega un `cancelado` de una secuencia de Demo Expo o del estado seguro para un sector que no figuraba regando
- **THEN** no se registra ningún corte

### Requirement: La bomba sale de "Dosificando"
El estado `Dosificando` de un sector SHALL volver a `En espera` con el ack final de la bomba (`ok`, `cancelado` o un
error, no `reemplazado`) y, si el ack no llega, cuando pase la duración de la dosis más el margen de ack.

#### Scenario: Ack final
- **WHEN** el nodo confirma la dosis del sector
- **THEN** el sector queda `En espera` y la próxima decisión de dosificar publica el comando

#### Scenario: El nodo se reinicia a mitad de la dosis
- **WHEN** no llega ningún ack
- **THEN** pasada la duración de la dosis más `yerbanalytics.actuadores.ack-margen-ms` el sector queda `En espera`

### Requirement: La dosis la decide el backend
El comando de la bomba SHALL llevar `parametros.ml` con el valor del parámetro `insumo.dosis-ml` (fábrica 10, rango 1–50).

#### Scenario: Dosis por defecto
- **WHEN** `SupplyRule` autoriza una dosificación con el catálogo de fábrica
- **THEN** se publica `pump ON {"ml": 10}` y el historial guarda 10 s de duración

#### Scenario: Dosis editada
- **WHEN** el agrónomo fija `insumo.dosis-ml` en 25
- **THEN** el comando lleva `{"ml": 25}`
