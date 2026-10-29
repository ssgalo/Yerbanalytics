# Yerbanalytics — Firmware ESP32

Firmware de los nodos del vivero para **ESP32-S3 DevKitC-1 N16R8**. Hay **tres firmwares
independientes** (uno por tipo de nodo), con arquitectura **FreeRTOS** (tareas y colas). Cada uno
compila solo lo que su rol necesita. El código común (red, contrato, tipos y drivers) se comparte
entre los tres sin duplicarse.

> **Estado.** Los tres firmwares **compilan** sin advertencias propias (`arduino-cli`, core
> `esp32:esp32` 3.3.10). **Todavía no se probaron en la placa**. Con PlatformIO no se compiló:
> ver §2.

---

## 1. Los tres firmwares

| Firmware (`env` PlatformIO) | Sketch                              | Qué hace                                                 | Uso                                  |
| --------------------------- | ----------------------------------- | -------------------------------------------------------- | ------------------------------------ |
| `nodo_sensor`               | `nodo_sensor/nodo_sensor.ino`       | Solo sensado + telemetría                                | Nodo testigo por macro-zona (campo)  |
| `nodo_actuador`             | `nodo_actuador/nodo_actuador.ino`   | Solo comandos (riego, insumo, mediasombra, riel) + ack   | Nodo de actuación (campo)            |
| `nodo_combinado`            | `nodo_combinado/nodo_combinado.ino` | Sensado **+** actuación en un ESP32                      | **Prototipo**                        |

Cada sketch tiene su propio `setup()` y su propia `task_red` a medida: el nodo sensor no compila
el subsistema de actuación ni su callback de comandos, y el nodo actuador no compila el subsistema
de sensado ni el buffer offline.

### Qué compila cada uno

|                  | `comun/` | `sensado/` | `actuacion/` |
| ---------------- | :------: | :--------: | :----------: |
| `nodo_sensor`    |    ✅    |     ✅     |      —       |
| `nodo_actuador`  |    ✅    |     —      |      ✅      |
| `nodo_combinado` |    ✅    |     ✅     |      ✅      |

El reparto lo hace `build_src_filter` en `platformio.ini`; no hay que tocar nada para que cada
entorno tome solo sus carpetas.

### Tareas FreeRTOS

| Tarea        | Prioridad | Qué hace                                                                                 |
| ------------ | :-------: | ---------------------------------------------------------------------------------------- |
| `actuacion`  | 4         | Recibe comandos, deduplica y despacha. Riego y mediasombra no bloquean: los atiende cada 50 ms |
| `sensado`    | 3         | Lee una vez al arrancar y después solo ante un `LEER_AHORA` (el backend lo publica cada X s; sin timer propio) y arma la telemetría |
| `red`        | 2         | WiFi/MQTT: publica telemetría, acks, eventos del riel y errores del nodo, y drena el buffer offline |
| `riel`       | 1         | Mueve el carro de la cámara. Genera los pasos con espera activa: va última y las demás la interrumpen. Fija al núcleo 1 |

El callback MQTT **solo encola o avisa**: nunca acciona, lee ni publica (PubSubClient reusa su buffer al
publicar y corrompería el mensaje que está leyendo).

### Comando de zona `LEER_AHORA`

`nodo_combinado` y `nodo_sensor` (los que sensan) se suscriben, a QoS 1, a
`nursery/zone/{NODO_ZONA_ID}/command` (`contratoTopicComandoZona`). Con
`{"commandId":"<id>","accion":"LEER_AHORA","parametros":{}}` el callback sólo le avisa a `task_sensado`
(`task_sensado::pedirLecturaAhora()`, una notificación de FreeRTOS): la tarea corta su espera, lee y
entrega el paquete por la cola de siempre, y `task_red` lo publica con `publicarTelemetria` en el topic
de telemetría de la zona. Sin `commandId` en la respuesta y **sin ACK**; una acción desconocida o un JSON
ilegible se ignoran con una línea de log.

- **Sensing is backend-driven (pure pull).** The node reads once at boot, then blocks in
  `ulTaskNotifyTake(pdTRUE, portMAX_DELAY)` until a `LEER_AHORA` arrives. The backend publishes it to
  every zone each X seconds (`sensingPullIntervalSeconds`, 10-3600, default 60). `SENSOR_POLLING_INTERVAL_MS`
  no longer exists. Back-to-back requests merge into a single read.
- **Sin dedup por `commandId`**: `dedup_comandos` vive en `actuacion/`, que `nodo_sensor` no compila, y
  una reentrega del broker sólo cuesta una telemetría de más.
