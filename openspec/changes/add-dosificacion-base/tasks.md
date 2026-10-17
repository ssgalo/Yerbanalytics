# Tasks: add-dosificacion-base

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-dosificacion-base`. Hasta el bloque 8 `SupplyRule` y
`DailyDoseLimitRule` siguen vigentes.

## 0. Previo
- [ ] 0.1 Resolver D-2 (bomba por insumo: **cambio de contrato y firmware**, recomendada la opción a), D-6 (tanque por MZ o central) y D-9 (cuándo retirar la v1).
- [x] 0.2 Leído: `ActionExecutor` publica `pump ON {}` y marca `Dosificando`; `HistorialService.registrarInsumo` escribe el evento "Insumo" que R-06 y el ciclo usan como última aplicación (`NurseryService.contextosDeRiego`). La nueva `aplicacion` debe seguir escribiendo ese evento o `ContextoRiego` pasa a leer de `aplicacion` (tarea 4.5).

## 0b. Contrato y firmware (si D-2 = a)
- [ ] 0b.1 `contrato.h`: campo `insumo` en `pump ON`, tipos de dispositivo y límite de `ml`; espejos `ContratoNodo.java` y `simulador/server/contract.ts`; test de paridad.
- [ ] 0b.2 Firmware (`act_riego.cpp`): elegir la bomba por `insumo`; compila con `arduino-cli`.
- [ ] 0b.3 `HardwareService`: tipos `bomba_fertilizante` y `bomba_fitosanitario` (una de cada una por sector); `resources/migracion-bombas-por-insumo.sql`.
- [ ] 0b.4 Simulador: `contract.ts` entiende `insumo` y lo muestra en el sector.

## 1. Catálogo
- [ ] 1.1 Test: claves, fábricas y rangos de D-10; restricciones cruzadas si las hay.
- [ ] 1.2 `ParametrosInsumo` ampliado (dos ventanas como dos parámetros); frontend los muestra.

## 2. Precondiciones (puras)
- [ ] 2.1 Test (tabla) P-D2…P-D5 con bordes (07:00, 10:00, 17:00, 19:00; 30 °C; 45 %; 5 mm).
- [ ] 2.2 `PrecondicionesAplicacion` con motivo por resultado.
- [ ] 2.3 Test O-03 anticipado: sin pronóstico, P-D4 no bloquea y P-D3 usa la lectura.

## 3. Diagnóstico vigente
- [ ] 3.1 Test: 48 h, confianza, "No concluyente", última captura.
- [ ] 3.2 `DiagnosticoVigente`.

## 4. Servicio de aplicación
- [ ] 4.1 Test: la orden lleva `ml` e `insumo` (sin calcular duración en el backend); registro de la aplicación.
- [ ] 4.2 Test: orden fitosanitario → fertilizante el mismo día.
- [ ] 4.3 `ApplicationService.request` → `APLICADA|DIFERIDA|RECHAZADA`; revalida al despachar.
- [ ] 4.4 Entidad `aplicacion` (historial propio de aplicaciones) y `aplicacion_pendiente`.
- [ ] 4.5 `ContextoRiego.ultimaAplicacionMs` pasa a leer de `aplicacion` (R-06 sigue igual); test de R-06 verde.

## 5. Stock y reingreso
- [ ] 5.1 Entidad `tanque` (insumo, zona, capacidad, nivel); test de descuento y de avisos 20 % / 0.
- [ ] 5.2 Reingreso del fitosanitario; test.
- [ ] 5.3 Endpoint de carga de tanque + permiso `insumos.gestionar` en `MapaPermisos`; evento de historial.

## 6. Diferidas
- [ ] 6.1 Test: pendiente que cumple al abrir la ventana se ejecuta; cancelada por su regla no.
- [ ] 6.2 Tick en el scheduler en 07:00 y 17:00 (reloj inyectado).

## 7. Frontend
- [ ] 7.1 Carga de tanques y vista de stock; reingreso en el detalle del sector; aplicaciones en el historial.
- [ ] 7.2 Tests, lint, tokens.

## 8. Retiro v1
- [ ] 8.1 Borrar `SupplyRule`, `DailyDoseLimitRule`, `ParametrosInsumo.MAX_DOSIS_24H`; `resources/migracion-quitar-max-dosis.sql`.
- [ ] 8.2 Actualizar el grafo (`/api/rules/schema`) y tests del Inspector.
- [ ] 8.3 CLAUDE.md §2 (Diagnóstico nuevo) y §6 (migraciones); índice; suites verdes.

## 9. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 9.1 `ApplicationPreconditions` como `DemoControllable`: apagado, bypass `diagnosis`/`daily`/`window`/`temperature`/`rain`/`moisture`/`stock`, cooldowns `interval-fertilizer`/`interval-fungicide`; el servicio revalida con `overrides.skipsGuard` como `DespachoRiego`. Tests de cada clave.
- [ ] 9.2 Escenario modificador `APPLICATION_PRECONDITIONS` en el simulador (publica clima y lecturas que cumplen P-D3…P-D5) con su test.
- [ ] 9.3 Retirar el escenario `SUPPLY` y `CRITICAL_AIR_TEMP` de `ruleConditions.ts` junto con `SupplyRule` (bloque 8.1).
