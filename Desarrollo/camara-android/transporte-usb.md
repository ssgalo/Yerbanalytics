# Yerbanalytics · App de cámara — conexión por USB

Cómo conectar el teléfono al backend **por el cable USB** en vez de por WiFi, en Linux y en
Windows.

Esto no cambia la app ni el contrato: la app sigue hablando HTTP contra el backend, con los
mismos siete endpoints de `camara/v1`. Lo único que cambia es **por dónde viajan esos bytes**.
El contrato lo dice en su propia sección de *Neutralidad de plataforma*: no presupone ningún
transporte. Un cable es una red como cualquier otra.

> Para generar la APK, instalarla, los ajustes de Android y el diagnóstico general, mirá
> [`README.md`](README.md). Este documento cubre **sólo el transporte**.

---

## Por qué un cable y no WiFi

Tres razones, en orden de peso.

**1. El teléfono necesita alimentación igual.** Montado en el riel, capturando todo el día, se
queda sin batería. Ya vas a tener un cable. La pregunta no es *"¿pongo un cable?"* sino
*"ya que hay un cable, ¿le pido también que lleve los datos?"*. Y la respuesta es que sí,
porque sale gratis.

**2. WiFi desaparece como modo de falla.** Un vivero no es una oficina: hay humedad, estructura
metálica, distancia al router y un enlace que se cae cuando nadie está mirando. Cada corte es una
orden que no llega y una imagen que se queda en la cola. Con el cable, el enlace existe mientras
exista el cable.

**3. El prototipo es el canal de venta.** `Documentacion/2_ModeloDeNegocio.md` lo dice con todas
las letras: *"Demostración con prototipo: es nuestro canal decisivo para cerrar la venta"*. Y el
plan de entrenamiento del modelo agrega que el test set sale de los tubetes del prototipo, *"mismas
condiciones que el día de la defensa"*. Una demo que depende del WiFi del lugar es una demo que
puede fallar delante del cliente.

## Qué NO es esto

**No** es usar el teléfono como webcam USB (modo UVC, DroidCam, Iriun, `scrcpy` + `v4l2loopback`).

Suena parecido y es lo contrario. La app Android existe justamente para escapar de eso: la PWA
sacaba fotogramas de un stream de video con `<canvas>`, con techo real de ~1080p y sin esperar a
que converjan exposición y foco. La app usa **CameraX con `ImageCapture`**: pipeline de foto fija,
con convergencia real de los ajustes automáticos.

Convertir el teléfono en webcam devuelve a la PC un stream de video del que hay que extraer
fotogramas — es decir, **vuelve al problema de la PWA, pero con más cables**. El teléfono deja de
ser un dispositivo del contrato y pasa a ser un periférico tonto: se pierden las colas en disco, el
acuse de fallos, el heartbeat y el modo degradado.

El cable transporta la red. La inteligencia del dispositivo se queda en el dispositivo.

---

## Atajo: que te lo diga un script

Antes de seguir a mano, probá esto. Detecta el enlace USB, averigua la IP, verifica que el
backend responda **por esa IP** (no por `localhost`, que es lo que engaña) y te imprime la URL
exacta para tipear en la app. Si algo falla, te dice cuál de las tres causas es.

```bash
# Linux
Desarrollo/camara-android/herramientas/url-backend.sh
```

```powershell
# Windows
.\Desarrollo\camara-android\herramientas\url-backend.ps1
```

No modifican nada: sólo miran interfaces y hacen una consulta HTTP. El resto de este documento
es el procedimiento a mano, por si el script no alcanza o querés entender qué está pasando.

---

## Dos caminos

| | **A · USB tethering** | **B · `adb reverse`** |
|---|---|---|
| Qué hace | El teléfono expone una interfaz de red por el cable | `adb` reenvía el `localhost:8000` del teléfono a la PC |
| URL que se tipea en la app | `http://<IP de la PC en el link USB>:8000` | `http://localhost:8000` — **siempre la misma** |
| Necesita | Tethering activado en el teléfono | Depuración USB + el demonio `adb` vivo |
| Recupera solo al desenchufar y reenchufar | **No** — Android apaga el anclaje y hay que ir al teléfono a reactivarlo a mano (verificado) | **Sí, desde la PC** — `adbd` vuelve solo y alcanza con relanzar un comando; el teléfono no se toca (verificado) |
| Contra | La IP puede cambiar entre sesiones si no se fija | Depende de un proceso extra corriendo, y de dejar la Depuración USB activada |

