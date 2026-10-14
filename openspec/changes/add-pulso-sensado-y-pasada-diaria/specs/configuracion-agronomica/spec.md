## ADDED Requirements

### Requirement: Editor del pulso de sensado

El bloque de intervalos de la configuración SHALL presentar `intervaloSensadoMinutos` como "Intervalo de sensado y
evaluación" (60 a 360 min), con una ayuda que diga que en cada franja, anclada a las 02:00, se leen los sensores de
todas las macro-zonas y se evalúa el motor, y que muestre el umbral de lectura vieja que devolvió el backend
(`effectiveStaleThresholdSeconds`; el navegador no calcula la fórmula). Es el único intervalo de sensado y
evaluación de la vista: el "Intervalo de Evaluación (IA/Watchdog)" se quita. Se guarda con
`DataRepository.saveConfig()` junto con el resto de la configuración, en modo `mock` y `http`.

#### Scenario: Valor actual
- **GIVEN** un intervalo guardado de 240 min
- **WHEN** el usuario abre la configuración
- **THEN** el campo muestra 240 y la ayuda muestra el umbral de lectura vieja informado por el backend

#### Scenario: Fuera de rango
- **WHEN** el usuario ingresa 30 o 400
- **THEN** el campo queda inválido y Guardar se deshabilita

#### Scenario: Sin intervalo de evaluación aparte
- **WHEN** el usuario abre el bloque de intervalos
- **THEN** no hay un campo de intervalo de evaluación ni de pedido de lectura en segundos

### Requirement: Editor de la pasada diaria

La configuración SHALL incluir un bloque "Pasada diaria del riel" con un interruptor (`pasadaDiariaActiva`) y una
hora (`pasadaDiariaHora`, `HH:mm`), con una ayuda que diga que la pasada fotografía los sectores del recorrido una
vez al día y que se puede pedir a mano desde Demo Expo. La hora SHALL editarse aunque la pasada esté desactivada y
SHALL guardarse con `DataRepository.saveConfig()`, en modo `mock` y `http`.

#### Scenario: Activar la pasada
- **GIVEN** la pasada desactivada
- **WHEN** el usuario la activa, elige "10:30" y guarda
- **THEN** `saveConfig()` recibe `pasadaDiariaActiva: true` y `pasadaDiariaHora: "10:30"`

#### Scenario: Hora vacía
- **WHEN** el usuario borra la hora
- **THEN** el campo queda inválido y Guardar se deshabilita

## REMOVED Requirements

### Requirement: Sensing pull interval editor
**Reason**: El pedido de lectura en segundos deja de existir; el intervalo de sensado es el del pulso.
**Migration**: El campo "Frecuencia de pedido de lectura a los nodos" se quita de la vista, del tipo
`ConfigOperativa`, de la validación y del mock. El umbral de lectura vieja pasa a mostrarse junto al intervalo de
sensado y evaluación.
