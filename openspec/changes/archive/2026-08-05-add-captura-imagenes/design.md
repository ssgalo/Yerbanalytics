## Context

El prototipo necesita imágenes cenitales reales y no tiene cámara industrial. El riel ya
mueve un cabezal; se le monta un iPhone y su cámara pasa a ser el sensor de visión. El
teléfono corre una PWA en Safari que queda en primer plano respondiendo órdenes del backend.

**La PWA es la primera implementación, no la definitiva.** Es probable que el cliente final
sea una app Android nativa. Eso reordena las prioridades del diseño: lo importante no es la
PWA, es el **contrato** que la PWA implementa, y que un cliente Android tiene que poder
implementar sin que el backend cambie ni una línea.

Estado actual relevante del repo:

- El backend (Spring Boot 3.2.4, Java 17, puerto 8000) **no tiene nada de imágenes**. La
  única mención es el campo `hasFoto` de `DiagnosisDetail`, que hoy es un booleano fijo.
- `NurseryService` fabrica `DiagnosisCard` sintéticas a partir de `SectorEntity.diagnosis*`,
  y el campo `thumb` es un **gradiente CSS** (`NurseryConstants.TINTS`), no una imagen.
- No hay autenticación de ningún tipo en el backend. HU-01 (login) está en el backlog sin
  desarrollar, prioridad 3-Baja.
- Ya existe el precedente de contrato versionado: `Desarrollo/embebido/comun/contrato.h` es
  la fuente de verdad del contrato MQTT del ESP32, y `CLAUDE.md` lo señala como tal.
- El frontend ya tiene el patrón de app separada: `simulador.html` +
  `vite.simulador.config.ts` + `dev:sim` sirven el simulador en `:5180`.
- El modelo de IA (`Desarrollo/Modelo_IA/`) es **TensorFlow/Keras**. Todo el prototipo corre
  en la misma PC, pero un modelo Keras no corre dentro del JVM de Spring Boot: va a ser un
  proceso Python aparte.

Restricciones de la plataforma iOS/Safari que condicionan **la implementación de referencia**
—no el contrato—: no existe `ImageCapture`/`takePhoto()`; `getUserMedia` exige origen seguro;
el permiso de cámara exige gesto del usuario; iOS suspende la sesión de cámara al perder el
primer plano; no hay ejecución en background; `Screen Wake Lock` recién existe desde iOS 16.4.

## Goals / Non-Goals

**Goals:**

- **Que escribir la app Android no requiera tocar el backend.** Cumplir el contrato tiene que
  alcanzar, y tiene que poder demostrarse con una suite de conformidad.
- Que el backend pueda pedir una foto y recibirla **sabiendo inequívocamente de qué orden
  vino** y, por lo tanto, en qué posición del riel se tomó.
- Que el backend detecte que una orden no produjo imagen, sin depender de que el
  dispositivo avise.
- Que el dispositivo aguante operación desatendida: cortes de red, suspensión de cámara,
  pantalla apagada, reinicio de la pestaña.
- Que la imagen quede archivada y consultable, y que un diagnóstico apunte a ella.
- **Que el diagnóstico cargado a mano y el que emitirá el modelo sean la misma operación**,
  sin marcas, banderas ni caminos alternativos que existan sólo para probar.

**Non-Goals:**

- El modelo de IA y el servicio de inferencia (HU-04 CA-02 en adelante).
- La app Android. Se define el contrato, no el cliente.
- Control del riel/gantry. La posición es un dato que viaja en la orden.
- Planificador de capturas periódicas. Se emite por API; hoy la llama el simulador.
- Autenticación de usuarios de la plataforma (HU-01/HU-20). La autenticación que se agrega
  acá es **sólo de dispositivos de captura** y vive en su propio espacio.
- Calibración fotométrica del riel y política de retención de imágenes.

## Decisions

### 1. El contrato es el entregable, no la PWA

