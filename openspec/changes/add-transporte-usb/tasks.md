## 1. Verificación de portabilidad, antes de escribir procedimiento encima

- [x] 1.1 Confirmar que el backend bindea a todas las interfaces: `server.port=8000` en
      `application.properties:2` y **ausencia** de `server.address` (D3)
- [x] 1.2 Confirmar que existen los wrappers de Windows: `Desarrollo/backend/mvnw.cmd` y
      `Desarrollo/camara-android/gradlew.bat` (D7)
- [x] 1.3 Auditar el manejo de rutas del backend: `AlmacenamientoImagenService.java:42` y el resto
      de la API NIO, buscando concatenación de strings con `/` — no hay (D7)
- [x] 1.4 Auditar los scripts npm de `simulador/package.json` y `frontend/package.json` buscando
      construcciones de shell POSIX (`rm -rf`, prefijos `VAR=x cmd`, `&&`) — no hay (D7)
- [x] 1.5 Confirmar que el conector TLS **degrada** en vez de abortar cuando falta el keystore
      (`HttpsConnectorConfig.java:47-68`), y que por lo tanto Windows no necesita certificados para
      la app nativa (D6)
- [x] 1.6 Confirmar que CORS no participa: `CorsConfig.java:25-27` usa `allowedOriginPatterns` y una
      app nativa no manda `Origin` (D3)
- [x] 1.7 Confirmar que el backend no está en `docker-compose.yml` y que el servicio de inferencia
      lo alcanza por `host.docker.internal` (`docker-compose.yml:35,49-50`), disponible de fábrica
      en Docker Desktop para Windows (D7)

## 2. El hallazgo de la URL

- [x] 2.1 Determinar si la app permite cambiar la URL del backend sin re-enrolar, leyendo
      `AlmacenCredenciales.kt`, `MainActivity.kt` y `Pantallas.kt` — **no lo permite** (D4)
- [x] 2.2 Determinar qué hace la app si la URL deja de responder: reintento indefinido con backoff,
      sin estado terminal; el único terminal es el 401 al renovar (D4)
- [x] 2.3 Determinar si el `refreshToken` está atado a la URL — **no lo está**: el backend lo valida
      por su hash, así que editar sólo `base_url` sería válido (D4)
- [x] 2.4 Comparar las tres mitigaciones (pantalla de ajustes / IP fija / `adb reverse`) y elegir
      cuáles entran en este cambio (D4)
- [x] 2.5 Dejar escrito el **disparador** para abrir el cambio de la pantalla de ajustes, en vez de
      escribirla a ciegas ahora (D4)

## 3. Documentación del procedimiento

- [x] 3.1 `Desarrollo/camara-android/transporte-usb.md`: por qué un cable y no WiFi, con el
      argumento de negocio (`2_ModeloDeNegocio.md:46`) y el del test set
      (`informe-ia-plan-entrenamiento.md:111`)
- [x] 3.2 Sección **"Qué NO es esto"**: descarte explícito de webcam UVC / DroidCam / `v4l2loopback`
      con el argumento de que vuelve al problema de la PWA (D1)
- [x] 3.3 Los dos caminos comparados en tabla, con el criterio para elegir y la recomendación por
      defecto ya corregida por el hallazgo de la URL (D2)
- [x] 3.4 Procedimiento en el teléfono, común a los dos sistemas, incluida la advertencia del cable
      sólo de carga
- [x] 3.5 Procedimiento en **Linux**: descubrir la interfaz, ver la IP, confirmar que el backend
      escucha, regla de `ufw` acotada a la interfaz, y la trampa de la ruta por defecto
- [x] 3.6 Procedimiento en **Windows**: descubrir el adaptador RNDIS, confirmar el backend, y el
      firewall como el obstáculo real, con la regla por puerto preferida sobre reclasificar la red
      (D5)
- [x] 3.7 Sección de `adb reverse` con su ventaja de URL fija y su precio explícito
- [x] 3.8 Tabla de qué URL va en la app según transporte, con la advertencia de que `localhost`
      desde el teléfono es el teléfono
- [x] 3.9 Sección del hallazgo de la URL, con las dos mitigaciones que no tocan la app (D4)
- [x] 3.10 Procedimiento de **IP fija** del lado de la PC, en Linux y en Windows, con la advertencia
      de verificar que la subred no choque con Docker ni libvirt (D4·b)
