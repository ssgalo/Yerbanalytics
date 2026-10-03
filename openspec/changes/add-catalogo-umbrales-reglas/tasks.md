# Tasks: add-catalogo-umbrales-reglas

**TDD estricto:** en cada tarea, primero el test en rojo, después el código mínimo, después
refactor. Una tanda cierra sólo con la suite en verde:
- backend: `cd Desarrollo/backend && ./mvnw test`
- frontend: `cd Desarrollo/frontend && npm test && npm run lint`

Paquetes nuevos: `be/engine/parametros/` y `be/engine/traza/` (`be/` =
`Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`).

## 0. Decisiones previas

- [x] 0.1 Confirmar con el usuario DA-1 … DA-6 de `design.md`. **Confirmadas con el default
      recomendado** (DA-1 desacoplar el umbral de riego con fábrica 42; DA-2 migrar las tres reglas
      de riego actuales; DA-3 DAG con traza en el Inspector de `/reglas`; DA-4 última traza por
      sector y origen en memoria; DA-5 un solo `diagnostico.confianza-minima`; DA-6 no tocar el
      Registro de Inacción).
- [x] 0.2 Confirmar la estrategia de entrega (ver *Review Workload Forecast*).

## 1. Catálogo: tipos y definiciones (PR 1)

- [x] 1.1 Test: `VentanaHoraria.parse("06:00-18:00")` y `contiene()` en 05:59 / 06:00 / 17:59 /
      18:00; ventana que cruza la medianoche (`18:00-06:00` contiene 02:00); formatos inválidos
      (`25:00-18:00`, `06:00`, vacío). Implementar `ValorParametro` (sellada: `Numero`, `Hora`,
      `VentanaHoraria`) y su formato canónico.
- [x] 1.2 Test: `TipoParametro.parsear(texto, def)` acepta y rechaza según tipo, rango y decimales
      (`ENTERO` con 10,5 → error; `NUMERO` 0,2 con 2 decimales → ok). Implementar.
- [x] 1.3 Test: `CatalogoParametros` falla al construirse con clave duplicada, con fábrica fuera de
      rango y con una regla que declara una clave inexistente. Implementar `DefinicionParametro` y
      `CatalogoParametros`.
- [x] 1.4 Test de cobertura de riego: una familia **de test** `ParametrosRiegoV2Fixture` con las 15
      entradas de `design.md` D2 se registra sin errores, y sus restricciones cruzadas
      (`crítico < umbral < objetivo`, `bloqueo ≤ alerta`) rechazan los conjuntos inválidos.
      Implementar `RestriccionCruzada`.
- [x] 1.5 Crear las familias reales con los valores de **hoy** (ver D7):
      `ParametrosSeguridad` (`antiguedad-max-lectura` 30 s), `ParametrosRiego` (`umbral-humedad` 42 %,
      `tiempo-max-apertura` 120 s, `max-riegos-24h` 2, `max-riegos-24h-sector` 1,
      `lluvia-probabilidad` 60 %), `ParametrosInsumo` (`max-dosis-24h` 1),
      `ParametrosMediasombra` (`uv-umbral` 7, `apertura-proteccion-uv` 30 %, `apertura-maxima` 100 %),
      `ParametrosDiagnostico` (`confianza-minima` 85 %). Test: cada fábrica coincide con el valor
      actual citado en D7.

## 2. Catálogo: persistencia, servicio y API (PR 1)

- [x] 2.1 Test (`@DataJpaTest`): `ParametroReglaEntity` guarda y lee un override por clave.
      Implementar entidad + `ParametroReglaRepository`.
- [x] 2.2 Test: `CatalogoParametrosService.vigentes()` devuelve fábrica sin overrides, override si
      existe, y el mismo objeto en dos llamadas seguidas (cache). Implementar.
- [x] 2.3 Test: `guardar(cambios, usuario)` es todo o nada (un cambio inválido en el lote → nada
      persistido), `valor = null` borra el override, invalida el cache, setea `updatedBy`/`updatedTs`
      y llama a `HistorialService.registrarConfiguracion` con las claves cambiadas. Implementar
      (agregar el overload de `registrarConfiguracion` con detalle).
- [x] 2.4 Test (`@WebMvcTest`): `GET /api/rules/parametros` devuelve cada clave una vez con
      `usadoPor`; `PUT` válido → 200 con el catálogo; `PUT` inválido → 400 con
      `errores[{clave, mensaje}]`. Implementar `ReglasParametrosController` y DTOs.
