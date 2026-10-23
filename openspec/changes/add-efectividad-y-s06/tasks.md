# Tasks: add-efectividad-y-s06

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-efectividad-s06`. Es el último de la cadena (necesita
`add-dosificacion-base`, `add-reglas-biotico` y `add-reglas-clorosis-estres-solar`).

## 0. Previo
- [x] 0.1 Verificado: nadie lee `bloqueoRepeticion`; S-06 es el primer consumidor.
- [x] 0.2 Verificado: el nodo testigo no tiene sector. Tarea 2.3 agrega `sectorId` opcional al registro (D-3).
- [x] 0.3 Resolver las preguntas abiertas del diseño.

## 1. Servicio de efectividad
- [x] 1.1 Test: `RiegoEvaluator` con delta con signo (+19, +4, −6), latencia de 30 min con el reloj inyectado.
- [x] 1.2 `EfectividadService` + interfaz `AccionEvaluable`; mover el `@Scheduled` desde `HistorialService`.
- [x] 1.3 Test de regresión: los eventos "Riego" históricos siguen mostrándose igual en el historial.

## 2. Lectura de verificación
- [x] 2.1 Test: se pide `LEER_AHORA` a los 30 min y la lectura resultante no ejecuta el motor.
- [x] 2.2 Banderín de verificación en `NurseryService.updateTelemetry`.
- [x] 2.3 `sectorId` opcional del nodo testigo en `HardwareService` (alta, recambio y API) y en la vista de Hardware; test. Sin él, E-01 evalúa cualquier riego de la MZ.

## 3. E-02 / E-03 / F-05
- [x] 3.1 Test E-02 (Sano, baja un nivel, igual) con `SeveridadV2`.
- [x] 3.2 Test E-03 (estable, se propaga) y caso sin diagnóstico vigente.
- [x] 3.3 Test F-05 (cierre anticipado) y `INFO`.
- [x] 3.4 Alta del seguimiento desde `ApplicationService` (aplicación correctiva / fitosanitario).

## 4. S-06
- [x] 4.1 Test: bloqueo por tipo y alcance; excepción de R-02 (1/12 h); N-01 sigue; mediasombra nunca.
- [x] 4.2 `SinEfectividadRule` + contexto `bloqueoPorEfectividad`; `CRITICAL`.
- [x] 4.3 Revisar el tope de R-02 (D-7) y dejar la decisión escrita.

## 5. Revisión humana
- [x] 5.1 Endpoint, permiso `efectividad.revisar` en `MapaPermisos`, evento de historial. `MapaPermisosCoberturaTest` verde.
- [x] 5.2 Frontend: botón "Registrar revisión" en el evento de historial sin efectividad; tests y lint.

## 6. Retiro
- [x] 6.1 Borrar `FollowUpRule`; actualizar grafo y tests del Inspector.
- [x] 6.2 README del backend, CLAUDE.md §6, índice; suites verdes.

## 7. Control desde el simulador (requiere `add-control-demo-reglas`)
- [x] 7.1 `EfectividadService` y `SinEfectividadRule` como `DemoControllable` (apagado; cooldowns `verification`, `fertilizer-latency`, `fungicide-latency`, `r02-cap`; bypass `block`); tests. Hoy la latencia sale de la configuración agronómica (`getLatencyMs`): pasa a respetar también el override.
- [ ] 7.2 Escenarios `IRRIGATION_NO_EFFECT` ✅ y `TREATMENT_NO_EFFECT` ✅ con sus tests; **`IRRIGATION_EFFECTIVE` pendiente**: la lectura fijada del simulador es estática y no puede subir 15 puntos después del riego.
