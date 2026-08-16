## ADDED Requirements

### Requirement: Proyecto Android independiente y reemplazable
La app de cámara Android SHALL vivir como un proyecto propio dentro del repositorio, hermano del
frontend, del backend y de la app de cámara web, con su propio build, su propio manifiesto de
dependencias y su propio toolchain. NO SHALL compartir build, configuración ni scripts con ningún
otro proyecto del repositorio. Quitar el proyecto entero NO SHALL afectar al resto.

#### Scenario: El proyecto se sostiene solo
- **WHEN** un desarrollador se para en el directorio de la app Android
- **THEN** puede compilar y generar la APK sin ejecutar nada de los demás proyectos del repositorio

#### Scenario: Eliminación del proyecto
- **WHEN** se elimina el directorio de la app Android
- **THEN** el dashboard, el backend, el simulador y la app de cámara web compilan y funcionan
  igual, sin referencias colgadas

#### Scenario: Coexistencia con la app de cámara web
- **WHEN** la app Android existe en el repositorio
- **THEN** la app de cámara web sigue funcionando sin modificaciones, y ambas pueden estar
  enroladas contra el mismo backend al mismo tiempo

### Requirement: Implementación conforme al contrato de dispositivo, sin cambios en el backend
La app SHALL implementar el contrato de dispositivo de captura y SHALL satisfacer todas las
obligaciones que ese contrato exige a un cliente conforme. NO SHALL emitir ninguna petición fuera
del namespace versionado del contrato. Su puesta en funcionamiento NO SHALL requerir modificar el
backend: ni endpoints nuevos, ni propiedades nuevas, ni ramas de código que distingan al cliente
Android de cualquier otro.

#### Scenario: Superficie usada
- **WHEN** se auditan las peticiones que emite la app
- **THEN** todas corresponden al namespace versionado del contrato

#### Scenario: El backend no se entera de qué cliente es
- **WHEN** la app opera contra un backend sin modificar
- **THEN** completa el ciclo del contrato —enrolamiento, obtención de token, configuración, canal
  de órdenes, subida de imagen, acuse de fallo y señal de vida— sin ningún tratamiento especial

#### Scenario: Campos y eventos desconocidos
- **WHEN** el backend agrega un campo opcional a una respuesta o un evento nuevo al canal
- **THEN** la app los ignora sin fallar y sigue operando

### Requirement: Autenticación por header en todos los endpoints
La app SHALL enviar la credencial de acceso en el header de autorización en **todos** los endpoints
del contrato, incluido el canal de órdenes. NO SHALL usar la alternativa por query string, que el
contrato admite sólo para clientes que no pueden fijar headers.

#### Scenario: Canal de órdenes autenticado por header
- **WHEN** la app abre el canal de órdenes
- **THEN** la credencial viaja en el header de autorización y no aparece en la URL

### Requirement: Renovación proactiva de la credencial de acceso
La app SHALL renovar la credencial de acceso antes de que expire, sin esperar a recibir un rechazo.
Ante un rechazo por credencial inválida SHALL renovar y reintentar la operación una vez; si la
renovación también falla, SHALL descartar la credencial persistida y volver a pedir el código de
vinculación.

#### Scenario: Renovación antes del vencimiento
- **WHEN** se acerca el vencimiento de la credencial de acceso
- **THEN** la app la renueva sin que ninguna operación falle por vencimiento

#### Scenario: Credencial de renovación revocada
- **WHEN** el dispositivo se da de baja desde la plataforma y la app intenta renovar
- **THEN** la app descarta la credencial guardada, lo registra en su log y vuelve a la pantalla de
  vinculación

### Requirement: Operación sostenida en segundo plano con la pantalla apagada
La app SHALL sostener el canal de órdenes, la cola de envío y la señal de vida mediante un servicio
en primer plano que sobreviva a que la interfaz deje de estar visible, con la pantalla apagada y el
teléfono bloqueado. La app NO SHALL requerir que la interfaz esté en pantalla para responder una
orden de captura.