Se define un **namespace versionado y agnóstico de plataforma**: todo lo que un dispositivo
de captura necesita vive bajo `/api/camara/v1/**` y nada más. Lo que no está ahí no es parte
del contrato y un cliente no debe usarlo.

```
CONTRATO — superficie que un cliente debe implementar
  POST /api/camara/v1/enrolar                     vinculación inicial
  POST /api/camara/v1/token                       renovación de credencial
  GET  /api/camara/v1/config                      configuración de captura
  POST /api/camara/v1/heartbeat                   señal de vida
  GET  /api/camara/v1/ordenes/stream              canal de órdenes (SSE)
  POST /api/camara/v1/ordenes/{ordenId}/imagen    subida de la captura
  POST /api/camara/v1/ordenes/{ordenId}/fallo     acuse de fallo

PLATAFORMA — API interna, fuera del contrato
  POST /api/camara/vinculacion                    generar código (backoffice)
  POST /api/capturas/ordenes                      emitir orden
  GET  /api/capturas/ordenes/{id}                 estado de una orden
  GET  /api/capturas/{capturaId}/imagen           servir la imagen
  POST /api/diagnosticos                          alta de diagnóstico
```

Esta separación hace que "¿qué tiene que implementar la app Android?" tenga una respuesta de
una línea: los siete endpoints de `/api/camara/v1/**`, más las obligaciones de comportamiento
que el contrato le exige.

**Artefacto**: `Desarrollo/contratos/camara/v1/openapi.yaml` más su documento de referencia,
siguiendo el precedente de `contrato.h`. Es la fuente de verdad; el backend se implementa
contra él, no al revés.

**Reglas de compatibilidad**: agregar un campo opcional a una respuesta, un evento nuevo al
stream o un motivo de fallo nuevo son cambios compatibles y no suben la versión. Quitar o
renombrar un campo, cambiar su tipo o su semántica, o volver obligatorio algo opcional exigen
`v2`, con `v1` conviviendo hasta que no queden clientes.

**Suite de conformidad**: un conjunto de pruebas ejecutables contra un backend levantado, que
recorre el ciclo completo —enrolarse, abrir el canal, recibir una orden, subir la imagen,
acusar un fallo, latir— y verifica los códigos de respuesta y las transiciones. Sin esto,
"cumplir el contrato" es una afirmación sin forma de comprobarse; con esto, el día que exista
el cliente Android se corre la suite y se sabe.

### 2. Transporte: SSE hacia el dispositivo, REST hacia el backend

El problema tiene dos flujos con forma distinta: **órdenes** (backend → dispositivo,
mensajes chicos, baja frecuencia, el dispositivo nunca inicia) e **imágenes** (dispositivo →
backend, binario de 1–4 MB). Forzar los dos por el mismo canal perjudica a alguno.

| Opción | A favor | En contra |
|---|---|---|
| **SSE + REST** *(elegida)* | HTTP plano: lo implementa igual un navegador que un cliente Android; reconexión con `Last-Event-ID` definida por el propio protocolo; unidireccional, que es la forma exacta del canal de órdenes; `SseEmitter` de Spring MVC no agrega dependencias; el binario viaja por `multipart`, que es su medio natural | Una conexión HTTP persistente por dispositivo; el cliente debe sostenerla activamente |
| MQTT sobre WebSockets | Reutiliza el broker que ya existe para el ESP32; QoS 1 con persistencia de sesión | Habilitar listener WSS en el broker y resolver su auth; **el JPEG viajaría en base64 (+33%)** por un broker dimensionado para telemetría de ~200 bytes; la subida terminaría siendo REST igual → dos protocolos en el contrato en vez de uno |
| WebSocket propio (STOMP o crudo) | Full-duplex real; permite subir binario por el mismo socket | Reconexión y backoff quedan indefinidos y hay que especificarlos a mano en el contrato; STOMP suma protocolo; **no aporta nada** para un canal que sólo va hacia abajo |
| Long polling | Funciona en cualquier lado | Cada ciclo es una request; los timeouts y el backoff se especifican a mano — peor que la semántica ya definida de SSE |

