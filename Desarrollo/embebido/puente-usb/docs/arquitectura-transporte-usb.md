# Transporte USB: arquitectura

> Versión visual: [`transporte-usb-yerbanalytics.pdf`](transporte-usb-yerbanalytics.pdf). Cómo se usa:
> [`../README.md`](../README.md). Diseño y porqués: `openspec/changes/add-transporte-usb-nodo/`.

## El problema

En un salón lleno el Wi-Fi de 2,4 GHz no sirve: el aire se satura y el router echa a los equipos. Nuestro nodo (el
ESP32) dependía de ese Wi-Fi para hablar con la plataforma, así que en la expo el hardware quedaba aislado del
sistema. La solución fue **cambiar sólo el medio de transporte**, sin tocar nada de lo que el sistema hace.

```
        ANTES (Wi-Fi)                              AHORA (USB)

  ┌────────┐  Wi-Fi   ┌────────┐            ┌────────┐  cable USB  ┌──────────┐
  │ ESP32  │ ───────▶ │ Router │            │ ESP32  │ ──────────▶ │  Puente  │
  └────────┘          └───┬────┘            └────────┘             └────┬─────┘
                          │                                             │
                    ┌─────▼─────┐                                 ┌─────▼─────┐
                    │ Mosquitto │                                 │ Mosquitto │
                    └─────┬─────┘                                 └─────┬─────┘
                          │                                             │
              Backend · Motor de reglas                     Backend · Motor de reglas
              Dashboard · IA · Simulador                    Dashboard · IA · Simulador
                    (sin cambios)                                 (sin cambios)
```

## El puente

La pieza nueva es el **puente**: un programa chico en la PC que recibe los mensajes del ESP32 por el cable USB y los
publica en el broker MQTT, y al revés, le entrega al ESP32 las órdenes que llegan del broker. Publica en los mismos
tópicos y con el mismo formato que usaba el ESP32 por Wi-Fi, así que **para el resto del sistema el puente *es* el
nodo**: el backend, el motor de reglas, el dashboard, la IA y el contrato MQTT no se enteraron del cambio. Seguimos
mostrando el sistema real decidiendo solo, no un hardware que se mueve por un guion.

## El firmware

Del lado del ESP32 pasó lo mismo, a otra escala. El firmware ya tenía la red aislada detrás de una interfaz chica
(«conectate», «publicá esto», «avisame lo que llegue»), y el resto del código (sensado, actuación, colas, memoria
offline) sólo habla con esa interfaz. Agregamos **una segunda implementación de esa interfaz** que usa el cable en
vez del Wi-Fi, y se elige al grabar la placa. Como el resto del firmware no sabe cuál está usando, siguió
funcionando igual, con su lógica de reintentos y su memoria offline incluidas.

```
            FIRMWARE DEL ESP32
  ┌──────────────────────────────────────────┐
  │  Sensado · Actuación · Riel · Memoria     │
  │  offline · Reloj           (sin cambios)  │
  ├──────────────────────────────────────────┤
  │       Interfaz de red: conectar,          │
  │       publicar, recibir                   │
  ├────────────────────┬─────────────────────┤
  │  Transporte Wi-Fi  │  Transporte USB      │  ◀── se elige al grabar
  │    (el de siempre) │  (sólo para la expo) │
  └────────────────────┴─────────────────────┘
```

## Lo que hace el puente además de trasladar mensajes

Para que el «caño» nuevo se comporte como uno de red, el puente cumple tres funciones:

- **Latido:** cada 2 s le avisa a la placa que el camino hasta el broker está vivo.
- **Aviso de corte:** si pierde el broker, se lo dice al instante, y la placa guarda lo suyo en su memoria offline.
- **La hora:** sin internet la placa no tiene de dónde sacarla, así que se la pasa el puente, y las lecturas llegan
  con la fecha correcta.

Si se corta el cable o se reinicia el puente, todo se reconecta solo y lo pendiente se envía al volver.

## Resultado

Con el ESP32 y la cámara por USB y el resto del sistema corriendo en la PC, **la demo ya no depende de ninguna red
inalámbrica**. Y como es sólo otra forma de transporte, volver al Wi-Fi es grabar el firmware de siempre.

La lección de arquitectura es que esto se pudo hacer en una noche porque el diseño separaba bien las
responsabilidades: cuando la red está detrás de una interfaz, cambiar el medio es enchufar otra implementación, no
reescribir el sistema.
