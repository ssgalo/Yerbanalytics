## ADDED Requirements

### Requirement: Emisión de órdenes de captura
El backend SHALL exponer un endpoint para emitir una orden de captura indicando el sector,
la macro-zona y la posición de riel donde debe tomarse la imagen. Cada orden SHALL recibir
un identificador único e irrepetible, un instante de creación y un plazo de vencimiento, y
SHALL nacer en estado `PENDIENTE`. El endpoint NO SHALL requerir que exista un dispositivo
conectado para aceptar la orden.

#### Scenario: Alta de una orden
- **WHEN** se emite una orden para un sector, su macro-zona y una posición de riel
- **THEN** el backend responde `201` con el identificador de la orden, su estado `PENDIENTE`
  y su instante de vencimiento

#### Scenario: Sector inexistente
- **WHEN** la orden referencia un sector que no existe en la topología vigente
- **THEN** el backend responde `400` y no crea la orden

#### Scenario: Orden emitida sin dispositivo conectado
- **WHEN** se emite una orden y no hay ningún dispositivo de cámara con el stream abierto
- **THEN** la orden se acepta igual y queda en estado `PENDIENTE`, sin error

### Requirement: Canal de órdenes por Server-Sent Events
El backend SHALL exponer un stream SSE autenticado por el que entrega las órdenes al
dispositivo de cámara. Al abrirse un stream, el backend SHALL entregar primero las órdenes
`PENDIENTE` acumuladas. El stream SHALL emitir un evento de keep-alive periódico para
sostener la conexión, y SHALL emitir un evento de configuración cuando la configuración
remota cambie. Una orden entregada SHALL pasar al estado `ENTREGADA`.

#### Scenario: Entrega inmediata de una orden nueva
- **WHEN** hay un stream abierto y se emite una orden
- **THEN** el dispositivo recibe un evento de orden con su identificador, sector, macro-zona
  y posición de riel
- **AND** la orden pasa a estado `ENTREGADA`

#### Scenario: Drenado de pendientes al conectar
- **WHEN** un dispositivo abre el stream y existen órdenes en estado `PENDIENTE`
- **THEN** el backend le entrega esas órdenes en orden de creación

#### Scenario: Keep-alive
- **WHEN** el stream permanece sin órdenes durante el intervalo de keep-alive
- **THEN** el backend emite un evento de ping para que la conexión no sea cerrada por
  proxies o NAT intermedios

#### Scenario: Stream sin autenticar
- **WHEN** se solicita el stream sin token o con un token vencido o inválido
- **THEN** el backend responde `401` y no abre el stream

### Requirement: Recepción de la imagen correlacionada con su orden
El backend SHALL aceptar la imagen únicamente citando el identificador de la orden que la
originó, y SHALL rechazar toda subida que no pueda correlacionarse. Una subida aceptada
SHALL pasar la orden al estado terminal `RECIBIDA`. El backend SHALL verificar la integridad
de los bytes recibidos contra el hash declarado por el cliente.

#### Scenario: Subida correlacionada correctamente
- **WHEN** el dispositivo sube el JPEG citando una orden en estado `ENTREGADA`
- **THEN** el backend responde `201` con el identificador de la captura y su URL de imagen
- **AND** la orden pasa a estado `RECIBIDA`
- **AND** la captura queda asociada al sector, la macro-zona y la posición de riel de la orden

#### Scenario: Orden inexistente
- **WHEN** la subida cita un identificador de orden que no existe
- **THEN** el backend responde `404` y descarta la imagen

#### Scenario: Reintento sobre una orden ya resuelta
- **WHEN** la subida cita una orden que ya está en estado `RECIBIDA`
- **THEN** el backend responde `409` incluyendo el identificador de la captura que ya existe,
  para que el cliente lo interprete como éxito y no como fallo
- **AND** no se crea una captura duplicada

#### Scenario: Subida desde un dispositivo distinto al de la orden
- **WHEN** el token de la subida pertenece a un dispositivo que no es aquel al que se entregó
  la orden
- **THEN** el backend responde `403` y descarta la imagen

#### Scenario: Imagen corrupta en tránsito
- **WHEN** el hash de los bytes recibidos no coincide con el hash declarado por el cliente
- **THEN** el backend responde `422`, descarta la imagen y devuelve la orden al circuito de
  reintento

### Requirement: Acuse de fallo de captura
El backend SHALL aceptar del dispositivo un acuse de fallo sobre una orden, con su motivo,
para enterarse de las capturas que no se pudieron ejecutar sin esperar al vencimiento. La
orden acusada SHALL pasar a estado `FALLIDA` y entrar al circuito de reintento.

#### Scenario: El dispositivo no pudo capturar
- **WHEN** el dispositivo acusa un fallo sobre una orden indicando el motivo
- **THEN** el backend responde `202`, registra el motivo y pasa la orden a `FALLIDA`

#### Scenario: El motivo queda disponible para diagnóstico operativo
- **WHEN** se consulta una orden que falló
- **THEN** su motivo de fallo es legible junto con el estado

### Requirement: Vencimiento de órdenes sin imagen
El backend SHALL detectar por sí mismo las órdenes que no produjeron imagen dentro de su
plazo, mediante un barrido periódico independiente de cualquier aviso del dispositivo. Una
orden vencida SHALL pasar a estado `VENCIDA`.

#### Scenario: Una orden entregada no produce imagen
- **WHEN** una orden en estado `ENTREGADA` supera su plazo de vencimiento sin recibir imagen
  ni acuse de fallo
- **THEN** el barrido periódico la marca `VENCIDA`

#### Scenario: El vencimiento no depende del dispositivo
- **WHEN** el dispositivo queda incomunicado sin avisar
- **THEN** las órdenes que le fueron entregadas vencen igual por el barrido del backend

### Requirement: Reintento acotado de órdenes no cumplidas
Una orden `VENCIDA` o `FALLIDA` SHALL volver a `PENDIENTE` para ser reintentada mientras no
supere el máximo de intentos configurado, incrementando su contador de intentos. Agotados los
intentos, la orden SHALL pasar al estado terminal `ERROR` y NO SHALL reintentarse más.

#### Scenario: Reintento dentro del máximo
- **WHEN** una orden vence y su contador de intentos es menor al máximo configurado
- **THEN** vuelve a `PENDIENTE` con su contador incrementado y se reentrega en cuanto haya un
  stream abierto

#### Scenario: Intentos agotados
- **WHEN** una orden vence habiendo alcanzado el máximo de intentos
- **THEN** pasa al estado terminal `ERROR` y no se vuelve a entregar

#### Scenario: Los estados terminales no se reintentan
- **WHEN** una orden está en `RECIBIDA` o en `ERROR`
- **THEN** el barrido de vencimiento la ignora

### Requirement: Consulta del estado de una orden
El backend SHALL permitir consultar el estado de una orden por su identificador, incluyendo
su estado actual, su contador de intentos, el motivo de fallo si lo hubo y el identificador
de la captura resultante si la produjo.

#### Scenario: Consulta de una orden cumplida
- **WHEN** se consulta una orden que ya recibió su imagen
- **THEN** la respuesta indica estado `RECIBIDA` y el identificador de la captura asociada

#### Scenario: Consulta de una orden en curso
- **WHEN** se consulta una orden aún no cumplida
- **THEN** la respuesta indica su estado actual y su contador de intentos, sin captura asociada
