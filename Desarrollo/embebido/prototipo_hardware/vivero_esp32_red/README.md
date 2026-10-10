# `vivero_esp32_red` — el riel de la cámara por MQTT

Copia de `../vivero_esp32/` (que no se toca) con WiFi y MQTT. Hace lo mismo que el original por
monitor serie y, además, mueve el **riel** cuando el backend se lo pide por MQTT. Riego (módulo de
4 relés) y mediasombra son **sólo por serie**.

- Placa: **ESP32-S3 DevKitC-1 N16R8**. Los pines están en `config.h` (ver §2).
- Escucha `nursery/rail/command` y responde en `nursery/rail/event`.
- Contrato completo: `openspec/changes/add-pasada-riel/design.md` §1 y la sección "Riel" de
  `Desarrollo/embebido/comun/contrato.h` (fuente de verdad).
- Riego: 2 minibombas (agua y insumo) y 2 electroválvulas (aspersor del sector 1 y del 2) por un
  módulo de 4 relés activo en LOW. La bomba ya no va por el L298N, que queda sólo para la mediasombra.

> **Estado.** La versión para ESP32 clásico (DevKit v1) se flasheó y probó con el riel real y el
> backend el 03/10/2026: una pasada completa desde la sección Demo Expo (sector 1 ≈ 21,5 s, foto,
> sector 2 ≈ 22 s, foto). La versión actual (ESP32-S3 + relés) **compila** con core `esp32:esp32`
> **3.3.10**, ArduinoJson **7.4.3** y PubSubClient **2.8** (FQBN de §3.b, sin advertencias del
> sketch; 924 KB, 70 %), pero **todavía no se probó en la placa**. Problemas conocidos de hardware:
> §6.1 (final de carrera de home, probablemente resuelto con el pull-up interno del S3) y §6.2
> (fuente del motor).

---

## 1. Instalar (una sola vez)

Con **Arduino IDE 2.x** o con **`arduino-cli`** (ver [3.b](#3-compilar-y-subir)). Pasos para el IDE:

1. **Placa.** *Herramientas → Placa → Gestor de placas*, buscar `esp32` e instalar
   **"esp32 by Espressif Systems"**, versión **3.x** (el sketch usa `ledcAttach`, que no existe en la 2.x).
2. **Librerías.** *Herramientas → Gestionar bibliotecas* e instalar:

   | Buscar | Instalar | Autor | Versión |
   |---|---|---|---|
   | `PubSubClient` | **PubSubClient** | Nick O'Leary | 2.8.x |
   | `ArduinoJson` | **ArduinoJson** | Benoit Blanchon | **7.x** (no la 6) |

   Si con `PubSubClient` aparecen varias, es la de **Nick O'Leary** (no "PubSubClient3", no
   "MQTTPubSubClient"). Si ArduinoJson es la 6, el sketch frena con un `#error` que lo dice.

   Versiones con las que se compiló y probó: core 3.3.12, ArduinoJson 7.4.3, PubSubClient 2.8.

## 2. Configurar

1. En esta carpeta, copiar `config.example.h` como **`config.h`** (mismo directorio que el `.ino`).
   `config.h` no se sube a Git.
2. Completar:

   | Constante | Qué poner |
   |---|---|
   | `WIFI_SSID`, `WIFI_PASSWORD` | Red WiFi de **2,4 GHz** (el ESP32 no ve las de 5 GHz). Sirve el hotspot de un celular |
   | `MQTT_HOST` | **IP en la LAN de la PC donde corre el broker** (la del backend). En Linux: `ip a`. **No** `localhost` |
   | `MQTT_PORT` | `1883` |
   | `MQTT_CLIENT_ID_BASE` | Dejar `riel-esp32` |
   | Bloque `PINES` | Dejarlo como está salvo que cambies el cableado. `RELE_ACTIVO_EN_LOW`, `FINES_CARRERA_NC` y `RIEL_DIR_HACIA_HOME` se ajustan al hardware |

   La PC y el ESP32 tienen que estar en **la misma red**. Detalle de red y broker:
   `docs-motor-reglas-e-integracion/conectar-esp32.md`.

   Si tenés un `config.h` de antes del ESP32-S3 (sin pines), el sketch frena con un `#error`: copiá
   el bloque `PINES` de `config.example.h`.

### Cableado (ESP32-S3 DevKitC-1 N16R8)

Los pines son espejo de `Desarrollo/embebido/comun/config.example.h` (fuente de verdad del cableado).

| Componente | Señal | GPIO | Notas |
|---|---|---|---|
| Riel (DRV8825) | STEP / DIR / SLEEP | 14 / 15 / 16 | RESET del módulo fijo a 3V3 del ESP32 |
| | final de carrera home / opuesto | 21 / 47 | NC: C y NC entre el pin y GND (pull-up interno) |
| Mediasombra (L298N canal A) | IN1 / IN2 / ENA (PWM) | 39 / 40 / 41 | |
| | final enrollada / desenrollada | 42 / 2 | NC, igual que el riel |
| Relés (activos en LOW) | IN1 bomba riego / IN2 bomba insumo | 10 / 11 | Cargas en los contactos NO, fuente aparte |
| | IN3 válvula sector 1 / IN4 válvula sector 2 | 12 / 13 | Diodo en antiparalelo en cada electroválvula |
| LED de estado | RGB de la placa | `RGB_BUILTIN` | |

Los sensores (LDR 1, HC-SR04 4/5/6, RS-485 8/17/18, DHT11 9) también están en `config.h`, pero este
sketch no los lee. No usar: 0, 3, 45, 46 (strapping), 19, 20 (USB), 26–37 (flash y PSRAM octal),
43, 44 (consola), 38 y 48 (LED RGB).

## 3. Compilar y subir

**a) Arduino IDE.**

