# Tasks: add-historial-trazabilidad

## 1. Backend — persistencia
- [x] 1.1 `model/HistorialEventoEntity.java` — entidad `historial_evento`, solo insert
- [x] 1.2 `repository/HistorialRepository.java` — Spring Data + queries por filtros y orden desc
- [x] 1.3 `dto/Evolution.java` — record del seguimiento post-acción (ya existía, reutilizado)
- [x] 1.4 `dto/HistorialEvento.java` — record rico (cadena + parámetros + evolución + colores derivados)

## 2. Backend — servicio y endpoint
- [x] 2.1 `service/HistorialService.registrarRiego/registrarInsumo(...)` — persiste un evento desde la actuación
- [x] 2.2 `service/HistorialService.getHistorial(sector, zona, tipo, desde, hasta)` — consulta filtrada → DTOs
- [x] 2.3 `service/HistorialService.evaluarSeguimiento()` — `@Scheduled`, fija veredicto y bloqueo (HU-12)
- [x] 2.4 `controller/HistorialController.java` — `GET /api/historial` con filtros opcionales
- [x] 2.5 Hook en `service/NurseryService.updateTelemetry()` — registrar en la transición del actuador
- [x] 2.6 `resources/data.sql` — seed de 10 eventos históricos variados (con y sin evaluación)

## 3. Frontend — capa de datos
- [x] 3.1 `types/domain.ts` — tipo `ActionRecord` (cadena + evolución reutilizando `Evolution`)
- [x] 3.2 `data/repository.ts` — agregar `getHistory(): Promise<ActionRecord[]>`
- [x] 3.3 `data/http/httpRepository.ts` — `GET {baseUrl}/historial`
- [x] 3.4 `data/mock/history.ts` — generador determinístico (RNG sembrado) + cache en `MockRepository`

## 4. Frontend — vista
- [x] 4.1 `hooks/useHistory.ts` — fetch + estado (espejo de `NurseryContext`)
- [x] 4.2 `features/historial/components/HistorialFilters.tsx` — tipo/zona/sector/resultado/rango + contador
- [x] 4.3 `features/historial/components/HistorialTimeline.tsx` — timeline expandible (cadena + evolución), read-only
- [x] 4.4 `features/historial/HistorialPage.tsx` (+ `.module.css`) — compone filtros + timeline, `usePageTitle`
- [x] 4.5 `router.tsx` — `/historial` apunta a `HistorialPage` (sidebar ya enlaza)

## 5. Tests y verificación
- [x] 5.1 Test del generador de historial (determinismo por semilla) y del filtrado
- [x] 5.2 `npm run lint` (0 warnings), `npm run test`, `npm run build` en frontend — OK
- [x] 5.3 Compilación del backend (`mvn compile` OK); endpoint sin verbos de escritura. Runtime end-to-end (`GET /api/historial` con Postgres + MQTT) queda para validar en el entorno del equipo
- [x] 5.4 Revisar que los specs cubren todos los CA de HU-11 y HU-12
