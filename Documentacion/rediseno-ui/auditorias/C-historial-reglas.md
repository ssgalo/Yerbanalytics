# Auditoría UI/UX · C — Historial y trazabilidad + Motor de reglas

**Alcance:** `/historial` (`src/features/historial/**`) y `/reglas` (pestañas Parámetros e Inspector, `src/features/reglas/**`, `src/components/DAGViewer/**`).
**Método:** lectura del código, de los datos mock (`history.ts`, `trazaReglas.ts`, `catalogoReglas.fixture.json`), de las specs (`historial-trazabilidad`, `motor-reglas`, `bloqueo-manual`) y de `reglas_v2.md`; la app en modo demo en `127.0.0.1:5173` manejada con Playwright (1440×900, 1440×2200 y 390×844). Probé editar, invalidar, guardar, descartar, cambiar de pestaña y de ruta, el teclado y el DAG.
**Evidencia nueva:** `scratchpad/shotsC/` (`h-*.png` = historial, `r-*.png` = parámetros, `i-*.png` = inspector, `m-*.png` = 390 px sin sidebar). Las rutas son de archivo y línea (`archivo:línea`), relativas a `Desarrollo/frontend/src/` salvo que diga otra cosa.
**No se modificó ningún archivo del repo.**

---

## 0. Resumen ejecutivo

1. **El "razonamiento del motor" del Historial suele estar mal.** El DAG del Historial (`RuleGraph` en modo `activeEvents`) no tiene datos de la evaluación: **adivina** con regex sobre textos. Pinta mal tres casos que verifiqué: el riego pospuesto sale verde "Pasó" en vez de ámbar "Pospuso"; el límite de dosis diaria sale verde y la culpa queda en "Dosificación de insumo"; y un riego abortado por sensor muestra los cuatro terminales en verde. Es la pantalla de auditoría, y hoy puede mentir.
2. **El concepto "Ciclo" es ficticio.** Se agrupa por el string de minuto `fecha` (`dd/MM HH:mm`). En la demo son 48 "ciclos" de 1 evento cada uno, en una lista plana sin agrupación por día ni paginación. Hacen falta 4 clics para ver una decisión (ciclo → macro-zona → sector → razonamiento).
3. **Hay mucha jerga y detalle de implementación a la vista del usuario:** clases Java (`ManualLockRule`, `RiegoPorDeficitRule`, también dentro de los textos de lectura), enums (`NOOP_INFO`, `MOVER_MEDIASOMBRA`, `Alerta CRITICAL`), claves (`riego.saturacion-bloqueo`), `refSpec` (`reglas_v2 §5 R-01 / §11 Riego`), "prioridad 0..20", "parámetros #4c872236", "Telemetría/Barrido", "DAG", "Pipeline". También aparecen payloads crudos como `[apertura=30]` y estados en inglés (`ok = critical`).
4. **La traza del Inspector se lee al revés.** Las reglas que "pasaron" muestran ✗ rojas, porque ✗ significa "la condición de corte no se cumplió", que es lo bueno. Por ejemplo, "Bloqueo manual · Pasó · no = sí ✗". El color sigue al booleano de la comparación y no al desenlace de la regla.
5. **Editar umbrales no es seguro:**
   - El campo numérico no se puede vaciar (pasa a 0) y no tiene `max`: con ↑ llega a 68 con un tope de 60.
   - No se ve "actual → nuevo" ni el impacto.
   - Las restricciones cruzadas del backend (crítico < umbral < objetivo, etc.) no se muestran ni se validan en el cliente, y el mock directamente las ignora.
   - Guardar no pide confirmación, no da feedback de éxito y no ofrece deshacer.
   - Al salir de la ruta, el borrador se pierde sin aviso.
6. **El DAG oscuro dentro de una UI clara** tiene dos paletas distintas (slate `#0a0f1a` en el Historial, verde `--g900` en el Inspector). Los nodos quedan con texto de 4 a 6 px cuando se abre el panel o en móvil, la navegación por teclado no sirve (Tab recorre ~20 aristas con `aria-label` en inglés y Enter no abre nada) y el estado depende del color.
7. **Accesibilidad:**
   - Los filtros del Historial no tienen nombre accesible (labels sin `htmlFor`).
   - El foco es invisible (`outline: none`).
   - Las pestañas no implementan el patrón `tablist` (sin `tabpanel` ni flechas).
   - El botón "Ocultar razonamiento" tiene contraste **1,85:1**, los nodos "No evaluado" **1,72:1**, y los rangos y valores de fábrica (`--faint`) **2,64:1**.
8. **A 390 px ninguna de las dos pantallas es usable.** Hay un problema global del shell (la sidebar no colapsa y deja ~120 px de contenido), pero aun ocultándola: las etiquetas de la cadena tienen ancho fijo de 130 px, el DAG queda cortado e ilegible, el panel del nodo aparece fuera de la pantalla y la barra de guardar fija ocupa ~20 % del alto incluso sin cambios.

---

## 1. Historial y trazabilidad (`/historial`)

### 1.1 Propósito y tareas del usuario

Es el registro inalterable de **qué hizo el sistema y por qué** (HU-11/HU-12; spec `historial-trazabilidad`). Usuarios: el Productor Viverista y el Ingeniero Agrónomo. Tareas reales:

| # | Tarea | Hoy |
|---|---|---|
| T1 | "¿Por qué se regó MZ-1-010 a las 14:00?" | Filtrar el sector, abrir ciclo → zona → sector → razonamiento. El "por qué" en texto es una línea (`Lectura / diagnóstico`), y el DAG puede contradecirla. |
| T2 | "¿Qué pasó anoche en la MZ-3?" | Filtro de zona y fechas, pero sin agrupación por día ni vista de resumen. Hay que abrir ciclo por ciclo. |
| T3 | "¿Por qué NO se regó MZ-2-030?" (pospuesto, abortado, bloqueado) | Con el filtro Resultado = Pospuesta/Abortada. El DAG suele mostrar mal la regla responsable. |
| T4 | "¿Funcionó el riego?" (seguimiento E-01) | El bloque Antes/Ahora/Delta existe, pero **no dice qué métrica es**. |
| T5 | "¿Qué alertas hubo hoy y de qué nivel?" | La lista colapsada muestra "Alerta crítica: Informativo" en gris. |
| T6 | "¿Quién cambió un umbral y cuándo, y qué efecto tuvo?" | El backend real registra eventos `Configuración`, pero el filtro no los ofrece y se muestran como si fueran un sector "—" con DAG. |
| T7 | Auditar o compartir un evento (link, export) | No hay deep-link por evento ni filtros en la URL, ni export. |

### 1.2 Inventario de información (lo que hoy muestra; el rediseño no debe perder nada)

**Encabezado de página (Topbar):** título "Historial y trazabilidad"; subtítulo `${records.length} acciones registradas`, p. ej. "48 acciones registradas" (`HistorialPage.tsx:50`). Cuenta todo, alertas incluidas, y también los eventos Info y Configuración en el sistema real.

**Barra de filtros** (`HistorialFilters.tsx`):
| Control | Valores |
|---|---|
| Tipo de acción (select) | Todas · Riego · Insumo · Mediasombra · Alerta (`:4`) |
| Macro-zona (select) | Todas + zonas presentes en los datos ("Macro-zona 1"…"Macro-zona 6"; en el sistema real también "Sistema" por los eventos de Configuración) |
| Sector (texto) | placeholder "ej. MZ-2-014", búsqueda por subcadena, sin distinguir mayúsculas |
| Resultado (select) | Todas · Efectiva · En seguimiento · Pospuesta · Abortada · Informativo (`:5`) |
| Desde / Hasta (date nativo) | se muestra con el formato del navegador ("mm/dd/yyyy" en un Chromium en inglés) |
| Contador | "**48** acciones" (`:103`) |

**Tarjeta "Historial de acciones por Ciclo"** (`HistorialTimeline.tsx:95-97`):
- Subtítulo: "Evaluaciones agrupadas por fecha y hora · Clic en un ciclo para ver los sectores · Clic en un sector para ver el DAG".
- **Nivel 1, ciclo:** ícono calendario + "Ciclo 06/10 23:01" + "1 sector evaluados · 1 decisión" / "0 sectores evaluados · 1 decisión" + chevron (`:115-133`).
- **Nivel 2, macro-zona:** pin + "Macro-zona 6" + el mismo contador + chevron (`:147-165`).
- **Nivel 3, sector:** "MZ-6-089" o "Alertas de la macro-zona" (cuando todos los eventos son alertas, `:181`), con badges por evento `"{tipo}: {resultado}"` (p. ej. "Riego: Efectiva", "Riego: Pospuesta", "Insumo: Abortada", "Mediasombra: Efectiva", "Alerta crítica: Informativo", "Alerta advertencia: Informativo", "Alerta informativa: Informativo") y una flecha que rota 90° (`:186-196`).
- **Contenido del sector:**
  - Botón "▼ 🧠 Ver razonamiento del motor — cómo se tomó esta decisión" / "▲ Ocultar razonamiento del motor" (`:204-214`). No aparece en las alertas.
  - Contenedor oscuro "Pipeline de decisión automática" con el DAG (`:219-227`). Estados: "Cargando motor de reglas…" y "Error al cargar el motor".
  - Por evento: ícono con tinte + rótulo de tipo ("Riego", "Insumo", "Mediasombra", "Alerta crítica").
  - Chips `EventoMeta`: **NIVEL** (Crítica/Advertencia/Informativa, con color), **VOLUMEN** ("5,4 L"), **DURACIÓN** ("648 s") y **REGLA** (nombre legible del esquema, p. ej. "💦 Riego por déficit hídrico (R-01)").
  - La cadena de justificación: **Lectura / diagnóstico** (si el texto es "Ciclo de evaluación: X." se reemplaza por "Evaluando: {label}", `:244`), **Decisión del motor** y **Acción ejecutada**.
  - **Seguimiento post-acción** (si hay `evo`): "Seguimiento post-acción · latencia 2 min", ANTES "38%", flecha, AHORA "57%" (o "—"), "Delta +19%" (o "Delta —"), badge de veredicto (Efectiva / En seguimiento / Sin efectividad).