#### Scenario: Captura con la pantalla apagada
- **WHEN** el operario apaga la pantalla del teléfono y el backend emite una orden de captura
- **THEN** la app recibe la orden, toma la imagen y la entrega, sin intervención del operario

#### Scenario: La interfaz se cierra
- **WHEN** el operario cierra la pantalla de la app desde el gestor de tareas del sistema
- **THEN** el servicio sigue operando y el dispositivo continúa respondiendo órdenes

#### Scenario: Notificación permanente de estado
- **WHEN** el servicio está operando
- **THEN** el sistema muestra una notificación persistente que indica el estado del dispositivo, y
  desde ella se puede volver a la pantalla de la app

### Requirement: Cámara en standby, encendida sólo mientras hay trabajo
La app NO SHALL mantener la cámara abierta en reposo. SHALL abrirla al recibir una orden con la
cámara cerrada, SHALL reutilizar la sesión ya abierta para las órdenes que lleguen a continuación,
y SHALL cerrarla al transcurrir una ventana de inactividad configurable sin órdenes nuevas,
liberando el hardware. La ventana NO SHALL estar fijada en el código.

#### Scenario: Reposo sin cámara
- **WHEN** la app está esperando órdenes y la ventana de inactividad ya transcurrió
- **THEN** la cámara está cerrada y el indicador de cámara en uso del sistema no está activo

#### Scenario: Primera orden de una ráfaga
- **WHEN** llega una orden con la cámara cerrada
- **THEN** la app abre la cámara, espera a que converjan los ajustes automáticos, toma la imagen y
  la entrega

#### Scenario: Órdenes sucesivas de una misma pasada
- **WHEN** llegan órdenes consecutivas dentro de la ventana de inactividad
- **THEN** todas se resuelven sobre la sesión de cámara ya abierta, sin reabrirla entre disparos

#### Scenario: Cierre por inactividad
- **WHEN** transcurre la ventana de inactividad sin órdenes nuevas
- **THEN** la app cierra la cámara y lo registra en su log

#### Scenario: La cámara no pudo abrirse
- **WHEN** la cámara no puede abrirse porque otra app la tiene tomada o falta el permiso
- **THEN** la app acusa el fallo de la orden con el motivo tipificado de cámara no lista y lo
  registra en su log

### Requirement: Calentamiento por convergencia, no por tiempo fijo
Antes de tomar la imagen la app SHALL esperar a que converjan los ajustes automáticos de la cámara
—exposición, foco y balance de blancos— y SHALL usar el tiempo de calentamiento de la configuración
remota como **plazo máximo** de esa espera, no como espera fija. Cuando la convergencia ya esté
alcanzada por una captura anterior de la misma ráfaga, NO SHALL volver a esperarla.

#### Scenario: Convergencia antes del plazo
- **WHEN** los ajustes automáticos convergen antes de agotar el tiempo de calentamiento configurado
- **THEN** la app dispara en ese momento, sin esperar el resto del plazo

#### Scenario: Convergencia que no llega
- **WHEN** los ajustes automáticos no convergen dentro del tiempo de calentamiento configurado
- **THEN** la app dispara igual al vencer el plazo y registra en el log que la convergencia no se
  alcanzó, en lugar de abandonar la captura

### Requirement: Interfaz mínima de log y visor efímero
La pantalla de la app SHALL mostrar un log de eventos desplazable con lo que va ocurriendo —cámara,
canal, captura, envío y credenciales—, el estado de conexión distinguiendo conectado, reconectando
y desconectado, y los contadores de capturas exitosas y fallidas. El visor de cámara SHALL
mostrarse **únicamente** mientras hay una captura en curso y SHALL desaparecer al terminarla. La
pantalla NO SHALL incluir vistas de dashboard, diagnóstico ni configuración agronómica.

#### Scenario: Visor sólo durante la captura
- **WHEN** la app está en reposo esperando órdenes
- **THEN** no se muestra ningún visor de cámara en pantalla

#### Scenario: Visor durante la captura
- **WHEN** llega una orden con la pantalla de la app visible
- **THEN** aparece el visor mientras dura la captura y desaparece al completarse

