# Tasks: implement-reglas-riego

**TDD estricto:** en cada tarea, primero el test en rojo, después el código mínimo, después
refactor. Cada bloque es un commit (conventional commits, sin atribución de IA) y cierra sólo con la
suite en verde:
- backend: `cd Desarrollo/backend && ./mvnw test`
- frontend: `cd Desarrollo/frontend && npm test && npm run lint`
- firmware: compila con `config.h` copiado de `config.example.h`

`be/`, `bt/`, `res/`, `fw/`, `fe/`: ver abreviaturas de `design.md`. Paquete nuevo: `be/engine/riego/`.
Hasta el bloque 10 el comportamiento del riego **no cambia**: los bloques 1–9 agregan piezas
probadas sin registrarlas.

## 0. Decisiones previas

- [x] 0.1 Aplicar los defaults de DA-1 … DA-13 (`design.md`): confirmados todos con el default del
      diseño. Estrategia de entrega confirmada: commits por bloque en la rama
      `feat/implementar-nuevas-reglas` (no PRs encadenados). DA-5 es **BLOQUEANTE para operar con
      plantines reales**, no para implementar: anotarlo en el README del backend (bloque 14).
- [ ] 0.2 Confirmar que el frontend de `add-catalogo-umbrales-reglas` (§7–8) cerró antes de empezar
      el bloque 13.

## 1. Reloj y zona horaria (D7)

- [x] 1.1 Test: `ZonaHorariaVivero.ZONA` es `America/Argentina/Buenos_Aires` y el bean `Clock` del
      contexto de Spring usa esa zona.
- [x] 1.2 Test: `NurseryService.updateTelemetry` arma el `RuleContext` con `now` del `Clock`
      inyectado (`Clock.fixed`), no con `Instant.now()` (`NurseryService.java:582`). Ídem
      `NurseryWatchdog.evaluarTodos` (`NurseryWatchdog.java:130-131`). Implementar.

## 2. Traza: ventanas horarias (D7)

- [x] 2.1 Test de `VentanaHoraria.contieneHastaElMinuto`: 05:59:59 ✗, 06:00:00 ✓, 17:59:59 ✓,
      18:00:00 ✓, 18:00:59 ✓, 18:01:00 ✗; y una ventana que cruza la medianoche (22:00-06:00):
      06:00:30 ✓, 06:01:00 ✗.
- [x] 2.2 Test: `Evaluacion.compararVentana("Hora local", 17:42, ventana)` registra
      `Comparacion(clave = riego.ventana-normal, recibido "17:42", operador EN, umbral "06:00-18:00",
      configurable = true, CUMPLE)`; con hora `null` → `SIN_DATO`; con un parámetro no declarado →
      `ParametroNoDeclaradoException`. Agregar `Operador.EN("∈")`. Implementar.

## 3. Modelo: acción tipada, contexto de riego y columnas (D2, D11)

- [x] 3.1 Test: `RuleAction.of(...)` y `noopInfo(...)` siguen dando `detalle == null`; una acción
      con `DetalleRiego` lo conserva; `ActionType.ALERTA.isBlocking()` es `false`.
- [x] 3.2 Test: el constructor de 9 argumentos de `RuleContext` deja `riego() == ContextoRiego.vacio()`.
      Agregar `ContextoRiego` y el constructor secundario (los 13 `new RuleContext(...)` no cambian).
- [x] 3.3 Test (`@DataJpaTest`): `HistorialEventoEntity` guarda y lee `regla`, `alerta`, `volumenL`,
      `duracionSeg` (nulos por defecto); `ZonaEntity` guarda `humSusTs`.
- [x] 3.4 Test (`@DataJpaTest`): `HistorialRepository.ultimosPorSector(zonaId, desde)` devuelve el
      `MAX(ts)` por sector y tipo (`Riego`, `Insumo`) y, para `Riego`, el último con
      `regla = DeficitCriticoRule`; ignora otras zonas y eventos anteriores a `desde`.
