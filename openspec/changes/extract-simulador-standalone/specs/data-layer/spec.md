## MODIFIED Requirements

### Requirement: Repository abstraction
El frontend SHALL acceder a los datos del vivero únicamente a través de una
interface `DataRepository`, sin que ningún componente conozca el origen concreto
de los datos. La interface SHALL cubrir únicamente el dominio del dashboard de monitoreo:
NO SHALL declarar operaciones de simulación ni de operación de la cámara, porque ningún
componente del dashboard las consume.

#### Scenario: La UI no depende del origen de datos
- **WHEN** un componente necesita los datos del vivero
- **THEN** los obtiene de un `DataRepository` (vía hook/Context)
- **AND** no importa ni instancia generadores ni clientes HTTP directamente

#### Scenario: La interface no declara operaciones de simulación
- **WHEN** se audita `DataRepository` y sus implementaciones
- **THEN** no hay métodos de modo de operación, de sensores simulados ni de envío de
  telemetría

#### Scenario: La interface no declara operaciones de cámara
- **WHEN** se audita `DataRepository` y sus implementaciones
- **THEN** no hay métodos de dispositivos de captura, de vinculación, de órdenes de captura
  ni de alta de diagnósticos
- **AND** los endpoints correspondientes del backend siguen existiendo, porque son superficie
  pública de la plataforma

### Requirement: Selección de origen por entorno
El sistema SHALL elegir la implementación del repositorio según la variable de
entorno `VITE_DATA_SOURCE` (`mock` por defecto, `http` para backend real). Esa elección SHALL
ser el **único** punto de decisión del origen de datos y SHALL regir para **todas** las
secciones de la aplicación —vivero, mapa, detalle de sector, diagnósticos, hardware,
configuración, historial y topología—, de modo que nunca convivan en pantalla datos mock con
datos del backend. Ninguna vista SHALL elegir su propio origen ni consultarlo al backend.

#### Scenario: Modo mock por defecto
- **WHEN** `VITE_DATA_SOURCE` no está definida o vale `mock`
- **THEN** se usa `MockRepository` con datos determinísticos

#### Scenario: Modo backend
- **WHEN** `VITE_DATA_SOURCE=http`
- **THEN** se usa `HttpRepository` apuntando a `VITE_API_BASE_URL`

#### Scenario: La demo ilustrativa cubre toda la aplicación
- **WHEN** la aplicación corre en modo mock
- **THEN** todas las secciones muestran datos del mock determinístico, sin que ninguna
  consulte el backend

#### Scenario: El modo real cubre toda la aplicación
- **WHEN** la aplicación corre en modo backend
- **THEN** todas las secciones muestran datos del backend real, sin que ninguna caiga al mock

#### Scenario: El origen no se consulta al backend
- **WHEN** se audita cómo la aplicación determina su origen de datos
- **THEN** lo resuelve por variable de entorno al arrancar, sin ninguna petición previa al
  backend

#### Scenario: La demo carga sin backend
- **WHEN** la aplicación corre en modo mock y el backend está apagado
- **THEN** todas las secciones cargan igual
