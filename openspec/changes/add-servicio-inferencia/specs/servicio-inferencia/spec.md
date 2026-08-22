# servicio-inferencia

## Purpose

Automatizar la asignación de diagnósticos a las imágenes capturadas mediante la ejecución continua de un modelo de visión artificial local, enlazando el filesystem con la API REST de registro.

## Requirements

### Requirement: Consulta de capturas pendientes por diagnóstico
El servicio SHALL consultar periódicamente al backend para obtener las capturas completadas que aún no tienen un diagnóstico. El backend SHALL exponer un endpoint (`GET /api/capturas/pendientes-diagnostico`) que liste el ID de captura y la ruta de acceso al archivo para cada elemento sin procesar.

El intervalo de polling SHALL tener un valor por defecto de **4 horas** y SHALL ser configurable mediante la variable de entorno `POLLING_INTERVAL_SECONDS` para permitir ajustes sin modificar el código.

#### Scenario: Existen capturas pendientes
- **WHEN** el servicio realiza el polling y el backend registra imágenes subidas sin diagnosticar
- **THEN** el backend responde exitosamente con una lista estructurada que incluye el `capturaId` y el nombre del archivo (`fileName`) de cada captura pendiente, con el siguiente formato:
```json
[
  { "capturaId": 1234, "fileName": "sector-2026-08-22-1234.jpg" }
]
```

### Requirement: Retención del modelo en memoria y configuración de ruta
El servicio SHALL instanciar el archivo `.pt` del modelo una única vez en su ciclo de vida de ejecución inicial, y SHALL mantener el objeto del modelo residente en memoria. La ruta de acceso al archivo `.pt` SHALL ser parametrizable mediante configuración externa (variables de entorno o archivo properties/.env) y NO SHALL estar hardcodeada.

#### Scenario: Inicialización de inferencia
- **WHEN** el demonio de inferencia arranca
- **THEN** lee la ruta del modelo desde su configuración (ej. `MODEL_PATH`), carga el archivo `.pt` correspondiente en PyTorch, y habilita su ciclo de lectura y procesamiento continuo.

### Requirement: Procesamiento de archivos locales y mapeo de estado
Por cada captura identificada como pendiente, el servicio SHALL abrir el archivo JPEG local directamente desde el directorio de capturas, invocar la predicción del modelo y mapear la inferencia probabilística a uno de los estados de salud estandarizados de la plataforma.

#### Scenario: Imagen válida y legible
- **WHEN** el servicio intenta leer una captura localizada y el archivo existe
- **THEN** extrae los features mediante el tensor, evalúa la clasificación dominante y define el nivel de confianza.

#### Scenario: Ausencia de archivo reportado
- **WHEN** la API del backend notifica de una captura pendiente pero el binario correspondiente no está escrito en la ruta esperada del disco
- **THEN** el servicio registra una advertencia en sus bitácoras operativas y aborta el intento sobre esa orden específica, protegiendo el ciclo de ejecución iterativa.

#### Scenario: Reprocesamiento por interrupción
- **WHEN** el servicio se reinicia tras un fallo ocurrido entre la lectura y el envío del diagnóstico
- **THEN** la captura afectada vuelve a aparecer como pendiente en el siguiente ciclo de polling y es reprocesada normalmente, dado que el resultado de la inferencia es determinista para la misma imagen.

### Requirement: Registro de diagnóstico y cierre de orden
El servicio SHALL consumir el endpoint ya definido `POST /api/diagnosticos` para asentar el resultado procesado en el sistema base, refiriendo expresamente el identificador unívoco de la captura analizada sin reportar información topológica (como el sector).

#### Scenario: Inserción de un diagnóstico exitoso
- **WHEN** el servicio logra clasificar una imagen consistentemente
- **THEN** realiza un POST al backend adjuntando el estado de salud, la severidad estimada, la confianza y el ID de captura
- **AND** el backend lo inserta con éxito, por lo que en el próximo polling esa captura ya no figurará en la lista de pendientes.