1. Abrir `vivero_esp32_red/vivero_esp32_red.ino` (Arduino exige que la carpeta se llame igual que el `.ino`).
2. *Herramientas*: Placa **ESP32S3 Dev Module**, y además:

   | Opción | Valor |
   |---|---|
   | Flash Size | **16MB (128Mb)** |
   | PSRAM | **OPI PSRAM** |
   | USB CDC On Boot | **Enabled** si conectás el cable al puerto **USB** de la placa · **Disabled** si lo conectás al puerto **COM/UART** |

   El resto por defecto. Con "USB CDC On Boot" mal puesto el sketch corre igual, pero el monitor serie
   no muestra nada. Puerto: el `COM` (Windows) o `/dev/ttyACM0`/`/dev/ttyUSB0` (Linux) del ESP32.
3. **Verificar (✓)**, después **Subir (→)**. Si se queda en `Connecting.....`, mantener apretado
   **BOOT** hasta que empiece a escribir.
4. Abrir el **Monitor serie a 115200** con fin de línea **"Nueva línea"** (los comandos se leen hasta `\n`).

**b) `arduino-cli`** (lo que se usó para la primera puesta en marcha; desde esta carpeta):

```bash
arduino-cli config add board_manager.additional_urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core update-index
arduino-cli core install esp32:esp32
arduino-cli lib install "ArduinoJson@7.4.3" "PubSubClient@2.8"
FQBN="esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,CDCOnBoot=cdc"   # CDCOnBoot=default si usás el puerto COM/UART
arduino-cli compile --fqbn "$FQBN" .
arduino-cli upload -p /dev/ttyACM0 --fqbn "$FQBN" .
```

El puerto puede ser `/dev/ttyUSB0` o `/dev/ttyACM0` según el chip USB-serie de la placa (`arduino-cli
board list`). En Linux tu usuario necesita permiso sobre el puerto: grupo `dialout` (`sudo usermod -aG
dialout $USER` y volver a entrar) o, para salir del paso, `sudo chmod a+rw /dev/ttyACM0`.