- **Sin red**, la lectura a pedido sigue el camino de cualquier otra: va al buffer offline con su
  timestamp. Es una lectura real, así que no se pierde; el backend ya habrá dado la secuencia por
  vencida por timeout, y el motor la procesa como una lectura atrasada más.
- `MAX_SUSCRIPCIONES` (`net_mqtt.cpp`) es 4: el combinado usa las 4 (2 sectores, riel y zona).

---

## 2. Compilar y flashear

Paso previo común (una sola vez): copiar la plantilla de configuración y completar credenciales,
broker e identidad (zona y sectores). Los pines ya vienen con el cableado del prototipo (§4).

```bash
cd Desarrollo/embebido
cp comun/config.example.h comun/config.h
# editar comun/config.h → WIFI_*, MQTT_HOST, NODO_ZONA_ID, NODO_SECTOR_1_ID / NODO_SECTOR_2_ID
```

### PlatformIO (vía recomendada)

`platformio.ini` usa la plataforma **pioarduino** (core Arduino-ESP32 3.x). La `espressif32`
oficial de PlatformIO quedó en el core 2.x, que no tiene `ledcAttach` y no compila este firmware.
**Esta configuración no se probó** (PlatformIO no estaba instalado cuando se escribió): si falla, la
alternativa verificada es `arduino-cli` (abajo).

```bash
pio run -e nodo_combinado -t upload   # prototipo
pio device monitor                    # logs a 115200 baud
pio run -e nodo_sensor -t upload      # testigo
pio run -e nodo_actuador -t upload    # actuación
```

> Cada `env` compila un solo sketch; nunca hay dos `setup()`/`loop()` en una build.
> `pio run` (sin `-e`) compila los tres, útil para verificar que todos cierran.

`-DARDUINO_USB_CDC_ON_BOOT=1` manda el `Serial` al puerto **USB** nativo de la placa. Si conectás
el puerto **COM/UART**, cambialo a `0` o el monitor no muestra nada.

### arduino-cli / Arduino IDE

Arduino exige que todos los fuentes estén en la carpeta del sketch, así que hay que armar una copia
plana: el `.ino` del nodo + los `.h/.cpp` de `comun/` y de las carpetas que usa (§1) + `config.h`.
Así se verificaron los tres firmwares:

```bash
FQBN="esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,CDCOnBoot=cdc,PartitionScheme=app3M_fat9M_16MB"
mkdir -p /tmp/nodo_combinado
cp nodo_combinado/nodo_combinado.ino comun/*.h comun/*.cpp sensado/* actuacion/* /tmp/nodo_combinado/
cp comun/config.h /tmp/nodo_combinado/ && rm /tmp/nodo_combinado/config.example.h
arduino-cli compile --fqbn "$FQBN" /tmp/nodo_combinado
arduino-cli upload -p <puerto> --fqbn "$FQBN" /tmp/nodo_combinado
```

En el Arduino IDE: placa **ESP32S3 Dev Module**, Flash Size **16MB**, PSRAM **OPI PSRAM**,
USB CDC On Boot según el puerto (ver arriba).

### Transporte USB (sólo para la expo)

Donde el Wi-Fi de 2,4 GHz no sirve (un salón lleno), el nodo combinado puede hablar MQTT **por el cable
USB** con un puente en la PC: `transporte_usb/` reemplaza a `comun/net_wifi.cpp` y `comun/net_mqtt.cpp`
con la misma interfaz, y el backend no se entera. Se graba con `puente-usb/compilar-nodo-usb.sh --subir`
(o el entorno `nodo_combinado_usb` de PlatformIO) y se vuelve al Wi-Fi grabando `nodo_combinado`. Guía:
[`puente-usb/README.md`](puente-usb/README.md).

---

## 3. Configuración (`comun/config.h`)

Un único archivo, compartido por los tres firmwares: es una copia de `config.example.h` y **no se
versiona**. Tiene las credenciales WiFi, el broker MQTT, la **identidad** (`NODO_ZONA_ID`,
`NODO_SECTOR_1_ID`, `NODO_SECTOR_2_ID`), los pines, los intervalos, los límites de seguridad, las
calibraciones y los flags. El tipo de nodo **no** se elige acá: se elige compilando el sketch, y
cada nodo usa solo la porción que le aplica.

### Dos sectores en un nodo

El prototipo maneja **dos sectores de una macro-zona** desde un solo ESP32. El nodo se suscribe al
tópico de comando de cada sector y publica cada ack en el tópico del sector del comando:

| Sector | Id (config.h)       | Válvula               |
| ------ | ------------------- | --------------------- |
| 1      | `NODO_SECTOR_1_ID`  | `PIN_RELE_VALVULA_S1` |
| 2      | `NODO_SECTOR_2_ID`  | `PIN_RELE_VALVULA_S2` |

La bomba de riego, la bomba de insumo, la mediasombra y el riel son **compartidos**.

### Flags y ajustes clave

| Constante | Qué hace |
|---|---|
| `TELEMETRIA_NIVEL_SECTOR` | `0` (de fábrica) → `nursery/zone/{zona}/telemetry`, que es lo que escucha el backend. `1` → tópico del sector 1, que el backend **no** escucha |
| `ENVIAR_METRICAS_EXTENDIDAS` | `1` (de fábrica) → agrega `tempSuelo`, `phSuelo`, `n`, `p`, `k`, `salinidad` y `tds`. El backend ignora las claves que no modela |
| `BATERIA_INSTALADA` | `0` (prototipo) → no se mide ni se publica `battery`, y el backend no actualiza ese dato |
| `RELE_ACTIVO_EN_LOW` | `1` para módulos de relés con optoacoplador (cierran con el IN en LOW) |
| `FINES_CARRERA_NC` | `1` para microswitches NC (cableados con C y NC). Vale para los 4 finales |
| `INSUMO_SEG_POR_DEFECTO` | Cuánto bombea el insumo cuando el backend manda `pump ON` sin parámetros (es lo que manda hoy) |
| `MEDIASOMBRA_UMBRAL_APERTURA_PCT` | `targetPct` (% de apertura) desde el que se enrolla |
| `MAX_CARGAS_SIMULTANEAS` | `1` (de fábrica) → el nodo corre una carga a la vez y encola el resto (ver "Cola de cargas"). Subilo sólo con una fuente que banque más. Si tu `config.h` no lo define, el módulo asume 1 |
| `LIMITE_VALVULA_SEG_MAX` | 1200 s, igual al máximo del contrato (`CONTRATO_VALVULA_DURACION_MAX_SEG`). Si es menor, no compila (`static_assert` en `actuacion/act_riego.cpp`) |
| `ENVIAR_ERRORES_CAUDAL` | `0` (de fábrica) → no se compila `task_actuacion::informarErrorCaudal()`, el punto de integración del caudalímetro (S-05). El hardware no está instalado en el vivero; los errores de caudal los inyecta el simulador |
| `RIEL_POSICION_SECTOR_1/2`, `RIEL_DIR_HACIA_HOME` | Calibración del riel en pasos y sentido del motor |
| `TANQUE_*_DIST_LLENO_CM` / `_VACIO_CM` | Calibración de los HC-SR04 para pasar distancia a % de nivel |

Si tu `config.h` es anterior al ESP32-S3, no compila (faltan pines y constantes nuevas): volvé a
copiar `config.example.h` y pasá tus credenciales.

---

## 4. Cableado (ESP32-S3 DevKitC-1 N16R8)

Fuente de verdad: `comun/config.example.h`.

| Componente | Señal | GPIO | Nodo(s) | Notas |
|---|---|---|---|---|
| LDR (luz) | AO | 1 | sensor, combinado | ADC1. Módulo a **3,3 V**; DO no se conecta |
| HC-SR04 ×2 | TRIG (compartido) | 4 | sensor, combinado | |
| | ECHO tanque de agua / de insumo | 5 / 6 | sensor, combinado | ⚠️ divisor 1k/2k (el ECHO sale a 5 V) |
| Sonda de suelo RS-485 | TX / RX | 17 / 18 | sensor, combinado | UART1, Modbus RTU 4800 8N1 |
| | DE+RE | 8 | sensor, combinado | ⚠️ si el MAX485 va a 5 V, el RO necesita divisor |
| DHT11 | data | 9 | sensor, combinado | |
| Relés (activos en LOW) | IN1 bomba riego / IN2 bomba insumo | 10 / 11 | actuador, combinado | Cargas en los contactos NO, fuente aparte |
| | IN3 válvula sector 1 / IN4 válvula sector 2 | 12 / 13 | actuador, combinado | Diodo en antiparalelo en cada electroválvula |
| Riel (DRV8825) | STEP / DIR / SLEEP | 14 / 15 / 16 | actuador, combinado | RESET del módulo fijo a 3V3 del ESP32 |
| | final de carrera home / opuesto | 21 / 47 | actuador, combinado | NC, `INPUT_PULLUP` |
| Mediasombra (L298N canal A) | IN1 / IN2 / ENA (PWM) | 39 / 40 / 41 | actuador, combinado | |
| | final enrollada / desenrollada | 42 / 2 | actuador, combinado | NC, `INPUT_PULLUP` |
| LED de estado | RGB de la placa | `RGB_BUILTIN` | todos | En la revisión v1.1 de la placa está en el 38 y no prende |
| *Libre* | | 7 | | ADC1. Reservado para la batería (`PIN_BATERIA`) |

