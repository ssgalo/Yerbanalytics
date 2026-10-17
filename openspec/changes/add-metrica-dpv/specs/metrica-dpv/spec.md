## ADDED Requirements

### Requirement: Cálculo del DPV
El sistema SHALL calcular, con cada lectura de una macro-zona, el déficit de presión de vapor
`DPV = 0,6108 · e^(17,27·T/(T+237,3)) · (1 − HR/100)` en kPa, con `T` la temperatura del aire en °C y `HR`
la humedad ambiental en %.

#### Scenario: Valor de referencia
- **WHEN** la lectura trae T = 25 °C y HR = 60 %
- **THEN** el DPV es 1,27 kPa

#### Scenario: Aire saturado
- **WHEN** la lectura trae HR = 100 %
- **THEN** el DPV es 0 kPa

#### Scenario: Falta una entrada
- **WHEN** la lectura no trae humedad ambiental
- **THEN** no se calcula un DPV nuevo y se conserva el anterior con su marca de tiempo

#### Scenario: Humedad fuera de rango
- **WHEN** HR vale 130
- **THEN** el DPV no se calcula

### Requirement: Persistencia e historial del DPV
El DPV SHALL guardarse en la zona como una métrica más, con su marca de tiempo, y SHALL poder consultarse
su historial para las reglas que piden lecturas seguidas o un máximo en una ventana.

#### Scenario: Dos lecturas seguidas bajo 0,3
- **WHEN** las dos últimas lecturas de MZ-1 dan 0,21 y 0,25 kPa
- **THEN** `ultimasLecturas(MZ-1, 2)` devuelve ambas, en orden cronológico

#### Scenario: Máximo de 72 h
- **WHEN** en las últimas 72 h el mayor DPV fue 2,3 kPa
- **THEN** `ReadingHistory.max` devuelve 2,3

### Requirement: Presentación del DPV
El dashboard SHALL mostrar el DPV junto a las demás métricas de la macro-zona, con su tramo
(saturado, confortable, demanda alta, calor seco) y SHALL tratarlo como informativo, sin alterar el estado de salud.

#### Scenario: Calor seco
- **WHEN** el DPV de una zona es 2,2 kPa
- **THEN** se muestra con la etiqueta "calor seco" y el estado de salud de sus sectores no cambia por eso

#### Scenario: Sin dato
- **WHEN** la zona nunca tuvo temperatura o humedad ambiental
- **THEN** se muestra "—" y no un cero