**Para la defensa, el camino recomendado es B (`adb reverse`), verificado contra hardware real**
(Moto Edge 40 Neo, 2026-09-19 — ver `tasks.md` §6.3.4). El motivo es concreto: tras desenchufar y
volver a enchufar el cable, el tethering **no se recupera solo** — hay que ir físicamente al
teléfono y reactivar el anclaje (§6.2.6). `adb reverse` sí se recupera sin tocar el teléfono: un
único comando desde la PC devuelve la app a `operativo` en unos segundos (§6.3.3). Para un
prototipo que va a quedar armado y desatendido, eso es lo que importa. El precio: hay que dejar la
Depuración USB activada de forma permanente, y automatizar la reconexión con una regla `udev` sigue
pendiente (`tasks.md` 6.3.5) — mientras tanto, alguien tiene que tipear el comando tras cada
desenchufe.

**A (USB tethering, con IP fija) queda como alternativa**, no se elimina: es un enlace de red de
verdad, sin depender de `adb` ni de la Depuración USB. Es la opción si preferís no dejar la
Depuración USB permanentemente activada, o para cuando el sistema deja de ser un prototipo. Eso sí:
no lo dejes con la IP que reparta el DHCP del teléfono — fijala del lado de la PC (más abajo), o
vas a terminar necesitando editar la URL desde Ajustes cada vez que cambie.

---

## Camino A · USB tethering

### En el teléfono (igual en los dos sistemas)

1. Enchufá el cable.
2. Ajustes → **Redes e Internet** → **Zona WiFi y conexión compartida** → activá **Conexión por
   USB** (el nombre exacto varía por fabricante: *USB tethering*, *Anclaje a red por USB*).
3. Si la opción está gris, hay tres causas posibles, en orden de frecuencia:
   - El cable no lleva datos (ver el aviso de abajo).
   - El teléfono está en modo "sólo carga" — bajá la notificación de USB y elegí cualquier otro.
   - **El teléfono no tiene conexión que compartir.** Varios Android se niegan a activar el
     tethering si no hay datos móviles ni WiFi, aunque el cable esté perfecto y aunque a vos
     no te interese compartir internet sino sólo tener el enlace. Prendé los datos o el WiFi
     del teléfono y probá de nuevo.

> **Ojo con el cable.** Muchos USB-C baratos son **sólo de carga**: no tienen los pines de datos.
> Con ese cable el teléfono carga pero no aparece nunca. Es la causa número uno de perder una
> tarde. Probalo primero con un cable que sepas que transfiere archivos.

### En Linux

El teléfono aparece como una interfaz nueva (`usb0`, o algo tipo `enp0s20u1`). NetworkManager suele
configurarla sola por DHCP.

```bash
# 1. Encontrar la interfaz nueva
ip -brief -4 addr show

# 2. Ver qué IP te dio el teléfono (típicamente 192.168.42.x)
ip -4 addr show usb0

# 3. Confirmar que el backend escucha en esa interfaz
ss -tlnp | grep 8000
```

Esa IP de la PC es la que va en la app.

**Firewall.** Si usás `ufw`, abrile el puerto sólo en esa interfaz — no en todas:

```bash
sudo ufw allow in on usb0 to any port 8000 proto tcp
```

**La ruta por defecto.** Al compartir conexión, el teléfono también ofrece internet, y Linux puede
mandarle *todo* el tráfico de la PC. Si de golpe la PC navega raro o lento, es eso:

```bash
ip route          # mirá si la default se fue por usb0
```

Se corrige subiéndole la métrica a esa ruta, o marcando la conexión como *sólo para recursos de
esta red* en NetworkManager.

### En Windows

Windows 10 y 11 traen el driver RNDIS de fábrica: el teléfono aparece solo en **Conexiones de red**
como un adaptador Ethernet nuevo (*"Remote NDIS based Internet Sharing Device"*).

```powershell
# 1. Ver los adaptadores y encontrar el nuevo
Get-NetIPAddress -AddressFamily IPv4 | Format-Table InterfaceAlias, IPAddress

# 2. Confirmar que el backend escucha
netstat -an | findstr :8000
```