- [x] 3.11 Tabla de diagnóstico síntoma → causa, con la regla para separar aguas entre backend, red
      y firewall
- [x] 3.12 Sección de límites conocidos: no escala al vivero, un cable un teléfono, largo del cable,
      y energía/datos como problemas separables
- [x] 3.13 Punteros desde `Desarrollo/camara-android/README.md` al documento del transporte

## 4. Coherencia con el resto del repo

- [x] 4.1 Confirmar que el contrato no se toca: `contratos/camara/v1/openapi.yaml` sin cambios y sin
      subir la versión (D9)
- [x] 4.2 Confirmar que el documento vive con el cliente y no en el contrato ni en el README del
      backend, con el porqué escrito (D9)
- [ ] 4.3 Verificar con `git status` que el backend no tiene ni una línea modificada — criterio de
      aceptación del cambio, igual que en `add-camara-android`
- [ ] 4.4 Correr la suite de conformidad del contrato y verificar que sigue en verde: el transporte
      no la afecta, y hay que poder demostrarlo

## 5. Scripts auxiliares

- [x] 5.1 `Desarrollo/camara-android/herramientas/url-backend.sh`: detecta la interfaz por el bus
      USB vía `/sys/class/net/*/device` —no por nombre—, imprime la IP del enlace y la URL lista
      para tipear (D8)
- [x] 5.2 El script verifica el backend **por la IP del enlace**, nunca por `localhost`, que es lo
      que da falsos positivos (D8)
- [x] 5.3 `Desarrollo/camara-android/herramientas/url-backend.ps1`: equivalente para Windows, como
      archivo hermano y no como una rama dentro de un script único (D8)
- [x] 5.4 Los dos son de **sólo lectura**: no levantan interfaces, no cambian rutas, no tocan el
      firewall (D8)
- [x] 5.5 Referenciados desde `transporte-usb.md` como atajo, con el procedimiento a mano igualmente
      completo para que el script nunca sea obligatorio (D10)
- [ ] 5.6 Hacer que el script señale cuando la IP del enlace **no** está fija, que es la condición
      que expone al usuario a D4 sin que se entere

## 6. Verificación punta a punta contra hardware real

> **Nada de esta sección está hecho.** Al escribir el cambio el teléfono no estaba conectado
> (`adb devices` vacío, `lsusb` sin ningún fabricante de teléfono). Todo lo anterior es análisis de
> código y de documentación; esto es la única prueba de que el procedimiento existe.

### 6.1 Linux — el camino principal

- [x] 6.1.1 Enchufar el teléfono con un cable que **transfiera datos** y confirmarlo antes que nada
      (`lsusb` lo lista, `adb devices` lo lista)
      > Verificado 2026-09-19 con el Moto Edge 40 Neo. `lsusb` lo enumera, así que el cable
      > transfiere datos. **Ojo con el nombre que imprime `lsusb`**: en modo carga sale como
      > `22b8:2e82 … [Moto G 3rd Gen]`, que es una etiqueta genérica de `usb.ids` reusada entre
      > modelos. Al activar tethering el product ID cambia a `22b8:2e24` y recién ahí reporta
      > `motorola edge 40 neo`. La enumeración sirve para confirmar el cable, **no** el modelo.
- [x] 6.1.2 Activar USB tethering en el teléfono y verificar que aparece la interfaz nueva en la PC
      con IP asignada
      > Interfaz `enx6ad0b6d5e0c5`, IP `10.206.241.106/24`. Confirmada como USB por el bus
      > (`/sys/devices/…/usb1/1-6/…`), que es el método del script — el nombre no es adivinable.
- [x] 6.1.3 Verificar que la subred que reparte el teléfono **no choca** con los bridges de Docker
      (`172.x`) ni con libvirt (`192.168.122.1`) en esta máquina
      > Subred `10.206.241.0/24`. Sin colisión: Docker ocupa `172.17`–`172.28`, libvirt
      > `192.168.122.0/24` y la LAN `192.168.1.0/24`.
- [x] 6.1.4 Correr `herramientas/url-backend.sh` con el teléfono conectado y confirmar que detecta
      la interfaz correcta y que la URL que imprime es la que sirve
      > Detectó la interfaz, imprimió `http://10.206.241.106:8000` y **avisó solo** que la ruta
      > por defecto se había ido por el enlace (ver 6.1.8). El aviso funciona.