#### Scenario: Log legible desde el propio teléfono
- **WHEN** ocurre cualquier evento de cámara, canal, captura, envío o credenciales
- **THEN** queda registrado con su marca temporal en el log de la pantalla, sin necesidad de
  herramientas de desarrollo externas

### Requirement: Log persistido en archivo rotativo y exportable
Además de la ventana visible en pantalla, la app SHALL escribir su log en archivos del
almacenamiento de la aplicación, acotados en cantidad y en tamaño y rotados al llegar al tope.
SHALL permitir exportarlos desde la propia pantalla y SHALL dejarlos accesibles por las
herramientas de depuración del sistema. El log persistido NO SHALL limitarse a lo que entra en la
ventana visible.

#### Scenario: Diagnóstico de un fallo pasado
- **WHEN** el operario investiga una falla ocurrida hace horas, con el log de pantalla ya
  desbordado
- **THEN** encuentra los eventos de ese momento en los archivos de log del dispositivo

#### Scenario: Rotación acotada
- **WHEN** los archivos de log alcanzan su tope de tamaño o de cantidad
- **THEN** la app rota descartando los más antiguos, sin crecer sin límite

#### Scenario: Exportación del log
- **WHEN** el operario elige exportar el log desde la pantalla
- **THEN** la app entrega el contenido persistido por los mecanismos de compartición del sistema

### Requirement: Canal de órdenes sostenido con reconexión automática y watchdog
La app SHALL mantener el canal de órdenes abierto mientras esté operativa y SHALL reconectarlo
automáticamente ante cortes, con espera creciente y acotada. NO SHALL asumir que el canal sigue
vivo por no haber recibido un error: SHALL reconectar cuando transcurra un múltiplo de la cadencia
configurada sin recibir ningún evento.

#### Scenario: Reconexión tras corte de red
- **WHEN** la red se cae y vuelve
- **THEN** la app reabre el canal sola, sin intervención del operario, y lo registra en el log

#### Scenario: Canal muerto sin error
- **WHEN** el canal deja de entregar eventos pero la conexión no reporta error
- **THEN** el watchdog lo detecta por ausencia de eventos y fuerza la reconexión

#### Scenario: Órdenes acumuladas
- **WHEN** la app reabre el canal después de haber estado desconectada
- **THEN** recibe las órdenes pendientes acumuladas y las ejecuta en el orden en que llegaron

### Requirement: Cola de órdenes acotada, persistente y con acuse de descarte
Una orden que llegue mientras hay una captura en curso SHALL encolarse, no descartarse, y las
órdenes encoladas SHALL ejecutarse en el orden en que llegaron. La cola SHALL persistirse en el
almacenamiento del dispositivo, de modo que las órdenes ya entregadas y todavía sin ejecutar
sobrevivan a la muerte del proceso y se retomen sin esperar a que el backend las venza. Al alcanzar
el tope que indica la configuración remota, la app SHALL descartar y SHALL acusar el fallo con el
motivo tipificado de cola llena. NO SHALL descartar ninguna orden en silencio.

#### Scenario: Orden durante una captura
- **WHEN** llega una orden mientras otra captura está en curso
- **THEN** la orden se encola y se ejecuta al terminar la anterior

#### Scenario: El proceso muere con órdenes encoladas
- **WHEN** el proceso muere con órdenes ya entregadas y sin ejecutar, y el servicio vuelve a
  levantar
- **THEN** la app retoma esas órdenes y las ejecuta si todavía no vencieron, sin esperar a que el
  backend las reintente

#### Scenario: Orden ya vencida al retomarse
- **WHEN** una orden persistida se retoma después de su vencimiento
- **THEN** la app no la captura y la descarta, dejando constancia en el log

#### Scenario: Cola en su tope
- **WHEN** la cola de órdenes alcanza el tope configurado y llega otra orden
- **THEN** la app descarta una orden, acusa el fallo con el motivo de cola llena y lo registra en
  el log

