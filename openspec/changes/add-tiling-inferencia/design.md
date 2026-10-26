## Context

El servicio de inferencia (`Desarrollo/servicio-inferencia/`) pide al backend las capturas
pendientes, lee cada JPEG del directorio compartido, corre YOLOv8 sobre la **imagen entera**
(`model.detect(path)`) y pasa las cajas a `diagnosis.diagnose()`, que filtra por clase, resuelve
cajas superpuestas, cuenta las hojas por estado y elige el diagnóstico (D1–D3 de
`add-diagnostico-por-detecciones`). El alta lleva cajas `xyxyn` normalizadas sobre el JPEG, y el visor
del dashboard (`CapturaModal` + `DetectionOverlay`) las dibuja con un SVG `viewBox="0 0 1 1"` encima
de la imagen.

Datos medidos sobre las capturas reales:

- Las capturas salen del teléfono en 1080×1440 (retrato), RGB y **sin** orientación EXIF.
- Quitando 278 px a la izquierda y 366 px arriba (riel y piso) queda una región de **802×1074** con
  ~60–80 hojas.
- Las hojas más grandes miden ~210–250 px en su eje más largo (estimado a ojo sobre `sector1.jpg` y
  `sector2.jpg`).
- Las imágenes de entrenamiento tenían entre 5 y 20 hojas.

Este cambio se apoya en `add-diagnostico-por-detecciones` (todavía sin archivar) y no cambia su
pipeline: agrega pasos antes de `diagnose()`.

## Goals / Non-Goals

**Goals:**

- Que cada imagen que ve el modelo tenga una cantidad de hojas parecida a la del entrenamiento.
- Que ninguna hoja se cuente dos veces por aparecer en dos tiles: la severidad sale de la cantidad.
- Que el recorte, la grilla y la unión sean configurables sin recompilar.
- Que el dashboard muestre la región analizada, no la foto con el riel.

**Non-Goals:**

- Garantizar que toda hoja aparezca entera en algún tile. Con hojas de ~250 px y tiles de ~450 px eso
  exige un solapamiento cercano a la mitad del tile, y deja más hojas por tile que las del
  entrenamiento (ver D3).
- Recortar o guardar imágenes derivadas en el backend: el JPEG de la captura queda intacto.
- Miniaturas recortadas en las tarjetas de diagnóstico (ver Open Questions).
- Cambiar el contrato de cámara, el MQTT, el simulador o el criterio del agrónomo.
- Recalibrar el modelo, las bandas de confianza o las de severidad.

## Decisions

### D1. Todo el procesamiento de imagen vive en el servicio de inferencia

Orientar, convertir, recortar, dividir en tiles y unir las cajas lo hace el servicio, antes de
`diagnose()`. Así sigue vigente la D1 de `add-diagnostico-por-detecciones`: la calibración del modelo
(umbrales, bandas de confianza y ahora el tiling) queda en el entorno del servicio, y el backend no
procesa imágenes.

- *Alternativa descartada: el backend recorta al recibir la imagen.* Pierde la foto original, obliga
  a leer EXIF en Java (`ImageIO` no lo aplica) y deja el recorte, que es calibración del montaje de la
  cámara, como configuración de la plataforma.
- *Alternativa descartada: el backend recorta, arma los tiles y une las cajas.* Obliga a pasar
  `diagnosis.py` a Java y a llevar los parámetros del equipo al backend, revirtiendo esa D1.
- *Alternativa descartada: el servicio sube una imagen recortada.* Hace falta un endpoint nuevo y un
  segundo archivo por captura, cuando alcanza con saber qué región se miró.

### D2. Pipeline

```
 JPEG de la captura (como está en disco, intacto)
   │ ImageOps.exif_transpose → convert("RGB")                     (normalización)
   ▼
 imagen orientada  W×H  (la misma que muestra el navegador)
   │ quitar RECORTE_IZQUIERDA/ARRIBA/DERECHA/ABAJO                (recorte)
   ▼
 imagen recortada  w×h  (802×1074 con la cámara actual)
   │ grilla TILES_FILAS × TILES_COLUMNAS con TILES_SOLAPAMIENTO_PX (tiles)
   ▼
 detect(tile) × N      → cajas en píxeles del tile
   │ + posición del tile → píxeles de la imagen recortada
   │ fusión entre tiles vecinos (D4)
   │ normalizar sobre w×h, redondear a 4 decimales
   ▼
 diagnose()  — sin cambios
   ▼
 POST /api/diagnosticos  { …, detections (sobre la región), crop (sobre la imagen orientada) }
```

