# simulacion-manual

## Purpose

Envio manual de telemetria desde el dashboard de simulacion.

## Requirements

### Requirement: App de simulación standalone en puerto propio
El panel de simulación SHALL servirse como una aplicación separada en su propio puerto
(`localhost:5180`), aislada del dashboard principal, y NO SHALL exponerse como una pestaña
ni una ruta dentro de la navegación del dashboard principal. La raíz del puerto (`/`) SHALL
abrir directamente el panel de simulación.

#### Scenario: La simulación no aparece en el dashboard principal
- **WHEN** el usuario navega el dashboard principal (`:5173`)
- **THEN** no hay ítem de "Simulación" en el sidebar ni ruta `/simulacion` accesible

#### Scenario: La app de simulación corre en su puerto
- **WHEN** el usuario abre `localhost:5180`
- **THEN** ve únicamente el panel de simulación (no el dashboard principal), con su propio
  encabezado y sin el sidebar del dashboard principal

### Requirement: Switch de modo que refleja el modo persistido
La app de simulación SHALL ofrecer un switch para alternar entre el modo de datos estáticos
y el modo de simulación, reflejando el modo **persistido** en el backend, y SHALL habilitar
los controles de envío sólo en modo simulación. Al recargar la app, el switch NO SHALL
reiniciarse a estático: SHALL mostrar el último modo elegido.

#### Scenario: Alternar a simulación
- **WHEN** el usuario pasa el switch a "Simulación"
- **THEN** la vista pide el cambio de modo al backend y habilita el alta de sensores y el
  envío de lecturas

#### Scenario: El switch conserva el modo tras recargar
- **WHEN** el usuario pasa a simulación y luego recarga la app de simulación
- **THEN** el switch sigue en simulación

#### Scenario: Modo estático deshabilita el envío
- **WHEN** el modo vigente es "Estático"
- **THEN** los controles de envío quedan deshabilitados con una nota que explica que hay que
  activar la simulación

### Requirement: Crear y asignar sensores simulados
La vista SHALL permitir crear un sensor simulado por serial/MAC y asignarlo a una macro-zona
**de la topología vigente del vivero**, SHALL mantener los sensores simulados en una lista
propia **separada del registro de hardware** (crear un sensor simulado NO SHALL registrarlo
como dispositivo del sistema), y SHALL listar los sensores simulados disponibles para enviar
lecturas.

#### Scenario: Las macro-zonas ofrecidas son las de la topología vigente
- **WHEN** el usuario abre el alta de sensor simulado
- **THEN** el desplegable de macro-zona ofrece exactamente las macro-zonas de la topología
  actual (por defecto las 6 del seed)

#### Scenario: La topología cambia
- **WHEN** la topología se regenera agregando o quitando macro-zonas/sectores
- **THEN** las opciones de macro-zona del alta reflejan la topología nueva, sin ofrecer
  zonas que dejaron de existir

#### Scenario: Alta de un sensor simulado
- **WHEN** el usuario da de alta un sensor con un serial/MAC y una macro-zona
- **THEN** el sensor simulado queda en la lista de sensores de la simulación y aparece para
  enviar lecturas
- **AND** NO se crea un dispositivo en el registro de hardware del sistema

#### Scenario: Serial/MAC ya usado
- **WHEN** el alta usa un serial/MAC que ya tiene un sensor simulado
- **THEN** la vista muestra el error devuelto por el backend y no agrega el sensor

### Requirement: Envío de lecturas por sensor con fecha/hora opcional
Cada sensor simulado listado SHALL permitir enviar **cada métrica por separado** —cada una
con su propio valor y su propia fecha/hora opcional— y también **enviar todas las métricas a
la vez**; en cualquier envío, si no se indica fecha/hora, la lectura SHALL enviarse con la
fecha/hora actual.

#### Scenario: Envío de una sola métrica
- **WHEN** el usuario carga el valor de una sola métrica (p. ej. radiación) y pulsa el
  Enviar de esa métrica
- **THEN** la vista envía únicamente esa métrica y las demás no se modifican en el sector

#### Scenario: Envío conjunto de todas las métricas
- **WHEN** el usuario carga valores y pulsa "Enviar todo"
- **THEN** la vista envía las cinco métricas en una sola lectura

#### Scenario: Envío con la hora actual
- **WHEN** el usuario deja la fecha/hora vacía y envía (una métrica o todas)
- **THEN** la vista envía la lectura sin `timestamp` y el backend la sella con la hora actual

#### Scenario: Envío con fecha/hora elegida
- **WHEN** el usuario carga una fecha/hora y envía
- **THEN** la vista convierte esa fecha/hora a epoch ms y la envía con la lectura

#### Scenario: Reflejo del envío en el detalle del sector
- **WHEN** una lectura (parcial o completa) se envía correctamente
- **THEN** las métricas enviadas de los sectores de la macro-zona del sensor se actualizan,
  conservando las no enviadas, y el detalle de cada sector muestra los valores sensados

### Requirement: Regenerar la topología desde el flujo de simulación
En modo simulación, la app de simulación SHALL ofrecer un control para regenerar la
topología del vivero (N macro-zonas × M sectores) para que coincida con el hardware a
simular. La regeneración SHALL dejar cada sector **sin valores históricos** (offline, con
sus lecturas en `null`) y SHALL requerir confirmación cuando ya existe una topología
cargada. En modo estático el control NO SHALL estar disponible.

#### Scenario: Regenerar deja los sectores sin históricos
- **WHEN** el usuario regenera la topología desde el flujo de simulación
- **THEN** los sectores de la nueva grilla quedan offline, sin lecturas previas, y al entrar
  a un sector no se muestran valores históricos

#### Scenario: Confirmación al reemplazar una topología existente
- **WHEN** el usuario pide regenerar y ya hay una topología cargada
- **THEN** la vista pide confirmación antes de reemplazarla

#### Scenario: Regeneración no disponible en estático
- **WHEN** el modo vigente es estático
- **THEN** el control de regenerar topología no está disponible
