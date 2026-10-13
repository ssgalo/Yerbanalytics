## ADDED Requirements

### Requirement: Punto de alerta por métrica
Cada umbral de métrica SHALL tener `alertMin` y `alertMax`, persistidos en `umbral_metrica` como
columnas nulables. Sin valor guardado (o si el payload no los trae) SHALL valer el punto medio de su
franja amarilla, redondeado a los decimales de la métrica. Al guardar SHALL cumplirse
`warnMin ≤ alertMin ≤ idealMin` e `idealMax ≤ alertMax ≤ warnMax`; si no, el backend rechaza con 400.
Los puntos SHALL NOT cambiar el estado de zonas ni sectores.

#### Scenario: Fila sin puntos
- **WHEN** `umbral_metrica` tiene `alert_min` nulo para `humAmb` (warn 52, ideal 62)
- **THEN** `GET /api/configuracion` devuelve `alertMin: 57`

#### Scenario: Punto incoherente
- **WHEN** se guarda `alertMin` menor que `warnMin`
- **THEN** el backend responde 400 con el error de bandas incoherentes

### Requirement: Alertas por umbral de métrica
El snapshot del vivero SHALL incluir, junto con las alertas por sector, una alerta por cada macro-zona
y métrica que afecta el estado cuya lectura esté en la franja roja (`CRITICAL`) o entre el crítico y
el punto de alerta (`WARNING`). Las métricas informativas SHALL NOT generar alertas. La lista SHALL
ordenarse por severidad y limitarse a 8.

#### Scenario: Lectura más allá del punto de alerta
- **WHEN** la humedad ambiental de MZ-2 es 55 % con crítico 52, alerta 57 e ideal 62
- **THEN** la campana muestra una alerta `WARNING` de MZ-2 por humedad ambiental

#### Scenario: Lectura amarilla antes del punto
- **WHEN** la humedad ambiental de MZ-2 es 60 % con el mismo umbral
- **THEN** MZ-2 está "En observación" y no hay alerta por esa métrica
