# Change: fix-integracion-nodo-real

## Why

Todo se probó con el simulador. Al leer el código para conectar el ESP32 real aparecieron
diferencias que el simulador no deja ver y que dejarían la zona **siempre "sin señal"**:

1. **Unidad del `timestamp`.** El firmware manda segundos epoch (`reloj::ahoraEpoch()`; sin NTP,
   segundos desde el arranque); el simulador manda milisegundos. El backend lo guardaba tal cual en
   `last_reading_time` y lo comparaba con `System.currentTimeMillis()`.
2. **Margen de "sin señal".** El nodo publica cada 30 s y el parámetro
   `seguridad.antiguedad-max-lectura` valía 30 s: un mensaje demorado ya la marcaba caída.
3. **`NODO_SECTOR_ID` de ejemplo** (`S-001`) no existe: los sectores reales son `MZ-1-001`, y el
   backend publica los comandos a ese id.
4. **Broker.** No había `mosquitto.conf`: que el broker aceptara conexiones de la LAN dependía del default de la imagen de Docker (al probarlo, las aceptaba). Se versiona la configuración para que quede explícita.

## What

- **El backend normaliza el `timestamp` en la ingesta**, por valor y no por origen (el backend no
  conoce al simulador) y en un solo lugar (`ContratoNodo.timestampAMs`): segundos epoch → ×1000;
  milisegundos → se dejan; ausente, anterior a 2020 o adelantado más de 5 min → hora de recepción
  con un warn por zona. El mismo valor va al heartbeat del dispositivo.
- **Lectura vieja** (buffer offline del nodo, que conserva su hora): si el timestamp normalizado es
  anterior al `last_reading_time` de la zona, se ignora la lectura completa (métricas, estado,
  motor y heartbeat) y se loguea a debug. Es lo más simple y seguro: no pisa datos más nuevos ni
  marca la zona como recién leída. Costo: se pierden esas lecturas históricas, que hoy no se
  almacenan como serie de todos modos.
- Fábrica de `seguridad.antiguedad-max-lectura`: **30 → 90 s** (3 intervalos). La property
  `stale-threshold-ms` ya no existe: el catálogo es la única fuente de verdad.
- `config.example.h` y README del firmware con ids reales de sector; `Desarrollo/mosquitto/mosquitto.conf`
  montado en el compose (listener 1883 anónimo, sólo LAN/desarrollo).
- Documentar la unidad en las tres copias del contrato (`contrato.h`, `ContratoNodo.java`,
  `contract.ts`; las dos últimas sin cambio de comportamiento en el simulador).

## Fuera de alcance

Comando de la bomba sin mililitros, ACK que el backend no escucha, cadencia del simulador, firewall.

## Sin verificar

Todo lo que requiere hardware o recrear el contenedor del broker.
