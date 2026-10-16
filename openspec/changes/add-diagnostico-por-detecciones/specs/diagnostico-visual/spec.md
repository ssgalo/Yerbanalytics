## ADDED Requirements

### Requirement: Inferencia con umbral global bajo
El servicio de inferencia SHALL ejecutar el modelo de detección sobre la imagen cenital con un
umbral de confianza global bajo (`YOLO_CONF_GLOBAL`), para no perder detecciones, y SHALL traducir
cada detección a una clase de la plataforma (según `MODEL_CLASSES`), su score y su caja en
coordenadas normalizadas a la imagen (`x1, y1, x2, y2` en `[0, 1]`).

#### Scenario: Detecciones de una imagen
- **WHEN** el servicio procesa una captura pendiente
- **THEN** obtiene todas las detecciones con score ≥ `YOLO_CONF_GLOBAL`, cada una con su clase de
  la plataforma, su score y su caja normalizada

#### Scenario: Cantidad de clases distinta a la del checkpoint
- **WHEN** el servicio arranca y `MODEL_CLASSES` no tiene la misma cantidad de clases que el modelo
- **THEN** el servicio no arranca y lo informa en el log

### Requirement: Parámetros del equipo por variables de entorno
El servicio de inferencia SHALL tomar de sus variables de entorno el umbral global, el IoU de
solapamiento y las bandas de confianza de cada una de las cinco clases del modelo (Sano, Clorosis,
Estrés solar, Plaga foliar, Daño fúngico); esos valores NO SHALL ser editables desde el dashboard.
Cada clase SHALL tener tres cortes sobre el score crudo: válida desde, media desde y alta desde.

#### Scenario: Variable ausente
- **WHEN** el servicio arranca sin una de esas variables
- **THEN** usa su valor por defecto y deja en el log un aviso que nombra la variable

#### Scenario: Bandas mal formadas
- **WHEN** las bandas de una clase no son tres números en `(0, 1]` estrictamente crecientes
- **THEN** el servicio no arranca y el log nombra la variable inválida

#### Scenario: Umbral global por encima de un umbral de clase
- **WHEN** `YOLO_CONF_GLOBAL` es mayor que el umbral de validez de alguna clase
- **THEN** el servicio no arranca, porque descartaría detecciones que esa clase considera válidas

### Requirement: Filtro por umbral de clase
Una detección SHALL ser válida sólo si su score alcanza el umbral de validez de su clase. Sólo las
detecciones válidas SHALL intervenir en el resto del diagnóstico, incluida la resolución de
superposiciones.

#### Scenario: Mismo score, distinta clase
- **WHEN** una detección de Clorosis y una de Sano tienen score 0,50, con umbral 0,40 para Clorosis
  y 0,60 para Sano
- **THEN** la de Clorosis es válida y la de Sano se descarta

### Requirement: Resolución de cajas superpuestas
El servicio SHALL conservar una sola detección entre las detecciones válidas cuyas cajas se
superponen con un IoU mayor o igual a `SOLAPAMIENTO_IOU`. La conservada SHALL ser la de mayor nivel de confianza de su
clase; ante igual nivel, la de mayor criticidad según el orden del agrónomo, con Sano como la menos
crítica; ante igual criticidad, la de mayor score.

#### Scenario: Gana el nivel de confianza
- **WHEN** dos cajas válidas se superponen y una tiene confianza alta en su clase y la otra media
- **THEN** se conserva la de confianza alta

#### Scenario: Empate de nivel, gana la criticidad
- **WHEN** una caja Sano con confianza alta se superpone con una caja Clorosis con confianza alta
- **THEN** se conserva la de Clorosis

#### Scenario: Una detección inválida no elimina a una válida
- **WHEN** una caja Sano bajo su umbral se superpone con una caja Clorosis válida
- **THEN** la de Clorosis se conserva, porque la de Sano ya fue descartada por el filtro

#### Scenario: Cajas que no se superponen lo suficiente
- **WHEN** dos cajas válidas tienen un IoU menor que `SOLAPAMIENTO_IOU`
- **THEN** se conservan las dos

### Requirement: Agregación, severidad y confianza por estado
Por cada estado, el servicio SHALL contar sus detecciones válidas y promediar sus scores. Cada
estado anómalo SHALL recibir un nivel de severidad según su cantidad y las bandas de severidad de su
clase: por debajo del mínimo queda fuera del diagnóstico; desde el mínimo, Baja; desde "media",
Media; desde "alta", Alta. Sano NO SHALL tener severidad. Cada estado que queda SHALL recibir un
nivel de confianza ubicando su score promedio en las bandas de confianza de su clase: Baja por
debajo de "media", Media desde "media", Alta desde "alta".