- [x] 6.1.5 Verificar que el backend responde **por la IP del enlace** desde la propia PC
      (`curl http://<ip-usb>:8000/api/nursery`), que es lo que separa "problema de backend" de
      "problema de red"
      > `HTTP 200` en ~31 ms. **Alcance real de esta prueba**: `ip route get 10.206.241.106`
      > devuelve `dev lo` — el paquete va por loopback y **no toca el cable**. Confirma que el
      > backend está vivo y que bindea a esa dirección; **no** confirma que el teléfono llegue.
      > Lo segundo depende del firewall (6.1.6) y sólo se prueba desde el teléfono (§6.2).
- [x] 6.1.6 Abrir el puerto en `ufw` **sólo en la interfaz USB** y confirmar que no quedó abierto en
      la WiFi
      > **No hizo falta en esta máquina: `ufw status` devuelve `inactivo`.** No hay reglas
      > aplicándose, así que el teléfono llega al 8000 sin abrir nada.
      >
      > **Trampa de diagnóstico**: `systemctl is-active ufw` devuelve `active` igual, porque
      > informa sobre la unidad de systemd, no sobre el firewall. Con `ENABLED=no` en
      > `/etc/ufw/ufw.conf` el servicio corre sin aplicar nada. **La fuente de verdad es
      > `sudo ufw status`** — no usar systemd para decidir esto.
      >
      > En una máquina con `ufw` activo (o si se activa acá), la regla correcta sigue siendo
      > por interfaz, no global:
      > ```bash
      > sudo ufw allow in on enx6ad0b6d5e0c5 to any port 8000 proto tcp
      > ```
      > El nombre `enx…` deriva de la MAC del teléfono, así que es estable para ese equipo y la
      > regla sobrevive a desenchufar y volver a enchufar.
- [x] 6.1.7 Fijar la IP de la PC en el enlace (`nmcli … ipv4.method manual`) y confirmar que
      sobrevive a desenchufar y volver a enchufar el cable — **es lo que sostiene D4**
      > **No hizo falta fijarla: se mantuvo sola.** Tras un ciclo de desenchufar / reactivar el
      > anclaje, la interfaz volvió con **el mismo nombre y la misma IP**
      > (`enx6ad0b6d5e0c5` → `10.206.241.106/24`). El nombre es estable porque deriva de la MAC
      > del teléfono; la IP la reasignó igual el DHCP del propio teléfono.
      >
      > También sobrevivió el `ipv4.never-default yes` de 6.1.8: la ruta por defecto siguió por
      > WiFi tras la reconexión. El ajuste es persistente.
      >
      > **Alcance honesto de esta evidencia**: es **un** ciclo de reconexión, dentro de la misma
      > sesión y sin reiniciar el teléfono. No prueba estabilidad frente a un reinicio del
      > equipo ni a lo largo de días. Como la IP se mantuvo, se **deja sin fijar** por ahora
      > (`ipv4.method manual` agregaría un punto de rotura si el teléfono cambiara de subred).
      > Si alguna vez cambia, la salida ya está construida: la pantalla de Ajustes (D4), que
      > permite corregir la URL sin re-vincular.
- [x] 6.1.8 Verificar si la ruta por defecto se fue por el enlace USB y, si pasó, corregirla y
      documentar el ajuste exacto que funcionó
      > **Pasó.** El DHCP del teléfono empujó una ruta por defecto con métrica 100, contra la 600
      > del WiFi: toda la navegación de la PC salía por los datos móviles del teléfono.
      > Ajuste que funcionó, persistente y reversible:
      > ```bash
      > nmcli connection modify "Conexión cableada 2" ipv4.never-default yes
      > nmcli connection up "Conexión cableada 2"
      > ```
      > No pidió contraseña (polkit lo permite para el usuario de la sesión local). Después:
      > una sola ruta por defecto (WiFi), `ip route get 8.8.8.8` sale por `wlp0s20f3`, la IP del
      > enlace se mantuvo y el backend siguió respondiendo por el cable. El nombre del perfil es
      > el que autogenera NetworkManager — confirmarlo con `nmcli connection show --active`.

### 6.2 Ciclo del contrato por el cable

- [x] 6.2.1 Vincular el dispositivo con la URL del enlace USB y un código emitido por el simulador
      > Verificado 2026-09-19 con el Moto Edge 40 Neo (Android 15). **No hizo falta el simulador**:
      > el código se pide por el endpoint público `POST /api/camara/vinculacion`, que es el mismo
      > que usa el simulador. Quedó registrado como `CAM-002` / "Android riel", estado `operativo`,
      > `capturaListo: true`, con heartbeat al día.
