## ADDED Requirements

### Requirement: Reglas agrupadas por objetivo agronómico
La pestaña Reglas de Motor de reglas SHALL agrupar las piezas por su objetivo, en este orden: Riego, Nutrición, Sanidad,
Mediasombra, Seguimiento y Protecciones. Un grupo sin piezas SHALL NOT dibujarse. La prioridad técnica SHALL NOT mostrarse.

#### Scenario: Grupo vacío
- **WHEN** no hay ninguna regla con objetivo Sanidad
- **THEN** la vista no muestra el grupo Sanidad

#### Scenario: Protección de un objetivo
- **WHEN** se dibuja `SustratoSaturadoRule`
- **THEN** aparece en el grupo Riego con el chip "Protege"

### Requirement: Tarjeta por regla
Cada pieza conmutable SHALL tener una tarjeta con su nombre y código, un chip de efecto (Actúa, Avisa o Protege), un
`Switch` y sus propios parámetros en línea con `InputUnit` (unidad), rango y valor de fábrica. Una regla apagada SHALL
mostrar quién, cuándo y el motivo; una protección apagada, además, la consecuencia en un `Callout` crítico. Una regla
apagada por la demo SHALL decirlo.

#### Scenario: Protección apagada
- **WHEN** R-04 está apagada con motivo
- **THEN** su tarjeta muestra el `Switch` apagado, el usuario, la fecha, el motivo y la consecuencia

#### Scenario: Regla retirada
- **WHEN** una regla tiene `activaPorDefecto: false`
- **THEN** su tarjeta muestra el chip "Retirada"

#### Scenario: Consumidor sin interruptor
- **WHEN** se dibuja el despacho de riego
- **THEN** aparece como tarjeta de ejecución con sus parámetros y sin `Switch`

### Requirement: Parámetros compartidos se editan una vez
Un parámetro que usan dos o más piezas SHALL editarse en un único lugar (el bloque de compartidos del grupo con más
consumidores con interruptor; empate, el primero en el orden de la pantalla; los consumidores sin interruptor no votan salvo
que sean los únicos) y SHALL mostrarse en cada tarjeta que lo usa en sólo lectura con "Compartido con N reglas" y un
enlace a ese lugar.

#### Scenario: Umbral de humedad
- **WHEN** se abre el grupo Riego
- **THEN** `riego.umbral-humedad` es editable en el bloque de compartidos de Riego y cada tarjeta que lo usa lo muestra como "Compartido con 5 reglas"

### Requirement: Parámetros en lote, interruptores al instante
Los cambios de parámetros SHALL acumularse en un borrador y guardarse juntos con la `SaveBar` (todo o nada). Un cambio de
`Switch` SHALL aplicarse en el momento, después de su confirmación, sin tocar el borrador de parámetros.

#### Scenario: Apagar con parámetros sin guardar
- **WHEN** hay dos parámetros sin guardar y el usuario apaga una regla y confirma
- **THEN** la regla queda apagada y la `SaveBar` sigue mostrando los dos parámetros pendientes

#### Scenario: Confirmación de una protección
- **WHEN** el usuario apaga una protección
- **THEN** se abre un diálogo con la consecuencia y un campo de motivo obligatorio; sin motivo no se puede confirmar

#### Scenario: Cancelar la confirmación
- **WHEN** el usuario cancela el diálogo
- **THEN** el `Switch` vuelve a su estado y el foco vuelve al `Switch`

### Requirement: Sólo lectura sin permiso
Sin `reglas.editar`, la pestaña Reglas SHALL mostrarse en sólo lectura: `Switch` deshabilitados, campos de sólo lectura,
sin `SaveBar`, y un `Callout` que diga qué permiso hace falta.

#### Scenario: Operario
- **WHEN** un usuario con `reglas.ver` y sin `reglas.editar` abre Motor de reglas
- **THEN** ve el estado de cada regla y sus umbrales, y no puede cambiar ninguno

### Requirement: Inspector limpio
La pestaña Inspector SHALL conservarse y SHALL dibujar condiciones fijas, alertas y resultados de comparación con
componentes del sistema de diseño (`Icon`, `Chip`, `StateGlyph`), sin emojis ni símbolos tipográficos sueltos.

#### Scenario: Resultado de una comparación
- **WHEN** el Inspector muestra una comparación que se cumple, otra que no y otra sin dato
- **THEN** usa el ícono `check`, el ícono `close` y el glifo de sin señal, cada uno con texto para lector de pantalla