> **No abras el puerto serie con herramientas genéricas en Linux.** En la PC de desarrollo (chip CH9102,
> `/dev/ttyACM0`), abrirlo con `cat`/`stty`, un script con `termios` o `arduino-cli monitor` sin TTY
> **reinicia el ESP32 y lo deja sin arrancar** (no muestra nada). Para ver el log usá el **Monitor serie
> del Arduino IDE**. Si ya pasó, se recupera con un hard reset desde la herramienta de flasheo:
> `esptool --chip esp32s3 -p /dev/ttyACM0 --after hard_reset chip_id`. (Esto se vio con el ESP32
> clásico; con el S3 no se probó.)

## 4. Qué se ve cuando todo anda

```
Iniciando sistema vivero (con red)...
Buscando posición home...
Home encontrado. Posición reseteada a 0.        (o "Ya está en home.")
[wifi] Conectando a "mi-red"...
Sistema listo. Escribí 'red' para ver el estado de la conexión.
[wifi] Conectado. IP: 192.168.1.80  RSSI: -58 dBm
[mqtt] Conectando a 192.168.1.64:1883 como riel-esp32-XXXXXXXXXXXX...
[mqtt] Conectado al broker
[mqtt] Suscripto a nursery/rail/command (QoS 1)
```

LED RGB de la placa (en blanco): parpadeo **rápido** = sin WiFi · **lento** = WiFi sin broker ·
**fijo** = conectado al broker. En la revisión v1.1 de la DevKitC-1 el LED está en el GPIO 38 y no
prende (el core lo espera en el 48); el resto funciona igual.

Al recibir un comando:

```
[cmd] Recibido t-1: IR_A 1
[mqtt] Evento publicado: {"commandId":"t-1","status":"ACEPTADO","posicion":null,"pasos":0}
[riel] IR_A 1: de 0 a 21000 pasos
[riel] Llegó a la posición 1
[mqtt] Evento publicado: {"commandId":"t-1","status":"LLEGO","posicion":1,"pasos":21000}
```

Comandos por serie: los de siempre (`home`, `mover N`, `rutina`, `enrollar`, `desenrollar`,
`parar motor`, `estado`) más **`red`**, que imprime IP, RSSI, estado del broker y el último
`commandId`, y los de riego:

| Comando | Qué hace |
|---|---|
| `valvula 1 abrir` / `valvula 1 cerrar` | Electroválvula del sector 1 (`valvula 2 ...` para el sector 2) |
| `bomba riego on` / `bomba riego off` | Minibomba de agua |
| `bomba insumo on` / `bomba insumo off` | Minibomba de insumo |
| `regar N S` | Riega el sector N durante S segundos (1 a 1200): abre la válvula, a los 300 ms prende la bomba de riego y al terminar apaga la bomba y después cierra la válvula |
| `insumo N S` | Lo mismo con la bomba de insumo |
| `parar riego` | Apaga las bombas y cierra las válvulas |

**Interbloqueo:** una bomba no prende si no hay ninguna válvula abierta, y al cerrar la última válvula
se apagan antes las bombas. Un riego temporizado sigue contando aunque el riel o la mediasombra se
estén moviendo. `regar`/`insumo` cortan lo que hubiera antes (de a un sector por vez). `estado`
muestra válvulas, bombas y el tiempo que le queda al riego.

## 5. Probar sin backend

Con el broker levantado (`docker compose up -d mosquitto` en la raíz del repo), en la PC:

**Terminal 1 — ver lo que publica el riel:**

```bash
docker compose exec mosquitto mosquitto_sub -t 'nursery/rail/#' -v
```

**Terminal 2 — mandar comandos:**

