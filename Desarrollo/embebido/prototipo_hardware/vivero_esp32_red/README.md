# `vivero_esp32_red` — el riel de la cámara por MQTT

Copia de `../vivero_esp32/` (que no se toca) con WiFi y MQTT. Hace lo mismo que el original por
monitor serie y, además, mueve el **riel** cuando el backend se lo pide por MQTT. Bomba y mediasombra
siguen siendo **sólo por serie**.

- Escucha `nursery/rail/command` y responde en `nursery/rail/event`.
- Contrato completo: `openspec/changes/add-pasada-riel/design.md` §1 y la sección "Riel" de
  `Desarrollo/embebido/comun/contrato.h` (fuente de verdad).

> **Ojo: este sketch nunca se compiló para el ESP32.** En la PC donde se escribió no hay toolchain
> de Arduino. Sí se compiló con `g++` en la PC contra ArduinoJson 7.4.2 y el `PubSubClient.h` 2.8
> reales (sin warnings con `-Wall -Wextra`), con el core de Arduino y WiFi simulados, y se corrió
> contra un riel y un broker simulados: camino feliz, comando repetido, inválidos, reemplazo
> durante el movimiento, final de carrera, `IR_A` sin referencia, caída de red a mitad de un tramo
> y homing sin home. Lo que no se pudo probar es el core real del ESP32 ni el hardware. El primer
> "Verificar" lo hacés vos; si falla, mirá [Si no compila](#si-no-compila).

---

## 1. Instalar (una sola vez)

Arduino IDE 2.x.

1. **Placa.** *Herramientas → Placa → Gestor de placas*, buscar `esp32` e instalar
   **"esp32 by Espressif Systems"**, versión **3.x** (el sketch usa `ledcAttach`, que no existe en la 2.x).
2. **Librerías.** *Herramientas → Gestionar bibliotecas* e instalar:

   | Buscar | Instalar | Autor | Versión |
   |---|---|---|---|
   | `PubSubClient` | **PubSubClient** | Nick O'Leary | 2.8.x |
   | `ArduinoJson` | **ArduinoJson** | Benoit Blanchon | **7.x** (no la 6) |

   Si con `PubSubClient` aparecen varias, es la de **Nick O'Leary** (no "PubSubClient3", no
   "MQTTPubSubClient"). Si ArduinoJson es la 6, el sketch frena con un `#error` que lo dice.

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

   La PC y el ESP32 tienen que estar en **la misma red**. Detalle de red y broker:
   `docs-motor-reglas-e-integracion/conectar-esp32.md`.

## 3. Compilar y subir

1. Abrir `vivero_esp32_red/vivero_esp32_red.ino` (Arduino exige que la carpeta se llame igual que el `.ino`).
2. *Herramientas*: Placa **ESP32 Dev Module**; el resto por defecto (Upload Speed 921600, Flash
   Frequency 80 MHz, Partition Scheme "Default 4MB with spiffs"). Puerto: el `/dev/ttyUSB0` o `COM` del ESP32.
3. **Verificar (✓)**, después **Subir (→)**. Si se queda en `Connecting.....`, mantener apretado
   **BOOT** hasta que empiece a escribir.
4. Abrir el **Monitor serie a 115200** con fin de línea **"Nueva línea"** (los comandos se leen hasta `\n`).

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
| `#error "Hace falta ArduinoJson 7.x"` o errores con `JsonDocument` | ArduinoJson 6 instalada | Actualizarla a 7.x desde el gestor de librerías |
| `'ledcAttach' was not declared` | Core ESP32 2.x | Actualizar "esp32 by Espressif Systems" a 3.x |
| LED parpadeando rápido; `[wifi] Sin conexión (estado 1)` | SSID no encontrado: red de 5 GHz, nombre mal escrito o lejos del AP | Usar una red de 2,4 GHz (hotspot del celular en "2,4 GHz" o "compatibilidad") y revisar mayúsculas |
| `[wifi] Sin conexión (estado 4)` o `(estado 6)` | Clave incorrecta | Revisar `WIFI_PASSWORD` |
| LED parpadeando lento; `[mqtt] Falló: no se pudo abrir la conexión TCP ... (-2)` | No llega al broker: IP mal, broker apagado, firewall, o red que aísla clientes (típico de redes de facultad/hotel) | Revisar `MQTT_HOST` con `ip a`; `docker compose ps`; desde otra máquina `mosquitto_sub -h <ip> -t '#'`; permitir el 1883 en el firewall; probar con el hotspot del celular |
| Conecta y se desconecta cada tanto | Dos placas con el mismo clientId, o señal débil | El clientId lleva la MAC, así que no debería repetirse; mirar el RSSI con `red` (peor que -80 dBm es poco) |
| `Brownout detector was triggered` y se reinicia, sobre todo al moverse el motor con WiFi | Caída de tensión: el WiFi pide picos de ~300 mA y el motor tira la fuente abajo | Alimentar el ESP32 aparte del DRV8825 (o del USB de la PC), masas unidas; capacitor de 470–1000 µF en la entrada del driver y uno de 100 µF entre 3V3 y GND del ESP32; cable USB corto y bueno |
| Se reinicia solo sin "Brownout" (`Guru Meditation`, `rst:0x...`) | Copiar el log completo | Es lo primero que hay que mandar para diagnosticar |
| `ERROR: Home no encontrado` o `HOME_NO_ENCONTRADO` | Final de carrera de home sin pull-up, mal cableado, o `DIR_HACIA_HOME` invertido | `estado` por serie y apretar el final a mano: tiene que pasar de `libre` a `ACTIVADO`. GPIO 34/35 **necesitan pull-up externo de 10k** a 3,3 V. Si el carro se va para el otro lado, cambiar `DIR_HACIA_HOME` |
| `ERROR: Se activó el final de carrera opuesto durante el homing` | Dirección invertida | Cambiar `DIR_HACIA_HOME` de `HIGH` a `LOW` |
| `FIN_DE_CARRERA` al ir a la posición 2 | `POSICION_SECTOR_2` (42000) más largo que el riel | Bajar `POSICION_SECTOR_1/2` en el `.ino` |
| El backend dice `RIEL_SIN_RESPUESTA` | El ESP32 no está suscripto (mirar el log `[mqtt] Suscripto`) o el backend apunta a otro broker | `red` por serie; `mosquitto_sub -t 'nursery/rail/#' -v` para ver si el comando llega al broker |

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

ArduinoJson y PubSubClient ya se compilaron con sus headers reales; lo que queda sin verificar es
lo que depende del **core del ESP32**:

1. **Versión de las librerías.** Si ArduinoJson no es 7.x o PubSubClient es anterior a 2.8
   (`setBufferSize`, `setKeepAlive`, `setSocketTimeout`), no compila: actualizarlas.
2. **`ESP.getEfuseMac()`** (arma el clientId en `red_iniciar()`). Si no compilara, reemplazar esas
   líneas por `strlcpy(mqtt_client_id, "riel-esp32-1", sizeof(mqtt_client_id));`.
3. **`WiFi.setAutoReconnect(true)`** (en `red_iniciar()`). Si no compilara, borrar esa línea: el
   sketch igual reintenta el WiFi cada 15 s.
4. Si el error es en `ledcAttach`, es el core 2.x (ver tabla de arriba): el original tiene lo mismo.