**Revalidado contra el objetivo Android**: SSE no es una concesión al navegador. En Android se
consume con el soporte de eventos de OkHttp o leyendo la respuesta en streaming, y REST
multipart es trivial. Justamente **por ser HTTP plano** es más portable que MQTT, que
arrastraría broker, credenciales y librería cliente al contrato.

Que el canal descendente sea SSE **no impide** que mañana la orden la dispare un planificador,
el motor de reglas o un mensaje MQTT: todos entran por `POST /api/capturas/ordenes` y
`CapturaService` la empuja al emisor abierto.

Eventos del stream: `orden`, `config` (cambió la configuración remota) y `ping` (keep-alive
cada 20 s, para que proxies y NAT no maten la conexión ociosa).

### 3. Correlación explícita y máquina de estados de la orden

La orden es la entidad de primera clase, no la imagen. Nace con `ordenId` (UUID), el sector,
la macro-zona, la posición de riel y un plazo de vencimiento.

```
                 ┌──────────── reintento (intentos < max) ─────────────┐
                 ↓                                                     │
  [PENDIENTE] ──despacho por SSE──> [ENTREGADA] ──llega imagen──> [RECIBIDA]  (terminal)
                                          │
                                          ├──acuse de fallo del device──> [FALLIDA] ──┤
                                          └──vence el plazo (watchdog)──> [VENCIDA] ──┘
                                                                                      │
                                                          intentos agotados ──> [ERROR] (terminal)
```

- Si no hay dispositivo conectado, la orden **se queda en `PENDIENTE`**. Al abrirse un
  stream, el backend le drena las pendientes. Una orden emitida con el teléfono caído no se
  pierde.
- La subida cita el `ordenId` **en la URL**. El backend rechaza `404` si no existe, `409` si
  ya está `RECIBIDA`, y `403` si el dispositivo del token no es al que se le entregó.
- El `409` devuelve el `capturaId` que ya existe, y el contrato **obliga al cliente a tratarlo
  como éxito**. Así el reintento tras un timeout de red —habiendo llegado la primera subida—
  no genera duplicados ni falsos fallos. Es una regla del contrato, no una astucia de la PWA.
- El watchdog corre en un `@Scheduled`, no en un timer por orden: barre las `ENTREGADA`
  vencidas y las reencola o las manda a `ERROR`.

**Alternativa descartada**: auto-correlacionar por `(sectorId, timestamp)`. Frágil — dos
órdenes seguidas sobre el mismo sector se vuelven indistinguibles, y el reloj del dispositivo
no es autoridad.

### 4. Autenticación: vinculación de un uso → token de vida corta

No hay usuarios en la plataforma, así que esto es un espacio de credenciales propio y acotado
a las rutas de captura. El código de cualquier cliente distribuido es inspeccionable, así que
nada de larga duración se escribe en el fuente.

```
  1. Backoffice/simulador  POST /api/camara/vinculacion
                           → { codigo: "7F3K-2M9Q", expiraEn }     (un solo uso, 10 min)

  2. El operario tipea el código en el dispositivo, una única vez
     device  POST /api/camara/v1/enrolar { codigo, nombre, plataforma }
             → { dispositivoId, refreshToken }        (persistido en el dispositivo)

  3. En cada arranque y antes de cada expiración
     device  POST /api/camara/v1/token { refreshToken }
             → { accessToken, expiraEnSeg }           (JWT ~15 min)
```

**El header `Authorization: Bearer` es la vía canónica y obligatoria** en todo el contrato. El
backend acepta además el token por query string **únicamente** en la ruta del stream, y el
contrato lo documenta como alternativa para clientes que no pueden fijar headers en su canal
de eventos — que es una limitación de `EventSource` en el navegador, no del protocolo. Un
cliente Android usa el header en todas las rutas, incluida la del stream, y no necesita saber
que la alternativa existe.

