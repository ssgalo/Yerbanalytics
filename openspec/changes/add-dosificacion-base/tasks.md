# Tasks: add-dosificacion-base

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-dosificacion-base`. Hasta el bloque 8 `SupplyRule` y
`DailyDoseLimitRule` siguen vigentes.

> **Cómo quedó (22/10/2026).** Los nombres de los tasks de abajo siguen siendo los de la spec; en el código van en inglés
> (`PrecondicionesAplicacion` → `ApplicationPreconditions`, `DiagnosticoVigente` → `ValidDiagnosis`, `aplicacion` → `application`,
> `aplicacion_pendiente` → `pending_application`, `tanque` → `supply_tank`). Ver "Decisiones tomadas" en `design.md`.

## 0. Previo
- [x] 0.1 Resolver D-2 (**b**: una bomba por sector, sin tocar contrato ni firmware), D-6 (**tanque por insumo y por MZ**) y D-9 (**retirar la v1 por flag, no borrarla**) — decididas por el equipo; ver "Decisiones tomadas".
- [x] 0.2 Leído: `ActionExecutor` publica `pump ON {}` y marca `Dosificando`; `HistorialService.registrarInsumo` escribe el evento "Insumo" que R-06 y el ciclo usan como última aplicación (`NurseryService.contextosDeRiego`). La nueva `application` **sigue escribiendo ese evento** (ver 4.5).

## 0b. Contrato y firmware (sólo si D-2 = a)
- [ ] 0b.1–0b.4 **No aplica** (D-2 = b). El comando de la bomba no lleva insumo; no se toca `contrato.h`, `ContratoNodo.java`, `simulador/server/contract.ts` ni el firmware. Si más adelante el nodo debe elegir la bomba, se retoma esta sección.

## 1. Catálogo
- [x] 1.1 Test: claves, fábricas y rangos de D-10 (`ParametrosRealesTest`); la dosis máxima coincide con `ContratoNodo.BOMBA_ML_MAX`. Restricciones cruzadas: ninguna hace falta.
- [x] 1.2 `ParametrosInsumo` ampliado (dos ventanas como dos parámetros); el frontend los muestra (grupos «Aplicación de insumos», «Cuándo se puede aplicar un insumo» y «Tanques de insumo» en la pestaña Parámetros). Sumó `diagnostico.vigencia-horas` (48 h) y `insumo.pendiente-vence-dias` (7 d).

## 2. Precondiciones (puras)
- [x] 2.1 Test (tabla) P-D2…P-D5 con bordes (07:00, 10:00, 17:00, 19:00; 30 °C; 45 %; 5 mm) — `ApplicationPreconditionsTest`.
- [x] 2.2 `ApplicationPreconditions` con motivo por resultado (`MET | DEFER | REJECT`).
- [x] 2.3 Test O-03 anticipado: sin pronóstico, P-D4 no bloquea y P-D3 usa la lectura.

## 3. Diagnóstico vigente
- [x] 3.1 Test: 48 h, confianza, "No concluyente" (`ValidDiagnosisTest`). «Última captura» se lee como «último diagnóstico del sector».
- [x] 3.2 `ValidDiagnosis`.

## 4. Servicio de aplicación
- [x] 4.1 Test: la orden lleva `ml` (sin calcular duración en el backend); registro de la aplicación — `ApplicationServiceTest`. **Sin `insumo` en la orden** (D-2 = b).
- [x] 4.2 Test: orden fitosanitario → fertilizante el mismo día.
- [x] 4.3 `ApplicationService.request` → `APPLIED|DEFERRED|REJECTED`; revalida al reintentar.
- [x] 4.4 Entidad `application` (historial propio de aplicaciones) y `pending_application`.
- [x] 4.5 Resuelto por la primera alternativa de 0.2: `application` **y** el evento "Insumo" del historial; `ContextoRiego.ultimaAplicacionMs` sigue leyendo del historial (R-06 sin cambios, sus tests siguen verdes).

## 5. Stock y reingreso
- [x] 5.1 Entidad `supply_tank` (insumo, zona, capacidad, nivel); test de descuento y de avisos 20 % / 0 — `TankServiceTest`.
- [x] 5.2 Reingreso del fitosanitario (`sector.reingreso_hasta`, expuesto como `reentryUntil` en el snapshot); test.
- [x] 5.3 Endpoint de carga de tanque + permisos `insumos.ver` / `insumos.gestionar` en `MapaPermisos`; evento de historial con el usuario. Migración manual `migracion-permisos-insumos.sql`.

## 6. Diferidas
- [x] 6.1 Test: pendiente que cumple al abrir la ventana se ejecuta; cancelada por su regla no; vence a los 7 días.
- [x] 6.2 Reintento por tick (`PendingApplicationScheduler`, reloj inyectado). **Desvío:** no es un tick fijo en 07:00 y 17:00 sino cada `yerbanalytics.insumo.pendientes-revision-ms` (5 min), y las precondiciones deciden: las ventanas son parámetros del catálogo y atar el tick a dos horas las desfasaría al editarlas.

## 7. Frontend
- [x] 7.1 Carga de tanques y vista de stock (`TankCard` en el detalle de la macro-zona); reingreso en el detalle del sector. Las aplicaciones ya figuran en el historial (eventos «Insumo»).
- [x] 7.2 Tests, lint, tokens.

## 8. Retiro v1
- [ ] 8.1 **Diferido (D-9).** `SupplyRule` y `DailyDoseLimitRule` no se borran: arrancan **retiradas** (`yerbanalytics.reglas.retiradas`, sembradas apagadas en `RuleOverridesStore`). `ParametrosInsumo.MAX_DOSIS_24H` y `migracion-quitar-max-dosis.sql` quedan para cuando entre la primera regla N/F. Sí se migró la dosis (`migracion-dosis-por-insumo.sql`).
- [ ] 8.2 Actualizar el grafo (`/api/rules/schema`) y tests del Inspector: sin cambios hasta el borrado.
- [x] 8.3 CLAUDE.md §2 y §6 (migraciones, convención de aplicación); índice; suites verdes.

## 9. Control desde el simulador (requiere `add-control-demo-reglas`)
- [x] 9.1 `ApplicationPreconditions` como `DemoControllable`: apagado, bypass `diagnosis`/`daily`/`window`/`temperature`/`rain`/`moisture`/`stock`, cooldowns `interval-fertilizer`/`interval-fungicide`; el servicio aplica `overrides.isEnabled` y `isBypassed`. Tests de cada clave.
- [x] 9.2 Escenario modificador `APPLICATION_PRECONDITIONS` en el simulador (clima, lectura y stock de tanques que cumplen P-D3…P-D5) con su test; el happy path lo usa y enciende `SupplyRule`.
- [ ] 9.3 **Diferido con 8.1.** `SUPPLY` se conserva, pero con la humedad ambiental en lugar de la temperatura crítica (con calor P-D3 no deja aplicar); `CRITICAL_AIR_TEMP` se reemplazó por `CRITICAL_AMBIENT_HUMIDITY`. Se retira con `SupplyRule`.
