## 1. Puente: protocolo (TDD)

- [x] 1.1 Tests de `protocolo.py`: leer `@S`, `@P` (payload con espacios), descartar texto sin `@`, formato inválido y líneas demasiado largas
- [x] 1.2 Tests de `protocolo.py`: armar `@H <epoch> <sesión>` y `@M <tópico> <payload>`
- [x] 1.3 Implementar `protocolo.py` hasta que pasen los tests

## 2. Puente: E/S

- [x] 2.1 `puente.py`: abrir el puerto con DTR/RTS en falso, leer líneas y reabrirlo cada 2 s si desaparece (nueva sesión al reabrir)
- [x] 2.2 `puente.py`: cliente MQTT `yerbanalytics-puente-usb`; `@S` ⇒ suscribir, `@P` ⇒ publicar con su QoS, mensaje entrante ⇒ `@M`
- [x] 2.3 `puente.py`: latido `@H` cada 2 s sólo con el broker conectado
- [x] 2.4 Configuración por entorno (puerto, baudios, broker) con valores por defecto; `requirements.txt` e `iniciar.sh`

## 3. Firmware

- [x] 3.1 `transporte_usb/net_wifi_usb.cpp`: `iniciar` sin Wi-Fi, `conectado` según el latido, MAC con `esp_read_mac`, `rssi` en 0
- [x] 3.2 `transporte_usb/net_mqtt_usb.cpp`: `Serial0`, lectura de líneas `@H`/`@M`, publicar `@P`, suscripciones `@S` reenviadas al conectar o al cambiar la sesión, hora con `settimeofday`
- [x] 3.3 Entorno `nodo_combinado_usb` en `platformio.ini`
- [x] 3.4 Compilar `nodo_combinado` y `nodo_combinado_usb` sin errores (con `arduino-cli`, `puente-usb/compilar-nodo-usb.sh`: PlatformIO no enlaza ninguno de los dos entornos, tampoco el original, porque no procesa el `.ino` de la subcarpeta; problema previo del repo)

## 4. Prueba con hardware

> Probado el 28/10/2026 con la placa real (grabada con `sg dialout -c "./puente-usb/compilar-nodo-usb.sh --subir"` tras sumar el
> usuario al grupo `dialout`). Abrir el puerto no resetea la placa (no aparece el texto de arranque de la ROM).

- [x] 4.1 Grabar `nodo_combinado_usb`, arrancar el puente y ver la sesión en el broker: la placa mandó sus 4 suscripciones en el primer latido
- [x] 4.2 Lectura sin motor (`POST /api/pulsos {"ejecutarMotor": false}`): «respondieron todas las zonas», MAC `DC:DA:0C:45:76:D0`, timestamp actual (el del latido), señal 0. Ojo: con la fuente de telemetría de demo en `SIMULATOR` el backend descarta, a propósito, el tópico del nodo real
- [x] 4.3 Pasada del riel: `COMPLETADA`, los 5 pasos en OK (eventos del riel y fotos)
- [x] 4.4 Desenchufar y enchufar el USB: se recupera solo (00:07:43 se pierde el puerto, 00:07:47 se reabre con sesión nueva y la placa reenvía sus 4 suscripciones; también se probó el reinicio rápido del puente)

## 5. Documentación

- [x] 5.1 `puente-usb/README.md`: qué es, cómo grabar, arrancar y volver al Wi-Fi después de la expo
- [x] 5.2 Mención en `Desarrollo/embebido/README.md` y en `CLAUDE.md` (transporte alternativo sólo para la expo)
