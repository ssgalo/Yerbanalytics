## ADDED Requirements

### Requirement: El simulador es un proyecto independiente y borrable
El simulador SHALL vivir en un único directorio propio (`Desarrollo/simulador/`) con su
propio manifiesto de dependencias, su propia configuración y su propio almacenamiento.
Eliminar ese directorio NO SHALL requerir ningún cambio en el backend, en el dashboard, en la
app de cámara ni en el firmware, y el sistema SHALL seguir compilando y funcionando igual.

#### Scenario: Borrar el simulador no rompe el sistema
- **WHEN** se elimina el directorio del simulador y se levanta el sistema
- **THEN** el backend arranca, el dashboard carga en sus dos modos y la app de cámara opera,
  sin errores de compilación ni referencias rotas

#### Scenario: Ningún proyecto importa del simulador
- **WHEN** se auditan los archivos fuente del backend, del dashboard y de la app de cámara
- **THEN** ninguno importa, referencia ni configura nada que viva dentro del directorio del
  simulador

#### Scenario: El estado del simulador vive en su directorio
- **WHEN** el simulador persiste sus sensores simulados y sus preferencias
- **THEN** los guarda dentro de su propio directorio, y no en la base de datos del vivero ni
  en ningún otro proyecto

### Requirement: Arranque propio, y nunca una dependencia
El simulador SHALL levantarse con su propio comando, independiente del arranque del backend y
del dashboard, y NO SHALL ser una dependencia de arranque de ningún otro proyecto: ni el
backend ni el dashboard SHALL requerirlo para funcionar.

Un script de conveniencia PUEDE levantar el simulador junto con el resto para el trabajo
diario, pero SHALL tratarlo como opcional: si el directorio del simulador no existe, el script
SHALL omitir ese paso e iniciar el sistema igual, sin error.

#### Scenario: El sistema arranca sin el simulador
- **WHEN** se levantan el backend y el dashboard sin haber levantado el simulador
- **THEN** ambos arrancan con normalidad y no reportan ninguna dependencia faltante

#### Scenario: El simulador se levanta por separado
- **WHEN** el usuario ejecuta el comando de arranque del simulador
- **THEN** el simulador queda disponible en su propio puerto, sin haber modificado ni
  reiniciado el backend ni el dashboard

#### Scenario: El script de conveniencia sin el simulador instalado
- **WHEN** se ejecuta el script que levanta todo y el directorio del simulador no existe
- **THEN** el script informa que lo omite y levanta el resto del sistema con normalidad, sin
  error ni interrupción

#### Scenario: El simulador tiene sus propias dependencias
- **WHEN** el script de conveniencia prepara el entorno
- **THEN** instala las dependencias del simulador aparte de las del dashboard, porque no
  comparten `node_modules`

#### Scenario: El simulador arranca sin el sistema
- **WHEN** se levanta el simulador con el backend apagado
- **THEN** el simulador arranca igual y muestra que el backend no está disponible, en lugar
  de fallar al iniciar

### Requirement: El backend no conoce al simulador
El backend NO SHALL contener ningún endpoint, entidad, tabla, propiedad de configuración,
origen permitido ni rama de código que exista para servir al simulador. Todo lo que el
simulador consuma del backend SHALL ser superficie pública que la plataforma expone para sus
propios emisores.

#### Scenario: Sin superficie exclusiva
- **WHEN** se audita la API del backend
- **THEN** no existe ningún endpoint cuyo único consumidor sea el simulador

#### Scenario: Sin estado del simulador en la base del vivero
- **WHEN** se inspecciona el esquema de la base de datos del sistema
- **THEN** no hay tablas de modo de operación ni de sensores simulados

#### Scenario: Sin configuración dedicada al simulador
- **WHEN** se revisa la configuración del backend
- **THEN** no hay propiedades de simulador ni orígenes permitidos que existan para el
  simulador

#### Scenario: El backend no publica telemetría
- **WHEN** se auditan los componentes MQTT del backend
- **THEN** el backend únicamente consume telemetría del broker, y no contiene ningún
  publicador

### Requirement: El simulador alcanza el backend sin exigirle configuración
La interfaz del simulador SHALL comunicarse con el backend a través del propio servidor del
simulador, de modo que el backend reciba peticiones de servidor a servidor y NO SHALL
necesitar declarar el origen del simulador entre los permitidos.

#### Scenario: El backend no declara el origen del simulador
- **WHEN** se revisan los orígenes permitidos del backend
- **THEN** no figura el origen del simulador

#### Scenario: La dirección del backend es configuración del simulador
- **WHEN** el backend se sirve en otra dirección
- **THEN** se ajusta la configuración del simulador, sin tocar la del backend

### Requirement: Interfaz propia del simulador
El simulador SHALL presentar su propia interfaz, autocontenida, sin depender de los estilos
ni de los componentes del dashboard. La interfaz SHALL agrupar en un solo lugar los controles
de sensores simulados, de emisión automática, de topología y de cámara.

#### Scenario: La interfaz no depende del dashboard
- **WHEN** se auditan los estilos y componentes del simulador
- **THEN** ninguno se importa del proyecto del dashboard

#### Scenario: Todos los controles en un solo lugar
- **WHEN** el usuario abre el simulador
- **THEN** encuentra los controles de sensores, de emisión automática, de topología y de
  cámara en la misma aplicación

### Requirement: El simulador no aparece en el dashboard
El dashboard de monitoreo NO SHALL exponer ninguna vista, ruta, ítem de navegación ni control
de simulación. El código del dashboard NO SHALL contener menciones al simulador.

#### Scenario: Sin rastro en la navegación
- **WHEN** el usuario navega el dashboard
- **THEN** no hay ítem de simulación en el menú ni ruta accesible que lleve a uno

#### Scenario: Sin rastro en el código del dashboard
- **WHEN** se auditan los archivos fuente del dashboard
- **THEN** no hay hooks, vistas, tipos ni métodos de acceso a datos dedicados a la simulación
