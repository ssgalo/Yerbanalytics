# proposal

## Why

Actualmente, el sistema recibe órdenes de captura, el dispositivo de cámara sube las imágenes y estas se persisten en el filesystem (con su metadata en base de datos). Sin embargo, la asignación de un diagnóstico para estas capturas se realiza de forma manual simulada. Para cerrar el ciclo de automatización, necesitamos integrar el modelo de inteligencia artificial entrenado (MobileNetV3-Large, provisto como un archivo `.pt`) para que procese automáticamente las nuevas capturas y asigne el diagnóstico correspondiente al sector.

## What Changes

- **Nuevo componente `Desarrollo/servicio-inferencia/`**: Un daemon independiente escrito en Python (aislado del monolito Java).
- **Procesamiento periódico (Polling)**: Este servicio consultará periódicamente al backend por capturas que aún no tengan un diagnóstico asociado, asegurando tolerancia a caídas.
- **Inferencia local**: Utilizará PyTorch para cargar el modelo `.pt`, leerá la imagen directamente desde la carpeta del sistema de archivos local, y ejecutará la predicción.
- **Registro en el Backend**: El servicio reportará el resultado (estado de salud y nivel de confianza) al backend consumiendo el endpoint existente `POST /api/diagnosticos`, vinculándolo mediante el ID de la captura presente en el nombre del archivo.

## Capabilities

### New Capabilities
- `servicio-inferencia`: Módulo daemon en Python capaz de cargar modelos `.pt`, consultar capturas pendientes, preprocesar JPEGs locales y publicar los diagnósticos resultantes en el backend.

### Modified Capabilities
- `captura-persistencia`: El backend ahora expondrá las capturas pendientes de procesamiento a través de un nuevo endpoint de lectura, para que el motor de reglas o agentes externos puedan consumirlas.

## Impact

- **Código nuevo**: Nuevo directorio `Desarrollo/servicio-inferencia/` (código Python, `requirements.txt`).
- **Backend**: Se añadirá un único endpoint de lectura (`GET /api/capturas/pendientes-diagnostico`). El endpoint de alta de diagnósticos (`POST /api/diagnosticos`) NO se modifica, pues el contrato no distingue el origen (manual o automático).
- **Dependencias nuevas**: `torch`, `torchvision`, `requests`, `Pillow` (acotadas únicamente al nuevo proyecto Python).
- **Verificación**: Conformidad del nuevo endpoint en el backend, y validación manual de logs del pipeline en el servicio de inferencia.
