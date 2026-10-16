# Servicio de Inferencia — Yerbanalytics

Daemon en Python que automatiza la asignación de diagnósticos a las capturas del vivero.
Consulta el backend periódicamente, corre el detector de hojas sobre cada imagen pendiente,
convierte todas sus detecciones en **un único diagnóstico** y lo registra por el camino único de
alta (`POST /api/diagnosticos`), el mismo que usa la carga manual del simulador.

Cambio de referencia: `openspec/changes/add-diagnostico-por-detecciones/` (design D1–D12).

## Arquitectura

```
[cada 10 s]
main.py
  → GET  /api/capturas/pendientes-inactivas      (backend Java)
  → si hay pendientes: GET /api/configuracion/diagnostico   (criterio del agrónomo, 1 vez por ciclo)
  → por captura:
      model.py      detect(imagen)   → todas las cajas con score ≥ YOLO_CONF_GLOBAL
      diagnosis.py  diagnose(...)    → estado, severidad, confianza, score promedio, detecciones
  → POST /api/diagnosticos                       (backend Java)
```

| Módulo | Qué hace |
|---|---|
| `src/settings.py` | Rutas, `MODEL_CLASSES` y parámetros del **equipo** (umbral global, IoU, bandas de confianza). Valida al arrancar. |
| `src/model.py` | Carga el detector YOLOv8 una vez y traduce `result.boxes` a detecciones (estado, score, caja `xyxyn`). No elige nada. |
| `src/diagnosis.py` | Lógica **pura** (sin I/O ni `ultralytics`): de las detecciones al diagnóstico. Se prueba sin GPU ni checkpoint. |
| `src/api.py` | Cliente HTTP con la sesión de la cuenta de servicio: pendientes, criterio y alta. |
| `src/main.py` | Orquestador: arranque, polling y tolerancia a fallos. |

El servicio no conoce la topología de sectores: opera sólo con IDs de captura. El backend asigna
el diagnóstico al sector vía la relación `captura → sector`.

## Pipeline del diagnóstico

El modelo es un **detector**: devuelve decenas de hojas por sector, cada una con su clase y su
score. El diagnóstico se arma así (`src/diagnosis.py`):

| # | Paso | Detalle |
|---|---|---|
| 1 | Inferencia | YOLO con `conf = YOLO_CONF_GLOBAL` (bajo, para no perder detecciones). |
| 2 | **Filtro por clase** | Una detección es válida si su score alcanza el umbral de validez **de su clase**. |
| 3 | **Solapes** | NMS calibrado entre clases, **sólo entre válidas**: entre cajas con IoU ≥ `SOLAPAMIENTO_IOU` queda una (ver abajo). |
| 4 | Agregación | Por estado: cantidad de detecciones y score promedio. |
| 5 | Severidad | Sólo anómalos: cantidad vs bandas del agrónomo (mínimo / media / alta). Bajo el mínimo, el estado queda fuera. Sano no tiene severidad. |
| 6 | Confianza | Score promedio vs bandas de su clase: Baja < media ≤ Media < alta ≤ Alta. |
| 7 | Elección | Mayor relevancia = severidad + confianza (Baja = 1, Media = 2, Alta = 3). Empate → el más crítico según el agrónomo. |
| 8 | Sin anómalos | Sano (con su confianza) si hay Sano válidas; si no, **No concluyente** (sin severidad, confianza ni score). |

**Por qué el filtro va antes que los solapes.** Una detección que no supera su propio umbral no
debe poder eliminar a una válida de otra clase. Con el orden inverso, una caja Sano de 0,55
(inválida: umbral 0,60) borraría a una Clorosis de 0,50 (válida: umbral 0,40) sobre la misma hoja,
y después el filtro tiraría a la Sano: la hoja desaparecería del análisis.

**Cuál se conserva en un solape.** Se ordenan las válidas por
`(nivel de confianza de su clase ↓, criticidad ↓ con Sano última, score ↓)` y se aplica NMS greedy.
El nivel reusa la calibración por clase, porque los scores crudos no son comparables entre clases.
Ejemplo: Sano 0,82 (alta en Sano) y Clorosis 0,72 (alta en Clorosis) sobre la misma hoja → empate
de nivel → queda Clorosis, el estado más peligroso.

**Qué se entrega.** El alta lleva `estado`, `sev` (sólo anómalos), `confidence` (salvo No
concluyente), `conf` = score promedio × 100 del estado elegido (salvo No concluyente) y
`detections`: **todas** las válidas que sobrevivieron a los solapes, de todas las clases, con caja
normalizada `[x1, y1, x2, y2]` a 4 decimales. Con ellas el dashboard dibuja las cajas sobre la foto.

**Dos dueños, dos criterios.**

- **Equipo** (calibración del modelo): umbral global, IoU y bandas de confianza por clase.
  Variables de entorno de este servicio; no se editan desde el dashboard.
