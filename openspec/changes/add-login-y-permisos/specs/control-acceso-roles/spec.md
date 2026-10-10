## ADDED Requirements

### Requirement: Roles fijos
El sistema SHALL tener exactamente cinco roles, definidos en código: Administrador, Ingeniero
Agrónomo, Productor Viverista, Operario y Servicio. Cada usuario SHALL tener exactamente un rol.
No SHALL poder crearse, renombrarse ni borrarse roles. El rol Servicio está destinado a cuentas
de integraciones (simulador, futuro servicio de inferencia) y el dashboard SHALL presentarlo
como tal.

#### Scenario: Asignación de un rol inexistente
- **WHEN** se intenta guardar un usuario con rol "Supervisor"
- **THEN** el backend responde `400` y no guarda el usuario

#### Scenario: El rol Servicio se distingue
- **WHEN** el Administrador elige el rol Servicio al dar de alta un usuario
- **THEN** el dashboard le advierte que es un rol para integraciones y no para personas

### Requirement: Catálogo de permisos
Los permisos SHALL ser un catálogo fijo en código; cada uno tiene un código, una descripción y un
grupo. El catálogo inicial es:

| Código | Habilita |
|---|---|
| `vivero.ver` | Panel general, detalle de macro-zona y de sector (snapshot del vivero) |
| `diagnosticos.ver` | Diagnósticos de IA y su consulta |
| `diagnosticos.registrar` | Alta de diagnósticos |
| `historial.ver` | Historial de acciones |
| `configuracion.ver` / `configuracion.editar` | Configuración agronómica |
| `reglas.ver` / `reglas.editar` | Motor de reglas: grafo, trazas / catálogo de parámetros |
| `hardware.ver` / `hardware.gestionar` | Estado del hardware / alta y recambio de dispositivos |
| `topologia.ver` / `topologia.gestionar` | Topología / generarla y cambiar su disposición |
| `capturas.ver` / `capturas.ordenar` | Órdenes de captura e imágenes / emitir órdenes |
| `camara.gestionar` | Códigos de vinculación, listado y revocación de dispositivos de captura |
| `pasadas.ver` / `pasadas.operar` | Estado de la pasada del riel / iniciarla y cancelarla |
| `demo-expo.configurar` | Mostrar u ocultar la pestaña Demo Expo |
| `usuarios.gestionar` | Usuarios, matriz de permisos y política de sesión |
| `auditoria.ver` | Registro de auditoría de seguridad |

Un permiso de edición NO SHALL implicar el de lectura: la matriz los guarda por separado y el
backend SHALL rechazar una matriz que tenga un permiso de edición sin su par de lectura.

#### Scenario: Consulta del catálogo
- **WHEN** quien tiene `usuarios.gestionar` consulta los permisos
- **THEN** recibe el catálogo completo agrupado, con su descripción

#### Scenario: Edición sin lectura
- **WHEN** se intenta guardar el rol Operario con `reglas.editar` pero sin `reglas.ver`
- **THEN** el backend responde `400` y no cambia la matriz

### Requirement: Matriz de permisos editable por el Administrador
Qué permisos tiene cada rol SHALL persistirse y SHALL poder editarlo quien tenga
`usuarios.gestionar`. En una base sin matriz el sistema SHALL sembrar la matriz por defecto:

| Rol | Permisos por defecto |
|---|---|
| Administrador | todos |
| Ingeniero Agrónomo | todos los `.ver`, `configuracion.editar`, `reglas.editar` |
| Productor Viverista | todos los `.ver`, `pasadas.operar` |
| Operario | `vivero.ver`, `diagnosticos.ver`, `historial.ver`, `capturas.ver`, `pasadas.ver` |
| Servicio | `vivero.ver`, `topologia.ver`, `capturas.ver`, `capturas.ordenar`, `diagnosticos.ver`, `diagnosticos.registrar`, `camara.gestionar` |

"Todos los `.ver`" excluye `auditoria.ver`, que por defecto sólo tiene el Administrador. (HU-20
CA-01)

#### Scenario: Siembra inicial
- **WHEN** el backend arranca contra una base sin matriz de permisos
- **THEN** cada rol queda con sus permisos por defecto

#### Scenario: El Operario no dispara pasadas por defecto
- **WHEN** un Operario con la matriz por defecto llama a `POST /api/pasadas`
- **THEN** el backend responde `403` y no se inicia ninguna pasada
- **AND** en Demo Expo ve el estado de la pasada sin los botones de iniciar ni cancelar