**DAG del Historial** (`RuleGraph.tsx`, modo `activeEvents`):
- Nodos: "Inicio Evaluación", más las 13 reglas con emoji, más los terminales por rama ("Riego efectuado"/"No se regó", "Dosificación efectuada"/"No se dosificó", "Mediasombra ajustada"/"No se movió mediasombra"; SEGUIMIENTO muestra el crudo "Evaluado OK").
- Aristas rotuladas "Continúa"/"Completado".
- Leyenda: Pasó · Bloqueó · Pospuso · Accionó · No evaluado.
- Controles de zoom (+/−/encuadrar) y un MiniMap.
- Sin selección ni panel: es sólo lectura visual.

**Estados de página:** "Cargando historial…", "No se pudo cargar el historial: {msg}" en rojo, y como vacío "No hay acciones registradas para los criterios seleccionados.".

**Textos de ejemplo de los datos** (mock `history.ts`, espejo del backend):
- Riego: "Humedad de sustrato 38% (regla RiegoPorDeficitRule)." / "El motor de reglas ordena regar 5,4 L durante 648 s." / "Electroválvula abierta · riego autónomo en curso."
- Déficit crítico: "…6 L durante 720 s (déficit crítico: volumen máximo, a cualquier hora)."
- Pospuesto: "Lluvia prevista (probabilidad máxima 80% y 8 mm en las próximas 4 h): se pospone el riego." / "Riego pospuesto para evitar saturación hídrica del sustrato."
- Abortado: "Sensor testigo sin reporte hace más de 2 h." / "Sin telemetría confiable: se anula la actuación autónoma por seguridad." / "Riego abortado para evitar inundación a ciegas."
- Insumo: "Diagnóstico IA: Daño fúngico (confianza 93%) + sustrato sobre 80%." / "Bomba peristáltica inyectó 4,5 ml de fungicida…"; "Confianza bajo el umbral del 85%: no se inyecta químico." / "Dosificación bloqueada · solicitada validación visual humana."; "Límite químico diario alcanzado…".
- Mediasombra: "Plan de rustificación día 12." / "Cobertura ajustada de 35% a 45%…"; "Pico de radiación UV 9…" / "…retrae la mediasombra." / "Cobertura llevada a 70%…".
- Alertas: "Alerta de la macro-zona MZ-6 (regla DeficitCriticoRule)." / "Déficit hídrico crítico" / "Alerta CRITICAL registrada para el operador."
- **Sólo en el sistema real** (backend `HistorialService.java`):
  - Tipo `Info` (inacción): "Ciclo de evaluación: X." / motivo / "Evaluación bloqueada." o "Condición normal — sin actuación."
  - Tipo `Configuración`, con zona "Sistema" y sector "—": "Recalibración de parámetros de reglas por {usuario}." / "Se validaron tipo, rango y restricciones cruzadas…" / detalle de claves.

**Datos disponibles que NO se muestran:** `time` ("hace 6 min"), `sev` (Alta/Media/—), `id` (HE-001), `evo.metric` ("Humedad de sustrato"), `zonaName` a nivel evento y la hora exacta con segundos.

### 1.3 Bugs e inconsistencias (verificados)

| # | Severidad | Hallazgo | Dónde |
|---|---|---|---|
| H1 | **Alta** | **El DAG del Historial atribuye mal la decisión.** `extractRuleName` asume que todo Insumo es `SupplyRule` y toda Mediasombra es `ShadingRule`. Con "Límite químico diario alcanzado" pinta **Límite de dosis diaria = Pasó (verde)** y **Dosificación de insumo = Bloqueó (rojo)**, que es al revés. Con "Confianza bajo el umbral: no se inyecta" pinta Bloqueó cuando en realidad no actuó. Captura `shotsC/h-insumo-abortada.png`. | `components/DAGViewer/RuleGraph.tsx:136-138, 229-236` |
| H2 | **Alta** | **"Pospuso" nunca se pinta.** `isPostpone` busca `POSTPONE`, "posterg" o "lluvia inminente", pero el texto real es "se pospone el riego" (mock) y "Riego pospuesto por lluvia…" (backend). R-03 sale **verde "Pasó"** en un riego pospuesto. Captura `h-riego-pospuesta.png`. | `RuleGraph.tsx:151-155` |
| H3 | **Alta** | **Los terminales de rama siempre en verde.** Un riego abortado por `StaleSensorRule` (GLOBAL) muestra "No se regó", "No se dosificó", "No se movió mediasombra" y "Evaluado OK", todos con borde y texto verde de éxito. `hadBlock` compara `e.tipo.startsWith('ABORT')`, que nunca se cumple porque `tipo` es "Riego"/"Insumo". | `RuleGraph.tsx:190-212, 203` |
| H4 | **Alta** | **El DAG asume como "Pasó" lo que no sabe.** Toda regla de prioridad menor a la que decidió se pinta verde (`n.priority < evNode.priority`), aunque no haya datos. Las aristas "Continúa" hacia ramas no evaluadas se encienden en verde mientras sus nodos quedan grises "No evaluado" (en `h-insumo-abortada.png`, la arista hacia "Sustrato saturado" queda verde). | `RuleGraph.tsx:239-241, 287-290` |
| H5 | Alta | **"Ciclo" = minuto.** Se agrupa por el string `fecha` `dd/MM HH:mm`. Dos evaluaciones del mismo minuto se funden y una que cruza el cambio de minuto se parte. En la demo hay 48 "ciclos" de 1 decisión cada uno. | `HistorialTimeline.tsx:53-81` |
| H6 | Media | **Concordancia:** "1 sector evaluados". Además, **"sectores evaluados" es falso**: cuenta sectores *con eventos*, no evaluados (un ciclo real evalúa 100 sectores por zona). Las alertas producen "**0 sectores evaluados · 1 decisión**". | `HistorialTimeline.tsx:124, 156` (no usa `contar()` de `lib/plural`, que sí usa Reglas) |
| H7 | Media | **Colisión de estado en las alertas.** `secId = fecha_sectorId`, y todas las alertas tienen `sectorId "—"`. Si en el mismo minuto hay alertas en varias zonas (p. ej. R-03 por lluvia en las 6 MZ), abrir una abre todas. | `HistorialTimeline.tsx:170` |
| H8 | Media | **Eventos `Configuración` (sistema real) mal presentados.** Aparecen bajo la "macro-zona" "Sistema", con nombre de sector "—" y con botón "Ver razonamiento del motor" (sólo se excluye `tipo === 'Alerta'`). El filtro Tipo no ofrece "Configuración" ni "Info". | `HistorialTimeline.tsx:181, 203`; `HistorialFilters.tsx:4`; `eventoPresentacion.ts` (`esAlertaDeZona`) |
| H9 | Media | **"Alerta crítica: Informativo"** en el badge colapsado, con color gris de "Informativo": el nivel crítico sólo se ve al expandir. "Alerta advertencia" no es castellano natural (se esperaría "Advertencia" o "Alerta de advertencia"). | `HistorialTimeline.tsx:186-188`; `eventoPresentacion.ts:32-33` |
| H10 | Media | **El seguimiento no dice la métrica.** `evo.metric` nunca se renderiza: "ANTES 82% → AHORA —". En un insumo (fungicida), la métrica es… la humedad de sustrato, con latencia de 2 min. Eso es agronómicamente incorrecto (reglas_v2 §9 dice que E-02/E-03 evalúan el diagnóstico a los 7–14 días). | `HistorialTimeline.tsx:285-317`; `data/mock/history.ts:235, 256` |
| H11 | Media | **"Efectiva" con dos significados.** El resultado "Efectiva" = *se ejecutó* (el backend lo pone al ordenar el riego); el veredicto "Efectiva" = *logró el efecto* (E-01). Se ven "Riego: Efectiva" y "Seguimiento: En seguimiento" a la vez. Además, "Delta +19%" debería ser "+19 puntos" (reglas_v2 usa "puntos"). | `HistorialFilters.tsx:5`; backend `HistorialService.java:78/92` |
| H12 | Media | **Clases Java dentro de los textos**: "Humedad de sustrato 38% (regla RiegoPorDeficitRule).", "Alerta de la macro-zona MZ-6 (regla DeficitCriticoRule).", "Alerta CRITICAL registrada…". El reemplazo sólo cubre "Ciclo de evaluación: X". | `HistorialTimeline.tsx:244` |
| H13 | Media | **Tokens inexistentes:** `var(--border)` (borde izquierdo de la zona) y `var(--surface-sunken)` (fondo del encabezado de zona y de los chips de EventoMeta) no están definidos en `tokens.css`, así que no se pintan. | `HistorialTimeline.tsx:146, 151`; `EventoMeta.module.css:14` |
| H14 | Baja | **Textos de mock contradictorios con el dominio** (pasan a la demo): "Pico de radiación UV 9 detectado por el sensor local" (el sensor es un LDR, `uv` = % de luz); "retrae la mediasombra" y "Cobertura llevada a 70%" en el mismo evento; "Sensor testigo sin reporte hace más de 2 h" con un umbral de 90 s. | `data/mock/history.ts:140-149` |
| H15 | Baja | Mayúscula "por **C**iclo" y doble título ("Historial y trazabilidad" + "Historial de acciones por Ciclo"); el h3 aparece sin h2. "Clic en un sector para ver el **DAG**". | `HistorialTimeline.tsx:95-97` |
| H16 | Baja | Filtro de fechas sin validación: con Desde > Hasta devuelve el vacío genérico sin explicar por qué. No hay "Limpiar filtros". | `HistorialPage.tsx:34-48` |
| H17 | Baja | CSS muerto de un "Inspector de Decisiones" que ya no está en esta página (`.split`, `.inspector*`, `.schema*`); `.zona` sin uso; `styles.zonasContainer` y `styles.zonaBlock` referenciados pero sin definir. | `HistorialPage.module.css:16-77`; `HistorialTimeline.module.css:113`; `HistorialTimeline.tsx:136,146` |
| H18 | Baja | Spec incumplida: "cada entrada muestra tipo, sector, **tiempo** y badge" → el tiempo relativo nunca se muestra, y el tipo y el sector recién aparecen en el 3.er nivel. | spec `historial-trazabilidad` §Timeline |
| H19 | Baja | En la demo, guardar parámetros **no** genera el evento "Configuración" (el backend sí). La demo no ejercita T6. | `data/mock/mockRepository.ts:223-240` |