Riesgo del query param: el token queda en logs de acceso y en el historial del navegador. Se
mitiga con vida corta, alcance mínimo y desactivando el logueo de query strings. Se acepta
sólo porque está confinado a una ruta y a los clientes que no tienen alternativa.

El `refreshToken` es revocable: dar de baja el dispositivo lo invalida y queda afuera en la
siguiente renovación.

Implementación: filtro propio sobre `/api/camara/**` y `/api/capturas/**`, no
`spring-boot-starter-security` completo. Traer Security implicaría configurar toda la cadena
de filtros de una app que hoy no tiene autenticación en ningún otro lado, con riesgo de romper
por defecto los endpoints existentes. Se suma sólo una librería JWT. Cuando llegue HU-01 se
unifica; hasta entonces esto no le impone nada al resto.

### 5. Heartbeat, no inferencia por fallo

`POST /api/camara/v1/heartbeat` cada 15 s con `{estado, capturaListo, capturasOk,
capturasError}`. El backend deriva `OPERATIVO` / `INTERMITENTE` / `FUERA_DE_SERVICIO` por
umbrales de silencio, **espejando la lógica de watchdog que ya usa el hardware**
(`yerbanalytics.hardware.watchdog-*`). Enterarse de que el dispositivo se cayó recién cuando
falla una captura es demasiado tarde: para entonces ya se perdió una pasada del riel.

`GET /api/camara/v1/config` entrega `{anchoMax, altoMax, calidadJpeg, warmupMs, heartbeatSeg,
timeoutOrdenSeg, maxColaOrdenes}`. La resolución y la calidad **no viven en el cliente**: se
cambian en el backend y bajan por el evento `config`. Es un requisito explícito y además es lo
práctico — el dispositivo está montado en un riel.

### 6. Almacenamiento: JPEG en filesystem, metadata en Postgres

```
${yerbanalytics.capturas.dir}/2026/08/04/<capturaId>.jpg
```

En la base queda la fila con `ordenId`, `sectorId`, `zonaId`, `posicionRiel`, `dispositivoId`,
`ancho`, `alto`, `bytes`, `sha256`, `capturadaEn`, `recibidaEn` y `rutaArchivo`. Se sirve por
`GET /api/capturas/{capturaId}/imagen`.

600 sectores × una captura por ciclo × varios ciclos por día son unos cuantos GB por semana.
Como `bytea` eso entra en el `pg_dump`, en la replicación y en la memoria del pool de
conexiones. En disco, el backup de imágenes tiene su propio ciclo de vida y la base queda
chica. El contenido de una captura no cambia nunca, así que se sirve con `Cache-Control:
immutable` y `ETag` = sha256.

Contra conocido: se rompe la atomicidad transaccional entre archivo y fila. Se ordena
escribiendo primero el archivo y después la fila; un archivo huérfano es basura recolectable,
una fila apuntando a un archivo inexistente sería un `404` a la vista. El `sha256` que declara
el cliente se verifica contra los bytes recibidos: una imagen corrupta en tránsito se rechaza
con `422` y la orden se reencola.

### 7. Un único camino de escritura para los diagnósticos, sin marca de origen

El modelo es Keras/Python y no corre dentro del JVM. Aunque todo el prototipo viva en la misma
PC, el diagnóstico va a entrar a la plataforma **por HTTP**, desde un proceso de inferencia
separado. Ese endpoint de alta tiene que existir de todas formas:

```
  producción:  captura recibida → servicio de inferencia → POST /api/diagnosticos
  hoy:         captura recibida → operario en el simulador → POST /api/diagnosticos
```

Es literalmente la misma llamada, con los mismos campos y las mismas validaciones. Por eso:

- **No hay columna `origen`.** Una marca que sólo sirviera para distinguir el ensayo de la
  operación real haría que el ensayo dejara de ser fiel, y quedaría para siempre en el esquema
  como residuo de una etapa de pruebas.
- **No hay endpoint `/api/simulacion/diagnosticos`.** `SimulacionController` no se toca.
- **No hay compuerta por modo de operación.** El alta de diagnóstico no depende del modo
  estático/simulación, porque el modelo tampoco va a depender de él.