- [x] 2.5 Test: `GET /api/rules/schema` incluye `parametros` en cada nodo de regla y `[]` en los
      especiales. Agregar el campo a `RuleNodeDto` y al controller.

## 3. Traza de evaluación (PR 2)

- [x] 3.1 Test: `Evaluacion.comparar(...)` devuelve el booleano correcto para `LT/LE/GT/GE/EQ`,
      registra `CUMPLE/NO_CUMPLE`, registra `SIN_DATO` con recibido `null`, y guarda el **valor**
      del umbral (no la referencia). Implementar `Evaluacion`, `Comparacion`, `Operador`.
- [x] 3.2 Test: `Evaluacion.numero(p)` con un `p` no declarado lanza `ParametroNoDeclaradoException`
      nombrando regla y clave. Implementar.
- [x] 3.3 Test: `compararFijo(...)` registra `configurable = false` y sin clave. Implementar.
- [x] 3.4 Test del orquestador con reglas falsas: rama RIEGO bloqueada → las siguientes de RIEGO
      quedan `OMITIDA_RAMA_BLOQUEADA` con `bloqueadaPor`; `ABORT_ALL` → resto `NO_ALCANZADA`;
      las acciones devueltas son idénticas a las de hoy para las mismas reglas. Agregar a `Rule`
      `parametros()` y la firma puente `evaluate(ctx, ev)` (D3); `RuleOrchestrator.evaluate(ctx,
      origen)` arma una `Evaluacion` por regla con un único `vigentes()` por sector y devuelve
      `ResultadoEvaluacion(acciones, traza)`.
- [x] 3.5 Test: `TrazaEvaluacionStore` guarda la última por sector **y por origen**, la de
      telemetría no se pisa con la de barrido, y soporta escrituras concurrentes (dos hilos,
      1 000 escrituras, sin excepciones y con la última de cada origen). Implementar.
- [x] 3.6 Test: `NurseryService.updateTelemetry` guarda trazas `TELEMETRIA` y
      `NurseryWatchdog.evaluarTodos` guarda `BARRIDO`; la cantidad de llamadas a `HistorialService`
      no cambia respecto de hoy. Adaptar los dos llamadores.
- [x] 3.7 Test (`@WebMvcTest`): `GET /api/rules/evaluaciones/{id}` → 200 / 204 (sin evaluar) / 404
      (sector inexistente); `?origen=` filtra; sin `origen` devuelve la más reciente. Implementar.

## 4. Migración de las reglas existentes (PR 3)

Una regla por tarea. En cada una: el test nuevo verifica **acciones iguales a las de hoy** y las
comparaciones esperadas en la traza; después se mueve la regla a `evaluate(ctx, ev)`, declara sus
`parametros()` y deja de leer `@Value`/literales/`configuracion_operativa`.

- [x] 4.1 `ManualLockRule`: sin parámetros; traza con la condición fija "bloqueo manual activo".
- [x] 4.2 `StaleSensorRule`: calcula la antigüedad con `ctx.zona().getLastReadingTime()` y
      `ctx.now()` contra `seguridad.antiguedad-max-lectura`; sin lectura → `SIN_DATO` y bloquea
      (igual que hoy). Quitar `sensorStale` de `RuleContext` y de `RuleContextTestFactory`;
      `NurseryService` (vista, `:95-121` y `:531-533`) y `NurseryWatchdog` leen el umbral del
      catálogo. Quitar `stale-threshold-ms` de `application.properties`.
- [x] 4.3 `WeatherOverrideRule` → `riego.lluvia-probabilidad`; sin pronóstico → `SIN_DATO` y sigue.
      Quitar `rain-threshold-pct`.
- [x] 4.4 `DailyVolumeLimitRule` → `riego.max-riegos-24h`.
- [x] 4.5 `DailyDoseLimitRule` → `insumo.max-dosis-24h`.
- [x] 4.6 `IrrigationRule` → `riego.umbral-humedad`, `riego.max-riegos-24h-sector`,
      `riego.tiempo-max-apertura`. Test extra: cambiar `idealMin` de `humSus` **no** cambia la
      decisión. Borrar `ConfiguracionService.getRiegoHumSusUmbral()` y el fallback 40
      (`IrrigationRule.java:41`). Primer test de esta regla en el repo.
- [x] 4.7 `SupplyRule` → `diagnostico.confianza-minima` + condición fija `estado == critical`.
      `DiagnosticoService.esConcluyente` lee el mismo parámetro; quitar `confianzaMinima` de
      `CapturaProperties` y `capturas.confianza-minima` de `application.properties`. Primer test
      de esta regla en el repo.