#### Scenario: Estado bajo el mínimo
- **WHEN** Plaga foliar tiene una detección válida y su mínimo es 2
- **THEN** Plaga foliar queda fuera del diagnóstico

#### Scenario: Severidad y confianza de un estado
- **WHEN** Clorosis tiene 6 detecciones válidas con score promedio 0,62, bandas de severidad 5/10/20
  y bandas de confianza 0,40/0,55/0,70
- **THEN** Clorosis queda con severidad Baja y confianza Media

### Requirement: Elección de un único diagnóstico
El servicio SHALL emitir un único diagnóstico por captura. Entre los estados anómalos que alcanzan su
mínimo SHALL ganar el de mayor relevancia, definida como nivel de severidad más nivel de confianza
(Baja = 1, Media = 2, Alta = 3). Ante igual relevancia SHALL ganar el de mayor criticidad según el
orden del agrónomo. Si ningún estado anómalo alcanza su mínimo, el diagnóstico SHALL ser Sano con
su nivel de confianza si hay detecciones Sano válidas, y No concluyente en caso contrario.

#### Scenario: Ejemplo del documento
- **WHEN** las detecciones válidas son 120 Sano (promedio 0,78), 6 Clorosis (0,62), 3 Daño fúngico
  (0,45) y 1 Plaga foliar, con los valores por defecto
- **THEN** el diagnóstico es Clorosis, severidad Baja, confianza Media

#### Scenario: Empate de relevancia
- **WHEN** Clorosis y Daño fúngico tienen la misma relevancia y Daño fúngico es más crítico
- **THEN** el diagnóstico es Daño fúngico

#### Scenario: Sólo hojas sanas
- **WHEN** ningún estado anómalo alcanza su mínimo y hay detecciones Sano válidas
- **THEN** el diagnóstico es Sano, sin severidad y con el nivel de confianza de Sano

#### Scenario: Sin detecciones válidas
- **WHEN** ningún estado anómalo alcanza su mínimo y no hay detecciones Sano válidas
- **THEN** el diagnóstico es No concluyente, sin severidad, sin confianza y sin score

### Requirement: Criterio del agrónomo leído en cada ciclo
Cuando haya capturas pendientes, el servicio SHALL leer del backend el criterio de diagnóstico
vigente (bandas de severidad y orden de criticidad) antes de procesarlas, una vez por ciclo. Si no
puede leerlo, o si el modelo emite un estado anómalo sin criterio definido, SHALL omitir el ciclo
sin registrar diagnósticos, y las capturas SHALL seguir pendientes.

#### Scenario: Cambio de criterio entre ciclos
- **WHEN** el agrónomo cambia las bandas de severidad y luego llega una captura nueva
- **THEN** el diagnóstico de esa captura se elige con las bandas nuevas

#### Scenario: Backend sin responder el criterio
- **WHEN** hay capturas pendientes y el pedido del criterio falla
- **THEN** el ciclo no registra diagnósticos y las capturas quedan pendientes para el próximo

### Requirement: Entrega del diagnóstico con sus detecciones
El servicio SHALL registrar cada diagnóstico por el camino único de alta, con el estado, el nivel de
severidad (sólo en estados anómalos), el nivel de confianza (salvo en No concluyente), el score
promedio del estado diagnosticado expresado como porcentaje (salvo en No concluyente) y todas las
detecciones válidas que sobrevivieron a la resolución de superposiciones, de todas las clases.

#### Scenario: Alta de un diagnóstico anómalo
- **WHEN** el diagnóstico elegido es Clorosis, severidad Baja, confianza Media, promedio 0,62
- **THEN** el alta lleva estado Clorosis, severidad Baja, confianza Media, score 62 y las detecciones
  válidas de todas las clases

#### Scenario: Alta de un No concluyente
- **WHEN** el diagnóstico elegido es No concluyente
- **THEN** el alta no lleva severidad, confianza ni score

### Requirement: Modelo vigente
El servicio SHALL usar `modelo_final.pt` como modelo en las configuraciones de ejemplo y de
despliegue. Una ruta relativa en `MODEL_PATH` SHALL resolverse contra la carpeta del servicio, sin
depender del directorio desde el que se lo lance.

#### Scenario: Ruta relativa
- **WHEN** `MODEL_PATH=modelo_final.pt` y el servicio se lanza desde su carpeta o desde `src/`
- **THEN** carga el mismo archivo `modelo_final.pt` de la carpeta del servicio