- [x] 3.5 Test (`@DataJpaTest`): `HistorialRepository.riegosDesde(desde)` devuelve los "Riego" con
      `duracionSeg` no nulo y `ts ≥ desde`. Implementar.
- [x] 3.6 Test: `HistorialEvento` (DTO) expone los cuatro campos nuevos (aditivo).

## 4. Frescura de la humedad de sustrato (D9.1)

- [x] 4.1 Test: `updateTelemetry` escribe `zona.humSusTs` sólo si el payload trae `humSus`; una
      lectura sin `humSus` lo deja como estaba.
- [x] 4.2 Test de `StaleSensorRule`: zona fresca y `humSusTs` de hace 300 s con umbral 90 →
      `ABORT_RIEGO` y comparación "Antigüedad de la humedad de sustrato 300 > 90"; con 89 s →
      `NOOP_INFO`; sin `humSusTs` → `SIN_DATO` y `ABORT_RIEGO`. Implementar.

## 5. Pronóstico con milímetros (D6)

- [x] 5.1 Test (`MockRestServiceServer` o cliente falso): `OpenMeteoWeatherClient` pide
      `precipitation` y `timezone=America/Argentina/Buenos_Aires`, y con un `Clock` fijo en
      13:30 UTC toma como hora actual la marca `T10:00`.
- [x] 5.2 Test de `WeatherForecast.lluviaProxima(10:20, 4)`: marcas 11–14 → (máx. 70 %, 5 mm);
      excluye 10:00 y 15:00; con menos horas disponibles devuelve `horasCubiertas` < 4.
- [x] 5.3 Test: el constructor viejo de `WeatherForecast` deja `horas` vacía y el widget del
      dashboard sigue igual (`NurseryService.buildWeatherFromForecast`). Implementar.

## 6. Catálogo: parámetros de riego v2 (D8)

- [x] 6.1 Test (`ParametrosRealesTest`): el catálogo real tiene las 15 claves de la tabla D8 con sus
      tipos, unidades, fábricas, rangos y decimales, **además** de las tres que salen (se van en el
      bloque 10). Mover las definiciones de `ParametrosRiegoV2Fixture` a `ParametrosRiego` con
      etiqueta, descripción y `refSpec` (`reglas_v2 §5 R-0x / §11 Riego`). Fábricas nuevas:
      `umbral-humedad` 45, `lluvia-probabilidad` 70 — **desvío**: esas dos se cambian en el bloque
      10.5 (conmutación), porque hasta entonces `IrrigationRule` y `WeatherOverrideRule` las leen y
      el comportamiento no debe cambiar; en 6.1 quedan en 42 y 60.
- [x] 6.2 Test: `CatalogoParametros` real rechaza (al guardar, vía servicio) `crítico ≥ umbral`,
      `umbral ≥ objetivo`, `bloqueo > alerta` y `volumen-max / caudal × 3600 > 1200`; los valores de
      fábrica cumplen las cuatro. Llevar las restricciones a `restriccionesReales()`.
- [x] 6.3 Test: un `ConsumidorParametros` que no es `Rule` (falso, `"DespachoRiego"`) aparece en
      `usadoPor` y no en `reglas()`; uno que declara una clave inexistente impide construir el
      catálogo. `Rule` extiende `ConsumidorParametros`. Implementar.
- [x] 6.4 Borrar `ParametrosRiegoV2Fixture` y apuntar `CatalogoParametrosTest` /
      `CatalogoParametrosServiceTest` a las definiciones reales.

## 7. Piezas puras: cálculo de riego y ciclo de lectura (D3, D5)

- [x] 7.1 Test de `CalculoRiego`: h 44,9 → 4,02 L / 483 s; h 44 → 4,2 L / 504 s (no 505);
      `litros-por-punto` 0,3 y h 40 → 6 L / 720 s; R-02 con caudal 60 → 6 L / 360 s; volumen 10 y caudal
      30 (o 6 L a 18 L/h) → 1200 s exactos sin recorte; volumen 10 y caudal 20 → 1200 s, `recortado = true`.
