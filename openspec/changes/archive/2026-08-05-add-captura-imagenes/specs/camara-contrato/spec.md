## ADDED Requirements

### Requirement: Superficie del contrato acotada y versionada
Todo lo que un dispositivo de captura necesita para operar SHALL vivir bajo un namespace
versionado propio, y ningún otro endpoint de la plataforma SHALL formar parte del contrato. Un
cliente conforme SHALL poder operar usando exclusivamente ese namespace.

#### Scenario: La superficie es enumerable
- **WHEN** un desarrollador consulta el contrato para escribir un cliente nuevo
- **THEN** encuentra enumerados los endpoints del namespace versionado, y sólo esos, como todo
  lo que necesita implementar

#### Scenario: Los endpoints de plataforma no son del contrato
- **WHEN** se revisa la emisión de órdenes, el servido de imágenes, la generación de códigos de
  vinculación o el alta de diagnósticos
- **THEN** ninguno pertenece al namespace del contrato, porque los consume la plataforma y no
  el dispositivo

#### Scenario: Un cliente que sólo usa el contrato opera completo
- **WHEN** un cliente implementa únicamente los endpoints del namespace versionado
- **THEN** puede enrolarse, recibir órdenes, subir imágenes, acusar fallos y reportar su estado,
  sin necesitar ningún otro endpoint

### Requirement: Neutralidad de plataforma
El contrato NO SHALL exigir capacidades específicas de un navegador ni de un sistema operativo.
La autenticación por header SHALL ser la vía canónica en todos los endpoints del contrato,
incluido el canal de órdenes. El token por query string SHALL admitirse únicamente en el canal
de órdenes, documentado como alternativa para clientes que no puedan fijar headers en ese
canal, y NO SHALL ser la única vía en ninguna ruta.

#### Scenario: Cliente que puede fijar headers
- **WHEN** un cliente envía su token por el header de autorización en cualquier endpoint del
  contrato, incluido el canal de órdenes
- **THEN** el backend lo acepta

#### Scenario: Cliente que no puede fijar headers en su canal de eventos
- **WHEN** un cliente abre el canal de órdenes enviando el token por query string
- **THEN** el backend lo acepta, y esa alternativa está disponible sólo en ese endpoint

#### Scenario: Sin dependencias de navegador
- **WHEN** se revisa el contrato buscando obligaciones ligadas al navegador
- **THEN** no hay ninguna: ni almacenamiento web, ni service workers, ni APIs de plataforma
  específicas, ni supuestos sobre el ciclo de vida de una pestaña

### Requirement: Definición formal y versionada del contrato
El contrato SHALL existir como artefacto versionado en el repositorio, con su especificación
OpenAPI y su documento de referencia, y SHALL ser la fuente de verdad de la superficie de
dispositivo. El backend SHALL implementarse contra el contrato.

#### Scenario: El artefacto existe y es navegable
- **WHEN** un desarrollador busca cómo hablar con la plataforma desde un dispositivo nuevo
- **THEN** encuentra la especificación OpenAPI y el documento de referencia bajo el directorio
  de contratos, versionados

#### Scenario: Discrepancia entre contrato e implementación
- **WHEN** el comportamiento del backend difiere de lo que declara el contrato
- **THEN** se considera un defecto del backend, no del contrato

### Requirement: Reglas de compatibilidad y evolución
El contrato SHALL declarar qué cambios son compatibles y cuáles exigen una versión nueva.
Agregar un campo opcional, un evento nuevo o un motivo de fallo nuevo SHALL ser compatible y NO
SHALL cambiar la versión. Quitar o renombrar un campo, cambiar su tipo o su semántica, o volver
obligatorio algo opcional SHALL exigir una versión nueva del namespace, que SHALL convivir con
la anterior mientras existan clientes usándola.

#### Scenario: Cambio compatible
- **WHEN** se agrega un campo opcional a la orden o un motivo de fallo nuevo
- **THEN** los clientes existentes siguen funcionando sin modificarse y la versión no cambia

#### Scenario: Cambio incompatible
- **WHEN** se necesita cambiar el tipo de un campo o quitar uno existente
- **THEN** se publica una versión nueva del namespace y la anterior sigue atendiendo hasta que
  no queden clientes

