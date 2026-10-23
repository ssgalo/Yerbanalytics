## ADDED Requirements

### Requirement: Grafo de razonamiento fiel a la ejecución
El grafo de razonamiento de una acción del historial SHALL dibujar las reglas como estaban cuando se tomó la decisión: cada
evento del motor SHALL guardar qué piezas estaban apagadas en ese momento (`reglasInactivas`), y el grafo SHALL excluir
sólo esas. La regla que decidió SHALL dibujarse siempre. Para un evento anterior a este registro, el grafo SHALL excluir las
reglas apagadas por defecto (retiradas). Las etiquetas SHALL llegar sin emojis desde el backend, sin limpieza en el cliente.

#### Scenario: Regla apagada hoy, activa entonces
- **WHEN** R-03 corrió en un riego de ayer y hoy se apagó
- **THEN** el grafo del riego de ayer sigue mostrando R-03 como ejecutada

#### Scenario: Regla apagada entonces
- **WHEN** R-05 estaba apagada al registrarse un riego
- **THEN** el grafo de ese riego no la dibuja, aunque hoy esté prendida

#### Scenario: Evento viejo
- **WHEN** un evento no tiene `reglasInactivas`
- **THEN** el grafo excluye las reglas con `activaPorDefecto: false`

### Requirement: Cambios de estado de reglas en el historial
Prender o apagar una regla SHALL quedar en el historial como un evento de configuración con la regla, el usuario, el motivo
y, si es una protección apagada, la consecuencia.

#### Scenario: Filtrar por configuración
- **WHEN** el usuario filtra el historial por tipo Configuración
- **THEN** ve los cambios de estado de reglas junto a los de parámetros
