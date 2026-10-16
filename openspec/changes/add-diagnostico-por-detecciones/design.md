## Context

Hoy el pipeline de visión es:

```
servicio-inferencia (Python, polling cada 10 s)
  GET  /api/capturas/pendientes-inactivas
  YOLO(imagen) → la caja de MAYOR score → estado; severidad = f(ese score)
  POST /api/diagnosticos {capturaId, estado, conf (0-100), sev}
backend
  diagnostico (conf NOT NULL) → sector.diagnosis_* → motor (SupplyRule: conf ≥ 85 %)
```

Problemas:

- `modelo_final.pt` es **YOLOv8 de detección** (`task=detect`, `imgsz=1024`, clases
  `0 clorosis · 1 colapso_hongos · 2 estres_solar · 3 plaga_foliar · 4 sano`). Devuelve decenas de
  hojas por imagen; quedarse con una sola caja descarta casi toda la información del sector.
- La severidad sale del score de esa caja: confunde "qué tan seguro está el modelo" con "qué tan
  grave está el sector".
- Un único umbral de confianza global ignora que los scores no son comparables entre clases.

El documento *Lógica del diagnóstico visual* define la nueva semántica: tres capas desacopladas
(modelo → módulo de diagnóstico → motor de reglas), con parámetros del **equipo** (umbrales y bandas
de confianza por clase) y del **agrónomo** (bandas de severidad y orden de criticidad).

Restricciones:

- **El motor de reglas no se toca** (decisión explícita). `SupplyRule` sigue leyendo
  `sector.diagnosisConf` (Double) contra `diagnostico.confianza-minima`.
- **Camino único de alta** (`POST /api/diagnosticos`): el simulador (carga manual) y la inferencia
  siguen usando exactamente el mismo endpoint, sin marca de origen.
- El backend no conoce al simulador; el contrato de cámara y el MQTT no cambian.
- Código nuevo en inglés; textos, comentarios y docs en español.

## Goals / Non-Goals

**Goals:**

- Implementar el módulo de diagnóstico completo (pasos 2–9 del documento) dentro del servicio de
  inferencia, como lógica pura testeable sin cargar YOLO.
- Resolver las cajas superpuestas (paso 3, pendiente en el documento).
- Parámetros del equipo por variables de entorno del servicio, con defaults provisorios.
- Criterio del agrónomo persistido en el backend y editable desde Configuración → Criterio de diagnóstico.
- Persistir las detecciones que intervinieron y mostrarlas sobre la imagen con un toggle.
- Usar `modelo_final.pt` como modelo vigente en todos los entornos de ejemplo.

**Non-Goals:**

- Cambiar reglas del motor, sus parámetros o su traza (cambio posterior).
- Recalibrar los umbrales del equipo: los defaults son provisorios hasta que el equipo los defina
  con las curvas PR.
- Guardar con cada diagnóstico una copia del criterio con que se calculó (trazabilidad histórica del
  criterio). Ver Open Questions.
- Multi-label o más de un diagnóstico por sector (costo asumido, §2.6 del documento).
- Reentrenar o versionar el modelo; agregar clases.

## Decisions

### D1. El módulo de diagnóstico vive en el servicio de inferencia, como funciones puras

`src/diagnosis.py` recibe una lista de detecciones (`label` de la plataforma, `score`, `box`
normalizada) más los parámetros del equipo y el criterio del agrónomo, y devuelve un `Diagnosis`
(`state`, `severity`, `confidence`, `mean_score`, `detections`). No importa `ultralytics` ni hace
I/O. `model.py` sólo corre YOLO y traduce `result.boxes` a detecciones.

- *Por qué ahí y no en el backend*: el usuario pidió que los umbrales del equipo sean variables de
  entorno del servicio, y el documento los separa del agrónomo. Si el backend eligiera el
  diagnóstico, el servicio tendría que mandar detecciones crudas y el backend conocer umbrales del
  modelo: la calibración del modelo quedaría acoplada a la plataforma.
- *Por qué puras*: el ejemplo del §4 del documento se vuelve un test unitario literal, y la lógica
  se prueba sin GPU ni checkpoint.
- *Alternativa descartada*: dividir el módulo (filtro y confianza en Python, severidad y elección en
  Java). Dos mitades de un mismo cálculo en dos lenguajes, con el criterio de elección repartido.

