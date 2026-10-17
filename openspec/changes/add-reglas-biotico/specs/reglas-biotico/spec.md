## ADDED Requirements

"Vigente" es el diagnóstico definido en `add-dosificacion-base`. Severidad moderada = `Media`.

### Requirement: F-F1 Daño fúngico
El motor SHALL pedir 100 mL de fitosanitario para el sector y emitir `WARNING` "Alta probabilidad de hongo"
(`CRITICAL` con severidad alta) ante un diagnóstico Daño fúngico vigente cuando se cumpla alguna de estas condiciones:
severidad moderada o alta; sustrato > 80 % o DPV < 0,3 kPa en 2 lecturas seguidas de las últimas 24 h; el diagnóstico
en 3 capturas diarias seguidas. Si no se cumple ninguna, SHALL esperar a las próximas capturas.

#### Scenario: Severidad moderada
- **WHEN** S-045 tiene Daño fúngico vigente con severidad Media y ambiente seco
- **THEN** se pide fitosanitario y queda un `WARNING`

#### Scenario: Hongo leve con ambiente seco
- **WHEN** el diagnóstico es Daño fúngico, severidad Baja, sustrato 55 % y DPV 0,9 kPa, primera captura
- **THEN** no se aplica nada

#### Scenario: Leve pero húmedo
- **WHEN** severidad Baja y el DPV fue 0,22 y 0,25 kPa en las dos últimas lecturas
- **THEN** se pide fitosanitario

#### Scenario: Persiste
- **WHEN** el mismo diagnóstico aparece en tres capturas de días distintos seguidos
- **THEN** se pide fitosanitario aunque sea leve y el ambiente seco

#### Scenario: Severidad alta
- **WHEN** la severidad es Alta
- **THEN** la alerta es `CRITICAL`

### Requirement: F-F2 Condición predisponente
Con DPV < 0,3 kPa en 2 lecturas seguidas, temperatura entre 18 y 28 °C y sustrato ≥ 75 %, el motor SHALL emitir
`WARNING` "Condiciones favorables a enfermedades fúngicas" y NO SHALL aplicar nada. Funciona sin diagnóstico.

#### Scenario: Se cumplen las tres
- **WHEN** DPV 0,2 y 0,25, 24 °C y sustrato 78 %
- **THEN** queda el `WARNING` y no se aplica ningún insumo

#### Scenario: Frío
- **WHEN** la temperatura es 15 °C
- **THEN** no actúa

#### Scenario: No repite
- **WHEN** la condición sigue en la lectura siguiente
- **THEN** no se emite otra alerta

### Requirement: F-P1 Plaga foliar
El motor SHALL pedir 100 mL de fitosanitario, con `WARNING` (`CRITICAL` si la severidad es alta), ante un diagnóstico
Plaga foliar (o Ácaro) vigente cuando se cumpla alguna condición: severidad moderada o alta; DPV ≥ 2,0 kPa en alguna
lectura de las últimas 72 h; el diagnóstico en 3 capturas seguidas. Si no se cumple ninguna, SHALL emitir `INFO` y esperar.

#### Scenario: Calor seco
- **WHEN** severidad Baja y el DPV llegó a 2,3 kPa hace 30 h
- **THEN** se pide fitosanitario

#### Scenario: Nada que justifique
- **WHEN** severidad Baja, DPV máximo 1,1 kPa y primera captura
- **THEN** queda un `INFO` y no se aplica

### Requirement: F-04 Foco
Si 3 o más sectores de la misma MZ tienen el mismo diagnóstico biótico vigente dentro de 72 h, el motor SHALL pedir
fitosanitario también para los 8 sectores lindantes de cada afectado, aunque estén sanos, respetando las
precondiciones salvo P-D1, y emitir `CRITICAL` "Foco de [diagnóstico] en MZ-N".

#### Scenario: Tres sectores
- **WHEN** S-010, S-011 y S-021 de MZ-1 tienen Daño fúngico vigente en 72 h
- **THEN** se pide fitosanitario para sus lindantes y queda el `CRITICAL`

#### Scenario: Dos sectores
- **WHEN** sólo 2 sectores tienen el diagnóstico
- **THEN** no hay foco

#### Scenario: Diagnósticos distintos
- **WHEN** hay 2 con Daño fúngico y 1 con Plaga foliar
- **THEN** no hay foco

#### Scenario: Borde de la zona
- **WHEN** un afectado está en el borde de la MZ
- **THEN** sólo se tratan los lindantes que pertenecen a la misma MZ

### Requirement: F-06 Tope de aplicaciones
El sistema SHALL bloquear la siguiente aplicación de fitosanitario en un sector que ya recibió 3 en 30 días, hasta
que alguien registre la revisión, y emitir `WARNING`.

#### Scenario: Cuarta aplicación
- **WHEN** S-045 recibió 3 fitosanitarios en 25 días y se pide otro
- **THEN** se rechaza con motivo "tope" y queda el `WARNING`

#### Scenario: Revisión registrada
- **WHEN** un usuario registra la revisión de S-045
- **THEN** el bloqueo se levanta

### Requirement: Un diagnóstico, una aplicación
Las reglas de este cambio SHALL consumir el diagnóstico sólo cuando la aplicación se ejecuta, no cuando se pide.

#### Scenario: Aplicación diferida
- **WHEN** F-F1 pide fitosanitario a las 12:00 y queda diferido a las 17:00
- **THEN** el diagnóstico no se marca como usado hasta las 17:00