**El firewall es EL problema en Windows, y se lleva puesta la tarde de todo el mundo.**

Cuando aparece una red nueva, Windows la clasifica como **Pública** y **bloquea todo lo entrante**.
El backend va a estar corriendo perfecto y el teléfono no lo va a alcanzar, sin ningún mensaje de
error útil de por medio. Dos formas de arreglarlo, en PowerShell **como administrador**:

```powershell
# Opción 1 — marcar esa red como privada (reemplazá el alias por el tuyo)
Set-NetConnectionProfile -InterfaceAlias "Ethernet 5" -NetworkCategory Private

# Opción 2 — regla explícita sólo para el puerto del backend (más quirúrgico)
New-NetFirewallRule -DisplayName "Yerbanalytics backend 8000" `
  -Direction Inbound -LocalPort 8000 -Protocol TCP -Action Allow
```

Preferí la **opción 2**: abre exactamente lo que hace falta y no cambia la postura de seguridad de
toda la interfaz.

---

## Camino B · `adb reverse`

Es el camino recomendado para la defensa (ver más arriba). Tiene la gracia de que **la URL es
fija**.

```bash
# En el teléfono: Opciones de desarrollador → Depuración por USB (activada)

adb devices                          # tiene que listar el teléfono
adb reverse tcp:8000 tcp:8000        # localhost:8000 del teléfono -> 8000 de la PC
```

En la app tipeás `http://localhost:8000` y listo. No importa qué IP tenga la PC ni qué haga la red:
el teléfono le habla a su propio `localhost` y `adb` se encarga del resto.

**El precio: hay que dejar la Depuración USB activada de forma permanente**, y volver a correr
`adb reverse` cada vez que se desenchufa el cable o se reinicia el demonio. Verificado
(`tasks.md` §6.3.3): la detección tarda hasta 60 s de silencio (el backend no da el dispositivo por
caído antes de eso), y la recuperación es de unos 5 s con un único comando **desde la PC, sin tocar
el teléfono** — `adbd` vuelve solo al reconectar el cable. A diferencia del tethering (§6.2.6), acá
nadie tiene que ir físicamente al teléfono.

**Y se cae en silencio.** Cuando el túnel muere, la app hace exactamente lo correcto: reintenta
para siempre, con backoff topeado en 60 segundos. No hay error, no hay estado terminal, no hay nada
en pantalla que grite hasta que el backend marca al dispositivo `intermitente`. El síntoma es el
peor de todos si nadie está mirando el panel — por eso conviene una regla `udev` que relance
`adb reverse` sola al enchufar (pendiente, `tasks.md` 6.3.5): hasta que exista, alguien tiene que
tipear el comando igual.

---

## Qué URL va en la app

La URL del backend se tipea **en la pantalla de vinculación**, junto con el código que emite el
simulador. No está compilada en la APK.

| Transporte | URL |
|---|---|
| WiFi (lo de siempre) | `http://<IP de la PC en la LAN>:8000` |
| USB tethering | `http://<IP de la PC en el link USB>:8000` |
| `adb reverse` | `http://localhost:8000` |

Nunca `localhost` en los dos primeros casos: para el teléfono, `localhost` es el teléfono.

### La URL se puede cambiar desde Ajustes, sin volver a vincular

La app tiene una pantalla de Ajustes (⚙ en la esquina de la pantalla de operación) que edita
**sólo la URL del backend**, conservando `dispositivoId` y `refreshToken`. Al guardar, el servicio
reconstruye el cliente HTTP y el canal de órdenes contra la URL nueva, sin recrearse ni perder
colas ni el visor. Está verificado contra hardware real (`tasks.md` §6.3.1): se cambió la URL de un
`CAM-002` ya vinculado —de la IP del tethering a `http://localhost:8000`— sin código nuevo del
simulador y sin dejar ningún dispositivo fantasma en el backend.

Eso resuelve el problema de fondo: si la IP de la PC cambia, **no hace falta re-vincular**, alcanza
con entrar a Ajustes y tipear la URL nueva. `ValidadorUrl` la valida (esquema, host, puerto) antes
de guardarla.

