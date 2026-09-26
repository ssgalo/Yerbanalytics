## Context

La app nativa de `Desarrollo/camara-android/` habla HTTP contra el backend por los siete endpoints
de `camara/v1`. Hoy esos bytes viajan por WiFi. Este cambio los manda por el cable USB sin tocar la
app, el contrato ni el backend: el cable **transporta la red**, no la imagen.

El contrato lo habilita explícitamente. Su segundo principio
(`Desarrollo/contratos/camara/v1/README.md:36`) es *"Neutralidad de plataforma: ninguna obligación
depende de una API de navegador"*, y ninguna depende tampoco de un medio físico. Un enlace USB es
una interfaz de red más.

**El escenario real es una demo.** El prototipo es el canal de venta
(`Documentacion/2_ModeloDeNegocio.md:46`) y el test set del modelo sale de sus tubetes *"en las
mismas condiciones que el día de la defensa"*
(`Documentacion/arquitectura y hw/informe-ia-plan-entrenamiento.md:111`). Lo que se optimiza acá no
es throughput —un JPEG sobre USB 2.0 viaja en milisegundos— sino **que el enlace exista cuando hay
alguien mirando**.

**Estado del entorno al escribir este documento**: PC Linux con WiFi en `192.168.1.64/24`, `adb`
34.0.4 en `/usr/bin/adb`, varios bridges Docker en `172.x` y libvirt en `192.168.122.1`. El teléfono
**no estaba conectado**: `adb devices` vacío y `lsusb` sin ningún fabricante de teléfono. Todo lo
que sigue está razonado sobre el código y sobre cómo se comporta Android, **no verificado contra
hardware real**. Eso es un riesgo declarado, no una omisión.

## Goals / Non-Goals

**Goals:**

- El dispositivo alcanza el backend por el cable, con el mismo contrato y la misma app.
- El procedimiento funciona en Linux **y** en Windows, con paridad real de documentación.
- Cero cambios en el backend, en el contrato y en la app.
- La URL que se tipea en el teléfono es **estable durante toda la vida del enrolamiento**.
- Cuando algo falla, hay una forma escrita de separar aguas entre backend, red y firewall.
- Los límites del transporte por cable quedan declarados, para que no sorprendan en la defensa.

**Non-Goals:**

- Convertir el teléfono en webcam USB (D1).
- Reemplazar el WiFi. Se suma un transporte; los dos deben seguir siendo posibles.
- Descubrimiento automático del backend (mDNS). Sigue vigente el *non-goal* de
  `add-camara-android`: la URL se tipea.
- Cablear el vivero real. Esto es para la maqueta y la demo.
- ~~Agregar una pantalla de ajustes a la app. Es la salida limpia a D4 y tiene su propio cambio.~~
  **Resuelto en este cambio (2026-09-26)**: el disparador de D4 se cumplió durante la verificación
  de hardware y la pantalla se implementó acá mismo. Ver la actualización en D4.

## Decisions

### D1 — El cable transporta la RED, no la imagen: tethering, nunca webcam UVC

Hay dos maneras de "conectar el teléfono por USB" y son opuestas.

| | Transporte de red (lo que se elige) | Webcam UVC / DroidCam / `v4l2loopback` |
|---|---|---|
| Qué viaja por el cable | Peticiones HTTP del contrato | Un stream de video crudo |
| Qué es el teléfono | Un dispositivo del contrato | Un periférico tonto |
| Quién decide cuándo disparar | La app, ante una orden | La PC, sacando fotogramas |
| Colas en disco, acuse de fallos, heartbeat, modo degradado | Se conservan | Se pierden todos |

La app nativa existe **justamente** para escapar del segundo modelo: la PWA sacaba fotogramas de un
stream con `<canvas>`, con techo real de ~1080p y sin esperar a que converjan exposición y foco. La
app usa CameraX con `ImageCapture` —pipeline de foto fija, con convergencia real de 3A—, y ésa fue
la decisión D3 de `add-camara-android`.

Convertir el teléfono en webcam **vuelve al problema de la PWA, pero con más cables**. Se descarta
sin más análisis: no es una alternativa peor, es el problema que ya resolvimos.

