## ADDED Requirements

### Requirement: Control de S-03 y S-04 desde el simulador
`ReadingValidationGuard` (S-03) y `HardwareIncompletoRule` (S-04) SHALL ser `DemoControllable` apagables; la primera con el bypass `retries`
(no pide relecturas) y la segunda con el bypass `hardware` (ignora los actuadores faltantes), y el simulador SHALL tener los escenarios
"Lectura inválida" y "Hardware incompleto".

#### Scenario: Apagar S-03
- **WHEN** se apaga `ReadingValidationGuard` y llega humedad de sustrato 112 %
- **THEN** la lectura se acepta como cualquier otra

#### Scenario: Escenario de lectura inválida
- **WHEN** el operador elige "Lectura inválida" y pulsa Disparar
- **THEN** el simulador publica una métrica fuera de rango y se observan la relectura y la alerta

#### Scenario: Escenario de hardware incompleto
- **WHEN** el operador elige "Hardware incompleto" para un sector
- **THEN** el simulador da de baja la electroválvula del sector por la API de dispositivos y el siguiente pulso aborta el riego de ese sector

#### Scenario: Bypass de hardware
- **WHEN** se activa el bypass `hardware`
- **THEN** el sector sin electroválvula se evalúa como si estuviera completo