- **Agrónomo** (criterio agronómico): bandas de severidad por clase y orden de criticidad.
  Viven en el backend (Configuración → Criterio de diagnóstico) y el servicio las lee **en cada
  ciclo con pendientes, sin caché**: un cambio se aplica a la próxima captura. Si el pedido falla, o
  a algún estado anómalo de `MODEL_CLASSES` le falta criterio, el ciclo se omite y las capturas
  siguen pendientes.

## Modelo

`modelo_final.pt`: **YOLOv8 de detección**, `imgsz=1024` (lo toma del propio checkpoint), clases
`0 clorosis · 1 colapso_hongos · 2 estres_solar · 3 plaga_foliar · 4 sano`.

**No se versiona** (`*.pt` está en el `.gitignore` de la raíz): es el entregable del equipo de IA
(carpeta `Modelo_Final/` de su entrenamiento) y se le pide a él. Se copia a la carpeta del
servicio: `Desarrollo/servicio-inferencia/modelo_final.pt`. Con `MODEL_PATH=modelo_final.pt` el
servicio lo encuentra se lo lance desde donde se lo lance.

Al arrancar, el servicio exige que `MODEL_CLASSES` tenga tantas clases como el checkpoint y deja en
el log el mapeo, para que un orden equivocado se vea:

```
[INFO] model —   0 → clorosis → Clorosis
[INFO] model —   1 → colapso_hongos → Daño fúngico
...
```

## Cuenta de servicio

El backend exige sesión de usuario en toda `/api/**`, y este servicio entra como cualquier otro
cliente: con una cuenta de rol **Servicio** (`BACKEND_USUARIO` / `BACKEND_CLAVE`). El rol ya trae
lo que hace falta (`capturas.ver`, `diagnosticos.ver` —para leer el criterio—,
`diagnosticos.registrar`).

- **En desarrollo ya viene creada.** El `env.example` del backend la declara en
  `YERBANALYTICS_USUARIOS_INICIALES` (`inferencia` / `inferencia-yerba-2026`), la misma que traen
  `.env.example` y el default de `docker-compose.yml`. En otro entorno, cambiá la clave en los dos lados.
- **Si la da de alta el Administrador** desde el dashboard, nace con clave temporal: hasta que se
  cambia, el backend responde `403` a todo. Entrá una vez con esa cuenta, cambiá la clave y poné la
  nueva en `BACKEND_CLAVE`.
- **Login perezoso.** La primera llamada inicia sesión; la cookie `YERBA_SESION` queda en un
  `requests.Session` del módulo (`src/api.py`).
- **Sesión vencida.** Si la sesión vence por inactividad, la próxima llamada recibe `401`, reinicia
  sesión y reintenta **una sola vez**.
- **Sin credenciales** no intenta el login: lo deja en el log como error y el backend rechaza la
  llamada, que se maneja como cualquier otro fallo del backend (se reintenta el próximo ciclo).

## Prerrequisitos

| Requisito | Mínimo recomendado |
|---|---|
| Python | 3.10+ |
| RAM | 2 GB |
| GPU (opcional) | CUDA 11.8+ para aceleración |
| Disco | Acceso al mismo directorio de capturas que el backend |

> Sin GPU la inferencia corre en CPU (del orden de 0,1–0,5 s por imagen de 1024 px).

## Configuración

```bash
cp .env.example .env
# Revisá MODEL_PATH, CAPTURAS_DIR y las credenciales.
```

El servicio carga **`<carpeta del servicio>/.env`** (no el del directorio de trabajo) y **no pisa**
las variables que ya estén en el entorno: en Docker mandan las de `docker-compose.yml`.
Las rutas relativas (`MODEL_PATH`, `CAPTURAS_DIR`) se resuelven contra la carpeta del servicio.

### Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `MODEL_PATH` | — | **Obligatoria.** Checkpoint del detector (`modelo_final.pt`). Relativa = contra la carpeta del servicio. |
| `MODEL_CLASSES` | — | **Obligatoria.** Estados de la plataforma en el orden de `model.names`. |
| `BACKEND_URL` | `http://localhost:8000` | URL base del backend. |
| `BACKEND_USUARIO` | — | Usuario de la cuenta de rol Servicio. |
| `BACKEND_CLAVE` | — | Su clave. Sin ella, el backend rechaza las llamadas. |
| `CAPTURAS_DIR` | `/capturas` | Directorio raíz de las imágenes. Local: `../backend/capturas`. |
| `POLLING_INTERVAL_SECONDS` | `14400` | Se informa en el log; el ciclo real corre cada 10 s y el backend decide qué capturas están listas (tiempo de quietud). |
| `YOLO_CONF_GLOBAL` | `0.10` | Umbral global de la inferencia. Debe ser ≤ al menor umbral de validez. |
| `SOLAPAMIENTO_IOU` | `0.60` | IoU desde el que dos cajas válidas son la misma hoja. |
| `BANDAS_CONFIANZA_SANO` | `0.60,0.70,0.80` | `válida, media desde, alta desde` sobre el score crudo. |
| `BANDAS_CONFIANZA_CLOROSIS` | `0.40,0.55,0.70` | idem |
| `BANDAS_CONFIANZA_ESTRES_SOLAR` | `0.40,0.55,0.70` | idem |
| `BANDAS_CONFIANZA_PLAGA_FOLIAR` | `0.35,0.50,0.65` | idem (umbral más bajo: clase peligrosa, se prioriza recall) |
| `BANDAS_CONFIANZA_DANO_FUNGICO` | `0.35,0.50,0.65` | idem |

