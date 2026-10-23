## ADDED Requirements

> **Implementado sin F-S2.** `SolarStressWideRule`, sus llaves `protection` y `quorum` y el escenario «Estrés solar extendido» no existen (ver el `design.md` del cambio). F-C2 no declara el cooldown `interval`: el intervalo mínimo lo respeta P-D2 y se acorta con `interval-fertilizer` de `ApplicationPreconditions`.

### Requirement: Control de clorosis y estrés solar desde el simulador
`ChlorosisNonNutritionalRule`, `ChlorosisNutritionalRule`, `SolarStressRule`, `SolarStressUnexplainedRule` y `SolarStressWideRule` SHALL ser
`DemoControllable` apagables. F-C2 SHALL tener el cooldown `interval` (minutos entre fertilizaciones correctivas), F-S1 el bypass `window` (ignora la
ventana de riego), F-S1/F-S3 el bypass `samples` (usa la última lectura en lugar de las 48 h) y F-S2 el cooldown `protection` (minutos de la protección,
3 días de fábrica) y el bypass `quorum` (basta un sector para el 10 %). El simulador SHALL tener un escenario por regla.

#### Scenario: Clorosis nutricional
- **WHEN** el operador elige "Clorosis nutricional" y pulsa Disparar
- **THEN** el simulador registra un diagnóstico Clorosis con lecturas normales de sustrato, CE y pH, y se pide fertilizante

#### Scenario: Clorosis por pH
- **WHEN** el operador elige "Clorosis no nutricional" y pulsa Disparar
- **THEN** el simulador registra el diagnóstico y publica pH 6,8, y no se aplica fertilizante

#### Scenario: Estrés solar extendido
- **WHEN** el operador elige "Estrés solar extendido" y pulsa Disparar
- **THEN** el simulador activa `quorum` y `samples`, registra Estrés solar con luz 90 % y la malla de la MZ se cierra de 11 a 16 h

#### Scenario: Estrés sin radiación
- **WHEN** el operador elige "Estrés solar sin radiación"
- **THEN** el simulador publica luz 40 % y 24 °C y sólo se emite el aviso

#### Scenario: Apagar F-S2
- **WHEN** se apaga `SolarStressWideRule`
- **THEN** la malla no se cierra aunque el 10 % de los sectores cumpla F-S1
