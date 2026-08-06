## ADDED Requirements

### Requirement: Proyecto independiente y reemplazable
La app de cámara SHALL vivir como un proyecto propio dentro del repositorio, hermano del
frontend y del backend, con su propio manifiesto de dependencias, su propia configuración de
build y su propio toolchain. NO SHALL compartir bundle, configuración de build ni scripts con
el dashboard ni con la app de simulación. Quitar el proyecto entero NO SHALL afectar al resto
del repositorio.

#### Scenario: El proyecto se sostiene solo
- **WHEN** un desarrollador se para en el directorio de la app de cámara
- **THEN** puede instalar dependencias, levantar y compilar sin ejecutar nada del proyecto
  frontend

#### Scenario: Eliminación del proyecto
- **WHEN** se elimina el directorio de la app de cámara
- **THEN** el dashboard, la app de simulación y el backend compilan y funcionan igual, sin
  referencias colgadas

#### Scenario: La cámara no aparece en el dashboard
- **WHEN** el usuario navega el dashboard de monitoreo o la app de simulación
- **THEN** no hay ítem de cámara en la navegación ni ruta que exponga la app de captura

### Requirement: Implementación conforme al contrato de dispositivo
La app SHALL implementar el contrato de dispositivo de captura y SHALL satisfacer todas las
obligaciones que el contrato exige a un cliente conforme. NO SHALL depender de ningún endpoint
fuera del namespace del contrato.

#### Scenario: Superficie usada
- **WHEN** se auditan las peticiones que emite la app
- **THEN** todas corresponden al namespace del contrato

#### Scenario: La app pasa la suite de conformidad
- **WHEN** se ejercita la app contra un backend levantado
- **THEN** cumple el ciclo completo definido por el contrato: enrolamiento, canal de órdenes,
  subida de imagen, acuse de fallo y señal de vida

### Requirement: Inicialización de cámara mediante gesto del usuario
La aplicación SHALL solicitar el stream de cámara únicamente a partir de un gesto explícito
del operario sobre un botón de inicio, y NO SHALL intentar inicializarlo automáticamente al
cargar la página. El stream SHALL solicitarse con la cámara trasera y **pidiendo
explícitamente la resolución configurada** por el backend, como valor preferido y no exigido,
para que el navegador degrade a la mejor disponible en lugar de fallar.

> Originalmente este requisito pedía "la resolución por defecto del dispositivo". Se corrigió
> al probar con el iPhone: sin restricciones, Safari entrega 480×640 —0,3 MP—, que no tiene
> relación con lo que la cámara puede dar y es insuficiente para diagnosticar un plantín.

#### Scenario: La carga no dispara el permiso
- **WHEN** la aplicación termina de cargar
- **THEN** no se solicita permiso de cámara y se muestra el botón de inicio

#### Scenario: Inicio por gesto
- **WHEN** el operario toca el botón de inicio
- **THEN** la aplicación solicita el stream con la cámara trasera y muestra el preview en vivo

#### Scenario: Permiso denegado
- **WHEN** el operario rechaza el permiso de cámara
- **THEN** la aplicación muestra el motivo en el panel de estado y ofrece reintentar, sin
  quedar en un estado ambiguo

### Requirement: Preview en línea sin pantalla completa
El elemento de video del preview SHALL declararse reproducible en línea, silenciado y de
reproducción automática, para que el sistema operativo no lo abra en pantalla completa y
rompa la interfaz.

#### Scenario: El preview no toma la pantalla completa
- **WHEN** el stream se inicia en el iPhone
- **THEN** el video se reproduce embebido dentro del panel, sin abrirse en pantalla completa

### Requirement: Stream sostenido entre capturas
La aplicación SHALL mantener el stream de cámara abierto entre capturas y NO SHALL volver a
solicitarlo en cada disparo, salvo cuando se haya detectado que el stream dejó de estar vivo.

#### Scenario: Capturas sucesivas sobre el mismo stream
- **WHEN** se ejecutan varias capturas consecutivas
- **THEN** todas usan el mismo stream ya abierto, sin volver a pedirlo

#### Scenario: Reapertura sólo ante stream caído
- **WHEN** la pista de video deja de estar viva
- **THEN** la aplicación vuelve a solicitar el stream, y sólo en ese caso

### Requirement: Calentamiento antes de considerar la cámara lista
Tras abrir el stream, la aplicación SHALL descartar los primeros fotogramas durante el tiempo
de calentamiento que indica la configuración remota antes de declararse lista para capturar,
porque los primeros fotogramas se toman con la exposición sin converger.

