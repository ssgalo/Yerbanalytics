## ADDED Requirements

### Requirement: Inicio de sesión con usuario y contraseña
El sistema SHALL autenticar a un usuario por nombre de usuario y contraseña. Al autenticar,
SHALL abrir una sesión del lado del servidor, entregarla al cliente en una cookie `HttpOnly`,
`SameSite=Strict`, y devolver el perfil del usuario: nombre, rol, permisos efectivos, tiempo
máximo de inactividad vigente y si debe cambiar la contraseña. El dashboard SHALL redirigir al
Panel general con sólo las vistas y acciones que habilitan esos permisos. (HU-01 CA-01)

#### Scenario: Credenciales válidas
- **WHEN** un usuario activo con rol "Ingeniero Agrónomo" ingresa usuario y contraseña correctos
- **THEN** el backend responde `200` con su perfil y fija la cookie de sesión
- **AND** el dashboard lo lleva al Panel general mostrando las vistas habilitadas para su rol

#### Scenario: Ruta pedida antes de autenticarse
- **WHEN** alguien sin sesión abre `/reglas` y luego inicia sesión con un rol que tiene `reglas.ver`
- **THEN** el dashboard lo lleva a `/reglas` en lugar del Panel general

#### Scenario: Usuario ya autenticado entra al login
- **WHEN** un usuario con sesión vigente abre `/login`
- **THEN** el dashboard lo redirige al Panel general

### Requirement: Error de credenciales genérico
Ante un intento de inicio de sesión fallido el sistema SHALL responder siempre `401` con el mismo
cuerpo y el dashboard SHALL mostrar el mismo mensaje ("Credenciales incorrectas"), sin revelar
si falló el usuario, la contraseña o si la cuenta está suspendida o dada de baja. El tiempo de
respuesta NO SHALL depender de si el usuario existe. (HU-01 CA-02)

#### Scenario: Contraseña incorrecta
- **WHEN** un usuario existente ingresa una contraseña errónea
- **THEN** el sistema responde `401` y el dashboard muestra "Credenciales incorrectas"

#### Scenario: Usuario inexistente
- **WHEN** se ingresa un nombre de usuario que no existe
- **THEN** la respuesta es idéntica en código y cuerpo a la de contraseña incorrecta
- **AND** el backend igualmente compara contra un hash de relleno para no responder más rápido

#### Scenario: Cuenta suspendida o dada de baja
- **WHEN** un usuario suspendido o dado de baja ingresa sus credenciales correctas
- **THEN** la respuesta es idéntica a la de credenciales incorrectas

#### Scenario: El formulario no adelanta información
- **WHEN** el login falla
- **THEN** el dashboard no marca ningún campo en particular como erróneo y limpia la contraseña

### Requirement: Cierre de sesión por inactividad
El sistema SHALL cerrar la sesión de un usuario cuando transcurre el tiempo máximo de inactividad
configurado sin actividad de su parte, y SHALL exigir una nueva autenticación para seguir
operando. La autoridad es el backend: una sesión vencida responde `401` con motivo
`SESION_EXPIRADA` aunque el cliente no se haya dado cuenta. (HU-01 CA-03)

Cuenta como actividad el inicio de sesión, cualquier petición que modifique datos y la señal de
actividad que el dashboard envía mientras detecta interacción real (teclado, puntero, toque,
scroll). Las consultas de sondeo automático del dashboard NO SHALL contar como actividad.

#### Scenario: Inactividad excedida
- **WHEN** el tiempo máximo es 60 min y el usuario no interactúa durante 60 min con el dashboard abierto
- **THEN** el dashboard cierra la sesión, descarta los datos en pantalla y muestra el login con el aviso "Tu sesión se cerró por inactividad"

#### Scenario: El sondeo no mantiene viva la sesión
- **WHEN** el Panel general sigue consultando el snapshot cada 5 s pero el usuario no interactúa
- **THEN** la sesión vence igual al cumplirse el tiempo máximo

#### Scenario: Vencimiento detectado por el backend
- **WHEN** una petición llega con una sesión cuyo último registro de actividad supera el tiempo máximo
- **THEN** el backend responde `401` con motivo `SESION_EXPIRADA` y da la sesión por cerrada

#### Scenario: Retorno a la ruta tras reautenticar
- **WHEN** la sesión venció estando en `/historial` y el usuario vuelve a iniciar sesión
- **THEN** el dashboard lo lleva de nuevo a `/historial` si su rol todavía puede verlo

