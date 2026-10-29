## Context

El nodo combinado (`nodo_combinado.ino`) habla con la plataforma sólo a través de dos módulos chicos de
`comun/`: `net_wifi` (asociación, reconexión, MAC, RSSI) y `net_mqtt` (envoltorio de PubSubClient:
`iniciar`, `reconectarSiNecesario`, `conectado`, `suscribir`, `publicar`, `loop`). Todo lo demás —colas,
buffer offline, reintento de acks, tareas FreeRTOS— está por encima de esa interfaz y no sabe qué hay
debajo. `reloj` también depende de `net_wifi::conectado()` para sincronizarse con NTP.

En la expo el Wi-Fi de 2,4 GHz no sirve (saturación del salón). La placa ESP32-S3 DevKitC-1 tiene dos
USB: el nativo, que con `ARDUINO_USB_CDC_ON_BOOT=1` es `Serial` y lleva los logs, y el **UART**, con un
chip CH343, que es `Serial0` (UART0) y aparece en la PC como `/dev/ttyACM0`
(`/dev/serial/by-id/usb-1a86_USB_Single_Serial_…`). El cable de la expo está en el puerto UART.

El backend descarta un `timestamp` anterior a 2020 (usa la hora de recepción), pero **acepta** un epoch
viejo y plausible. Sin NTP, `reloj` cae a la última hora guardada en flash más el `millis()`: una lectura
podría quedar con fecha de hace días.

## Goals / Non-Goals

**Goals:**
- El nodo publica y recibe sus mensajes MQTT por el cable USB, con el mismo contrato de siempre.
- Backend, broker, dashboard, simulador y contrato MQTT sin cambios.
- Ningún archivo existente del firmware cambia de comportamiento: el Wi-Fi sigue siendo el transporte por
  defecto.
- Si el cable se desenchufa o el puente se reinicia, todo se recupera solo.

**Non-Goals:**
- Reemplazar al Wi-Fi o volverlo el transporte de producción.
- Soportar `nodo_sensor` y `nodo_actuador` (en la expo se usa el combinado). El diseño los permite, pero no
  se agregan sus entornos.
- Integrar el puente en `start-all.sh`.
- La cámara: ya va por cable con `adb reverse` (`camara-android/transporte-usb.md`).

## Decisions

### D1. Otra implementación de las mismas cabeceras, elegida al compilar

`transporte_usb/net_wifi_usb.cpp` y `transporte_usb/net_mqtt_usb.cpp` implementan **`net_wifi.h` y
`net_mqtt.h` tal como están**. El entorno nuevo de PlatformIO las compila en lugar de las de `comun/`:

```ini
[env:nodo_combinado_usb]
build_src_filter =
    +<nodo_combinado/> +<comun/> +<sensado/> +<actuacion/> +<transporte_usb/>
    -<comun/net_wifi.cpp> -<comun/net_mqtt.cpp>
```

**Por qué:** el sketch, las tareas, `reloj` y el buffer offline no se tocan. La alternativa —un `#ifdef`
dentro de `net_mqtt.cpp`— mezcla dos transportes en el archivo productivo y lo arriesga justo antes de
volver al Wi-Fi.

### D2. El protocolo va por `Serial0` (UART, CH343); los logs siguen en `Serial`

El transporte usa `Serial0` a **115200 baudios**, configurable con `USB_TRANSPORTE_BAUD` (valor por
defecto en el código, con `#ifndef`, así `config.h` no cambia). Los logs siguen saliendo por `Serial`,
el USB nativo.

**Por qué:** separa protocolo y logs físicamente: el cable de la expo (puerto UART) lleva sólo el
protocolo y los mensajes de arranque de la ROM. Con un segundo cable en el USB nativo se ven los logs como
siempre. 115200 alcanza de sobra: una telemetría ocupa unos 400 bytes y la línea serie mueve unos 11 KB/s.

### D3. Protocolo de líneas con prefijo `@`

Una línea por mensaje, terminada en `\n`. Los tópicos no tienen espacios; el payload es el resto de la
línea (JSON de una sola línea, puede tener espacios).

| Sentido | Línea | Significado |
|---|---|---|
| placa → PC | `@S <qos> <tópico>` | Suscribirse al tópico |
| placa → PC | `@P <qos> <tópico> <payload>` | Publicar |
| PC → placa | `@H <epoch> <sesión>` | Latido del puente, con la hora y el id de su sesión |
| PC → placa | `@M <tópico> <payload>` | Mensaje recibido de un tópico suscripto |
| PC → placa | `@X` | El puente perdió el broker: la placa se da por desconectada al instante |

La placa antepone un `\n` a cada línea suya, para cerrar cualquier texto del sistema que haya quedado a medio
imprimir en UART0. Toda línea que no empiece con `@` o que no respete el formato se **descarta** (la ROM imprime al arrancar
por el mismo UART, y una línea dañada no debe convertirse en un comando). El largo máximo de línea es
`MQTT_BUFFER_SIZE` más el encabezado; una línea más larga se descarta entera.

**Por qué:** texto legible (se puede mirar con cualquier monitor serie), sin escapes y trivial de probar.
Se descartó PPP sobre serie (lwIP PPPoS más `pppd` en la PC): MQTT sería transparente, pero suma NAT, rutas
y una pila más que configurar la noche antes de la expo.

### D4. «Conectado» es «latido reciente», y la sesión dispara las suscripciones

