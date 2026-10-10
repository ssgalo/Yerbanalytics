## MODIFIED Requirements

### Requirement: Navegación lateral persistente
La aplicación SHALL presentar un sidebar fijo (256px, verde oscuro) con la marca
Yerbanalytics y dos grupos de navegación: Principal (Panel general, Diagnósticos de IA y, si
el interruptor de Configuración la enciende, Demo Expo) y Gestión (Historial, Configuración,
Motor de reglas, Hardware, Topología, Usuarios).

Cada ítem SHALL mostrarse sólo si el usuario autenticado tiene el permiso de lectura de esa
vista (`vivero.ver`, `diagnosticos.ver`, `pasadas.ver`, `historial.ver`, `configuracion.ver`,
`reglas.ver`, `hardware.ver`, `topologia.ver`, `usuarios.gestionar`). Un grupo sin ítems
visibles NO SHALL mostrarse.

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

#### Scenario: Menú según permisos
- **WHEN** un Operario con la matriz por defecto ve el sidebar
- **THEN** el grupo Gestión muestra sólo Historial

#### Scenario: Usuarios sólo para quien gestiona
- **WHEN** un usuario con `usuarios.gestionar` ve el sidebar
- **THEN** el grupo Gestión incluye "Usuarios"

### Requirement: Barra superior contextual
La aplicación SHALL presentar una topbar (74px) con título y subtítulo según la
vista, widget de clima, campana de alertas con contador, y los datos del usuario
autenticado: nombre a mostrar, rol e iniciales en el avatar. Al hacer clic sobre el usuario
SHALL desplegarse un menú con "Cambiar contraseña" y "Cerrar sesión".

#### Scenario: Título dinámico por vista
- **WHEN** cambia la ruta activa
- **THEN** el título y subtítulo de la topbar reflejan la vista actual

#### Scenario: Apertura del panel de alertas
- **WHEN** el usuario hace clic en la campana
- **THEN** se despliega el panel de alertas activas y muestra el conteo sin atender

#### Scenario: Usuario real en la topbar
- **WHEN** "Ana Benítez" con rol Ingeniero Agrónomo inicia sesión
- **THEN** la topbar muestra "Ana Benítez", "Ingeniero Agrónomo" y el avatar "AB"

#### Scenario: Cerrar sesión desde la topbar
- **WHEN** el usuario elige "Cerrar sesión" en el menú de usuario
- **THEN** la sesión se cierra y se muestra el login
