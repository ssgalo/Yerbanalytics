# Design: add-reglas-biotico

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| Estados del diagnóstico | `DiagnosticoService` admite `Sano`, `Clorosis`, `Estrés solar`, `Daño biótico`, `No concluyente`, **`Ácaro`, `Plaga foliar`, `Daño fúngico`** |
| Sector | Guarda el **último** diagnóstico (`diagnosisEstado/Conf/Sev/Ts`) y `diagnosisUsadoTs` ("usado") |
| Historial de diagnósticos | `DiagnosticoEntity` (`sectorId`, `estado`, `sev`, `conf`, `creadoEn`) permite consultar los últimos N por sector |
| Severidad | Baja / Media / Alta (CLAUDE.md §2); la v2 dice leve / moderada / alta |
| Vecindad | Verificado: cada sector tiene `n` (1…N dentro de su MZ, id `MZ-z-NNN`, `TopologiaService.nuevoSectorOffline`) y `TopologiaLayoutEntity.sectoresPorFila` (default 10) es la "**disposición visual** de la fila única" del mapa (`NurseryService.buildLayout`). **No hay un mapa físico de adyacencia**; la grilla (fila = (n−1)/w, columna = (n−1)%w) es una convención de presentación |
| Historial de lecturas | No existe: DPV de 24 h / 72 h y "2 lecturas seguidas" salen de `ReadingHistory` (`add-historial-lecturas`) |
| Capturas seguidas | `DiagnosticoRepository.findBySectorIdOrderByCreadoEnDesc(sectorId)` permite traer los últimos N diagnósticos del sector |
| "Diagnóstico nuevo" | Las reglas con `usaDiagnostico()` actúan una vez por diagnóstico (CLAUDE.md §2) |

## Decisiones

### D-1 — Mapa de estados
v2 "Plaga foliar" = estados `Plaga foliar` **y** `Ácaro` (el ácaro es una plaga foliar; F-P1 habla de ácaros).
`Daño fúngico` = `Daño fúngico`. `Daño biótico` genérico: *pregunta abierta* — ¿cuenta como hongo, como plaga o ninguno?
Default: ninguno, sólo alerta informativa (no se sabe qué fitosanitario aplicar; con un solo producto es igual, pero
F-F1 y F-P1 tienen condiciones distintas).

### D-2 — Severidad
`Baja` = leve, `Media` = moderada, `Alta` = alta. "Severidad moderada o alta" ⇒ `Media` o `Alta`. Constante en un solo
lugar (`SeveridadV2`).

### D-3 — Tres capturas diarias seguidas
Consulta de los últimos 3 diagnósticos **de días distintos** del sector con el mismo estado y todos vigentes en su
momento. "Diaria" porque hay una pasada por día (§1.3); si faltó una pasada, no se cuenta como seguida.

### D-4 — "Una vez por diagnóstico"
Las reglas con diagnóstico usan `diagnosisUsadoTs` para no re-disparar con el mismo diagnóstico. Interacción con
P-D2: una aplicación diferida **no** consume el diagnóstico nuevo hasta ejecutarse (si no, se perdería). La marca se
pone al **ejecutar**, no al pedir. Esto es un cambio de contrato con `ApplicationService`: la aplicación diferida
guarda el `diagnostico_id` de origen.

### D-5 — F-F2 sin diagnóstico
Corre aunque el sector no tenga diagnóstico, por MZ (DPV y temperatura son de la MZ; el sustrato también). Alerta una
vez por evento (principio 8): mientras la condición persista no repite.

### D-6 — F-04 foco
Cuenta por MZ en una ventana de 72 h los sectores **distintos** con diagnóstico biótico vigente del **mismo** estado.
Vecinos = los 8 que rodean al sector en la grilla de su MZ, **calculada con `sectoresPorFila`** (menos los que caen fuera de la MZ). Esto
asume que el layout visual refleja la disposición física, algo que ni la v2 ni el código garantizan (v2 §12 no lo lista): **supuesto a
validar con el vivero**; si la numeración física difiere, hace falta un mapa de adyacencia real (cambio aparte). Se aplica "salvo P-D1"
(los vecinos sanos no tienen diagnóstico); el resto de §8.3 rige. Los vecinos tienen que estar en la misma MZ.
*Pregunta abierta:* vecinos de distinta MZ (el borde): default, no se tratan.

### D-7 — F-06 tope
3 aplicaciones de fitosanitario en 30 días (de la tabla `aplicacion`). A la cuarta ⇒ `ApplicationService` rechaza con
motivo "tope", `WARNING`, y queda bloqueada **hasta que alguien registre la revisión** (la misma acción de revisión
que usa S-06 en `add-efectividad-y-s06`; hasta que ese cambio exista, el endpoint de revisión se crea acá y se reusa).
Parámetro `insumo.tope-fitosanitario` (3 en 30 días, rango 2–5).