El puente manda `@H` cada 2 s **sólo mientras está conectado al broker**. La placa se considera conectada
si recibió un latido en los últimos 6 s, así que `conectado()` refleja el camino completo hasta el broker.

`reconectarSiNecesario()` devuelve `true` cuando hay latido, y **reenvía todas las suscripciones** cuando
pasa de desconectada a conectada **o** cuando cambia el id de sesión del latido. Así, un puente que se
reinicia en menos de 6 s (sin hueco de latidos) igual recibe las suscripciones de nuevo.

Cuando el puente pierde el broker manda `@X`, y la placa se da por desconectada **en ese momento**: si esperara a
que vencieran los latidos, durante hasta 6 s publicaría mensajes que el puente ya no puede entregar, y el
firmware, creyéndolos enviados, no los reintentaría.

**Por qué:** replica la semántica de PubSubClient que el sketch ya espera (re-suscribir al reconectar), y
el buffer offline y el reintento de acks del firmware funcionan sin cambios: `publicar()` devuelve `false`
cuando no hay latido.

### D5. La hora llega en el latido

Al recibir `@H` con un epoch válido (≥ `1700000000`) que difiere más de 2 s de su hora, la placa ajusta el reloj
del sistema (`settimeofday`). `reloj::sincronizarSiHayRed()` ya hace el resto: ve `net_wifi::conectado()` en `true`,
lee `time(nullptr)` válido, fija la base y la persiste. `reloj` no cambia; `configTime` (NTP) queda
configurado pero sin red no hace nada; para que no aborte, el `iniciar()` USB levanta la pila TCP/IP
(`esp_netif_init`) sin encender ninguna radio.

**Por qué:** evita la hora vieja de la flash, que el backend aceptaría como válida.

### D6. La MAC es la misma que por Wi-Fi; la señal es 0

`net_wifi::obtenerMac()` lee la MAC de estación con `esp_read_mac(…, ESP_MAC_WIFI_STA)` sin encender el
Wi-Fi: es la misma que reporta hoy, así que el backend ve **el mismo nodo**. `rssi()` devuelve `0`
(el dashboard lo muestra como señal buena: el umbral de señal débil es −85 dBm). `iniciar()` no enciende el
Wi-Fi.

### D7. El puente: Python, protocolo puro y una capa de E/S fina

`Desarrollo/embebido/puente-usb/`:
- `protocolo.py`: **funciones puras** para leer y armar líneas (`@S`, `@P`, `@H`, `@M`) y validar formato. Es
  lo que se prueba con pytest (TDD estricto).
- `puente.py`: abre el puerto, conecta a Mosquitto (paho-mqtt), traslada en los dos sentidos y manda latidos.
  Cliente MQTT `yerbanalytics-puente-usb`. Publica con la QoS que pide la placa y se suscribe con la que
  pide. Un mensaje para la placa con el puerto cerrado se pierde, igual que un comando MQTT con el nodo
  desconectado (el backend ya maneja la falta de ACK).
- Configuración por variables de entorno con valores por defecto: puerto (primero el `by-id` del CH343,
  si no `/dev/ttyACM0`), baudios, host y puerto del broker.
- `iniciar.sh`: crea el venv si falta, instala `requirements.txt` y arranca el puente.

**Por qué Python:** pyserial y paho-mqtt son puros y estables; el repo ya usa Python en la inferencia. Node
pediría `serialport`, que compila nativo.

### D8. Abrir el puerto no tiene que resetear la placa

El circuito de auto-reset de la placa resetea con **DTR bajo y RTS alto**. Al abrir el puerto, Linux sube los dos
(estado inofensivo); el puente baja **primero RTS y después DTR**, así nunca pasa por el estado de reset. (Bajar
DTR primero, que es lo que hace pyserial con `dtr = False` y `rts = False`, sí pasaría por ese estado.)
Escribe con timeout de 1 s: si el puerto se traba, pierde ese mensaje en vez de bloquear los dos hilos. Si el puerto desaparece (cable desenchufado),
lo reintenta cada 2 s y, al volver, cambia el id de sesión para forzar las suscripciones (D4).

## Risks / Trade-offs

- **Ruido eléctrico de relés y motores en el USB** → cable corto y blindado, lejos de los cables de
  potencia. Si el USB se reinicia, D4 y D8 recuperan la sesión solos.
- **`Serial` (USB nativo) sin host conectado** podría demorar los logs si el buffer se llena → verificar en
  hardware que la placa no se frena con un solo cable en el puerto UART. Si pasa, bajar la verbosidad o
  enchufar también el USB nativo.
- **Mensajes de la ROM y líneas dañadas por el mismo UART** → se descartan por formato (D3); ningún texto
  ajeno puede volverse un comando.
- **Hay que regrabar la placa dos veces** (para la expo y para volver al Wi-Fi) → la guía lo dice con los
  comandos exactos.
- **El puente es un punto único de falla** → es un proceso aparte y simple, con reconexión propia al puerto
  y al broker.

## Migration Plan

1. Grabar `nodo_combinado_usb` en la placa y arrancar el puente.
2. Verificar con una lectura **sin motor** (`POST /api/pulsos {"ejecutarMotor": false}`) que llega la
   telemetría, y con una pasada del riel que vuelven sus eventos.
3. Después de la expo: grabar `nodo_combinado` (Wi-Fi) y no arrancar el puente.

## Open Questions

- Ninguna bloqueante. Se confirma en hardware el comportamiento de `Serial` sin host (ver riesgos).