`tiling.py` es lógica pura (geometría de la grilla, traslado y fusión de cajas, en píxeles), sin
`ultralytics` ni I/O, igual que `diagnosis.py`: se prueba sin checkpoint. Lo que toca Pillow
(normalización y recorte) va en una función chica aparte, y `model.detect` pasa a recibir una imagen
PIL en vez de una ruta. Ultralytics acepta PIL y la trata como RGB.

La unión trabaja en **píxeles** y normaliza al final. Normalizar por tile y volver a escalar suma
error de redondeo justo en los bordes, que es donde decide la fusión.

### D3. Grilla de 3×2 con 130 px de solapamiento

Los tiles son todos del mismo tamaño. Por eje, con largo `L`, `n` tiles y solapamiento `O`:

```
 tamaño     t = ceil((L + (n − 1)·O) / n)
 posición   p_i = round(i · (L − t) / (n − 1))     (p_0 = 0, el último apoyado en el borde)
```

Con 802×1074, 3 filas, 2 columnas y O = 130 salen 6 tiles de 466×445:

```
        0             336   466          802
   0    ┌──────────────┬─────┬────────────┐
        │     r0c0     │░░░░░│    r0c1    │
  315   ├──────────────┼─────┼────────────┤
  445   │░░░░░░░░░░░░░░│▓▓▓▓▓│░░░░░░░░░░░░│
        │     r1c0     │░░░░░│    r1c1    │
  629   ├──────────────┼─────┼────────────┤
  760   │░░░░░░░░░░░░░░│▓▓▓▓▓│░░░░░░░░░░░░│
        │     r2c0     │░░░░░│    r2c1    │
 1074   └──────────────┴─────┴────────────┘        ░ franja compartida (~130 px)
```

| Grilla y solapamiento | Tile | Hojas por tile (aprox.) | Inferencias |
|---|---|---|---|
| 3×2, 258 px | 530×530 | ~23 (pasa del máximo de entrenamiento) | 6 |
| **3×2, 130 px** | **466×445** | **~17** | **6** |
| 3×2, 60 px | 431×398 | ~14 | 6 |
| 3×3, 50 px | 297×395 | ~10 | 9 |

- *Por qué 3×2*: la región es retrato (≈ 3:4) y 3×2 da tiles casi cuadrados, que aprovechan el lienzo
  cuadrado de YOLO. Deja ~17 hojas por tile, dentro del rango de entrenamiento (5–20).
- *Por qué 130 px*: alcanza para que las hojas de tamaño típico queden enteras en algún tile; para las
  más grandes, deja una franja ancha donde los dos pedazos se pisan y la fusión los reconoce (D4).
- *Por qué no más solapamiento*: el solapamiento no cambia la cantidad de inferencias (la fija la
  grilla), pero agranda los tiles, y con ~258 px pasan del máximo de hojas del entrenamiento.
- *Por qué no 3×3*: más bordes internos ⇒ más hojas cortadas, y 9 inferencias en vez de 6. Para
  bajar de ~17 hojas por tile no hace falta con este rango de entrenamiento.
- Son valores iniciales: los fija el spike de la tarea 1.

### D4. Unión: fusión por IoS sólo en el borde compartido

Con tiles de un tamaño parecido al de las hojas grandes, una hoja puede quedar cortada en todos los
tiles donde aparece. El paso de cajas superpuestas de `diagnose()` compara por IoU, y una caja cortada
contra la entera de la misma hoja tiene IoU bajo (≈ 0,3–0,5): no las reconocería como la misma.

Regla, para cada par de cajas de **tiles vecinos** (comparten un borde o una esquina):

1. **Al menos una toca el borde interno compartido**: su lado está a menos de
   `TILES_TOLERANCIA_BORDE_PX` del borde de su tile que da hacia el otro tile.
2. **IoS** = área de la intersección ÷ área de la caja más chica, ≥ `TILES_FUSION_IOS`.
3. Si se cumplen las dos, las dos cajas se reemplazan por su **unión** (el rectángulo que las
   envuelve). Clase y score: los de la caja de **mayor área**, que es la que vio más de la hoja.
4. Se repite hasta que no quede ningún par (las esquinas de cuatro tiles se resuelven solas).

