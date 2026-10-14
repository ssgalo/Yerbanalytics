## ADDED Requirements

### Requirement: El diagnóstico del sector sólo lo cambia el alta

Los campos de diagnóstico del sector (estado, confianza, severidad) SHALL cambiar únicamente al dar de alta un
diagnóstico de ese sector. El alta SHALL registrar además en el sector la hora de llegada del diagnóstico, que el
motor usa para saber si es nuevo. La ingesta de telemetría y el cálculo del estado del sector NO SHALL escribir
ningún valor de diagnóstico.

#### Scenario: Lectura de la zona no toca el diagnóstico
- **GIVEN** `MZ-1-001` con un diagnóstico "Clorosis" de confianza 91
- **WHEN** llega una lectura de `MZ-1` con todas las métricas en rango
- **THEN** el sector queda en estado `ok` y su diagnóstico sigue siendo "Clorosis" de confianza 91

#### Scenario: Sector sin diagnóstico
- **GIVEN** un sector que nunca recibió un diagnóstico
- **WHEN** llega una lectura de su zona
- **THEN** su diagnóstico sigue siendo "Sin diagnóstico", sin confianza

#### Scenario: Alta registra la hora de llegada
- **WHEN** se da de alta un diagnóstico de `MZ-1-001`
- **THEN** el sector toma el estado, la confianza, la severidad y la hora de llegada de ese diagnóstico