#### Scenario: La siembra no pisa ediciones
- **WHEN** el backend arranca y la matriz ya fue editada
- **THEN** conserva la matriz editada

#### Scenario: Edición de la matriz
- **WHEN** el Administrador le quita `pasadas.operar` al Productor Viverista y guarda
- **THEN** a partir de ese momento ningún Productor Viverista puede iniciar una pasada
- **AND** el cambio queda auditado con los permisos agregados y quitados

### Requirement: Salvaguardas contra la pérdida de administración
El sistema SHALL impedir que quede sin nadie capaz de administrarlo. El rol Administrador NO
SHALL poder perder `usuarios.gestionar` ni `auditoria.ver`, y NO SHALL poder suspenderse, darse
de baja ni cambiarse de rol al último usuario activo con rol Administrador.

#### Scenario: Quitarle la gestión de usuarios al Administrador
- **WHEN** se intenta guardar el rol Administrador sin `usuarios.gestionar`
- **THEN** el backend responde `409` y no cambia la matriz

#### Scenario: Último Administrador
- **WHEN** hay un único Administrador activo y se intenta suspenderlo, darlo de baja o cambiarle el rol
- **THEN** el backend responde `409` explicando que el sistema quedaría sin Administrador

### Requirement: Aplicación de permisos en el backend
Toda ruta bajo `/api/**` SHALL exigir una sesión de usuario válida y el permiso que le
corresponda, salvo: `POST /api/auth/login`, la superficie del contrato de cámara
`/api/camara/v1/**` (que mantiene su propio token de dispositivo) y `/ca.pem`. Los permisos
SHALL evaluarse en cada petición contra la matriz vigente, de modo que un cambio de matriz rija
sin esperar a que el usuario vuelva a iniciar sesión. Una ruta sin permiso asignado SHALL quedar
denegada por omisión.

#### Scenario: Sin sesión
- **WHEN** se llama a `GET /api/nursery` sin cookie de sesión
- **THEN** el backend responde `401` con motivo `SIN_SESION`

#### Scenario: Sin permiso
- **WHEN** un Operario llama a `PUT /api/rules/parametros`
- **THEN** el backend responde `403` y no modifica el catálogo

#### Scenario: Con permiso
- **WHEN** un Ingeniero Agrónomo llama a `PUT /api/rules/parametros` con cambios válidos
- **THEN** el backend aplica los cambios como hasta ahora

#### Scenario: Ruta nueva sin declarar permiso
- **WHEN** se agrega un endpoint bajo `/api/**` sin asociarle un permiso
- **THEN** el backend lo rechaza con `403` para todos los roles hasta que se le asigne uno

#### Scenario: El contrato de cámara no cambia
- **WHEN** un dispositivo de captura llama a `/api/camara/v1/**` con su token de dispositivo y sin sesión de usuario
- **THEN** el backend lo atiende como hasta ahora

### Requirement: Aplicación de permisos en el dashboard
El dashboard SHALL mostrar sólo las vistas y acciones habilitadas por los permisos del usuario.
Una vista sin su permiso `.ver` NO SHALL aparecer en el menú y, si se accede por URL, SHALL
mostrar un aviso de acceso denegado sin pedir datos al backend. Las acciones de edición sin su
permiso SHALL ocultarse o mostrarse deshabilitadas con el motivo. El dashboard es sólo
presentación: la autoridad sigue siendo el backend.

#### Scenario: Operario en el menú
- **WHEN** un Operario con la matriz por defecto ve el sidebar
- **THEN** no aparecen Configuración, Motor de reglas, Hardware, Topología ni Usuarios

#### Scenario: Acceso por URL sin permiso
- **WHEN** un Operario abre `/topologia` escribiendo la URL
- **THEN** ve el aviso "No tenés permiso para ver esta sección" y no se consulta la topología

#### Scenario: Lectura sin edición
- **WHEN** un Productor Viverista abre el Motor de reglas
- **THEN** ve el catálogo de parámetros en sólo lectura, sin el botón para guardar

#### Scenario: Rol sin vistas
- **WHEN** inicia sesión en el dashboard un usuario cuyo rol no tiene ningún permiso `.ver` de vistas
- **THEN** el dashboard muestra "Tu rol no tiene secciones habilitadas" con la opción de cerrar sesión

#### Scenario: Cambio de permisos mientras navega
- **WHEN** el backend responde `403` a una acción que el dashboard mostraba habilitada
- **THEN** el dashboard recarga el perfil del usuario y actualiza menú y acciones
