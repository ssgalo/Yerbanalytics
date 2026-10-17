## ADDED Requirements

### Requirement: Control de las reglas bióticas desde el simulador
`DanoFungicoRule`, `PlagaFoliarRule`, `CondicionFungicaRule` y `FocoRule` SHALL ser `DemoControllable` apagables. F-F1 y F-P1 SHALL tener el
bypass `persistence` (toma el diagnóstico como visto en 3 capturas seguidas), F-F2 el bypass `readings` (basta una lectura de DPV bajo el umbral),
F-04 el cooldown `window` (horas de la ventana de foco, en minutos) y el tope de fitosanitarios (F-06) el bypass `cap`. El simulador SHALL tener
un escenario por regla.

#### Scenario: Hongo leve que persiste
- **WHEN** el operador elige "Daño fúngico persistente" y pulsa Disparar
- **THEN** el simulador registra un diagnóstico Daño fúngico severidad Baja, activa `persistence` y el siguiente pulso pide fitosanitario

#### Scenario: Plaga con calor seco
- **WHEN** el operador elige "Plaga foliar" y pulsa Disparar
- **THEN** el simulador registra el diagnóstico y publica humedad ambiental 25 % y 38 °C (DPV ≥ 2 kPa) y se pide fitosanitario

#### Scenario: Foco
- **WHEN** el operador elige "Foco" y pulsa Disparar
- **THEN** el simulador registra el mismo diagnóstico biótico en tres sectores de la MZ y se piden aplicaciones a sus lindantes

#### Scenario: Tope
- **WHEN** se activa `cap` con un sector que ya recibió tres fitosanitarios en 30 días
- **THEN** la cuarta aplicación procede

#### Scenario: Apagar una regla
- **WHEN** se apaga `FocoRule` con tres sectores afectados
- **THEN** no se tratan los lindantes
