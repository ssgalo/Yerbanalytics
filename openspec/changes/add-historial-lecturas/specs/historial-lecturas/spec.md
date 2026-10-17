## ADDED Requirements

### Requirement: Registro de cada lectura aceptada
El sistema SHALL guardar una fila por cada lectura aceptada de una macro-zona, con las diez métricas vigentes después de
aplicarla, la hora del nodo y el origen de la lectura.

#### Scenario: Lectura completa
- **WHEN** MZ-1 envía una lectura con las diez métricas
- **THEN** se guarda una fila con esos valores y origen `PULSO` si respondía a un pulso

#### Scenario: Lectura parcial
- **WHEN** MZ-1 envía sólo humedad de sustrato y la temperatura vigente era 24 °C
- **THEN** la fila guarda la humedad nueva y 24 °C en temperatura

#### Scenario: Lectura anterior a la última
- **WHEN** llega una lectura con hora anterior a la última guardada
- **THEN** no se guarda en el historial

#### Scenario: CE en dS/m
- **WHEN** llega `ce` = 800 µS/cm
- **THEN** la fila guarda 0,8 dS/m

### Requirement: Consultas del historial
El sistema SHALL permitir obtener las últimas N lecturas de una zona y el máximo, el mínimo y la media de una métrica en una
ventana de tiempo, con filtro opcional por franja horaria local, devolviendo "sin dato" y nunca cero cuando no hay lecturas.

#### Scenario: Media de 6 lecturas de pulso
- **WHEN** hay 6 lecturas de pulso de CE en 24 h y una lectura de verificación entre ellas
- **THEN** la media usa las 6 de pulso y no la de verificación

#### Scenario: Máximo de ventana
- **WHEN** la luz máxima entre las 10:00 y las 16:00 de las últimas 48 h fue 88 %
- **THEN** el máximo consultado con esa franja es 88

#### Scenario: Sin lecturas
- **WHEN** la zona no tiene lecturas en la ventana
- **THEN** la consulta devuelve "sin dato"

#### Scenario: Pocas lecturas
- **WHEN** hay 4 lecturas y la regla exige 6
- **THEN** el conteo permite a la regla registrar "sin dato"

### Requirement: Retención del historial
El sistema SHALL conservar las lecturas durante `lecturas.retencion-dias` (fábrica 14 días) y SHALL borrar las más viejas
con una tarea diaria.

#### Scenario: Purga
- **WHEN** corre la tarea y hay lecturas de hace 20 días
- **THEN** se borran y las de hace 5 días se conservan

#### Scenario: Valor inválido
- **WHEN** se intenta guardar una retención de 1 día
- **THEN** se rechaza por estar bajo el mínimo de 3 días

### Requirement: Atomicidad con la lectura
El guardado del historial SHALL ocurrir en la misma transacción que la actualización de la zona.

#### Scenario: Falla el guardado
- **WHEN** falla la escritura de la serie
- **THEN** la lectura de la zona tampoco se aplica
