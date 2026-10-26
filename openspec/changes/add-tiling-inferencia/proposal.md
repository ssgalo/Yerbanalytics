## Why

Las capturas cenitales del riel (1080×1440) muestran 60–80 hojas, y el modelo se entrenó con
imágenes de 5 a 20 hojas: en la foto entera cada hoja ocupa una fracción del cuadro mucho menor que
la que vio el modelo. Además, la foto incluye el riel y el piso, que no son parte del sector. Hay que
llevar al modelo imágenes parecidas a las del entrenamiento sin contar dos veces la misma hoja,
porque la severidad del diagnóstico sale de la **cantidad** de hojas, y una severidad inflada dispara
reglas bióticas que aplican fitosanitarios.

## What Changes

- El servicio de inferencia **normaliza** cada captura antes de usarla: la orienta según su tag EXIF
  y la convierte a RGB.
- La **recorta** con márgenes configurables en píxeles por variables de entorno (izquierda, arriba,
  derecha, abajo; hoy 278 y 366 a la izquierda y arriba). Si el recorte no entra en la imagen, la
  captura no se diagnostica y el log nombra las variables.
- Divide la imagen recortada en una **grilla de tiles con solapamiento** (3 filas × 2 columnas y
  130 px de fábrica, configurable) y corre el detector sobre cada tile.
- **Une las cajas** de los tiles en coordenadas de la imagen recortada: una caja cortada por el borde
  compartido con un tile vecino se fusiona con la que la cubre (la hoja entera, o el otro pedazo). Los
  duplicados enteros de una hoja que cae dentro de la franja compartida los sigue resolviendo el paso
  de cajas superpuestas del diagnóstico.
- El diagnóstico (`diagnose()`) recibe las cajas ya unidas y **no cambia**.
- El alta de diagnóstico (`POST /api/diagnosticos`) acepta un **recorte opcional** (`crop`) y las
  cajas pasan a estar normalizadas sobre la imagen recortada. El backend lo guarda con el diagnóstico
  y lo devuelve junto a las detecciones. Sin `crop`, todo sigue como hoy (diagnósticos viejos y carga
  manual del simulador).
- El **visor de la captura** del dashboard muestra sólo la región recortada del JPEG original y dibuja
  las cajas sobre ella.
- El JPEG original **no se modifica**: el recorte es una vista de la foto, no una foto nueva.

## Capabilities

### New Capabilities
- `tiling-inferencia`: preprocesado (EXIF, RGB, recorte), división en tiles con solapamiento,
  detección por tile y unión de las cajas en una sola imagen, con sus parámetros del equipo por
  variables de entorno.
- `diagnostico-recorte`: el recorte viaja con el diagnóstico (alta, persistencia y consulta) y el
  visor muestra la región recortada con las cajas encima.

### Modified Capabilities
<!-- Ninguna. Los requisitos del diagnóstico viven en `add-diagnostico-por-detecciones`, que todavía
     no se archivó: este cambio agrega requisitos nuevos en vez de modificar los de un delta abierto. -->

## Impact

- **Servicio de inferencia** (`Desarrollo/servicio-inferencia/`): módulo nuevo `src/tiling.py`
  (geometría pura, sin modelo), `model.py` (detecta sobre una imagen en memoria), `settings.py`
  (variables nuevas y su validación), `main.py` (pipeline), `api.py` (envía `crop`), `env.example` y
  README. Tests nuevos sin checkpoint.
- **Backend**: `POST /api/diagnosticos` y `GET /api/diagnosticos/{id}/detecciones` (campo `crop`
  opcional), `DiagnosticoEntity` (columna nueva, nullable; la crea `ddl-auto=update`, sin migración
  manual). Sin cambios de permisos.
- **Frontend**: `CapturaModal` y `DetectionOverlay` (región recortada), tipos en `domain.ts`, repositorio
  http y mock.
- **Sin cambios**: contrato de cámara (`/api/camara/v1/**`), MQTT, simulador, motor de reglas y
  criterio del agrónomo.
- Depende de `add-diagnostico-por-detecciones` (pipeline de diagnóstico y contrato de detecciones).
