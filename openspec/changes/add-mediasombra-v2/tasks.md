# Tasks: add-mediasombra-v2

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-mediasombra`. Hasta el bloque 7 `ShadingRule` sigue
vigente; se retira en el 7.

## 0. Previo
- [x] 0.1 Verificado: no existe evaluación por zona; se crea `ZoneRuleEngine` en este cambio (D-1).
- [x] 0.2 Verificado: el front `http` ya llama `GET/POST /api/zonas/{id}/rustificacion` y `POST .../cancelar`; el backend no los implementa. Falta `resetear` (se agrega).
- [ ] 0.3 Confirmar D-5 (varias ventanas por etapa) con el equipo. **Es un cambio de alcance que sí se hace** (bloque 5), pero cambia un contrato del front ya construido.

## 1. Pronóstico (se resuelve en `add-control-demo-reglas` §2)
- [ ] 0.4 **Bloqueo manual (S-01, `add-bloqueo-manual` D-2):** la evaluación por macro-zona (`ZoneRuleEngine`, M-01/M-02) debe respetar el bloqueo de la zona: con un bloqueo vigente (de la zona, o de un sector si la regla mueve por sector) la mediasombra no recibe órdenes. Hoy lo cumple `ShadingRule` porque `ManualLockRule` corta la cadena de cada sector; `ManualLockShadingTest` fija ese comportamiento y hay que extenderlo a la regla por zona (consultar `ManualLockRepository` por `zonaId` sin sector).
- [ ] 1.1 `add-control-demo-reglas` ya dejó `WeatherForecast.PronosticoHora.tempC()` (temperatura por marca horaria) y `WeatherForecast.uvMaxDia()`. No hay un método `temperaturaHora(...)`: si M-02 lo necesita, se agrega acá sobre `horas()`. Verificar con un test lo que M-02 lee y seguir.
- [ ] 1.2 `OpenMeteoWeatherClient` ya completa la temperatura por hora (`temperature_2m`) y calcula `uvMaxDia` como el máximo de `uv_index` del día local de hoy (no pide `uv_index_max`). Confirmar que alcanza para M-02.

## 2. Núcleo de decisión (puro)
- [ ] 2.1 Test (tabla): `HorarioMediasombra` — perfil de crecimiento y etapas con una y dos ventanas, incluidos bordes.
- [ ] 2.2 Test (tabla): `ProteccionCalor` — las tres condiciones, "temperatura = máx(lectura, pronóstico)", no oscila.
- [ ] 2.3 Test: `MediasombraPedido` — gana cerrar.
- [ ] 2.4 Implementar las tres piezas sin Spring.

## 3. Reglas y servicio por zona
- [ ] 3.0 Test: `ZoneRuleEngine` corre las `ZoneRule` una vez por zona, tras el bucle de sectores de `updateTelemetry` y sólo con `ejecutaMotor`; y en cada cambio de franja desde `SensingScheduler`. Interfaz `ZoneRule extends DemoControllable`.
- [ ] 3.1 `MediasombraZonaService` sobre `ZoneRuleEngine` (D-1; un comando por cada sector de la MZ con mediasombra registrada) y reglas `M01HorarioBaseRule` / `M02ProteccionCalorRule` (patrón CLAUDE.md §6, flag ON/OFF).
- [ ] 3.2 Parámetros nuevos y restricción cruzada; migración de los de `ShadingRule`.
- [ ] 3.3 `proteccion_hasta` en la zona (D-3); test de que sobrevive a un reinicio.
- [ ] 3.4 Alertas INFO/WARNING/CRITICAL por MZ y evento, sin repetir (principio 8).
- [ ] 3.5 Traza: el Inspector (`/reglas`) muestra M-01/M-02 con sus comparaciones.

## 4. Plan de rustificación
- [ ] 4.1 Entidad `PlanRustificacion` (zona, inicio, etapas con ventanas, estado) y repositorio; un plan vivo por MZ.
- [ ] 4.2 Endpoints `/api/zonas/{zonaId}/rustificacion` (iniciar, cancelar, resetear, consultar), permisos en `MapaPermisos`.
- [ ] 4.3 Test: fin automático y horario de la última etapa; inicio = día 1.
- [ ] 4.4 Horario por defecto en `ConfiguracionOperativa`.

## 5. Frontend
- [ ] 5.1 `EtapaRustificacion.ventanas[]`; formulario y tarjeta con varias ventanas.
- [ ] 5.2 Repositorio `http` apunta a los endpoints reales; mock actualizado.
- [ ] 5.3 Tests y `npm run lint`.

## 6. Contrato
- [ ] 6.1 Confirmar con un test del publisher que 0/100 ⇒ desenrolla/enrolla; sin cambios en `contrato.h`.

## 7. Retiro
- [ ] 7.1 Borrar `ShadingRule` y `RustificacionEtapa*`; `resources/migracion-quitar-rustificacion-global.sql`; `sowing-date-iso`.
- [ ] 7.2 CLAUDE.md §2 (Rustificación: "el backend todavía no lo implementa" ⇒ implementado) y §6 (migraciones); marcar `add-rustificacion-por-macrozona` §2 como hecho.
- [ ] 7.3 Suites verdes, índice.

## 8. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 8.1 `M01HorarioBaseRule` y `M02ProteccionCalorRule` como `DemoControllable` (apagado; M-01 `force-open`/`force-closed`; M-02 cooldown `hold` y bypass `window`); tests.
- [ ] 8.2 Las reglas por zona aparecen en `GET /api/demo/rules` y `MediasombraZonaService` respeta el apagado.
- [ ] 8.3 Escenarios `SHADE_SCHEDULE` y `HEAT_EXTREME` en el simulador (usan el clima simulado con temperatura por hora, de `add-control-demo-reglas` §2) con sus tests.
- [ ] 8.4 El simulador no inicia planes de rustificación (acción del Ingeniero Agrónomo, D-7 de `add-control-demo-reglas`): la etapa se prueba con `force-*`.