#### Scenario: Campos desconocidos
- **WHEN** un cliente recibe un campo que no conoce
- **THEN** lo ignora sin fallar, de modo que los cambios compatibles no lo rompan

### Requirement: Motivos de fallo tipificados
El contrato SHALL definir un conjunto cerrado de motivos de fallo de captura que un cliente
puede acusar, para que el backend pueda clasificarlos sin interpretar texto libre. El acuse
SHALL admitir además un detalle libre complementario.

#### Scenario: Acuse con motivo tipificado
- **WHEN** un cliente acusa un fallo con un motivo del conjunto definido
- **THEN** el backend lo registra clasificado, y el detalle libre queda como información
  complementaria

#### Scenario: Motivo desconocido
- **WHEN** un cliente acusa un fallo con un motivo fuera del conjunto definido
- **THEN** el backend lo rechaza con `400`, evitando que el conjunto se degrade en texto libre

### Requirement: Obligaciones exigibles a cualquier cliente conforme
El contrato SHALL enunciar, de forma independiente de la tecnología del cliente, las
obligaciones que un cliente debe cumplir para considerarse conforme: sostener el canal de
órdenes con reconexión automática, encolar las órdenes que lleguen durante una captura en curso
en lugar de descartarlas, acusar como fallo toda orden que descarte, reintentar el envío de una
imagen con espera creciente, conservar en almacenamiento persistente y acotado las imágenes que
no pudo enviar, reintentarlas al recuperar conectividad, reportar el resultado de cada captura
incluidos los fallos, emitir su señal de vida con la cadencia configurada, y tratar la respuesta
de orden ya resuelta como un éxito. El contrato NO SHALL prescribir con qué mecanismos se
cumplen.

#### Scenario: Las obligaciones no mencionan tecnología
- **WHEN** se leen las obligaciones del cliente en el contrato
- **THEN** ninguna nombra una API, una librería ni un mecanismo de almacenamiento concreto

#### Scenario: Un cliente distinto cumple las mismas obligaciones
- **WHEN** se implementa un cliente sobre una plataforma diferente
- **THEN** puede satisfacer todas las obligaciones con los mecanismos propios de esa
  plataforma, sin que el contrato lo obligue a imitar al cliente de referencia

#### Scenario: Reintento sobre una orden ya resuelta
- **WHEN** un cliente reintenta la subida y recibe la respuesta de orden ya resuelta con el
  identificador de la captura existente
- **THEN** debe considerarlo un éxito, quitar la imagen de su cola y no contarlo como fallo

### Requirement: Suite de conformidad ejecutable
El contrato SHALL contar con una suite de pruebas ejecutable contra un backend levantado, que
recorra el ciclo completo de un dispositivo y verifique los códigos de respuesta y las
transiciones de estado. La suite SHALL poder usarse para validar cualquier cliente presente o
futuro.

#### Scenario: La suite recorre el ciclo completo
- **WHEN** se ejecuta la suite de conformidad contra un backend levantado
- **THEN** cubre vinculación, enrolamiento, obtención y renovación de token, apertura del canal,
  recepción de una orden, subida de imagen, acuse de fallo y señal de vida

#### Scenario: La suite detecta una regresión del backend
- **WHEN** un cambio en el backend altera un código de respuesta o una transición definida por
  el contrato
- **THEN** la suite falla

#### Scenario: Validación de un cliente nuevo
- **WHEN** se escribe un cliente para otra plataforma
- **THEN** la suite permite comprobar que el backend le responde según el contrato, sin
  modificar el backend para acomodarlo

### Requirement: Un cliente nuevo no requiere cambios en el backend
Implementar un cliente de captura para otra plataforma SHALL requerir únicamente cumplir el
contrato. NO SHALL requerir modificar el backend, agregar endpoints, ni introducir variantes de
comportamiento condicionadas al tipo de cliente.

#### Scenario: El backend no distingue clientes
- **WHEN** el backend atiende una petición del contrato
- **THEN** su comportamiento no depende de qué plataforma la originó

#### Scenario: Sustitución del cliente de referencia
- **WHEN** se reemplaza el cliente de referencia por otro que cumple el contrato
- **THEN** la plataforma sigue emitiendo órdenes, recibiendo imágenes y mostrando diagnósticos
  sin ningún cambio en el backend