#### Scenario: No se captura durante el calentamiento
- **WHEN** llega una orden dentro de la ventana de calentamiento
- **THEN** la aplicación espera a que la ventana termine antes de tomar el fotograma

#### Scenario: Estado listo tras el calentamiento
- **WHEN** transcurre el tiempo de calentamiento
- **THEN** el panel de estado indica que la cámara está lista

### Requirement: Captura mediante canvas
La aplicación SHALL obtener la imagen dibujando un fotograma del video en un canvas y
exportándolo como JPEG con la resolución y la calidad que indica la configuración remota. NO
SHALL usar la API de captura de imagen del navegador, por no existir en el navegador objetivo.

#### Scenario: Exportación del fotograma
- **WHEN** la aplicación ejecuta una captura
- **THEN** dibuja el fotograma actual del video en un canvas y produce un JPEG

#### Scenario: La resolución y la calidad vienen del backend
- **WHEN** el backend cambia la resolución máxima o la calidad JPEG
- **THEN** la siguiente captura usa los valores nuevos, sin cambios en el cliente
- **AND** si el cambio es de resolución, la aplicación reabre el stream, porque la resolución
  es del modo de captura de la cámara y no se puede cambiar sólo al exportar

#### Scenario: El límite se aplica sin importar la orientación
- **WHEN** el dispositivo entrega el video en vertical y el máximo está expresado en apaisado
- **THEN** el límite se aplica comparando lado largo con lado largo y corto con corto, sin
  descartar resolución por la diferencia de orientación

#### Scenario: La resolución obtenida queda registrada
- **WHEN** se inicializa la cámara
- **THEN** el log muestra la resolución pedida, la efectivamente obtenida y sus megapíxeles
- **AND** si el dispositivo entrega bastante menos de lo pedido, lo advierte, para no dejar
  creer que subir la configuración lo va a mejorar

### Requirement: Ajuste de exposición y balance de blancos con degradación
La aplicación SHALL intentar fijar el modo de exposición y el de balance de blancos sobre la
pista de video, SHALL tolerar sin romper que el navegador no los soporte, y SHALL registrar en
su log qué ajustes se aplicaron realmente, junto con las capacidades y los valores efectivos
de la pista.

#### Scenario: Ajustes soportados
- **WHEN** el navegador acepta los ajustes de exposición y balance de blancos
- **THEN** se aplican y el log registra cuáles quedaron efectivamente fijados

#### Scenario: Ajustes no soportados
- **WHEN** el navegador rechaza los ajustes
- **THEN** la aplicación continúa operando normalmente, la captura no se aborta, y el log
  registra que no pudieron aplicarse

#### Scenario: Evidencia para evaluar la consistencia entre imágenes
- **WHEN** el operario revisa el log del dispositivo
- **THEN** encuentra las capacidades declaradas por la pista y sus valores efectivos, que es
  la información necesaria para juzgar si la consistencia fotométrica alcanza

### Requirement: Recuperación tras perder el primer plano
La aplicación SHALL escuchar los cambios de visibilidad y, al volver a estado visible, SHALL
verificar que la pista de video siga viva; si no lo está, SHALL reinicializar el stream y
reabrir el canal de órdenes. La aplicación NO SHALL asumir en ningún momento que el stream
sigue vivo.

#### Scenario: Vuelta al primer plano con el stream suspendido
- **WHEN** la aplicación vuelve a estado visible y la pista de video ya no está viva
- **THEN** reinicializa el stream, vuelve a calentar la cámara y reabre el canal de órdenes

#### Scenario: Vuelta al primer plano con el stream intacto
- **WHEN** la aplicación vuelve a estado visible y la pista sigue viva
- **THEN** continúa operando sin reinicializar

### Requirement: Bloqueo de apagado de pantalla con degradación silenciosa
La aplicación SHALL solicitar el bloqueo de apagado de pantalla cuando el navegador lo
soporte, SHALL volver a solicitarlo al recuperar el primer plano —porque el bloqueo se pierde
al perderlo— y SHALL continuar funcionando sin error visible cuando la API no esté disponible.

#### Scenario: Bloqueo disponible
- **WHEN** el navegador soporta el bloqueo de pantalla y la aplicación está activa
- **THEN** la pantalla no se apaga sola

#### Scenario: Bloqueo no disponible
- **WHEN** el navegador no soporta la API de bloqueo de pantalla
- **THEN** la aplicación arranca igual, sin error, y lo registra en su log

#### Scenario: Recuperación del bloqueo
- **WHEN** la aplicación pierde y recupera el primer plano
- **THEN** vuelve a solicitar el bloqueo de pantalla