**No usar:** 0, 3, 45, 46 (strapping) · 19, 20 (USB nativo) · 26–32 (flash) · 33–37 (PSRAM
octal) · 43, 44 (consola) · 38 y 48 (LED RGB). Con WiFi encendido, solo el ADC1 (GPIO 1–10) lee
valores analógicos.

---

## 5. Librerías

`knolleary/PubSubClient` · `bblanchon/ArduinoJson` (7.x) · `adafruit/DHT sensor library`
(+ `Adafruit Unified Sensor`). Están declaradas en `platformio.ini` (`lib_deps`).

---

## 6. Contrato MQTT

Fuente de verdad: `comun/contrato.h`. Los espejos del backend son `ContratoNodo.java` y
`ContratoRiel.java`; el del simulador es `simulador/server/contract.ts`.

### Tópicos

| Canal | Topic | Publica | Suscribe | QoS |
|---|---|---|---|---|
| Telemetría | `nursery/zone/{zona}/telemetry` | ESP32 | Backend | 1 |
| Comando (×2 sectores) | `nursery/zone/{zona}/sector/{sector}/command` | Backend | ESP32 | 1 |
| Ack (×2 sectores) | `nursery/zone/{zona}/sector/{sector}/ack` | ESP32 | Backend (`AckActuadorReceiver`) | 1 |
| Comando del riel | `nursery/rail/command` | Backend | ESP32 | 1 |
| Evento del riel | `nursery/rail/event` | ESP32 | Backend | 0 (PubSubClient) |
| Errores del nodo (S-05) | `nursery/zone/{zona}/errores` | ESP32 | Backend (`NodeErrorReceiver`) | 1 |

### Payload de telemetría

```json
{
  "mac": "A4:CF:12:9A:00:01",
  "signal": -63,
  "timestamp": 1782414800,
  "metrics": {
    "humSus": 52.4, "humAmb": 72.1, "temp": 23.5, "ce": 1200, "uv": 40.0,
    "tempSuelo": 22.8, "phSuelo": 6.4, "n": 12, "p": 8, "k": 30, "salinidad": 120, "tds": 210,
    "nivelAgua": 75.0, "nivelInsumo": 40.0
  }
}
```

- `battery` solo aparece con `BATERIA_INSTALADA 1`.
- Las métricas extendidas (`tempSuelo` … `tds`) solo aparecen con `ENVIAR_METRICAS_EXTENDIDAS 1`.
- `nivelAgua` y `nivelInsumo` (en %) aparecen siempre que el HC-SR04 responde. **El backend todavía
  no las modela**: las ignora.
- Una métrica cuyo sensor falló no se envía.

### Comandos (lo que manda el backend hoy)

| Comando | Qué hace el nodo |
|---|---|
| `{"actuador":"valve","accion":"ON","parametros":{"durationSec":600}}` | **Riega el sector** del tópico: abre su válvula y, a los `RIEGO_VALVULA_ANTES_DE_BOMBA_MS`, prende la bomba de riego. Al terminar apaga la bomba y después cierra la válvula |
| `{"actuador":"valve","accion":"OFF"}` | Corta el riego del sector |
| `{"actuador":"pump","accion":"ON","parametros":{}}` | **Aplica insumo al sector**: abre su válvula y prende la bomba de insumo durante `INSUMO_SEG_POR_DEFECTO`. Con `"ml"` usa `BOMBA_INSUMO_MS_POR_ML`; con `"durationSec"`, esa duración |
| `{"actuador":"pump","accion":"OFF"}` | Corta el insumo del sector |
| `{"actuador":"shade","accion":"SET","parametros":{"targetPct":40}}` | `targetPct` es el % de **apertura**: desde `MEDIASOMBRA_UMBRAL_APERTURA_PCT` (50) enrolla, por debajo desenrolla. Hay una sola mediasombra: manda el último comando |

**La mediasombra es de la macro-zona** (`add-rustificacion-por-macrozona`): el backend manda el `shade SET` por el
**comando de zona** (`nursery/zone/{zona}/command`, el del `LEER_AHORA`, con `"actuador":"shade"`) y el nodo contesta por el
**ack de zona** (`nursery/zone/{zona}/ack`, `contratoTopicAckZona`). Internamente el comando lleva `sector = CONTRATO_SECTOR_ZONA`
(0). `nodo_actuador` ahora también se suscribe al comando de zona (4ª suscripción) y `nodo_sensor` ignora el de la malla. Un
`shade SET` por el tópico de un sector se sigue aceptando. **Pendiente de compilar y probar en el hardware.**

