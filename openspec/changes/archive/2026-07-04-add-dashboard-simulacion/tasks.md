# Tasks: add-dashboard-simulacion

## 1. Backend — modo y publisher
- [x] 1.1 `service/SimulacionService.java` — modo `estatico|simulacion` (`AtomicReference`, default estático) + flag `autoSimuladorActivo` (default false), getters/setters
- [x] 1.2 `mqtt/MqttTelemetryPublisher.java` — extrae conectar/publicar/desconectar; `publish(zonaId, payload)` en el topic real, QoS 1
- [x] 1.3 `mqtt/MqttTelemetrySimulator.java` — usa el publisher y gatea el tick por `SimulacionService` (simulación + auto activo)
- [x] 1.4 `resources/application.properties` — `simulator.enabled=false`

## 2. Backend — endpoint de simulación
- [x] 2.1 `dto/SimulacionEstado.java` · `dto/EnvioTelemetria.java`
- [x] 2.2 `controller/SimulacionController.java` — `GET /api/simulacion`, `PUT /api/simulacion` (modo/auto), `POST /api/simulacion/telemetria` (409 fuera de simulación, 400 dato inválido, 502 al fallar la publicación)
- [x] 2.3 Envío: arma `MqttTelemetryPayload` (timestamp null → ahora) y publica; valida serial/zona/métricas

## 3. Frontend — capa de datos
- [x] 3.1 `types/domain.ts` — `ModoSimulacion`, `SimulacionEstado`, `EnvioMetrics`, `EnvioTelemetria`
- [x] 3.2 `data/repository.ts` — `getSimulacionEstado()`, `setModoSimulacion(modo)`, `enviarTelemetria(input)`
- [x] 3.3 `data/http/httpRepository.ts` — `GET/PUT/POST {base}/simulacion*`
- [x] 3.4 `data/mock/simulacion.ts` + `data/mock/mockRepository.ts` — modo en memoria + reflejo del envío en los sectores de la zona
- [x] 3.5 `hooks/useSimulacion.ts` — `{ estado, loading, error, sending, setModo, enviar }`

## 4. Frontend — app standalone (puerto propio)
- [x] 4.1 `features/simulacion/components/` — `ModoSwitch`, `NuevoSensorForm`, `SensorSimCard`
- [x] 4.2 `features/simulacion/SimulacionPage.tsx` (+ `Simulacion.module.css`) — switch + alta + lista de sensores, `usePageTitle`
- [x] 4.3 App standalone en `src/simulador/` (`main.tsx`, `SimuladorApp`, `SimuladorHeader`, css) + `simulador.html` + `vite.simulador.config.ts` (`port 5180`) + scripts `dev:sim`/`build:sim`/`preview:sim`
- [x] 4.4 Quitar del dashboard principal la ruta `/simulacion` (`router.tsx`) y el ítem de sidebar (`components/layout/Sidebar.tsx`); ícono `signal` en `components/ui/Icon.tsx` (usado por el header del simulador)

## 5. Refinaciones — métricas parciales, sensores desacoplados, heartbeat (ex refine-simulacion-topologia)
- [x] 5.1 `NurseryService.updateTelemetry` — setear cada raw reading sólo si viene en el payload (conservar las ausentes) y recomputar con los valores combinados
- [x] 5.2 `dto/EnvioTelemetria` métricas opcionales; `SimulacionController` valida ≥1 métrica (400 si todas nulas)
- [x] 5.3 `SimulacionService` — lista de sensores simulados `{serial, zonaId}` con alta (rechaza duplicado)/baja/listado; `GET/POST/DELETE /api/simulacion/sensores`
- [x] 5.4 `HardwareService.actualizarHeartbeat` — matchear el dispositivo por serial/MAC (`findBySerial`); sectores siguen por zona
- [x] 5.5 Frontend — `NuevoSensorForm` zonas desde `useTopologia`; `SensorSimCard` envío por métrica + "Enviar todo"; `SimulacionPage` lista sensores simulados; mock refleja el envío parcial

## 6. Persistencia y la vista sigue el modo (ex persist-simulacion-modo)
- [x] 6.1 `model/ModoOperacionEntity` + repo (fila única `id=1`); `data.sql` siembra `estatico` idempotente
- [x] 6.2 `model/SensorSimuladoEntity` (`serial_key` único, `serial`, `zona_id`, orden por `id`) + repo
- [x] 6.3 `SimulacionService` — modo y sensores contra la DB (degrada a `estatico` si falta la fila); conserva la API pública y el apagado del auto-simulador al volver a estático
- [x] 6.4 `data/index.ts` — `getHttpRepository()`/`getMockRepository()` singletons; `getRepository()` delega según `VITE_DATA_SOURCE`
- [x] 6.5 `hooks/NurseryContext` — al montar lee el modo del backend (http) y elige fuente (`estatico`→mock, `simulacion`→http); degrada a estático ante fallo
- [x] 6.6 `features/simulacion/components/RegenerarTopologiaForm` + `SimulacionPage` levanta `useTopologia` (zonas compartidas); regenerar sólo en simulación, con confirmación

## 7. Arreglos operativos
- [x] 7.1 `config/CorsConfig` — permitir origen `http://localhost:5180` (además de `:5173`)
- [x] 7.2 `vite.simulador.config.ts` — servir `simulador.html` en la raíz (`/`) en dev y preview; `localhost:5180` abre el simulador
- [x] 7.3 `.env` del frontend — `VITE_DATA_SOURCE=http` + `VITE_API_BASE_URL=http://localhost:8000/api`
- [x] 7.4 `TopologiaService.generar` — borrado en bloque (`deleteAllInBatch`, sectores antes que zonas) para no colgarse ni fallar por optimistic locking al regenerar

## 8. Verificación
- [x] 8.1 `npm run lint` (0 warnings), `npm run test` (46 OK), `npm run build` y `npm run build:sim` en el frontend — OK
- [x] 8.2 `mvnw compile` en el backend — OK
- [x] 8.3 Regeneración verificada bajo carga (8/8 regenerates 200 en ~0.66s con polling concurrente); modo y sensores persistidos verificados en Postgres (`modo_operacion`, `sensor_simulado`)
- [x] 8.4 End-to-end con Mosquitto + Postgres verificado: modo persiste (PUT/GET); regenerar deja 0 sectores con lecturas (sin históricos); alta (201)/listado/baja (204) de sensores; envío de una sola métrica (202) reflejado en el sector (`uv_raw=9.1`, resto null); telemetría en estático → 409