*Corolario:* cualquier propuesta futura que empiece con "y si la PC le saca la foto al teléfono"
está pidiendo revertir `add-camara-android`. Ése es el argumento a dar, no el ancho de banda.

### D2 — USB tethering **con IP fija** es el camino principal; `adb reverse` es la alternativa

| | **A · USB tethering** | **B · `adb reverse`** |
|---|---|---|
| Qué hace | El teléfono expone una interfaz de red por el cable | `adb` reenvía el `localhost:8000` del teléfono a la PC |
| URL en la app | `http://<IP de la PC en el link USB>:8000` | `http://localhost:8000` — **siempre la misma** |
| Necesita | Tethering activado en el teléfono | Depuración USB + el demonio `adb` vivo |
| Desatendido | Sí: es un enlace de red, sin proceso niñera | No: se cae al desenchufar y hay que relanzarlo |
| Contra | Con DHCP, la IP puede cambiar entre sesiones | Depende de un proceso extra corriendo |

**A es el principal** porque es un enlace de red de verdad: existe mientras exista el cable, sin
nada corriendo que haya que vigilar. Un prototipo que queda armado y desatendido no puede depender
de que alguien se acuerde de relanzar un comando.

**Pero A "pelado", con la IP que reparta el DHCP del teléfono, ya no es la opción por defecto.** Lo
descalifica D4: si la IP se mueve, el dispositivo queda apuntando a una dirección muerta y la única
salida es borrar los datos de la app y re-enrolar. La opción por defecto es **A con IP fija del lado
de la PC** (`nmcli … ipv4.method manual` en Linux, `New-NetIPAddress` en Windows), que conserva la
robustez del enlace y le saca la varianza.

**B no es el plan B de emergencia: es la otra respuesta válida a D4.** Su URL es fija *por
construcción*, así que ninguna IP puede romperla. Se elige cuando hay una sesión de trabajo con la
PC delante, o cuando fijar la IP no resulta practicable en la máquina concreta. Se paga con un
demonio que puede morir en silencio (ver *Risks*).

*Alternativa evaluada y descartada:* correr el backend en el propio teléfono (Termux). Elimina la
red del problema pero mueve el backend, la base y el broker a un dispositivo que existe para
capturar. Es una arquitectura distinta disfrazada de solución de transporte.

### D3 — Cero cambios en el backend, y está verificado

`Desarrollo/backend/src/main/resources/application.properties:2` fija `server.port=8000` y **no hay
ninguna propiedad `server.address`**. Spring Boot, sin `server.address`, bindea a `0.0.0.0`: escucha
en todas las interfaces, incluida la que aparezca por el cable. **El teléfono alcanza el backend por
el link USB sin que nadie toque nada.**

Ésa es la razón por la que este cambio es casi sólo documentación, y conviene decirlo explícito para
que nadie "arregle" el problema agregando una propiedad.

CORS tampoco entra en juego. `CorsConfig.java:25-27` usa `allowedOriginPatterns`, y además una app
nativa **no manda header `Origin`**: no hay preflight, no hay origen que autorizar. Cualquier intento
de resolver un problema de este transporte tocando CORS está mirando el lugar equivocado.

*Consecuencia de seguridad, asumida a conciencia:* bindear a todas las interfaces significa que el
backend también escucha en la WiFi de la casa. Es el comportamiento que ya había antes de este
cambio; no se degrada nada. La postura correcta para endurecerlo es firewall por interfaz (D5), no
`server.address`, porque fijarlo rompería el acceso del dashboard y del simulador.

### D4 — La URL queda atada al enrolamiento: se resuelve eligiendo el transporte, no tocando la app

**Éste es el hallazgo que justifica que el cambio exista.**

Lo que dice el código:

- `contrato/AlmacenCredenciales.kt:9-15,44-50` — el DataStore `dispositivo` guarda `base_url`,
  `dispositivo_id` y `refresh_token` **como una unidad** (`Credencial`), escritos en un solo `edit`.
- `ui/MainActivity.kt:185-206` — `vincular()` es el **único** escritor de ese registro, y para
  llegar ahí hace falta un código de vinculación de un solo uso: llama a `/enrolar`
  (`MainActivity.kt:192`) y recién con su respuesta guarda (`MainActivity.kt:194`).
