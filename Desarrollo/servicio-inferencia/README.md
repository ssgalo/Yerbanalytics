# Servicio de Inferencia — Yerbanalytics

Daemon en Python que automatiza la asignación de diagnósticos a las capturas del vivero.
Consulta el backend periódicamente, ejecuta el modelo de visión artificial local y
registra el diagnóstico resultante.

## Arquitectura

```
[cada POLLING_INTERVAL_SECONDS]
main.py
  → GET /api/capturas/pendientes-diagnostico   (backend Java)
  → lee JPEG desde CAPTURAS_DIR / fileName
  → model.py: inferencia MobileNetV3-Large (.pt)
  → POST /api/diagnosticos                      (backend Java)
```

El servicio no conoce la topología de sectores: opera solo con IDs de captura.
El backend asigna el diagnóstico al sector internamente vía la relación `captura → sector`.

## Prerrequisitos

| Requisito | Mínimo recomendado |
|---|---|
| Python | 3.10+ |
| RAM | 2 GB (4 GB con modelo grande) |
| GPU (opcional) | CUDA 11.8+ para aceleración |
| Disco | Acceso al mismo volumen de capturas que el backend |

> **Nota de hardware**: Sin GPU, la inferencia corre en CPU. Para un lote grande de
> capturas acumuladas, esto puede ser lento. El intervalo de 4 horas por defecto está
> dimensionado para ritmos normales de captura.

## Configuración

```bash
cp .env.example .env
# Editá .env y completá al menos:
#   MODEL_PATH=/ruta/al/modelo.pt
#   MODEL_CLASSES=Sano,Clorosis,...  (en el orden exacto del entrenamiento)
```

### Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `MODEL_PATH` | — | **Obligatoria.** Ruta absoluta al archivo `.pt`. |
| `MODEL_CLASSES` | — | **Obligatoria.** Orden de clases del modelo, separadas por coma. |
| `BACKEND_URL` | `http://localhost:8080` | URL base del backend. |
| `CAPTURAS_DIR` | `/capturas` | Directorio raíz del volumen de imágenes. |
| `POLLING_INTERVAL_SECONDS` | `14400` (4 h) | Segundos entre ciclos de polling. |

### Mapeo de clases (`MODEL_CLASSES`)

El modelo devuelve un índice de clase (0, 1, 2…). `MODEL_CLASSES` define a qué estado
del backend corresponde cada índice, **en el mismo orden que el entrenamiento**.

Los valores válidos son exactamente los que acepta el backend:
- `Sano`
- `Clorosis`
- `Estrés solar`
- `Daño biótico`
- `No concluyente`
- `Ácaro`
- `Plaga foliar`
- `Daño fúngico`

Ejemplo si el modelo fue entrenado con el orden `[Sano, Clorosis, Estrés solar]`:
```
MODEL_CLASSES=Sano,Clorosis,Estrés solar
```

## Instalación y ejecución local

```bash
cd Desarrollo/servicio-inferencia

# Crear entorno virtual (recomendado)
python -m venv .venv
.venv\Scripts\activate       # Windows
# source .venv/bin/activate  # Linux/macOS

pip install -r requirements.txt
cp .env.example .env
# Editá .env

python src/main.py
```

## Ejecución con Docker Compose

El servicio está declarado en `docker-compose.yml` en la raíz del repositorio.
Solo necesitás setear las variables de entorno del servicio antes de levantar:

```bash
# En docker-compose.yml, sección servicio-inferencia > environment:
#   MODEL_PATH: /modelos/tu_modelo.pt
#   MODEL_CLASSES: Sano,Clorosis,...

docker compose up servicio-inferencia
```

El volumen `capturas-data` es compartido entre el backend y este servicio.
El modelo `.pt` debe montarse externamente (ver `volumes` en `docker-compose.yml`).

## Tolerancia a fallos

| Escenario | Comportamiento |
|---|---|
| Backend no responde al GET | Log error, espera el próximo ciclo |
| Archivo JPEG no existe en disco | Log warning, pasa a la siguiente captura |
| Error durante la inferencia | Log error, pasa a la siguiente captura |
| Backend rechaza el POST | Log error, la captura reaparece en el próximo ciclo |
| Reinicio del proceso | La captura vuelve a aparecer como pendiente y se reprocesa (idempotente) |