Tabla `diagnostico`: `id`, `sector_id`, `zona_id`, `captura_id` **NOT NULL**, `estado`, `conf`,
`sev`, `creado_en`. La captura es obligatoria: todo diagnóstico nace de una imagen, tanto el
del modelo como el cargado a mano. Esa restricción es lo que hace que el camino no se pueda
falsear ni por accidente.

`NurseryService` **antepone** los diagnósticos persistidos a las `DiagnosisCard` que ya genera
y los cuenta en `stats.diagCount`. Los persistidos traen `imagenUrl`; los derivados de
sectores lo traen vacío. El ID de un diagnóstico persistido es el suyo propio, con prefijo
distinto al `DG-###` sintético, para que `diagById` no colisione.

**Trade-off aceptado**: sin marca de origen no se pueden purgar selectivamente los
diagnósticos de prueba. La purga posible es por rango de fechas o por sector, que alcanza
porque cada diagnóstico está anclado a una captura fechada. Es el precio exacto de que el
ensayo sea fiel, y se paga a conciencia.

**Alternativa descartada**: pisar `SectorEntity.diagnosis_estado/conf/sev`. No guarda
historial, no puede vincular la imagen, un sector sólo podría tener un diagnóstico, y habría
que rehacerlo entero cuando llegue el modelo.

### 8. La app de cámara es un proyecto propio

`Desarrollo/camara/`, hermano de `frontend/` y `backend/`, con su `package.json`, su
`tsconfig`, su ESLint y sus dependencias. No comparte build ni toolchain con el frontend.

El costo es duplicar configuración de tooling. La contrapartida es lo que se busca: el
dispositivo de captura es una unidad reemplazable en bloque. El día que exista la app Android,
`Desarrollo/camara/` se borra o se archiva y el resto del repositorio no se entera. Si en
cambio fuera un entry point más del proyecto frontend, esa sustitución dejaría restos en
`package.json`, en la config de Vite y en los scripts.

Los design tokens que se necesiten se copian; son cuatro variables CSS y el panel de estado no
tiene que parecerse al dashboard.

### 9. Sostener el stream, no reabrirlo *(implementación de referencia)*

`getUserMedia({video: {facingMode: 'environment'}})` una sola vez, tras el gesto del usuario
en el botón "Iniciar", y el stream queda abierto. Reabrirlo en cada disparo agrega segundos
de latencia por captura y es la razón habitual por la que estos sistemas terminan sin usarse.

- **Warm-up**: se descartan los frames de los primeros ~500 ms (configurable desde el backend)
  antes de declarar la cámara lista. El primer frame del sensor viene con exposición sin
  converger.
- **Captura**: `ctx.drawImage(video, …)` sobre un `<canvas>` dimensionado según la config
  remota, y `canvas.toBlob(cb, 'image/jpeg', calidad)`. Nada de `takePhoto()`: no existe en
  Safari.
- **`<video>`** con `playsinline`, `muted` y `autoplay`. Sin `playsinline`, iOS lo abre en
  pantalla completa y rompe la interfaz.
- **`applyConstraints`** para `exposureMode` y `whiteBalanceMode` en modo *best effort*
  envuelto en `try/catch`: Safari los soporta muy parcialmente. Se **loguea en pantalla y se
  reporta al backend** el resultado de `getCapabilities()` y `getSettings()` reales, porque
  saber qué se pudo fijar de verdad es lo que permite evaluar si la consistencia entre
  imágenes alcanza. Un fallo acá nunca aborta la captura.
- **`visibilitychange` → `visible`**: se verifica `videoTrack.readyState === 'live'` y, si no
  lo está, se rehace `getUserMedia` + warm-up y se reabre el stream de órdenes. **Nunca se
  asume que el stream sobrevivió**; iOS lo suspende al perder el primer plano.
- **Wake lock**: `navigator.wakeLock.request('screen')`, re-solicitado al volver a visible
  (el lock se pierde solo), con detección de soporte y degradación silenciosa — la API existe
  recién desde iOS 16.4.