### Requirement: Cola de envío persistente en disco y acotada
La app SHALL reintentar el envío de una imagen con espera creciente ante fallo de red y SHALL
conservar en almacenamiento persistente del dispositivo las imágenes que no pudo entregar,
reintentándolas al recuperar conectividad. La cola SHALL estar acotada en cantidad y en tamaño,
descartando lo más antiguo al llegar al tope, y SHALL sobrevivir al cierre de la app y al reinicio
del teléfono.

#### Scenario: Imagen no entregada
- **WHEN** una imagen agota sus reintentos inmediatos
- **THEN** queda guardada en disco, la app lo registra y sigue operando

#### Scenario: Drenaje al volver la red
- **WHEN** la conectividad se restablece con imágenes pendientes en disco
- **THEN** la app las entrega y las borra del almacenamiento local

#### Scenario: La cola sobrevive al reinicio
- **WHEN** el teléfono se reinicia con imágenes pendientes de entrega
- **THEN** al volver el servicio las imágenes siguen en disco y se intentan de nuevo

#### Scenario: Tope de la cola
- **WHEN** la cola alcanza su tope de cantidad o de tamaño
- **THEN** la app descarta las entradas más antiguas y lo registra, en lugar de crecer sin límite

### Requirement: Subida duplicada tratada como éxito
La app SHALL tratar el rechazo por orden ya cumplida como un envío exitoso: SHALL quitar la imagen
de su cola y NO SHALL contarla como fallo ni reintentarla.

#### Scenario: Respuesta perdida y reintento
- **WHEN** una subida llega al backend pero su respuesta se pierde, y la app reintenta
- **THEN** el rechazo por orden ya cumplida se registra como éxito, la imagen sale de la cola y el
  contador de fallos no se incrementa

### Requirement: Configuración de captura tomada del backend
La app SHALL tomar del backend la resolución máxima, la calidad de compresión, el tiempo de
calentamiento, la cadencia de señal de vida y el tope de la cola de órdenes. NO SHALL fijar esos
valores en su código. SHALL aplicar un cambio de configuración recibido por el canal en la captura
siguiente, sin reiniciarse ni requerir intervención física sobre el teléfono.

#### Scenario: Configuración al arrancar
- **WHEN** el servicio arranca
- **THEN** consulta la configuración vigente antes de declararse listo para capturar

#### Scenario: Cambio de configuración en caliente
- **WHEN** el backend cambia la resolución o la calidad y lo anuncia por el canal
- **THEN** la captura siguiente usa los valores nuevos, sin reiniciar la app ni tocar el teléfono

#### Scenario: La resolución obtenida queda registrada
- **WHEN** se ejecuta una captura
- **THEN** el log registra la resolución pedida y la efectivamente obtenida, y advierte si el
  equipo entregó bastante menos de lo pedido

### Requirement: Metadata veraz de la imagen entregada
La app SHALL declarar en la metadata de cada subida el ancho y el alto **reales** del archivo
adjunto y el hash de sus bytes tal como se envían. SHALL registrar además los ajustes de cámara que
efectivamente pudo aplicar, para poder evaluar la consistencia entre capturas.

#### Scenario: Hash verificable
- **WHEN** la app sube una imagen
- **THEN** el backend verifica el hash declarado contra los bytes recibidos y la acepta

#### Scenario: Evidencia fotométrica
- **WHEN** el operario revisa el log tras una captura
- **THEN** encuentra los ajustes de cámara que se aplicaron realmente en ese disparo

### Requirement: Señal de vida honesta sobre la capacidad de capturar
La app SHALL emitir la señal de vida con la cadencia que indica la configuración remota,
informando si está en condiciones de capturar en ese momento, junto con sus contadores de capturas
exitosas, fallidas y pendientes de envío. NO SHALL informar que está en condiciones de capturar
cuando no puede hacerlo.

#### Scenario: Cadencia de la señal
- **WHEN** el servicio está operando
- **THEN** emite la señal de vida con la cadencia configurada

#### Scenario: Dispositivo sin cámara disponible
- **WHEN** la app no puede acceder a la cámara por falta de permiso o de habilitación del sistema
- **THEN** su señal de vida informa que no está en condiciones de capturar, y el motivo queda en el
  log y en la notificación