### Requirement: Almacén persistente apto para binarios
La aplicación SHALL cumplir la obligación del contrato de conservar las imágenes que no pudo
enviar usando un almacén del navegador apto para datos binarios, y NO SHALL usar
almacenamiento clave-valor de texto, que no sirve para blobs. El almacén SHALL estar acotado
en cantidad y en tamaño, descartando las entradas más antiguas al llegar al tope, y la
aplicación SHALL solicitar al navegador que lo trate como persistente.

#### Scenario: Respaldo de una imagen no enviada
- **WHEN** una imagen agota sus reintentos inmediatos
- **THEN** queda guardada en el almacén binario y la aplicación sigue operando

#### Scenario: Tope del almacén
- **WHEN** el almacén alcanza su tope de cantidad o de tamaño
- **THEN** la aplicación descarta las entradas más antiguas y lo registra en su log, en lugar
  de crecer sin límite

#### Scenario: Solicitud de persistencia
- **WHEN** la aplicación arranca
- **THEN** solicita al navegador que su almacenamiento sea persistente, para reducir la chance
  de que el sistema lo desaloje

### Requirement: Credencial del dispositivo conservada localmente
La aplicación SHALL conservar la credencial obtenida en el enrolamiento en almacenamiento
local del dispositivo, NO SHALL contener credenciales de larga duración en su código fuente, y
SHALL volver a pedir el código de vinculación cuando el backend rechace la credencial guardada.

#### Scenario: Primer arranque
- **WHEN** la aplicación arranca sin credencial guardada
- **THEN** pide el código de vinculación antes de habilitar la operación

#### Scenario: Arranques siguientes
- **WHEN** la aplicación arranca con credencial guardada
- **THEN** obtiene un token de acceso y opera sin volver a pedir el código

#### Scenario: Credencial rechazada
- **WHEN** el backend rechaza la credencial guardada
- **THEN** la aplicación la descarta y vuelve a pedir el código de vinculación

### Requirement: Panel de estado del dispositivo
La aplicación SHALL mostrar en pantalla el preview en vivo, un indicador bien visible del
estado de conexión distinguiendo conectado, reconectando y desconectado, la miniatura de la
última captura con su marca temporal y su identificador, los contadores de capturas exitosas y
fallidas de la sesión, un botón de captura manual y un log de eventos desplazable.

#### Scenario: Estado de conexión visible
- **WHEN** la conexión cambia de estado
- **THEN** el indicador lo refleja de inmediato y de forma legible a distancia

#### Scenario: Última captura
- **WHEN** una captura se completa
- **THEN** el panel muestra su miniatura, su marca temporal y su identificador, e incrementa el
  contador de exitosas

#### Scenario: Captura manual de prueba
- **WHEN** el operario toca el botón de captura manual
- **THEN** la aplicación ejecuta una captura de prueba y la registra en el log

#### Scenario: Log en pantalla
- **WHEN** ocurren eventos de cámara, conexión, captura o envío
- **THEN** quedan registrados en un log desplazable dentro de la propia pantalla, sin necesidad
  de herramientas de desarrollo externas

#### Scenario: Aplicación no operativa
- **WHEN** la aplicación pierde el canal o la cámara y deja de poder responder órdenes
- **THEN** lo indica de forma inequívoca, porque sin ella en primer plano el sistema no captura

### Requirement: Empaquetado como PWA instalable
La aplicación SHALL incluir un manifiesto con presentación autónoma, orientación fijada e
íconos, y SHALL funcionar correctamente agregada a la pantalla de inicio.

#### Scenario: Instalación en la pantalla de inicio
- **WHEN** el operario agrega la aplicación a la pantalla de inicio y la abre desde ahí
- **THEN** se abre en modo autónomo, sin la barra del navegador, con la orientación fijada

### Requirement: Service worker que cachea el shell y nunca la API ni las imágenes
El service worker SHALL cachear únicamente el shell de la aplicación y NO SHALL cachear las
llamadas a la API ni las imágenes de captura. NO SHALL interceptar peticiones de escritura.

#### Scenario: Shell disponible desde caché
- **WHEN** la aplicación se abre con la red degradada
- **THEN** el shell carga desde caché

#### Scenario: Las órdenes nunca se sirven desde caché
- **WHEN** la aplicación consulta el backend por órdenes o configuración
- **THEN** la petición va a la red y nunca se responde con una copia cacheada

#### Scenario: Las subidas no se interceptan
- **WHEN** la aplicación sube una imagen
- **THEN** el service worker no intercepta la petición
