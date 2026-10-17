## ADDED Requirements

### Requirement: Control del modo sin pronóstico desde el simulador
El simulador SHALL poder poner y sacar el modo sin pronóstico con `PUT /api/demo/weather/offline`, y SHALL tener un escenario modificador "Sin pronóstico"
que se combina con las reglas que usan el pronóstico (R-03, M-02, P-D3, P-D4).

#### Scenario: Riego sin pronóstico
- **WHEN** el operador marca R-01 junto a "Sin pronóstico" con un clima simulado de lluvia fuerte
- **THEN** el simulador activa el modo offline y R-01 riega aunque el clima simulado anunciara lluvia

#### Scenario: Volver a la normalidad
- **WHEN** se desactiva "Sin pronóstico"
- **THEN** el pronóstico simulado vuelve a estar disponible y R-03 pospone otra vez

#### Scenario: Alerta de entrada y salida
- **WHEN** se activa y desactiva el modo offline
- **THEN** quedan exactamente dos alertas `INFO`