- `ui/MainActivity.kt:74` — `yaVinculado = almacen.leer() != null`; y en `MainActivity.kt:83-98`,
  si hay credencial se muestra `PantallaOperacion` y **nunca más** `PantallaVinculacion`.
- `ui/Pantallas.kt:57-119` — la única pantalla que acepta una URL exige además el código
  (`enabled = … && codigo.isNotBlank()`, líneas 111-117). **Cambiar la URL implica un enrolamiento
  completo nuevo.**
- No hay pantalla de ajustes en toda la app: el único uso de `android.provider.Settings` es la
  exención de batería (`ui/MainActivity.kt:14,170`).
- `contrato/AlmacenCredenciales.kt:53-55` — `borrar()` existe, pero su **único** llamador es
  `servicio/DispositivoService.kt:296-304`, en el callback `alRevocar`, que dispara desde
  `contrato/SesionToken.kt:41-46` **sólo ante un HTTP 401** al renovar el token.

Y lo que pasa cuando la URL deja de responder —que es el caso real de este cambio— es que **no es un
401**. `CanalOrdenes.iniciar()` (`contrato/CanalOrdenes.kt:47-157`) es un `while (isActive)` con
espera creciente y jitter, topeada en 60 s (`contrato/Backoff.kt:17`): reintenta **para siempre**, el
estado oscila entre `RECONECTANDO` y `DESCONECTADO`, pero `vinculado` **nunca vuelve a false**. No
hay estado terminal por falta de conectividad; el único terminal es el 401.

> **Respuesta a la pregunta abierta: NO.** La app no permite cambiar la URL del backend sin volver a
> enrolar el dispositivo. No cae en un estado terminal —reintenta indefinidamente, que es el
> comportamiento correcto ante un corte— pero tampoco ofrece salida: la pantalla donde se tipea la
> URL es inalcanzable mientras haya credencial guardada.

**El `refreshToken` NO está atado criptográficamente a la URL.** Se co-guardan por conveniencia,
pero el backend lo valida **sólo por su hash**; `SesionToken` lo presenta contra `$base/token`
(`contrato/SesionToken.kt:37`). Una pantalla que editara únicamente `base_url` conservando
`dispositivoId` + `refreshToken` funcionaría contra el mismo backend sin re-enrolar. **Es
acoplamiento de UX y de almacenamiento, no de protocolo.** Eso importa porque acota la solución a un
cambio de UI en la app: ni el contrato ni el backend entran.

**Tres mitigaciones, y no son intercambiables:**

| | Qué resuelve | Costo | Cuándo |
|---|---|---|---|
| **(a) Pantalla de ajustes** que edite sólo la URL conservando la credencial | El problema de fondo: desacopla transporte de enrolamiento | Código en la app, y una decisión de diseño propia (¿revalidar contra `/config` antes de guardar?) | Cuando (b) y (c) no alcancen |
| **(b) IP fija del lado de la PC** en la interfaz USB (`nmcli … ipv4.method manual`, `New-NetIPAddress`) | Que la URL del tethering deje de moverse | Un paso más de puesta en marcha, por máquina | Prototipo armado y desatendido |
| **(c) `adb reverse tcp:8000 tcp:8000`** → `http://localhost:8000` | El problema desaparece: la URL es fija **por construcción** | Depende del demonio `adb`, que se cae al desenchufar y en silencio | Sesión de trabajo con la PC delante; defensa |

**Decisión para este cambio: no se agrega código a la app.** Se toma (b) como camino por defecto y
(c) como alternativa, que son las dos que no tocan el cliente. (a) es la solución correcta a largo
plazo y **no se descarta: se difiere con disparador escrito.**

**Disparador explícito para abrir un cambio propio** (`add-ajustes-dispositivo` o el nombre que
corresponda): si la verificación de `tasks.md` §6 muestra que la URL cambia entre sesiones **y**
ni (b) ni (c) resultan practicables en el teléfono y la máquina reales, entonces la app necesita esa
pantalla. No antes: escribirla hoy sería resolver un problema que todavía no se midió —y con un
diseño elegido a ciegas, que es peor.

