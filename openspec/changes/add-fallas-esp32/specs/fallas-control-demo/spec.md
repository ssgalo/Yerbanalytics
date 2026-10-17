## ADDED Requirements

### Requirement: Control de las fallas desde el simulador
`FallaHidraulicaRule`, `FallaMediasombraRule` y el receptor de errores del nodo SHALL ser `DemoControllable` apagables, las dos reglas con el
bypass `fault` (ignoran la falla marcada), y el simulador SHALL tener un escenario por cada tipo de error.

#### Scenario: Falla hidráulica desde el simulador
- **WHEN** el operador elige "Sin caudal" para S-012 y pulsa Disparar
- **THEN** el simulador publica el error en el tópico de errores, el sector queda en "Falla hidráulica" y el siguiente pulso no lo riega

#### Scenario: Bypass de la falla
- **WHEN** se activa el bypass `fault` en `FallaHidraulicaRule`
- **THEN** el sector con falla marcada vuelve a evaluarse para el riego

#### Scenario: Receptor apagado
- **WHEN** se apaga el receptor de errores y el simulador publica `SIN_CAUDAL`
- **THEN** el sector no se marca averiado
