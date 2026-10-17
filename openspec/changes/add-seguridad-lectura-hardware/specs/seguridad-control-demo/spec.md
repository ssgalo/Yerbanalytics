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
- **THEN** el simulador fija una métrica fuera de rango en cada respuesta de la zona (la del pulso y las relecturas) y se observan la relectura, el descarte y la alerta

#### Scenario: Escenario de hardware incompleto
- **WHEN** el operador elige "Hardware incompleto" para una zona
- **THEN** el simulador fija el sustrato seco que pide riego, busca en el registro de hardware (sólo lectura) los sectores de la zona sin electroválvula registrada, y el siguiente pulso aborta el riego de esos sectores con "hardware incompleto" mientras los que sí la tienen riegan

> **Cambio respecto de la spec original.** Decía que el simulador "da de baja la electroválvula por la API de dispositivos". Esa API
> no existe (`/api/hardware` sólo tiene GET, POST y PUT) y el rol Servicio no tenía permisos de hardware. Se resolvió sin una baja
> destructiva: el rol Servicio recibe sólo `hardware.ver` y el escenario usa los sectores que ya carecen de válvula.

#### Scenario: Bypass de hardware
- **WHEN** se activa el bypass `hardware`
- **THEN** el sector sin electroválvula se evalúa como si estuviera completo