- Todos llevan `commandId`.
- `accion` no distingue mayúsculas y acepta también los nombres históricos (`open`, `inject`,
  `close`, `move`).
- **El nodo corre a lo sumo `MAX_CARGAS_SIMULTANEAS` cargas a la vez** (ver "Cola de cargas" abajo): riego e
  insumo de los dos sectores y la mediasombra hacen cola. Las bombas prenden mientras algún sector las
  necesite, y nunca con su válvula cerrada.
- Un `ON` para algo que ya está en curso lo **reemplaza**: el comando viejo termina con ack
  `ERROR` y `reemplazado`.
- `shade SET` acepta `"parametros":{"prioridad":"INMEDIATA"}`: arranca ya aunque el nodo esté lleno. Lo manda **sólo**
  el estado seguro del backend; en `valve ON` / `pump ON` se ignora, y cualquier otro valor cuenta como ausente.

### Cola de cargas (`actuacion/cola_cargas`)

El 17/10/2026 el motor mandó riego, insumo y mediasombra en un mismo pulso, el nodo prendió dos válvulas, dos
minibombas y el motor de la malla a la vez, la fuente del prototipo no aguantó el pico, cayó la tensión y el ESP32 se
reinició ~7 s después (con otro client id de MQTT): quedaron acciones cortadas y los ack finales nunca llegaron.
Por eso el nodo **serializa las cargas** (design `openspec/changes/add-cola-actuadores-nodo/`):

- **Carga** = un tramo de riego de un sector, un tramo de insumo de un sector, o un movimiento de la mediasombra.
  Corren a lo sumo `MAX_CARGAS_SIMULTANEAS` (`config.h`, 1 por defecto; si tu `config.h` no lo define, el módulo
  asume 1). El resto espera en una cola **en RAM** (`COLA_CARGAS_MAX` = 8), en orden de llegada. Cola llena →
  `ERROR cola_llena` al instante. Se pierde en un reinicio: lo encolado termina como la alerta "no confirmó a
  tiempo" del backend.
- **Lo de seguridad no hace cola:** `valve OFF` / `pump OFF` actúan ya, cortan el tramo en curso de ese sector y
  actuador y descartan lo encolado de lo mismo (cada uno recibe `ERROR cancelado`). Un `shade SET` con
  `prioridad: "INMEDIATA"` arranca ya y descarta el `SET` encolado (`ERROR reemplazado`). Un comando inválido o un
  `sin_cambio` responde en el acto y no ocupa lugar.
- **Reemplazo:** un comando para lo mismo que está en curso lo reemplaza y arranca (mismo lugar, no suma consumo); si
  estaba encolado toma su lugar en la fila.
- **El riel no entra en el límite:** tiene su propio slot y su propio ciclo (`act_riel`), y ya se probó solo. Una pasada
  que coincida con una carga en curso sigue sumando consumo (riesgo conocido).
- La lógica es **pura** (`cola_cargas.{h,cpp}`: sin `Arduino.h` ni FreeRTOS, el reloj entra como argumento) y la
  conecta `task_actuacion` con los drivers (`act_riego`, `act_mediasombra`), que no cambiaron de contrato.
- **Prueba en el host** (no hace falta placa): `test/host/cola_cargas_test.cpp`.

  ```bash
  cd Desarrollo/embebido
  g++ -std=c++17 -Wall -Wextra -I actuacion actuacion/cola_cargas.cpp test/host/cola_cargas_test.cpp -o /tmp/cola && /tmp/cola
  ```

  Está fuera de `actuacion/` a propósito, para no romper la copia plana (`cp actuacion/* …`) de arduino-cli.
- Los ack ya no son uno solo por comando: ver "Ack". Y `task_red` ya no pierde los ack con la red caída (como los
  eventos del riel: mira el primero, publica y recién ahí lo saca; `sys.ack` es de 16).

### Ack

```json
{ "commandId": "abc-123", "status": "SUCCESS", "detalle": { "tipo": "ok", "durationSec": 600 } }
{ "commandId": "abc-124", "status": "SUCCESS", "detalle": { "tipo": "sin_cambio", "targetPct": 100 } }
{ "commandId": "abc-125", "status": "ERROR",   "detalle": { "tipo": "falla_mecanica" } }
```