### 1.4 Problemas de UX

- **Jerarquía invertida.** La información útil (qué pasó, en qué sector, con qué resultado) está tres niveles adentro. El primer nivel sólo muestra una hora y contadores sin sentido ("1 sector evaluados · 1 decisión" ×48). Para escanear "qué pasó hoy" hay que abrir todo.
- **No hay agrupación temporal** (Hoy / Ayer / fecha), ni paginación, carga incremental o virtualización. Con el backend real (sin límite: `HistorialService.getHistorial` devuelve todo y el front filtra en memoria; el endpoint acepta filtros que el front no usa) la lista crece sin tope.
- **El "por qué" está mal resuelto.**
  - El texto de la cadena es correcto pero escueto: "Humedad 38%", sin decir contra qué umbral.
  - El DAG agrega 1.000 px de grafo oscuro que en el mejor caso muestra 13 cajas verdes y en el peor muestra algo falso (H1–H4). No dice valores, umbrales ni motivos.
  - El usuario no puede contestar "¿qué umbral hizo que regara?" desde el Historial.
- **Tampoco hay puentes:** el sector no es link, no hay "Ver evaluación actual en el Inspector" ni "Editar este umbral". Tampoco se puede llegar con `?sector=` desde el detalle del sector.
- **Filtros:** no hay filtro por regla (p. ej. "todos los R-02"), ni búsqueda libre, ni filtros en la URL (no se puede compartir "MZ-3 ayer"). Con 600 sectores, el campo "Sector" no autocompleta.
- **Las alertas, mal clasificadas como sector.** Son de macro-zona pero se presentan como un "sector" ("Alertas de la macro-zona") al mismo nivel que MZ-x-xxx.
- **Estados:** la carga es texto plano (sin skeleton). El error es texto rojo sin "Reintentar". El vacío no ofrece limpiar filtros. La página no se actualiza sola (un único fetch al montar), así que no se ven eventos nuevos sin recargar.
- **Señales que se pierden en los resultados.** "Abortada" y "Pospuesta" están en la misma bolsa que "Efectiva": no hay forma rápida de ver "lo que el motor decidió **no** hacer", que es justamente lo que el viverista quiere auditar.

### 1.5 Problemas visuales / UI

- El **DAG oscuro** (`#0a0f1a`, paleta slate/índigo de Tailwind hardcodeada) dentro de una tarjeta blanca sobre fondo crema rompe el sistema visual. Encima usa **otra** paleta oscura que el Inspector (verde `--g900`). Ver `HistorialTimeline.module.css:239`, `RuleGraph.tsx:32-120`.
- **El botón "Ocultar razonamiento del motor"** es lila `#a5b4fc` sobre lila al 6 %: **1,85:1**, prácticamente invisible (`HistorialTimeline.module.css:270-293`). El índigo no existe en el sistema de tokens. Mezcla ▲/▼ en texto y el emoji 🧠.
- **Cuatro íconos de disclosure distintos:** chevron (ciclo, zona), una `arrow-right` rotada 90° que parece "descargar" (sector), y ▲/▼ de texto (razonamiento).
- **Indentación acumulada:** 16 + 12 + 24 + 16 + 12 px de padding anidado, más un borde izquierdo que no se dibuja (H13). Los chips de EventoMeta tienen `margin-left: 32px` y no se alinean con las filas de la cadena (`EventoMeta.module.css:5`).
- El DAG del Historial mide ~1.000 px de alto para una sola decisión. Tiene rótulos "Continúa" en cada arista (ruido), un MiniMap que no aporta, y los controles de zoom tapan la leyenda: el botón "encuadrar" queda debajo (`RuleGraph.tsx:360-371`).
- Badges de 10,5 px con estilos inline (`HistorialTimeline.tsx:186`). El veredicto queda pegado al margen derecho, lejos de Antes/Ahora.
- Fechas "06/10 23:01" sin día de semana ni "hoy". El campo date nativo hereda el locale del navegador.

### 1.6 Accesibilidad

- **Filtros sin nombre accesible:** los 6 `<label>` no tienen `htmlFor` y los controles no tienen `id` ni `aria-label`. Con Playwright, `getByRole('combobox', {name:'Tipo de acción'})` devuelve 0 (`HistorialFilters.tsx:27-94`).
- **Foco invisible** en selects e inputs: `outline: none` sin reemplazo; el estilo computado es `outline none, border #e7e5d9, box-shadow none` (`HistorialFilters.module.css:37, 49`).
- **`aria-expanded` falta** en los botones de zona y de sector (`HistorialTimeline.tsx:147, 175`); sólo lo tienen el de ciclo y el del DAG. No hay `aria-controls`.
- **El DAG no es accesible:**
  - Sin alternativa textual.
  - El estado de cada nodo se comunica **sólo por color** (en el Historial los nodos no llevan chip de texto).
  - "No evaluado": texto `#334155` sobre `#0f172a`, **1,72:1**.
  - Rótulos de aristas `#64748b` a 10 px, 3,75:1.
  - Verde/rojo como único diferenciador (daltonismo).
- Badge "Informativo" `#6A776E` sobre `#EEEDE5`: 3,99:1 a 10,5 px, no llega a AA.
- Emojis dentro de los nombres (🧠, 💦, 🔒…): los lectores de pantalla los leen ("cerebro", "gotas de sudor"…).

### 1.7 Responsive (390 px)

- **Global, fuera de alcance pero bloqueante:** la sidebar de 256 px no colapsa. A 390 px el contenido útil queda en ~120 px, con texto vertical palabra por palabra y `scrollWidth` de 683 px (`shots/mob-historial.png`). La Topbar también se superpone.
- Aun ocultando la sidebar (`shotsC/m-historial*.png`, `scrollWidth` 427 > 390):
  - Los filtros ocupan media pantalla, sin colapsar en un "Filtros (2)".
  - Las filas de la cadena tienen `chainLabel` fijo de **130 px** (`HistorialTimeline.module.css:174`). El texto queda en una columna de ~4 palabras ("El / motor / de / reglas / ordena…") y "RiegoPorDefi…" se corta por `overflow: hidden`.
  - La fila de seguimiento desborda: el badge "Efectiva" queda cortado.
  - El DAG, con `fitView minZoom: 0.9` (`RuleGraph.tsx:347`), no entra: se ve un cuarto del grafo, cortado a la derecha.
  - Con `panOnScroll`, arrastrar dentro del DAG mueve el grafo en vez de la página (trampa de scroll en táctil).

---

## 2. Motor de reglas · pestaña **Parámetros** (`/reglas`)

### 2.1 Propósito y tareas

Consultar y ajustar los umbrales del motor con seguridad. Tareas:
- **P1:** "¿Qué umbral cambio para que riegue antes?"
- **P2:** "¿Cuánto vale hoy X y cuál es el de fábrica?"
- **P3:** "Si cambio X, ¿qué otras reglas afecto?"
- **P4:** "¿Quién cambió X y cuándo?"
- **P5:** "Volver todo a fábrica / deshacer lo que hice".
- **P6:** "Ver sólo lo que difiere de fábrica".

### 2.2 Inventario

**Pestañas** (`ReglasPage.tsx:53-77`): "Parámetros" (con badge ámbar con la cantidad de cambios sin guardar y tooltip "N cambios sin guardar") e "Inspector". Viven en la URL (`?tab=inspector`; `?regla=` abre una regla; `?sector=`).

**Intro:** "Cada regla del motor decide comparando lo que recibe contra uno o más umbrales. Acá ves cuánto vale cada uno y podés cambiarlo. Un umbral que usan varias reglas es un solo valor: editarlo en una lo cambia en todas."

**Barra:** buscador "Buscar regla o parámetro…" (busca sin tildes en nombre, id, etiqueta, clave y descripción, y **abre las reglas que coinciden**); select "Todas las ramas / Global / Riego / Insumos / Mediasombra / Seguimiento"; check "Sólo modificados"; segmentado "Por regla | Por parámetro".

