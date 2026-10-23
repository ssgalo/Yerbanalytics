# Tasks: add-motor-reglas-operativo

**TDD estricto**: en cada bloque, primero el test que falla (RED), después el código (GREEN), después la limpieza.
Un commit por bloque. Rama: `feat/motor-reglas-operativo`. Orden: backend → frontend → simulador → migración y docs.
Entra **antes** que #64, #65, #67 y #68 (design, "Adaptación de los PRs de reglas v2").
Suites: `cd Desarrollo/backend && ./mvnw test`; `cd Desarrollo/frontend && npm test && npm run lint && npx tsc -b`;
`cd Desarrollo/simulador && npm test && npm run typecheck`.

## 0. Previo
- [x] 0.1 Resolver las preguntas abiertas 1 (vista Rustificación) y 2 (clasificación de protecciones) del design antes de 4.x y 7.x.

## 1. Backend: declaración de cada pieza (D-3, D-4, D-12)
- [x] 1.1 RED: `ReglasArquitecturaTest` suma: toda `label()` sin `\p{Extended_Pictographic}` y sin `(X-NN)`; `code()` con formato válido o vacío; `effect() == PROTECTS` ⇔ `protection().isPresent()`; la lista de protecciones esperada (D-4) es exactamente la que declara `protection()`.
- [x] 1.2 RED: `RuleEngineSchemaControllerTest` afirma etiquetas sin emoji y `codigo` en `RuleNodeDto`.
- [x] 1.3 GREEN: `RuleObjective`, `RuleEffect`; `code()`, `objective()`, `effect()`, `protection()` en `DemoControllable` con sus defaults; javadoc nuevo (no se renombra la interfaz).
- [x] 1.4 GREEN: las 23 piezas: `label()` limpia, `code()`, `objective()`, `effect()` y, en las protecciones, el texto de la consecuencia (tabla D-4).
- [x] 1.5 Texto de `ApplicationService:221` sin "control de demo".

## 2. Backend: estado operativo y efectivo (D-1, D-2, D-9)
- [x] 2.1 RED: `OperationalRuleStatesTest`: sin fila rige la siembra de retiradas; con fila rige la fila; fila huérfana se ignora y se loguea; la cache se actualiza después del commit y no si hay rollback.
- [x] 2.2 RED: `EffectiveRuleOverridesTest`: tabla operativo × override de demo (ninguno, prendido, apagado) → efectivo (el override explícito gana; sin override, el operativo); cooldowns y bypass siguen saliendo de la capa de demo.
- [x] 2.3 RED: reescribir `RuleOverridesStoreRetiredTest`: el store ya no siembra; las retiradas salen apagadas por el estado operativo.
- [x] 2.4 RED: test de integración (Spring, modo `REAL`): apagar una regla ⇒ `OMITIDA_DESHABILITADA` en la traza del pulso siguiente; reiniciar el contexto ⇒ sigue apagada.
- [x] 2.5 GREEN: `RuleStateEntity` (`regla_estado`), `RuleStateRepository`, `OperationalRuleStates`, `EffectiveRuleOverrides` (único bean de `RuleOverrides`); `RuleOverridesStore` sin `implements RuleOverrides` ni siembra.
- [x] 2.6 RED/GREEN: `DemoRuleDto.operationalActive`; `DemoRulesControllerTest` (modo demo): `PUT enabled: true` sobre una regla apagada en el vivero la hace correr y `operationalActive` sigue en `false`; `clear()` devuelve el estado operativo.

## 3. Backend: API, permisos y registro (D-5, D-6, D-7, D-8)
- [x] 3.1 RED: `ReglasEstadoControllerTest` (MockMvc): `200` al apagar una regla de acción sin motivo; `400 MOTIVO_REQUERIDO`, `400 CONFIRMACION_REQUERIDA` (con `consecuencia`) y `400 MOTIVO_DEMASIADO_LARGO` para una protección; `404` nombre desconocido; mismo estado ⇒ `200` sin fila ni evento.
- [x] 3.2 RED: `MapaPermisosCoberturaTest` cubre `PUT /api/rules/*/estado`; test de seguridad: Operario y Servicio ⇒ `403`, Ingeniero Agrónomo ⇒ `200`.
- [x] 3.3 RED: `GET /api/rules/parametros` lista todas las piezas del registro con `codigo`, `objetivo`, `efecto`, `activa`, `activaPorDefecto`, `apagadaPorDemo`, `proteccion`, `estado`.
- [x] 3.4 RED: `HistorialServiceTest`: `registrarEstadoRegla` deja un evento `Configuración` con regla, usuario, motivo y, si es protección, la consecuencia.
- [x] 3.5 GREEN: `PUT /api/rules/{name}/estado` (controller + servicio), `ReglaCatalogoDto` ampliada, `CatalogoParametrosService.catalogo()` desde el registro, `MapaPermisos`, descripción de `Permiso.REGLAS_EDITAR`.