Dicho esto, el procedimiento sigue prefiriendo una URL que no se mueva de entrada —Ajustes es la
salida cuando algo cambió, no el plan de puesta en marcha—, porque nadie quiere depender de entrar
a la app cada vez que la red cambia. Por eso conviene elegir uno de estos dos caminos, que fijan la
URL desde el arranque:

1. **`adb reverse`** (recomendado para la defensa, ver arriba), donde la URL es
   `http://localhost:8000` **por construcción** y no depende de ninguna IP.
2. **IP fija del lado de la PC en la interfaz USB** (ver abajo), si preferís no depender de `adb`.

**Borrar los datos de la app y re-vincular queda como último recurso**, sólo si Ajustes no está
disponible o la credencial quedó inválida (por ejemplo, tras un 401 al renovar el token, que el
propio dispositivo detecta y borra solo). Cuando haga falta, el costo es el de siempre: un código
de vinculación nuevo desde el simulador, y dar de baja el dispositivo fantasma que queda en el
panel del backend.

### Fijar la IP de la PC en el enlace USB

Android hace de servidor DHCP y suele repartir en `192.168.42.0/24` (el teléfono se queda con
`.129`), así que en la práctica la IP se repite. *Suele*. Para que no dependa de la suerte,
fijala:

```bash
# Linux — con NetworkManager, sobre la conexión de esa interfaz
nmcli connection modify "<nombre-de-la-conexion>" \
  ipv4.method manual ipv4.addresses 192.168.42.10/24 ipv4.gateway 192.168.42.129
```

```powershell
# Windows — sobre el adaptador RNDIS
New-NetIPAddress -InterfaceAlias "Ethernet 5" `
  -IPAddress 192.168.42.10 -PrefixLength 24
```

Elegí una dirección dentro de la subred que reparte el teléfono y **fuera** del rango que asigna
por DHCP. Verificá antes que esa subred no choque con ninguna otra red de la máquina — en
particular con los bridges de Docker (`172.x` habitualmente) o con libvirt (`192.168.122.x`).

---

## Diagnóstico de fallas

| Síntoma | Causa más probable |
|---|---|
| El teléfono no aparece en `lsusb` / `adb devices` | Cable **sólo de carga**, o el teléfono en modo sólo carga |
| La opción de tethering está gris | Cable sin datos, modo sólo carga, o **el teléfono no tiene conexión que compartir** |
| Con `adb reverse`: todo parece bien y no pasa nada | El túnel murió. La app reintenta en silencio — relanzá `adb reverse` |
| Aparece la interfaz pero sin IP | DHCP del teléfono no arrancó — desactivá y reactivá el tethering |
| El teléfono no alcanza el backend, y en la PC `curl localhost:8000` sí anda | **Firewall** (Windows, casi seguro) o el backend escuchando sólo en loopback |
| Andaba y dejó de andar al reenchufar | La IP cambió, o el tethering se apagó solo al desconectar |
| La PC pierde internet al conectar el teléfono | El tethering le robó la ruta por defecto |

Regla general para separar aguas: si `curl http://<IP>:8000/api/nursery` **desde la propia PC
usando la IP del link USB** (no `localhost`) responde, el backend está bien y el problema es de
red o de firewall. Si no responde ni desde la PC, el problema es del backend.

---

## Límites conocidos

- **USB no escala al vivero real.** Seiscientos sectores no se cablean uno por uno a la PC. Para
  la maqueta y la demo es la decisión correcta; para producción se vuelve a red. Que no sorprenda
  el día de la defensa.
- **Un cable, un teléfono.** El tethering da un enlace punto a punto. Si algún día hay más de un
  dispositivo de captura, vuelve WiFi o entra un switch.
- **Largo del cable.** USB 2.0 pasivo llega a ~5 m (~4 m si es C-a-C). Más que eso necesita una
  extensión **activa**. El ancho de banda no es problema ni de cerca: un JPEG de pocos MB sobre
  USB 2.0 viaja en milisegundos.
- **Energía y datos son dos problemas distintos.** Si algún día el tramo se estira más de lo que
  aguanta el USB, se pueden separar: alimentación por un cable grueso (ahí el problema es caída de
  tensión, o sea calibre) y datos por WiFi. La app no se entera: se le cambia la URL y nada más.