#### Scenario: Actividad entre pestañas
- **WHEN** el usuario tiene dos pestañas abiertas e interactúa sólo en una
- **THEN** la sesión no vence en la otra mientras haya actividad en la primera

### Requirement: Tiempo máximo de inactividad configurable
El tiempo máximo de inactividad SHALL ser un valor persistido, editable por quien tenga
`usuarios.gestionar`, con un valor por defecto de 60 minutos y un rango admitido de 5 a 480
minutos. El cambio SHALL aplicarse a todas las sesiones a partir de ese momento y SHALL quedar
auditado.

#### Scenario: Cambio del tiempo máximo
- **WHEN** el Administrador cambia el tiempo máximo de 60 a 15 minutos
- **THEN** las sesiones sin actividad en los últimos 15 minutos vencen en su próxima petición
- **AND** el cambio queda en la auditoría con el valor anterior y el nuevo

#### Scenario: Valor fuera de rango
- **WHEN** se intenta guardar 2 minutos
- **THEN** el backend responde `400` indicando el rango admitido y no cambia el valor

### Requirement: Cierre de sesión voluntario
El usuario SHALL poder cerrar su sesión. El cierre SHALL invalidar la sesión en el servidor y
borrar la cookie, de modo que reutilizar el identificador de sesión no dé acceso.

#### Scenario: Cerrar sesión
- **WHEN** el usuario elige "Cerrar sesión" en la topbar
- **THEN** el backend invalida la sesión y el dashboard muestra el login

#### Scenario: Reutilizar una sesión cerrada
- **WHEN** se repite una petición con la cookie de una sesión ya cerrada
- **THEN** el backend responde `401`

### Requirement: Contraseñas almacenadas de forma segura
Las contraseñas SHALL almacenarse sólo como hash BCrypt y NUNCA SHALL devolverse por la API ni
escribirse en logs o en la auditoría. Una contraseña nueva SHALL tener al menos 8 caracteres y
no SHALL ser igual al nombre de usuario.

#### Scenario: Ninguna respuesta expone el hash
- **WHEN** se consulta la lista de usuarios o el perfil propio
- **THEN** ninguna respuesta incluye la contraseña ni su hash

#### Scenario: Contraseña débil
- **WHEN** se intenta fijar una contraseña de 6 caracteres
- **THEN** el backend responde `400` con el motivo y no la guarda

### Requirement: Cambio de contraseña propio y obligatorio en el primer ingreso
Todo usuario autenticado SHALL poder cambiar su contraseña indicando la actual. Cuando el
Administrador asigna una contraseña (alta o blanqueo), el usuario SHALL quedar marcado para
cambiarla; mientras lo esté, el backend SHALL rechazar con `403` y motivo `CAMBIO_CLAVE_REQUERIDO`
toda petición que no sea cambiar la contraseña, consultar el perfil propio o cerrar sesión.

#### Scenario: Primer ingreso con contraseña temporal
- **WHEN** un usuario recién dado de alta inicia sesión
- **THEN** el dashboard le muestra sólo el formulario de cambio de contraseña
- **AND** al cambiarla accede al Panel general

#### Scenario: Contraseña actual incorrecta
- **WHEN** el usuario intenta cambiar su contraseña con una actual errónea
- **THEN** el backend responde `400` y no cambia nada

#### Scenario: Cambio propio cierra las otras sesiones
- **WHEN** un usuario cambia su contraseña
- **THEN** sus demás sesiones abiertas quedan invalidadas y la actual sigue vigente

### Requirement: Administrador inicial
Al arrancar, si no existe ningún usuario activo con rol Administrador, el backend SHALL crear uno
con el nombre y la contraseña definidos en la configuración, marcado para cambiar la contraseña
en el primer ingreso, y SHALL asentarlo en la auditoría con el sistema como autor. Si la
contraseña no está configurada, SHALL generar una aleatoria y mostrarla una única vez en el log de
arranque.

#### Scenario: Base vacía
- **WHEN** el backend arranca contra una base sin usuarios
- **THEN** existe un Administrador activo que debe cambiar la contraseña al primer ingreso

#### Scenario: Ya hay un Administrador
- **WHEN** el backend arranca y existe al menos un Administrador activo
- **THEN** no crea ningún usuario