### D2. Orden del pipeline: el filtro va ANTES que los solapes

El documento lista "superposiciones" (3) antes del "filtro" (4). Se invierte:

```
1 Inferencia (conf = YOLO_CONF_GLOBAL)       → todas las detecciones
2 Filtro por umbral de su clase              → detecciones válidas
3 Solapes, sólo entre válidas (D3)           → una caja por hoja
4 Agregación por estado: cantidad, promedio de score
5 Severidad (anómalos): cantidad vs bandas del agrónomo; bajo el mínimo → fuera
6 Confianza: promedio vs bandas de la clase
7 Elección: max(relevancia = sev + conf); empate → criticidad
8 Sin anómalos: Sano (si hay Sano válidas) o No concluyente
```

Una detección que no supera su propio umbral no debe poder eliminar a una válida de otra clase.
Con el orden del documento, una caja Sano de 0,55 (inválida, umbral 0,60) borraría a una Clorosis de
0,50 (válida, umbral 0,40) y después el filtro tiraría a la Sano: la hoja desaparecería del análisis.

### D3. Cajas superpuestas: NMS calibrado entre clases

Se ordenan las detecciones válidas por la clave
`(nivel de confianza de su clase ↓, criticidad ↓, score ↓)` y se aplica NMS greedy: se conserva la
primera y se descartan las que la pisan con `IoU ≥ SOLAPAMIENTO_IOU` (default `0,6`). Se aplica entre
todas las clases (las duplicadas de una misma clase ya las resolvió el NMS por clase de YOLO; volver
a aplicarlo no cambia nada).

- **Nivel de una detección** = su score ubicado en las bandas de confianza de su clase (D4). Reusa la
  calibración por clase: no compara scores crudos entre clases, que el documento declara no
  comparables.
- **Criticidad** = orden del agrónomo para los anómalos; **Sano va siempre última**. Ante igual nivel
  se conserva el estado más peligroso, coherente con el desempate del §2.3.
- Ejemplo: misma hoja con Sano 0,82 (alta en Sano) y Clorosis 0,72 (alta en Clorosis) → empate de
  nivel → queda Clorosis.

*Alternativas descartadas*:

- `agnostic_nms=True` de Ultralytics: corre antes del filtro por clase (problema de D2) y elige por
  score crudo.
- Criticidad pura: una caja de Daño fúngico apenas sobre el umbral le gana a un Sano con confianza
  alta; sube los falsos positivos.
- Margen normalizado sobre el umbral (`(score − umbral) / (1 − umbral)`): más fino, pero introduce una
  fórmula nueva que nadie calibró; los niveles ya son el lenguaje del documento.

### D4. Parámetros del equipo por variables de entorno (`src/settings.py`)

| Variable | Default provisorio | Significado |
|---|---|---|
| `YOLO_CONF_GLOBAL` | `0.10` | Umbral global de la inferencia. Debe ser ≤ al menor umbral de clase. |
| `SOLAPAMIENTO_IOU` | `0.60` | IoU desde el que dos cajas son la misma hoja (D3). |
| `BANDAS_CONFIANZA_SANO` | `0.60,0.70,0.80` | `válida, media desde, alta desde` sobre el score crudo. |
| `BANDAS_CONFIANZA_CLOROSIS` | `0.40,0.55,0.70` | idem |
| `BANDAS_CONFIANZA_ESTRES_SOLAR` | `0.40,0.55,0.70` | idem |
| `BANDAS_CONFIANZA_PLAGA_FOLIAR` | `0.35,0.50,0.65` | idem (umbral más bajo: clase peligrosa, se prioriza recall) |
| `BANDAS_CONFIANZA_DANO_FUNGICO` | `0.35,0.50,0.65` | idem |

- Una variable por clase con tres cortes: legible en un `.env` y en `docker-compose.yml`, sin un
  formato anidado que haya que parsear a mano.
- Se validan **al arrancar**: tres números en `(0, 1]`, estrictamente crecientes, y
  `YOLO_CONF_GLOBAL ≤` todos los umbrales de validez. Un valor mal formado **detiene el servicio**
  (`sys.exit(1)`): una calibración inválida produce diagnósticos plausibles y errados sin ningún
  aviso. Una variable ausente toma su default con un `WARNING` que la nombra.
