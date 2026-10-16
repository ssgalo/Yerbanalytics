## Why

El servicio de inferencia trata al modelo como si fuera un clasificador: toma **la caja de mayor
score** de la imagen, la convierte en estado y deriva la severidad de ese mismo score. Pero
`modelo_final.pt` es un **detector YOLOv8** (5 clases, `imgsz=1024`) que devuelve decenas de hojas
por sector: una sola caja no describe el sector, y la confianza de una detección no mide la
gravedad. Hace falta una capa explícita —el **módulo de diagnóstico**— que convierta todas las
detecciones en un único diagnóstico con estado, nivel de severidad y nivel de confianza, con
criterios separados para el equipo (calibración del modelo) y para el agrónomo (criterio
agronómico).

## What Changes

- **Módulo de diagnóstico en el servicio de inferencia** (`Desarrollo/servicio-inferencia/`):
  inferencia con umbral global bajo → filtro por umbral de clase → resolución de cajas superpuestas
  → agregación por estado (cantidad y score promedio) → severidad por cantidad absoluta → confianza
  por bandas de la clase → elección por relevancia (severidad + confianza) con desempate por orden
  de criticidad → Sano o No concluyente si no queda ningún estado anómalo.
- **Cajas superpuestas resueltas** (el pendiente del documento): NMS *calibrado* aplicado **después**
  del filtro por clase y sólo entre detecciones válidas; entre cajas que se pisan queda la de mayor
  nivel de confianza de su clase, luego la de mayor criticidad, luego la de mayor score.
- **Parámetros del equipo por variables de entorno del servicio**: umbral global de YOLO, bandas de
  confianza por clase (válida / media / alta, sobre el score crudo) e IoU de solapamiento, con
  valores por defecto provisorios.
- **`modelo_final.pt` como modelo vigente**: `MODEL_PATH` de `.env`, `.env.example` y
  `docker-compose.yml` apunta a él. Las rutas relativas se resuelven contra la carpeta del servicio.
- **Criterio agronómico del diagnóstico** en el backend: bandas de severidad por clase (mínimo,
  media, alta, en cantidad de hojas) y orden de criticidad de los estados anómalos. Recurso propio
  (`GET/PUT /api/configuracion/diagnostico`); el servicio de inferencia lo lee en cada ciclo con su
  cuenta de rol Servicio.
- **Nueva vista Configuración → Criterio de diagnóstico** en el dashboard para editar ese criterio.
- **BREAKING (contrato de `POST /api/diagnosticos`)**: el alta lleva nivel de confianza
  (`Baja`/`Media`/`Alta`), severidad sólo en estados anómalos, score promedio y las detecciones que
  intervinieron. *No concluyente* no lleva severidad, confianza ni score. El simulador se adapta;
  sigue siendo el mismo camino único de alta.
- **Detecciones persistidas** con el diagnóstico y consultables (`GET /api/diagnosticos/{id}/detecciones`).
- **Toggle de cajas en la imagen del diagnóstico**: *Original · Diagnóstico · Todas*. "Todas" pinta
  cada clase y resalta la del estado diagnosticado, de modo que se ve también el estado anómalo que
  perdió (mitiga el costo conocido del diagnóstico único).
- La tarjeta y el detalle muestran **nivel de confianza** en lugar de un porcentaje con aviso de
  actuación: qué se hace con el diagnóstico es del motor, no del diagnóstico.
- **El motor de reglas no cambia.** `SupplyRule` y su parámetro `diagnostico.confianza-minima`
  siguen igual, leyendo el `conf` numérico (ahora, el score promedio × 100). Las reglas que usan el
  diagnóstico se revisan en un cambio posterior.

## Capabilities

### New Capabilities
- `diagnostico-visual`: módulo de diagnóstico del servicio de inferencia — pipeline de detecciones
  a diagnóstico, parámetros del equipo por entorno, resolución de solapes, lectura del criterio
  agronómico y entrega al backend.
- `criterio-diagnostico`: criterio agronómico del diagnóstico (bandas de severidad por clase y orden
  de criticidad): persistencia, API, validaciones y vista Configuración → Criterio de diagnóstico.

### Modified Capabilities
- `diagnosticos-registro`: el alta lleva nivel de confianza, severidad condicionada al estado, score
  promedio y detecciones; "concluyente" pasa a ser "estado distinto de No concluyente"; se agrega la
  consulta de detecciones de un diagnóstico.
- `ai-diagnostics`: el modal de imagen ofrece el toggle *Original · Diagnóstico · Todas* con las cajas
  superpuestas; la tarjeta muestra el nivel de confianza.
- `diagnostico-consulta`: el indicador de confianza pasa de porcentaje con aviso de actuación (85 %)
  a nivel de confianza; la severidad se informa sólo en estados anómalos.

## Impact

- **Servicio de inferencia**: nuevo `src/diagnosis.py` (lógica pura, testeable sin YOLO),
  `src/settings.py` (parámetros del equipo), cambios en `model.py`, `api.py` y `main.py`, tests con
  `pytest`, `.env`, `.env.example`, `README.md`, `docker-compose.yml`.
- **Backend**: `DiagnosticoEntity` (columnas `confidence`, `detections`, `detection_count`; `conf`
  pasa a admitir nulo), `DiagnosticoService`, `DiagnosticoController`, DTOs `Diagnosis`/
  `DiagnosisCard`/`Diagnostico`/`DiagnosticoPaso`, `SectorEntity` (`diagnosis_confidence`), nueva
  entidad, servicio y controller `DiagnosisCriteria*`, `MapaPermisos`, historial (cambio de
  criterio). Script manual `migracion-diagnostico-detecciones.sql` (quita el `NOT NULL` de
  `diagnostico.conf`).
- **Frontend**: tipos de dominio, capa de datos (http y mock), `DiagnosisTile`, `CapturaModal`,
  detalle de sector, nueva vista `/criterio-diagnostico` en el grupo Configuración, `vistas.ts`, tokens
  de color para las cajas (`--det-*`).
- **Simulador**: el formulario de carga manual de diagnóstico manda el nuevo payload.
- **Sin cambios**: motor de reglas (`engine/**`), contrato de cámara (`/api/camara/v1/**`), contrato MQTT.
