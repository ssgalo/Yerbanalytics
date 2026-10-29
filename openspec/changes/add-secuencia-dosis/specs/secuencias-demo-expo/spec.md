## ADDED Requirements

### Requirement: Secuencia de dosis
El backend SHALL aceptar una secuencia `DOSIS` con un parámetro `ml` entero entre 1 y 50 (5 si falta). La secuencia SHALL
prender la bomba de insumo del primer sector de la zona destino con `pump ON {ml}`, esperar el ACK final de ese comando hasta
ml segundos más el margen del riego, y publicar siempre `pump OFF` como paso seguro. SHALL NOT pasar por el motor de reglas ni
por `ApplicationService`.

#### Scenario: Dosis completa
- **WHEN** se inicia una dosis de 8 ml y el nodo confirma el ON al terminar
- **THEN** se publica `pump OFF`, y con su ACK la secuencia queda `COMPLETADA`

#### Scenario: Sin respuesta del nodo
- **WHEN** el ACK del ON no llega en ml + margen
- **THEN** el paso falla con `ACTUADOR_SIN_RESPUESTA` («La bomba de insumo no respondió…») y la bomba se apaga igual

#### Scenario: El nodo está ocupado regando
- **WHEN** el nodo contesta `EN_CURSO en_cola` con su espera máxima
- **THEN** el plazo del ACK se estira por esa espera, sin fallar

#### Scenario: Cantidad fuera de rango
- **WHEN** se pide 0 o 51 ml
- **THEN** responde 400 y no publica nada

### Requirement: Botón de dosificación en el simulador
El panel de secuencias del simulador SHALL ofrecer una tarjeta «Dosificación» con un campo de ml (1 a 50, 5 por defecto) que
inicia la secuencia `DOSIS`, y sus pasos SHALL nombrar la bomba de insumo, no la válvula.

#### Scenario: Pasos de la dosis
- **WHEN** corre una dosis de 8 ml
- **THEN** los pasos dicen «Prender la bomba de insumo», «Dosificar 8 ml» y «Apagar la bomba»
