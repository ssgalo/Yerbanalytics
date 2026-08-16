## ADDED Requirements

### Requirement: Panel de cámara dentro del simulador
El simulador SHALL incluir una sección de cámara para probar el dispositivo de captura,
servida en la misma aplicación que el resto de los controles de simulación. Esta sección NO
SHALL exponerse en el dashboard de monitoreo.

#### Scenario: La sección vive en el simulador
- **WHEN** el usuario abre el simulador
- **THEN** encuentra la sección de cámara junto al resto de los controles de simulación

#### Scenario: La sección no aparece en el dashboard
- **WHEN** el usuario navega el dashboard de monitoreo
- **THEN** no hay sección de cámara ni ruta que la exponga

### Requirement: El panel de cámara no tiene superficie de API propia
El panel de cámara SHALL operar exclusivamente con endpoints que la plataforma expone para
sus propios emisores: el de emisión de órdenes, el de consulta de órdenes, el de servido de
imágenes, el de dispositivos de captura y el de alta de diagnósticos. NO SHALL existir ningún
endpoint, parámetro ni rama de código en el backend que exista para servir al panel.

#### Scenario: Superficie exclusiva vacía
- **WHEN** se audita la API que consume el panel de cámara
- **THEN** ningún endpoint es exclusivo del simulador

#### Scenario: El backend no conoce al panel
- **WHEN** se revisan los servicios de captura y de diagnóstico del backend
- **THEN** ninguno contiene lógica condicionada a que el llamante sea el simulador

#### Scenario: El backend no depende del panel
- **WHEN** el simulador no se ejecuta
- **THEN** las órdenes de captura, la recepción de imágenes y el dashboard funcionan igual

### Requirement: Visibilidad del dispositivo de cámara
El panel SHALL mostrar el estado del dispositivo de cámara —operativo, intermitente o fuera
de servicio— y el instante de su última señal de vida, y SHALL permitir generar un código de
vinculación para enrolar un dispositivo nuevo.

#### Scenario: Estado del dispositivo
- **WHEN** el usuario abre el panel de cámara
- **THEN** ve el estado del dispositivo y cuándo reportó por última vez

#### Scenario: Generación del código de vinculación
- **WHEN** el usuario pide un código de vinculación
- **THEN** el panel lo muestra junto con su vencimiento, para tipearlo en el dispositivo

#### Scenario: Sin dispositivo enrolado
- **WHEN** no hay ningún dispositivo de cámara enrolado
- **THEN** el panel lo indica y ofrece generar el código, en lugar de mostrar un estado vacío
  sin explicación

### Requirement: Solicitud manual de una captura de prueba
El panel SHALL permitir emitir una orden de captura eligiendo el sector, su macro-zona y la
posición de riel, usando el mismo endpoint de emisión de órdenes que empleará cualquier otro
emisor. El panel SHALL mostrar el avance de la orden hasta que se resuelva.

#### Scenario: Emisión de la orden
- **WHEN** el usuario elige sector, macro-zona y posición de riel y pide la captura
- **THEN** el panel emite la orden y muestra su identificador y su estado

#### Scenario: Seguimiento hasta la imagen
- **WHEN** la orden avanza de pendiente a entregada y a recibida
- **THEN** el panel refleja cada estado y, al recibirse, muestra la imagen capturada

#### Scenario: Orden que no se cumple
- **WHEN** la orden vence, falla o agota sus reintentos
- **THEN** el panel muestra el estado final y el motivo, sin quedar esperando indefinidamente

#### Scenario: Las macro-zonas y sectores son los de la topología vigente
- **WHEN** el usuario abre el formulario de captura
- **THEN** las opciones de macro-zona y sector corresponden a la topología actual del vivero

### Requirement: Carga manual del diagnóstico sobre la captura recibida
Recibida la imagen, el panel SHALL permitir cargar a mano el diagnóstico que el modelo de IA
devolvería —estado, severidad y nivel de confianza— junto con el sector y la macro-zona,
precargados desde la orden y editables. Al confirmarlo, el panel SHALL dar de alta el
diagnóstico por el mismo endpoint público que usará el servicio de inferencia, con los mismos
campos.

#### Scenario: Carga del diagnóstico
- **WHEN** el usuario carga estado, severidad y nivel de confianza sobre una captura recibida
  y confirma
- **THEN** el panel da de alta el diagnóstico por el endpoint público, asociado a esa captura

#### Scenario: El alta es indistinguible de la del modelo
- **WHEN** se compara la petición que emite el panel con la que emitirá el servicio de
  inferencia
- **THEN** son la misma operación sobre el mismo endpoint, con los mismos campos, y el
  diagnóstico resultante no lleva ninguna marca que lo diferencie

#### Scenario: Sector y macro-zona precargados y editables
- **WHEN** el usuario abre el formulario de diagnóstico sobre una captura recibida
- **THEN** el sector y la macro-zona vienen precargados desde la orden y pueden modificarse
  antes de confirmar

#### Scenario: Sin captura recibida
- **WHEN** no hay una captura recibida sobre la cual diagnosticar
- **THEN** el panel no permite confirmar, porque todo diagnóstico requiere su captura

#### Scenario: Datos inválidos
- **WHEN** el nivel de confianza cargado queda fuera del rango válido o el estado no pertenece
  a la taxonomía
- **THEN** el panel muestra el error devuelto por el backend y no registra el diagnóstico

### Requirement: El diagnóstico cargado aparece en el dashboard
Un diagnóstico cargado desde el panel de cámara SHALL aparecer como una tarjeta más en la
vista de Diagnósticos de IA del dashboard, con su imagen real, su estado, su severidad y su
nivel de confianza, indistinguible de uno emitido por el modelo tanto en su presentación como
en los datos persistidos.

#### Scenario: La tarjeta aparece en Diagnósticos de IA
- **WHEN** el usuario carga un diagnóstico y luego abre la vista de Diagnósticos de IA del
  dashboard contra el backend real
- **THEN** ve una tarjeta nueva con ese estado, esa severidad y ese nivel de confianza

#### Scenario: La imagen real se ve en el detalle
- **WHEN** el usuario abre esa tarjeta
- **THEN** el modal muestra la fotografía capturada por la cámara, no un marcador de posición

#### Scenario: El recorrido ensayado es el recorrido real
- **WHEN** en el futuro el servicio de inferencia emita un diagnóstico sobre una captura
- **THEN** recorre exactamente los mismos pasos que se ensayaron a mano, sin requerir cambios
  en el backend ni en el dashboard
