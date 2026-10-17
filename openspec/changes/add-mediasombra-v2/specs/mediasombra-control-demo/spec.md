## ADDED Requirements

### Requirement: Control de la mediasombra desde el simulador
M-01 y M-02 SHALL ser `DemoControllable` apagables. M-01 SHALL tener los bypass `force-open` y `force-closed` (toma el horario como si la franja
actual fuese abierta o cerrada) y M-02 el cooldown `hold` (minutos que se mantiene cerrada, 0 lo elimina) y el bypass `window` (ignora la
franja 11:00–16:00). El simulador SHALL tener los escenarios "Horario de mediasombra" y "Calor extremo".

#### Scenario: Forzar apertura
- **WHEN** se activa `force-open` en M-01 a las 22:00 y se pide un pulso
- **THEN** se ordena abrir la malla de la MZ y la traza indica el bypass

#### Scenario: Escenario de calor extremo
- **WHEN** el operador elige "Calor extremo" y pulsa Disparar fuera de las 11:00–16:00
- **THEN** el simulador activa `window`, publica 36 °C y luz 90 % e inyecta clima de 36 °C, y la malla se cierra

#### Scenario: Apagar M-02
- **WHEN** se apaga M-02 con 37 °C a las 13:00
- **THEN** la malla no se cierra por protección

#### Scenario: Cierre sostenido
- **WHEN** se fija `hold` en 0 y la temperatura baja tras un cierre
- **THEN** la malla puede reabrirse de inmediato