**Vista "Por regla": grupos por rama** (`catalogoView.ts:20-26`):
| Grupo | Descripción |
|---|---|
| GLOBAL · 2 REGLAS | Corren primero y aplican a todo el motor |
| RIEGO · 7 REGLAS | Electroválvulas |
| EJECUCIÓN DEL RIEGO (no colapsable) | Cómo se reparten los riegos ordenados entre los sectores de una macro-zona |
| INSUMOS · 2 REGLAS | Bombas peristálticas |
| MEDIASOMBRA · 1 REGLA | Techo móvil |
| SEGUIMIENTO · 1 REGLA | Evaluación posterior a la acción |

**Tarjeta de regla, colapsada:**
- chevron "›";
- label **con emoji**;
- `id · prioridad N` en monospace gris;
- chips con los valores vigentes (tooltip con la etiqueta; ámbar si están modificados);
- punto rojo (error) o ámbar (edición pendiente);
- resumen "N parámetros · M modificados" o "Sin parámetros configurables".

**Las 13 reglas** (`catalogoReglas.fixture.json`, igual al backend):
| Rama | Prio | Label (tal cual) | id | Parámetros (chips) |
|---|---|---|---|---|
| GLOBAL | 0 | 🔒 Bloqueo manual | ManualLockRule | — |
| GLOBAL | 1 | 📵 Sensor sin datos recientes | StaleSensorRule | 90 s |
| RIEGO | 2 | 💧 Sustrato saturado (R-04) | SustratoSaturadoRule | 75 % · 80 % |
| RIEGO | 3 | 🔁 Un riego por ciclo de lectura | CicloLecturaRiegoRule | 35 % |
| RIEGO | 4 | 🚨 Déficit hídrico crítico (R-02) | DeficitCriticoRule | 35 % · 6 L · 30 L/h · 12 h |
| INSUMO | 5 | 🛑 Límite de dosis diaria | DailyDoseLimitRule | 1 dosis |
| RIEGO | 6 | 🌙 Riego fuera de ventana horaria (R-05) | FueraDeVentanaRiegoRule | 06:00–18:00 · 45 % · 35 % |
| RIEGO | 7 | ⏸️ Pausa tras una aplicación (R-06) | PausaTrasAplicacionRule | 6 h · 45 % · 35 % |
| RIEGO | 8 | 🌧️ Posponer por lluvia (R-03) | PosponerPorLluviaRule | 70 % · 5 mm · 4 h · 45 % · 35 % |
| RIEGO | 10 | 💦 Riego por déficit hídrico (R-01) | RiegoPorDeficitRule | 45 % · 35 % · 65 % · 0.2 L/punto · 6 L · 30 L/h |
| INSUMO | 11 | 🧪 Dosificación de insumo | SupplyRule | 85 % |
| MEDIASOMBRA | 12 | ⛅ Control de mediasombra | ShadingRule | 7 índice · 30 % · 100 % |
| SEGUIMIENTO | 20 | ⏱️ Seguimiento post-acción | FollowUpRule | — |

**Los 21 parámetros** (fila: etiqueta · chips · descripción · Rango · Fábrica · refSpec · auditoría · error · campo · "Restablecer fábrica"):
| Clave | Etiqueta | Tipo/unidad | Rango | Fábrica | Usado por |
|---|---|---|---|---|---|
| seguridad.antiguedad-max-lectura | Antigüedad máxima de la lectura | ENTERO s | 10–600 | 90 | StaleSensor, DespachoRiego |
| riego.umbral-humedad | Umbral de riego (humedad de sustrato) | % | 35–60 | 45 | R-05, R-06, R-03, R-01 |
| riego.lluvia-probabilidad | Probabilidad de lluvia que posterga el riego | % | 50–95 | 70 | R-03, Despacho |
| riego.umbral-critico | Umbral crítico (humedad de sustrato) | % | 25–40 | 35 | Ciclo, R-02, R-05, R-06, R-03, R-01 |
| riego.humedad-objetivo | Humedad objetivo | % | 55–75 | 65 | R-01 |
| riego.litros-por-punto | Litros por punto de humedad | L/punto (2 dec) | 0.1–0.5 | 0.2 | R-01 |
| riego.volumen-max-evento | Volumen máximo por evento de riego | L (1 dec) | 3–10 | 6 | R-02, R-01 |
| riego.caudal-emisor | Caudal del emisor | L/h | 5–120 | 30 | R-02, R-01 |
| riego.saturacion-bloqueo | Saturación que bloquea el riego | % | 65–85 | 75 | R-04, Despacho |
| riego.saturacion-alerta | Saturación que genera alerta | % | 65–85 | 80 | R-04 |
| riego.ventana-normal | Ventana horaria de riego normal | VENTANA (dos `time` "06:00 AM a 06:00 PM") | — | 06:00-18:00 | R-05, Despacho |
| riego.lluvia-mm | Lluvia prevista que posterga el riego | mm | 2–20 | 5 | R-03, Despacho |
| riego.lluvia-ventana | Ventana del pronóstico de lluvia | ENTERO h | 2–12 | 4 | R-03, Despacho |
| riego.pausa-tras-aplicacion | Pausa de riego tras una aplicación | h | 2–24 | 6 | R-06, Despacho |
| riego.exceptuado-bloqueo | Intervalo mínimo entre riegos por déficit crítico | h | 6–24 | 12 | R-02, Despacho |
| riego.sectores-simultaneos | Sectores regando a la vez por macro-zona | ENTERO sectores | 1–100 | 10 | DespachoRiego ("Ejecución del riego") |
| insumo.max-dosis-24h | Máximo de dosis en 24 h | ENTERO dosis | 1–10 | 1 | DailyDoseLimit |
| mediasombra.uv-umbral | Índice UV de protección | índice | 1–11 | 7 | Shading |
| mediasombra.apertura-proteccion-uv | Apertura protectora ante pico UV | % | 0–100 | 30 | Shading |
| mediasombra.apertura-maxima | Apertura máxima de la mediasombra | % | 10–100 | 100 | Shading |
| diagnostico.confianza-minima | Confianza mínima del diagnóstico | % | 50–100 | 85 | Supply |

**Chips de la fila:** "Modificado" (ámbar); en la vista por regla "Compartido con N reglas: {nombres}"; en la vista por parámetro "Usado por N reglas" + la línea "Usado por: A · B". La meta muestra "Rango 35–60 %", "Fábrica 45 %" y el refSpec ("reglas_v2 §5 R-01 / §11 Riego", "motor-reglas: StaleSensorRule", "motor-reglas: SupplyRule; HU-04 CA-03"). La auditoría dice "Modificado por Ingeniero Agrónomo · 06/10 23:15".

**Vista "Por parámetro":** "21 PARÁMETROS — Cada umbral una sola vez, con las reglas que lo usan", en una sola lista plana. El campo `familia` existe (SEGURIDAD, RIEGO, INSUMO, MEDIASOMBRA, DIAGNOSTICO) pero no se usa.

**Estados interactivos:**
- Edición: la fila se pinta con fondo ámbar y borde ámbar **en todas sus apariciones**; aparece el punto ámbar en las cabeceras y el badge en la pestaña.
- Error de cliente: borde rojo, mensaje "Debe estar entre 35 y 60 %." (`role=alert`) y aviso "Corregí los valores marcados para poder guardar.".
- Error oculto por los filtros: "1 parámetro con error queda oculto por los filtros. [Ver los que tienen error]".
- Errores del servidor: por clave, o generales en un banner.
- Barra fija inferior: "Sin cambios pendientes." / "N cambio(s) sin guardar" + [Descartar] [Guardar cambios | Guardando…].
- El borrador sobrevive al cambio de pestaña.
- Vacíos: "Ninguna regla coincide con los filtros." / "Ningún parámetro coincide…".
- Carga y error: "Cargando parámetros…" / "No se pudieron cargar los parámetros: …".

### 2.3 Bugs e inconsistencias

