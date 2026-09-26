## ADDED Requirements

### Requirement: El enlace por cable transporta la red, no la imagen
El transporte por cable SHALL llevar las peticiones del contrato de dispositivo tal como viajarían
por cualquier otra red. El dispositivo de captura SHALL seguir siendo quien decide cuándo disparar,
quién encola, quién acusa fallos y quién reporta señal de vida. El transporte NO SHALL convertir al
teléfono en una fuente de video para que la máquina del backend extraiga fotogramas, ni en un
periférico de captura gobernado desde afuera.

#### Scenario: El dispositivo sigue siendo el dispositivo
- **WHEN** el teléfono opera conectado por cable
- **THEN** ejecuta el mismo ciclo del contrato que por red inalámbrica —órdenes, captura, entrega,
  acuse de fallo y señal de vida— con sus colas y su modo degradado intactos

#### Scenario: La máquina del backend no recibe video
- **WHEN** se audita lo que viaja por el cable
- **THEN** lo único que circula son peticiones del contrato, y en ningún momento un stream de video
  del que haya que extraer fotogramas

### Requirement: Operar por cable no requiere cambios en el sistema
Conectar el dispositivo por cable NO SHALL requerir modificar el backend, el contrato de dispositivo
ni la aplicación de captura. NO SHALL requerir endpoints nuevos, propiedades nuevas, orígenes
autorizados nuevos, ni subir la versión del contrato. El cambio de transporte SHALL ser invisible
para el backend.

#### Scenario: El backend no se entera del transporte
- **WHEN** el dispositivo completa el ciclo del contrato por el cable
- **THEN** el backend lo atiende exactamente igual que a un dispositivo conectado por red
  inalámbrica, sin ninguna rama de código que los distinga

#### Scenario: La misma aplicación sirve para los dos transportes
- **WHEN** se cambia el transporte de un dispositivo ya instalado
- **THEN** no hace falta recompilar ni reinstalar la aplicación

#### Scenario: El contrato queda intacto
- **WHEN** se revisa la superficie versionada del contrato tras habilitar el transporte por cable
- **THEN** no cambió ninguna ruta, ningún esquema ni la versión, y la suite de conformidad sigue en
  verde

### Requirement: La dirección del backend debe ser estable durante la vida del enrolamiento
El procedimiento de conexión por cable SHALL garantizar que la dirección que el dispositivo tiene
configurada siga siendo alcanzable entre reconexiones del cable y entre reinicios de la máquina que
corre el backend. El procedimiento NO SHALL depender de una dirección asignada dinámicamente sin
fijarla.

> Esta obligación existía porque la aplicación de captura no ofrecía ninguna vía para editar la
> dirección del backend después de vincularse: cambiarla exigía un enrolamiento completo nuevo. La
> pantalla de Ajustes de la app resolvió eso (ver `design.md` D4), así que hoy hay una salida sin
> re-enrolar. Aun así el procedimiento sigue prefiriendo una dirección estable por defecto, para no
> depender de que alguien entre a Ajustes cada vez que cambia la red.

#### Scenario: Reconexión del cable
- **WHEN** se desenchufa y se vuelve a enchufar el cable
- **THEN** el dispositivo vuelve a alcanzar el backend en la misma dirección, sin intervención sobre
  el teléfono

#### Scenario: Dirección dinámica sin fijar
- **WHEN** el procedimiento se sigue sin fijar la dirección de la máquina en el enlace
- **THEN** el documento advierte explícitamente que el dispositivo puede quedar apuntando a una
  dirección muerta, y nombra las alternativas que lo evitan

#### Scenario: Dirección estable por construcción
- **WHEN** se elige el camino de reenvío de puerto sobre el canal de depuración
- **THEN** la dirección configurada en el dispositivo es la misma siempre, y ninguna reasignación de
  direcciones de la red puede invalidarla

#### Scenario: La dirección dejó de responder
- **WHEN** el backend deja de estar disponible en la dirección configurada
- **THEN** el dispositivo reintenta indefinidamente con espera creciente, sin descartar su
  credencial ni quedar en un estado terminal

### Requirement: Procedimiento soportado en los dos sistemas operativos
El procedimiento SHALL estar documentado y soportado tanto en la plataforma usada para el desarrollo
diario como en la alternativa de escritorio más difundida, con paridad de detalle. NO SHALL existir
ningún paso que obligue a modificar código del sistema para que funcione en uno de los dos.

#### Scenario: Puesta en marcha en la plataforma alternativa
- **WHEN** alguien clona el repositorio en la plataforma alternativa y sigue el procedimiento
- **THEN** puede levantar el backend, generar la aplicación de captura y conectar el dispositivo por
  cable, sin editar código ni portar scripts del camino crítico

#### Scenario: Sin certificados
- **WHEN** el backend arranca en una máquina donde no se generaron certificados
- **THEN** arranca igual, deja constancia de que el canal cifrado quedó deshabilitado, y la
  aplicación nativa de captura opera normalmente contra el canal sin cifrar

