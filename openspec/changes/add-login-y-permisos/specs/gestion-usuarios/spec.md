## ADDED Requirements

### Requirement: Panel de gestión de usuarios
Quien tenga `usuarios.gestionar` SHALL contar con una vista "Usuarios" que lista todos los
usuarios con su nombre de usuario, nombre a mostrar, rol, estado y último ingreso, separando las
cuentas de rol Servicio de las de personas. Las bajas SHALL poder mostrarse a pedido.

#### Scenario: Listado
- **WHEN** el Administrador abre la vista Usuarios
- **THEN** ve los usuarios activos y suspendidos con su rol, estado y último ingreso

#### Scenario: Sin permiso
- **WHEN** un Ingeniero Agrónomo llama a `GET /api/usuarios`
- **THEN** el backend responde `403`

### Requirement: Alta de usuario
El Administrador SHALL poder dar de alta un usuario indicando nombre de usuario, nombre a
mostrar, rol y una contraseña temporal. El nombre de usuario SHALL ser único sin distinguir
mayúsculas, incluso frente a usuarios dados de baja, y SHALL tener entre 3 y 40 caracteres de
`[a-z0-9._-]`. El usuario creado SHALL quedar activo y obligado a cambiar la contraseña en su
primer ingreso. (HU-20 CA-01)

#### Scenario: Alta válida
- **WHEN** el Administrador da de alta "jperez" con rol Operario
- **THEN** el usuario queda activo con los permisos del rol Operario
- **AND** el alta queda auditada

#### Scenario: Nombre de usuario repetido
- **WHEN** se da de alta "JPerez" existiendo "jperez", aunque esté dado de baja
- **THEN** el backend responde `409` y no crea el usuario

### Requirement: Edición y cambio de rol
El Administrador SHALL poder editar el nombre a mostrar y el rol de un usuario. El nombre de
usuario NO SHALL poder cambiarse. Un cambio de rol SHALL invalidar todas las sesiones vigentes del
usuario, que al volver a ingresar opera con los permisos del rol nuevo. (HU-20 CA-01, CA-02)

#### Scenario: Cambio de rol con sesión abierta
- **WHEN** el Administrador cambia de Productor Viverista a Operario a un usuario con sesión abierta
- **THEN** la siguiente petición de ese usuario recibe `401` con motivo `SESION_REVOCADA`
- **AND** el dashboard le muestra el login con el aviso "Tu cuenta cambió; volvé a iniciar sesión"
- **AND** al reingresar ve sólo lo habilitado para Operario

#### Scenario: Edición sin cambio de rol
- **WHEN** el Administrador sólo corrige el nombre a mostrar
- **THEN** las sesiones del usuario siguen vigentes y el cambio queda auditado

### Requirement: Suspensión y reactivación
El Administrador SHALL poder suspender a un usuario, lo que invalida todas sus sesiones e impide
que inicie sesión, y SHALL poder reactivarlo después. Un usuario NO SHALL poder suspenderse a sí
mismo. (HU-20 CA-02)

#### Scenario: Suspender con sesión abierta
- **WHEN** el Administrador suspende a un usuario que está operando
- **THEN** la siguiente petición de ese usuario recibe `401` con motivo `SESION_REVOCADA`
- **AND** un nuevo intento de login recibe el error genérico de credenciales

#### Scenario: Reactivar
- **WHEN** el Administrador reactiva a un usuario suspendido
- **THEN** el usuario puede volver a iniciar sesión con su contraseña de siempre

#### Scenario: Autosuspensión
- **WHEN** el Administrador intenta suspenderse a sí mismo
- **THEN** el backend responde `409` y no cambia su estado

### Requirement: Baja de usuario
El Administrador SHALL poder dar de baja (revocar) a un usuario. La baja SHALL ser lógica y
definitiva: el registro se conserva para la auditoría, todas sus sesiones se invalidan, no puede
volver a iniciar sesión ni reactivarse, y su nombre de usuario no se reutiliza. Un usuario NO
SHALL poder darse de baja a sí mismo. (HU-20 CA-02)

#### Scenario: Baja con sesión abierta
- **WHEN** el Administrador da de baja a un usuario que está operando
- **THEN** la siguiente petición de ese usuario recibe `401` con motivo `SESION_REVOCADA`

#### Scenario: Reactivar una baja
- **WHEN** se intenta reactivar un usuario dado de baja
- **THEN** el backend responde `409`

#### Scenario: La baja conserva la trazabilidad
- **WHEN** se consulta la auditoría de un usuario dado de baja
- **THEN** sus registros siguen mostrando su nombre de usuario

### Requirement: Blanqueo de contraseña
El Administrador SHALL poder asignar una contraseña temporal a otro usuario. El blanqueo SHALL
invalidar las sesiones del usuario y obligarlo a cambiarla en el próximo ingreso. La contraseña
asignada NO SHALL quedar registrada en ningún lado más que su hash.

#### Scenario: Blanqueo
- **WHEN** el Administrador blanquea la contraseña de un usuario
- **THEN** el usuario debe usar la temporal y cambiarla al ingresar
- **AND** la auditoría registra el blanqueo sin la contraseña

### Requirement: Cambios de matriz invalidan sesiones del rol
Al guardar un cambio en los permisos de un rol, el sistema SHALL invalidar las sesiones vigentes
de todos los usuarios con ese rol, excepto la sesión de quien hizo el cambio. (HU-20 CA-02)

#### Scenario: Matriz del Operario modificada
- **WHEN** el Administrador cambia los permisos del rol Operario
- **THEN** cada Operario con sesión abierta recibe `401` con motivo `SESION_REVOCADA` en su siguiente petición

#### Scenario: El autor sigue operando
- **WHEN** el Administrador cambia los permisos de su propio rol
- **THEN** su sesión sigue vigente y opera con la matriz nueva desde la siguiente petición
