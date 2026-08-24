# design

## Arquitectura del Servicio de Inferencia

El servicio de inferencia operará como un proceso en segundo plano (daemon) escrito en Python. La decisión de mantenerlo separado del backend (Java) permite aislar las dependencias pesadas de Machine Learning (PyTorch) y facilitar su escalamiento o ejecución en máquinas con aceleración de hardware (GPU), sin impactar el servidor web de telemetría.

### 1. Detección de Capturas Pendientes
Para la detección de nuevas imágenes, se descarta el uso de eventos de sistema de archivos (`watchdog`) a favor de un enfoque **pull basado en la base de datos**. Esto centraliza la fuente de verdad y previene problemas de asincronía.
- El servicio consultará periódicamente al backend a un nuevo endpoint del backend: `GET /api/capturas/pendientes-diagnostico`.
- El intervalo por defecto es **14400 segundos (4 horas)** y se puede sobreescribir con la variable de entorno `POLLING_INTERVAL_SECONDS`.
- Este endpoint devolverá las capturas cuya fila no posea una relación establecida en la tabla `diagnostico`.
- La respuesta tendrá el siguiente schema:
```json
[
  { "capturaId": 1234, "fileName": "sector-2026-08-22-1234.jpg" }
]
```
- **Idempotencia**: Si el servicio se interrumpe entre la lectura y el envío del diagnóstico, la captura volverá a aparecer en el próximo ciclo. Dado que la inferencia es determinista para la misma imagen, el reprocesamiento es seguro y se acepta como comportamiento esperado.
- **Autenticación**: No se requiere. El servicio de inferencia y el backend operan en la misma red privada Docker, por lo que el acceso al endpoint no está protegido por credenciales.

### 2. Acceso al Filesystem y Nomenclatura de Archivos
El servicio debe estar desplegado en un entorno que comparta el volumen de almacenamiento de imágenes con el backend (ej. un volumen Docker compartido).
La ruta del archivo es devuelta por el backend, pero convencionalmente la nomenclatura de las imágenes debe garantizar unicidad y trazabilidad. Se recomienda el formato `{sector}-{fecha}-{id}.jpg`.
- **Por qué incluir el ID**: Porque múltiples capturas del mismo sector pueden ocurrir el mismo día. El ID de captura es requerido para el `POST /api/diagnosticos`.
Al recibir el nombre del archivo guardado, el servicio leerá el binario en la ruta particionada configurada (`/capturas/YYYY-MM-DD/{sector}-{fecha}-{id}.jpg`).

### 3. Pipeline de Inferencia
1. **Configuración y Carga del Modelo**: La ruta al archivo del modelo `.pt` no debe estar hardcodeada. Debe ser configurable externamente (ej. mediante archivo `.env` o variables de entorno `MODEL_PATH`), permitiendo usar distintos modelos (ej. de staging o producción) sin tocar el código. Al inicializarse, el script lee esta propiedad y carga el modelo. Se mantiene persistente en memoria para evitar la penalización de carga desde disco en cada imagen.
2. **Preprocesamiento**: La imagen JPEG se carga usando `Pillow` y se aplican las transformaciones de tensor requeridas por el modelo original (normalización, escalado).
3. **Predicción**: El tensor atraviesa el modelo. Se extrae la clase inferida y el *confidence score* probabilístico.

### 4. Publicación del Resultado
El servicio enviará el resultado de la inferencia al backend realizando un llamado HTTP a `POST /api/diagnosticos`.
Payload esperado (siguiendo el contrato existente):
```json
{
  "capturaId": "1234",
  "estado": "CLOROSIS",
  "confianza": 0.89,
  "severidad": "MEDIA"
}
```
> **Nota de diseño**: El servicio de inferencia ignora por completo a qué sector corresponde la imagen. Solo transacciona IDs de captura. Es el backend el que, por diseño interno, asigna este diagnóstico al sector mapeándolo a través de la captura recibida.

### 5. Tolerancia a Fallos
- **Desincronización de Filesystem**: Si el endpoint indica una captura pendiente pero el JPEG no existe en disco, el servicio emitirá una alerta local en logs y omitirá la captura, sin detener el ciclo general.
- **Caída de Red/Backend**: El servicio gestionará excepciones `ConnectionError` en la librería HTTP, reanudando la consulta en el próximo ciclo de polling.