- [x] 7.2 Test de `CicloLectura.inicio(ahora, minutos)`: con 240 → 01:59 da 22:00 del día anterior,
      02:00 da 02:00, 13:59:59 da 10:00, 14:00 da 14:00; con 300 → 23:00 da 22:00 (último ciclo del
      día, corto); `minutos` 5 se acota a 60 y 600 a 360.
- [x] 7.3 Test: `ConfiguracionService` rechaza `intervaloSensadoMinutos` fuera de 60–360 con 400
      (`ConfiguracionService.java:223`). Implementar.

## 8. Cola y despacho (D4) — sin conectar a las reglas

- [x] 8.1 Test de `ColaRiego`: `solicitar` reemplaza la solicitud del sector; `retirar` la saca;
      `pendientes(zona)` sale ordenada por `sector.n`; acceso concurrente desde dos hilos no pierde
      solicitudes.
- [x] 8.2 Test de `ComandoActuadorPublisher`: arma el payload del contrato
      (`commandId`, `actuador`, `accion`, `parametros`) y propaga la falla del gateway como resultado
      (no la traga). Mover `publishCommand` de `ActionExecutor` (`ActionExecutor.java:148-169`);
      bomba y mediasombra siguen igual.
- [x] 8.3 Test de `DespachoRiego.tick()` (reloj fijo, repositorios falsos): 100 pendientes en MZ-2 →
      publica 10 (MZ-2-001…010), registra 10 "Riego" con volumen, duración, humedad y regla;
      siguiente tick sin vencimientos → 0; vence MZ-2-003 (+5 s) → abre MZ-2-011; dos zonas no se
      bloquean entre sí; `sectores-simultaneos` = 3 → nunca más de 3.
- [x] 8.4 Test: bloqueo manual activo del sector o de la MZ → la solicitud se descarta sin comando;
      gateway que falla → no se registra el riego y la solicitud sigue.
- [x] 8.5 Test: tras "reiniciar" (despacho nuevo con el mismo historial) cuenta como en curso los
      riegos con `ts + duracionSeg + 5 s > ahora`.
- [x] 8.6 Test: `estadoValvula(sectorId)` → `Regando` / `En cola` / `Cerrada`; `HistorialService.registrarRiego(sector, detalle, regla, ts)`
      guarda los campos nuevos y mantiene el seguimiento (`withSeguimiento`). Implementar.

## 9. Reglas nuevas (D1, D9.2, D10) — clases sin `@Component` todavía

Cada test usa `ReglaTestSupport` y verifica acciones **y** comparaciones de la traza.

- [x] 9.1 `CicloLecturaRiegoRule`: riego del sector a las 10:12 y evaluación 13:59 → `ABORT_RIEGO`;
      a las 14:00:10 → `NOOP_INFO`; riego en curso (de un ciclo anterior) → `ABORT_RIEGO`;
      `ContextoRiego.vacio()` → `NOOP_INFO`.
- [x] 9.2 `SustratoSaturadoRule` (R-04): 74 → `NOOP_INFO`; 75 → `ABORT_RIEGO` sin `ALERTA`;
      80 → `ABORT_RIEGO` + `ALERTA` WARNING; override bloqueo 70 → 72 bloquea.
- [x] 9.3 `DeficitCriticoRule` (R-02): 34 → `ACTIVAR_VALVULA` (6 L, 720 s) + `ALERTA` CRITICAL;
      35 → `NOOP_INFO`; último R-02 hace 11 h 59 min → `ABORT_RIEGO` (tope); hace 12 h → riega;
      sin humedad → `SIN_DATO` y `NOOP_INFO`.
- [x] 9.4 `FueraDeVentanaRiegoRule` (R-05): con h 40, los cinco bordes de la spec; con h 34 o h 50
      → `NOOP_INFO` "no aplica"; la comparación de hora usa `EN` con la ventana vigente.