### D-8 — Parámetros
| Clave | Fábrica | Rango |
|---|---|---|
| `biotico.dpv-saturado` | 0,3 kPa | 0,1–0,5 |
| `biotico.dpv-calor-seco` | 2,0 kPa | 1,5–3,0 |
| `biotico.humedad-hongo` | 80 % | 70–90 |
| `biotico.temp-predisponente` | 18–28 °C | — |
| `biotico.humedad-predisponente` | 75 % | 65–85 |
| `biotico.capturas-seguidas` | 3 | 2–5 |
| `biotico.horas-dpv-acaros` | 72 h | 24–120 |
| `biotico.sectores-foco` / `horas-foco` | 3 / 72 h | 2–10 / 24–120 |
| `insumo.tope-fitosanitario` / `…-dias` | 3 / 30 d | 2–5 / 14–60 |

## Decisiones tomadas al implementar

Los nombres de clases y métodos del diseño estaban en español; se implementaron en inglés (CLAUDE.md §7): `DanoFungicoRule` → `FungalDamageRule`,
`PlagaFoliarRule` → `FoliarPestRule`, `CondicionFungicaRule` → `FungalConditionRule`, `FocoRule` → `OutbreakRule`, `SeveridadV2` → `Severity`,
`Vecinos.de` → `GridNeighbors.of`, `CapturasSeguidas.de` → `ConsecutiveCaptures.count`. Todo vive en el paquete `biotic/`.

- **D-1 y D-6 (preguntas abiertas)**: se tomó el default del diseño. El `Daño biótico` genérico no dispara nada y los lindantes de otra MZ no se tratan.
  Los dos siguen siendo un supuesto a validar con el vivero (como la vecindad por la grilla visual).
- **Tipos, no textos (D-1)**: `BioticKind` (`FUNGAL`, `PEST`) mapea el estado del diagnóstico; *Plaga foliar* y *Ácaro* son el mismo tipo, también en el foco.
- **Un esqueleto para F-F1 y F-P1**: `BioticDiagnosisBase` (template method) concentra lo común —diagnóstico del tipo y vigente (P-D1), las tres razones, las
  acciones y la alerta— y cada regla concreta sólo dice qué ambiente favorece al problema, su texto y qué hace al esperar (F-P1 deja un `INFO`). Cada una declara su
  `parametros()` con un `List.of(...)` literal porque el test anti-drift del frontend lee el código fuente.
- **P-D1 sobre el sector (D-4)**: `ValidDiagnosis.of(...)` gana una sobrecarga con los campos que el sector ya guarda (estado, confianza, llegada), así la regla no
  consulta la tabla de diagnósticos para saber si sirve. El foco la usa con **su propia ventana** (72 h) en lugar de la vigencia de 48 h.
- **D-4, un diagnóstico, una aplicación**: la fila diferida guarda `diagnosis_ts` (no un `diagnostico_id`: lo que se compara con `diagnosisUsadoTs` es el instante).
  `ApplicationService` da por usado ESE diagnóstico al ejecutar o al rechazar (un rechazo no se reintenta) y nunca retrocede el último usado.
  `RuleOrchestrator` ya no lo gasta si alguna regla pidió una aplicación por el diagnóstico.
- **Capturas seguidas (D-3)**: de cada día vale la última captura; la racha corre hacia atrás desde la más nueva sin saltear ningún día. Cuentan sólo las del tipo de
  daño y con la confianza mínima.
- **F-F2 sin estado (D-5)**: avisa cuando la condición se cumple con las últimas lecturas y no se cumplía con las mismas lecturas corridas una hacia atrás. No hace falta
  una columna ni una tabla.
- **F-04 sin repetir (D-6)**: no se le pide a un lindante que ya recibió fitosanitario dentro de la ventana ni al que ya lo tiene en cola (`FungicideLog`); si no queda
  ninguno por tratar no se repiten ni las órdenes ni la alerta. Cada orden lleva su sector (`DetalleOutbreakTreatment`) y la ejecuta `ZoneActionExecutor`
  llamando a `ApplicationService.request(..., false)`.
- **D-7, tope**: es una precondición más (`ApplicationPreconditions`, bypass `cap`), no una regla. Cuenta las aplicaciones de la tabla `application` desde la última
  revisión del sector (`sector.fungicide_review_ts`). La revisión es `POST /api/sectores/{id}/revision-fitosanitario` con `insumos.gestionar`: **no se creó un permiso
  nuevo** (evita una migración manual de la matriz). `add-efectividad-y-s06` tiene su propia revisión por evento (`/api/efectividad/{eventoId}/revision`); no es la
  misma acción, así que no se reusa. Sin botón en el dashboard todavía.
- **D-8, temperatura predisponente**: dos parámetros (`-min` 10–22, `-max` 24–35, rangos propuestos por el equipo) que nunca se cruzan, así no hace falta una restricción cruzada.
- **Dosis**: la del catálogo (`insumo.dosis-fitosanitario`, 10 ml de fábrica, máx. 50), no los 100 mL de la v2: el firmware no acepta más de 50 ml por comando.