Nada de esto está en el contrato: son las obligaciones de "estar listo para capturar" y
"reportar el resultado" resueltas con los medios que da el navegador. Un cliente Android
cumple las mismas obligaciones con CameraX y un foreground service.

### 10. Dos colas distintas, con propósitos distintos

**Cola de órdenes (memoria, FIFO)**: una orden que llega con otra captura en curso se encola
en lugar de descartarse. Tope configurable (`maxColaOrdenes`, default 20); si se llena, se
descarta la más vieja y **se la acusa al backend como fallo** con motivo `COLA_LLENA` para que
la reencole en vez de que desaparezca en silencio. Esto es obligación del contrato.

**Cola de subida (persistente)**: la subida reintenta con espera exponencial con jitter (1s,
2s, 4s, 8s, 16s, tope 30s). Agotados los reintentos inmediatos, la imagen se persiste y se
drena al recuperar conectividad. El contrato exige *que exista* un almacén persistente
acotado; **no exige cuál**. La PWA usa IndexedDB —`localStorage` no sirve para blobs— con tope
de ~50 imágenes / 200 MB, porque el storage de Safari es limitado y puede evictarse. Un
cliente Android usaría el filesystem de la app. Se pide `navigator.storage.persist()` para
reducir la chance de evicción.

Las dos colas se sobreviven mutuamente: aunque el dispositivo pierda todo lo pendiente, el
backend vence la orden por watchdog y la reintenta. La cola local optimiza, no es la garantía.

### 11. Service worker: shell sí, API jamás *(implementación de referencia)*

`manifest.json` con `display: standalone`, orientación fijada e íconos. El service worker
cachea **solamente** el shell (HTML, JS, CSS, íconos) con estrategia cache-first, y deja pasar
directo (network-only, sin interceptar) todo `/api/**` y toda imagen. Un service worker que
cachee una orden de captura o una subida es una fuente de bugs silenciosos: serviría una orden
vieja o daría por subida una imagen que nunca salió. La regla se escribe como exclusión
explícita por path, y el SW no intercepta `POST` bajo ninguna circunstancia.

### 12. El simulador no tiene endpoints propios

El panel de cámara del simulador usa `POST /api/capturas/ordenes` —el mismo que usará el
planificador— y `POST /api/diagnosticos` —el mismo que usará el servicio de inferencia—, y
consulta el estado de la orden por polling. Superficie exclusiva del simulador: **ninguna**.

La dependencia va en una sola dirección. `CapturaService` y `DiagnosticoService` no saben que
el simulador existe. Borrar `simulador.html` entero no le quita nada al backend ni al
dashboard.

### 13. HTTPS como prerrequisito de entorno

`getUserMedia` exige origen seguro; sin HTTPS no hay cámara, salvo en `localhost`. El código
no fija ninguna receta: la config de Vite de la app de cámara levanta en HTTPS si encuentra
`CAMARA_HTTPS_KEY`/`CAMARA_HTTPS_CERT`, y en HTTP si no. El README documenta las dos vías:
certificado local con `mkcert` para la LAN del vivero (el camino del piloto, sin depender de
internet), y túnel con certificado válido para demos. El backend expone `server.ssl.*` por
perfil.

## Risks / Trade-offs

- **El contrato podría no alcanzar y la app Android terminar exigiendo cambios en el backend**
  → Es el riesgo central de este diseño. Se ataca con la suite de conformidad: si la suite
  cubre el ciclo completo y un cliente la pasa, funciona. Se ataca también manteniendo el
  contrato en HTTP plano, sin nada específico del navegador en su superficie obligatoria.

- **Sin marca de origen no se distinguen los diagnósticos de prueba de los reales** →
  Aceptado a pedido explícito, y es lo correcto: la marca volvería infiel el ensayo. La purga
  por fecha o por sector alcanza, porque todo diagnóstico está anclado a una captura fechada.