| # | Sev. | Hallazgo | Dónde |
|---|---|---|---|
| P1 | **Alta** | **El campo numérico no se puede vaciar:** `Number('')` = 0 → `onChange(0)`. Borrar para reescribir deja "0" y dispara el error de rango. **No tiene `max`:** con ↑ desde 48 llegué a **68** con un tope de 60. | `features/configuracion/components/NumberField.tsx:32-35`; `ParametroField.tsx:63-64` |
| P2 | **Alta** | **No se muestran ni se validan las restricciones cruzadas.** El backend exige crítico < umbral, umbral < objetivo, saturación-bloqueo ≤ saturación-alerta, y volumen/caudal ≤ 1200 s de válvula. Los rangos se solapan (crítico 25–40 contra umbral 35–60; bloqueo/alerta 65–85 los dos), así que el usuario sólo se entera por un 400 después de guardar. El **mock no valida cruzadas** (`reglasMock.ts:43-55`): en la demo se puede guardar crítico 40 > umbral 35 y R-01 deja de aplicar en silencio. | backend `CatalogoParametros.java:63-82`; `lib/parametrosValidation.ts` |
| P3 | Alta | **El borrador se pierde sin aviso** al ir a otra sección (probado: editar → Historial → volver = "Sin cambios pendientes."). No hay `useBlocker` ni `beforeunload`. | `ReglasPage.tsx:27` |
| P4 | Media | **"Compartido/Usado por N reglas" cuenta `DespachoRiego`, que no es una regla:** "Antigüedad máxima de la lectura · Usado por 2 reglas" (una es "Ejecución del riego"). | `ParametroRow.tsx:47, 66` |
| P5 | Media | **Resumen y chips ignoran el borrador:** con un valor inválido (70 %) los chips de las otras 3 reglas muestran "70 %" sin marca, y su resumen dice "3 parámetros · **0 modificados**" junto a un punto rojo. El color del chip sigue a `p.modificado` (guardado), no a la edición. | `ReglaCard.tsx:68, 80` |
| P6 | Media | **Sin feedback de guardado:** después de guardar sólo queda "Sin cambios pendientes.". La clase `.avisoOk` existe sin uso (`Parametros.module.css:381`), señal de que se pensó un "Guardado" que no está. | `ParametrosTab.tsx:133-140` |
| P7 | Media | **"Descartar" sin confirmación** borra todas las ediciones de todas las reglas. **"Restablecer fábrica"** significa dos cosas: con una edición pendiente sobre un valor de fábrica, "deshace"; sobre un modificado, "vuelve a fábrica". | `ParametrosTab.tsx:127`; `ParametroRow.tsx:103`; `borrador.ts` (`restablecer`) |
| P8 | Media | **Al volver del Inspector, las reglas abiertas se cierran** (el estado `abiertas` vive en la pestaña que se desmonta): la edición pendiente queda escondida en una tarjeta colapsada. | `ParametrosTab.tsx:68` |
| P9 | Media | **Autoría inconsistente:** la auditoría dice "Modificado por **Ingeniero Agrónomo**" y la Topbar "Mariano Duarte · Productor Viverista". El mock tiene el autor hardcodeado. | `data/mock/mockRepository.ts:111, 235` |
| P10 | Media | **Descripciones que contradicen la conducta:** "Antigüedad máxima…: se bloquea **la evaluación**", pero la regla sólo emite `ABORT_RIEGO` (insumos y mediasombra siguen). "La fábrica son 3 intervalos de publicación del nodo (30 s)" se lee como "fábrica = 30 s". | fixture/backend `ParametrosSeguridad` |
| P11 | Media | **La regla "Un riego por ciclo de lectura" muestra como único chip "35 %"** (el umbral crítico, que sólo usa como excepción). Su concepto central, el ciclo de 240 min, no es un parámetro ni se explica. | fixture; `trazaReglas.ts:271-294` |
| P12 | Baja | **Formato numérico:** "0.2 L/punto" con punto (en el resto de la app se usa coma: "5,4 L"); "**7 índice**"; "Rango 0.1–0.5 L/punto". | `lib/parametrosValidation.ts` (`formatearValor`) |
| P13 | Baja | **Códigos R-xx incrustados en el label** y sólo en 6 de 13 reglas. Bloqueo manual = **S-01**, Sensor sin datos = **S-02**, Mediasombra ≈ **M-02**, Seguimiento ≈ **E-01** (reglas_v2): no figuran. Los emojis vienen del backend (`DeficitCriticoRule.java:57` `"🚨 Déficit hídrico crítico (R-02)"`). | fixture; backend `rules/*.label()` |
| P14 | Baja | "Prioridad" **global** entre ramas (0,1,2,3,4,**5 INSUMO**,6,7,8,**10**,11,12,**20**) mostrada dentro de cada rama: "Límite de dosis diaria · prioridad 5" entre reglas de riego de prioridad 4 y 6. Hay saltos (9, 13–19) sin explicación. | `ReglaCard.tsx:58` |
| P15 | Baja | "Apertura" (parámetros) contra "Cobertura" (historial): "apertura protectora 30 %" ≈ "cobertura 70 %". Es el mismo actuador con dos vocabularios inversos. | fixture; `history.ts:91,143` |
| P16 | Baja | La ventana horaria usa `type=time` y se ve en 12 h ("06:00 AM a 06:00 PM") según el locale; el resto usa 24 h ("06:00–18:00"). | `ParametroField.tsx` |

### 2.4 Problemas de UX

- **No hay entrada por tarea.** Para "regar antes" el usuario tiene que saber que la palanca es *Umbral de riego (humedad de sustrato)* dentro de R-01. Al buscar "riego" se abren 9 reglas. Las reglas **no tienen descripción propia** (sólo la tienen los parámetros): no se lee "cuándo actúa / qué hace".
- **Edición poco segura:**
  - No se muestra "actual → nuevo": mientras edito no veo el 45 original.
  - El rango y la fábrica están en gris 11,5 px a 2,64:1, justo la información más importante para editar con seguridad.
  - No hay un control visual del rango (slider o pista con la marca de fábrica).
  - No hay **vista previa del impacto**: el motor y el Inspector existen, pero no se puede "probar" un borrador contra la última lectura.
  - No hay confirmación con un resumen de cambios ni campo de **motivo** (la auditoría queda "quién/cuándo" sin "por qué").
  - Falta un "Deshacer" tras guardar y un historial de cambios del parámetro (sólo se ve el último "Modificado por").
- **"Compartido con 5 reglas: …"** se resuelve con un chip verde oscuro que domina la fila, más llamativo que el propio valor. Además, el nombre de cada regla repite emoji y código.
- **La barra fija de acciones siempre visible** ("Sin cambios pendientes." con botones deshabilitados) consume espacio sin aportar. En móvil ocupa ~170 px de 844.
- **Los chips de valores** no tienen rótulo (sólo `title`, inaccesible al tacto y al teclado) y no se entienden: "35 % · 6 L · 30 L/h · 12 h". A ≤900 px desaparecen (`Parametros.module.css:454-456`).
- **"Ejecución del riego"** aparece como pseudo-grupo entre Riego e Insumos, con otro formato (sin colapsar).
- **El intro** explica la mecánica del borrador pero no qué pasa al guardar: aplica al próximo ciclo y queda registrado en el Historial.
- **Sin permisos visibles:** cualquier rol puede editar. Si en el sistema real hay roles, la UI debería mostrarlo.

### 2.5 Visual/UI

Las cabeceras de regla tienen 4 zonas de peso parecido: nombre, id monospace, chips y resumen. Los emojis multicolor compiten con los colores semánticos (rojo 🛑/🚨 en reglas que no están en error). El monospace aparece para id, refSpec y claves; el uppercase con tracking, en los títulos de grupo. Al hacer hover sobre la cabecera, los chips (`--line2`) se funden con el fondo (`--line2`). Los chips "Compartido" tienen un radio de 12 px que hace que el multilínea parezca un bloque.

### 2.6 Accesibilidad

- **`tablist` incompleto:** no hay `role=tabpanel` ni `aria-controls`, las dos pestañas están en el orden de Tab y no funcionan las flechas (verificado: ArrowRight no mueve el foco) (`ReglasPage.tsx:53-77`).
- **Nombre accesible del botón de regla** = "› 🔒 Bloqueo manual ManualLockRule · prioridad 0 Sin parámetros configurables": largo, con emoji y clase Java.
- **Puntos de error y de edición:** sólo color y `title`.
- **`outline: none`** en buscador, select e inputs de hora; el foco se reduce a cambiar el borde a `--muted` o no tiene reemplazo (`Parametros.module.css:22,38`; `Reglas.module.css:83`).
- El `<label>` visible de `NumberField` no tiene `htmlFor` (acá se oculta y se usa `aria-label`, así que esta pantalla zafa; el resto de los usos no).
- **Contraste:** `--faint` (rango, fábrica, id, refSpec) 2,64:1; botón primario deshabilitado 2,09:1.

### 2.7 Responsive (390 px) — `shotsC/m-reglas*.png`

- Los chips de valores se ocultan.
- La cabecera de regla ocupa 3 líneas, con id monospace largo.
- Los chips "Compartido" se vuelven bloques de 3 líneas.
- El segmentado baja a su propia fila.
- La barra fija de guardar (aviso + 2 botones) tapa el campo que se está editando.
- El intro es de 5 líneas.
- Sin la sidebar es usable pero larguísimo: 13 tarjetas + grupo de despacho.

---

## 3. Motor de reglas · pestaña **Inspector** (+ `DAGViewer`)

### 3.1 Propósito y tareas

Entender y probar el motor **en vivo** para un sector: "¿por qué MZ-2-057 no está regando ahora?", "¿qué valor vio cada regla y contra qué umbral?", "si cambio X, ¿cambia la decisión?". La traza es la última evaluación en memoria del backend, no es auditoría (`InspectorTab.tsx:1-6`).

### 3.2 Inventario

**Controles:**
- **Macro-zona** (select "MZ-1 · Sector norte" … "MZ-6 · Sector sur". *Ojo:* "Sector norte" es el subtítulo de una **macro-zona**, y "sector" es otra cosa en el dominio).
- **Sector** (select nativo con 100 opciones MZ-x-001…100; por defecto el **primero**, MZ-1-001, `InspectorTab.tsx:34`).
- **Origen** ("Telemetría (al llegar una lectura)" / "Barrido (cada 5 min)").
- Botón **Actualizar** y check **Actualizar cada 5 s**.

**Estados:**
- "No se pudo obtener la evaluación del sector {id}: {msg}".
- "Cargando evaluación…".
- Vacío: "Este sector todavía no se evaluó: sin evaluaciones desde el último arranque del backend. La traza vive en memoria: el barrido la regenera como máximo cada 5 minutos, y la telemetría con cada lectura nueva de la zona."
- "No se pudo cargar el esquema del motor: …" y "Cargando motor de reglas…".

**Resumen "Qué pasó en la última evaluación"**, con la meta "Telemetría · 06/10 22:46:02 · parámetros #4c872236" y líneas con punto de color (`lecturaTraza.ts:54-114`):
- acción (azul): "⛅ Control de mediasombra movió la mediasombra." / "…abrió la electroválvula de riego." / "…dosificó insumo con la bomba peristáltica.";
- alerta (ámbar): "💧 Sustrato saturado (R-04) emitió una alerta.";
- pospuso (ámbar): "… pospuso el riego.";
- bloqueo (rojo): "🔁 Un riego por ciclo de lectura bloqueó la rama de riego." / "…la rama de insumos" / "…todo el motor";
- omitidas (gris): "🚨 Déficit hídrico crítico (R-02), 🌙 …, 💦 … quedaron omitidas por 🔁 Un riego por ciclo de lectura.";
- no alcanzadas: "N reglas no se evaluaron: el motor se detuvo antes.";
- error: "X falló: …";
- nada: "Ninguna regla actuó ni bloqueó: no hubo nada que hacer.".