```
 cortada + entera                  pedazo + pedazo
 ┌──────┐┊┌───────────┐            ┌──────┬┊┬──────┐
 │  ┌───┼┊┼──────┐    │            │  ┌───┼┊┼───┐  │
 │  │ ▒▒│┊│▒▒▒▒▒▒│    │            │  │ ▒▒│┊│▒▒ │  │
 │  └───┼┊┼──────┘    │            │  └───┼┊┼───┘  │
 └──────┘┊└───────────┘            └──────┴┊┴──────┘
  IoS ≈ 1 → unión = la entera        IoS alto en la franja → unión = hoja completa
```

- *Por qué exigir el borde compartido*: en el follaje las hojas se pisan. Sin esa condición, una hoja
  chica dentro de la caja de una grande (IoS ≈ 1) se fusionaría y se perdería una hoja. Una caja
  cortada por un tile siempre toca su borde; dos hojas que se pisan en el medio de la franja, no.
- *Por qué IoS y no IoU*: la caja cortada está casi entera dentro de la otra aunque su IoU sea bajo.
- *Por qué fusionar (unión) y no suprimir*: cuando ningún tile vio la hoja entera, la unión de los
  pedazos es la mejor caja disponible. Cuando uno sí la vio, la unión coincide con ella.
- *Por qué sin mirar la clase*: no compara dos hojas, compara dos vistas de la misma hoja, y un pedazo
  sin contexto puede salir con otra clase. Es distinto del criterio de D2 de
  `add-diagnostico-por-detecciones` (una inválida no elimina a una válida), que sigue rigiendo entre
  hojas distintas dentro de `diagnose()`.
- *Por qué antes del filtro por clase*: los pedazos de una hoja suelen tener score bajo. Si se
  filtraran primero, el pedazo válido quedaría solo y se contaría como una hoja (bien) o los dos
  quedarían sin fusionar (una de más). Fusionando primero, la hoja entra al filtro una sola vez, con el
  score de su vista más completa.
- *Lo que no hace*: dos vistas **enteras** de una hoja que cae en la franja no tocan el borde y no se
  fusionan acá. Sus cajas son casi iguales (IoU ≈ 0,9), y las resuelve el paso de cajas superpuestas
  de `diagnose()`, con su orden calibrado.
- *Alternativa descartada: SAHI* (`sahi`, `get_sliced_prediction` + `GREEDYNMM`/IoS). Hace algo
  parecido, pero fusiona cualquier par por IoS, sin la condición del borde, y agrega una dependencia
  con su propio cálculo de la grilla. La regla son unas decenas de líneas puras y testeables.

### D5. El recorte viaja con el diagnóstico, normalizado

El alta agrega `crop: [x1, y1, x2, y2]`, normalizado sobre la imagen orientada, con el mismo formato
que una caja. Las `detections` pasan a estar normalizadas sobre la región recortada.

- *Por qué normalizado y no en píxeles*: es el mismo formato que las cajas, se valida igual y el front
  lo aplica con porcentajes de CSS sin esperar a que cargue la imagen para conocer su tamaño.
- *Por qué cajas relativas a la región*: el visor dibuja la región como si fuera la imagen, y el
  `DetectionOverlay` de hoy (SVG `0 0 1 1`) sirve sin cambios dentro de ella.
- *Por qué guardarlo con el diagnóstico y no con la captura*: el recorte es del servicio que
  diagnosticó. Si mañana se ajustan los píxeles, los diagnósticos viejos se siguen viendo con el
  recorte que usaron.
- *Opcional*: sin `crop`, todo es como hoy. La carga manual del simulador y los diagnósticos viejos
  no cambian.
- Persistencia: una columna nullable en `diagnostico` (cuatro números como JSON, igual que las
  detecciones en D8 de `add-diagnostico-por-detecciones`). La crea `ddl-auto=update`: no hace falta
  migración manual.
- Consulta: `GET /api/diagnosticos/{id}/detecciones` devuelve `{ detections, crop }`.

### D6. Visor: la región recortada con CSS

`CapturaModal` envuelve la imagen en un contenedor con `overflow: hidden` y la relación de aspecto de
la región (que sale de `crop` y del tamaño natural de la imagen). La `<img>` se escala y se desplaza
por porcentajes (`width: 100 / (x2 − x1) %`, `left: −x1 / (x2 − x1) · 100 %`, ídem en el otro eje), y
`DetectionOverlay` ocupa el contenedor. Las tres pestañas (Original, Diagnóstico, Todas) muestran la
región. El navegador aplica el EXIF por defecto (`image-orientation: from-image`), así que la imagen
que recorta es la misma que orientó el servicio.

Sin `crop` (o si fallan las detecciones) se muestra la imagen entera, como hoy.

### D7. Variables de entorno nuevas

