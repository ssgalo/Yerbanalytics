## ADDED Requirements

### Requirement: Control de la efectividad desde el simulador
`EfectividadService` y `SinEfectividadRule` SHALL ser `DemoControllable` apagables. `EfectividadService` SHALL tener los cooldowns `verification` (minutos
hasta la lectura de verificación), `fertilizer-latency` y `fungicide-latency` (minutos de latencia de E-02 y E-03, 0 las elimina) y `SinEfectividadRule` el
bypass `block` (ignora el bloqueo) y el cooldown `r02-cap` (tope de R-02 por sector). El simulador SHALL tener los escenarios "Riego sin efecto",
"Riego efectivo" y "Tratamiento sin efecto".

#### Scenario: Riego sin efecto
- **WHEN** el operador elige "Riego sin efecto" y pulsa Disparar
- **THEN** el simulador fija `verification` en 0, provoca un riego y publica una humedad que sube menos de 8 puntos, y R-01 queda bloqueado en la MZ

#### Scenario: Riego efectivo
- **WHEN** el operador elige "Riego efectivo"
- **THEN** la verificación publica una subida de 15 puntos y el riego queda "Efectiva"

#### Scenario: Bypass del bloqueo
- **WHEN** se activa `block` con el riego bloqueado por S-06
- **THEN** R-01 vuelve a regar

#### Scenario: Latencia en cero
- **WHEN** se fija `fungicide-latency` en 0 tras una aplicación
- **THEN** E-03 evalúa en el siguiente ciclo

#### Scenario: Apagar el servicio
- **WHEN** se apaga `EfectividadService`
- **THEN** no se evalúa ninguna acción y no se bloquea nada