- La clave de cada clase sale del estado de la plataforma (`Daño fúngico` → `DANO_FUNGICO`), no del
  nombre interno del checkpoint (`colapso_hongos`), porque `MODEL_CLASSES` ya traduce uno al otro.
- Al arrancar, el servicio exige además `len(MODEL_CLASSES) == len(model.names)` y loguea el mapeo
  índice → nombre del checkpoint → estado, para que un orden equivocado se vea en el log.

### D5. Criterio del agrónomo: recurso propio en el backend

Tabla `diagnosis_criteria`, una fila por estado anómalo que emite el modelo (`Clorosis`,
`Estrés solar`, `Plaga foliar`, `Daño fúngico`):

| columna | |
|---|---|
| `state` (PK) | estado de la plataforma |
| `min_count`, `medium_from`, `high_from` | bandas de severidad en cantidad de hojas |
| `criticality_rank` | 1 = más crítico; permutación de 1..4 |

Defaults (sembrados si la tabla está vacía, como la matriz de permisos): Daño fúngico 2/4/8,
Plaga foliar 2/5/10, Clorosis 5/10/20, Estrés solar 5/10/20; criticidad
Daño fúngico > Plaga foliar > Clorosis > Estrés solar.

API (`DiagnosisCriteriaController`):

```
GET /api/configuracion/diagnostico   → diagnosticos.ver
PUT /api/configuracion/diagnostico   → configuracion.editar
{
  "severityBands": [ {"state": "Daño fúngico", "minimum": 2, "mediumFrom": 4, "highFrom": 8}, … ],
  "criticality":   ["Daño fúngico", "Plaga foliar", "Clorosis", "Estrés solar"]
}
```

Validación del `PUT` (`400` con mensaje apto para mostrar): enteros, `1 ≤ minimum < mediumFrom <
highFrom`, exactamente las cuatro clases, criticidad = permutación de esas cuatro. El cambio se
registra en el historial como una recalibración (mismo patrón que `registrarConfiguracion`).

- *Por qué un recurso aparte y no dentro de `/api/configuracion`*: el servicio de inferencia necesita
  leerlo, y su rol (Servicio) tiene `diagnosticos.ver` pero no `configuracion.ver`. Leer con
  `diagnosticos.ver` es coherente: quien registra diagnósticos necesita conocer el criterio con que
  se eligen. No hay que tocar la matriz por defecto, que sólo se siembra en bases vacías.
- *Por qué no en el catálogo de parámetros del motor*: el catálogo es del motor (`engine/parametros/`)
  y el motor no se toca; además, el criterio describe el diagnóstico, no una acción.
- *Por qué el backend no recalcula*: el criterio se usa en el servicio. El backend sólo lo guarda,
  lo valida y lo sirve; no valida que un diagnóstico entrante "cumpla" las bandas (el camino manual
  del simulador debe poder cargar cualquier combinación válida).

### D6. Lectura del criterio en cada ciclo, sin caché

Cuando hay capturas pendientes, el servicio pide el criterio **una vez por ciclo**, antes de
procesarlas. Si el pedido falla, el ciclo se saltea y las capturas siguen pendientes. Un estado
emitido por el modelo sin fila de criterio también saltea el ciclo con un `ERROR` claro.

*Alternativa descartada*: cachear el último criterio conocido. Un cambio del agrónomo tendría que
esperar a que el caché venza, y si el backend no responde tampoco se podría registrar el
diagnóstico.

### D7. Contrato del alta (`POST /api/diagnosticos`) — BREAKING

```json
{
  "capturaId": "CAP-…",
  "estado": "Clorosis",
  "sev": "Baja",              // Baja|Media|Alta; sólo en estados anómalos
  "confidence": "Media",      // Baja|Media|Alta; todos salvo No concluyente
  "conf": 62.0,               // score promedio × 100 del estado diagnosticado; null en No concluyente
  "detections": [             // opcional (la carga manual no trae); máx. 2000
    {"label": "Clorosis", "score": 0.62, "box": [0.12, 0.30, 0.18, 0.37]}
  ]
}
```

