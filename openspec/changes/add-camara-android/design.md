## Context

El contrato `camara/v1` es neutral de plataforma por diseño y su criterio de aceptación es que un
cliente nuevo no obligue a tocar el backend. La PWA de `Desarrollo/camara/` es la implementación de
referencia; este cambio agrega la **segunda** implementación, nativa, sobre la plataforma para la
que el contrato fue previsto.

Lo que cambia respecto de la PWA no es el protocolo sino lo que la plataforma permite:

| Restricción de iOS/Safari | Qué permite Android |
|---|---|
| No hay ejecución en segundo plano | Foreground service: canal, colas y heartbeat con la pantalla apagada |
| iOS suspende la cámara al perder el primer plano | El servicio conserva el acceso a cámara mientras vive |
| `getUserMedia` exige origen seguro → CA local + TLS | Un cliente nativo no tiene esa restricción |
| No existe `ImageCapture`/`takePhoto()`: hay que dibujar en un canvas | CameraX usa el pipeline de fotografía fija |
| Reabrir el stream cuesta segundos → hay que sostenerlo prendido | Abrir la cámara bajo demanda es viable |
| No hay forma de saber si la exposición convergió: se descartan fotogramas por tiempo | CameraX avisa cuando 3A convergió |
| Sin filesystem: todo lo que no entra en memoria o IndexedDB se pierde | Archivos y colas persistentes son triviales |

Las dos últimas filas importan tanto como las primeras, y son las que este diseño estuvo a punto
de desaprovechar por copiar la forma de la PWA. Están señaladas en D3, D7 y D11.

Restricciones del entorno del piloto: teléfono Android montado en el riel, misma WiFi que la PC del
backend, sin Play Store (la APK se instala de costado), sin IP fija garantizada.

## Goals / Non-Goals

**Goals:**

- Cliente conforme al contrato `camara/v1`, sin usar un solo endpoint fuera de ese namespace.
- Operar con el teléfono bloqueado y la pantalla apagada, indefinidamente.
- Cámara apagada en reposo: se abre por captura y se cierra al terminar.
- UI mínima: log de eventos + visor sólo durante la captura.
- APK generable con un comando y con el trámite de instalación documentado punta a punta.
- Cero cambios en el backend.

**Non-Goals:**

- Dar de baja la PWA. Conviven; la baja se decide después.
- Control del riel, planificación de pasadas o inferencia del modelo — nada de eso es del
  dispositivo.
- Publicación en Play Store, firma de release, actualización automática (OTA).
- Descubrimiento automático del backend en la red (mDNS). La URL se tipea una vez.
- Tests instrumentados sobre dispositivo. Se cubre con tests JVM la lógica que no depende de
  Android.

## Decisions

### D1 — Kotlin + Jetpack Compose, minSdk 26 / target 35

Compose porque la UI es una lista de log y un visor: declararla cuesta menos que mantener XML.
minSdk 26 cubre cualquier teléfono razonable; target 35 obliga a respetar el régimen moderno de
foreground services, que es justamente de lo que depende el cambio.

*Alternativas:* Flutter o React Native reusarían algo del ecosistema JS del repo, pero el foreground
service y CameraX terminan escribiéndose igual en Kotlin, con un puente de por medio. No aporta.

### D2 — Un único foreground service es el dispositivo; la Activity es sólo una ventana

Todo el estado operativo —credenciales, canal SSE, cola de órdenes, cola de envío, heartbeat,
captura— vive en un `LifecycleService`. La Activity se ata a él para mostrar el log y el visor, y
puede morir sin que el dispositivo deje de operar. Es la inversión exacta respecto de la PWA, donde
la pestaña visible *era* el dispositivo.

Tipo declarado: `camera|specialUse`.

- `camera` es lo que habilita usar la cámara con la app fuera de primer plano.
- `specialUse` porque el servicio también sostiene una conexión de red permanente, y los tipos
  pensados para eso (`dataSync`) tienen tope de horas por día en Android 15 — inservible para algo
  que debe estar 24/7.

**El tipo se declara al arrancar el servicio desde la Activity, con la app visible.** Ese es el
momento en que el sistema concede el acceso a cámara "en uso"; una vez concedido, el servicio lo
conserva mientras viva, aunque la pantalla se apague. Declarar el tipo `camera` **no** abre la
cámara: el hardware sigue apagado hasta que llega una orden (ver D3).

