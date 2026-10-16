## MODIFIED Requirements

### Requirement: Navegación lateral persistente
La aplicación SHALL presentar un sidebar fijo (256px, verde oscuro) con la marca Yerbanalytics y dos grupos de navegación exclusivamente productivos: Principal (Panel general, Diagnósticos de IA) y Gestión (Historial, Configuración, Hardware, Topología).

El sidebar NO SHALL incluir ningún ítem de navegación ni control para "Demo Expo" ni para funciones de demostración o simulación. El detalle de macro-zona NO SHALL figurar en el sidebar: se entra eligiendo una macro-zona en el panel general, y sin esa elección la vista no tiene de dónde sacar qué zona mostrar.

#### Scenario: Ítem activo resaltado
- **WHEN** el usuario está en una vista
- **THEN** el ítem de navegación correspondiente se resalta (borde naranja + fondo)

#### Scenario: Contador de diagnósticos
- **WHEN** se muestra el ítem "Diagnósticos de IA"
- **THEN** incluye un badge con la cantidad de diagnósticos activos

#### Scenario: El detalle de macro-zona no es un destino del sidebar
- **WHEN** el usuario recorre la navegación lateral
- **THEN** no encuentra un ítem que lleve al detalle de macro-zona

#### Scenario: Sin ítems de demo en la navegación productiva
- **WHEN** el usuario inspecciona el sidebar en cualquier modo de operación
- **THEN** no encuentra ningún enlace a Demo Expo ni controles de simulación
