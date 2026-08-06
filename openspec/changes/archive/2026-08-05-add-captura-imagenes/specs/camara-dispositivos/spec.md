## ADDED Requirements

### Requirement: Vinculación por código de un solo uso
El backend SHALL permitir generar un código de vinculación de un solo uso y vida corta para
enrolar un dispositivo de cámara. El código SHALL invalidarse al ser consumido y al vencer su
plazo.

#### Scenario: Generación del código
- **WHEN** se solicita un código de vinculación
- **THEN** el backend responde con el código y su instante de vencimiento

#### Scenario: Código ya consumido
- **WHEN** se intenta enrolar un segundo dispositivo con un código ya usado
- **THEN** el backend responde `401` y no enrola el dispositivo

#### Scenario: Código vencido
- **WHEN** se intenta enrolar con un código cuyo plazo ya pasó
- **THEN** el backend responde `401` y no enrola el dispositivo

### Requirement: Enrolamiento y credenciales de vida corta
Al consumir un código válido, el backend SHALL registrar el dispositivo y devolverle un
identificador y una credencial de renovación. El dispositivo SHALL obtener tokens de acceso de
vida corta presentando esa credencial. Las credenciales de larga duración NO SHALL estar
embebidas en el código de la aplicación cliente.

#### Scenario: Enrolamiento exitoso
- **WHEN** el dispositivo se enrola con un código válido
- **THEN** el backend responde `201` con su identificador y su credencial de renovación
- **AND** el dispositivo queda registrado

#### Scenario: Obtención de un token de acceso
- **WHEN** el dispositivo presenta su credencial de renovación
- **THEN** el backend responde con un token de acceso de vida corta y su tiempo de expiración

#### Scenario: Renovación antes del vencimiento
- **WHEN** el token de acceso está próximo a expirar
- **THEN** el dispositivo obtiene uno nuevo sin intervención del operario y sin interrumpir su
  operación

#### Scenario: Credencial revocada
- **WHEN** el dispositivo se da de baja en el backend y luego intenta renovar su token
- **THEN** el backend responde `401` y el dispositivo queda sin acceso

### Requirement: Autenticación de todas las rutas de cámara y captura
El backend SHALL exigir un token de acceso válido en todas las rutas de cámara y de captura,
excepto en las de vinculación y enrolamiento. Las rutas preexistentes de la plataforma NO
SHALL verse afectadas por esta autenticación.

#### Scenario: Petición sin token
- **WHEN** se llama a una ruta de captura sin token
- **THEN** el backend responde `401`

#### Scenario: Petición con token vencido
- **WHEN** se llama a una ruta de captura con un token expirado
- **THEN** el backend responde `401` y el cliente renueva el token antes de reintentar

#### Scenario: El resto de la API sigue abierta
- **WHEN** el dashboard consulta el snapshot del vivero, el historial o la configuración
- **THEN** esas rutas responden como antes, sin requerir token de dispositivo

### Requirement: Heartbeat y estado operativo del dispositivo
El dispositivo SHALL reportar periódicamente su estado al backend, incluyendo si su stream de
cámara está activo y sus contadores de capturas exitosas y fallidas. El backend SHALL derivar
el estado operativo del dispositivo a partir del silencio transcurrido desde el último
heartbeat, sin esperar a que falle una captura para detectar una caída.

#### Scenario: Dispositivo reportando
- **WHEN** el dispositivo envía su heartbeat dentro del intervalo esperado
- **THEN** el backend lo marca como operativo y actualiza sus contadores

#### Scenario: Silencio intermedio
- **WHEN** el dispositivo supera el umbral de silencio intermedio sin reportar
- **THEN** el backend lo marca con señal intermitente

#### Scenario: Silencio prolongado
- **WHEN** el dispositivo supera el umbral crítico de silencio sin reportar
- **THEN** el backend lo marca fuera de servicio

#### Scenario: Detección sin capturas de por medio
- **WHEN** el dispositivo se cae y no se emite ninguna orden en ese lapso
- **THEN** el backend igual detecta la caída por ausencia de heartbeat

### Requirement: Configuración remota de captura
El backend SHALL ser la fuente de la configuración de captura —resolución máxima, calidad
JPEG, tiempo de calentamiento de cámara, intervalo de heartbeat, plazo de vencimiento de orden
y tope de cola de órdenes— y SHALL entregarla al dispositivo. Estos valores NO SHALL estar
fijos en el cliente. Un cambio de configuración SHALL notificarse por el stream de órdenes.

#### Scenario: El dispositivo toma la configuración del backend
- **WHEN** el dispositivo arranca
- **THEN** obtiene del backend la resolución, la calidad JPEG y los demás parámetros, y captura
  con esos valores

#### Scenario: Cambio de configuración en caliente
- **WHEN** la configuración cambia en el backend
- **THEN** el dispositivo recibe el aviso por el stream y aplica los valores nuevos en la
  siguiente captura, sin reiniciarse ni requerir intervención física

### Requirement: Consulta del estado de los dispositivos
El backend SHALL exponer el listado de dispositivos de cámara registrados con su nombre, su
estado operativo, el instante de su último heartbeat y sus contadores de capturas.

#### Scenario: Listado de dispositivos
- **WHEN** se consulta el listado de dispositivos de cámara
- **THEN** la respuesta incluye, por cada uno, su estado operativo, su última señal de vida y
  sus contadores de capturas exitosas y fallidas