*Alternativa:* `WorkManager` periódico. Descartado: no sostiene una conexión SSE ni da acceso a
cámara, y su cadencia mínima (15 min) no tiene relación con la latencia que pide una pasada de riel.

### D3 — Standby por ráfaga: la cámara se abre ante la primera orden y se cierra tras el silencio

En reposo no hay ningún caso de uso de CameraX atado: el hardware está apagado y el indicador de
cámara en uso del sistema, apagado también. Ante la primera orden se abre, y **se mantiene abierta
mientras siga llegando trabajo**; se cierra cuando pasa una ventana de inactividad configurable
(orden de 10–15 s, por debajo del `timeoutOrdenSeg` del backend).

```
orden ──┬─ cámara cerrada → bindToLifecycle(ImageCapture [+ Preview si la Activity es visible])
        └─ cámara abierta → reutiliza la sesión
   → esperar convergencia de 3A (tope: warmupMs)
   → takePicture() a memoria
   → sha256 + subida
   → rearmar el temporizador de inactividad
        └─ vencido sin órdenes → unbindAll(), el hardware queda libre
```

Esto **no** es "sostener el stream como la PWA". Es reconocer que una pasada de riel son 600
órdenes seguidas, y que abrir y cerrar la cámara 600 veces tiene dos costos: la latencia de
inicialización por foto, y —más grave— que **cada disparo reinicia 3A desde cero**, que es
justamente lo que más ensucia la consistencia fotométrica entre imágenes de la misma pasada. Con la
sesión sostenida durante la ráfaga, la exposición y el balance ya convergidos se conservan entre
sectores contiguos.

El requisito que pidió el usuario se cumple igual: entre pasadas —que es donde el teléfono pasa la
mayor parte del día— la cámara está apagada.

**El calentamiento es por convergencia, no por reloj.** `warmupMs` existe en el contrato porque en
un canvas no hay forma de saber si la exposición convergió y hay que descartar fotogramas por
tiempo. CameraX sí lo sabe: se dispara una acción de medición y se espera su resultado, usando
`warmupMs` como **techo** y no como espera fija. Sale una foto mejor y normalmente más rápido. Y en
la segunda captura de una ráfaga la convergencia ya está hecha, así que el costo desaparece.

Se ata **`ImageCapture` sin `Preview`** cuando la Activity no está visible: CameraX no exige una
superficie de dibujo para capturar, y con la pantalla apagada no habría dónde dibujarla. Cuando la
Activity sí está visible se ata también `Preview`, y eso —y sólo eso— es lo que hace aparecer el
visor durante la captura.

*Alternativa descartada:* cámara abierta permanentemente (lo que hace la PWA, porque reabrir un
stream de navegador cuesta segundos). Consume batería todo el día y mantiene el indicador de cámara
encendido sin motivo.

*Alternativa descartada:* abrir y cerrar por foto. Era el diseño anterior de este documento; es
herencia de razonar en los términos binarios de la PWA y paga el peor precio justo durante la
pasada, que es cuando importa.

### D4 — Sin TLS ni CA: HTTP contra el `:8000`, con `https://…:8443` disponible sin recompilar

La CA local y el conector 8443 existen porque **el navegador** los exige: `getUserMedia` sólo corre
en origen seguro y una página HTTPS no puede llamar a un endpoint HTTP. Una app nativa no tiene
ninguna de las dos restricciones, y el backend ya sirve el contrato completo por el `:8000`. CORS
tampoco entra en juego: un cliente nativo no manda `Origin`.

Esto **es un desvío del §8 del contrato**, que pide HTTPS igual, y el motivo que da es real: sin TLS
el `accessToken` viaja en claro por la WiFi. Se asume a conciencia — red privada de un piloto, token
de 15 minutos, alcance acotado — y no se esconde: queda escrito en el README de la app.

Implementación: `network_security_config` con `cleartextTrafficPermitted="true"` y anclas de
confianza `system` + `user`. La consecuencia práctica es que **la misma APK sirve para los dos
modos**: si algún día se quiere TLS, se instala `ca.pem` en Android (Ajustes → Seguridad →
Cifrado y credenciales → Instalar certificado → Certificado de CA) y se tipea `https://<ip>:8443`
al vincular. Sin rebuild y sin tocar código.

