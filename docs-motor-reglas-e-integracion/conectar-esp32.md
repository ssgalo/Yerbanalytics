# Conectar el ESP32 real al backend

Pasos para reemplazar el simulador por el nodo físico. Sale de leer el código; **todavía no se
probó con hardware**.

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

3. **Configurar el firmware.** Copiar `Desarrollo/embebido/comun/config.example.h` a
   `config.h` y completar:

   | Constante | Valor |
   |---|---|
   | `WIFI_SSID`, `WIFI_PASSWORD` | Los de la red del paso 1 |
   | `MQTT_HOST` | La IP del paso 2 (no `localhost`) |
   | `NODO_ZONA_ID` | Una zona que **exista en la base**, por ejemplo `MZ-1` |
   | `NODO_SECTOR_ID` | El id real del sector, por ejemplo `MZ-1-001` (sólo nodo actuador) |

   Si la zona no existe, el backend descarta el mensaje sin avisar.

4. **Abrir el broker a la red.**
   - Firewall de la PC: permitir el 1883 entrante.
   - Mosquitto 2 sin archivo de configuración acepta sólo conexiones locales, y el
     `docker-compose.yml` no monta ninguno. Si el nodo no logra conectar, agregar un
     `mosquitto.conf` con:

     ```
     listener 1883
     allow_anonymous true
     ```

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

Son diferencias entre el firmware y el backend que el simulador no deja ver.

| Problema | Efecto | Arreglo |
|---|---|---|
| El firmware manda `timestamp` en segundos y el backend lo trata como milisegundos | La zona figura siempre "sin señal" | Unificar la unidad en uno de los dos lados |
| El nodo publica cada 30 s y la zona se da por caída a los 30 s | "Sin señal" intermitente | Subir `yerbanalytics.nursery.stale-threshold-ms` o bajar el intervalo del nodo |
| El comando de la bomba sale sin mililitros | El firmware lo rechaza con `dosis_invalida` | Pendiente en el backend |
| El backend no escucha el ACK | Marca "Regando" sin confirmación del hardware | Pendiente en el backend |
| El nodo toma la hora por NTP | Sin internet en esa red, el `timestamp` sale mal | Usar una red con salida a internet |

Los dos primeros bloquean la prueba y se corrigen en un cambio aparte. El detalle de todo el
circuito está en `circuito-sensado-a-motor.md`.