- [x] 4.8 `ShadingRule` → `mediasombra.uv-umbral`, `mediasombra.apertura-proteccion-uv`,
      `mediasombra.apertura-maxima`. Conserva el `@Value` de `sowing-date-iso` (excepción de D7).
- [x] 4.9 `FollowUpRule`: sin parámetros ni comparaciones; la traza registra sólo su acción.
- [x] 4.10 Test de arquitectura: ninguna clase en `engine/rules/` tiene `@Value` salvo
      `ShadingRule.sowingDateIso`, y toda regla registrada implementa `evaluate(ctx, ev)`. Borrar
      la firma vieja `evaluate(ctx)` de `Rule`.

## 5. Mudanza de `configuracion_operativa` (PR 3)

- [x] 5.1 Test: `GET /api/configuracion` ya no trae `riegoTiempoMaxSeg` ni
      `mediasombraAperturaMaxPct`; `PUT` sin esos campos → 200. Quitar los campos de entidad, DTO,
      `defaultOperativa()` y validación.
- [x] 5.2 Test: `validarRustificacion` rechaza una etapa con apertura mayor al valor vigente de
      `mediasombra.apertura-maxima` del catálogo. Adaptar `ConfiguracionService:175-176,228-251`.
- [ ] 5.3 (escrito; falta verificarlo a mano contra una base sembrada con valores no de fábrica) Escribir `res/migracion-catalogo-parametros.sql`: copia a `parametro_regla` los dos
      valores sólo si difieren de fábrica, baja las columnas, y trae comentado el bloque inverso
      (`ADD COLUMN … DEFAULT`). Encabezado con el mismo formato que `migracion-quitar-simulador.sql`.
      Verificarlo a mano contra una base sembrada con valores no de fábrica.
- [ ] 5.4 (README hecho; falta CLAUDE.md §6, fuera del alcance del backend) README del backend: sumar el script a la sección de migraciones manuales y documentar
      `/api/rules/parametros` y `/api/rules/evaluaciones`. Actualizar `CLAUDE.md` §6 (migraciones
      pendientes).
- [x] 5.5 (desvío: el fixture se escribió a mano, no lo genera un test del backend; un test del frontend
      `catalogoReglas.test.ts` lee los enums `Parametros*.java` y falla si divergen) Test que genera `fe/data/mock/catalogoReglas.fixture.json` desde el catálogo de fábrica y
      falla si el archivo commiteado difiere (D8 / *Risks*).

## 6. Frontend: capa de datos (PR 4)

- [x] 6.1 Tipos en `fe/types/domain.ts`: `ParametroRegla`, `ReglaCatalogo`, `CatalogoReglas`,
      `CambioParametro`, `TrazaEvaluacion`, `TrazaRegla`, `Comparacion`, `OrigenEvaluacion`;
      `RuleNode.parametros: string[]`; quitar los dos campos de `ConfigOperativa`.
- [x] 6.2 Test (`httpRepository.test.ts`): `getCatalogoReglas`, `saveParametros` (incluido 400 con
      `errores`), `getRuleSchema`, `getTrazaEvaluacion` (200 / 204 → `null`). Implementar en
      `DataRepository` y `HttpRepository`.
- [x] 6.3 Test: el mock arma el catálogo desde `catalogoReglas.fixture.json`; `saveParametros`
      valida rango con la misma función que la UI y persiste en memoria; la traza mock de un sector
      es determinística y coherente con su lectura (humedad < umbral ⇒ `CUMPLE`). Implementar.
- [x] 6.4 Test: `useRuleEngineSchema` obtiene el esquema del repositorio (sin `fetch`); el Historial
      sigue mostrando el DAG en modo `mock`. Migrar el hook.
- [x] 6.5 Test de `lib/parametrosValidation.ts`: valida tipo/rango/decimales/ventana a partir del
      DTO, sin constantes propias. Implementar.

## 7. Frontend: pestaña Parámetros (PR 5)

- [ ] 7.1 Test: `/reglas` aparece en el `Sidebar` y monta `ReglasPage` con pestañas Parámetros /
      Inspector. Ruta y página.
- [ ] 7.2 Test: las reglas se agrupan por rama en orden de prioridad, colapsadas, con resumen
      "N parámetros · M modificados"; una regla sin parámetros dice "Sin parámetros configurables".
      `ReglaCard`.
- [ ] 7.3 Test: un parámetro compartido muestra el chip con las otras reglas; editarlo bajo una
      regla cambia el valor mostrado bajo la otra; guardar envía **un** cambio para esa clave.
      Borrador indexado por clave (`useCatalogoReglas`).