**Escape hatch mientras tanto**, para que nadie quede encerrado: Ajustes de Android → la app →
Almacenamiento → **Borrar datos** limpia el DataStore y devuelve la pantalla de vinculación. El
precio es concreto y hay que decirlo: hace falta **un código de vinculación nuevo** desde el
simulador, y el dispositivo anterior **queda registrado como fantasma** en el backend, así que se lo
da de baja desde el panel de dispositivos.

> **Actualización (2026-09-26): el disparador se cumplió, y la mitigación (a) se implementó en
> este cambio.**
>
> `tasks.md` §6.3.1 registra que, validando el Camino B (`adb reverse`) contra hardware real, se
> cambió la URL del `CAM-002` ya vinculado desde una pantalla de Ajustes —de
> `http://10.206.241.106:8000` a `http://localhost:8000`— **sin re-enrolar, sin código nuevo del
> simulador y sin dispositivo fantasma**. Es la primera validación en uso real de la mitigación (a)
> de esta tabla, y confirma la premisa que la sostenía: el `refreshToken` no está atado a la URL.
>
> La pantalla se implementó en este mismo cambio, no en uno aparte:
> - `PantallaAjustes` (`ui/Pantallas.kt`) — UI que edita sólo la URL.
> - `AlmacenCredenciales.actualizarUrl` / `mutarSoloUrl` (`contrato/AlmacenCredenciales.kt`) —
>   persiste únicamente `base_url` dentro del DataStore, conservando `dispositivoId` y
>   `refreshToken` por construcción del propio `MutablePreferences.edit`.
> - `ValidadorUrl` (`contrato/ValidadorUrl.kt`) — valida esquema/host/puerto antes de guardar, con
>   tests en `app/src/test/.../AjustesTest.kt`.
> - `DispositivoService.reiniciarConexion` (`servicio/DispositivoService.kt`) — reconstruye
>   cliente HTTP, sesión de token y canal SSE contra la credencial vigente en disco, sin recrear el
>   `Service` ni perder colas ni visor.
>
> La decisión original de este documento —"no se agrega código a la app"— fue el razonamiento
> correcto **en el momento en que se escribió**: no había medición todavía, y escribir la pantalla
> a ciegas hubiese sido peor. La medición de §6.3 la superó, tal como el propio disparador preveía.
> Ver `tasks.md` 7.3.

### D5 — En Windows el obstáculo real es el firewall, y se abre por puerto, no reclasificando la red

Cuando aparece una interfaz nueva, Windows la clasifica como **Pública** y bloquea todo lo entrante.
El síntoma es cruel: el backend corre perfecto, `curl localhost:8000` responde desde la propia PC, y
el teléfono no llega nunca **sin ningún mensaje de error útil**.

Hay dos salidas y no son equivalentes:

| | Qué hace | Por qué |
|---|---|---|
| `Set-NetConnectionProfile -NetworkCategory Private` | Reclasifica **toda** la interfaz | Cambia la postura de seguridad de todo lo que escuche ahí, no sólo del backend |
| `New-NetFirewallRule -LocalPort 8000 -Protocol TCP` | Abre **un** puerto | Es exactamente lo que hace falta y nada más |

**Se prefiere la regla por puerto.** El principio es el de siempre: abrir la mínima superficie que
resuelve el problema. Reclasificar la red como privada además activa el descubrimiento de red y
compartición de archivos, que no tienen nada que ver con esto.

El equivalente en Linux es el mismo criterio, y encima se puede acotar por interfaz —cosa que
Windows no hace tan cómodo—: `ufw allow in on usb0 to any port 8000 proto tcp` abre el puerto
**sólo** en el link del cable, no en la WiFi.

### D6 — Windows no necesita certificados, porque el backend degrada solo

Ésta era la duda razonable: `Desarrollo/certs/generar-certificados.sh` es bash puro, así que en
Windows no corre. ¿Se rompe el arranque del backend?

No. `config/HttpsConnectorConfig.java:47-68` resuelve el keystore y, si no lo encuentra, **loguea un
warning y arranca sin el conector TLS** en vez de abortar. Es un diseño que degrada con elegancia, y
es el que hace que este cambio no arrastre un port de scripts a PowerShell.

