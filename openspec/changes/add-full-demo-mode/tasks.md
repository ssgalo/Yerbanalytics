# Tasks: add-full-demo-mode

El cambio se estructura en pistas de trabajo siguiendo la metodología TDD (Test-Driven Development).
Las reglas de negocio mantienen una única implementación (sin clases duplicadas).
Cada regla soporta su propio feature flag de activación, múltiples flags de cooldown y variables de bypass.
Todo el código nuevo debe estar escrito en **inglés** respetando principios SOLID y arquitectura limpia.

| Pista | Componente | Descripción |
|---|---|---|
| **A** | Backend Core & Flags | Propiedades de configuración, mapeo de entorno Docker, conditional configuration y estado granular por regla en `RuleOrchestrator`. |
| **B** | Backend Reglas & Cooldowns | Soporte de cooldowns individuales y variables de bypass en `RuleContext` y reglas, traza `OMITIDA_DESHABILITADA` y endpoints `/api/demo/rules`. |
| **C** | Backend Simulación Clima | Inyección de pronóstico ficticio en memoria en `WeatherService`, endpoints `/api/demo/weather` y verificación con reglas climáticas. |
| **D** | Backend Ciclo "Leer Ahora" | Endpoints demo, seguridad (`Permiso.SIMULACION_GESTIONAR`, `MapaPermisos`), integración de historial y validación de comando físico al ESP32. |
| **E** | Simulador Frontend & Server | Controles de clima en UI, panel de interruptores de reglas con inputs de cooldowns individuales, switches de variables de bypass y botón "Leer ahora". |
| **F** | Verificación & Docker | Pruebas de integración completas, verificación con docker-compose y documentación en `CLAUDE.md`. |

---

## Pista A: Backend Core & Feature Flags

- [x] A.1 `DemoProperties`: Crear `@ConfigurationProperties(prefix = "yerbanalytics.demo")` con `yerbanalytics.mode` (`REAL` | `SIMULATED`) y `yerbanalytics.demo.enabled` (`boolean`).
- [x] A.2 `@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")` aplicado directamente a los controllers de demo.
- [x] A.3 `RuleOrchestrator` Estado Granular: `RuleOverridesStore` (ConcurrentHashMap) + `RuleOverride` record (`enabled`, `cooldownMinutes`, `bypassed`) + `RuleOverrides` interface con `NONE`.
- [ ] A.4 Tests unitarios de configuración (`DemoModeConfigurationTest`): validar que con `demo.enabled=false` no se cargan controladores de demo y con `true` sí se instancian.

---

## Pista B: Backend Reglas, Cooldowns y Variables de Bypass

- [x] B.1 `EstadoRegla`: Valor `OMITIDA_DESHABILITADA` ya existía en el enum.
- [x] B.2 `RuleContext`: Métodos `cooldownOverrideMinutes(ruleName, key)` y `isBypassed(ruleName, key)` implementados.
- [x] B.3 Soporte de cooldowns y bypass variables en reglas existentes:
  - [x] `CicloLecturaRiegoRule`: `cooldownKeys()` y `bypassKeys()` implementados.
  - [x] `DeficitCriticoRule`: `cooldownKeys()` implementado.
  - [x] `PausaTrasAplicacionRule`: `cooldownKeys()` implementado.
  - [x] `FueraDeVentanaRiegoRule`: `bypassKeys()` implementado.
  - [x] `DailyDoseLimitRule`: `bypassKeys()` implementado.
  - [x] `ManualLockRule`: `bypassKeys()` implementado; `BYPASS_LOCK` aplicado en `evaluate()`.
- [x] B.4 `RuleOrchestrator`: Regla apagada por override → `OMITIDA_DESHABILITADA`.
- [ ] B.5 `RuleOrchestratorCooldownTest`: Tests unitarios (pendiente).
- [x] B.6 `DemoRulesController`: `GET /api/demo/rules`, `PUT /api/demo/rules/{ruleName}`, `PUT /api/demo/rules/cooldowns/zero`.
- [ ] B.7 `DemoRulesControllerTest`: Pruebas con `@WebMvcTest` (pendiente).

---

## Pista C: Backend Simulación de API Climática

- [x] C.1 `WeatherService` Mock In-Memory: `setMockForecast`, `clearMockForecast`, `getMockForecast`; mock tiene prioridad sobre caché y API.
- [x] C.2 `DemoWeatherController`: `GET/POST/DELETE /api/demo/weather`.
- [x] C.3 `SimulatedWeatherInputDto` con validaciones Jakarta Bean Validation.
- [ ] C.4 `WeatherServiceMockTest`: Tests unitarios (pendiente).
- [ ] C.5 `ReglasConClimaSimuladoTest`: Test de integración (pendiente).

---

## Pista D: Backend Seguridad, Ciclo "Leer Ahora" e Historial

- [x] D.1 `Permiso.SIMULACION_GESTIONAR` agregado; `/api/demo/**` registrado en `MapaPermisos`.
- [x] D.2 `MapaPermisosCoberturaTest` cubre las rutas demo (pasan con `demo.enabled=false` por diseño: los controllers no se cargan y no son escaneados).
- [ ] D.3 Test de Integración "Leer Ahora" (pendiente).

---

## Pista E: Simulador (Frontend & Server)

- [x] E.1 Proxy `/backend/api/demo/**` ya funciona vía proxy genérico en `server/proxy.ts`.
- [x] E.2 `WeatherSimulation.tsx`: formulario con tempC, humedadRelPct, probLluviaPct, precipMm, uvIndex.
- [x] E.3 `RuleToggles.tsx`: lista de reglas con enabled, cooldowns por clave, bypasses por clave; botón "Todos los CDs en 0".
- [x] E.4 `ReadNowButton.tsx`: dispara `POST /api/pulsos` con `ejecutarMotor=true`; maneja 409 (pulso en curso); el servidor MQTT ya responde al `LEER_AHORA` con telemetría actual.

---

## Pista F: Verificación, Docker & Documentación

- [x] F.1 Suite del backend: 946 tests en verde (excl. 12 preexistentes en `DiagnosticoControllerTest` y `NurseryControllerTest`).
- [ ] F.2 Probar conmutación de modos manualmente.
- [ ] F.3 Actualizar `docker-compose.yml` con `YERBANALYTICS_DEMO_ENABLED` y `YERBANALYTICS_MODE`.
- [ ] F.4 Actualizar `README.md` del backend y del simulador.