Parámetros del equipo, como `SOLAPAMIENTO_IOU` y `BANDAS_CONFIANZA_*`: se leen al arrancar, una
ausente toma su valor de fábrica con un `WARNING` que la nombra, y una mal formada detiene el
servicio. `env.example` documenta cada una con una explicación breve:

| Variable | Fábrica | Qué hace |
|---|---|---|
| `RECORTE_IZQUIERDA` | 278 | Píxeles que se quitan del borde izquierdo de la imagen ya orientada, antes de dividirla en tiles. Sirve para sacar lo que no es el sector (riel, piso). Entero ≥ 0. |
| `RECORTE_ARRIBA` | 366 | Píxeles que se quitan del borde superior. Entero ≥ 0. |
| `RECORTE_DERECHA` | 0 | Píxeles que se quitan del borde derecho. Entero ≥ 0. |
| `RECORTE_ABAJO` | 0 | Píxeles que se quitan del borde inferior. Entero ≥ 0. |
| `TILES_FILAS` | 3 | En cuántas filas se divide la imagen recortada. Más filas, menos hojas por tile y más hojas cortadas. Entero ≥ 1. |
| `TILES_COLUMNAS` | 2 | En cuántas columnas se divide. Cada tile es una inferencia más del modelo. Entero ≥ 1. |
| `TILES_SOLAPAMIENTO_PX` | 130 | Píxeles que comparten dos tiles vecinos. Más solapamiento, más hojas enteras en algún tile, pero tiles más grandes. No cambia la cantidad de inferencias. Entero ≥ 0. |
| `TILES_TOLERANCIA_BORDE_PX` | 6 | Distancia máxima, en píxeles, entre el lado de una caja y el borde interno de su tile para considerarla cortada por ese borde. Absorbe la imprecisión del detector. Entero ≥ 0, menor que el solapamiento. |
| `TILES_FUSION_IOS` | 0,5 | Cuánto tiene que estar una caja cortada dentro de otra de un tile vecino (intersección ÷ área de la más chica) para fusionarlas como la misma hoja. Más alto, fusiona menos. Número en (0, 1]. |

Los píxeles de recorte se ajustan a mano si cambia la resolución de la cámara: un recorte que no entra
en la imagen deja la captura pendiente con un error que nombra las variables (no se recorta "lo que se
pueda", porque eso analizaría una región distinta sin avisar).

## Risks / Trade-offs

- **[Las hojas crecen con los plantines]** Con la cámara a altura fija, en unas semanas las hojas se
  ven más grandes y más quedan cortadas. → La fusión de pedazos (D4) degrada sin perder hojas; el
  solapamiento se sube por variable de entorno.
- **[Un pedazo no se detecta]** Media hoja tiene menos contexto: el detector puede no verla, o verla
  con otra clase. → Fusionar antes del filtro y tomar la clase de la vista más grande; el spike mide
  cuántas hojas se pierden o duplican contra un conteo manual.
- **[Fusión de hojas distintas]** Dos hojas que se pisan justo sobre el borde compartido pueden
  fusionarse. → La condición del borde lo limita a la franja; `TILES_FUSION_IOS` permite endurecerlo.
- **[Recorte atado a la resolución]** Si cambia `capturas.ancho-max`/`alto-max`, los píxeles quedan
  mal. → Error explícito si no entra; los diagnósticos viejos conservan su recorte (D5).
- **[Más tiempo por captura]** 6 inferencias en vez de 1. → El servicio procesa una captura cada
  tanto y en segundo plano; el tiempo de cada ciclo queda en el log.
- **[Dos cambios abiertos sobre el diagnóstico]** `add-diagnostico-por-detecciones` sigue sin
  archivar. → Este cambio sólo agrega requisitos (no modifica los de ese delta) y se archiva después.

## Migration Plan

1. Desplegar el backend (acepta y devuelve `crop` opcional; `ddl-auto` agrega la columna).
2. Desplegar el frontend (usa `crop` si viene).
3. Desplegar el servicio con las variables nuevas en su `.env` / `docker-compose.yml`.

Cualquier orden funciona porque `crop` es opcional, pero en éste el servicio nunca manda un campo que
el backend no conoce. Rollback: volver al servicio anterior; los diagnósticos con recorte se siguen
viendo bien.

## Open Questions

- Valores definitivos de `TILES_SOLAPAMIENTO_PX` y `TILES_FUSION_IOS`: los fija el spike.
- ¿Las miniaturas de las tarjetas de diagnóstico también muestran la región recortada? Requiere llevar
  `crop` al DTO de la tarjeta; queda para después si hace falta.
