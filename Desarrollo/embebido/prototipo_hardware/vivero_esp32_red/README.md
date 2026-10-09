# `vivero_esp32_red` — el riel de la cámara por MQTT

Copia de `../vivero_esp32/` (que no se toca) con WiFi y MQTT. Hace lo mismo que el original por
monitor serie y, además, responde al backend por MQTT:

| Tópico | Dirección | Qué |
|---|---|---|
| `nursery/rail/command` / `nursery/rail/event` | backend → ESP32 / ESP32 → backend | Riel: `IR_A`, `HOME` |
| `nursery/zone/{NODO_ZONA_ID}/sector/{NODO_SECTOR_ID}/command` / `.../ack` | backend → ESP32 / ESP32 → backend | Válvula (`valve ON`/`OFF`, la bomba) y mediasombra (`shade SET` 0 o 100) |
| `nursery/zone/{NODO_ZONA_ID}/command` | backend → ESP32 | `LEER_AHORA`: responde con la telemetría de siempre, sin ACK |

- Contrato completo: `openspec/changes/add-pasada-riel/design.md` §1 (riel),
  `openspec/changes/add-secuencias-demo-expo/design.md` §1 (actuadores y zona) y
  `Desarrollo/embebido/comun/contrato.h` (fuente de verdad).
- Los comandos por serie (`bomba on`, `enrollar`…) siguen andando igual.

> **Estado.** Compila y corre en un ESP32 real: se compiló y flasheó con `arduino-cli` 1.5.2, core
> `esp32:esp32` **3.3.12**, ArduinoJson **7.4.3** y PubSubClient **2.8** (FQBN `esp32:esp32:esp32`, sin
> errores; 927 KB, 70 % del espacio). Se probó con el riel real y el backend: una pasada completa
> desde la sección Demo Expo (sector 1 ≈ 21,5 s, foto, sector 2 ≈ 22 s, foto). Hay **dos problemas
> conocidos de hardware** (§6.1 y §6.2): el final de carrera de home y la fuente del motor.
>
> **Bomba, mediasombra y "leer ahora" por MQTT** (cambio `add-secuencias-demo-expo`): compilan sin
> warnings propios (con `LECTURA_SENSORES_HABILITADA` en 0 y en 1), pero **no se probaron con
> hardware**. Se prueban a partir del 10/10 con la §5.b.

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
   | `NODO_ZONA_ID` | Zona del stand, igual que en la topología del backend (ej. `"MZ-1"`). **Obligatoria**: sin ella no compila |
   | `NODO_SECTOR_ID` | Sector cuyos actuadores son la bomba y la mediasombra (ej. `"MZ-1-001"`). **Obligatoria** |
   | `BOMBA_CAUDAL_PWM` | PWM (0-255) de la bomba con la válvula abierta. Default 200 |
   | `LECTURA_SENSORES_HABILITADA` | `0` (default): "leer ahora" sólo loguea y no publica. Poner `1` recién al completar `leer_sensores()` |

   La PC y el ESP32 tienen que estar en **la misma red**. Detalle de red y broker:
   `docs-motor-reglas-e-integracion/conectar-esp32.md`.

## 3. Compilar y subir

**a) Arduino IDE.**

1. Abrir `vivero_esp32_red/vivero_esp32_red.ino` (Arduino exige que la carpeta se llame igual que el `.ino`).
2. *Herramientas*: Placa **ESP32 Dev Module**; el resto por defecto (Upload Speed 921600, Flash
   Frequency 80 MHz, Partition Scheme "Default 4MB with spiffs"). Puerto: el `/dev/ttyUSB0` o `COM` del ESP32.
3. **Verificar (✓)**, después **Subir (→)**. Si se queda en `Connecting.....`, mantener apretado
   **BOOT** hasta que empiece a escribir.
4. Abrir el **Monitor serie a 115200** con fin de línea **"Nueva línea"** (los comandos se leen hasta `\n`).

**b) `arduino-cli`** (lo que se usó para la primera puesta en marcha; desde esta carpeta):

