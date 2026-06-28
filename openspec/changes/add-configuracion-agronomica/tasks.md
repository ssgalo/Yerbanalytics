# Tasks: add-configuracion-agronomica

## 1. Backend — persistencia
- [x] 1.1 `model/UmbralMetricaEntity.java` — PK `metricKey` + bandas ideal/warn/crit
- [x] 1.2 `model/ConfiguracionOperativaEntity.java` — fila única + límites + seguimiento + auditoría
- [x] 1.3 `model/RustificacionEtapaEntity.java` — etapas del plan (orden, día desde/hasta, % apertura)
- [x] 1.4 `repository/UmbralMetricaRepository.java` · `ConfiguracionOperativaRepository.java` · `RustificacionEtapaRepository.java`
- [x] 1.5 `dto/UmbralMetrica.java` · `ConfiguracionOperativa.java` · `RustificacionEtapa.java` · `Configuracion.java` (agregado)

## 2. Backend — servicio y endpoint
- [x] 2.1 `service/ConfiguracionService.getConfiguracion()` — arma el DTO agregado
- [x] 2.2 `service/ConfiguracionService.updateConfiguracion(cfg, usuario)` — valida (CA-03..06), persiste, audita
- [x] 2.3 `service/ConfiguracionService.getEffectiveSpecs()` — `List<MetricSpec>` desde umbrales persistidos
- [x] 2.4 getters de seguimiento (latencia ms/label, delta) para `HistorialService`
- [x] 2.5 `controller/ConfiguracionController.java` — `GET`/`PUT /api/configuracion`, 400 en validación (`ConfiguracionInvalidaException`)
- [x] 2.6 `resources/data.sql` — seed de umbrales (= SPECS), config operativa y etapas de rustificación

## 3. Backend — integración con el motor
- [x] 3.1 `NurseryService` usa `getEffectiveSpecs()` y deriva el riego del `idealMin` de humSus
- [x] 3.2 `HistorialService.withSeguimiento()` toma latencia/delta de `ConfiguracionService` (fallback `@Value`)
- [x] 3.3 Registrar el cambio de configuración en el historial (CA-02, `registrarConfiguracion`); ciclos resueltos con `@Lazy`

## 4. Frontend — capa de datos
- [x] 4.1 `types/domain.ts` — `MetricThreshold`, `ConfigOperativa`, `RustificacionEtapa`, `Configuracion`
- [x] 4.2 `data/repository.ts` — `getConfig()` y `saveConfig(cfg)`
- [x] 4.3 `data/http/httpRepository.ts` — `GET`/`PUT {baseUrl}/configuracion`
- [x] 4.4 `data/mock/config.ts` — config de fábrica; `lib/configValidation.ts`; cache + saveConfig en `MockRepository`

## 5. Frontend — vista
- [x] 5.1 `hooks/useConfig.ts` — fetch + estado de guardado
- [x] 5.2 `features/configuracion/components/` — `UmbralesForm`, `LimitesActuadoresForm`, `RustificacionPlanForm`, `SeguimientoForm`, `NumberField`
- [x] 5.3 `features/configuracion/ConfiguracionPage.tsx` (+ `.module.css`) — compone bloques, guardar + reset, `usePageTitle`
- [x] 5.4 `router.tsx` — `/configuracion` apunta a `ConfiguracionPage` (sidebar ya enlaza)

## 6. Tests y verificación
- [x] 6.1 Test del mock de config (determinismo de los defaults) y de la validación de bandas (`data/mock/config.test.ts`)
- [x] 6.2 `npm run lint` (0 warnings), `npm run test` (20 OK), `npm run build` en frontend — OK
- [x] 6.3 Compilación del backend (`mvn compile`, 53 fuentes OK). Runtime end-to-end (`GET/PUT /api/configuracion` con Postgres + MQTT) queda para validar en el entorno del equipo
- [x] 6.4 Revisar que los specs cubren todos los CA de HU-15