**DAG en modo traza** (`TrazaGraph` + `ReglaNode`):
- Nodo "Inicio Evaluación".
- Nodos de regla de 270 px con:
  - un chip de estado (**Pasó / Bloqueó / Pospuso / Accionó / Omitida / No alcanzada / Error / Sin evaluar**);
  - hasta 2 comparaciones "etiqueta / recibido op umbral ✓|✗|?", con 🔒 si es una condición fija ("Condición fija: no se edita") y "+N comparaciones más";
  - "Sin comparaciones";
  - las notas "Omitida: la cortó X", "El motor se detuvo en X" y el error.
- Terminales: RIEGO "Riego efectuado/No se regó", INSUMO "Dosificación efectuada/No se dosificó", MEDIASOMBRA "Mediasombra ajustada/No se movió mediasombra", SEGUIMIENTO "Seguimiento evaluado/Sin acciones pendientes", "Rama omitida", "Rama no alcanzada".
- Aristas punteadas o animadas, con rótulo "Completado".
- Leyenda: "Pasó · Bloqueó · Pospuso · Accionó · Omitida / no alcanzada". Zoom arriba a la derecha.
- Clic en un nodo de regla = selección (contorno crema); clic en el fondo = deselección.

**Comparaciones reales del motor** (texto de `trazaReglas.ts`, espejo del backend): "Bloqueo manual activo (no = sí)", "Antigüedad de la última lectura (1 s > 90 s)", "Antigüedad de la humedad de sustrato", "Humedad de sustrato (60.85 % ≥ 75 %)", "Minutos desde el último riego (contra los del ciclo en curso) (sin dato ≤ 0 ?)", "Segundos restantes del riego en curso (0 > 0)", "Hora local (∈ 06:00-18:00)", "Horas desde la última aplicación de insumo", "Probabilidad máxima de lluvia en las próximas 4 h", "Lluvia acumulada en las próximas 4 h", "Horas desde el último riego por déficit crítico", "Dosificaciones en las últimas 24 h (0 dosis ≥ 1 dosis)", "Estado del sector (ok = critical)", "Confianza del diagnóstico", "Índice UV pronosticado (7 índice ≥ 7 índice)".

**Panel del nodo** (`NodoPanel.tsx`):
- título con emoji;
- "ShadingRule · rama MEDIASOMBRA · prioridad 12";
- × para cerrar;
- chip de estado;
- las notas omitida, no alcanzada y error;
- **COMPARACIONES** (etiqueta, "recibido op umbral" y marca, más la clave `mediasombra.uv-umbral` o "Condición fija: no se edita");
- **ACCIÓN Y MOTIVO** (código `NOOP_INFO` / `ACTIVAR_VALVULA` / `ACTIVAR_BOMBA` / `MOVER_MEDIASOMBRA` / `ABORT_RIEGO` / `ABORT_INSUMO` / `ABORT_ALL` / `POSTPONE_RIEGO` / "⚠ ALERTA", más el motivo);
- botón **Editar parámetro** (va a Parámetros con `?regla=`).

### 3.3 Bugs e inconsistencias

| # | Sev. | Hallazgo | Dónde |
|---|---|---|---|
| I1 | **Alta** | **✓/✗ semánticamente invertidos para el usuario.** Una regla "Pasó" (verde) lleva ✗ rojas: "Bloqueo manual activo · no = sí ✗", "Antigüedad 1 s > 90 s ✗", "Sustrato saturado 60.85 % ≥ 75 % ✗". La marca dice si la comparación es verdadera, no si está bien. Las reglas "sin novedad" se ven llenas de errores. | `lecturaTraza.ts:30`; `ReglaNode.tsx` |
| I2 | **Alta** | **"Pasó" para reglas que "no aplican".** R-05/R-06/R-03 repiten las mismas 2 comparaciones de humedad que R-01 (✓ ≥ 35 %, ✗ < 45 %) y salen "Pasó": no hay un estado "No aplica". `clasificarRegla` devuelve `'paso'` para todo NOOP. | `trazaANodos.ts:41`; `trazaReglas.ts:229-244` |
| I3 | Alta | **Payloads y enums crudos:** motivo "**[apertura=30]** Pico de UV: mediasombra en posición protectora."; acciones `NOOP_INFO`, `MOVER_MEDIASOMBRA`; "Estado del sector **ok = critical**" (estados en inglés); claves `riego.saturacion-bloqueo`; "SustratoSaturadoRule · rama RIEGO · prioridad 2". | `trazaReglas.ts:417, 427`; `NodoPanel.tsx:38, 75, 89` |
| I4 | Media | **Números inconsistentes** en la misma vista: nodo "60.85 %" (punto, 2 decimales) contra motivo "Humedad de sustrato **60,85221%**" (coma, 5 decimales). | `lecturaTraza.ts:26`; `trazaReglas.ts:221` (`num` con `toFixed(6)`) |
| I5 | Media | **"Editar parámetro" pierde el contexto:** `setParams({ regla })` borra `sector` y `tab`. Al volver a Inspector se resetea a MZ-1-001. Tampoco hace scroll ni resalta el parámetro (la regla puede estar abierta al fondo de la página). Está en singular aunque la regla tenga 6 parámetros. | `ReglasPage.tsx:36`; `NodoPanel.tsx:97-101` |
| I6 | Media | **"Sin dato" engañoso:** "Minutos desde el último riego: sin dato ≤ 0 ?" (ámbar) en un sector que nunca se regó, que es un estado normal y no un dato faltante. El umbral "0" va sin unidad. | `trazaReglas.ts:276-277` |
| I7 | Media | **Origen "Barrido"** siempre muestra "Sensor sin datos recientes bloqueó la rama de riego" + 7 omitidas, porque el barrido no trae métricas (igual que el `NurseryWatchdog` real). Para el usuario parece una falla del nodo. | `mockRepository.ts` (`getTrazaEvaluacion`); `InspectorTab.tsx:108-109` |
| I8 | Media | **El resumen no dice *por qué*:** "Un riego por ciclo de lectura bloqueó la rama de riego." no incluye el motivo ("ya se regó a las 14:02; el ciclo empezó a las 14:00"), que hay que buscar clic por clic. Las listas de omitidas son largas y con emojis. | `lecturaTraza.ts:54-114` |
| I9 | Baja | Concordancia: "`${n} reglas no se evaluaron`" con n=1 da "1 reglas". | `lecturaTraza.ts:108` |
| I10 | Baja | Hacer clic en terminales e Inicio no hace nada (sin cursor distinto ni feedback). La selección sólo se hace con mouse (`onNodeClick`): con teclado y Enter no abre el panel. | `TrazaGraph.tsx:237-251` |
| I11 | Baja | "parámetros #4c872236": un hash sin significado para el usuario. No avisa si la evaluación usó parámetros que **ya cambiaron**, que es el único uso útil del hash. | `InspectorTab.tsx:146` |
| I12 | Baja | "Actualizar" pone la traza en null: hay un parpadeo ("Cargando evaluación…") y el grafo se vuelve a montar y encuadrar. | `useTrazaEvaluacion.ts` |

### 3.4 UX

- **Arranca en un sector cualquiera** (MZ-1-001) en vez de en uno relevante: el último con acción, uno en alerta o el que se venía mirando. El selector de 100 sectores no tiene búsqueda ni muestra el estado de cada uno.
- **El grafo es la vista principal, pero la pregunta es lineal** ("¿qué cortó y por qué?"). El orden del motor es una cadena por prioridad con 4 ramas: una **lista ordenada** lo expresa mejor y escala mejor (13 nodos × ~140 px).
- **Comparaciones repetidas.** La aplicabilidad de R-01 aparece 4 veces (R-05, R-06, R-03, R-01). Es ruido que tapa la señal.
- **Explicaciones a medias.** Las condiciones fijas (🔒) no explican por qué son fijas. "Prioridad" tampoco se explica.
- **No se pueden probar cambios.** Sería el valor diferencial del Inspector frente al Historial: "¿y si el umbral fuera 50 %?".
- **El panel queda lejos en pantallas angostas.** En ≤1100 px el panel cae **debajo** de un lienzo de ~1.000 px y tocar un nodo no hace scroll hasta él (`shotsC/m-inspector-panel.png`).
- **Terminología.** "Origen", "Telemetría" y "Barrido" son conceptos del backend; para el usuario es "Al recibir una lectura del nodo" / "Revisión periódica (cada 5 min)", y probablemente debería ser una opción avanzada.

### 3.5 Visual/UI

- **Lienzo `--g900` casi negro dentro de la UI crema.** Con el panel abierto, el `fitView` llega a zoom ~0,55: títulos de 12,5 px pasan a ~7 px, etiquetas de 10,5 px a ~6 px y chips de 10 px a ~5 px (`shotsC/i-panel.png`, ilegible).
- **Sin glow en los nodos "Pasó"**, pero con un borde de 2 px saturado en todos: con 11 nodos verdes "Pasó", el único nodo que importa (el azul "Accionó") no resalta.
- **Leyenda y zoom quedan en esquinas opuestas.** La leyenda tapa nodos en móvil.

### 3.6 Accesibilidad