Y ese conector existe **sólo para la PWA**: `getUserMedia` exige origen seguro y una página HTTPS no
puede llamar a un endpoint HTTP. **La app nativa no tiene ninguna de esas restricciones** y habla
HTTP contra el `:8000` (decisión D4 de `add-camara-android`). Su
`app/src/main/res/xml/network_security_config.xml` ya permite tráfico en claro con anclas `system` +
`user`, así que `http://192.168.42.x:8000` y `http://localhost:8000` funcionan sin recompilar —y
`https://…:8443` también, el día que haya certificados.

**Conclusión operativa:** en Windows se puede correr el backend sin generar un solo certificado y la
app Android funciona igual. La PWA no, pero la PWA no es el cliente de producción.

### D7 — El resto del stack ya era portable; se verificó en vez de asumirlo

No alcanzaba con que el backend bindeara bien: si el proyecto no corre en Windows, el procedimiento
no existe. Lo que se revisó, con su evidencia:

| Qué | Evidencia | Veredicto |
|---|---|---|
| Wrapper de Maven | `Desarrollo/backend/mvnw.cmd` existe | El backend compila y corre |
| Wrapper de Gradle | `Desarrollo/camara-android/gradlew.bat` existe | La APK se genera |
| Rutas del filesystem | `service/AlmacenamientoImagenService.java:42` usa `Paths.get(...).toAbsolutePath().normalize()`, y el resto es API NIO (`Files.*`, `Path.resolve`) | Cero concatenación de strings con `/`: portable tal cual |
| Directorio de capturas | `application.properties:135` → `./capturas`, relativo | Portable |
| Scripts npm | `Desarrollo/simulador/package.json` y `Desarrollo/frontend/package.json`: sólo `tsx`/`vite`/`tsc`/`eslint`, sin `rm -rf`, sin prefijos `VAR=x cmd`, sin `&&` de shell | Portables sin tocar nada |
| Contenedores | `docker-compose.yml:35,49-50`: el backend **no** está en compose, y el servicio de inferencia le pega por `host.docker.internal` con `extra_hosts: host-gateway` | Docker Desktop para Windows trae `host.docker.internal` de fábrica |

El resultado es que la portabilidad a Windows **ya estaba**, por decisiones anteriores bien tomadas.
Vale escribirlo para que nadie la rompa sin darse cuenta: el día que aparezca un script `.sh` en el
camino crítico del backend, esta tabla deja de ser verdad.

### D8 — Un script auxiliar que informa, en dos sabores hermanos, y no configura nada

La parte del procedimiento que más se equivoca a mano es **"cuál de estas interfaces es el cable, y
qué URL tipeo"**. En una PC con WiFi, varios bridges de Docker en `172.x` y libvirt en
`192.168.122.1`, elegir la IP correcta a ojo es una fuente de tardes perdidas.

El script detecta la interfaz aparecida por USB, imprime la IP de la PC en ese link, **la URL
completa lista para tipear**, y verifica que el backend responda **por esa IP** —no por `localhost`,
que es justamente lo que engaña—. Dos archivos hermanos, uno por sistema:
`Desarrollo/camara-android/herramientas/url-backend.sh` y `url-backend.ps1`.

En Linux la detección no se hace por nombre de interfaz (`usb0`, `enp0s20u1`, `rndis0`: el nombre
depende del fabricante y de `systemd`) sino **por el bus**, leyendo `/sys/class/net/*/device`. Es la
diferencia entre una heurística y un hecho.

Tres restricciones, y las tres son decisiones:

1. **Es de sólo lectura.** No levanta interfaces, no cambia rutas, no toca el firewall. Un script que
   modifica el entorno de la máquina en un procedimiento que el usuario va a correr para *entender*
   qué pasa es un script que agrega variables en vez de sacarlas.
2. **Dos scripts hermanos, no uno "multiplataforma".** Un solo archivo que detecte el sistema y se
   bifurque es peor de leer que dos archivos de treinta líneas cada uno, y el lector que abre el
   `.ps1` no tiene por qué leer el camino de Linux.