Reglas de validación (todas `400`):

| estado | `sev` | `confidence` | `conf` |
|---|---|---|---|
| anómalo (Clorosis, Estrés solar, Plaga foliar, Daño fúngico, Daño biótico, Ácaro) | obligatoria | obligatoria | obligatorio |
| Sano | ausente | obligatoria | obligatorio |
| No concluyente | ausente | ausente | ausente |

Detecciones: `label` en la taxonomía, `score ∈ [0, 1]`, `box` = 4 números en `[0, 1]` con
`x1 < x2` e `y1 < y2` (coordenadas normalizadas `xyxyn`, independientes de la resolución).

- **`conf` se conserva** y pasa a significar *score promedio × 100*. Es lo que permite que el motor
  siga igual: `SupplyRule` sigue comparando un Double contra su umbral, y con `null` (No
  concluyente) ya cae en "sin dato" (`Evaluacion.comparar` lo contempla). La semántica cambió y la
  regla se revisa en el cambio de reglas; queda en Risks.
- `sev` sigue persistiéndose con `—` cuando no aplica (convención existente de `diagnosis_sev`,
  `NOT NULL`); en la API entra y sale como ausente/`—`.
- Nombres nuevos en inglés (`confidence`, `detections`), conviviendo con los existentes en español
  (`estado`, `sev`, `conf`) que se migran cuando se reescriban, según la convención del repo.

### D8. Persistencia de detecciones: columna JSON en `diagnostico`

Columnas nuevas: `confidence` (texto, nulo), `detections` (texto JSON), `detection_count` (entero).
`sector` suma `diagnosis_confidence`. Se guardan **todas las detecciones válidas que sobrevivieron a
los solapes**, de todas las clases (lo necesita la vista "Todas").

- *Por qué JSON y no una tabla `deteccion`*: sólo se leen juntas para dibujarlas, nunca se consultan
  por campo. ~130 cajas × 600 sectores × pasada serían ~80 k filas por pasada; como JSON es una
  fila de ~10 KB por diagnóstico.
- `detection_count` evita parsear el JSON al armar el snapshot: la tarjeta sabe si ofrece el toggle
  sin pedir las cajas.
- Consulta: `GET /api/diagnosticos/{id}/detecciones` → `{ "detections": [...] }` (cubierto por
  `GET /api/diagnosticos/**` → `diagnosticos.ver`). No va en el snapshot `/api/nursery`: sería
  decenas de KB por tarjeta.
- `ddl-auto=update` agrega las columnas, pero **no quita el `NOT NULL` de `diagnostico.conf`**: hace
  falta `resources/migracion-diagnostico-detecciones.sql` (manual, como los otros cinco). Las filas
  anteriores quedan con `confidence` nulo y la UI las muestra "sin nivel".

### D9. "Concluyente" = estado distinto de No concluyente

`Diagnostico.concluyente` y `DiagnosisCard.concluyente` dejan de derivarse de
`diagnostico.confianza-minima` (un parámetro del motor) y pasan a ser `estado != "No concluyente"`.
La decisión de no concluir ahora la toma el módulo de diagnóstico; si el motor actúa o no, es del
motor. Se elimina el chip "Bajo el umbral" de la tarjeta. `ParametrosDiagnostico` no se toca.

### D10. UI

- **Configuración → Criterio de diagnóstico** (`/criterio-diagnostico`, ítem del grupo desplegable): dos
  bloques — *Bandas de severidad* (tabla de 4 clases × mínimo / media desde / alta desde, con
  `InputUnit` "hojas") y *Orden de criticidad* (lista reordenable con botones subir/bajar, accesible
  por teclado; sin drag & drop). Reusa `ConfigLayout` y la `SaveBar`: `ConfigDraft` se generaliza a
  `ConfigDraft<T>` y la vista usa su propio borrador (`useDiagnosisCriteriaDraft`), porque es otro
  recurso. Permisos de la vista: `configuracion.ver` + `diagnosticos.ver`; editar,
  `configuracion.editar`. Ruta propia (no `/configuracion/...`) porque `vistaDeRuta` identifica la
  vista por el primer segmento. Incluye un texto que aclara que los umbrales de confianza son del
  equipo y no se editan acá.
