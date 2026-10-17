## ADDED Requirements

### Requirement: Control de la nutrición desde el simulador
`PlanNutricionRule` (N-01) y `CeBajaRule` (N-02) SHALL ser `DemoControllable` apagables; N-01 con el cooldown `interval` (minutos entre aplicaciones
del plan, 0 lo elimina) y N-02 con el cooldown `no-fertilized` (minutos sin fertilizar, 0 lo elimina) y el bypass `samples` (usa la última lectura de CE
como media). El simulador SHALL tener los escenarios "Plan de nutrición" y "CE baja".

#### Scenario: Plan vencido de inmediato
- **WHEN** se fija `interval` en 0 y se pide un pulso
- **THEN** N-01 pide fertilizante para los sectores de la MZ

#### Scenario: Escenario de CE baja
- **WHEN** el operador elige "CE baja" y pulsa Disparar
- **THEN** el simulador activa `samples`, publica CE 0,3 dS/m y fija `no-fertilized` en 0, y N-02 adelanta el plan

#### Scenario: Apagar N-02
- **WHEN** se apaga N-02 con la CE en 0,3 dS/m
- **THEN** no se adelanta el plan
