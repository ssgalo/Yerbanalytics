## MODIFIED Requirements

### Requirement: Obtención del pronóstico con retry y fallback

El cliente de pronóstico SHALL pedir a Open-Meteo, por hora, probabilidad de precipitación y
**precipitación en mm**, además de UV, temperatura, humedad relativa y código WMO, en la zona horaria
`America/Argentina/Buenos_Aires`. El pronóstico SHALL exponer las horas siguientes (hasta 24) y
calcular, para una ventana de N horas desde la hora actual, la probabilidad horaria máxima y la
lluvia acumulada. Ante cualquier falla SHALL devolver `null` tras los reintentos (modo degradado),
como hasta ahora.

#### Scenario: Ventana de 4 h
- **WHEN** son las 10:20 y las marcas 11:00, 12:00, 13:00 y 14:00 traen (40 %, 0 mm), (70 %, 2 mm),
  (55 %, 3 mm), (20 %, 0 mm) y la de 15:00 trae (90 %, 10 mm)
- **THEN** la lluvia prevista en 4 h es probabilidad máxima 70 % y 5 mm

#### Scenario: Hora actual con el JVM en otra zona
- **WHEN** el JVM corre en UTC y son las 13:30 UTC
- **THEN** la hora actual del pronóstico es la marca de las 10:00 locales

### Requirement: Postergación de riego por lluvia inminente

La postergación del riego por lluvia SHALL ser la regla R-03 (`PosponerPorLluviaRule`, ver
`reglas-riego`): sólo cuando aplica R-01, con probabilidad **y** milímetros en la ventana
configurada. NO SHALL postergar un riego por déficit crítico (R-02). Sin pronóstico SHALL no
postergar.

#### Scenario: Lluvia no pospone el déficit crítico
- **WHEN** la humedad es 30 % y el pronóstico da 95 % y 20 mm en las próximas 4 h
- **THEN** el sector se riega igual
