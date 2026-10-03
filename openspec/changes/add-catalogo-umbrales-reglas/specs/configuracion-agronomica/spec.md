## MODIFIED Requirements

### Requirement: Edición de límites operativos
La vista SHALL permitir definir el volumen diario máximo de riego, la dosis máxima de insumo por
24 h y el plan de rustificación por etapas (día desde, día hasta, % apertura). El tiempo máximo de
apertura de riego y la apertura máxima de la mediasombra NO SHALL editarse en esta vista: son
parámetros del catálogo de reglas y se editan en "Motor de reglas". La vista SHALL indicarlo con un
enlace a esa sección, y SHALL validar las etapas de rustificación contra el valor vigente de la
apertura máxima del catálogo.

#### Scenario: Editar un límite de actuador
- **WHEN** el usuario modifica un límite operativo (p. ej. volumen diario de riego)
- **THEN** el formulario lo incluye al guardar

#### Scenario: Editar el plan de rustificación
- **WHEN** el usuario ajusta las etapas del plan de rustificación
- **THEN** la vista muestra el cronograma actualizado de días y porcentaje de apertura

#### Scenario: Campos mudados al catálogo
- **WHEN** el usuario abre Configuración
- **THEN** no encuentra "Tiempo máx. de apertura de riego" ni "Apertura máx. de mediasombra", y sí
  un enlace a "Motor de reglas"

#### Scenario: Etapa por encima de la apertura máxima
- **WHEN** una etapa de rustificación supera el valor vigente de `mediasombra.apertura-maxima`
- **THEN** el campo se marca inválido y no se puede guardar
