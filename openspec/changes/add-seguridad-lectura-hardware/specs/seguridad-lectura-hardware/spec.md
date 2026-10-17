## ADDED Requirements

### Requirement: S-03 Validación de rango físico
Al ingerir una lectura, el sistema SHALL validar cada métrica contra su rango físico del catálogo y NO SHALL
usar un valor fuera de rango en ninguna regla.

#### Scenario: Humedad imposible
- **WHEN** llega humedad de sustrato 112 %
- **THEN** la métrica se marca inválida y se pide la lectura de nuevo

#### Scenario: Valor en el borde
- **WHEN** llega humedad de sustrato 100 %
- **THEN** la métrica es válida

#### Scenario: CE convertida
- **WHEN** llega `ce` = 12 000 µS/cm (12 dS/m)
- **THEN** se valida como 12 dS/m y resulta inválida (máximo 10)

### Requirement: S-03 Relectura y descarte
Ante una métrica inválida el sistema SHALL pedir la lectura de nuevo enseguida, hasta
`seguridad.relecturas-invalidas` veces. Si alguna relectura es válida, SHALL usarla sin alertar. Si todas
fallan, SHALL descartar sólo esa métrica en el ciclo.

#### Scenario: La relectura corrige
- **WHEN** el primer valor de pH es 11 y la relectura da 5,4
- **THEN** se usa 5,4 y no queda ninguna alerta

#### Scenario: Tres valores inválidos
- **WHEN** la humedad de sustrato es inválida en la lectura y en sus dos relecturas
- **THEN** R-01 y R-02 no actúan en el ciclo, la traza muestra "sin dato" para esa métrica, y las reglas que
  usan temperatura siguen evaluándose

### Requirement: S-03 Escalamiento de alerta
La primera falla de una métrica en un ciclo SHALL dejar una alerta `WARNING` "Lectura inválida en [métrica]".
Si la misma métrica vuelve a fallar en el ciclo siguiente, SHALL dejar `CRITICAL` "Sensor de [métrica] con
falla: revisar". Una lectura válida SHALL reiniciar la cuenta.

#### Scenario: Dos ciclos seguidos
- **WHEN** la temperatura falla en el ciclo de las 10:00 y otra vez en el de las 14:00
- **THEN** quedan un `WARNING` a las 10:00 y un `CRITICAL` a las 14:00

#### Scenario: Se recupera
- **WHEN** la temperatura falla a las 10:00 y es válida a las 14:00
- **THEN** sólo existe el `WARNING` de las 10:00

### Requirement: S-04 Hardware incompleto
El motor SHALL abortar las acciones autónomas de una rama cuando falte mapear un actuador que esa rama
requiere para el sector (o la MZ, en la mediasombra), sin emitir alertas nuevas.

#### Scenario: Sector sin electroválvula
- **WHEN** S-120 no tiene electroválvula registrada y R-01 aplicaría
- **THEN** el riego de S-120 se aborta con motivo "hardware incompleto" y no se emite alerta

#### Scenario: Sector sin bomba
- **WHEN** S-120 no tiene bomba y la humedad pide riego
- **THEN** el riego de S-120 sí procede; sólo la rama de insumos se aborta

#### Scenario: Hardware completo
- **WHEN** el sector tiene todo lo requerido
- **THEN** la regla registra "completo" y la cadena sigue
