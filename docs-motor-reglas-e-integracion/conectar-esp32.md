# Conectar el ESP32 real al backend

Pasos para reemplazar el simulador por el nodo físico. Sale de leer el código; **todavía no se
probó con hardware**, y el firmware modificado para el riego (`act_valvula.cpp`) **no se compiló**:
no hay toolchain (`pio`, `platformio`, `arduino-cli`) en la PC de desarrollo. Compilar y probar
es el primer paso pendiente.

## La idea en tres líneas

- MQTT corre sobre TCP/IP común: no hace falta una red especial, sólo que el ESP32 y la PC del
  broker estén en **la misma red** y el ESP32 llegue al puerto **1883** de esa PC.
- Nadie se conecta con nadie directamente: el nodo y el backend se conectan al **broker**
  (Mosquitto), que recibe y reparte.
- El backend no distingue al nodo real del simulador: mismo tópico, mismo mensaje. No hay que
  tocarlo para integrar.

## Pasos

1. **Misma red.** Conectar la PC y el ESP32 al mismo WiFi (2,4 GHz, que es lo que soporta el
   ESP32). Sirve el hotspot de un celular. Evitar redes que aíslan los equipos entre sí, como
   suele pasar con la de la facultad.

2. **IP de la PC.** Con `ip a`, anotar la IP en esa red (por ejemplo `192.168.0.100`).

3. **Configurar el firmware.** Regenerar `config.h` desde `Desarrollo/embebido/comun/config.example.h`
   (copiarlo de nuevo, no reutilizar uno viejo) y completar:

   | Constante | Valor |
   |---|---|
   | `WIFI_SSID`, `WIFI_PASSWORD` | Los de la red del paso 1 |
   | `MQTT_HOST` | La IP del paso 2 (no `localhost`) |
   | `NODO_ZONA_ID` | Una zona que **exista en la base**, por ejemplo `MZ-1` |
   | `NODO_SECTOR_ID` | El id real del sector, por ejemplo `MZ-1-001` (sólo nodo actuador) |
   | `LIMITE_VALVULA_SEG_MAX` | `1200` (ya viene así en el ejemplo): el backend puede pedir hasta 1200 s por riego |
   | `CAUDALIMETRO_INSTALADO` | `0` de fábrica (el prototipo riega con una bomba, sin caudalímetro). Con `1` y sin caudalímetro, todo riego falla a los 10 s |

   Si la zona no existe, el backend descarta el mensaje sin avisar.

   **Un `config.h` anterior falla al compilar, a propósito**: si dice `LIMITE_VALVULA_SEG_MAX 120`
   o no define `CAUDALIMETRO_INSTALADO`, `act_valvula.cpp` aborta con `static_assert` / `#error`
   (`act_valvula.cpp:5-13`). Es mejor que recortar los riegos largos en silencio.

4. **Broker abierto a la red.** Ya está resuelto en esta PC (verificado el 03/10/2026):
   - El compose monta `Desarrollo/mosquitto/mosquitto.conf` (`listener 1883`,
     `allow_anonymous true`; sin autenticación, sólo para una LAN de confianza) y el broker
     responde por la IP de la red.
   - El firewall (`ufw`) está deshabilitado. En otra PC, permitir el 1883 entrante.

5. **Levantar todo.** `docker compose up -d mosquitto yerbanalytics-db`, después el backend
   (`./mvnw spring-boot:run`) y el dashboard. **El simulador apagado**, para que no publique
   sobre la misma zona.

6. **Probar por partes.**
   - Red y broker: desde otra máquina de la red,
     `mosquitto_sub -h <ip-de-la-pc> -t 'nursery/#' -v`. Tiene que aparecer la telemetría del
     nodo cada 30 s.
   - Backend: la zona debe actualizar sus métricas en el dashboard.
   - Actuador: publicar un comando a mano y mirar el ACK (ejemplos en
     `Desarrollo/embebido/README.md`).

## Lo que va a fallar aunque la red ande

Son diferencias entre el firmware y el backend que el simulador no deja ver. Dos ya se
corrigieron en `fix-integracion-nodo-real`: el `timestamp` en segundos (el backend lo normaliza
a ms) y el umbral de "sin señal" de 30 s (ahora 90 s, 3 intervalos de publicación).

| Problema | Efecto | Arreglo |
|---|---|---|
| El comando de la bomba sale sin mililitros | El firmware lo rechaza con `dosis_invalida` | Pendiente en el backend |
| El backend no escucha el ACK | Da el riego por abierto cuando el cliente MQTT aceptó el comando, sin confirmación del hardware (pasada la duración + 5 s lo da por cerrado) | Pendiente en el backend |
| La sonda de suelo no manda `humSus` | A los 90 s el backend considera la humedad vieja y no riega | Revisar la sonda RS485; el resto de la lectura sigue entrando |
| Mientras la válvula está abierta el nodo no atiende otros comandos | Un comando de bomba o mediasombra al mismo nodo espera hasta 1200 s (`act_valvula.cpp:70-75`) | Aceptado en el prototipo |
| El nodo toma la hora por NTP | Sin internet en esa red, el `timestamp` es inutilizable y el backend usa la hora de recepción (warn en el log) | Usar una red con salida a internet |

Ninguna de las cinco bloquea la prueba de telemetría. El detalle de todo el circuito está en `circuito-sensado-a-motor.md`.
