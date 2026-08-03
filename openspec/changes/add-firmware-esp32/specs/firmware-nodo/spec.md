## ADDED Requirements

### Requirement: Arquitectura concurrente basada en FreeRTOS

El firmware SHALL organizar su ejecución en tareas FreeRTOS concurrentes que se comunican
mediante colas (`QueueHandle_t`), en lugar de un `loop()` secuencial con `delay()`. El
diseño SHALL aprovechar los núcleos del ESP32 asignando prioridades explícitas a cada tarea.

#### Scenario: Comunicación por colas, no por variables compartidas
- **WHEN** una tarea productora (ej. muestreo de sensores o recepción de comando) genera
  un dato para otra tarea
- **THEN** lo entrega por una cola FreeRTOS, sin variables globales mutables compartidas
  entre tareas.

#### Scenario: Prioridades explícitas
- **WHEN** se crean las tareas
- **THEN** cada una recibe una prioridad y un tamaño de stack definidos por constantes
  nombradas (sin números mágicos), coherentes con su criticidad.

### Requirement: Ahorro de energía sin deep sleep

El firmware SHALL lograr el ahorro de energía mediante el scheduling de tareas —
principalmente `vTaskDelay`, que cede el núcleo entre ciclos de trabajo— y NO SHALL usar
deep sleep como modo de operación. El nodo permanece operativo y capaz de atender red y
comandos en todo momento.

#### Scenario: Cesión de CPU entre muestreos
- **WHEN** una tarea termina un ciclo de trabajo (ej. muestreo de sensores)
- **THEN** invoca `vTaskDelay` por el intervalo configurado, cediendo el núcleo al
  planificador en vez de bloquearlo con `delay()`.

#### Scenario: Nodo siempre disponible
- **WHEN** el nodo está entre muestreos
- **THEN** sigue atendiendo la conexión MQTT y (según su tipo) los comandos entrantes, sin
  entrar en suspensión profunda.

### Requirement: Firmwares separados por tipo de nodo (sensor / actuador / combinado)

El firmware SHALL entregarse como tres sketches independientes —`nodo_sensor`,
`nodo_actuador` y `nodo_combinado` (sensado + actuación en el mismo ESP32)—, cada uno con
su propio `setup()` y su tarea de red a medida, **sin condicionales de perfil** en el
código. Cada sketch SHALL compilar únicamente las tareas y periféricos de su rol; el
reparto de fuentes lo hace `build_src_filter` en `platformio.ini` (un `env` por sketch),
no un build flag de perfil. `nodo_combinado` es el usado en el prototipo.

#### Scenario: Nodo combinado para el prototipo
- **WHEN** se compila el `env:nodo_combinado`
- **THEN** el sketch inicializa tanto las tareas de sensado como las de actuación sobre un
  mismo ESP32.

#### Scenario: Nodo solo-sensor
- **WHEN** se compila el `env:nodo_sensor`
- **THEN** la build incluye solo `comun/` + `sensado/` y el sketch arranca únicamente el
  subsistema de sensado, sin compilar ni inicializar tareas ni drivers de actuación.

#### Scenario: Código compartido sin duplicación
- **WHEN** existen los tres firmwares
- **THEN** comparten el mismo `comun/` (config, contrato, red, tipos) y los mismos drivers
  (`sensado/`, `actuacion/`) vía `build_src_filter`, sin copiar lógica entre firmwares:
  solo el `.ino` de arranque es propio de cada nodo.

### Requirement: Conectividad WiFi/MQTT con reconexión

El firmware SHALL gestionar la conexión WiFi y MQTT en una tarea dedicada que reconecta
automáticamente ante caídas y mantiene vivo el cliente MQTT (`loop()`), de modo que las
tareas de sensado/actuación no se bloqueen por la red.

#### Scenario: Reconexión transparente
- **WHEN** se pierde la conexión WiFi o al broker
- **THEN** la tarea de red reintenta la conexión sin frenar el resto del sistema, y al
  reconectar reanuda publicaciones y suscripciones.

### Requirement: Configuración centralizada y sin secretos versionados

El firmware SHALL centralizar todos los parámetros ajustables (credenciales, broker,
`zonaId`, `sectorId`, pines, intervalos, flags) en un `config.h`. El repo SHALL
versionar un `config.example.h` con placeholders y excluir el `config.h` real por
`.gitignore`, coherente con la convención `env.example` del proyecto.

#### Scenario: Un solo lugar para recalibrar
- **WHEN** se necesita cambiar el intervalo de muestreo, un pin o el `sectorId`
- **THEN** el cambio se hace en `config.h` sin tocar la lógica de los módulos.

#### Scenario: Credenciales fuera de Git
- **WHEN** se clona el repo
- **THEN** existe `config.example.h` con placeholders y el `config.h` real está ignorado,
  evitando commitear SSID/passwords/broker.