```bash
arduino-cli config add board_manager.additional_urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core update-index
arduino-cli core install esp32:esp32
arduino-cli lib install "ArduinoJson@7.4.3" "PubSubClient@2.8"
arduino-cli compile --fqbn esp32:esp32:esp32 .
arduino-cli upload -p /dev/ttyACM0 --fqbn esp32:esp32:esp32 .
```

El puerto puede ser `/dev/ttyUSB0` o `/dev/ttyACM0` según el chip USB-serie de la placa (`arduino-cli
board list`). En Linux tu usuario necesita permiso sobre el puerto: grupo `dialout` (`sudo usermod -aG
dialout $USER` y volver a entrar) o, para salir del paso, `sudo chmod a+rw /dev/ttyACM0`.

> **No abras el puerto serie con herramientas genéricas en Linux.** En la PC de desarrollo (chip CH9102,
> `/dev/ttyACM0`), abrirlo con `cat`/`stty`, un script con `termios` o `arduino-cli monitor` sin TTY
> **reinicia el ESP32 y lo deja sin arrancar** (no muestra nada). Para ver el log usá el **Monitor serie
> del Arduino IDE**. Si ya pasó, se recupera con un hard reset desde la herramienta de flasheo:
> `esptool --chip esp32 -p /dev/ttyACM0 --after hard_reset chip_id`.

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
[mqtt] Suscripto a nursery/zone/MZ-1/sector/MZ-1-001/command y nursery/zone/MZ-1/command (QoS 1)
```

LED azul de la placa (GPIO 2): parpadeo **rápido** = sin WiFi · **lento** = WiFi sin broker ·
**fijo** = conectado al broker.

Al recibir un comando:

```
[cmd] Recibido t-1: IR_A 1
[mqtt] Evento publicado: {"commandId":"t-1","status":"ACEPTADO","posicion":null,"pasos":0}
[riel] IR_A 1: de 0 a 21000 pasos
[riel] Llegó a la posición 1
[mqtt] Evento publicado: {"commandId":"t-1","status":"LLEGO","posicion":1,"pasos":21000}
```

Comandos por serie: los de siempre (`home`, `mover N`, `rutina`, `enrollar`, `desenrollar`,
`parar motor`, `bomba on`, `bomba off`, `estado`) más **`red`**, que imprime IP, RSSI, estado del
broker y el último `commandId`.

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

### 5.b Bomba, mediasombra y "leer ahora"

Con el ESP32 conectado y **sin backend** (si el backend corre, él también publica en estos tópicos).
Asumiendo `NODO_ZONA_ID "MZ-1"` y `NODO_SECTOR_ID "MZ-1-001"`.

**Terminal 1 — ver comandos, ACK y telemetría:**

```bash
docker compose exec mosquitto mosquitto_sub -t 'nursery/#' -v
```

**Terminal 2:**

```bash
# Bomba 5 s → ACK SUCCESS {"tipo":"ok","durationSec":5}; se apaga sola a los 5 s
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/zone/MZ-1/sector/MZ-1-001/command \
  -m '{"commandId":"v-1","actuador":"valve","accion":"ON","parametros":{"durationSec":5}}'

# Cerrar antes de tiempo (siempre apaga; ACK ok si estaba abierta, sin_cambio si no)
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/zone/MZ-1/sector/MZ-1-001/command \
  -m '{"commandId":"v-2","actuador":"valve","accion":"OFF","parametros":{}}'

# Mediasombra: 0 = desenrollada, 100 = enrollada; el ACK llega al tocar el final de carrera
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/zone/MZ-1/sector/MZ-1-001/command \
  -m '{"commandId":"s-1","actuador":"shade","accion":"SET","parametros":{"targetPct":0}}'
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/zone/MZ-1/sector/MZ-1-001/command \
  -m '{"commandId":"s-2","actuador":"shade","accion":"SET","parametros":{"targetPct":100}}'

# Leer ahora → sin sensores (LECTURA_SENSORES_HABILITADA 0) sólo se ve el log "[leer] sin sensores..."
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/zone/MZ-1/command \
  -m '{"commandId":"l-1","accion":"LEER_AHORA","parametros":{}}'