```bash
# Ir a la posición 1 → ACEPTADO y, ~21 s después, LLEGO posicion 1
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/rail/command \
  -m '{"commandId":"t-1","actuador":"rail","accion":"IR_A","parametros":{"posicion":1}}'

# El mismo commandId otra vez → republica LLEGO sin moverse
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/rail/command \
  -m '{"commandId":"t-1","actuador":"rail","accion":"IR_A","parametros":{"posicion":1}}'

# Volver a home → LLEGO posicion 0
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/rail/command \
  -m '{"commandId":"t-2","actuador":"rail","accion":"HOME","parametros":{}}'

# Inválido → ERROR COMANDO_INVALIDO, sin moverse
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/rail/command \
  -m '{"commandId":"t-3","actuador":"rail","accion":"IR_A","parametros":{"posicion":3}}'
```

Usá un `commandId` nuevo para cada prueba nueva: el firmware ignora los que vio entre los últimos 4
(y al último terminado le republica el evento). Para cancelar un movimiento, mandá `HOME` con otro
id mientras se mueve: el viejo termina en `ERROR REEMPLAZADO` y el riel vuelve a home.

**Alternativa sin `mosquitto_pub`** (usa el paquete `mqtt` que ya está en el simulador; si falta,
`npm install` en `Desarrollo/simulador`). Manda un comando y queda mostrando los eventos (Ctrl+C para salir):

```bash
cd Desarrollo/simulador
node -e '
const mqtt = require("mqtt");
const [id, accion, pos] = process.argv.slice(1);
const c = mqtt.connect("mqtt://localhost:1883");
c.on("connect", () => {
  c.subscribe("nursery/rail/event", { qos: 1 });
  const parametros = accion === "IR_A" ? { posicion: Number(pos) } : {};
  c.publish("nursery/rail/command", JSON.stringify({ commandId: id, actuador: "rail", accion, parametros }), { qos: 1 });
});
c.on("message", (t, m) => console.log(t, m.toString()));
' t-10 IR_A 2
```

(Para `HOME`: `... ' t-11 HOME`.)