> **Los parámetros del equipo son PROVISORIOS**: no salen todavía de las curvas PR. Se cambian por
> entorno, sin tocar código.

Reglas de los parámetros del equipo:

- La clave de cada clase es el estado de la plataforma sin acentos ni espacios
  (`Daño fúngico` → `BANDAS_CONFIANZA_DANO_FUNGICO`), no el nombre interno del checkpoint.
- **Ausente** → toma el default y deja un `WARNING` que nombra la variable.
- **Mal formada** (no son tres números en `(0, 1]` estrictamente crecientes) o `YOLO_CONF_GLOBAL`
  mayor que algún umbral de validez → el servicio **no arranca** (`CRITICAL` + `exit 1`). Una
  calibración inválida produciría diagnósticos plausibles y errados sin ningún aviso.
- Una clase de `MODEL_CLASSES` sin default (p. ej. `Ácaro`) exige su variable.

### Mapeo de clases (`MODEL_CLASSES`)

El detector devuelve un índice de clase por caja. `MODEL_CLASSES` define a qué estado de la
plataforma corresponde cada índice, en el orden de `model.names` del checkpoint. Para
`modelo_final.pt`:

```
MODEL_CLASSES=Clorosis,Daño fúngico,Estrés solar,Plaga foliar,Sano
```

Valores válidos: `Sano`, `Clorosis`, `Estrés solar`, `Daño biótico`, `Ácaro`, `Plaga foliar`,
`Daño fúngico`. `No concluyente` no es una clase del modelo: es un resultado del diagnóstico.

## Instalación y ejecución local

```bash
cd Desarrollo/servicio-inferencia

python -m venv .venv
.venv\Scripts\activate       # Windows
# source .venv/bin/activate  # Linux/macOS

pip install -r requirements.txt
cp .env.example .env
# copiá modelo_final.pt a esta carpeta

python src/main.py           # o: cd src && python main.py
```

## Tests

La lógica del diagnóstico y la validación de parámetros se prueban sin YOLO ni backend:

```bash
pip install -r requirements-dev.txt
python -m pytest
```

| Archivo | Cubre |
|---|---|
| `tests/test_diagnosis.py` | El ejemplo del documento, empates (relevancia y solapes), filtro antes que solapes, IoU, Sano, No concluyente, bajo el mínimo, estado sin criterio. |
| `tests/test_settings.py` | Defaults, variable ausente, bandas mal formadas, umbral global por encima de un umbral de clase, rutas relativas. |
| `tests/test_api.py` | Cuerpo del alta para un anómalo, Sano y No concluyente. |

## Ejecución con Docker Compose

El servicio está declarado en `docker-compose.yml` en la raíz del repositorio. El `.pt` se monta
como volumen y `MODEL_PATH` apunta a la ruta dentro del contenedor; los parámetros del equipo se
pasan por entorno.

```bash
INFERENCIA_BACKEND_CLAVE=... docker compose up servicio-inferencia
```

El volumen `capturas-data` es compartido entre el backend y este servicio.

## Tolerancia a fallos

| Escenario | Comportamiento |
|---|---|
| Parámetros del equipo mal formados | `CRITICAL`, el servicio no arranca |
| `MODEL_CLASSES` con otra cantidad de clases que el checkpoint | `CRITICAL`, el servicio no arranca |
| Backend no responde al GET de pendientes | Log error, espera el próximo ciclo |
| No se puede leer el criterio del agrónomo | Log error, se omite el ciclo; las capturas siguen pendientes |
| Estado anómalo del modelo sin criterio | Log error, se omite el ciclo; las capturas siguen pendientes |
| Sesión vencida o revocada (`401`) | Reinicia sesión y reintenta una vez |
| Credenciales faltantes o rechazadas | Log error, espera el próximo ciclo |
| Archivo JPEG no existe en disco | Log warning, pasa a la siguiente captura |
| JPEG ilegible o error durante la inferencia | Log error, pasa a la siguiente captura |
| Backend rechaza el POST (`400`) | Log error con el mensaje del backend; la captura reaparece en el próximo ciclo |
| Reinicio del proceso | La captura vuelve a aparecer como pendiente y se reprocesa (idempotente) |