El ack **final** sale **cuando termina** la acción, no cuando llega el comando. Además, por cada comando de carga
(`valve ON`, `pump ON`, `shade SET`) el nodo publica ack **intermedios** (`status: "EN_CURSO"`, mismo tópico y mismo
`commandId`) para que el backend sepa cuándo la carga espera turno y cuándo arrancó de verdad:

```json
{ "commandId": "abc-126", "status": "EN_CURSO", "detalle": { "tipo": "en_cola", "posicion": 2, "esperaMaxSec": 45 } }
{ "commandId": "abc-126", "status": "EN_CURSO", "detalle": { "tipo": "iniciado", "durationSec": 10, "ml": 10 } }
```

Secuencia: `[en_cola] → iniciado → final`. `en_cola` sólo sale si el comando no pudo arrancar al llegar; `posicion` (1 = el
próximo) y `esperaMaxSec` son una cota superior de la espera. Un encolado que se descarta pasa directo a su final
(`cancelado` / `reemplazado`). Los `OFF`, `sin_cambio` e inválidos sólo llevan el final. El backend nuevo sigue
funcionando sin intermedios (firmware viejo); **el backend viejo no entiende `EN_CURSO`
(pierde el ack final): desplegar el backend antes que el firmware**. Los valores de `tipo` son:

| Tipo | Cuándo sale |
|---|---|
| `ok` | La acción terminó bien |
| `limite_excedido` | Terminó, pero se recortó al límite local |
| `sin_cambio` | La mediasombra ya estaba en esa posición |
| `reemplazado` | Llegó otro comando para lo mismo antes de terminar |
| `cancelado` | Llegó un `OFF` |
| `falla_mecanica` | La mediasombra no llegó al final de carrera a tiempo |
| `comando_invalido`, `accion_invalida`, `duracion_invalida`, `parametros_invalidos` | El comando no se pudo ejecutar |
| `cola_llena` | La cola de cargas no tenía lugar (`ERROR`, final) |
| `en_cola`, `iniciado` | Intermedios (`EN_CURSO`): la carga espera turno / arrancó |

Un `commandId` repetido no se vuelve a ejecutar:
- si sigue en curso **o está encolado**, se ignora (no se reenvían intermedios);
- si ya terminó, se reenvía su ack.

> **Qué hace el backend con el ack** (`SeguimientoComandosService`): un `EN_CURSO` mantiene el comando pendiente y
> recalcula su plazo desde el arranque; un `SUCCESS` cierra el seguimiento;
> un `ERROR` que es una falla deja una alerta en el historial (`falla_mecanica` es crítica; `reemplazado`
> y `cancelado` no son fallas); y si no llega ningún ack en `durationSec` + 90 s, alerta
> `ActuadorSinRespuesta`. No cambia ninguna decisión del motor.

### Errores del nodo (S-05)

El nodo detecta una falla, **corta el actuador por su cuenta** y la publica **una sola vez** por evento en
`nursery/zone/{zona}/errores` (QoS 1, sin retain):

```json
{ "nodo": "A4:CF:12:9A:00:01", "sector": "", "tipo": "MEDIASOMBRA_FIN_DE_CARRERA", "valor": "CERRADA", "timestamp": 1782414800 }
{ "nodo": "A4:CF:12:9A:00:01", "sector": "MZ-1-012", "tipo": "SIN_CAUDAL", "valor": 0, "timestamp": 1782414800 }
```

| `tipo` | `sector` | `valor` |
|---|---|---|
| `SIN_CAUDAL`, `CAUDAL_EXCESIVO` | el del sector de la electroválvula | caudal medido en L/h (número) |
| `MEDIASOMBRA_FIN_DE_CARRERA` | vacío: la malla es de la macro-zona | `CERRADA` (no llegó a cerrar: quedó abierta) o `ABIERTA` (no llegó a abrir) |

- **Mediasombra (hoy):** cuando `act_mediasombra` se rinde (`TIMEOUT_MEDIASOMBRA_MS` sin tocar el final de carrera, que es también el
  ack `falla_mecanica`) deja la falla pendiente y `task_actuacion` la encola por `sys.error`; `task_red` la publica. No se repite
  mientras el problema siga: se rearma cuando un movimiento termina bien. El log serie muestra el estado real de los dos finales
  de carrera (`estadoFines()`). El backend deduplica este error con el ack: queda una sola alerta.
- **Caudal (todavía no):** el caudalímetro no está instalado (solo hay un sketch de prototipo, `prototipo_hardware/test_caudalimetro`).
  Detrás de `ENVIAR_ERRORES_CAUDAL` queda `informarErrorCaudal()` como punto de integración; sin el flag no se compila.