3. **Vive en `Desarrollo/camara-android/`**, al lado del documento que explica el procedimiento, no
   en el backend ni en el contrato. El backend no conoce transportes; el contrato tampoco.

**Los dos scripts ya existen** y son parte de este cambio. Lo que falta no es escribirlos sino
**probarlos contra hardware real**: el `.sh` se ejercitó en Linux pero sin teléfono enchufado, y del
`.ps1` sólo se validó la sintaxis con `pwsh` —nunca corrió en Windows. Eso es `tasks.md` §6, y hasta
entonces la salida del script es una hipótesis igual que el resto del documento.

### D9 — La documentación del transporte vive con el cliente, no con el contrato ni con el backend

`Desarrollo/camara-android/transporte-usb.md` es el lugar correcto, y por una razón que no es de
comodidad: **el transporte es un problema de puesta en marcha del cliente, no una obligación del
contrato**. El contrato es neutral de plataforma por principio
(`Desarrollo/contratos/camara/v1/README.md:36`); meterle un capítulo de USB lo volvería menos
neutral, no más completo.

Tampoco va en el README del backend, que no conoce ni debe conocer por dónde le llegan las
peticiones.

Corolario para el futuro: si mañana la PWA también se conecta por cable, el documento se generaliza o
se duplica en `Desarrollo/camara/`; lo que **no** se hace es subirlo al contrato.

### D10 — No atarse a x86 ni a Windows: lo que se decide acá tiene que sobrevivir a una Raspberry

`Documentacion/arquitectura y hw/informe-ia-plan-entrenamiento.md:219` deja abierto que el edge se
mude a una **Raspberry** más adelante. Eso impone dos reglas sobre este cambio:

- **Nada de drivers propietarios ni herramientas de un solo sistema en el camino crítico.** USB
  tethering (RNDIS/NCM) y `adb` existen en Linux ARM igual que en x86. Cualquier solución que
  dependiera de un ejecutable de Windows quedaría descartada por esto solo.
- **Los scripts de D8 son auxiliares, no requisitos.** El procedimiento tiene que poder ejecutarse
  leyendo el documento y tipeando tres comandos, sin ellos. Si el script se vuelve obligatorio, el
  procedimiento dejó de ser portable.

El `.ps1` es soporte de una plataforma que hoy hace falta, no una apuesta a ella.

### D11 — Lo que la demo necesita se ajusta por entorno, no por código

Al revisar el stack apareció algo que va a doler el día de la defensa y no es del transporte:
`docker-compose.yml:39` fija `POLLING_INTERVAL_SECONDS: 14400` para el servicio de inferencia —**4
horas**. En una demo eso es inusable: se pide una captura y el diagnóstico aparece cuatro horas
después.

**Se ajusta por variable de entorno, no cambiando el default en el código.** Cuatro horas puede ser
el número correcto para operación continua en un vivero; lo que está mal es usarlo en una demo. Un
sistema que necesita recompilarse para demostrarse tiene un problema de configuración, no de valor
por defecto.

Queda como tarea de la puesta a punto de la demo (`tasks.md` §6), señalado acá porque es
exactamente el tipo de detalle que aparece cinco minutos antes de la presentación.

## Risks / Trade-offs

- **Nada de esto está probado contra hardware real.** Es el riesgo principal y no se puede mitigar
  con más análisis: al escribir este documento el teléfono no estaba conectado (`adb devices` vacío,
  `lsusb` sin fabricante de teléfono). → La §6 de `tasks.md` es la mitigación, y hasta que se corra,
  todo este diseño es una hipótesis bien fundada.
- **La URL atada al enrolamiento es el riesgo número uno (D4).** No es una molestia de puesta en
  marcha: si la IP se mueve, el dispositivo queda muerto y hay que borrar los datos de la app y
  re-vincular, con código nuevo del simulador y un dispositivo fantasma que dar de baja. Para algo
  que queda armado y se muestra en una defensa, es inaceptable. → Mitigado por elección de
  transporte (D4·b, D4·c) y con disparador escrito para la solución de fondo (D4·a).
