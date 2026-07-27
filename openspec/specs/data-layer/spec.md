# data-layer

## Purpose

Capa de datos del frontend: contratos de dominio y acceso via repositorio.
## Requirements
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

### Requirement: La lectura sensada se expone dentro de la macro-zona

El contrato de dominio SHALL exponer la lectura sensada y el estado del nodo testigo como
parte de la macro-zona, no del sector. El sector NO SHALL exponer valores de métricas
propios.

#### Scenario: Acceso a la lectura desde un componente

- **WHEN** un componente necesita los valores sensados que aplican a un sector
- **THEN** los obtiene de la macro-zona a la que ese sector pertenece

#### Scenario: Contrato del sector

- **WHEN** se inspecciona el contrato de un sector
- **THEN** no contiene métricas sensadas ni marca de tiempo de lectura propia

### Requirement: Serie histórica por métrica

El repositorio SHALL entregar la serie histórica de cualquiera de las diez métricas de una
macro-zona para un rango temporal dado (24h / 7d / 30d), no sólo de humedad de sustrato.

#### Scenario: Serie de una métrica arbitraria

- **WHEN** se solicita la serie de nitrógeno de MZ-4 en rango 30d
- **THEN** el repositorio devuelve la serie de esa métrica para ese rango

#### Scenario: Serie estable entre renders

- **WHEN** se solicita dos veces la misma métrica, zona y rango sin que haya cambiado la
  lectura
- **THEN** se obtiene la misma serie

### Requirement: Paridad entre mock y backend para el sensado

Ambas implementaciones del repositorio SHALL exponer las diez métricas, el estado del nodo
testigo y la serie histórica por métrica, de modo que la interfaz funcione igual con
`VITE_DATA_SOURCE=mock` y con `http`.

#### Scenario: Modo mock con las métricas nuevas

- **WHEN** la aplicación corre en modo mock
- **THEN** el panel de sensado muestra las diez métricas con valores generados de forma
  determinística y el estado simulado del nodo

#### Scenario: Modo backend

- **WHEN** la aplicación corre contra el backend real
- **THEN** el panel de sensado muestra las diez métricas provenientes de la lectura
  persistida de la macro-zona