- El `timestamp` del nodo actuador son los segundos desde el arranque (no tiene NTP); el backend no lo usa: fecha la falla con la hora
  de recepción.
- **Sin compilar:** este cambio se escribió sin toolchain a mano (`arduino-cli`/PlatformIO). Compilarlo antes de flashear.

### Riel

Contrato completo en la sección "Riel" de `comun/contrato.h` y en
`openspec/changes/add-pasada-riel/design.md` §1.

- **Comandos:** `IR_A` con `posicion` 1 o 2, y `HOME`.
- **Eventos:** `ACEPTADO`, después `LLEGO` o `ERROR`. Los errores llevan un `codigo`:
  `COMANDO_INVALIDO`, `HOME_NO_ENCONTRADO`, `FIN_DE_CARRERA` o `REEMPLAZADO`.
- **Al arrancar** hace homing.
- **Comportamiento:** portado del sketch del prototipo `vivero_esp32_red` (retirado el 23/10/2026; queda en el
  historial de git), que se probó con el riel real:
  - el último comando gana;
  - el mismo `commandId` se ignora o se republica su evento;
  - un evento que no se pudo publicar se reintenta al reconectar.

### Límites conocidos del riel (probado con hardware el 03/10/2026)

Una pasada completa desde Demo Expo anduvo con el ESP32 clásico (sector 1 ≈ 21,5 s, foto, sector 2 ≈ 22 s, foto).
La versión S3 + firmware modular no tiene esa prueba por separado: confirmar en banco.

- **Final de carrera de home.** En el ESP32 clásico el GPIO 34 no tiene pull-up interno y, sin el externo, un contacto
  NC lee "activado" aunque el carro esté lejos. El homing da por hecho que ya está en home si el final marca
  activado: `HOME` y el homing de arranque terminaban al instante (`LLEGO` con `pasos: 0`, ~0,2 s) y, tras una
  pasada, había que llevar el carro a mano a home o la siguiente lo empujaba 42.000 pasos de más. En el S3 los
  finales usan `INPUT_PULLUP`, así que debería estar resuelto. Chequeo: con el carro lejos de home, el estado
  tiene que decir `libre`; si dice `ACTIVADO`, el contacto NC está abierto o hay un cable cortado (medir C–NC).
  `ERROR: Home no encontrado` suele ser cableado, `FINES_CARRERA_NC` que no coincide con el contacto o
  `RIEL_DIR_HACIA_HOME` invertido; `FIN_DE_CARRERA` yendo a la posición 2 indica `POSICION_SECTOR_2` (42000) más
  largo que el riel.
- **Fuente del motor sin detección.** El firmware no sabe si el DRV8825 tiene alimentación: con la fuente apagada y
  el ESP32 por USB, una pasada responde `LLEGO` en el tiempo esperado sin que nada se mueva. Un `LLEGO` no
  prueba que el carro se movió.
- **Brownout.** El WiFi pide picos de ~300 mA y el motor tira la fuente abajo (`Brownout detector was triggered`,
  reinicio). Alimentar el ESP32 aparte del DRV8825 con masas unidas, capacitor de 470–1000 µF en el driver y
  100 µF entre 3V3 y GND del ESP32, cable USB corto.
- **Puerto serie en Linux.** Con el chip CH9102 (`/dev/ttyACM0`), abrir el puerto con `cat`/`stty`/`termios` o
  `arduino-cli monitor` sin TTY reinicia el ESP32 y lo deja sin arrancar; usar el monitor serie del Arduino IDE.
  Recuperación: `esptool --chip esp32s3 -p /dev/ttyACM0 --after hard_reset chip_id`. Hace falta el grupo `dialout`.
- **Relés activos en LOW.** Entre que el pin pasa a salida y se lo pone en "abierto" pasan microsegundos y el
  relé no llega a cerrar; antes el pin flota y el módulo tiene su propio pull-up. Si los relés hacen lo contrario,
  `RELE_ACTIVO_EN_LOW 0`.
- **Pines del S3.** No usar 0, 3, 45, 46 (strapping), 19, 20 (USB), 26–37 (flash y PSRAM octal), 43, 44 (consola),
  38 y 48 (LED RGB). Con WiFi sólo el ADC1 (GPIO 1–10) lee analógico. El GPIO 2 no es de strapping en el S3.
- **Comandos de prueba del riel.** `mosquitto_pub -q 1 -t nursery/rail/command -m
  '{"commandId":"t-1","actuador":"rail","accion":"IR_A","parametros":{"posicion":1}}'` mirando
  `nursery/rail/#`. Usar un `commandId` nuevo en cada prueba (el firmware ignora los últimos 4); `HOME` con otro id
  cancela un movimiento (el viejo termina en `ERROR REEMPLAZADO`).