- **`url-backend.ps1` nunca corrió en Windows.** Se validó su sintaxis con `pwsh` y nada más. El
  `.sh` sí se ejercitó en Linux, pero sin teléfono enchufado. → Los dos están en la verificación de
  `tasks.md` §6; hasta entonces son ayudas no probadas, y el procedimiento a mano es el que manda.
- **Fijar la IP es un paso por máquina, no una propiedad del procedimiento.** Quien clone el repo en
  otra PC y siga el documento sin fijarla queda expuesto a D4 sin enterarse. → Va señalado en el
  documento y en el propio script, no sólo acá.
- **El cable puede ser sólo de carga.** Muchos USB-C baratos no tienen los pines de datos: el
  teléfono carga y no aparece nunca en ningún lado. Es la causa número uno de perder una tarde. →
  Primer paso de la verificación, antes que cualquier otra cosa.
- **Algunos Android se niegan a activar el tethering sin una conexión que compartir.** La opción
  queda gris aunque el cable esté bien. → Se prueba con datos o WiFi activos en el teléfono; si el
  comportamiento se confirma, `adb reverse` (D2·B) pasa a ser el camino principal, no la alternativa.
- **El tethering le puede robar la ruta por defecto a la PC.** El teléfono también ofrece internet, y
  Linux puede mandarle *todo* el tráfico. El síntoma es "de golpe la PC navega raro". → Se corrige
  subiendo la métrica de esa ruta o marcando la conexión como *sólo para recursos de esta red* en
  NetworkManager. Queda en el diagnóstico de fallas.
- **Colisión de subredes.** El tethering de Android usa típicamente `192.168.42.0/24`. En **esta**
  máquina no choca con nada (los bridges de Docker están en `172.x` y libvirt en `192.168.122.1`),
  pero eso es una propiedad de esta PC, no del procedimiento. → Se verifica antes de dar por bueno
  el enlace en cualquier otra máquina.
- **`adb reverse` se cae en silencio.** Si el demonio muere o se desenchufa el cable, el túnel
  desaparece sin avisarle a nadie; la app hace lo correcto —reintenta para siempre (D4)— contra un
  túnel muerto. → Es el motivo por el que B no es el camino desatendido. Para sesiones con la PC
  delante es aceptable; para el prototipo armado, no.
- **Sin TLS el token viaja en claro.** Es el mismo desvío ya asumido en `add-camara-android` D4, y
  por el cable el riesgo es **menor** que por WiFi: un enlace punto a punto no tiene a nadie
  escuchando. No mejora la postura del sistema, pero tampoco la empeora.
- **Un cable, un teléfono.** El tethering da un enlace punto a punto. Si alguna vez hay más de un
  dispositivo de captura, vuelve el WiFi o entra un switch. → Declarado como límite, no como deuda.
- **Largo del cable.** USB 2.0 pasivo llega a ~5 m (~4 m si es C-a-C); más que eso necesita una
  extensión activa. El ancho de banda no es problema ni de cerca. → Si el tramo crece, energía y
  datos se separan y la app no se entera: se le cambia la URL y nada más… con el costo de D4.

## Open Questions

- **¿La IP de la PC en el link USB se mantiene entre reconexiones en el teléfono concreto del
  piloto?** Es la pregunta que decide si D4 se resuelve con tethering o si obliga a `adb reverse`.
  Sólo se responde enchufando.
- **¿El tethering sobrevive a que el teléfono entre en Doze con la pantalla apagada?** El servicio de
  la app ya pide exención de optimización de batería (`add-camara-android` D10), pero el tethering es
  una función del sistema y se gobierna aparte.
- **¿Conviene dejar el enlace USB **y** la WiFi conectados a la vez?** Daría un camino de respaldo,
  pero con la URL fija al enrolamiento (D4) la app no puede alternar: sigue una sola URL. Responder
  esto puede cambiar el veredicto sobre la pantalla de ajustes.
- **Si aparece la pantalla de ajustes, ¿editar la URL debería revalidar contra `/config` antes de
  guardar?** Guardar una URL que no responde deja al dispositivo peor que antes. Es diseño del
  cambio siguiente, pero conviene que quede anotado desde acá.
