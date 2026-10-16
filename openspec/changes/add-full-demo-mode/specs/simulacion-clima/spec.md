## ADDED Requirements

### Requirement: Cliente de clima simulado en memoria
En modo `SIMULATED`, el backend SHALL instanciar `SimulatedWeatherClient` como implementación primaria de `WeatherClient`, evitando llamadas salientes a Open-Meteo. El cliente simulado SHALL permitir registrar y actualizar en memoria un pronóstico meteorológico ficticio (`WeatherForecast`) mediante `POST /api/demo/weather`.

#### Scenario: Inyección de clima lluvioso ficticio
- **GIVEN** el modo demo activo
- **WHEN** se envía un payload a `POST /api/demo/weather` con `rainProbability: 90.0` y `rainVolumeMm: 25.0`
- **THEN** el endpoint retorna 200 OK y las subsiguientes consultas a `WeatherService.getForecastSinEspera()` retornan el pronóstico inyectado

#### Scenario: Restablecer clima por defecto
- **GIVEN** un pronóstico ficticio previamente configurado
- **WHEN** se envía una petición `DELETE /api/demo/weather`
- **THEN** el cliente de clima simulado restablece el pronóstico neutro por defecto (temperatura óptima, sin lluvia)

### Requirement: Influencia del clima simulado en las reglas agronómicas
El pronóstico inyectado a través del cliente simulado SHALL propagarse a `RuleContext.forecast()` y ser consumido por las reglas dependientes de clima (`PosponerPorLluviaRule`, `DeficitCriticoRule`, `ShadingRule`) exactamente igual que si proviniera de una estación meteorológica real.

#### Scenario: Regla de lluvia pospone riego ante clima ficticio
- **GIVEN** un clima simulado inyectado con `rainProbability: 95.0` y `rainVolumeMm: 20.0`
- **AND** la regla `PosponerPorLluviaRule` habilitada
- **WHEN** se procesa una telemetría que requiere riego por déficit hídrico
- **THEN** `PosponerPorLluviaRule` emite `POSTPONE_RIEGO`, posponiendo la apertura de la electroválvula
