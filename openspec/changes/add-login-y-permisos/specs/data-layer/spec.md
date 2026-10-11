## ADDED Requirements

### Requirement: Sesión y gestión de usuarios por repositorio
El frontend SHALL acceder a la sesión (login, perfil, actividad, cierre, cambio de contraseña)
y a la gestión de usuarios, permisos, política de sesión y auditoría únicamente a través de una
interface `SeguridadRepository`, elegida por el mismo `VITE_DATA_SOURCE` que el
`DataRepository`, de modo que nunca convivan una sesión simulada con datos del backend.

#### Scenario: Un único punto de decisión
- **WHEN** `VITE_DATA_SOURCE=http`
- **THEN** tanto el `DataRepository` como el `SeguridadRepository` hablan con el backend

#### Scenario: La UI no conoce el origen
- **WHEN** la pantalla de login autentica a un usuario
- **THEN** lo hace a través del `SeguridadRepository`, sin importar clientes HTTP ni datos mock

### Requirement: Sesión en las peticiones al backend
El repositorio HTTP SHALL enviar la cookie de sesión en todas las peticiones
(`credentials: 'include'`) y SHALL tratar de forma uniforme las respuestas de seguridad: ante un
`401` SHALL notificar a la capa de sesión con el motivo (`SIN_SESION`, `SESION_EXPIRADA`,
`SESION_REVOCADA`) para que el dashboard muestre el login con el aviso que corresponda; ante un
`403` SHALL rechazar con un error tipado de permiso. Las consultas de sondeo SHALL marcarse para
que el backend no las cuente como actividad del usuario.

#### Scenario: Sesión vencida durante el sondeo
- **WHEN** el snapshot del vivero responde `401` con motivo `SESION_EXPIRADA`
- **THEN** el dashboard detiene los sondeos y muestra el login con "Tu sesión se cerró por inactividad"

#### Scenario: Acción sin permiso
- **WHEN** guardar parámetros responde `403`
- **THEN** la vista muestra "No tenés permiso para esta acción" y no da el cambio por aplicado

### Requirement: Usuarios demo en modo mock
En modo `mock`, el `SeguridadRepository` SHALL ofrecer un usuario demo por rol de persona
(Administrador, Ingeniero Agrónomo, Productor Viverista, Operario) con contraseña conocida,
aplicar la misma matriz de permisos por defecto que el backend y permitir la gestión de usuarios
y de la matriz en memoria. La pantalla de login SHALL listar esos usuarios demo sólo en este modo.
La auditoría y el cierre por inactividad SHALL simularse en memoria con el mismo comportamiento
visible.

#### Scenario: Demo con perfil restringido
- **WHEN** en modo mock se inicia sesión como el Operario demo
- **THEN** el menú y las acciones se restringen igual que con un Operario real

#### Scenario: Los usuarios demo no aparecen en el sistema real
- **WHEN** el dashboard corre con `VITE_DATA_SOURCE=http`
- **THEN** la pantalla de login no menciona usuarios demo