- [ ] 7.4 Test: campo por tipo (`NumberField` reutilizado, `HoraField`, `VentanaField`); valor
      inválido marca el campo y deshabilita Guardar; "Restablecer" envía `valor: null`.
- [ ] 7.5 Test: búsqueda, filtro por rama, "sólo modificados" y "Ver por parámetro" (cada clave una
      vez con `usadoPor`).
- [ ] 7.6 Test: un 400 del servidor muestra cada error junto a su parámetro y conserva el borrador.
- [x] 7.7 Configuración: test de que `LimitesActuadoresForm` ya no muestra los dos campos, muestra
      el enlace a "Motor de reglas" y rotula volumen diario y dosis máx. como "no intervienen en
      decisiones del motor"; `RustificacionPlanForm` valida contra `mediasombra.apertura-maxima`
      del catálogo. Adaptar `ConfiguracionPage`, `configValidation.ts` y `data/mock/config.ts`.

## 8. Frontend: Inspector (PR 6)

- [ ] 8.1 Test de `trazaANodos(schema, traza)`: mapea cada estado de la traza a pasó / bloqueó /
      pospuso / accionó / omitida / no alcanzada, y el terminal de cada rama a "accionó" o "no
      accionó". Función pura, sin regex sobre textos.
- [ ] 8.2 Test: `ReglaNode` (nodo personalizado de React Flow) muestra hasta dos comparaciones
      "recibido op umbral" con su resultado, candado si no es configurable y "sin dato" para
      `SIN_DATO`. Estilos con CSS Modules y tokens.
- [ ] 8.3 Test: `RuleGraph` con prop `traza` usa `trazaANodos`; sin `traza` se comporta igual que
      hoy (los tests del Historial no cambian).
- [ ] 8.4 Test: `InspectorTab` con selector zona → sector, origen y "Actualizar"; respeta
      `?sector=`; sin traza muestra "sin evaluaciones desde el último arranque".
- [ ] 8.5 Test: clic en un nodo abre el panel con todas las comparaciones, acciones y motivo, y
      "Editar parámetro" navega a Parámetros con esa regla abierta.
- [ ] 8.6 Detalle de sector: enlace "Ver última evaluación del motor" → `/reglas?tab=inspector&sector=…`.

## 9. Cierre

- [ ] 9.1 Verificación manual con backend + simulador: publicar humedad 38 % en una zona y ver en el
      Inspector `IrrigationRule` "38 % < 42 % ✓"; subir el umbral a 35 desde Parámetros y ver
      "38 % < 35 % ✗" en la siguiente telemetría.
- [ ] 9.2 Verificación en `npm run dev:demo`: sección completa sin backend.
- [ ] 9.3 `CLAUDE.md`: mencionar la sección "Motor de reglas" (§4) y el catálogo como única fuente de
      umbrales de reglas (§6, convenciones internas).

## Review Workload Forecast

| Área | Producción | Tests | Total |
|---|---|---|---|
| Backend catálogo + API (§1–2) | ~550 | ~450 | ~1 000 |
| Backend traza + orquestador (§3) | ~350 | ~350 | ~700 |
| Backend migración reglas + config (§4–5) | ~300 (neto, mucho es reemplazo) | ~450 | ~750 |
| Frontend datos (§6) | ~350 | ~250 | ~600 |
| Frontend Parámetros + Configuración (§7) | ~600 | ~300 | ~900 |
| Frontend Inspector (§8) | ~400 | ~250 | ~650 |
| **Total** | **~2 550** | **~2 050** | **~4 600** |

- **Líneas cambiadas estimadas:** ~4 600 (más el fixture JSON generado, que no se revisa a mano).
- **¿Supera 400?** Sí, unas 11 veces.
- **400-line budget risk:** High.
- **Chained PRs recommended:** Yes. Seis PRs encadenados, uno por bloque (§1–2, §3, §4–5, §6, §7,
  §8). Cada uno deja `main` funcionando: PR 1 y PR 2 agregan sin cambiar comportamiento; PR 3 es
  el único que cambia contratos (DTO de configuración y properties) y debe mergearse junto con
  PR 4 si no se quiere romper el frontend entre medio (o PR 4 primero con los campos opcionales).
  Aun así, los PR 1, 3 y 5 rondan 750–1 000 líneas con tests: si el equipo exige ≤ 400, partir
  cada uno en "tipos/servicio" y "API/UI".
- **Decision needed before apply:** Yes — estrategia de entrega (encadenados vs. `size:exception`)
  y DA-1…DA-6.