> Notas de implementación (bloques 0–6): (a) `PUT /api/rules/{name}/estado` sin `activa` responde `400 ACTIVA_REQUERIDA`; (b) `MOTIVO_DEMASIADO_LARGO` vale para toda regla que traiga motivo, no sólo las protecciones; (c) `RuleOverride.enabled` pasó a `Boolean` (nulo = demo nunca tocó el interruptor) y `DemoRuleDto` suma `operationalActive` y `effectiveEnabled`; (d) `data.sql` inserta la fila operativa con `WHERE NOT EXISTS` (el `ON CONFLICT` no evita el chequeo `NOT NULL` de las columnas viejas); (e) `CatalogoParametrosService` recibe `ReglasCatalogo` con `@Lazy` (varios `DemoControllable` dependen de él); (f) `ApplicationPreconditions` declara el código como lista `P-D1, P-D2, P-D3, P-D4, P-D5`.

## 4. Backend: despacho de riego (D-10)
- [x] 4.1 RED: `DespachoRiegoTest`: con `ManualLockRule`, `PosponerPorLluviaRule` o `SustratoSaturadoRule` apagadas, el despacho no descarta por bloqueo, lluvia o saturación; prendidas, descarta como hoy.
- [x] 4.2 GREEN: `DespachoRiego` consulta el estado efectivo antes de esos tres descartes.

## 5. Backend: historial fiel a la ejecución (D-11)
- [x] 5.1 RED: `HistorialServiceTest`: un evento del motor guarda `reglasInactivas` con las piezas apagadas en ese momento; el DTO lo expone como lista.
- [x] 5.2 RED: `RuleEngineSchemaControllerTest`: `RuleNodeDto.activaPorDefecto`.
- [x] 5.3 GREEN: columna `historial_evento.reglas_inactivas`, `ActionRecord` del backend, `RuleNodeDto.activaPorDefecto`.

## 6. Backend: Umbrales al motor (D-14)
- [x] 6.1 RED: `ParametrosRealesTest` con `seguimiento.latencia-min` y `seguimiento.delta-min` (familia `SEGUIMIENTO`, rangos, fábrica 2 y 5); `CatalogoParametros` exige que `HistorialService` los declare.
- [x] 6.2 RED: `HistorialServiceConfiguracionTest`: la latencia y el delta salen del catálogo (override ⇒ se usa el override).
- [x] 6.3 RED: `ConfiguracionServiceTest` / controller: el DTO de configuración ya no trae `insumoDosisMax24hMl`, `seguimientoLatenciaMin` ni `seguimientoDeltaMin`; un PUT viejo con esos campos no falla (se ignoran).
- [x] 6.4 GREEN: `ParametrosSeguimiento`, `HistorialService implements ConsumidorParametros`; se borran los campos de `ConfiguracionOperativa`, `ConfiguracionOperativaEntity`, `ConfiguracionService` (getters, validación, fábrica), `data.sql` y las propiedades `yerbanalytics.historial.latency-*`.
- [x] 6.5 `migracion-umbrales-al-motor.sql` (idempotente, encabezado al estilo de `migracion-catalogo-parametros.sql`): copia los valores editados y en rango a `parametro_regla`, `RAISE NOTICE` si no; `DROP COLUMN IF EXISTS` de las tres columnas. Probarla dos veces seguidas en una base de desarrollo.

## 7. Frontend: datos y tipos
- [x] 7.1 RED: tests del repositorio HTTP y mock: `setEstadoRegla(name, { activa, motivo, confirmaProteccion })`; error tipado con `MOTIVO_REQUERIDO` / `CONFIRMACION_REQUERIDA`.
- [x] 7.2 GREEN: `ReglaCatalogo` (`codigo`, `objetivo`, `efecto`, `activa`, `activaPorDefecto`, `apagadaPorDemo`, `proteccion`, `estado`), `RuleNode.activaPorDefecto`/`codigo`, `ActionRecord.reglasInactivas`; `DataRepository`, `httpRepository`, `mockRepository`; `catalogoReglas.fixture.json` sin emojis y con los campos nuevos.
- [x] 7.3 `lib/catalogoSeguridad.ts`: descripción de `reglas.editar`.

## 8. Frontend: sin emojis (D-12)
- [x] 8.1 RED: `sinEmojis.test.ts` recorre `src/**/*.{ts,tsx,json}` y falla con `\p{Extended_Pictographic}` (hoy falla con `ReglaNode.tsx`, el fixture y los tests).
- [x] 8.2 RED: `ReglaNode.test` (ícono `lock` con texto accesible), `NodoPanel.test` (`Chip` "Alerta"), `lecturaTraza.test` (resultado, no carácter).
- [x] 8.3 GREEN: reemplazos de D-12; tests existentes sin emojis; token `--scrim` en `tokens.css` y `theme.ts`, usado en `ReasoningModal.module.css`.