- [x] 6.2.2 Verificar que el canal de órdenes abre y sostiene el `ping` por el cable, con la WiFi
      del teléfono **apagada**, que es la prueba de que los bytes van por el USB
      > `CapturaService: Canal de órdenes abierto para CAM-002`, con la WiFi del teléfono apagada.
      > La URL usada (`http://10.206.241.106:8000`) es la IP de la PC **en el enlace USB**: no
      > existe en la red WiFi, así que la conexión sólo pudo haber ido por el cable.
- [x] 6.2.3 Pedir una captura desde el simulador y verificar que la imagen llega y aparece en el
      dashboard
      > Orden emitida por `POST /api/capturas/ordenes` (el endpoint del futuro planificador):
      > **entregada en 13 ms** (`creadaEn` → `entregadaEn`) y devuelta como `RECIBIDA` con
      > `capturaId: CAP-000001`. El JPEG quedó en
      > `Desarrollo/backend/capturas/2026/09/19/CAP-000001.jpg` (289.948 bytes) — **en el
      > filesystem, no en la base**, como manda el invariante. La API lo sirve con
      > `HTTP 200 · image/jpeg`.
      >
      > **Prueba de que la foto es real y de ese equipo**, por EXIF del propio archivo:
      > `manufacturer=motorola · model=motorola edge 40 neo · datetime=2026:09:19 14:24:10 ·
      > 1280x960`.
      >
      > Falta ver la captura **en el dashboard** del frontend (no se levantó en esta corrida).
- [x] 6.2.4 Pasada de varias órdenes seguidas por el enlace, verificando que no aparecen fallos de
      transporte que no existían por WiFi
      > Ráfaga de 4 órdenes seguidas (`MZ-1-002`, `MZ-1-003`, `MZ-2-001`, `MZ-3-001`): las 4
      > llegaron a `RECIBIDA` con **`intentos = 1`** y `motivoFallo = null`, generando
      > `CAP-000002` … `CAP-000005`. Ni un reintento, ni una reentrega. El enlace aguanta la
      > cadencia sin introducir fallos propios.
- [ ] 6.2.5 Desenchufar el cable en medio de una subida y verificar que la imagen queda en disco y
      se drena al reconectar
- [x] 6.2.6 Desenchufar y volver a enchufar, y verificar que el dispositivo vuelve **solo**, sin
      tocar el teléfono
      > **Respuesta partida en dos, y la distinción es la que importa:**
      >
      > **La app SÍ vuelve sola.** Restaurado el enlace, el canal SSE se reabrió sin tocar nada
      > (`Canal de órdenes abierto para CAM-002` a las 14:23:14 y de nuevo a las 14:27:16),
      > `heartbeat: hace 0 s`, `capturasOk: 5`, `capturasError: 0`.
      >
      > **El tethering de Android NO vuelve solo.** Al desenchufar, Android apaga el anclaje por
      > USB y no lo reactiva al reconectar. Con el cable puesto: `lsusb` lista el teléfono
      > (`22b8:2e81`), `adb devices` lo ve — pero **no existe ninguna interfaz de red USB**. Hay
      > que ir físicamente al teléfono y volver a activar el anclaje.
      >
      > Los product IDs delatan el modo, y sirven para diagnosticar:
      > `2e82` carga · `2e24` **tethering activo** · `2e81` adb/MTP sin tethering.
      >
      > **Consecuencia de diseño**: el fallo NO está en el contrato ni en el cliente, está en el
      > transporte. Para un prototipo desatendido esto es una grieta real, y es un argumento
      > fuerte a favor del Camino B (`adb reverse`), que muere igual al desenchufar pero **se
      > relanza desde la PC** (script o regla `udev`), sin tocar el teléfono. Reevaluar en 6.3.4.
- [ ] 6.2.7 Dejar el enlace armado unas horas y verificar que sigue vivo: es la diferencia entre
      "anduvo en la prueba" y "sirve para un prototipo desatendido"
- [ ] 6.2.8 Verificar si el tethering sobrevive a que el teléfono entre en Doze con la pantalla
      apagada (*Open Question* del diseño)

### 6.3 La alternativa `adb reverse`