- [x] 9.5 `PausaTrasAplicacionRule` (R-06): aplicación hace 5 h 59 min 59 s → `ABORT_RIEGO`;
      hace 6 h → `NOOP_INFO`; sin aplicaciones → `NOOP_INFO`; h 34 → "no aplica".
- [x] 9.6 `PosponerPorLluviaRule` (R-03): (70 %, 5 mm) → `POSTPONE_RIEGO` + `ALERTA` INFO;
      (95 %, 4,9 mm) y (69 %, 20 mm) → `NOOP_INFO`; sin pronóstico → dos `SIN_DATO` y `NOOP_INFO`;
      h 60 o h 34 → "no aplica" sin alerta; `lluvia-ventana` 2 ignora lluvia de la hora +3.
- [x] 9.7 `RiegoPorDeficitRule` (R-01): 44 → `ACTIVAR_VALVULA` (4,2 L, 504 s) con motivo legible;
      45 → `NOOP_INFO`; 34 → `NOOP_INFO` "lo cubre R-02"; override umbral 50 y h 48 → riega.
- [x] 9.8 Test de orquestación con las siete reglas nuevas + `ManualLockRule` + `StaleSensorRule`
      (orquestador real, sin Spring): h 34 a las 23:30 con lluvia y aplicación reciente → una sola
      `ACTIVAR_VALVULA` (de R-02) y la traza muestra R-05/R-06/R-03 "no aplica"; h 40 con lluvia
      → `POSTPONE_RIEGO` y R-01 `OMITIDA_RAMA_BLOQUEADA`; h 40 a las 19:00 → R-05 corta; bloqueo
      manual → nada en RIEGO.

## 10. Conmutación (cambia el comportamiento)

- [x] 10.1 Test: `ActionExecutor.execute(acciones, ctx, TELEMETRIA)` con `ACTIVAR_VALVULA` encola y
      no publica; sin ella retira; con `BARRIDO` no toca la cola; `ALERTA` se persiste como evento
      "Alerta" una vez por (MZ, regla, inicio de ciclo) aunque llegue de 100 sectores.
- [x] 10.2 Test: `updateTelemetry` arma `ContextoRiego` con **una** consulta por zona (verificar con
      el mock del repositorio) y el inicio de ciclo del `Clock`.
- [x] 10.3 Test: el snapshot toma la válvula de `DespachoRiego.estadoValvula`
      (`NurseryService.java:148-149`).
- [x] 10.4 Test de integración (**sin** `@SpringBootTest`: piezas reales cableadas a mano, repositorios y broker falsos, reloj fijo 10:05; ver desvíos de `design.md`): telemetría de
      MZ-2 con `humSus` 40 → tras un tick, 10 comandos `valve ON` con `durationSec` 600 (5 L) y 90
      sectores "En cola"; segunda telemetría a las 10:06 → ningún comando nuevo para los 10 ya
      regados; avanzar el reloj 605 s → tick abre los 10 siguientes.
- [x] 10.5 Registrar las siete reglas (`@Component`). Pasar las fábricas de `riego.umbral-humedad`
      a 45 y `riego.lluvia-probabilidad` a 70 (ver desvío de 6.1; ajustar `ParametrosRealesTest`). Borrar `IrrigationRule`,
      `WeatherOverrideRule`, `DailyVolumeLimitRule`, sus tests, y las claves
      `riego.tiempo-max-apertura`, `riego.max-riegos-24h`, `riego.max-riegos-24h-sector`
      (actualizar `ParametrosRealesTest`). Borrar `TIEMPO_MAX_PATTERN`/`parseTiempoMax` y el enganche
      "Regando" de `ActionExecutor`. `ReglasArquitecturaTest` sigue en verde.