- **Tab recorre ~20 aristas antes de los nodos**, con `aria-label` en inglés que expone clases ("Edge from StaleSensorRule to SustratoSaturadoRule"). Sin foco visible. Enter no abre el panel. No hay alternativa textual navegable del grafo (verificado con Playwright: TAB 15-24 = aristas).
- **Marcas ✓/✗/? de 14-16 px:** ámbar `#e0972c` sobre blanco **2,43:1** en el panel; rojo sobre el nodo oscuro ~3,5:1.
- **Estado por color:** el chip de texto ayuda, pero las aristas (rojo/verde/ámbar/azul) y los terminales dependen sólo del color.
- **Panel del nodo:** `<aside>` sin `aria-live` ni foco programático al abrirse; el lector no se entera.
- **Emojis** en títulos (🔒 con `role=img aria-label="No configurable"`, bien; los del label, no).

### 3.7 Responsive (390 px) — `shotsC/m-inspector*.png`

- **Controles:** 3 selects y un botón en columna, ~350 px de alto.
- **DAG:** `minZoom: 0.4` (`TrazaGraph.tsx:231, 253`) no alcanza para 4 columnas × 330 px. El grafo queda **cortado a izquierda y derecha**, con texto de ~4 px. La leyenda ocupa el 15 % inferior del lienzo.
- **Panel** al pie, fuera de la vista.
- **Arrastre:** `panOnScroll` vuelve a atrapar el gesto.

---

## 4. Transversales

- **Vocabulario sin glosario único.**
  - Las mismas cosas tienen nombres distintos: Ciclo / evaluación / pasada, Rama / grupo, Apertura / Cobertura, Efectiva (ejecutada) / Efectiva (eficaz), "Sector norte" (macro-zona) / sector.
  - Los códigos (R-01…R-06) aparecen incrustados sólo en algunas reglas. reglas_v2 usa S-01, S-02, M-01/02 y E-01 para las demás.
- **Formato numérico inconsistente:** coma en el historial ("5,4 L"), punto en el Inspector y en Parámetros ("60.85 %", "0.2 L/punto"), 5 decimales en motivos; "648 s" en vez de "10 min 48 s".
- **Dos DAGs con dos paletas** (slate hardcodeado contra tokens verdes), dos lógicas de estado (regex contra traza estructurada) y dos leyendas ("No evaluado" contra "Omitida / no alcanzada") para el mismo motor.
- **Emojis como íconos de regla**, definidos en el label del backend: no son temables, se leen en voz alta y compiten con el color semántico.
- **Historial e Inspector desconectados:** el Historial no puede abrir "esta decisión" en el Inspector (la traza histórica no existe) y el Inspector no puede ir al Historial del sector.

---

## 5. Propuestas de rediseño (priorizadas)

### ALTA

**A1. "Receta de la decisión": un componente único para explicar una evaluación, en lugar del grafo negro.** Que lo usen el Historial (por evento) y el Inspector (en vivo). Es una lista vertical clara, en el orden real del motor, en lenguaje natural:

```
┌─ MZ-1-054 · Hoy 18:30 · Al recibir lectura del nodo ─────────────────────┐
│  ⏸  NO SE REGÓ — se pospuso por lluvia                      [R-03]     │
│     Lluvia prevista 80 % y 8 mm en las próximas 4 h. Se vuelve a          │
│     decidir con la próxima lectura.                                        │
│                                                                            │
│  Por qué                                                                   │
│   ● Hacía falta riego (R-01)                                               │
│       Humedad de sustrato 40 %  ·  debajo del umbral de riego 45 %  ✔     │
│                              ·  arriba del umbral crítico 35 %   ✔     │
│   ● Pero se espera lluvia suficiente (R-03)                                │
│       Probabilidad de lluvia 80 %  ·  alcanza 70 %   ✔   [Ajustar]        │
│       Lluvia acumulada 8 mm        ·  alcanza 5 mm   ✔   [Ajustar]        │
│                                                                            │
│  Chequeos previos sin novedad (6)                               ▸ ver     │
│     S-01 Sin bloqueo manual · S-02 Nodo reportó hace 1 s (máx. 90 s) ·     │
│     R-04 Sustrato no saturado (40 % < 75 %) · …                            │
│  Otras etapas                                                              │
│     Insumos — no se dosificó: el sector no está en estado crítico.         │
│     Mediasombra — sin cambios: UV previsto 5 (< 7).                        │
│  Parámetros vigentes en esa evaluación ✓ (no cambiaron desde entonces)     │
└────────────────────────────────────────────────────────────────────────────┘
```

Reglas de diseño:
- **El color codifica el desenlace de la regla**, no el booleano: azul = actuó, ámbar = pospuso o alertó, rojo = cortó, gris = sin efecto o no aplica, punteado = no se evaluó.
- Las comparaciones se escriben como **frases con el verbo correcto según el operador** ("debajo de", "alcanza", "dentro de la ventana 06:00–18:00"). Llevan ✔/✖ neutros (tinta, no rojo/verde) y la condición decisiva en negrita.
- **Primero la conclusión** (titular de una línea con verbo de acción), después el "por qué" (sólo las reglas decisivas) y por último un plegable con lo demás. Cada regla "sin efecto" queda en una línea; las 4 repeticiones de "aplica R-01" se colapsan en una sola.
- Las condiciones fijas se traducen: "Estado del sector: no está crítico", "Nunca se regó en este ciclo", y nada de `ok = critical`.
- Cada umbral configurable tiene un **[Ajustar]** que abre el parámetro en contexto (drawer) sin perder el sector.
- El **DAG queda como vista secundaria** ("Ver como diagrama"), en tema claro, con tokens y con la lista como alternativa accesible.
- **Portabilidad:** `TrazaEvaluacion` ya trae todo (`comparaciones[]` con etiqueta, recibido, operador, umbral, unidad, resultado, configurable y clave; `acciones[]` con tipo y motivo; `estado`; `bloqueadaPor`). Del lado del front sólo se necesitan plantillas por operador y un mapa `tipoAccion → frase`. Del lado del backend, conviene agregar a la comparación un `rol` (`DISPARA` | `IMPIDE` | `APLICABILIDAD`) para saber si un ✔ es bueno o malo, y a la regla un `codigo` (S-01/R-01…), un `nombre` sin emoji y un `icono` semántico.

**A2. Persistir la traza con cada evento del historial y eliminar el DAG "adivinado".** Backend: `historial_evento.evaluacion_id` + traza compacta (reglas decisivas con sus comparaciones y `parametrosHash`), o una tabla `evaluacion_traza` con retención. Front: el Historial muestra la Receta (A1) del evento. Hasta tenerlo, **sacar ya** el `RuleGraph` en modo `activeEvents` (H1–H4) y mostrar sólo "Decidió: {regla} — {motivo}". Esto también resuelve "Ciclo": agrupar por `evaluacion_id` real.

**A3. Historial como feed cronológico plano por día.**

```
Hoy · lunes 6/10                                  [Filtros (2)] [Buscar…]
  23:09  💧 Riego        MZ-6-089   Se regaron 5,4 L (10 min 48 s): humedad 38 % < 45 %.   ● Ejecutado  ↗ +19 pts eficaz
  21:00  ⚠ Alerta crítica MZ-6      Déficit hídrico crítico.                                 
  18:30  ⏸ Riego pospuesto MZ-1-054 Lluvia prevista 80 % / 8 mm en 4 h.
  14:54  ⛔ Riego abortado MZ-2-030 El nodo de MZ-2 no reporta hace 2 h.
Ayer · domingo 5/10
  …                                                     [Cargar día anterior]
```

- Una fila por evento: hora, tipo (ícono + texto), sector como **link**, resumen en lenguaje natural, resultado y, si corresponde, el veredicto del seguimiento. Al expandir en línea aparecen la **Receta (A1)**, los parámetros de la acción y el seguimiento (con el **nombre de la métrica**). Un clic, no cuatro.
- Encabezados por día (Hoy/Ayer/fecha) y carga por día o paginación usando los **filtros del backend** (`?sector&zona&tipo&desde&hasta`), que ya existen.
- Toggle "Agrupar por evaluación" sólo cuando exista `evaluacion_id`; las alertas van como filas de macro-zona, no como un "sector".
- Filtros en un panel y como chips activos con "Limpiar". Persisten en la URL (`/historial?sector=MZ-1-010&desde=…`), con presets de fecha (Hoy, 24 h, 7 días) y validación Desde ≤ Hasta. Se agregan filtros por **regla** y por "Mostrar evaluaciones sin acción (Info)" y "Cambios de configuración".
- Separar **Resultado de ejecución** (Ejecutada / Pospuesta / Abortada / Bloqueada / Informativa) de **Eficacia** (Eficaz / En seguimiento / Sin efectividad). El nivel de alerta va como badge con color propio.
- Links de salida: "Ver sector", "Ver evaluación en vivo" (Inspector), "Ajustar umbral" (Parámetros) y deep-link al evento (`?evento=HE-001`).

**A4. Edición de umbrales segura** (Parámetros):
- **Fila de parámetro:**

  ```
  Umbral de riego (humedad de sustrato)                     [ 45 → 50 ] %
  Por debajo de esta humedad el sector necesita riego.
  ├──────●═════════════○──────────────┤  35 · fábrica 45 · 60
  Debe ser mayor que Umbral crítico (35 %) y menor que Humedad objetivo (65 %).
  Lo usan: R-01 Riego por déficit · R-03 Lluvia · R-05 Ventana · R-06 Pausa
  Impacto ahora: con 50 %, 37 sectores de 600 cumplirían la condición de riego (hoy 12).   [Probar en Inspector]
  ```
  *(Los números del impacto son ilustrativos.)*