## 6. Problemas típicos

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `#error "Falta config.h..."` | No se copió la plantilla | Copiar `config.example.h` → `config.h` en esta carpeta |
| `#error "config.h sin pines..."` | `config.h` de antes del ESP32-S3 | Copiar el bloque `PINES` de `config.example.h` a tu `config.h` |
| `#error "Este sketch es para ESP32-S3..."` | Placa equivocada en el IDE | Elegir **ESP32S3 Dev Module** (§3) |
| Monitor serie vacío aunque el sketch corre | "USB CDC On Boot" no coincide con el puerto conectado | **Enabled** con el puerto USB, **Disabled** con el COM/UART (§3) |
| Una bomba no prende: `no hay ninguna válvula abierta` | Interbloqueo | Abrir antes una válvula o usar `regar N S` / `insumo N S` |
| Los relés hacen lo contrario (prenden con `off`) | El módulo es activo en HIGH | `RELE_ACTIVO_EN_LOW 0` en `config.h` |
| `#error "Hace falta ArduinoJson 7.x"` o errores con `JsonDocument` | ArduinoJson 6 instalada | Actualizarla a 7.x desde el gestor de librerías |
| `'ledcAttach' was not declared` | Core ESP32 2.x | Actualizar "esp32 by Espressif Systems" a 3.x |
| LED parpadeando rápido; `[wifi] Sin conexión (estado 1)` | SSID no encontrado: red de 5 GHz, nombre mal escrito o lejos del AP | Usar una red de 2,4 GHz (hotspot del celular en "2,4 GHz" o "compatibilidad") y revisar mayúsculas |
| `[wifi] Sin conexión (estado 4)` o `(estado 6)` | Clave incorrecta | Revisar `WIFI_PASSWORD` |
| LED parpadeando lento; `[mqtt] Falló: no se pudo abrir la conexión TCP ... (-2)` | No llega al broker: IP mal, broker apagado, firewall, o red que aísla clientes (típico de redes de facultad/hotel) | Revisar `MQTT_HOST` con `ip a`; `docker compose ps`; desde otra máquina `mosquitto_sub -h <ip> -t '#'`; permitir el 1883 en el firewall; probar con el hotspot del celular |
| Conecta y se desconecta cada tanto | Dos placas con el mismo clientId, o señal débil | El clientId lleva la MAC, así que no debería repetirse; mirar el RSSI con `red` (peor que -80 dBm es poco) |
| `Brownout detector was triggered` y se reinicia, sobre todo al moverse el motor con WiFi | Caída de tensión: el WiFi pide picos de ~300 mA y el motor tira la fuente abajo | Alimentar el ESP32 aparte del DRV8825 (o del USB de la PC), masas unidas; capacitor de 470–1000 µF en la entrada del driver y uno de 100 µF entre 3V3 y GND del ESP32; cable USB corto y bueno |
| Se reinicia solo sin "Brownout" (`Guru Meditation`, `rst:0x...`) | Copiar el log completo | Es lo primero que hay que mandar para diagnosticar |
| `ERROR: Home no encontrado` o `HOME_NO_ENCONTRADO` | Final de carrera de home mal cableado, `FINES_CARRERA_NC` que no coincide con el contacto, o `RIEL_DIR_HACIA_HOME` invertido | `estado` por serie y apretar el final a mano: tiene que pasar de `libre` a `ACTIVADO`. Si el carro se va para el otro lado, cambiar `RIEL_DIR_HACIA_HOME` en `config.h`. Si en cambio **termina al instante sin moverse**, ver [6.1](#61-final-de-carrera-de-home-conocido) |
| `ERROR: Se activó el final de carrera opuesto durante el homing` | Dirección invertida | Cambiar `RIEL_DIR_HACIA_HOME` de `HIGH` a `LOW` en `config.h` |
| `FIN_DE_CARRERA` al ir a la posición 2 | `POSICION_SECTOR_2` (42000) más largo que el riel | Bajar `POSICION_SECTOR_1/2` en el `.ino` |
| El backend dice `RIEL_SIN_RESPUESTA` | El ESP32 no está suscripto (mirar el log `[mqtt] Suscripto`) o el backend apunta a otro broker | `red` por serie; `mosquitto_sub -t 'nursery/rail/#' -v` para ver si el comando llega al broker |

### 6.1 Final de carrera de home (conocido)

Con el ESP32 clásico, el final de carrera de **HOME (GPIO 34)** leía "activado" aunque el carro
estuviera lejos. El GPIO 34 no tiene pull-up interno, el pull-up externo no estaba, y con un contacto
NC eso lee "activado". La lógica de `nema_homing()` es: si el endstop marca activado, da por hecho que
ya está en home. Consecuencias que se vieron:

- `HOME`, el homing de arranque y cualquier retroceso **terminaban al instante sin mover el carro**
  (`LLEGO` con `pasos: 0` en ~0,2 s).
- Tras una pasada el carro quedaba físicamente en el sector 2, y **entre pasadas había que llevarlo a
  mano a home**; si no, la pasada siguiente lo empujaba 42.000 pasos más allá.

En el ESP32-S3 los finales usan `INPUT_PULLUP`, así que **probablemente esté resuelto**. Para
confirmarlo: `estado` con el carro lejos de home tiene que decir `libre`. Si sigue diciendo
`ACTIVADO`, el contacto NC está abierto o hay un cable cortado: medir continuidad entre C y NC.

### 6.2 Limitación: la fuente del motor

El firmware no puede saber si el motor tiene alimentación. Con la fuente del DRV8825 **apagada** y el
ESP32 por USB, una pasada "funciona": responde `LLEGO` en el tiempo esperado sin que nada se mueva.
Un `LLEGO` no prueba que el carro se movió.

### Pines del ESP32-S3

- Todos los GPIO tienen pull-up interno (no hay pines "sólo entrada" como el 34/35 del ESP32 clásico):
  los cuatro finales de carrera usan `INPUT_PULLUP`, sin resistencia externa.
- Con WiFi encendido sólo el **ADC1** (GPIO 1–10) lee analógico. Los relés (10–13) usan pines ADC1
  como digitales, sin problema; el LDR (GPIO 1) queda en ADC1 para el firmware modular.
- El GPIO 2 (final de carrera desenrollada) no es de *strapping* en el S3 (sí lo son 0, 3, 45 y 46).
- **Relés activos en LOW:** entre que el pin pasa a salida y el sketch lo pone en "abierto" hay
  microsegundos: el relé no llega a cerrar. Antes de eso el pin está flotante y la entrada del módulo
  tiene su propio pull-up, así que tampoco cierra al arrancar.

## 7. Cómo está hecho (para quien lo toque)

- **Movimiento bloqueante cooperativo.** Es el mismo bucle de pasos del original; cada 200 pasos
  (~200 ms) llama a `atender_fondo()`: `red_atender()`, que hace **sólo** `mqtt.loop()` (keepalive y
  recepción) y el LED, y `riego_atender()`, que corta el riego temporizado al cumplirse el tiempo.
  **Nunca reconecta mientras se mueve**: `connect()` puede bloquear segundos. La reconexión
  (cada 3 s al broker; el WiFi se reconecta solo y se fuerza cada 15 s) la hace `loop()` con el riel quieto.
- **El callback MQTT sólo copia** el comando a un slot (`entrante_*`). No mueve ni publica: PubSubClient
  reusa su buffer y publicar adentro del callback corrompe el mensaje.
- **El último gana.** Si durante un movimiento llega un comando *válido* con otro `commandId`, el
  movimiento se corta, el viejo termina en `ERROR REEMPLAZADO` y se ejecuta el nuevo. Un comando
  inválido o repetido no corta el movimiento: se responde al terminar.
- **Si se cae la red durante un movimiento**, el movimiento termina igual; el evento final queda
  guardado y se publica al reconectar.
- `IR_A` sin referencia (por ejemplo, después de un homing fallido o interrumpido) hace homing primero.
- Por MQTT sólo se aceptan las posiciones 1 y 2 (nunca pasos arbitrarios). La calibración es
  `POSICION_SECTOR_1/2` en el `.ino`.
- Prototipos declarados a mano al principio y sólo con tipos primitivos (`int`, `long`, `char*`…): el
  generador de prototipos de Arduino IDE no se lleva bien con `enum`/`struct` propios en las firmas.

## Si no compila

Compiló sin errores con las versiones de §1 (incluidas `ESP.getEfuseMac()` y
`WiFi.setAutoReconnect()`). Si falla, casi siempre es la versión:

1. **Librerías.** Si ArduinoJson no es 7.x o PubSubClient es anterior a 2.8
   (`setBufferSize`, `setKeepAlive`, `setSocketTimeout`), no compila: actualizarlas.
2. **Core 2.x.** Si el error es en `ledcAttach`, es el core 2.x (ver tabla de arriba): el original
   tiene lo mismo.

## Secuencias de Demo Expo (add-secuencias-demo-expo)

Las secuencias de Demo Expo (riego, mediasombra y lectura) **corren contra el firmware modular
`nodo_combinado`** (ESP32-S3), no contra este sketch: riego (`valve ON`/`OFF`), mediasombra
(`shade SET 0/100`) y el comando de zona `LEER_AHORA` (`nursery/zone/{zona}/command`, QoS 1, respondido con la
telemetría de siempre, sin `commandId` y sin ACK) los atiende ese firmware. Ver
`Desarrollo/embebido/README.md`, `openspec/changes/add-secuencias-demo-expo/` y la sección "Comando de zona" de
`Desarrollo/embebido/comun/contrato.h`.

Este sketch queda como **banco de pruebas por serie y del riel por MQTT**: sirve para probar el hardware
suelto, pero no hace falta para la expo. Hoy no lee sensores ni atiende `LEER_AHORA` a propósito.
