## Why

En la expo (unas 200 personas en un salón, decenas de stands con router o hotspot) el espectro de 2,4 GHz
se saturó: el router del stand echaba a los clientes a los segundos y el ESP32 nunca sostuvo la sesión
MQTT más de 30 s. En casa, con el mismo router y la misma configuración, todo anda. No es un problema
de configuración sino del ambiente, y se repite todos los años: los equipos con IoT terminan yendo por
cable. El ESP32-S3 DevKitC-1 no tiene Ethernet, pero sí un cable que ya está enchufado a la PC: el USB.

Queremos mostrar el sistema real —motor de reglas, IA, dashboard en vivo— y no un hardware que se
mueve por un guion. Para eso, el nodo tiene que hablar con el broker sin depender del Wi-Fi.

## What Changes

- **Firmware:** un transporte alternativo, **MQTT por el puerto serie del USB**, que implementa la misma
  interfaz que hoy implementan `net_wifi` y `net_mqtt`. Se elige al compilar, con un entorno de
  PlatformIO aparte (`nodo_combinado_usb`). El entorno actual (`nodo_combinado`, por Wi-Fi) **no cambia**
  ni en código ni en comportamiento: es el transporte de siempre y vuelve a serlo después de la expo.
- **Puente en la PC** (`Desarrollo/embebido/puente-usb/`, Python): lee el puerto serie y publica en
  Mosquitto, y al revés, en los mismos tópicos y con los mismos payloads del contrato MQTT. Le pasa
  además la hora a la placa, que en la expo no tiene NTP.
- **Para el backend, el puente es el nodo.** Backend, broker, dashboard, simulador y contrato MQTT
  (`contrato.h` y sus espejos) **no cambian ni una línea**.
- La cámara ya tiene su camino por cable (`adb reverse`, `camara-android/transporte-usb.md`): no es parte
  de este cambio, sólo se referencia en la guía de la expo.
- Alcance acotado: es un **workaround para la expo**. No reemplaza al Wi-Fi ni pasa a ser el transporte
  de producción.

## Capabilities

### New Capabilities
- `transporte-usb-nodo`: el nodo combinado publica y recibe sus mensajes MQTT por el puerto serie del
  USB, y un puente en la PC los traslada al broker, sin cambios en el contrato ni en la plataforma.

### Modified Capabilities
<!-- Ninguna: el transporte Wi-Fi, el contrato MQTT y el backend siguen igual. -->

## Impact

- **Firmware** (`Desarrollo/embebido/`): carpeta nueva `transporte_usb/` con las implementaciones USB de
  `net_wifi.h` y `net_mqtt.h`, y un entorno nuevo en `platformio.ini`. Ningún archivo existente del
  firmware cambia de comportamiento.
- **Herramienta nueva** (`Desarrollo/embebido/puente-usb/`): puente Python con sus tests, `requirements.txt`
  y un script de arranque. No toca `start-all.sh`.
- **Plataforma:** sin cambios. Mosquitto ve un cliente más (el puente) en lugar del ESP32.
- **Operación:** para la expo hay que grabar el entorno USB; después hay que volver a grabar
  `nodo_combinado` para recuperar el Wi-Fi.
