# Tasks: fix-integracion-nodo-real

## 1. Backend (TDD)
- [x] 1.1 `ContratoNodoTimestampTest` + `ContratoNodo.timestampAMs(ts, ahoraMs)` (segundos/ms/no plausible)
- [x] 1.2 `NurseryServiceTimestampTest` + `NurseryService.updateTelemetry`: normaliza, warn una vez por zona, ignora lecturas viejas, mismo valor al heartbeat
- [x] 1.3 Fábrica de `seguridad.antiguedad-max-lectura` a 90 s; ajustar `StaleSensorRuleTest` y `ParametrosRealesTest`
- [x] 1.4 Verificar que no queda `stale-threshold-ms` en properties ni código

## 2. Contrato y firmware
- [x] 2.1 Comentar la unidad en `contrato.h`, `ContratoNodo.java` y `simulador/server/contract.ts`
- [x] 2.2 `config.example.h` y README del firmware: `NODO_SECTOR_ID` = `MZ-1-001`

## 3. Broker
- [x] 3.1 `Desarrollo/mosquitto/mosquitto.conf` y montaje en `docker-compose.yml`
- [x] 3.2 Recrear el contenedor y verificar que el broker responde por la IP de la LAN (CONNACK desde `192.168.1.64`)

## 4. Documentación
- [x] 4.1 `conectar-esp32.md` y `circuito-sensado-a-motor.md` §5

## 5. Pendiente de hardware
- [ ] 5.1 Probar con un ESP32 real (con y sin NTP) que la zona no queda "sin señal"