- **Tarjeta (`DiagnosisTile`)**: chip de nivel de confianza (`Confianza alta`), severidad sólo si es
  anómalo. **Ni el score promedio ni la cantidad de detecciones se muestran en el dashboard**
  (decisión de la usuaria): son datos internos del módulo. El filtro de columna es por nivel de
  confianza, no por score. Sin barra 0–100 % ni aviso de actuación.
- **Modal (`CapturaModal`)**: `Segmented` *Original · Diagnóstico · Todas* (default *Original*, para no
  cambiar lo que ya se ve), visible sólo si `detectionCount > 0`. Las cajas se piden al abrir el
  modal la primera vez que se elige una vista con cajas. Se dibujan en un `<svg viewBox="0 0 1 1"
  preserveAspectRatio="none">` superpuesto a un contenedor del tamaño exacto de la imagen.
  - *Diagnóstico*: sólo las del estado diagnosticado, trazo continuo grueso.
  - *Todas*: cada clase con su color; las del estado diagnosticado con trazo continuo grueso y las
    demás con trazo discontinuo fino (forma además de color, como pide el sistema de diseño).
    Leyenda con clase y estilo de trazo, sin cantidades.
  - Colores: tokens nuevos `--det-sano`, `--det-clorosis`, `--det-estres-solar`,
    `--det-plaga-foliar`, `--det-dano-fungico` en `tokens.css` (+ `theme.ts` y catálogo
    `/sistema-diseno`), **restringidos a superposiciones sobre fotografía** (misma categoría de uso
    que `--tray`: arte). Se eligen con contraste sobre follaje verde.
- **Detalle de sector**: el mismo modal; el id del diagnóstico se toma de la tarjeta `DX-` más
  reciente de ese sector en `useNurseryData().diagnoses`.
- **Modo mock**: el repositorio mock genera detecciones determinísticas para las capturas de demo,
  así el toggle se puede ver en `npm run dev:demo`.

### D11. `modelo_final.pt` y rutas

- `MODEL_PATH` relativa se resuelve contra la carpeta del servicio (padre de `src/`), no contra el
  directorio de trabajo: `MODEL_PATH=modelo_final.pt` funciona con `python src/main.py` desde la
  carpeta del servicio o desde `src/`.
- `.env` y `.env.example`: `MODEL_PATH=modelo_final.pt`.
- `docker-compose.yml`: monta `./Desarrollo/servicio-inferencia/modelo_final.pt:/modelo_final.pt:ro`
  y `MODEL_PATH=/modelo_final.pt` (reemplaza `./best.pt`).
- El `.pt` sigue fuera de Git (`*.pt` en `.gitignore`); el README dice de dónde sacarlo.

### D12. Contrato entre las partes (fuente única para backend, frontend, servicio y simulador)

Tipos compartidos: `ConfidenceLevel = "Baja" | "Media" | "Alta"`; `Severity` sigue siendo
`"Alta" | "Media" | "Baja" | "—"` (`"—"` = sin severidad). Errores de validación: `400` con
`{"error": "<mensaje apto para mostrar>"}`.

**Alta** — `POST /api/diagnosticos` (ver D7). En `sev`, ausente / `null` / `"—"` significan "sin
severidad". `detections` ausente = `[]`.

**Detección** (alta y consulta): `{"label": "<estado de la plataforma>", "score": 0.62, "box": [x1, y1, x2, y2]}`
con coordenadas normalizadas `xyxyn`, redondeadas a 4 decimales; `score` a 4 decimales.

**Consulta de detecciones** — `GET /api/diagnosticos/{id}/detecciones` →
`{"detections": [Detección, …]}`; `404` `{"error": …}` si el id no existe (incluye los `DG-`
derivados del sector).

**Criterio** — `GET/PUT /api/configuracion/diagnostico`:

```json
{
  "severityBands": [
    {"state": "Clorosis",     "minimum": 5, "mediumFrom": 10, "highFrom": 20},
    {"state": "Estrés solar", "minimum": 5, "mediumFrom": 10, "highFrom": 20},
    {"state": "Plaga foliar", "minimum": 2, "mediumFrom": 5,  "highFrom": 10},
    {"state": "Daño fúngico", "minimum": 2, "mediumFrom": 4,  "highFrom": 8}
  ],
  "criticality": ["Daño fúngico", "Plaga foliar", "Clorosis", "Estrés solar"]
}
```