- [x] 10.6 Test (`@WebMvcTest`): `/api/rules/schema` lista en RIEGO, en orden, Ciclo → R-04 → R-02 →
      R-05 → R-06 → R-03 → R-01, cada uno con sus claves; `/api/rules/parametros` da a
      `riego.umbral-humedad` el `usadoPor` de la spec y a `riego.sectores-simultaneos`
      `["DespachoRiego"]`.
- [x] 10.7 `res/migracion-reglas-riego.sql`: borra overrides de las tres claves eliminadas, consulta
      comentada para detectar overrides que violan las restricciones nuevas, índice opcional
      `historial_evento(zona_id, tipo, ts)`, bloque de rollback comentado (`actuador_valve` →
      'Cerrada'). Property `yerbanalytics.riego.despacho-intervalo-ms=10000`.

## 11. Contrato MQTT, firmware y simulador (D13)

- [x] 11.1 Test: `ContratoNodo.DURACION_VALVULA_MAX_SEG == 1200` y `CalculoRiego` lo usa como tope.
- [ ] 11.2 `fw/comun/contrato.h`: `CONTRATO_VALVULA_DURACION_MAX_SEG 1200` con comentario de fuente
      de verdad. `fw/comun/config.example.h`: `LIMITE_VALVULA_SEG_MAX 1200` (con el porqué: 6 L a
      30 L/h = 720 s; 10 L = 1200 s) y `CAUDALIMETRO_INSTALADO 0`. `fw/actuacion/act_valvula.cpp`:
      `static_assert` del límite y saltear la verificación de flujo con el flag en 0. Compilar.
      _(Escrito y revisado a mano; SIN COMPILAR: no hay toolchain en esta máquina. Queda sin tildar hasta compilar.)_
- [x] 11.3 Test (simulador): `contract.ts` exporta `VALVE_MAX_DURATION_SEC = 1200` y el log de
      comandos avisa si `durationSec` lo supera. Sin cambios en su API ni en el backend.

## 12. Baja de `riegoVolMaxDiarioMl` (DA-10)

- [x] 12.1 Test backend: `GET /api/configuracion` ya no trae `riegoVolMaxDiarioMl`; sacarlo de la
      entidad, el DTO y `ConfiguracionService`. Sumar `DROP COLUMN riego_vol_max_diario_ml` al script.
- [x] 12.2 Test frontend: `LimitesActuadoresForm` ya no muestra el campo; tipos y mock sin él.

## 13. Frontend (D12) — después de 0.2

- [x] 13.1 `OperadorComparacion` suma `'EN'`; el Inspector lo muestra como "∈" (test del render de
      una comparación de ventana).
- [x] 13.2 Regenerar `fe/data/mock/catalogoReglas.fixture.json` con el catálogo real (las 7 reglas
      nuevas, sin las 3 viejas) y reescribir `fe/data/mock/trazaReglas.ts` para esas reglas; tests
      del mock en verde.
- [x] 13.3 Test: la pestaña Parámetros muestra `riego.sectores-simultaneos` en el grupo "Ejecución del
      riego" (parámetros cuyo `usadoPor` no es una regla).
- [x] 13.4 Test: `RuleGraph` mapea un evento "Riego" a `record.regla` (o `RiegoPorDeficitRule` si
      falta) en vez de `IrrigationRule` (`RuleGraph.tsx:129`).
- [x] 13.5 Test: el Historial muestra volumen, duración y regla de los riegos y nivel de las
      alertas; el filtro de tipo suma "Alerta". El mock del mapa usa 45 % y "En cola".

## 14. Documentación

- [x] 14.1 Actualizar `docs-motor-reglas-e-integracion/diferencias-motor-reglas-vs-reglas-v2.md`:
      §2 (disparadores, orden, acciones tipadas, estado de la válvula, origen de parámetros, zona
      horaria), §3 (R-01…R-06 implementadas, con las desviaciones: ciclo de lectura, tope de R-02
      sin S-06, ventana cerrada a minuto), §4.2, §4.9 (principios 3, 6, 8 y 9), §5 (puntos 1, 2 y
      13 resueltos; nuevo: pronóstico consultado sin `timezone` hasta este cambio), §6.1 y §6.3.