*Alternativa:* empaquetar `ca.pem` en los assets. Descartado: ata el APK a una CA que se regenera
cada vez que cambia la IP de la máquina.

### D5 — Autenticación por header en todos los endpoints, incluido el stream

El contrato admite `?token=` en `/ordenes/stream` como concesión a `EventSource`, y aclara que un
cliente que puede fijar headers debe usar el header. OkHttp puede, así que la query string no se usa
nunca. Ventaja lateral: el token no queda en los access logs del backend.

Renovación proactiva a los ~2/3 de `expiraEnSeg`. Ante un `401`: renovar y reintentar **una vez**;
si la renovación también falla, descartar la credencial y volver a la pantalla de vinculación.

### D6 — Canal SSE con OkHttp y watchdog de `ping`

`okhttp-sse` con `readTimeout = 0` sobre el cliente del stream (y sólo sobre él). Reconexión con
espera creciente y jitter, con tope. Un watchdog independiente reconecta si pasan más de ~3×
`heartbeatSeg` sin ningún evento: el contrato advierte que la ausencia de `ping` es la señal de
canal muerto, porque un socket colgado no da error.

Se ignoran los eventos y los campos desconocidos sin fallar (§5.6 del contrato).

**Por qué SSE sigue siendo lo correcto en un cliente nativo.** El canal se eligió con la PWA en
mente, así que corresponde revisarlo sin ella:

| Alternativa | Por qué no |
|---|---|
| Polling | Peor latencia, más tráfico y más batería que una conexión abierta. Estrictamente inferior. |
| WebSocket | Bidireccional, y por este canal no fluye nada del dispositivo al backend. Se paga handshake y framing por una dirección que no se usa. |
| FCM (push de Google) | Exige Play Services **y salida a internet**. Un vivero con la conexión caída dejaría de capturar aunque el teléfono y el backend estén en la misma WiFi. Entrega best-effort y diferible. Descalificado por el entorno. |
| MQTT | La única con argumentos: el broker ya existe, QoS 1 y Last Will darían presencia instantánea y harían innecesario el heartbeat. Pero obliga al backend a **publicar**, y hoy sólo consume (invariante de CLAUDE.md §6); suma una dependencia del broker al pipeline de captura y una superficie de autenticación nueva donde el JWT del contrato no aplica. Y las imágenes seguirían yendo por REST, así que ni siquiera se queda un solo protocolo. |

La conclusión no depende de la restricción de no tocar el backend: **MQTT se descartaría igual
teniendo permiso para cambiarlo.**

**Sobre la redundancia del heartbeat, y por qué la cadencia se deja como está.** El heartbeat
duplica en parte lo que el propio stream ya prueba: si la conexión está abierta, el dispositivo
está vivo. Lo que aporta de verdad es lo que la conexión no puede decir —"estoy conectado pero mi
cámara está rota"— y los contadores.

Se evaluó subir `heartbeat-seg` de 15 a 60 para ahorrar batería, y **se descartó**. En un móvil lo
caro de un heartbeat no son los bytes sino sacar la radio del estado ocioso, y el backend ya emite
un `ping` por el stream cada 20 s (`sse-keep-alive-ms`) porque sin él los NAT intermedios cierran
la conexión: la radio ya está despierta a esa cadencia. El `POST` viaja además sobre una conexión
que el cliente ya tiene abierta contra el mismo host. El ahorro es marginal.

El precio, en cambio, es concreto: los watchdogs son umbrales absolutos, no múltiplos de la
cadencia. Con `heartbeat-seg=15` y `watchdog-intermitente-ms=60000` el backend detecta una cámara
caída en menos de un minuto, con un margen de cuatro latidos. Subir la cadencia obligaría a llevar
el umbral a ~180 s y a enterarse de la caída tres minutos tarde — justo lo que el heartbeat existe
para evitar en un sistema donde el riel se posiciona antes de pedir la foto.

Conclusión: **no se toca ninguna propiedad del backend.** La cadencia actual es la correcta para un
cliente nativo, no sólo la heredada.

### D7 — Dos colas, y **las dos** en disco

- **Órdenes (disco, acotada a `maxColaOrdenes`).** FIFO. Si llega una orden con una captura en
  curso, se encola. Al llegar al tope se descarta y **se acusa `COLA_LLENA`**; nunca en silencio.