`severityBands` sale en ese orden fijo; `criticality[0]` es el más crítico. El `PUT` devuelve el
criterio guardado.

**Campos nuevos en los DTO de lectura** (todo lo demás no cambia):

| DTO (backend → `types/domain.ts`) | Cambios |
|---|---|
| `Diagnosis` (sector del snapshot) | `conf: number \| null`, `+ confidence: ConfidenceLevel \| null` |
| `DiagnosisCard` | `conf: number \| null`, `+ confidence: ConfidenceLevel \| null`, `+ detectionCount: number`; `concluyente` según D9 |
| `Diagnostico` (`GET /api/diagnosticos`) | `conf: number \| null`, `+ confidence`, `+ detectionCount`; `concluyente` según D9 |
| `DiagnosticoPaso` (Demo Expo) | `conf: number \| null`, `+ confidence: ConfidenceLevel \| null` |
| `DiagnosisDetail` | `+ confidence: ConfidenceLevel \| null` |

Las tarjetas `DG-` derivadas del sector toman `confidence` del sector y `detectionCount = 0`; las
"No concluyente" sintéticas, `conf = null`, `confidence = null`.

## Risks / Trade-offs

- **[Semántica de `conf` en el motor]** `SupplyRule` compara el score promedio × 100 contra 85 %; con
  bandas por clase eso ya no es "confianza suficiente" en el sentido del documento. → Aceptado por
  decisión: el motor no se toca en este cambio y la regla se rediseña después. Queda anotado en el
  README del backend y en `diferencias-motor-reglas-vs-reglas-v2.md`.
- **[Defaults provisorios]** Los umbrales del equipo no salen de curvas PR. → Se documentan como
  provisorios en `.env.example` y README; se cambian por entorno sin tocar código.
- **[Contrato BREAKING]** Un emisor viejo (simulador sin actualizar, servicio viejo) recibe `400`. →
  Se actualizan simulador y servicio en el mismo cambio; el mensaje de error nombra el campo.
- **[NMS entre clases puede ocultar una lesión real]** Si dos hojas distintas se superponen mucho en
  la vista cenital, una se pierde. → IoU configurable; las descartadas no se persisten, pero el
  default 0,6 es conservador.
- **[El criterio no queda versionado con el diagnóstico]** Si el agrónomo cambia las bandas, un
  diagnóstico viejo no explica con qué bandas se eligió. → Fuera de alcance; el historial registra
  cuándo cambió el criterio.
- **[Tamaño del JSON]** Una imagen con miles de detecciones inflaría la fila. → Tope de 2000 en el
  alta; con el umbral global en 0,10 y `max_det` de YOLO (300 por defecto) no se alcanza.
- **[Tokens nuevos de color]** Agregan tinta saturada fuera de los estados. → Uso restringido a
  superposiciones sobre foto, documentado en el catálogo; `tokens.test.ts` se actualiza.

## Migration Plan

1. Backend: correr `migracion-diagnostico-detecciones.sql` (quita `NOT NULL` de `diagnostico.conf`)
   antes o después de arrancar la versión nueva; `ddl-auto` crea tabla y columnas. El criterio se
   siembra solo.
2. Servicio de inferencia: copiar `modelo_final.pt` a su carpeta, actualizar `.env` (o tomar los
   defaults), reiniciar.
3. Simulador y dashboard: se despliegan con el backend.
4. Rollback: volver a la versión anterior de los tres; las columnas nuevas quedan sin uso. Si ya
   hay diagnósticos No concluyente con `conf` nulo, el backend viejo los leería como `null` en un
   `double` del DTO: antes de volver atrás, `UPDATE diagnostico SET conf = 0 WHERE conf IS NULL`.

## Open Questions

- Valores definitivos de los umbrales y bandas de confianza (equipo, con curvas PR y el conjunto
  fijo de imágenes de campo).
- ¿Hace falta guardar el criterio vigente junto con cada diagnóstico (snapshot) para auditoría?
- Rediseño de `SupplyRule` (y del resto de reglas que usen el diagnóstico) sobre niveles de
  severidad y confianza: cambio aparte.
