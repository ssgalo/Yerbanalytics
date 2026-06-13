# Spec: data-layer

## ADDED Requirements

### Requirement: Repository abstraction
El frontend SHALL acceder a los datos del vivero únicamente a través de una
interface `DataRepository`, sin que ningún componente conozca el origen concreto
de los datos.

#### Scenario: La UI no depende del origen de datos
- **WHEN** un componente necesita los datos del vivero
- **THEN** los obtiene de un `DataRepository` (vía hook/Context)
- **AND** no importa ni instancia generadores ni clientes HTTP directamente

### Requirement: Selección de origen por entorno
El sistema SHALL elegir la implementación del repositorio según la variable de
entorno `VITE_DATA_SOURCE` (`mock` por defecto, `http` para backend real).

#### Scenario: Modo mock por defecto
- **WHEN** `VITE_DATA_SOURCE` no está definida o vale `mock`
- **THEN** se usa `MockRepository` con datos determinísticos

#### Scenario: Modo backend
- **WHEN** `VITE_DATA_SOURCE=http`
- **THEN** se usa `HttpRepository` apuntando a `VITE_API_BASE_URL`

### Requirement: Datos mock determinísticos fieles al diseño
El `MockRepository` SHALL reproducir exactamente la lógica de generación del
diseño original (semilla configurable vía `VITE_MOCK_SEED`, default `20260613`).

#### Scenario: Stats agregadas estables
- **WHEN** se genera el dataset con la semilla por defecto
- **THEN** los totales (sano, warning, critical, offline, diagCount) son
  estables entre ejecuciones y coinciden con el diseño de referencia
