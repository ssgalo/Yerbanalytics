# app-shell

## Purpose

Shell de la aplicacion: navegacion, encabezado y alertas activas.

## Requirements

### Requirement: Navegación lateral persistente
La aplicación SHALL presentar un sidebar fijo (256px, verde oscuro) con la marca
Yerbanalytics y dos grupos de navegación: Principal (Panel general, Diagnósticos de IA) y
Gestión (Historial, Configuración, Hardware, Topología).

El detalle de macro-zona NO SHALL figurar en el sidebar: se entra eligiendo una macro-zona
en el panel general, y sin esa elección la vista no tiene de dónde sacar qué zona mostrar.

#### Scenario: Ítem activo resaltado
- **WHEN** el usuario está en una vista
- **THEN** el ítem de navegación correspondiente se resalta (borde naranja + fondo)

#### Scenario: Contador de diagnósticos
- **WHEN** se muestra el ítem "Diagnósticos de IA"
- **THEN** incluye un badge con la cantidad de diagnósticos activos

#### Scenario: El detalle de macro-zona no es un destino del sidebar
- **WHEN** el usuario recorre la navegación lateral
- **THEN** no encuentra un ítem que lleve al detalle de macro-zona

### Requirement: Barra superior contextual
La aplicación SHALL presentar una topbar (74px) con título y subtítulo según la
vista, widget de clima, campana de alertas con contador, y datos del usuario.

#### Scenario: Título dinámico por vista
- **WHEN** cambia la ruta activa
- **THEN** el título y subtítulo de la topbar reflejan la vista actual

#### Scenario: Apertura del panel de alertas
- **WHEN** el usuario hace clic en la campana
- **THEN** se despliega el panel de alertas activas y muestra el conteo sin atender

### Requirement: Indicador de estado del sistema
El sidebar SHALL mostrar el estado del sistema (en línea / edge sincronizado).

#### Scenario: Sistema operativo
- **WHEN** se renderiza el sidebar
- **THEN** muestra el indicador "Sistema en línea" y la última sincronización del edge