#### Scenario: Herramientas de un solo sistema
- **WHEN** se evalúa una herramienta para incorporar al procedimiento
- **THEN** se descarta si no tiene equivalente en las plataformas donde el sistema podría correr más
  adelante, incluidas las de bajo consumo

### Requirement: La apertura del cortafuegos es la mínima necesaria
Cuando el sistema operativo bloquee el tráfico entrante por el enlace nuevo, el procedimiento SHALL
indicar abrir únicamente el puerto del backend. NO SHALL indicar como opción preferida reclasificar
la interfaz entera a un perfil de confianza más permisivo.

#### Scenario: Apertura por puerto
- **WHEN** el dispositivo no alcanza el backend por bloqueo del cortafuegos
- **THEN** el procedimiento indica una regla que habilita exactamente el puerto del backend, y
  explica por qué se prefiere a reclasificar la red

#### Scenario: Acotar por interfaz cuando el sistema lo permite
- **WHEN** el sistema operativo permite acotar la regla a una interfaz
- **THEN** el procedimiento la acota al enlace del cable, y no deja el puerto abierto en la red
  inalámbrica

### Requirement: Diagnóstico que distingue backend, red y cortafuegos
El procedimiento SHALL incluir una forma escrita de determinar, ante una falla, si el problema es
del backend, del enlace o del cortafuegos, sin depender de la intuición de quien lo ejecuta. SHALL
incluir además una tabla de síntomas frecuentes con su causa más probable.

#### Scenario: Separar aguas
- **WHEN** el dispositivo no alcanza el backend
- **THEN** el procedimiento indica una comprobación que, según responda o no, ubica el problema en
  el backend o fuera de él

#### Scenario: La comprobación no puede dar un falso positivo
- **WHEN** se verifica que el backend responde
- **THEN** la comprobación se hace contra la dirección del enlace y nunca contra la dirección local
  de la propia máquina, que respondería aunque el enlace estuviera roto

#### Scenario: Síntomas frecuentes
- **WHEN** aparece una falla de las conocidas —cable sin líneas de datos, interfaz sin dirección,
  bloqueo del cortafuegos, dirección cambiada, ruta por defecto secuestrada—
- **THEN** el procedimiento la nombra junto a su causa más probable

### Requirement: Ayuda automatizada para descubrir la dirección, y de sólo lectura
El procedimiento SHALL ofrecer una herramienta que identifique el enlace del cable, informe la
dirección a configurar en el dispositivo y verifique que el backend responde en ella. La herramienta
NO SHALL modificar el estado del sistema: ni interfaces, ni rutas, ni reglas de cortafuegos. El
procedimiento SHALL poder completarse sin ella.

#### Scenario: Identificación del enlace sin adivinar
- **WHEN** la máquina tiene varias interfaces activas además del cable
- **THEN** la herramienta identifica la del cable por su origen físico y no por el nombre que le
  haya tocado

#### Scenario: Verificación honesta
- **WHEN** la herramienta informa que el backend responde
- **THEN** lo comprobó contra la dirección del enlace, que es la que va a usar el dispositivo

#### Scenario: No cambia nada
- **WHEN** se ejecuta la herramienta
- **THEN** el estado de red de la máquina queda exactamente como estaba

#### Scenario: El procedimiento no depende de ella
- **WHEN** la herramienta no está disponible o no funciona en esa máquina
- **THEN** el documento alcanza para completar la conexión a mano

### Requirement: El transporte por cable se suma; volver a red inalámbrica sigue siendo posible
Habilitar el transporte por cable NO SHALL eliminar la posibilidad de operar por red inalámbrica. El
procedimiento SHALL declarar qué cuesta cambiar de transporte en un dispositivo ya vinculado.

#### Scenario: Convivencia de transportes
- **WHEN** el transporte por cable está documentado y en uso
- **THEN** un dispositivo puede seguir vinculándose por red inalámbrica, sin cambios en el sistema

#### Scenario: Costo declarado del cambio de transporte
- **WHEN** alguien quiere cambiar el transporte de un dispositivo ya vinculado
- **THEN** el procedimiento le dice de antemano que la dirección se edita desde Ajustes sin volver a
  vincular, y reserva borrar los datos de la app como último recurso si Ajustes no está disponible

### Requirement: Los límites del transporte por cable quedan declarados
El procedimiento SHALL declarar explícitamente hasta dónde sirve el transporte por cable, para que
sus límites no se descubran durante una demostración.

#### Scenario: Escala
- **WHEN** se lee el procedimiento
- **THEN** dice que el cable sirve para la maqueta, el prototipo y la demostración, y que la
  instalación completa del vivero vuelve a red

#### Scenario: Cantidad de dispositivos
- **WHEN** se evalúa conectar más de un dispositivo de captura por cable
- **THEN** el procedimiento advierte que el enlace es punto a punto y nombra las salidas

#### Scenario: Distancia
- **WHEN** el tramo entre la máquina y el dispositivo supera lo que admite un cable pasivo
- **THEN** el procedimiento indica el límite aproximado y que alimentación y datos son problemas
  separables
