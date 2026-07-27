# Spec: sensado-persistencia

## ADDED Requirements

### Requirement: La lectura se persiste a nivel macro-zona

El sistema SHALL almacenar una única lectura por macro-zona, con las diez métricas
monitoreadas, el instante de la lectura y el estado del nodo testigo que la produjo
(identificador, batería y señal). Los sectores NO SHALL almacenar valores de métricas
propios.

#### Scenario: Ingesta de telemetría

- **WHEN** llega un paquete de telemetría de la macro-zona MZ-3
- **THEN** el sistema persiste la lectura una sola vez, asociada a MZ-3
- **AND** no se escribe ningún valor de métrica en los sectores de esa zona

#### Scenario: Zona inexistente

- **WHEN** llega telemetría con un identificador de macro-zona que no existe
- **THEN** el paquete se descarta sin alterar el estado del sistema

### Requirement: Lectura parcial preserva los valores anteriores

La ingesta SHALL actualizar únicamente las métricas presentes en el payload. Las métricas
ausentes SHALL conservar su último valor conocido, permitiendo que un nodo con un sensor
en falla —o un envío manual desde el dashboard de simulación— reporte sólo lo que tiene.

#### Scenario: Payload con una sola métrica

- **WHEN** llega un paquete que sólo incluye luminosidad
- **THEN** se actualiza la luminosidad de la zona
- **AND** las otras nueve métricas conservan sus valores previos

#### Scenario: Sonda de suelo en falla

- **WHEN** el nodo publica sin las métricas de suelo porque la sonda no respondió
- **THEN** se actualizan las métricas ambientales disponibles y la zona registra que las
  de suelo no se refrescaron

### Requirement: Normalización de unidades en la ingesta

La ingesta SHALL convertir la conductividad eléctrica de µS/cm —unidad en que la publica la
sonda— a dS/m antes de persistirla, de modo que la base almacene siempre la unidad
canónica que consumen los umbrales, el motor de reglas y la interfaz.

#### Scenario: Conversión de conductividad

- **WHEN** el nodo publica una conductividad de 1400 µS/cm
- **THEN** el sistema persiste 1,4 dS/m
- **AND** el motor de reglas la compara contra umbrales expresados en dS/m

### Requirement: Tolerancia a métricas no modeladas

La ingesta SHALL aceptar payloads que contengan claves de métricas que el sistema no
modela —en particular `salinidad` y `tds`, que la sonda deriva por factor de la misma
medición de conductividad— descartándolas sin fallar. El firmware SHALL poder publicar el
set extendido completo sin romper la ingesta.

#### Scenario: Payload con métricas extendidas

- **WHEN** el nodo publica con las métricas extendidas activadas, incluyendo `salinidad` y `tds`
- **THEN** el sistema persiste las diez métricas modeladas y descarta el resto sin error

#### Scenario: Clave desconocida futura

- **WHEN** llega un payload con una clave de métrica que el sistema desconoce
- **THEN** la ingesta la ignora y procesa normalmente el resto del paquete

### Requirement: El estado de los sectores se deriva de la lectura de su macro-zona

Al recibir una lectura, el sistema SHALL recalcular el estado de salud de todos los
sectores de esa macro-zona a partir de esa única lectura, conservando el comportamiento
actual del mapa de producción y del motor de reglas. Sólo las métricas no informativas
SHALL participar del cálculo.

#### Scenario: Recálculo tras la ingesta

- **WHEN** se persiste una lectura con humedad de sustrato en valor crítico
- **THEN** todos los sectores de esa macro-zona pasan a estado crítico
- **AND** el mapa de producción refleja el cambio de color

#### Scenario: Métrica informativa fuera de banda

- **WHEN** se persiste una lectura con pH fuera de banda y el resto de las métricas
  originales dentro de rango
- **THEN** el estado de los sectores permanece saludable

#### Scenario: Diagnóstico de IA independiente

- **WHEN** se recalcula el estado de los sectores de una zona
- **THEN** el diagnóstico de IA de cada sector se mantiene como dato propio del sector

### Requirement: Umbrales configurables para las diez métricas

Los umbrales de las diez métricas SHALL estar disponibles en la configuración agronómica,
editables por el usuario con el mismo mecanismo que las métricas existentes. Los umbrales
de las cinco métricas incorporadas en este cambio SHALL quedar registrados como
provisionales.

#### Scenario: Edición de un umbral nuevo

- **WHEN** el usuario modifica la banda ideal del pH del sustrato
- **THEN** la nueva banda se persiste y se aplica en la evaluación de las próximas lecturas

#### Scenario: Umbrales sembrados al iniciar

- **WHEN** el sistema arranca sobre una base sin umbrales cargados
- **THEN** siembra las diez métricas con sus bandas por defecto

### Requirement: El envío manual de telemetría cubre las diez métricas

El dashboard de simulación SHALL permitir enviar cualquier subconjunto de las diez
métricas para una macro-zona, respetando la semántica de lectura parcial.

#### Scenario: Envío de una métrica nueva

- **WHEN** el usuario envía manualmente un valor de nitrógeno para MZ-2
- **THEN** la lectura de MZ-2 refleja ese nitrógeno y conserva el resto de sus valores

### Requirement: Migración documentada del modelo anterior

El cambio SHALL entregar documentados los pasos manuales de base de datos necesarios para
pasar del modelo por sector al modelo por macro-zona: traslado de los valores existentes,
eliminación de las columnas obsoletas de sector y re-siembra de umbrales. La generación
automática del esquema NO SHALL asumirse suficiente.

#### Scenario: Migración de un entorno con datos

- **WHEN** un desarrollador aplica el cambio sobre una base con datos previos
- **THEN** encuentra en el repositorio el script y el orden de ejecución para conservar las
  lecturas y dejar el esquema limpio
