# Tasks: add-gestion-hardware

## 1. Backend — persistencia
- [x] 1.1 `model/DispositivoEntity.java` — PK `id`, `serial` único, `tipo`, `zonaId`/`sectorId`, `bateria`, `senal`, `ultimoUpdate`, `falla`
- [x] 1.2 `repository/DispositivoRepository.java` — finders `findBySerial`, `findByZonaIdAndTipo`, `findBySectorIdAndTipo`
- [x] 1.3 `dto/Dispositivo.java` · `dto/SectorIncompleto.java` · `dto/HardwareData.java`

## 2. Backend — servicio y endpoint
- [x] 2.1 `service/HardwareService.getHardware()` — estado derivado (watchdog + batería + falla), KPIs y sectores incompletos
- [x] 2.2 `service/HardwareService.registrarDispositivo(dto)` — valida unicidad/tipo/posición (CA-02/03), persiste; `recambiarDispositivo(id, dto)` (CA-05)
- [x] 2.3 `service/HardwareService.actualizarHeartbeat(zonaId, mac, bateria, senal, ts)` — actualiza el nodo testigo
- [x] 2.4 `service/HardwareInvalidoException.java` (400) · `service/HardwareConflictoException.java` (409)
- [x] 2.5 `controller/HardwareController.java` — `GET`/`POST`/`PUT /api/hardware`, 400/409 en validación
- [x] 2.6 `resources/data.sql` — seed de 6 nodos testigo + actuadores (sectores completos, incompletos y un averiado)

## 3. Backend — integración MQTT (cierra la brecha)
- [x] 3.1 `mqtt/MqttTelemetryPayload.java` — agregar `Integer signal`
- [x] 3.2 `service/NurseryService.updateTelemetry()` — invoca `actualizarHeartbeat` tras procesar la zona
- [x] 3.3 `mqtt/MqttTelemetrySimulator.java` — MAC por macro-zona + señal + una batería baja
- [x] 3.4 `resources/application.properties` — `hardware.bateria-min-pct`, `…watchdog-intermitente-ms`, `…watchdog-critico-ms`

## 4. Frontend — capa de datos
- [x] 4.1 `types/domain.ts` — `Dispositivo`, `EstadoHardware`, `SectorIncompleto`, `HardwareData`, `NuevoDispositivo`
- [x] 4.2 `data/repository.ts` — `getHardware()`, `registerDevice(d)`, `replaceDevice(id, d)`
- [x] 4.3 `data/http/httpRepository.ts` — `GET`/`POST`/`PUT {baseUrl}/hardware`
- [x] 4.4 `data/mock/hardware.ts` — flota de fábrica determinística + derivación de estado/KPIs/incompletos + dedupe; cache + mutadores en `MockRepository`

## 5. Frontend — vista
- [x] 5.1 `hooks/useHardware.ts` — fetch + estado de alta/recambio
- [x] 5.2 `features/hardware/components/` — `HardwareKpis`, `HardwareFilters`, `HardwareTable`, `AltaHardwareForm`, `SectoresIncompletos`
- [x] 5.3 `features/hardware/HardwarePage.tsx` (+ `Hardware.module.css`) — compone KPIs/filtros/tabla/incompletos/alta, `usePageTitle`
- [x] 5.4 `router.tsx` — `/hardware` apunta a `HardwarePage` (el sidebar ya enlaza)

## 6. Tests y verificación
- [x] 6.1 `data/mock/hardware.test.ts` — determinismo de la flota, dedupe y derivación de estados/incompletos
- [x] 6.2 `npm run lint` (0 warnings), `npm run test` (29 OK), `npm run build` en frontend — OK
- [x] 6.3 Compilación del backend (`mvn compile`, OK). Runtime end-to-end (`GET/POST/PUT /api/hardware` con Postgres + MQTT) queda para validar en el entorno del equipo
- [x] 6.4 Revisar que los specs cubren todos los CA de HU-18 y HU-21
