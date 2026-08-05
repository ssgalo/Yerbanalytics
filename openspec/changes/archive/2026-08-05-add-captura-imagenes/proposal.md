# Captura de imágenes cenitales con la cámara del iPhone

## Why

La plataforma diagnostica plantines a partir de **imágenes cenitales**, pero hoy no existe
ninguna forma de obtener una imagen real. Toda la vista de Diagnósticos de IA trabaja con
datos inventados: el campo `thumb` de `DiagnosisCard` no es una foto sino un **gradiente
CSS**, y `hasFoto` de `DiagnosisDetail` está clavado. HU-04 CA-01 ("captura una imagen
cenital de los plantines y la asocia directamente a las coordenadas de ese sector y al
timestamp exacto") y HU-05 CA-02 ("despliega la fotografía cenital original sin procesar")
no tienen implementación de ningún tipo.

El prototipo no cuenta con una cámara industrial montada sobre el riel. Sí hay un iPhone,
que tiene una cámara mejor que cualquier módulo que podamos comprar y ya resuelve
autoenfoque, exposición y estabilización. Usarlo como cabezal de captura desbloquea el
pipeline completo **sin comprar hardware**: el riel mueve el teléfono, el backend le pide
la foto, la recibe y la archiva asociada a la posición.

Esto también destraba el trabajo del modelo de IA (`Desarrollo/Modelo_IA/`), que hoy se
entrena con datasets donantes de terceros. Con capturas reales del vivero empieza a existir
un corpus propio del dominio.

**Este cambio deja la imagen adentro del sistema. No implementa el modelo de IA.** El
diagnóstico automático (HU-04 CA-02/03) queda para cuando el modelo exista; hasta entonces
el simulador escribe el diagnóstico a mano **por el mismo camino exacto** que va a usar el
modelo, para poder ver el recorrido completo funcionando de punta a punta.

## What Changes

### Un contrato versionado entre el dispositivo de captura y la plataforma

El iPhone corriendo una PWA es **la primera implementación**, no la definitiva: es probable
que termine siendo una app Android nativa. Entonces lo que se define acá no es "una PWA que
habla con el backend" sino un **contrato de dispositivo de captura**: un namespace versionado
(`/api/camara/v1/**`), agnóstico de plataforma, con sus payloads, sus eventos, sus códigos de
error, sus obligaciones del lado del cliente y una **suite de conformidad** que cualquier
cliente puede correr para demostrar que lo cumple.

El criterio de aceptación del contrato es literal: **escribir la app Android no debe requerir
tocar una línea del backend.** Cumplir el contrato tiene que alcanzar. Por eso el contrato no
asume nada del navegador —el header `Authorization` es la vía canónica, y el token por query
string queda documentado sólo como alternativa para clientes que no pueden fijar headers, que
es una limitación de `EventSource` y no del protocolo.

El contrato vive versionado en `Desarrollo/contratos/camara/v1/` como OpenAPI más su
documento de referencia, siguiendo el precedente de `Desarrollo/embebido/comun/contrato.h`,
que ya es la fuente de verdad del contrato MQTT del ESP32.

### La app de cámara es un proyecto propio

`Desarrollo/camara/`, hermano de `frontend/` y `backend/`, con su `package.json`, su
`tsconfig` y sus dependencias. Se borra entera o se reemplaza por la app Android sin tocar
nada más del repositorio. Es el software del dispositivo de captura, no una vista del
dashboard.

### Canal de órdenes: SSE hacia abajo, REST hacia arriba

Se elige **Server-Sent Events** para el canal descendente de órdenes y **REST sobre HTTPS**
para todo lo ascendente (imagen, acuse, heartbeat, configuración). Es HTTP plano, por lo que
un cliente Android lo implementa igual de bien que un navegador. La comparación completa
contra MQTT-over-WebSockets, WebSocket y polling está en `design.md`.

### Correlación explícita orden ↔ imagen

Cada orden nace con un identificador propio y la posición de riel donde se la va a ejecutar.
La imagen sube citando ese identificador y el backend rechaza cualquier subida que no lo
traiga o que cite una orden ya resuelta. Una orden que no produce imagen dentro de su plazo
la vence un watchdog, que la reintenta hasta un máximo y después la marca fallada. Sin esto
el sistema no sabe de qué sector es la foto que le llegó.

### Las imágenes se guardan en disco, no en la base

El JPEG va a un directorio configurable, particionado por fecha; en Postgres queda la fila
con la ruta, el hash, el tamaño y la correlación. Una captura por sector por ciclo sobre 600
sectores infla la base rápido si se guarda como `bytea`, y el backup de las imágenes tiene
un ciclo de vida distinto al de los datos. Se sirven por un endpoint dedicado.

### Registro y autenticación de dispositivos de captura

El código de un cliente distribuido es inspeccionable, así que no lleva credenciales de larga
duración adentro. El dispositivo se enrola una vez con un **código de vinculación de un solo
uso** que emite el backend, recibe una credencial propia, y a partir de ahí opera con **tokens
de acceso de vida corta** que renueva solo. El backend conoce el estado de cada dispositivo
por heartbeat —sin esperar a que falle una captura para enterarse de que se cayó— y le baja la
resolución y la calidad JPEG por configuración remota, no hardcodeadas en el cliente.

### Un único camino de escritura para los diagnósticos

El modelo de IA es Keras/Python: no va a correr adentro del JVM de Spring Boot, sino como un
proceso aparte en la misma PC. Por lo tanto el diagnóstico entra a la plataforma por HTTP, y
ese endpoint de alta tiene que existir de todos modos.

El simulador escribe **por ese mismo endpoint**, con los mismos campos y las mismas
validaciones. No hay endpoint de simulación, no hay columna que marque el origen del
diagnóstico y no hay modo que habilite o deshabilite el alta. Un diagnóstico cargado a mano
hoy y uno emitido por el modelo mañana son la misma fila, indistinguible, porque **son la
misma operación**. Cuando el modelo exista, se enchufa y nada más cambia.

Consecuencia asumida: no se pueden purgar selectivamente los diagnósticos de prueba, porque
nada los distingue. Es el precio exacto de que el ensayo sea fiel, y se paga a conciencia.

### Panel de cámara en el simulador

Sección nueva en la app de simulación (`:5180`), que sigue siendo una app separada del
dashboard: pedir una captura eligiendo sector y posición de riel, ver llegar la imagen, y
cargar a mano el diagnóstico —estado, severidad y nivel de confianza— que el modelo
devolvería. Al confirmarlo aparece la card nueva en Diagnósticos de IA con esa imagen.

El panel **no tiene ni un endpoint propio**: usa el de emisión de órdenes que va a usar el
planificador y el de alta de diagnóstico que va a usar el modelo. El acoplamiento va en una
sola dirección; borrar el simulador entero no le quita nada al backend ni al dashboard.

### Lo que este cambio NO hace

- No implementa el modelo de IA ni el servicio de inferencia (HU-04 CA-02/03/04/05).
- No mueve el riel ni habla con el gantry: la posición viaja como dato en la orden.
- No agrega un planificador que dispare capturas periódicas. Las órdenes se emiten por API;
  hoy las emite el simulador, mañana el planificador hará la misma llamada.
- No implementa la app Android. Define el contrato que la haría posible sin tocar el backend.
- No modifica el flujo de sensado ni el motor de reglas.

## Capabilities

### New Capabilities

- `camara-contrato`: el contrato de dispositivo de captura — namespace versionado, neutralidad
  de plataforma, definición de payloads y eventos, motivos de fallo tipificados, obligaciones
  exigibles a cualquier cliente conforme, reglas de compatibilidad y suite de conformidad.
- `captura-imagenes`: ciclo de vida de la orden de captura en el backend — emisión con
  posición de riel, entrega al dispositivo, recepción de la imagen, correlación estricta
  orden ↔ imagen, acuses de fallo, vencimiento por watchdog y reintentos.
- `captura-persistencia`: modelo de datos y almacenamiento — tablas de órdenes, capturas,
  dispositivos de cámara y diagnósticos; JPEG en filesystem con metadata en Postgres;
  endpoint de servido de la imagen.
- `camara-dispositivos`: enrolamiento por código de un solo uso, emisión y renovación de
  tokens de vida corta, heartbeat y estado operativo del dispositivo, y configuración remota
  de resolución y calidad.
- `camara-pwa`: la implementación de referencia del contrato sobre Safari iOS — proyecto
  propio, inicialización y sostenimiento del stream, captura por canvas, wake lock,
  recuperación tras perder el primer plano, panel de estado y empaque PWA.
- `diagnosticos-registro`: alta de diagnósticos por un único camino público, siempre vinculada
  a una captura, y su incorporación al snapshot del vivero que consume el dashboard.
- `simulacion-captura`: panel de la app de simulación para pedir capturas de prueba, ver la
  imagen recibida y cargar a mano el diagnóstico, sin endpoints exclusivos.

### Modified Capabilities

- `ai-diagnostics`: la vista de Diagnósticos de IA pasa a mostrar la **imagen real** de la
  captura cuando el diagnóstico tiene una asociada, en la card y en el modal, conservando el
  gradiente actual como respaldo para los diagnósticos sin foto.

> `sector-detail` **no** se modifica: el diagnóstico del detalle de sector sigue saliendo de
> `SectorEntity` y no tiene captura asociada, así que su requisito de captura cenital a
> tamaño completo se cumple igual que hoy. Enganchar el detalle de sector al último
> diagnóstico persistido es trabajo posterior, cuando exista el modelo.

## Impact

### Contrato (`Desarrollo/contratos/`)

- **Nuevo**: directorio de contratos versionados. `camara/v1/openapi.yaml` más su documento de
  referencia. Es la fuente de verdad de la superficie `/api/camara/v1/**`.
- Suite de conformidad ejecutable contra un backend levantado, para validar cualquier cliente
  presente o futuro.

### Backend (`Desarrollo/backend/`)

- **Nuevo**: `CapturaController` (órdenes, subida de imagen, servido), `DispositivoCamaraController`
  (enrolamiento, token, heartbeat, configuración), `DiagnosticoController` (alta pública),
  `CapturaService`, `DiagnosticoService`, `AlmacenamientoImagenService`, watchdog de órdenes.
- **Nuevas entidades**: `OrdenCapturaEntity`, `CapturaEntity`, `DispositivoCamaraEntity`,
  `DiagnosticoEntity` (más sus repositorios).
- **Modificado**: `NurseryService` mezcla los diagnósticos persistidos sobre los generados;
  `DiagnosisCard` y `DiagnosisDetail` suman la URL de imagen. `SimulacionController` **no se
  toca**.
- **Nuevas dependencias**: una librería JWT para los tokens de dispositivo.
- **Nueva configuración**: directorio de imágenes, tamaño máximo de subida, plazo de
  vencimiento de orden, política de reintentos, vida de los tokens.
- **Migración**: tablas nuevas — `ddl-auto=update` las crea; se documenta igual en
  `migracion-manual.sql`.

### App de cámara (`Desarrollo/camara/`)

- **Nuevo proyecto**: Vite + React + TypeScript, con su propio `package.json` y su toolchain.
  Service worker y manifest propios. Reemplazable en bloque por un cliente Android.

### Frontend (`Desarrollo/frontend/`)

- **Simulador**: sección nueva de cámara, sin endpoints propios.
- **Dashboard**: `DiagCard` y `PhotoModal` renderizan la foto real cuando existe.
- **Tipos**: `DiagnosisCard` y `DiagnosisDetail` suman el campo de imagen; tipos nuevos de
  orden, captura y dispositivo. `DataRepository` suma los métodos correspondientes en ambas
  implementaciones (mock y http).

### Entorno

- `getUserMedia` **exige origen seguro**: la PWA y el backend tienen que servirse por HTTPS
  (salvo en `localhost`). Es un prerrequisito de despliegue, no de código; el README
  documenta las dos recetas (certificado local para la LAN del vivero, y túnel con
  certificado válido para demos).
- El dispositivo y el backend tienen que verse en la misma red.

### Historias de usuario

- Cubre **HU-04 CA-01** (captura cenital asociada a sector y timestamp) y habilita
  **HU-05 CA-02** (foto original sin procesar en el detalle del diagnóstico).
- Prepara el terreno para HU-04 CA-02/03, que quedan fuera de alcance.