- [x] 6.3.1 Con depuración USB activada, correr `adb reverse tcp:8000 tcp:8000` y vincular un
      dispositivo con `http://localhost:8000`
      > Túnel armado: `adb reverse --list` → `UsbFfs tcp:8000 tcp:8000`.
      >
      > **No hizo falta vincular un dispositivo nuevo**: se cambió la URL del `CAM-002` ya
      > existente desde la **pantalla de Ajustes** (D4), de `http://10.206.241.106:8000` a
      > `http://localhost:8000`. Sin código nuevo, sin re-enrolar, sin dispositivo fantasma.
      > **Es la primera validación en uso real de la pantalla de Ajustes**, y confirma la
      > premisa de D4: el `refreshToken` no está atado a la URL.
      >
      > Prueba de que los bytes van por el túnel, del lado del teléfono (`adb shell netstat`):
      > todas las conexiones de la app pasaron a `::ffff:127.0.0.1:8000 ESTABLISHED`, y
      > desaparecieron las de `10.206.241.x`. El backend registró `Canal de órdenes abierto
      > para CAM-002` a las 14:31:00.
      >
      > **Cómo verificar el túnel sin `curl`**: el teléfono no trae `curl` y el `nc` de toybox
      > no imprime la respuesta. `adb shell netstat -an | grep :8000` alcanza y sobra: si el
      > túnel vive, el teléfono muestra `[::]:8000 LISTEN`, y los intentos aparecen como
      > `127.0.0.1:… → 127.0.0.1:8000 TIME_WAIT`. Un comando que no imprime nada **no** es un
      > comando que no conectó.
- [x] 6.3.2 Completar el ciclo del contrato por ese camino: canal abierto, captura pedida, imagen
      entregada
      > Orden a `MZ-4-001` (posición 3): **entregada en 7 ms**, pasó a `RECIBIDA` con
      > `intentos: 1` y `motivoFallo: null`, generando `CAP-000006`. JPEG en
      > `Desarrollo/backend/capturas/2026/09/19/CAP-000006.jpg`.
      >
      > Sobre los tiempos (7 ms por el túnel contra 13 ms por tethering): es **una muestra
      > contra una muestra**. No alcanza para afirmar que un camino sea más rápido.
- [x] 6.3.3 Verificar cuánto tarda la app en darse cuenta de que el túnel murió al desenchufar, y
      si se recupera sola al relanzar `adb reverse`
      > **Detección: 60 s de silencio.** Desenchufado el cable, el backend mantiene el
      > dispositivo en `operativo` mientras el heartbeat sea menor a un minuto, y lo pasa a
      > `intermitente` al cumplirse los 60 s. No lo da por muerto de entrada.
      >
      > **Recuperación: 5 s, sin tocar el teléfono.** Al reconectar, `adbd` vuelve solo (la
      > depuración USB alcanza). Un único comando desde la PC —`adb reverse tcp:8000 tcp:8000`—
      > y la app volvió a `operativo` con heartbeat `hace 4 s`. **En ningún momento hubo que
      > tocar el teléfono**, a diferencia del tethering (6.2.6).
      >
      > **Prueba fallida que conviene no repetir**: `adb reverse --remove-all` NO sirve para
      > simular la caída del túnel. Quita el *listener* (no se abren conexiones nuevas) pero
      > **no corta las conexiones ya establecidas**, y como el canal SSE y el heartbeat son
      > persistentes, siguieron latiendo más de 90 s con el túnel "removido". Para simular la
      > caída real hay que desenchufar el cable, que mata `adbd` y las conexiones de una.
- [x] 6.3.4 Dejar registrado cuál de los dos caminos se usa para la defensa y por qué
      > **Decisión (2026-09-19): para la defensa va el Camino B, `adb reverse`.** El Camino A
      > (USB tethering) queda documentado como alternativa, no se elimina.
      >
      > **Por qué**, con lo medido en §6.2 y §6.3:
      > 1. **Recuperación sin mano humana.** Tras desenchufar y reenchufar, el tethering exige
      >    ir físicamente al teléfono a reactivar el anclaje (6.2.6). `adb reverse` se recupera
      >    con un comando en la PC y la app vuelve en **5 s** (6.3.3). Para un prototipo que
      >    debe quedar desatendido, esto es lo decisivo.
      > 2. **URL fija.** `http://localhost:8000` no depende de qué subred reparta el teléfono.
      > 3. **No secuestra la ruta por defecto.** El problema de 6.1.8 —la navegación de la PC
      >    saliendo por los datos móviles— no existe acá, porque no se crea ninguna red.
      >
      > **Precio asumido, explícito:**
      > - Exige dejar la **Depuración USB activada** de forma permanente.
      > - `adb` es una herramienta de desarrollo sosteniendo el camino de producción del
      >   prototipo. Es deuda conceptual, aceptada para la defensa.
      > - **La ventaja es *automatizable*, no *automática***: sin la regla `udev` que relance el
      >   túnel al enchufar, alguien tiene que tipear el comando igual y los dos caminos
      >   empatan en la práctica. **Escribir esa regla es la tarea que sostiene esta decisión.**
      >
      > **Por qué NO se borra el Camino A**: es el camino honesto si esto deja de ser un
      > prototipo. No depende de herramientas de desarrollo ni de depuración activada.