- **Envío (disco).** El JPEG va a `filesDir/pendientes/<ordenId>.jpg` con un sidecar de metadata.
  Reintentos con espera creciente; agotados, queda en disco y se drena al volver la red. Acotada a
  50 imágenes / 200 MB, descartando lo más viejo.

Ambas sobreviven al cierre de la app y al reinicio del teléfono, que es lo que la PWA no podía
prometer.

**Por qué la de órdenes también va a disco.** La versión anterior de este documento la dejaba en
memoria argumentando que el backend vence y reintenta. Es cierto pero es una racionalización
heredada: en una pestaña de navegador persistir es incómodo, en Android es una línea. Y el costo de
no hacerlo no es cero — una orden ya **entregada** por el stream no se le vuelve a drenar a la app
al reconectar, así que si el proceso muere con ocho órdenes entregadas y sin ejecutar, esas ocho
esperan su vencimiento de a una y recién ahí se reintentan. En medio de una pasada de 600 sectores
eso es una demora perfectamente evitable.

La cola local sigue siendo una **optimización, no la garantía**: aunque el teléfono pierda todo, el
backend vence la orden y la reintenta.

**`409` es éxito**: se borra el pendiente y no cuenta como fallo. Sin esa regla, un corte de red en
el momento justo produce duplicados o falsos errores.

### D8 — Credenciales en DataStore, nada en el código fuente

`refreshToken`, `dispositivoId` y URL base en DataStore (Preferences). El `accessToken` sólo en
memoria: dura 15 minutos y no tiene sentido persistirlo. El código fuente no lleva ninguna
credencial de larga duración — el APK es inspeccionable.

### D9 — Arranque tras reinicio: degradar antes que mentir

Un receptor de `BOOT_COMPLETED` levanta el servicio para que el dispositivo vuelva solo después de
un corte de luz. Pero el régimen de Android puede negar el acceso a cámara a un servicio arrancado
sin la app visible (D2). Entonces:

- Si el servicio consigue el tipo `camera`, opera normal.
- Si no, opera **degradado**: sostiene el canal y el heartbeat reportando `capturaListo: false`,
  acusa `CAMARA_NO_LISTA` a las órdenes que lleguen, y muestra en su notificación que hace falta
  abrir la app una vez. Al primer arranque de la Activity, se promueve a modo normal.

Nunca reporta que puede capturar si no puede: el backend deriva el estado del dispositivo de ese
campo.

### D10 — Batería: exención de optimización, documentada como paso obligatorio

Un foreground service no lo mata el sistema, pero Doze puede cortarle la red al teléfono quieto —
y un teléfono montado en un riel está siempre quieto. La app pide la exención de optimización de
batería (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) y el README la trata como un paso del setup, no
como una sugerencia. Se suma un `WakeLock` parcial mientras dura una captura y su envío.

### D11 — UI: log, visor efímero y nada más

Una sola pantalla. Arriba, estado de conexión y contadores; en el medio, el visor **sólo** mientras
hay una captura en curso; abajo, el log desplazable. La pantalla de vinculación se ve una vez en la
vida del dispositivo.

El log en pantalla no es un lujo: depurar con las devtools enchufadas a un teléfono montado en un
riel no es practicable, y ese fue el motivo original en la PWA.

**Pero el log vive en un archivo, no sólo en memoria.** Un buffer de ~500 líneas es lo que se puede
hacer en una pestaña, no lo que corresponde a un equipo de producción: cuando algo falla a las
cuatro de la mañana en medio de una pasada, lo que se necesita es el historial, y a esa altura el
buffer ya se dio vuelta varias veces. Se escribe un archivo rotativo acotado (p. ej. 5 archivos ×
1 MB) en el almacenamiento de la app, bajable por `adb pull` o exportable desde la propia pantalla.
En memoria queda sólo la ventana que se muestra.

Sin botón de captura manual en el flujo normal; el disparo de prueba sale del simulador, que es
quien emite las órdenes. (Se deja un disparo local de prueba accesible desde el propio log para
verificar la cámara sin backend.)

### D12 — Resolución y calidad: del backend, siempre

`ResolutionSelector` con la resolución de `/config` como estrategia preferida y degradación a la
más cercana; `setJpegQuality` con `calidadJpeg × 100`. El evento `config` se aplica en la captura
siguiente sin reiniciar nada.

