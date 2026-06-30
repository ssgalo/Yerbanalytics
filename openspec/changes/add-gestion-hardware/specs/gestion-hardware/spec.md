# Spec: gestion-hardware

## ADDED Requirements

### Requirement: Panel de estado técnico de la flota
La vista Hardware SHALL listar los dispositivos registrados mostrando, por cada uno, su
tipo, el sector o macro-zona asociado, el nivel de batería, la calidad de señal, la marca
del último update y su estado operativo (Operativo / Señal intermitente / Fuera de
servicio) (HU-21 CA-01).

#### Scenario: Render de la flota
- **WHEN** el Administrador abre `/hardware`
- **THEN** ve la lista de dispositivos con su tipo, ubicación, batería, señal, último
  update y estado

#### Scenario: Indicadores de la flota
- **WHEN** se renderiza el panel
- **THEN** muestra los KPIs de la flota (total, operativos, batería baja, fuera de
  servicio, averiados)

### Requirement: Señalización de batería baja y equipos caídos
La vista SHALL resaltar los dispositivos con batería bajo el umbral como "Batería Baja" y
los que superan el umbral crítico sin reportar como "Fuera de Servicio", y SHALL mostrar
la falla física de los equipos averiados asociada a su sector (HU-21 CA-02/CA-03/CA-04).

#### Scenario: Nodo con batería baja
- **WHEN** un nodo reporta batería bajo el umbral
- **THEN** la vista lo marca como "Batería Baja"

#### Scenario: Equipo averiado
- **WHEN** un dispositivo tiene una falla física
- **THEN** la vista muestra la avería asociada a su sector y ofrece su recambio

### Requirement: Filtros de la flota
La vista SHALL permitir filtrar los dispositivos por tipo, estado operativo y macro-zona.

#### Scenario: Filtrar por estado
- **WHEN** el usuario elige un estado (p. ej. "Fuera de servicio")
- **THEN** la lista muestra sólo los dispositivos en ese estado

### Requirement: Alta y mapeo de dispositivos con validación en cliente
La vista SHALL permitir dar de alta un dispositivo indicando su serial/MAC, su tipo y su
sector o macro-zona, validando en cliente antes de enviar (serial obligatorio, ubicación
acorde al tipo) y mostrando el conflicto cuando el backend rechaza un duplicado (HU-18
CA-02/CA-03).

#### Scenario: Alta válida
- **WHEN** el usuario completa un alta válida y la confirma
- **THEN** la vista registra el dispositivo vía `DataRepository.registerDevice()` y lo
  muestra en la flota

#### Scenario: Alta duplicada rechazada
- **WHEN** el backend rechaza el alta por serial/MAC duplicado o actuador repetido
- **THEN** la vista muestra el mensaje de conflicto sin agregar el dispositivo

### Requirement: Sectores con mapeo incompleto
La vista SHALL mostrar los sectores con mapeo de hardware incompleto, indicando los
actuadores faltantes y que la actuación autónoma permanece deshabilitada en ellos (HU-18
CA-04).

#### Scenario: Listado de sectores incompletos
- **WHEN** existen sectores parcialmente aprovisionados
- **THEN** la vista los lista con los actuadores que faltan

### Requirement: Recambio de equipo
La vista SHALL ofrecer el recambio de un dispositivo averiado o fuera de servicio,
reutilizando su registro vía `DataRepository.replaceDevice()` (HU-21 CA-05).

#### Scenario: Recambiar una pieza
- **WHEN** el usuario recambia un dispositivo indicando el serial de la pieza nueva
- **THEN** la vista actualiza la flota con la avería limpia y el monitoreo reanudado

### Requirement: Origen de datos por entorno
La vista SHALL obtener y mutar la flota a través de `DataRepository`, sin conocer el
origen concreto (mock o backend HTTP).

#### Scenario: Modo backend
- **WHEN** `VITE_DATA_SOURCE=http`
- **THEN** la flota se obtiene de `GET {VITE_API_BASE_URL}/hardware` y las altas/recambios
  usan `POST`/`PUT {VITE_API_BASE_URL}/hardware`

#### Scenario: Modo mock
- **WHEN** `VITE_DATA_SOURCE` no está definida o vale `mock`
- **THEN** la flota proviene del mock determinístico y las altas/recambios actualizan el
  cache en memoria