- [ ] 6.3.5 Escribir la regla `udev` que relance `adb reverse tcp:8000 tcp:8000` al enchufar el
      teléfono, y verificar que tras desenchufar y reenchufar la app vuelve **sin intervención
      de ninguna clase** — es lo que convierte la ventaja de 6.3.4 en real

### 6.4 Windows

- [ ] 6.4.1 Levantar el backend en Windows con `mvnw.cmd`, **sin generar certificados**, y confirmar
      que arranca con el warning de HTTPS deshabilitado y sirve el `:8000` (D6)
- [ ] 6.4.2 Confirmar que las capturas se escriben bien en el filesystem de Windows con la ruta
      relativa `./capturas` (D7)
- [ ] 6.4.3 Activar el tethering y confirmar que Windows levanta el adaptador RNDIS sin instalar
      driver
- [ ] 6.4.4 Reproducir el modo de falla del firewall —backend vivo, teléfono sin llegar— para poder
      documentar el síntoma exacto, y recién después aplicar la regla
- [ ] 6.4.5 Aplicar la regla de firewall **por puerto** y verificar que alcanza, sin reclasificar la
      red como privada (D5)
- [ ] 6.4.6 Correr `herramientas/url-backend.ps1` en Windows real: **nunca se ejecutó**, sólo se
      validó su sintaxis con `pwsh`
- [ ] 6.4.7 Completar el ciclo del contrato desde Windows: vinculación, canal, captura e imagen en
      el dashboard
- [ ] 6.4.8 Generar la APK en Windows con `gradlew.bat`, para cerrar la paridad de toolchain (D7)

### 6.5 Puesta a punto de la demo

- [ ] 6.5.1 Bajar `POLLING_INTERVAL_SECONDS` del servicio de inferencia **por entorno**, no tocando
      el default de `docker-compose.yml:39` (D11)
- [ ] 6.5.2 Ensayo completo con el cable como único transporte: WiFi del teléfono apagada, riel
      posicionando, captura, diagnóstico y dashboard

## 7. Cierre

- [ ] 7.1 Volcar en `design.md` → *Open Questions* el resultado de la §6: si la IP se mantiene, si
      el tethering sobrevive a Doze, y cuál de los dos caminos quedó elegido
- [x] 7.2 Corregir `transporte-usb.md` con lo que la realidad haya desmentido: nombres de interfaz,
      comandos que no funcionaron, síntomas que aparecieron y no estaban previstos
      > Corregido: la advertencia "la URL no se puede cambiar sin volver a vincular" era falsa a
      > partir de 7.3 — se reemplazó por el procedimiento con Ajustes. Se actualizó también la
      > recomendación de camino por defecto según lo medido en §6.2.6/§6.3.4 (el tethering no se
      > recupera solo al desenchufar/reenchufar; `adb reverse` sí).
- [x] 7.3 Decidir, con el dato en la mano, si se dispara el cambio de la pantalla de ajustes (D4) o
      si las mitigaciones de transporte alcanzaron
      > **Se disparó.** §6.3.1 mostró la pantalla de Ajustes en uso real, cambiando la URL de un
      > dispositivo ya vinculado sin re-enrolar. Implementada en este mismo cambio (no en uno
      > aparte): `PantallaAjustes`, `AlmacenCredenciales.actualizarUrl`,
      > `DispositivoService.reiniciarConexion`, `ValidadorUrl`. Ver `design.md` D4 (actualización
      > 2026-09-26) y `proposal.md`.
- [ ] 7.4 Barrer, si se toca `docker-compose.yml` por cualquier motivo, el volumen `capturas-data`
      declarado y no usado (línea 59) — limpieza oportunista, no alcance de este cambio
