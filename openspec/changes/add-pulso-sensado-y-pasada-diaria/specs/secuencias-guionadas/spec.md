## ADDED Requirements

### Requirement: Lectura con motor

La secuencia `LECTURA` SHALL aceptar el parámetro opcional `ejecutarMotor` (de fábrica `false`; en `RIEGO` y
`MEDIASOMBRA` se ignora y viaja `null`). Con `ejecutarMotor: true`, el paso `PEDIR` SHALL emitir un pulso de sensado
con motor sólo para la zona de la secuencia (ver `pulso-sensado`) en lugar del `LEER_AHORA` suelto: la telemetría que
responde a ese pulso evalúa el motor de los sectores de la zona en la ingesta y es, a la vez, la que la secuencia
muestra. Si al iniciar hay un pulso en curso, la secuencia SHALL rechazarse con `409` sin publicar nada. Sin
`ejecutarMotor`, la secuencia SHALL comportarse como hasta ahora y no ejecutar el motor.

#### Scenario: Lectura con motor
- **GIVEN** la zona de la secuencia es `MZ-1` y no hay pulso en curso
- **WHEN** se invoca `POST /api/secuencias` con `{"tipo":"LECTURA","parametros":{"ejecutarMotor":true}}`
- **THEN** se emite un pulso con motor sólo para `MZ-1`; al llegar su telemetría, la secuencia la muestra y termina `COMPLETADA` y los sectores de `MZ-1` se evalúan una vez

#### Scenario: Pulso en curso
- **GIVEN** un pulso en curso
- **WHEN** se pide una lectura con motor
- **THEN** el backend responde `409` y no publica nada

#### Scenario: Lectura simple
- **WHEN** se pide una `LECTURA` sin `ejecutarMotor`
- **THEN** se publica un `LEER_AHORA` a la zona y el motor no se evalúa
