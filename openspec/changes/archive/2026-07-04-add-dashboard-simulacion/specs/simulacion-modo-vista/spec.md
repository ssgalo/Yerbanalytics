# Spec: simulacion-modo-vista

## ADDED Requirements

### Requirement: La vista del vivero sigue el modo persistido para elegir su fuente de datos
El dashboard principal SHALL determinar, al montar, el modo de operación vigente leyéndolo
del backend, y SHALL elegir la fuente del snapshot del vivero según ese modo: en **estático**
SHALL usar el repositorio mock (demo determinística hardcodeada de 6 macro-zonas), y en
**simulación** SHALL usar el repositorio http (backend real). El estado de simulación (modo
y sensores) SHALL leerse siempre del backend, con independencia del modo.

#### Scenario: Modo estático muestra la demo hardcodeada
- **WHEN** el modo persistido es estático y el usuario abre el dashboard principal
- **THEN** la vista muestra el vivero de demostración (6 macro-zonas con valores
  hardcodeados) tomado del mock determinístico

#### Scenario: Modo simulación muestra el vivero real
- **WHEN** el modo persistido es simulación y el usuario abre el dashboard principal
- **THEN** la vista muestra el snapshot del vivero tomado del backend real (topología
  vigente y sus últimas lecturas)

### Requirement: El modo se respeta al refrescar la página
El modo elegido SHALL sobrevivir a un refresco (F5) del dashboard: al recargar, la vista
SHALL volver a leer el modo persistido y NO SHALL revertir a la demo estática cuando el modo
persistido es simulación.

#### Scenario: F5 en modo simulación no vuelve a la demo estática
- **WHEN** el modo persistido es simulación y el usuario refresca el dashboard
- **THEN** la vista sigue mostrando el vivero real de simulación y no la demo de 6
  macro-zonas hardcodeadas

#### Scenario: F5 en modo estático mantiene la demo default
- **WHEN** el modo persistido es estático y el usuario refresca el dashboard
- **THEN** la vista vuelve a mostrar la topología default con los valores hardcodeados

### Requirement: Degradación a estático si no se puede leer el modo
Si la lectura del modo del backend falla, la vista SHALL degradar a estático (mock), de modo
que la demo siempre pueda cargar sin backend disponible.

#### Scenario: Backend no disponible al determinar el modo
- **WHEN** el dashboard no logra leer el modo del backend
- **THEN** la vista carga la demo estática (mock) en lugar de quedar sin datos
