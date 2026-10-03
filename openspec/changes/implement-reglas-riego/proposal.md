# Change: implement-reglas-riego

## Why

El riego autónomo que tiene hoy el motor no es el de `reglas_v2.md` §5, y además **riega una sola
vez en la vida de cada sector**:

1. **Enganche "Regando".** `ActionExecutor` pone `actuadorValve = "Regando"` y sólo publica el
   comando en la transición (`engine/ActionExecutor.java:78-85`). Nada lo vuelve a "Cerrada"
   (el único otro `setActuadorValve` es el alta de sectores, `service/TopologiaService.java:241`).
   Tras el primer riego no sale nunca más un comando de válvula para ese sector.
2. **La lluvia pospone también el déficit crítico.** `WeatherOverrideRule` (prioridad 2, rama
   RIEGO) emite `POSTPONE_RIEGO` sólo con la probabilidad de la hora actual
   (`rules/WeatherOverrideRule.java:70-85`) y corta la rama antes de `IrrigationRule`. v2 pide lo
   contrario (principio 3, R-02): con déficit crítico se riega aunque llueva.
3. **No hay volumen ni tiempo calculado.** El riego dura `riego.tiempo-max-apertura` (120 s) fijo
   (`rules/IrrigationRule.java:116-119`); v2 pide `V = (65 − h) × 0,2 L`, tope 6 L, y
   `t = V / caudal`. Aunque se calcule, **el firmware recorta a 120 s**
   (`embebido/comun/config.example.h:96`, `actuacion/act_valvula.cpp:25-29`): 6 L a 30 L/h son 720 s.
4. **Los 100 sectores de una MZ abren a la vez** (`service/NurseryService.java:502-551`); v2 pide
   de a 10 y en orden de numeración.
5. **Límites diarios que v2 eliminó** (Anexo A): `DailyVolumeLimitRule` (≥ 2 riegos/24 h) y la
   guarda de 1 riego/24 h dentro de `IrrigationRule` (`rules/IrrigationRule.java:98-113`).
6. **Faltan R-02, R-04, R-05 y R-06**, la ventana horaria y la zona horaria fija
   (hoy `ZoneId.systemDefault()`, y Open-Meteo se consulta sin `timezone`, así que la "hora actual"
   del pronóstico se busca en un arreglo en GMT: `engine/weather/OpenMeteoWeatherClient.java:268-275,343`).

## What Changes

- **Seis reglas nuevas en la rama RIEGO** (R-01…R-06) más una guarda de **ciclo de lectura**, en
  reemplazo de `IrrigationRule`, `WeatherOverrideRule` y `DailyVolumeLimitRule`. La prioridad
  "R-02 gana a R-03/R-05/R-06" se expresa **sin tocar el orquestador**: R-02 corre antes que esas
  compuertas y las compuertas sólo actúan cuando aplica R-01 (comparten los umbrales).
- **Volumen y tiempo calculados** con el caudal calibrado (parámetro global), viajando tipados en la
  acción (sin `[tiempo-max-seg=N]` en el texto).
- **Cola de riego por MZ** (`DespachoRiego`): las reglas deciden; el despacho abre de a
  `riego.sectores-simultaneos` sectores por MZ, en orden de `n`, cada 10 s.
- **Cierre de la válvula por tiempo.** El estado "regando" deja de ser un enganche: se deriva del
  evento "Riego" del historial (`ts + duracionSeg`), que se escribe al despachar con volumen,
  duración y regla (HU-06 CA-05). Sin escuchar el ACK.
- **Pronóstico con milímetros y ventana** (`precipitation` + `timezone` en Open-Meteo).
- **Zona horaria fija** `America/Argentina/Buenos_Aires` y `Clock` inyectable.
- **Catálogo:** 14 parámetros de riego de §11 (base: `ParametrosRiegoV2Fixture`), salen 3.
- **Alertas mínimas:** eventos "Alerta" en el historial con nivel `INFO/WARNING/CRITICAL`, uno por
  MZ y ciclo de lectura.
- **Contrato MQTT:** duración máxima de la válvula 120 → 1200 s, declarada en `contrato.h` y
  espejada en `ContratoNodo` y en el simulador.

## Out of scope

- S-01…S-06 salvo lo imprescindible (ver design D9: frescura de `humSus` dentro de
  `StaleSensorRule` y tope de R-02 con el parámetro que después reutiliza S-06).
- Mediasombra, nutrición, reglas por diagnóstico, efectividad (E-01), operación sin internet más
  allá de "sin pronóstico no se pospone" (O-01, que ya es el comportamiento).
- Que el backend **pida** la lectura al nodo cada 4 h (S-02). Mientras tanto se emula la cadencia
  con el ciclo de lectura (design D5).
- Escuchar el ACK del nodo y el tópico de errores del ESP32.
- Integrar las alertas a la campana del dashboard (se derivan del estado de los sectores,
  `service/NurseryService.java:418-428`); HU-10 completo es otro cambio.
- Dejar de persistir el Registro de Inacción por ciclo (DA-6 de `add-catalogo-umbrales-reglas`).
- Caudal por zona (design D8).

## Capabilities

### New Capabilities

- `reglas-riego`: R-01…R-06, ciclo de lectura, cálculo de volumen/tiempo, cola por MZ, cierre de la
  válvula, zona horaria y alertas de riego.

### Modified Capabilities

- `motor-reglas`: materialización del riego (la acción encola, el despacho publica) e idempotencia
  por ciclo de lectura en lugar de cooldown.
- `pronostico-climatico`: el pronóstico trae milímetros por hora y se consulta en la zona horaria del
  vivero; la postergación pasa a ser R-03.

## Impact

- **Backend:** `engine/` (`RuleAction` con detalle tipado, `ActionType.ALERTA`, `RuleContext` con
  `ContextoRiego`, `Evaluacion.compararVentana`, `Operador.EN`), `engine/rules/` (7 reglas nuevas,
  3 borradas), `engine/riego/` nuevo (cálculo, ciclo, cola, despacho), `engine/parametros/ParametrosRiego`,
  `CatalogoParametros`, `engine/weather/*`, `NurseryService`, `HistorialService`,
  `HistorialRepository`, `HistorialEventoEntity` (+4 columnas nulas), `ContratoNodo`,
  `application.properties`, script `migracion-reglas-riego.sql`.
- **Firmware:** `comun/contrato.h`, `comun/config.example.h` (límite de válvula, flag de
  caudalímetro), `actuacion/act_valvula.cpp`.
- **Simulador:** `server/contract.ts` (constante espejada) y aviso en el log de comandos. El backend
  sigue sin conocerlo.
- **Frontend:** chico y después de que cierre `add-catalogo-umbrales-reglas` (§7–8): operador `EN`,
  fixture/mocks del catálogo y la traza, grupo de parámetros sin regla, `RuleGraph` lee `regla` del
  evento, campos nuevos del historial.