Ni la resolución ni la calidad se pueden cambiar sobre una sesión viva: la resolución es parte del
modo de captura, y la calidad JPEG se fija al construir el caso de uso `ImageCapture` (CameraX no
expone un setter en caliente). Un cambio de cualquiera de las dos **marca la sesión como obsoleta**
y la próxima orden la vuelve a atar con los valores nuevos (D3).

> Una versión anterior de este documento decía que la calidad se aplicaba en caliente. Lo corrigió
> la implementación: no existe esa API.

Como la sesión se rearma sola en la orden siguiente, el efecto práctico es el mismo que se buscaba:
no hace falta reapertura explícita, ni reiniciar la app, ni tocar el teléfono — que era la
complicación propia de la PWA.

La metadata declara el `ancho`/`alto` **reales** del JPEG adjunto y el `sha256` de sus bytes.

## Risks / Trade-offs

- **El régimen de foreground services de Android cambia versión a versión y el acceso a cámara
  desde segundo plano es el punto más sensible.** → El tipo se adquiere con la app visible (D2), y
  hay modo degradado explícito cuando no se puede (D9). El primer paso de la implementación es
  verificarlo en el teléfono real, antes de construir el resto encima.
- **Un fabricante agresivo (Xiaomi, Huawei, Oppo) puede matar el servicio igual, exención o no.** →
  El README documenta los ajustes por fabricante; y si el servicio muere, el backend vence la orden
  y la reintenta: se pierde una pasada, no el sistema.
- **Sin TLS el token viaja en claro (D4).** → Red privada, token de 15 min, riesgo escrito en el
  README. Migrar a TLS no requiere rebuild.
- **Abrir la cámara agrega latencia en la primera orden de una ráfaga y puede fallar si otra app la
  tomó.** → Espera por convergencia de 3A acotada por `warmupMs` (D3), y `CAMARA_NO_LISTA` como
  acuse cuando `bindToLifecycle` falla.
- **La ventana de inactividad de la cámara es un número inventado.** Muy corta, se pierde el
  beneficio de la ráfaga; muy larga, la cámara queda prendida entre pasadas. → Arranca en 10–15 s y
  se calibra con la cadencia real del riel, que hoy no existe. Queda configurable, no fijo en el
  código.
- **La calidad fotométrica entre capturas puede variar**, porque cada apertura de sesión
  reinicia auto-exposición y balance de blancos. → Sostener la sesión durante la ráfaga (D3) es
  precisamente lo que lo acota: los sectores de una misma pasada comparten convergencia. Además se
  registran en el log los valores efectivos de cada captura y se envían en `constraints` de la
  metadata, que es exactamente para lo que el campo existe. Si aun así la variación no alcanza para
  entrenar, la salida es iluminación controlada en el riel — hardware, otro problema.
- **La APK sin firmar de release y sin Play Store obliga al trámite de "orígenes desconocidos".** →
  Es lo esperado en un piloto; el README lo documenta como paso normal.
- **Dos clientes del mismo contrato es más superficie para mantener.** → Es deliberado: es la
  evidencia de que el contrato sirve. Si el mantenimiento pesa, la salida es dar de baja la PWA —
  decisión posterior, no de este cambio.

## Migration Plan

No hay migración: el cambio agrega un proyecto y no toca datos, esquema ni backend.

Puesta en marcha: generar la APK → instalarla → generar un código de vinculación desde el simulador
→ tipear código y URL del backend en el teléfono → conceder cámara, notificaciones y exención de
batería → **Pedir captura** desde el simulador.

Rollback: desinstalar la APK y borrar `Desarrollo/camara-android/`. La PWA sigue operando sin
enterarse. Un dispositivo Android que quedó enrolado se da de baja desde el panel de dispositivos,
que revoca su credencial.

## Open Questions

- ¿La captura con la pantalla apagada funciona en el teléfono concreto del piloto? Es lo primero a
  verificar (T1 de `tasks.md`); si no funciona, el diseño degrada a "pantalla encendida con
  `KEEP_SCREEN_ON`", que sigue siendo mejor que la PWA.
- ¿Conviene fijar la resolución al máximo del sensor ahora que CameraX da el pipeline de foto
  fija? Se deja en manos de la configuración del backend, que es donde el contrato la puso; el
  costo de subirla (≈1,5–3 MB por captura sobre 600 sectores) se mide con capturas reales.