```

Lo mismo que con el riel: un `commandId` nuevo por prueba (el firmware recuerda los últimos 4 y al
repetido le republica su ACK). Un `targetPct` distinto de 0 o 100, o un `durationSec` fuera de rango,
da ACK `ERROR` (`comando_invalido` / `duracion_invalida`); `pump` o cualquier otro actuador, `actuador_desconocido`.

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
| `#error` por `NODO_ZONA_ID` / `NODO_SECTOR_ID` | `config.h` viejo, de antes de las secuencias | Agregar las constantes nuevas desde `config.example.h` |
| `#error "Hace falta ArduinoJson 7.x"` o errores con `JsonDocument` | ArduinoJson 6 instalada | Actualizarla a 7.x desde el gestor de librerías |
| `'ledcAttach' was not declared` | Core ESP32 2.x | Actualizar "esp32 by Espressif Systems" a 3.x |
| LED parpadeando rápido; `[wifi] Sin conexión (estado 1)` | SSID no encontrado: red de 5 GHz, nombre mal escrito o lejos del AP | Usar una red de 2,4 GHz (hotspot del celular en "2,4 GHz" o "compatibilidad") y revisar mayúsculas |
| `[wifi] Sin conexión (estado 4)` o `(estado 6)` | Clave incorrecta | Revisar `WIFI_PASSWORD` |
| LED parpadeando lento; `[mqtt] Falló: no se pudo abrir la conexión TCP ... (-2)` | No llega al broker: IP mal, broker apagado, firewall, o red que aísla clientes (típico de redes de facultad/hotel) | Revisar `MQTT_HOST` con `ip a`; `docker compose ps`; desde otra máquina `mosquitto_sub -h <ip> -t '#'`; permitir el 1883 en el firewall; probar con el hotspot del celular |
| Conecta y se desconecta cada tanto | Dos placas con el mismo clientId, o señal débil | El clientId lleva la MAC, así que no debería repetirse; mirar el RSSI con `red` (peor que -80 dBm es poco) |
| `Brownout detector was triggered` y se reinicia, sobre todo al moverse el motor con WiFi | Caída de tensión: el WiFi pide picos de ~300 mA y el motor tira la fuente abajo | Alimentar el ESP32 aparte del DRV8825 (o del USB de la PC), masas unidas; capacitor de 470–1000 µF en la entrada del driver y uno de 100 µF entre 3V3 y GND del ESP32; cable USB corto y bueno |
| Se reinicia solo sin "Brownout" (`Guru Meditation`, `rst:0x...`) | Copiar el log completo | Es lo primero que hay que mandar para diagnosticar |
| `ERROR: Home no encontrado` o `HOME_NO_ENCONTRADO` | Final de carrera de home sin pull-up, mal cableado, o `DIR_HACIA_HOME` invertido | `estado` por serie y apretar el final a mano: tiene que pasar de `libre` a `ACTIVADO`. GPIO 34/35 **necesitan pull-up externo de 10k** a 3,3 V. Si el carro se va para el otro lado, cambiar `DIR_HACIA_HOME`. Si en cambio **termina al instante sin moverse**, ver [6.1](#61-final-de-carrera-de-home-conocido) |
| `ERROR: Se activó el final de carrera opuesto durante el homing` | Dirección invertida | Cambiar `DIR_HACIA_HOME` de `HIGH` a `LOW` |
| `FIN_DE_CARRERA` al ir a la posición 2 | `POSICION_SECTOR_2` (42000) más largo que el riel | Bajar `POSICION_SECTOR_1/2` en el `.ino` |
| El backend dice `RIEL_SIN_RESPUESTA` | El ESP32 no está suscripto (mirar el log `[mqtt] Suscripto`) o el backend apunta a otro broker | `red` por serie; `mosquitto_sub -t 'nursery/rail/#' -v` para ver si el comando llega al broker |

### 6.1 Final de carrera de home (conocido)

En el riel real, el final de carrera de **HOME (GPIO 34)** lee "activado" aunque el carro esté lejos
(GPIO 34 no tiene pull-up interno y el sketch lo declara `ENDSTOP_RIEL_NC true`, línea 70). Es la
lógica original de `nema_homing()`: si el endstop marca activado, da por hecho que ya está en home
(`vivero_esp32_red.ino:464`). Consecuencias:

- `HOME`, el homing de arranque y cualquier retroceso **terminan al instante sin mover el carro**
  (`LLEGO` con `pasos: 0` en ~0,2 s).
- Tras una pasada el carro queda físicamente en el sector 2. **Entre pasadas hay que llevarlo a mano
  a home**; si no, la pasada siguiente lo empuja 42.000 pasos más allá.

Es un problema del cableado/contacto, no del agregado de red. Pendiente: reparar el final de carrera
(pull-up externo de 10k a 3,3 V y revisar el tipo de contacto NO/NC).

### 6.2 Limitación: la fuente del motor

El firmware no puede saber si el motor tiene alimentación. Con la fuente del DRV8825 **apagada** y el
ESP32 por USB, una pasada "funciona": responde `LLEGO` en el tiempo esperado sin que nada se mueva.
Un `LLEGO` no prueba que el carro se movió.

### Pines y WiFi

Los pines son los del original; no se cambiaron.

- **ADC2** (GPIO 0, 2, 4, 12–15, 25–27) no puede usar `analogRead` con WiFi encendido. Acá el 2, 14,
  15, 25, 26 y 27 se usan como **digitales o PWM**, que no se ven afectados. Los finales de carrera
  del riel (34/35) son ADC1 y se leen como digitales: tampoco.
- **GPIO 15 (SLEEP)** es pin de *strapping*: sólo decide si salen logs del bootloader al arrancar. No
  molesta.
- **GPIO 2 (LED)** es de *strapping*: tiene que estar en bajo o libre al flashear. El LED de la placa
  no molesta; no colgarle nada más.
- **GPIO 34/35** son sólo entrada, sin pull-up interno: necesitan el pull-up externo de 10k.

## 7. Cómo está hecho (para quien lo toque)

- **Movimiento bloqueante cooperativo.** Es el mismo bucle de pasos del original; cada 200 pasos
  (~200 ms) llama a `red_atender()`, que hace **sólo** `mqtt.loop()` (keepalive y recepción) y el LED.
  **Nunca reconecta mientras se mueve**: `connect()` puede bloquear segundos. La reconexión
  (cada 3 s al broker; el WiFi se reconecta solo y se fuerza cada 15 s) la hace `loop()` con el riel quieto.
- **El callback MQTT sólo copia** el comando a un slot (`entrante_*`). No mueve ni publica: PubSubClient
  reusa su buffer y publicar adentro del callback corrompe el mensaje.
- **Actuadores (válvula y mediasombra).** Reusan el mismo patrón: el callback copia a un slot
  (`act_entrante`), el ACK se arma fuera del callback y, si no hay red, queda pendiente hasta reconectar.
  La **válvula** es el driver de la bomba (L298N canal B) con PWM `BOMBA_CAUDAL_PWM`; `valvula_vigilar()`
  la apaga al vencer `durationSec`, también durante los movimientos largos del riel. La **mediasombra**
  sólo acepta `targetPct` 0 o 100; un comando nuevo la interrumpe (`reemplazado`) y un movimiento que
  no llega al final de carrera en 30 s termina en `falla_mecanica`. Los ACK recuerdan los últimos 4
  `commandId` de actuadores (anillo propio, distinto del del riel).
- **Hueco de sensores.** `leer_sensores(JsonObject)` está vacía a propósito: todavía no se sabe qué
  sensores lleva el stand (`docs-motor-reglas-e-integracion/analisis-demo-expo-vs-vivero.md` §9.4). Para
  completarla, cargar las métricas con las claves y unidades de `contrato.h` (`uv` es % de un LDR; `ce` va
  en µS/cm), devolver `true` y poner `LECTURA_SENSORES_HABILITADA 1`. La telemetría sale con `signal` = RSSI
  y `timestamp` en segundos.
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
