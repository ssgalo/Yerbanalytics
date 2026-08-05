## ADDED Requirements

### Requirement: Persistencia de órdenes de captura
El backend SHALL persistir cada orden de captura con su identificador, sector, macro-zona,
posición de riel, dispositivo destinatario, estado, contador de intentos, motivo de fallo,
instante de creación, instante de entrega e instante de vencimiento. El estado de las órdenes
SHALL sobrevivir a un reinicio del backend.

#### Scenario: Las órdenes pendientes sobreviven al reinicio
- **WHEN** el backend se reinicia con órdenes en estado `PENDIENTE`
- **THEN** al abrirse un stream esas órdenes se entregan igual, sin haberse perdido

#### Scenario: Las órdenes entregadas siguen venciendo tras el reinicio
- **WHEN** el backend se reinicia con órdenes en estado `ENTREGADA`
- **THEN** el barrido de vencimiento las evalúa contra su plazo original

### Requirement: Imagen en filesystem con metadata en base
El backend SHALL almacenar el JPEG de cada captura en el filesystem, bajo un directorio raíz
configurable particionado por fecha, y SHALL persistir en la base la fila de metadata con la
orden de origen, el sector, la macro-zona, la posición de riel, el dispositivo, el ancho, el
alto, el tamaño en bytes, el hash SHA-256, el instante de captura, el instante de recepción y
la ruta del archivo. El JPEG NO SHALL almacenarse como columna binaria en la base.

#### Scenario: Archivo y metadata quedan asociados
- **WHEN** se recibe una imagen correlacionada con su orden
- **THEN** el JPEG queda escrito en el directorio de capturas bajo la partición de su fecha
- **AND** la fila de metadata registra la ruta del archivo y el resto de los datos de la
  captura

#### Scenario: El archivo se escribe antes que la fila
- **WHEN** falla la escritura del archivo en disco
- **THEN** no se crea la fila de metadata y la subida se rechaza, evitando filas que apunten a
  archivos inexistentes

#### Scenario: Directorio de capturas no escribible
- **WHEN** el backend arranca y el directorio raíz de capturas no existe y no puede crearse, o
  no es escribible
- **THEN** el arranque falla con un mensaje explícito, en lugar de fallar recién en la primera
  subida

### Requirement: Servido de la imagen de una captura
El backend SHALL exponer un endpoint que devuelva los bytes del JPEG de una captura por su
identificador, con el tipo de contenido correspondiente. Dado que el contenido de una captura
nunca cambia, la respuesta SHALL declararse cacheable de forma inmutable e incluir un
validador basado en el hash de la imagen.

#### Scenario: Descarga de la imagen
- **WHEN** se solicita la imagen de una captura existente
- **THEN** el backend responde `200` con los bytes del JPEG y `Content-Type: image/jpeg`

#### Scenario: Captura inexistente
- **WHEN** se solicita la imagen de una captura que no existe
- **THEN** el backend responde `404`

#### Scenario: Archivo faltante en disco
- **WHEN** existe la fila de metadata pero el archivo no está en el filesystem
- **THEN** el backend responde `404` y registra el incidente en el log del servidor

### Requirement: Persistencia del registro de dispositivos de cámara
El backend SHALL persistir cada dispositivo de cámara con su identificador, nombre, la huella
de su credencial de renovación, su estado operativo, el instante de su último heartbeat y sus
contadores de capturas exitosas y fallidas. La credencial de renovación NO SHALL almacenarse
en claro.

#### Scenario: El enrolamiento sobrevive al reinicio
- **WHEN** el backend se reinicia
- **THEN** el dispositivo ya enrolado renueva su token sin volver a vincularse

#### Scenario: La credencial no se guarda en claro
- **WHEN** se inspecciona la fila del dispositivo en la base
- **THEN** la credencial de renovación figura como huella derivada, no como valor recuperable

### Requirement: Persistencia de diagnósticos
El backend SHALL persistir cada diagnóstico con su identificador, sector, macro-zona, captura
asociada, estado, nivel de confianza, severidad e instante de creación. La captura asociada
SHALL ser obligatoria a nivel de esquema. La tabla NO SHALL incluir ninguna columna que
distinga el origen del diagnóstico ni que exista únicamente para diferenciar los ensayos de la
operación real.

#### Scenario: Diagnóstico con captura asociada
- **WHEN** se persiste un diagnóstico referenciando una captura
- **THEN** la fila conserva el vínculo y permite recuperar la imagen que lo originó

#### Scenario: La captura es obligatoria en el esquema
- **WHEN** se intenta persistir un diagnóstico sin captura asociada
- **THEN** la restricción de la base lo impide, además de la validación de la aplicación

#### Scenario: El esquema no distingue el origen
- **WHEN** se inspecciona la tabla de diagnósticos
- **THEN** no existe columna alguna que indique si el diagnóstico fue cargado a mano o emitido
  por el modelo, porque ambos son la misma operación

### Requirement: Creación de tablas sin afectar el esquema existente
Las tablas de este cambio SHALL ser tablas nuevas y NO SHALL modificar ni eliminar columnas de
las tablas existentes. El DDL correspondiente SHALL quedar documentado en el archivo de
migración manual del backend, siguiendo la práctica ya establecida en el repositorio.

#### Scenario: El esquema existente no se altera
- **WHEN** se aplica este cambio sobre una base ya poblada
- **THEN** las tablas de sectores, zonas, dispositivos, historial y configuración quedan
  intactas

#### Scenario: El DDL queda documentado
- **WHEN** un desarrollador consulta el archivo de migración manual
- **THEN** encuentra el DDL de las tablas nuevas