- **iOS mata la pestaña en background y el sistema deja de responder** → No hay forma de
  evitarlo desde el navegador; se ataca por tres lados: wake lock, un indicador de estado
  imposible de ignorar, y el backend detectando la ausencia por heartbeat en vez de por
  captura fallida. Las órdenes quedan `PENDIENTE` y se drenan al reconectar. **Es, además, la
  razón más fuerte para que el cliente final sea Android**, donde un foreground service sí
  puede sostener la operación.

- **La consistencia fotométrica entre capturas puede no alcanzar para entrenar** → Safari
  soporta `applyConstraints` de forma muy parcial y el iPhone va a autoexponer. Por eso se
  loguea y se reporta qué constraints se aplicaron **realmente**: es la evidencia para decidir
  con datos. Si no alcanza, la salida es iluminación controlada en el riel, que es hardware.

- **Token de SSE en la query string** → Confinado a una ruta y a clientes sin alternativa,
  vida de 15 min, alcance mínimo, HTTPS obligatorio y logueo de query strings desactivado.

- **Duplicación de toolchain por el proyecto de cámara separado** → Aceptado: es el costo de
  que el dispositivo sea reemplazable en bloque, que es exactamente el objetivo.

- **Almacén persistente evictado por el sistema** → El contrato exige que exista y esté
  acotado, no que sea infalible. La garantía real es el watchdog del backend.

- **Archivo y fila de base no son atómicos** → Se escribe archivo primero, fila después; el
  huérfano posible es basura recolectable y no un `404` visible. Se verifica `sha256`.

- **Una dependencia JWT nueva y un filtro de auth en un backend que hoy no tiene ninguno** →
  El filtro se acota por path; el resto de la API queda exactamente como está. Cuando llegue
  HU-01 habrá que unificar, y se sabe de antemano.

- **`ddl-auto=update` no migra ni borra** → Acá sólo se crean tablas nuevas. Igual se deja el
  DDL en `migracion-manual.sql`, siguiendo lo hecho con el cambio de sensado por macro-zona.

## Migration Plan

1. Escribir el contrato **primero** (`Desarrollo/contratos/camara/v1/`) e implementar el
   backend contra él. Es la única secuencia que garantiza que el contrato describa el sistema
   y no que lo documente a posteriori.
2. Tablas nuevas (`orden_captura`, `captura`, `dispositivo_camara`, `diagnostico`). No tocan
   ninguna existente: `ddl-auto=update` las crea sola. DDL espejado en `migracion-manual.sql`.
3. El directorio de imágenes se crea al arrancar si no existe. Si no es escribible, la app
   falla temprano y con mensaje claro, no en la primera subida.
4. La app de cámara se despliega por HTTPS y se enrola con un código de vinculación. Sin
   dispositivo enrolado, todo lo demás sigue funcionando: las órdenes quedan pendientes.
5. **Rollback**: el cambio es puramente aditivo. Apagar la app de cámara deja el sistema como
   estaba; los diagnósticos se borran con un `DELETE` por rango de fechas y las cards
   sintéticas vuelven a ser lo único que muestra la vista.

## Open Questions

- **Cadencia real del riel**: cuántas capturas por hora y cuánto tarda una pasada completa.
  Define si el plazo de vencimiento de orden (default propuesto: 60 s) y el tope de la cola
  son razonables. Se resuelve midiendo con el prototipo.
- **Cómo se dispara la inferencia**: cuando exista el servicio Python, hay que decidir si el
  backend le notifica cada captura o si el servicio consulta las capturas sin diagnóstico. No
  afecta a este cambio —el alta de diagnóstico ya está definida— pero es la próxima decisión.
- **Retención de imágenes**: cuánto tiempo se guardan y qué se purga. Fuera de alcance, pero
  el particionado por fecha deja la purga por antigüedad lista para escribir.
- **Un dispositivo o varios**: hoy se asume uno. El modelo de datos soporta N (cada orden
  puede dirigirse a un `dispositivoId`), pero no hay política de reparto entre varios.