### Requirement: Recuperación tras reinicio del teléfono, degradando antes que mentir
La app SHALL volver a operar sola tras un reinicio del teléfono, sin que el operario tenga que
abrirla. Cuando el sistema le niegue el acceso a cámara por haber arrancado sin interfaz visible,
SHALL operar de forma degradada —sosteniendo el canal y la señal de vida, informando que no puede
capturar y acusando las órdenes que reciba— y SHALL indicar en su notificación que hace falta abrir
la app una vez. Al abrirse la interfaz SHALL promoverse a operación normal.

#### Scenario: Reinicio con acceso a cámara concedido
- **WHEN** el teléfono se reinicia
- **THEN** el servicio vuelve solo, reabre el canal y queda listo para capturar

#### Scenario: Reinicio con acceso a cámara negado
- **WHEN** el sistema niega el acceso a cámara al servicio arrancado tras el reinicio
- **THEN** la app opera degradada, informa que no puede capturar, acusa las órdenes que lleguen y
  pide en su notificación que se la abra una vez

#### Scenario: Promoción al abrir la interfaz
- **WHEN** el operario abre la app estando en modo degradado
- **THEN** la app recupera el acceso a cámara y pasa a operar normalmente, sin volver a vincularse

### Requirement: Credencial del dispositivo conservada localmente
La app SHALL conservar la credencial obtenida en el enrolamiento en almacenamiento local del
dispositivo y NO SHALL contener credenciales de larga duración en su código fuente. La pantalla de
vinculación SHALL pedirse una única vez en la vida del dispositivo, salvo que el backend rechace la
credencial guardada.

#### Scenario: Primer arranque
- **WHEN** la app arranca sin credencial guardada
- **THEN** pide la dirección del backend y el código de vinculación antes de habilitar la operación

#### Scenario: Arranques siguientes
- **WHEN** la app arranca con credencial guardada
- **THEN** obtiene la credencial de acceso y opera sin volver a pedir el código

### Requirement: Dirección del backend configurable sin recompilar
La app SHALL permitir configurar la dirección del backend en tiempo de ejecución, al vincularse, y
SHALL admitir tanto una dirección sin cifrado en la red local como una dirección con cifrado, sin
requerir una compilación distinta para cada caso. NO SHALL llevar ninguna dirección de red fija en
su código.

#### Scenario: Cambio de dirección del backend
- **WHEN** cambia la dirección de la máquina que corre el backend
- **THEN** el operario puede apuntar la app a la nueva dirección sin recompilar la aplicación

#### Scenario: Backend con cifrado
- **WHEN** se configura una dirección cifrada cuyo certificado firma una autoridad instalada en el
  teléfono
- **THEN** la app opera contra ella con la misma APK, sin cambios en el código

### Requirement: APK generable e instalable con procedimiento documentado
El proyecto SHALL producir una APK instalable con un único comando de build, y su README SHALL
documentar el procedimiento completo: qué instalar para compilar, cómo generar la APK desde el
entorno de desarrollo y desde la línea de comandos, cómo instalarla en el teléfono, y **qué
opciones del sistema Android hay que habilitar** para operar una app de desarrollo instalada fuera
de la tienda —permisos, instalación de orígenes desconocidos, exención de optimización de batería y
notificaciones—, incluyendo el porqué de cada una.

#### Scenario: Build reproducible
- **WHEN** un desarrollador ejecuta el comando de build documentado sobre un clon limpio
- **THEN** obtiene una APK instalable, sin pasos manuales no documentados

#### Scenario: Puesta en marcha guiada
- **WHEN** el operario sigue el README de punta a punta sobre un teléfono nuevo
- **THEN** llega a un dispositivo enrolado que responde una orden de captura emitida desde el
  simulador

#### Scenario: Ajustes del sistema documentados
- **WHEN** el operario busca por qué el dispositivo deja de responder con el teléfono quieto
- **THEN** el README identifica la optimización de batería como causa y el ajuste concreto a
  cambiar