- [x] 14.2 `circuito-sensado-a-motor.md`: comando con duración calculada, despacho por tandas,
      columnas nuevas, límite de válvula 1200 s; §5.4 (ACK) sigue abierto pero ya no deja la válvula
      enganchada.
- [x] 14.3 README del backend: script `migracion-reglas-riego.sql`, despacho y su property, DA-5
      (no operar con plantines reales sin E-01/S-06). README del firmware: límite y flag del
      caudalímetro, actualizar el `config.h` local. `CLAUDE.md` §6/§6.2: el contrato MQTT suma la
      duración máxima de la válvula.

## 15. Verificación manual con simulador

- [ ] 15.1 Backend + simulador, en horario de ventana: publicar `humSus` 40 en MZ-1 → el log del
      simulador muestra 10 comandos `valve ON` con `durationSec` 600 (5 L); el mapa muestra 10 "Regando" y
      90 "En cola"; con `riego.caudal-emisor` 120 L/h las tandas duran 150 s y la MZ termina en ~26 min.
- [ ] 15.2 `humSus` 30 → `durationSec` 720 (o 180 con 120 L/h) y un evento "Alerta" CRITICAL.
- [ ] 15.3 `humSus` 82 → nada en cola y un "Alerta" WARNING. Correr la ventana a una franja que no
      incluya la hora actual y publicar 40 → R-05 corta (Inspector); publicar 30 → R-02 riega igual.
- [ ] 15.4 Publicar de nuevo 40 en el mismo ciclo → ningún comando (Inspector: regla de ciclo).

## Review Workload Forecast

| Área | Producción | Tests | Total |
|---|---|---|---|
| Reloj, traza, modelo, frescura (§1–4) | ~250 | ~350 | ~600 |
| Pronóstico (§5) | ~120 | ~150 | ~270 |
| Catálogo (§6) | ~150 (mucho es mudanza del fixture) | ~120 | ~270 |
| Cálculo, ciclo, cola, despacho (§7–8) | ~380 | ~450 | ~830 |
| Reglas nuevas (§9) | ~550 | ~650 | ~1 200 |
| Conmutación + script (§10) | ~200 (neto: −~350 de lo borrado) | ~250 | ~450 |
| Contrato/firmware/simulador (§11) | ~40 | ~30 | ~70 |
| Baja de `riegoVolMaxDiarioMl` (§12) | ~40 | ~40 | ~80 |
| Frontend (§13) | ~200 + fixture regenerado | ~200 | ~400 |
| Docs (§14) | ~200 | — | ~200 |
| **Total** | **~2 130** | **~2 240** | **~4 370** |

- **Líneas cambiadas estimadas:** ~4 400 (sin contar el fixture JSON regenerado).
- **¿Supera 400?** Sí, unas 11 veces.
- **400-line budget risk:** High.
- **Chained PRs recommended:** Yes. Siete PRs encadenados, cada uno con `main` funcionando:
  PR 1 = §1–5 (infraestructura y pronóstico, sin cambio de riego); PR 2 = §6–8 (parámetros y
  despacho sin conectar); PR 3 = §9.1–9.4; PR 4 = §9.5–9.8; PR 5 = §10 + §11 (el que cambia el
  comportamiento; firmware y contrato van juntos porque el backend empieza a mandar hasta 1200 s);
  PR 6 = §12–13 (frontend); PR 7 = §14 (docs) o junto con PR 5. PR 2, 3 y 4 rondan 600–800 líneas
  con tests: si el equipo exige ≤ 400, partir §8 aparte y cada tanda de reglas en dos.
- **Decision needed before apply:** Yes — estrategia de entrega (encadenados vs. `size:exception`).
  Los defaults de DA-1…DA-13 se aplican tal cual (0.1).
