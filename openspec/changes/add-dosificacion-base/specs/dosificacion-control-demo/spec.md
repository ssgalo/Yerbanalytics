## ADDED Requirements

### Requirement: Control de las aplicaciones desde el simulador
Las precondiciones de aplicación (`ApplicationPreconditions`) SHALL ser un `DemoControllable`; apagado, NO SHALL ejecutarse ninguna
aplicación. SHALL declarar los bypass `diagnosis` (P-D1), `daily` (una aplicación por día), `window` y `temperature` (P-D3), `rain` (P-D4),
`moisture` (P-D5) y `stock`, y los cooldowns `interval-fertilizer` e `interval-fungicide` (minutos; 0 los elimina).

#### Scenario: Ventana ignorada
- **WHEN** se activa el bypass `window` a las 12:00 y una regla pide una aplicación
- **THEN** la aplicación procede aunque esté fuera de 07–10 y 17–19

#### Scenario: Intervalo en cero
- **WHEN** se fija `interval-fungicide` en 0 tras una aplicación reciente
- **THEN** una nueva aplicación de fitosanitario no se difiere por el intervalo

#### Scenario: Apagar las aplicaciones
- **WHEN** se apaga `ApplicationPreconditions` y una regla pide fertilizante
- **THEN** no se ejecuta ninguna aplicación y queda constancia en el registro

#### Scenario: Escenario modificador
- **WHEN** el operador marca "Precondiciones de aplicación" junto a una regla que aplica insumos
- **THEN** el simulador publica el clima y las lecturas (temperatura, humedad, lluvia) que cumplen todas las precondiciones

#### Scenario: Sustitución del escenario viejo
- **WHEN** se retira la dosificación por estado crítico
- **THEN** el escenario "Dosificación de insumo" del simulador se reemplaza por los de las reglas que pueden pedir aplicaciones