## 9. Frontend: pantalla Motor de reglas (D-13)
- [x] 9.1 RED: `catalogoView.test`: agrupado por objetivo en el orden fijado, grupos vacíos fuera; compartidos ubicados en el primer grupo con consumidor; `CONSUMIDORES` sin las piezas que ahora son reglas y con `objetivo`.
- [x] 9.2 RED: `ReglaCard.test`: nombre + código, chip de efecto, chip Retirada, `Switch` con `aria-checked`, `Callout` de apagada (crit si protección), aviso de demo, parámetro compartido en sólo lectura con enlace.
- [x] 9.3 RED: `ReglasTab.test` (reemplaza a `ParametrosTab.test`): `SaveBar` con parámetros; apagar una regla con borrador pendiente no toca el borrador; diálogo de protección con motivo obligatorio; cancelar devuelve el `Switch` y el foco; sólo lectura sin `reglas.editar`; filtros Apagadas / Protecciones / Con cambios.
- [x] 9.4 RED: `ReglasPage.test`: pestañas Reglas e Inspector; `?tab=parametros` abre Reglas; `?regla=` lleva a la tarjeta.
- [x] 9.5 GREEN: `ReglasTab`, `ReglaCard`, diálogo de confirmación, bloque de compartidos, sobre `src/components/ui/` y tokens; sin prioridades visibles.

## 10. Frontend: historial (D-11)
- [x] 10.1 RED: `historyGraphModel.test`: regla apagada hoy y activa entonces se dibuja; regla en `reglasInactivas` no; evento sin `reglasInactivas` excluye `activaPorDefecto: false`; la que decidió siempre aparece.
- [x] 10.2 GREEN: `historyGraphModel` sin `plainLabel` ni filtro por `activa`.

## 11. Frontend: Configuración (D-14)
- [x] 11.1 RED: `ConfiguracionPage.test`: sólo bandas de métricas, sin seguimiento, límites de insumos ni mediasombra, sin enlace a Motor de reglas.
- [x] 11.2 RED: test de la vista Rustificación: edita el horario por defecto (validación de cierre > apertura) y lista las macro-zonas con su plan y enlace.
- [x] 11.3 GREEN: se borran `SeguimientoForm`, `LimitesActuadoresForm`, sus tests y los campos de `ConfigOperativa`/`configValidation`; `RustificacionPage` + ruta + entrada del Sidebar; textos de `ConfiguracionPage` y `PreferencesPage` sin el motor.

## 12. Simulador (D-9)
- [x] 12.1 RED: `RuleToggles` (o su modelo) muestra la leyenda "Apagada en el vivero" en una regla con `operationalActive: false`, con el interruptor habilitado.
- [x] 12.2 RED: `happyPath` sigue prendiendo `SupplyRule` por demo y completa el tramo de insumo (test de regresión).
- [x] 12.3 GREEN: tipos (`DemoRule.operationalActive`), `RuleToggles`, `happyPath`; escenarios que asumían etiquetas con emoji.

## 13. Documentación
- [x] 13.1 CLAUDE.md: invariante de D-15 en §4 ("Sin controles de demo"), §6 ("Modos de operación", "Patrón para agregar una nueva regla": `code()`, `objective()`, `effect()`, `protection()`, estado operativo) y §6.2; regla "sin emojis" en §4 "Convenciones de estilo"; §4 Sidebar con la vista Rustificación real y Umbrales sólo con bandas; endpoints `PUT /api/rules/{name}/estado`; §6 lista de migraciones con `migracion-umbrales-al-motor.sql` y el conteo reconciliado con los archivos de `resources/`.
- [x] 13.2 README del backend ("Motor de reglas": estado operativo, precedencia con la demo, protecciones; migración nueva), del frontend (pantalla) y del simulador (en modo demo el override del simulador gana sobre el estado operativo).
- [ ] 13.3 Avisar en #64, #65, #67 y #68 qué adapta cada uno (tabla del design).

## 14. Verificación
- [ ] 14.1 Las tres suites verdes, `npm run lint` con 0 warnings, `tsc -b` y `npm run build` del frontend.
- [ ] 14.2 `rg -n '\p{Extended_Pictographic}' Desarrollo/frontend/src Desarrollo/backend/src/main` sin resultados.
- [ ] 14.3 Manual, modo `REAL`: apagar R-04 pide motivo y confirmación; reiniciar el backend y seguir viéndola apagada; el historial muestra el evento; un Operario la ve en sólo lectura.
- [ ] 14.4 Manual, modo demo con el simulador: el simulador apaga una regla prendida y prende una apagada en el vivero (gana el override); happy path completo, incluido el tramo de insumo.
- [ ] 14.5 Historial: el grafo de un riego anterior al apagado de R-03 sigue mostrando R-03.
- [ ] 14.6 Migración manual corrida dos veces en la base de desarrollo; crear una fila operativa nueva funciona.
- [ ] 14.7 Revisión con contexto fresco del diff antes del PR (CLAUDE.md, regla de PR).