- Campo numérico corregido: se puede vaciar mientras se escribe y valida al salir, con `min`/`max` reales. Hay que mostrar el **valor actual junto al nuevo** y una pista de rango con la marca de fábrica.
- **Restricciones cruzadas declaradas en el DTO** (p. ej. `restricciones: [{otra, operador, mensaje}]`) y validadas en el cliente y en el mock, mostradas como texto de ayuda **antes** de fallar.
- **Guardar** abre una hoja de confirmación con el diff ("Umbral de riego 45 → 50 % · afecta 4 reglas"), un **motivo** opcional que va a la auditoría y al evento Configuración, y la aclaración "Se aplica desde la próxima evaluación". Después, un toast "Guardado · Deshacer" (restaurar los valores previos en un PUT).
- **Guardia de salida** (`useBlocker` + `beforeunload`). Separar "Deshacer edición" de "Volver a fábrica". La barra de acciones aparece sólo cuando hay cambios.
- **Historial del parámetro** en línea: los últimos N cambios con autor, fecha, valor y motivo, alimentado por los eventos Configuración.
- **Portabilidad:** las restricciones cruzadas ya existen en `CatalogoParametros.restriccionesReales()`; sólo falta exponerlas. La "vista previa del impacto" puede ser un `POST /api/rules/evaluaciones/simular` que reciba el borrador y devuelva la traza por sector o un conteo agregado, reutilizando el orquestador. En la demo ya existe `evaluarMotor(catalogo, entrada)` como función pura.

**A5. Lenguaje del usuario, no del código** (aplica a las tres vistas):
- Ocultar las clases Java, claves, refSpec, hash, prioridades y enums en un plegable "Detalles técnicos" (útil para el equipo, no para el viverista).
- Códigos de regla como **badge separado y consistente** con reglas_v2 (S-01 Bloqueo manual, S-02 Nodo sin respuesta, R-01…R-06, N-/F- dosificación, M-02 mediasombra, E-01 seguimiento); las reglas sin código de reglas_v2 llevan uno propio.
- Traducir los estados (`critical` → "crítico") y los motivos (sin `[apertura=30]`; "Mediasombra a 30 % de apertura (70 % de cobertura) por UV alto").
- Unificar "apertura/cobertura", "Ejecutada/Eficaz", "macro-zona" (y el subtítulo "Zona norte", no "Sector norte"), y "evaluación" en lugar de "ciclo".
- Formato numérico único (coma decimal, máximo 1–2 decimales; duraciones en "10 min 48 s"; "UV 7" en lugar de "7 índice"; "+19 pts").

**A6. Accesibilidad base:**
- Labels asociados en todos los filtros y foco visible (`:focus-visible` con anillo de marca, en lugar de `outline: none`).
- Patrón `tablist` completo (con `tabpanel`, `aria-controls`, roving tabindex y flechas).
- `aria-expanded`/`aria-controls` en todo disclosure.
- Contraste ≥ 4,5:1 para el texto (reemplazar `--faint` en información útil y el lila del botón del DAG) y ≥ 3:1 para las marcas.
- Estado siempre con **texto o ícono además de color**.
- La Receta (A1) es la alternativa accesible al DAG; si el DAG queda, `nodesFocusable` con Enter para abrir el panel, aristas no focalizables (`edgesFocusable={false}`) y `aria-label` en español.

**A7. Responsive real** (supone que el shell global ya tiene una sidebar colapsable, a coordinar con la revisión del layout):
- Las filas de la cadena pasan a "etiqueta arriba / texto abajo" por debajo de 600 px.
- Los filtros van a un bottom-sheet.
- La Receta reemplaza al DAG en móvil.
- Las filas de parámetro van en una sola columna con el campo a ancho completo y la barra de guardar compacta y sólo con cambios.
- El panel del nodo pasa a ser un bottom-sheet o drawer que se abre en foco.
- `touch-action`/`preventScrolling` para no atrapar el scroll.

### MEDIA

- **M1. Inspector con mejor punto de partida:**
  - Arrancar en el último sector con evento o en el sector que llega por URL, con un buscador de sectores (combobox con estado: regando, en alerta, bloqueado).
  - Mostrar "hace 2 min" junto a la hora.
  - Que el resumen incluya **el motivo** de la regla decisiva y que cada línea lleve a su regla en la Receta.
  - Renombrar la pestaña a "Evaluación en vivo".
  - Mandar "Origen" a "Opciones avanzadas", explicado.
  - Avisar "Esta evaluación usó parámetros anteriores a tu último cambio" cuando el hash difiera del catálogo vigente.
- **M2. "Editar parámetro" sin perder contexto:**
  - Abrirlo en un drawer sobre el Inspector, o como mínimo conservar `sector`.
  - Hacer scroll y resaltar el parámetro concreto (`?param=riego.umbral-humedad`).
  - Ofrecer "Volver a la evaluación".
- **M3. Reglas como oraciones** en la tarjeta de regla (con chips editables en línea), que responden "qué hace" sin abrir nada:

  > **R-01 Riego por déficit** — Riega cuando la humedad de sustrato baja de **[45 %]** (y no de **[35 %]**, que es crítico), hasta llegar a **[65 %]**, a razón de **[0,2 L]** por punto y con un tope de **[6 L]** por riego. El caudal del emisor es **[30 L/h]**.

  Junto con esto, un bloque de "Ajustes frecuentes" arriba de todo: Regar antes/después, Ventana horaria, Lluvia que posterga, Confianza para dosificar.
- **M4. Vista "Por parámetro" agrupada por `familia`** (Seguridad, Riego, Despacho, Insumos, Mediasombra, Diagnóstico), con filtro "Distintos de fábrica" que también considere el borrador.
- **M5. Chips de valor rotulados** ("Umbral 45 %", "Crítico 35 %") y visibles también en móvil (en dos líneas). El resumen tiene que contar las ediciones pendientes ("1 sin guardar").
- **M6. "Compartido con…"** como texto secundario con íconos de regla (no un pill oscuro), y que no cuente `DespachoRiego` como regla ("También lo usa: Ejecución del riego").
- **M7. Estados ricos:** skeletons en la carga, "Reintentar" en los errores, vacíos con causa y acción ("Ningún evento entre 06/10 y 01/10: la fecha 'Desde' es posterior a 'Hasta'"). Que el Historial se refresque solo (o muestre un aviso de "N eventos nuevos").
- **M8. Configuración visible en el Historial:** tipo "Cambio de parámetros" con el diff y un link al parámetro. Que el mock lo genere al guardar.

### BAJA

- **B1.** Íconos SVG del sistema (`Glyph`) por rama o regla en lugar de emojis; `nombre` limpio desde el backend.
- **B2.** Eliminar el MiniMap y los rótulos "Continúa"; si el DAG sigue, tema claro con tokens y una sola paleta para ambos modos.
- **B3.** Limpieza: CSS muerto (`HistorialPage.module.css:16-77`, `.zona`), tokens indefinidos (`--border`, `--surface-sunken`), estilos inline (`HistorialTimeline.tsx:136-151, 186`), y `contar()` en todas las pluralizaciones.
- **B4.** Export CSV/PDF del historial filtrado (trazabilidad para auditorías).
- **B5.** Corregir los textos de mock que contradicen el dominio (H14, H10, P10, P15).
- **B6.** Ventana horaria en 24 h con un control propio (no depender del locale de `type=time`).

---

## 6. Checklist "no perder nada" en el rediseño

- **Historial:** los 6 filtros y el contador; el agrupamiento temporal; tipo, sector, zona y hora exacta; resultado y nivel de alerta; volumen, duración y regla; la cadena lectura → decisión → acción; el seguimiento (métrica, antes, ahora, delta, latencia y veredicto); estados carga/error/vacío; los eventos Info y Configuración; la explicación del motor por evento; el carácter de sólo lectura (spec).
- **Parámetros:** búsqueda (incluida la apertura automática de coincidencias), filtro por rama, "Sólo modificados", vistas por regla y por parámetro; los 21 parámetros con etiqueta, descripción, unidad, rango, fábrica, valor, modificado, autor y fecha, refSpec (en "técnico"), usado por y compartido; errores de cliente y de servidor (por clave y generales), "errores ocultos por filtros" con su atajo; badge de cambios en la pestaña; borrador por clave que sobrevive al cambio de pestaña; restablecer; descartar; guardar; deep-link `?regla=`.
- **Inspector:** zona, sector, origen, actualizar y auto 5 s; resumen por tipo de línea; los 8 estados de regla y los 4 de terminal; comparaciones (recibido, operador, umbral, unidad, resultado, configurable o fija, clave); acciones y motivos; omitida o no alcanzada por quién; error; editar parámetro; deep-link `?tab=inspector&sector=`; el aviso "traza en memoria".

---

## Anexo — capturas generadas

`scratchpad/shotsC/`:
- **Historial:** `h-riego-efectiva.png`, `h-riego-pospuesta.png` (R-03 en verde), `h-riego-abortada.png` (terminales verdes), `h-insumo-abortada.png` (culpa invertida), `h-alerta.png`, `h-mediasombra.png`, `h-insumo-seg.png`.
- **Parámetros:** `r-edit-valid.png`, `r-edit-invalid.png`, `r-after-save.png`, `r-modificado.png`.
- **Inspector:** `i-panel.png` (texto de 6 px con el panel abierto), `i-barrido.png`, `i-focus.png`.
- **390 px sin sidebar:** `m-historial*.png`, `m-reglas*.png`, `m-inspector*.png`.

Scripts: `scratchpad/c1.mjs`…`c7.mjs`.
