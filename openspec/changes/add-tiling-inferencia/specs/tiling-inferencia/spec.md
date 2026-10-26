## ADDED Requirements

### Requirement: Normalización de la imagen
Antes de cualquier otro paso, el servicio de inferencia SHALL orientar la imagen según su tag EXIF de
orientación y SHALL convertirla a modo RGB. Todas las coordenadas del pipeline (recorte, tiles y
cajas) SHALL referirse a la imagen ya orientada.

#### Scenario: Imagen con orientación EXIF
- **WHEN** la captura trae un tag EXIF que indica una rotación de 90°
- **THEN** el servicio la procesa rotada, tal como la muestra el navegador

#### Scenario: Imagen que no es RGB
- **WHEN** la captura está en escala de grises, CMYK o tiene canal alfa
- **THEN** el servicio la convierte a RGB antes de recortarla

#### Scenario: Imagen sin tag EXIF
- **WHEN** la captura no trae orientación EXIF
- **THEN** el servicio la usa tal cual

### Requirement: Recorte configurable
El servicio SHALL recortar la imagen normalizada quitando los márgenes en píxeles de
`RECORTE_IZQUIERDA`, `RECORTE_ARRIBA`, `RECORTE_DERECHA` y `RECORTE_ABAJO` (enteros ≥ 0). Una variable
ausente SHALL tomar su valor de fábrica (278, 366, 0 y 0) con un aviso en el log que la nombra. Un
valor que no es un entero ≥ 0 SHALL impedir que el servicio arranque.

#### Scenario: Recorte de fábrica
- **WHEN** una captura de 1080×1440 se procesa sin variables de recorte definidas
- **THEN** el servicio trabaja sobre la región de 802×1074 que empieza en (278, 366)

#### Scenario: Recorte que no entra en la imagen
- **WHEN** la suma de los márgenes horizontales o verticales es mayor o igual que el ancho o el alto
  de la captura
- **THEN** la captura no se diagnostica, sigue pendiente, y el log nombra las variables de recorte y
  el tamaño de la imagen

#### Scenario: Valor de recorte inválido
- **WHEN** `RECORTE_ARRIBA` vale `-10` o `abc`
- **THEN** el servicio no arranca y el log nombra la variable

### Requirement: División en tiles con solapamiento
El servicio SHALL dividir la imagen recortada en una grilla de `TILES_FILAS` × `TILES_COLUMNAS` tiles
del mismo tamaño, con `TILES_SOLAPAMIENTO_PX` píxeles compartidos entre tiles vecinos en cada eje. Los
tiles SHALL cubrir la imagen recortada entera, y los de la última fila y la última columna SHALL
apoyarse en el borde. Con una sola fila o columna, ese eje no SHALL tener solapamiento. El servicio
SHALL correr el detector una vez por tile, con la imagen en memoria.

#### Scenario: Grilla de fábrica
- **WHEN** la imagen recortada mide 802×1074 con 3 filas, 2 columnas y 130 px de solapamiento
- **THEN** el servicio genera 6 tiles de 466×445 y corre el detector 6 veces

#### Scenario: Solapamiento que no deja avance entre tiles
- **WHEN** la configuración da tiles cuyo avance entre vecinos es menor o igual que cero
- **THEN** la captura no se diagnostica, sigue pendiente, y el log nombra las variables de la grilla

#### Scenario: Grilla de un solo tile
- **WHEN** `TILES_FILAS` y `TILES_COLUMNAS` valen 1
- **THEN** el detector corre sobre la imagen recortada entera y no hay unión entre tiles

### Requirement: Cajas en coordenadas de la imagen recortada
Cada caja detectada en un tile SHALL trasladarse a coordenadas de la imagen recortada (sumando la
posición del tile) y SHALL normalizarse sobre el tamaño de la imagen recortada.

#### Scenario: Caja en el tile de abajo a la derecha
- **WHEN** el detector encuentra una caja en el tile de la última fila y la última columna
- **THEN** la caja se entrega en coordenadas normalizadas de la imagen recortada, no del tile

### Requirement: Unión de cajas entre tiles vecinos
El servicio SHALL fusionar en una sola caja dos detecciones de tiles vecinos cuando al menos una toca
(dentro de `TILES_TOLERANCIA_BORDE_PX`) el borde interno que comparten esos dos tiles y su
intersección dividida por el área de la caja más chica es mayor o igual que `TILES_FUSION_IOS`. La
caja fusionada SHALL ser la unión de las dos, con la clase y el score de la caja de mayor área. Las
fusiones SHALL repetirse hasta que no quede ningún par que fusionar. Dos cajas que no tocan el borde
compartido NO SHALL fusionarse en este paso. La unión SHALL hacerse antes del diagnóstico y sin mirar
la clase de las cajas.

#### Scenario: Hoja cortada y hoja entera
- **WHEN** una hoja aparece cortada por el borde derecho del tile r0c0 y entera en el tile r0c1
- **THEN** queda una sola caja, igual a la de la hoja entera, con su clase y su score

#### Scenario: Dos pedazos de una hoja
- **WHEN** una hoja más grande que el solapamiento aparece cortada en los dos tiles vecinos y los dos
  pedazos se pisan en la franja compartida
- **THEN** queda una sola caja que cubre los dos pedazos, con la clase del pedazo más grande

#### Scenario: Hojas distintas que se pisan
- **WHEN** dos hojas distintas se superponen en el follaje y ninguna de sus cajas toca el borde
  compartido
- **THEN** la unión entre tiles no las fusiona

#### Scenario: Hoja en la esquina de cuatro tiles
- **WHEN** una hoja cortada aparece en cuatro tiles que comparten una esquina
- **THEN** las fusiones sucesivas dejan una sola caja

#### Scenario: Hoja entera repetida en la franja
- **WHEN** una hoja cae entera dentro de la franja compartida y aparece entera en los dos tiles
- **THEN** la unión entre tiles no la fusiona, y el paso de cajas superpuestas del diagnóstico deja
  una sola

### Requirement: Parámetros de tiling por variables de entorno
Los parámetros de recorte, grilla y unión SHALL ser parámetros del equipo, tomados de variables de
entorno del servicio y no editables desde el dashboard. `env.example` SHALL documentar cada una con
una explicación breve. Al arrancar, el servicio SHALL dejar en el log los valores efectivos. Un valor
mal formado SHALL impedir que el servicio arranque.

#### Scenario: Variable ausente
- **WHEN** el servicio arranca sin `TILES_SOLAPAMIENTO_PX`
- **THEN** usa 130 y deja un aviso en el log que nombra la variable

#### Scenario: Umbral de fusión fuera de rango
- **WHEN** `TILES_FUSION_IOS` vale `1.5` o `0`
- **THEN** el servicio no arranca y el log nombra la variable

#### Scenario: Grilla inválida
- **WHEN** `TILES_FILAS` vale `0`
- **THEN** el servicio no arranca y el log nombra la variable

### Requirement: El diagnóstico se entrega con su recorte
El servicio SHALL registrar cada diagnóstico con el recorte aplicado, normalizado sobre la imagen
orientada, y con sus cajas normalizadas sobre la imagen recortada.

#### Scenario: Alta con el recorte de fábrica
- **WHEN** el servicio diagnostica una captura de 1080×1440 con el recorte de fábrica
- **THEN** el alta lleva `crop` = `[0.2574, 0.2542, 1.0, 1.0]` y las cajas relativas a la región de
  802×1074