### QoS

PubSubClient no se suscribe con QoS 2 (devuelve `false`) y no publica con QoS 1. Por eso los
comandos se suscriben con QoS 1 (que es lo que publica el backend), y la doble ejecución la evitan
el dedup por `commandId` y el lock del backend. Para tener QoS 2 real habría que migrar a un cliente
async (`esp-mqtt`).

---

## 7. Cambios pendientes en el backend (fuera del embebido)

- **Niveles de tanque:** modelar `nivelAgua` y `nivelInsumo` en `MetricsPayload` para persistirlos y
  mostrarlos (hoy se ignoran).
- **Mediasombra de dos estados (decidido el 10/10/2026):** la mediasombra **nunca** tiene un % de
  apertura: siempre se abre (enrolla) o se cierra (desenrolla) del todo. El backend todavía piensa en
  porcentajes: `ShadingRule` calcula una apertura a partir del plan de rustificación y del UV, la manda
  como `targetPct` y la web la muestra como %. Hay que pasarlo a dos estados en la regla, en el plan
  de rustificación, en el comando (p. ej. `shade SET {"estado":"abierta"|"cerrada"}`, con su espejo en
  `contrato.h`) y en la web. Mientras tanto lo resuelve el umbral del firmware
  (`MEDIASOMBRA_UMBRAL_APERTURA_PCT`, 50 %).
- **Insumo:** el backend manda `pump ON` sin dosis. Si se quiere dosificar, mandar `ml` o
  `durationSec`.

---

## 8. Estructura

```
embebido/
  platformio.ini        tres envs (uno por firmware) para ESP32-S3 N16R8; reparte carpetas con build_src_filter
  comun/                compartido por los 3: config, contrato, tipos, red (wifi/mqtt), reloj, util_json
  sensado/              luz (LDR), ambiente (DHT11), suelo (RS-485), tanques (2x HC-SR04),
                        buffer_offline, task_sensado                              (sensor + combinado)
  actuacion/            act_riego (relés: 2 válvulas + 2 bombas), act_mediasombra (L298N, 2 estados),
                        act_riel (NEMA 17 + DRV8825), dedup_comandos, task_actuacion (actuador + combinado)
  nodo_sensor/          setup() red + sensado
  nodo_actuador/        setup() red + actuación + riel
  nodo_combinado/       setup() red + sensado + actuación + riel (prototipo)
  prototipo_hardware/   diagnósticos de un solo sensor (caudalímetro, HC-SR04), sin red
```

---

## 9. Validación sin backend

Con el broker levantado (`docker compose up -d mosquitto` en la raíz del repo):

```bash
# Ver todo lo que publica el nodo
docker compose exec mosquitto mosquitto_sub -t 'nursery/#' -v

# Regar el sector 1 durante 10 s
docker compose exec mosquitto mosquitto_pub -q 1 -t 'nursery/zone/MZ-1/sector/MZ-1-001/command' \
  -m '{"commandId":"t1","actuador":"valve","accion":"ON","parametros":{"durationSec":10}}'

# Insumo al sector 2 (duración por defecto)
docker compose exec mosquitto mosquitto_pub -q 1 -t 'nursery/zone/MZ-1/sector/MZ-1-002/command' \
  -m '{"commandId":"t2","actuador":"pump","accion":"ON","parametros":{}}'

# Mediasombra: 100 % de apertura → enrollar
docker compose exec mosquitto mosquitto_pub -q 1 -t 'nursery/zone/MZ-1/sector/MZ-1-001/command' \
  -m '{"commandId":"t3","actuador":"shade","accion":"SET","parametros":{"targetPct":100}}'

# Riel a la posición 1
docker compose exec mosquitto mosquitto_pub -q 1 -t nursery/rail/command \
  -m '{"commandId":"t4","actuador":"rail","accion":"IR_A","parametros":{"posicion":1}}'

# Lectura inmediata (nodo_combinado / nodo_sensor): una telemetría nueva en nursery/zone/MZ-1/telemetry
# al toque, sin ack. Una acción desconocida solo deja una línea de log en el monitor serie.
docker compose exec mosquitto mosquitto_pub -q 1 -t 'nursery/zone/MZ-1/command' \
  -m '{"commandId":"t5","accion":"LEER_AHORA","parametros":{}}'
```

Usá un `commandId` nuevo para cada prueba nueva: los repetidos no se vuelven a ejecutar.
