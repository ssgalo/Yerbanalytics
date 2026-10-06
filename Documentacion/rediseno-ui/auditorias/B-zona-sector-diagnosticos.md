# Auditoría UI/UX — Macro-zona · Detalle de sector · Diagnósticos de IA

> Yerbanalytics · frontend `Desarrollo/frontend/src` · modo demo (`VITE_DATA_SOURCE=mock`, semilla 20260613)
> Fecha: 06/10/2026 · Alcance: `/mapa?zona=…`, `/sector/:id`, `/diagnosticos`
> No se modificó ningún archivo del repo. Rutas citadas relativas a `Desarrollo/frontend/src/` salvo indicación.

## 0. Método y convenciones

- Capturas previas (`scratchpad/shots/`) + capturas nuevas en `scratchpad/shotsB/` (1440×900, 1280×720, 1024×768, 768×1024): `diag-top`, `diag-baja` (estado vacío), `diag-nc`, `diag-modal-nc`, `map-mz1`, `map-mz4`, `map-focus`, `map-ph-24h/7d/30d`, `map-1024/1280/768`, `sector-fold`, `sector-offline(-scrolled|-captura)`, `sector-riego`, `sector-mz4`, `sector-1024`, `diag-768`.
- Sondas de datos con `vite-node` sobre `buildNursery(20260613)` y `buildHistory` (scripts en `scratchpad/probeB/`), sondas de accesibilidad con el árbol de accesibilidad de Playwright y conteo de foco con Tab.
- Contrastes calculados con la fórmula WCAG 2.x sobre los tokens de `styles/tokens.css`.
- Severidad de hallazgos: **[P0]** muestra un dato falso/engañoso o rompe una tarea · **[P1]** problema serio de UX/a11y · **[P2]** medio · **[P3]** cosmético.

### Escenario de datos de la demo (para leer los ejemplos)

| Zona | Saludables | Observación | Críticos | Sin señal | Causa dominante | Riego | Bomba |
|---|---|---|---|---|---|---|---|
| MZ-1 | 89 | 7 | 2 | 2 | Diagnósticos individuales | 100 cerradas | 2 dosificando |
| MZ-2 | 0 | 95 | 2 | 3 | Humedad de sustrato 40 % (warning) | 10 regando · 87 en cola | 2 |
| MZ-3 | 0 | 97 | 3 | 0 | Humedad de sustrato 41 % (pospuesto por lluvia) | 100 cerradas | 3 |
| MZ-4 | 0 | 0 | 98 | 2 | Humedad de sustrato 30 % (crítico) | 9 regando · 89 en cola | 84 |
| MZ-5 | 0 | 0 | 98 | 2 | Humedad de sustrato 82 % (saturado) | 100 cerradas | 86 |
| MZ-6 | 90 | 7 | 2 | 1 | Diagnósticos individuales | 100 cerradas | 1 |

Diagnósticos: **414** = Estrés solar 153 (93 Media / 60 Alta) · Clorosis 83 (Media) · Plaga foliar 65 (Alta) · Daño fúngico 53 (Alta) · No concluyente 60 (—). Severidad Baja: **0**. Sólo 38 de 600 sectores tienen al menos un evento en el historial.

---

## 1. Hallazgos transversales (afectan a las tres pantallas)

1. **[P0] Datos sintéticos que llegan a producción.** No existe endpoint de series ni de detalle de sector (`data/repository.ts`, `data/http/httpRepository.ts`): con `VITE_DATA_SOURCE=http`:
   - Los sparklines de las 10 métricas y el histórico 24h/7d/30d del inspector se **inventan** a partir de la lectura actual (`data/selectors.ts:49-75` y `81-102`, usan `series()` de `data/mock/generators.ts:54-64`). El comentario lo admite ("la serie es sintética"), pero la UI lo presenta como histórico real, con mín/máx.
   - El "Seguimiento post-acción" del sector se fabrica en `data/mock/sectorDetail.ts:65-88` (importado por `data/selectors.ts:14` para ambos modos): `antes = ahora < 45 ? ahora + 11 : ahora - 9`, `latencia: '2 h'`. En producción el productor vería un "antes/ahora" que nunca ocurrió.
2. **[P1] Tres nombres para el mismo estado y nomenclatura cruzada.** `offline` se llama "Fuera de servicio" (badge del sector, `data/mock/specs.ts:20`), "Sin señal" (chip de la zona, `features/map/components/ZoneSummary.tsx:26`) y "Sin reporte de telemetría · señal perdida" (motivo, `data/mock/generators.ts:246`). "Observación" vs "En observación", "Saludables" vs "Saludable". Además las macro-zonas tienen subtítulo **"Sector norte/centro/sur"** (`data/mock/specs.ts:169-174`), y "sector" es justamente la unidad de ~100 tubetes: en el topbar del mapa se lee "Macro-zona 2 · Sector norte · 100 sectores".
3. **[P1] Taxonomía de diagnósticos inconsistente con el dominio.** CLAUDE.md define 5 clases (Sano, Clorosis, Estrés solar, **Daño biótico**, No concluyente); el backend acepta además "Ácaro", "Plaga foliar", "Daño fúngico" (`backend/.../service/DiagnosticoService.java:41-43`). El front sólo conoce "Plaga foliar"/"Daño fúngico" (`features/diagnostics/components/DiagFilters.tsx:4`, `data/mock/specs.ts:177-199`): un diagnóstico real "Daño biótico" o "Ácaro" no se puede filtrar y cae al gradiente gris de "Sin diagnóstico". Severidad: la spec `diagnostico-consulta` habla de "crítica / advertencia / saludable", la UI de "Alta / Media / Baja / —".
4. **[P1] Umbrales y parámetros configurables hardcodeados.** El umbral de confianza del 85 % es un parámetro editable del motor (`diagnostico.confianza-minima` en `data/mock/catalogoReglas.fixture.json:556`), pero está fijo en el texto `features/sector/components/DiagnosisCard.tsx:91`, en `data/mock/sectorDetail.ts:25` y en `data/mock/generators.ts:433`. La latencia de seguimiento es `seguimientoLatenciaMin` (minutos, valor 2 en `data/mock/config.ts:36`) y la tarjeta dice "Latencia configurada: **2 h**" (`sectorDetail.ts:84`); el historial global dice "2 min" para el mismo concepto (`data/mock/history.ts` → `buildEvo`). El veredicto usa `Math.abs(delta) > 6` (`sectorDetail.ts:73`) en vez de `seguimientoDeltaMin`.
5. **[P1] Responsive inexistente a nivel shell.** El sidebar de 256 px no colapsa (`components/layout/AppLayout.module.css` sin media queries); a 390 px quedan ~70 px útiles y hay overflow horizontal (ancho real de página: 636 px en mapa, 631 px en sector, 694 px en diagnósticos — ver `shots/mob-*.png`). En las tres features hay **una sola** media query de layout (`features/sector/SectorPage.module.css:163`).
6. **[P1] Contraste del token `--faint` (#97A299): 2,64:1 sobre blanco, 2,35:1 sobre `--bg`.** Se usa para información útil, no decorativa: rango óptimo de cada métrica (9,5 px), unidades, "Nodo testigo · Macro-zona 1", el contador de "Sectores a revisar", la hora de cada diagnóstico, mín/máx del inspector, la hora del historial y los estados vacíos.
7. **[P2] Colores fuera del sistema de tokens.** Hex sueltos en vez de `--ok-ink/--warn-ink/--crit-ink`: `ZoneSummary.tsx:17-26` (#2E7A4F, #A66A12, #A8331C, #EEEDE5), `SensadoCard.module.css:44,172` (#a66a12, ≠ `--warn-ink` #8a5a10), `data/selectors.ts:63-70`, `data/mock/sectorDetail.ts:56-58,86-87,95-110`, y colores de Bootstrap en `DiagnosisCard.module.css:131,137` (#fff3cd/#856404). `SectorHistory.module.css:100,102` usa `--surface-sunken` y `--border`, **que no existen** en `tokens.css`: el estado vacío pierde fondo y borde (se ve como texto suelto en `shots/desk-sector.png`).
8. **[P2] Specs desfasadas respecto de lo implementado** (útil para el rediseño): `openspec/specs/diagnostico-consulta/spec.md` declara "✅ Implementado completamente" pero faltan Escape en el modal, el estado vacío, el orden por recencia, el indicador de actuación en la tarjeta y la antigüedad en el modal. `sector-detail` exige un botón "Volver" a la vista previa (el diseño original lo tenía, `Yerbanalytics.dc.html:410` + `prevView` en `:865`); se reemplazó por un breadcrumb que siempre va a la zona. `diagnostico-consulta` ("sin imagen → la card no muestra miniatura") contradice a `ai-diagnostics` ("conservar el respaldo visual").

---

## 2. Pantalla 1 — Macro-zona (`/mapa?zona=MZ-n`)

Archivos: `features/map/MapPage.tsx`, `components/SectorGrid.tsx`, `ZoneSummary.tsx`, `SensadoCard.tsx`, `SensadoChart.tsx`, `ProblemsList.tsx`, `gridLayout.ts`.

### 2.1 Propósito y tareas del usuario

Ver de un vistazo cómo está una macro-zona (100 sectores) y por qué: condiciones ambientales del nodo testigo (que valen para toda la zona) vs. problemas individuales detectados por la IA. Tareas:
1. ¿Hay algo grave? ¿Cuántos sectores y dónde?
2. ¿La causa es ambiental (toda la zona) o del plantín (sector puntual)?
3. ¿El sistema ya está actuando (riego por tandas, dosificación, mediasombra)?
4. ¿El nodo testigo es confiable (batería, señal, antigüedad)?
5. Ver la tendencia de una métrica y compararla con su rango óptimo.
6. Entrar a un sector puntual.

### 2.2 Inventario de información (todo lo que se ve hoy)

**Encabezado (topbar, compartido)** — título `Macro-zona 1`, subtítulo `Sector norte · 100 sectores` (`MapPage.tsx:69-72`); a la derecha clima `21°C · UV 7 · Parcial nublado`, campana con `5`, usuario `Mariano Duarte · Productor Viverista`.

**Rastro** — botón `‹ Panel general` → `/` (`MapPage.tsx:110-115`). Sin `?zona=` o con zona inexistente redirige a `/` sin mensaje (`:94`).

**Tarjeta de grilla** (columna 1, ancho = ancho exacto de la grilla)
- Resumen por estado (`ZoneSummary`), 4 chips con número + etiqueta, que además hacen de leyenda: MZ-1 `89 Saludables` · `7 Observación` · `2 Críticos` · `2 Sin señal`; MZ-2 `0 · 95 · 2 · 3`; MZ-4 `0 · 0 · 98 · 2`. Los ceros se muestran con el mismo peso.
- Grilla de 100 botones, 10 por fila (`layout.sectoresPorFila`), coloreados por estado (#3FA06A / #E0972C / #DD5238 / #A9B2AB), con el número 1–100 si la celda mide ≥ 20 px (`SectorGrid.tsx:19,58`), tooltip nativo `MZ-1-010 · Crítico` (`:47`), hover con escala 1,12 y sombra, clic → `/sector/MZ-1-010`. Tamaño de celda medido: **28 px** a 1440×900 (número a 7 px), **~14 px** a 1280×720 (sin número), puntos a 1024.

**Panel "Valores sensados"** (columna 2, ocupa el resto)
- Título `Valores sensados`, origen `Nodo testigo · Macro-zona 1`.
- Aviso condicional de lectura vencida: `⚠ Sin reporte reciente — los valores no son vigentes.` (`SensadoCard.tsx:44-49`; no aparece en la demo por defecto).
- 10 botones de métrica, 2 columnas, sin agrupar (aunque el dato `grupo` ambiente/nutrición existe, `SensadoCard.module.css:49`). Cada uno: etiqueta, `*` naranja si el rango es provisional (tooltip "… · rango provisional, pendiente de validación agronómica"), valor coloreado por estado, unidad, rango óptimo alineado a la derecha y sparkline de 14 puntos coloreado por estado. Estado activo: fondo `--ok-soft` + borde de marca, `aria-pressed`. Sin dato: `—` en gris, sin unidad ni sparkline.
  - MZ-1: Humedad de sustrato **61 %** (42–68%) · Humedad ambiental 75 % (62–84%) · Temperatura del aire 22.2 °C (18–27 °C) · Luminosidad 64 % (35–70%) · Temperatura del sustrato* 19.8 °C (16–24 °C) · Nutrientes (CE) 1.4 dS/m (1–1.9 dS/m) · pH del sustrato* 5.2 pH (5–6 pH) · Nitrógeno* 153 mg/kg (100–200 mg/kg) · Fósforo* 36 mg/kg (30–60 mg/kg) · Potasio* 164 mg/kg (120–240 mg/kg).
  - MZ-2: Humedad de sustrato **40 %** en naranja; MZ-4: **30 %** en rojo; MZ-5: **82 %** en rojo.
- Pie del nodo: `🔋 67%` (en naranja + texto `Batería baja` si < 20 %), `📶 -60 dBm`, `🕒 hace 25 min`. MZ-2: 30 % · -56 dBm · hace 16 min; MZ-4: 49 % · **-87 dBm** (señal débil, sin marcar) · hace 17 min. La MAC del nodo (`A4:CF:12:9A:00:01`) y el `ts` absoluto existen en el dato pero no se muestran.

**Inspector de métrica** (estado interactivo; entra deslizándose sobre la grilla y tapa también el resumen por estado)
- Se abre al clic en una métrica, se cierra con la ✕ (`aria-label="Cerrar histórico"`) o con un segundo clic en la misma métrica. El rango elegido (por defecto `7d`, `MapPage.tsx:46`) se conserva al cambiar de métrica.
- Encabezado: cuadradito de color, título (h3) con la etiqueta, chip `rango provisional` si aplica, selector `24h · 7d · 30d`, botón cerrar.
- Lectura actual grande: `61 %  lectura actual · óptimo 42–68%`.
- Gráfico de área + línea (SVG 660×170, `preserveAspectRatio="none"`), sin ejes, sin fechas, sin tooltip.
- Pie: `mín 57%` · `banda óptima 42–68%` · `máx 61%` (MZ-1 humedad de sustrato 7d). 24h: 59–65 %; 30d: 46–65 %. pH MZ-1: 24h 5.1–6.5 pH; 30d 4.7–7.3 pH.
- Vacío: `El nodo testigo no reportó humedad de sustrato — no hay serie para mostrar.`

**Lista "Sectores a revisar"** (columna 3, 300 px fijos)
- Título + contador en gris claro: `Sectores a revisar 9` (MZ-1), `97` (MZ-2), `98` (MZ-4). El contador se oculta si es 0.
- Filas: punto de color del estado, ID (`MZ-1-010`), motivo (`Daño fúngico` o, si la causa es ambiental, `Humedad de sustrato 40%`), badge de **severidad del diagnóstico** (`Alta` / `Media` / `—`).
- Orden: críticos primero, después observación; dentro de cada grupo, número de sector.
- Vacío: `Ningún sector requiere revisión.`
- Clic → detalle del sector. Los sectores "Sin señal" no figuran.

**Datos disponibles que la vista NO muestra:** estado de la electroválvula por sector (Regando / En cola / Cerrada), bomba (Dosificando), apertura de mediasombra, diagnóstico y confianza en la grilla, sectores sin señal en la lista, MAC/ID del nodo, hora absoluta de la lectura, agrupación ambiente/nutrición, texto del estado de cada métrica (bajo/alto).

### 2.3 Bugs e inconsistencias

| # | Sev. | Hallazgo | Dónde |
|---|---|---|---|
| M1 | P0 | **Historias imposibles entre rangos.** Cada rango usa su propia semilla aleatoria: en MZ-1 humedad de sustrato 24h = 59–65 % pero 7d = 57–61 % (la ventana de 7 días contiene a la de 24 h, su máximo no puede ser menor). MZ-2: 24h 31–42 % vs 7d 37–42 %. El sparkline de la tarjeta (14 puntos, otra semilla) no coincide con ningún rango del inspector. | `data/selectors.ts:51-53, 89-93` |
| M2 | P0 | En modo `http` todo el histórico (sparklines + inspector) es sintético (ver §1.1). | `data/selectors.ts:43-48` |
| M3 | P1 | **El gráfico del inspector escala engañosamente.** `pathFrom` normaliza mín→máx de la serie a todo el alto (`data/mock/generators.ts:67-77`): una variación de 4 puntos (57→61 %) se dibuja como un precipicio de pared a pared. No hay eje Y, ni eje X con fechas, ni la banda óptima dibujada (sólo se *nombra* en el pie, `SensadoChart.tsx:102`); "mín/máx/banda" se ubican donde iría un eje X y parecen etiquetas temporales. | `SensadoChart.tsx:72-104` |
| M4 | P1 | `preserveAspectRatio="none"` estira el trazo: la línea cambia de grosor según la pendiente y aparecen "muescas" (visibles en `shotsB/map-ph-24h.png`). | `SensadoChart.tsx:76` (también `components/ui/Sparkline.tsx`) |
| M5 | P2 | El área se genera para alto 150 (`selectors.ts:93`) pero el viewBox mide 170 (`SensadoChart.tsx:75`): queda una franja vacía del ~12 % abajo y el gradiente "termina en el aire". | — |
| M6 | P1 | **Título truncado/tapado.** `.title` con `white-space: nowrap` sin `overflow/text-overflow` dentro de un `.titleWrap` con `min-width: 0`: "Humedad de sust…" queda cortado y pisado por el selector de rango; en pH el chip `rango provisional` queda **completamente oculto** detrás del selector (`shotsB/map-ph-24h.png`). El inspector mide lo que mide la grilla (~360 px) y no le entra título + 3 botones + ✕. | `SensadoChart.module.css:26-31, 40-46` |
| M7 | P1 | La línea del inspector se pinta con el color del estado **actual**: si la serie salió de rango (pH 30d llega a 7.3, umbral crítico 7.0) igual se ve toda verde. | `SensadoChart.tsx:88-95` |
| M8 | P1 | **"Sectores a revisar 97"**: la cuenta es consistente (95 obs. + 2 crít.), pero la lista repite 95 veces `Humedad de sustrato 40%` — un problema **de zona** (un único nodo testigo) presentado como 95 problemas de sector. En MZ-4 son 98 filas idénticas `Humedad de sustrato 30% · Alta`. La lista deja de servir para priorizar. | `ProblemsList.tsx:34-46`, motivo en `generators.ts:249-255` |
| M9 | P1 | **Badge que no corresponde al texto.** La fila muestra el motivo ambiental ("Humedad de sustrato 40%") con el badge de severidad del *diagnóstico de IA* ("Media" por una Clorosis que no se nombra, o "—" por un No concluyente). Lectura natural: "humedad 40 % = severidad Media". | `ProblemsList.tsx:42-44, 63-73` |
| M10 | P2 | Orden secundario inexistente: dentro de "observación", un `—` (No concluyente) aparece entre dos `Media`. No se ordena por severidad de diagnóstico ni por confianza. | `ProblemsList.tsx:36` |
| M11 | P2 | Los sectores **Sin señal** (2–3 por zona) no están en "a revisar", aunque requieren intervención técnica. | `ProblemsList.tsx:35` |
| M12 | P2 | Sector "offline" con el nodo de la zona sano: en MZ-1 el nodo reporta "hace 25 min", pero MZ-1-054 figura "Sin señal" con motivo "señal perdida". Por dominio el sensado no es por sector; el generador lo modela como "nodo del sector sin responder". La UI no explica qué es lo que perdió señal (¿actuadores? ¿cámara?). | `generators.ts:219-220, 246` |
| M13 | P2 | El `*` de "rango provisional" no tiene leyenda visible en el panel (sólo tooltip). `aria-label` sobre un `<span>` sin rol no se anuncia de forma confiable. | `SensadoCard.tsx:106-110` |
| M14 | P2 | Señal débil (-87 dBm en MZ-4) no se marca; batería sólo alerta < 20 %. Unidad cruda sin escala ("-56 dBm" no le dice nada al productor). | `SensadoCard.tsx:63-79` |
| M15 | P3 | Unidad redundante: "5.2 pH", "5–6 pH". | `selectors.ts:62` |
| M16 | P3 | Comentarios desfasados: `ZoneSummary.module.css:1` dice "dentro del panel de sensado" (está en la grilla); `SectorGrid.module.css:15` cita `RESUMEN_ALTO` "de SectorGrid.tsx" (vive en `gridLayout.ts:18`); `MapPage.tsx:6` dice que hay un botón "Volver" (dice "Panel general"). | — |
| M17 | P3 | Gradiente con `id="ybArea"` fijo (colisiona si alguna vez hay dos inspectores en la página). | `SensadoChart.tsx:80` |

### 2.4 Problemas de UX

- **Jerarquía.** Lo primero que se ve es una grilla de 100 cuadraditos del mismo color (MZ-2 y MZ-4 son 100 % naranja o rojo): no comunica nada que el resumen no diga. La causa —una métrica de zona fuera de rango— está en una tarjeta de 10 con el mismo peso visual que Potasio, y la respuesta del sistema (riego por tandas: 10 regando / 87 en cola) **no se ve en ningún lado**.
- **Falta una "frase de estado" de la zona:** p. ej. "Déficit hídrico (40 % < 42 %) · riego en curso por tandas: 10/97 · 2 sectores con plaga foliar".
- **La grilla tiene una sola capa (estado).** No se puede ver qué sectores tienen diagnóstico de IA vs. causa ambiental, cuáles están regando/en cola/dosificando, ni la antigüedad de la última captura.
- **Sin vínculo entre lista y grilla:** al pasar el mouse por una fila de "a revisar" no se resalta la celda y viceversa. El tooltip nativo de la celda sólo dice "MZ-1-010 · Crítico" (no el motivo ni el diagnóstico) y no existe en táctil.
- **El inspector tapa la grilla y el resumen:** no se puede correlacionar la métrica con los sectores mientras se la mira. El rango óptimo de cada tarjeta está en 9,5 px gris claro: la comparación clave (actual vs. óptimo) es la menos legible del panel.
- **Afordancias:** las tarjetas de métrica son botones pero parecen tarjetas estáticas (sin ícono de "ver histórico"); las celdas sin número a ≤ 1280 px no invitan al clic.
- **Navegación:** sólo "Panel general". No hay salto a la zona vecina (la spec lo prohíbe a propósito —ver `sensado-macrozona` "Acceso exclusivo desde el panel general"—; vale revisarlo para el recorrido técnico zona por zona), ni a "Diagnósticos de esta zona", ni al nodo en Hardware, ni al historial filtrado por zona (el backend ya soporta `GET /api/historial?zona=`, `HistorialController.java:27-33`).
- **Feedback/estados:** la redirección silenciosa a `/` cuando la zona no existe; ningún estado de carga propio (depende del contexto global); el aviso de lectura vencida es el único estado de error del nodo.
- **Densidad:** 10 sparklines con escala propia cada uno (todas "se mueven" igual de dramáticas) generan ruido sin información; no se distinguen las 5 métricas que afectan el estado de las 5 informativas, salvo por el `*`.

### 2.5 Problemas visuales/UI

- Números de celda a 7 px en blanco al 82 %: contraste 2,7:1 (verde), 2,1:1 (naranja), 1,9:1 (gris). Ilegibles.
- Valores de métrica en naranja `--warn` sobre blanco: 2,43:1; en verde `--ok`: 3,26:1 (17 px bold no alcanza "texto grande").
- Chip "Sin señal": número #A9B2AB sobre #EEEDE5 = 1,85:1 (el "2" casi no se ve). "Observación" naranja sobre `--warn-soft`: 2,15:1.
- Columna de "a revisar" de 300 px fijos y la de mapa de ancho exacto: a 1024 px las etiquetas del resumen quedan en "S…, O…, C…, Si…", las de métricas en "Humedad de…", "Temperatura…" y el rango óptimo se sale de la tarjeta (`shotsB/map-1024.png`).
- Tres estilos distintos de "chip de estado" en la misma pantalla (resumen, badge de la lista, punto de la lista).
- El selector de rango (pastilla gris) y la ✕ (cuadrado con borde) no comparten estilo con el resto de botones de la app.

### 2.6 Accesibilidad

- **Celdas:** nombre accesible = el número ("10") y la descripción = el `title`; con celdas < 20 px el botón **no tiene texto** y depende del `title`. Debería ser `aria-label="Sector MZ-1-010, crítico, daño fúngico"`.
- **Orden de tabulación:** 100 tab-stops de la grilla antes de llegar a las métricas (medido: hay que pasar por 100 celdas). Falta patrón `role="grid"` con *roving tabindex* o un "saltar a métricas".
- **Inspector cerrado pero enfocable:** tiene `aria-hidden="true"` pero no `inert`; sus 4 botones (`24h`, `7d`, `30d`, `Cerrar histórico`) reciben foco por teclado estando fuera de pantalla (verificado: tab-stops 109–112). Violación `aria-hidden-focus`. `MapPage.tsx:133-146`.
- El selector de rango no expone estado (`aria-pressed`/`role="radiogroup"`). El gráfico SVG no tiene `title/desc` ni tabla alternativa.
- **Dependencia del color:** el estado de cada celda es sólo color; naranja vs. verde tiene contraste 1,34:1 entre sí (indistinguible con deuteranopía). Sin patrón, ícono ni borde.
- **Targets:** 28 px a 1440×900 (aceptable), ~14 px con 6 px de gap a 1280×720 → separación de centros 20 px < 24 px: falla WCAG 2.2 SC 2.5.8 (Target Size Minimum).
- Foco visible: se usa el outline por defecto del navegador (visible en `shotsB/map-focus.png`), sin estilo propio pero funcional.

### 2.7 Responsive (390 px)

- Inutilizable: el sidebar fijo deja ~70 px; la grilla se vuelve puntos, los chips del resumen muestran "S…/O…/C…", el panel de sensado y la lista quedan fuera de pantalla con overflow horizontal (página de 636 px de ancho). `shots/mob-mapa.png`.
- Ya a **768 px** el panel de sensado colapsa a **0 px** de ancho y desaparece (la columna es `minmax(0, 1fr)` y la de "a revisar" fija en 300 px, `MapPage.tsx:121`; `SENSADO_MIN` sólo limita la grilla, `:32,88`). `shotsB/map-768.png`.
- La premisa "sin scroll de página" (`MapPage.module.css:6-9`) no es sostenible en mobile: hace falta un layout apilado.

### 2.8 Propuestas de rediseño — Macro-zona

**Alta**
1. **Cabecera de diagnóstico de zona** (encima de todo): estado + causa + respuesta del sistema en una línea: `MZ-2 · En observación — Humedad de sustrato 40 % (óptimo 42–68 %) · Riego por tandas: 10 regando · 87 en cola · 2 sectores con Plaga foliar (Alta)`. Derivable hoy de `Zona.lectura`, `sector.actuadores` y `sector.diagnosis`.
2. **"A revisar" agrupada por causa**, no por sector: un grupo "Ambiental de zona (97 sectores) — Humedad de sustrato 40 %" colapsable, un grupo "Diagnóstico de IA" (con nombre de la anomalía y su badge propio) y un grupo "Técnico / sin señal". Separar visualmente motivo y severidad del diagnóstico.
3. **Inspector de métrica honesto:** eje Y con escala que incluya siempre la banda óptima (o escala fija por métrica), banda óptima sombreada y bandas warn/crit tenues, eje X con fechas/horas, tooltip por punto, línea coloreada por tramo según estado, `vector-effect: non-scaling-stroke` o sin `preserveAspectRatio="none"`. Título con elipsis y selector de rango en una segunda fila. En modo `http`, **ocultar o rotular "sin histórico disponible"** hasta que exista el endpoint de series (proponer `GET /api/zonas/{id}/series?metrica=&desde=&hasta=`).
4. **Capas de la grilla** (selector segmentado): Estado · Diagnóstico IA · Riego (regando/en cola/cerrada) · Insumos · Antigüedad de captura. Como mínimo, un glifo de gota sobre las celdas "Regando" y un contorno punteado en "En cola".
5. **Responsive:** < 1200 px pasar a 2 columnas (grilla + "a revisar" arriba, sensado abajo); < 768 px una columna con la grilla en 10×10 de celdas ≥ 28 px y scroll de página; sidebar colapsable (global).
6. **A11y:** `aria-label` descriptivo por celda, `role="grid"` con flechas, `inert` en el inspector cerrado, patrón/ícono por estado además del color, contraste de números de celda (tinta oscura sobre tinte suave en vez de blanco sobre saturado).

**Media**
7. Tarjetas de métrica con **mini bullet-chart** (posición del valor dentro de la banda óptima) en lugar del sparkline sintético; rango óptimo en tamaño legible; texto de estado ("bajo", "alto") además del color; agrupar "Ambiente" y "Nutrición del sustrato" y separar visualmente las informativas con la leyenda "* rango provisional, no afecta el estado".
8. Pie del nodo con hora absoluta ("14:32 · hace 16 min"), ID/MAC del nodo, calidad de señal en palabras ("débil") y enlace "Ver nodo en Hardware".
9. Tooltip rico (accesible, también en foco) en cada celda: ID, estado, motivo, diagnóstico + confianza, válvula/bomba.
10. Brushing lista↔grilla (hover/foco resalta la celda correspondiente).
11. Enlaces contextuales: "Diagnósticos de MZ-2" (`/diagnosticos?zona=MZ-2`) y "Historial de MZ-2" (`/historial?zona=MZ-2`; el backend ya filtra).

**Baja**
12. Mensaje al redirigir por zona inválida; ocultar ceros en el resumen o atenuarlos; revisar si la regla "sin selector de zonas" (spec) sigue teniendo sentido para el personal técnico (anterior/siguiente zona).

---

## 3. Pantalla 2 — Detalle de sector (`/sector/:id`)

Archivos: `features/sector/SectorPage.tsx`, `components/SectorDiagram.tsx`, `JerarquiaExplainer.tsx`, `DiagnosisCard.tsx`, `CapturaModal.tsx`, `AlcanceDiagnostico.tsx`, `ActuatorsCard.tsx`, `PostActionCard.tsx`, `SectorHistory.tsx`, `geometriaSector.ts`; datos en `data/mock/sectorDetail.ts`, `data/selectors.ts:20-28`.

### 3.1 Propósito y tareas del usuario

Entender **por qué** un sector está como está y **qué está haciendo (o hizo) el sistema** al respecto. Tareas:
1. Ver el diagnóstico de IA del plantín con su foto y su confianza; ampliar la foto para validarla a ojo.
2. Saber si la causa es ambiental (zona) o del plantín.
3. Ver qué actuadores están operando ahora y desde cuándo.
4. Ver si la última acción funcionó (seguimiento antes/ahora).
5. Revisar el historial reciente (lectura → decisión → acción).
6. Saltar al motor de reglas, a la zona, al historial completo; pedir una nueva captura si el diagnóstico es dudoso.

### 3.2 Inventario de información

**Topbar** — `Sector MZ-1-010` / `Macro-zona 1 · Crítico` (`SectorPage.tsx:38-41`).

**Breadcrumb** — `Vivero / MZ-1 / Sector MZ-1-010`; "Vivero" → `/`, "MZ-1" → `/mapa?zona=MZ-1` (`:67-80`).

**Tira de jerarquía** (no interactiva) — `Vivero → Macro-zona → [Sector] → [Bandeja] → Tubete / plantín`, con **Sector y Bandeja** resaltados (`:83`, `activos={[2, 3]}`).

**Encabezado del sector** — cuadradito de color, h2 `MZ-1-010`, badge `Crítico` (variantes `Saludable`, `En observación`, `Fuera de servicio`), motivo `Daño fúngico` (o `Humedad de sustrato 30%`, `Todos los parámetros en rango óptimo`, `Sin reporte de telemetría · señal perdida`), botón-enlace `Ver última evaluación del motor` → `/reglas?tab=inspector&sector=MZ-1-010`.

**Tarjeta "El sector, tal como es en el vivero"** (columna izquierda, primera, ~650 px de alto)
- Subtítulo: `Macro-zona 1 · 100 tubetes (4 bandejas × 25) · 1 microaspersor · nodo testigo de la zona`.
- Dibujo SVG: riel con cámara y texto `riel/cámara del vivero`; caja punteada `Sector MZ-1-010`; 4 bandejas de 5×5 tubetes con rayado del color del estado (offline: gris liso al 45 %); punto azul + círculo punteado del área de riego + texto `microaspersor (1 por sector)`; caja `Nodo testigo / de la macro-zona MZ-1 / no está en este sector` unida con una curva punteada.
- Leyenda: `Los 100 tubetes llevan el diagnóstico del sector` · `Microaspersor + área de riego`.
- Si offline: `Sin señal del nodo testigo de la zona: no hay lectura vigente para evaluar este sector.` (itálica).
- Desplegable `▸ ¿Qué estoy viendo?` → "Este dibujo representa la realidad física de **un sector**: ~100 tubetes agrupados en 4 bandejas, regados por **un** microaspersor compartido, con el riel y la cámara del vivero pasando por arriba. El **nodo testigo** que aparece a un costado NO está físicamente en este sector: pertenece a la macro-zona y su lectura vale para los ~100 sectores de esa zona. El diagnóstico sale de **una foto** del sector y se aplica al sector completo, por eso todos los tubetes llevan el mismo color."

**Tarjeta "Diagnóstico de IA"**
- Miniatura 150×150 (botón): foto real (`background: url(...) center/cover`) o gradiente por clase + hoja; rótulo `Imagen cenital · hace 25 min`; pista `Ampliar` al hover/foco; `aria-label="Ver la captura cenital de MZ-1-010 a tamaño completo"`.
- Estado en 22 px: `Daño fúngico`; badge `Severidad Alta` (o `Severidad Media`, `Severidad —`).
- `Nivel de confianza del modelo 91%` + barra (siempre verde de marca).
- Caja verde si conf ≥ 85: `✓ Confianza por encima del umbral (85%). El motor de reglas puede ejecutar acciones correctivas automáticas.`
- Caja amarilla (inalcanzable en la demo, ver S9): `El sector está fuera de servicio. Este fue el último diagnóstico antes de perder conexión y podría estar desactualizado.`
- Variantes: Sano → `Sano · Severidad — · 98%` + caja verde; Offline → `Sin diagnóstico · Severidad — · %` (sin número).

**Modal de captura** (estado) — fondo oscuro con blur; cabecera `MZ-1-010` / `Daño fúngico · imagen cenital hace 25 min`; botón ✕ (`aria-label="Cerrar"`, recibe foco); imagen cuadrada hasta 760 px. Cierra con Escape, ✕ o clic afuera. `role="dialog"`, `aria-modal`.

**Caja "alcance"** — "El diagnóstico de este sector sale de **una foto** y se aplica al **sector completo** (sus 100 tubetes), no de una medición tubete por tubete. `[a futuro]` una segunda pasada del riel podría fotografiar más de una vez cada sector."

**Tarjeta "Últimas acciones del sector"** — subtítulo `Registro inalterable · lectura → decisión → acción ejecutada`; hasta 5 entradas: hora relativa (`hace 4 h`), punto de línea de tiempo, tipo (`Riego`), badge de resultado (`Pospuesta`, `Efectiva`, `En seguimiento`, `Abortada`), descripción `decisión · acción` (ej. "Lluvia prevista (probabilidad máxima 80% y 8 mm en las próximas 4 h): se pospone el riego. · Riego pospuesto para evitar saturación hídrica del sustrato."). Vacío: `No hay acciones registradas en el historial reciente para este sector.`

**Tarjeta "Actuadores"** (columna derecha) — 3 filas con ícono, nombre, estado y punto: `Electroválvula (riego)` `Cerrada` / `Regando` (verde) / `En cola` (naranja); `Bomba peristáltica` `En espera` / `Dosificando` (verde); `Mediasombra` `Apertura 60%` (nunca "activa").

**Tarjeta "Seguimiento post-acción"** — `Latencia configurada: 2 h`; caja `Antes 52%` → caja verde `Ahora 61%`; `Delta +9%`; badge `En seguimiento` (o `Efectiva`). Vacío (sano u offline): `Sin acciones recientes que requieran seguimiento. El sector opera dentro de los parámetros esperados.`

**Datos que existen y no se muestran:** qué métrica sigue el seguimiento (`evo.metric = 'Humedad de sustrato'`, nunca renderizado), qué acción lo originó y cuándo; hora real de la captura e ID de captura/orden; diagnósticos anteriores del sector; desde cuándo está activo cada actuador; la "lectura" de cada evento del historial; si la lectura de la zona está vencida; `hasFoto` (calculado y sin uso).

### 3.3 Bugs e inconsistencias

| # | Sev. | Hallazgo | Dónde |
|---|---|---|---|
| S1 | P0 | **Seguimiento post-acción fabricado y con veredicto invertido.** `antes` sale de una fórmula (`ahora±11/9`), así que **siempre** es "Delta +9%" si la humedad ≥ 45 y "Delta −11%" si < 45. El veredicto mira `|delta| > 6` sin signo: MZ-2-001 está **regando** y muestra `Antes 51% → Ahora 40% · Delta -11% · Efectiva` (la humedad bajó y se declara efectiva). En crítico siempre "En seguimiento". Además compara una métrica **de zona**, así que los 100 sectores de la zona muestran el mismo antes/ahora. | `data/mock/sectorDetail.ts:65-88` |
| S2 | P1 | La caja **"Ahora" se pinta siempre de verde** (`style={{ color: 'var(--ok)' }}` + fondo `--ok-soft`), aun con 30 % en MZ-4 (crítico) o 40 % (bajo el óptimo). | `PostActionCard.tsx:31-37` |
| S3 | P1 | "Delta +9%" son **puntos porcentuales**, no %. "Delta" es jerga; no dice qué métrica ni qué acción. `evo.metric` existe pero no se renderiza. | `PostActionCard.tsx:42-45` |
| S4 | P1 | "Latencia configurada: **2 h**" está hardcodeada y con unidad errónea: el parámetro real es `seguimientoLatenciaMin` = 2 **min** (y el historial global dice "2 min"). | `sectorDetail.ts:84` |
| S5 | P0 | **Contradicción entre tarjetas de la misma página.** MZ-1-010 crítico: Bomba `Dosificando` (activa) + Seguimiento `En seguimiento` + Historial `No hay acciones registradas…`. Tres fuentes no reconciliadas: actuadores del snapshot, seguimiento sintético y un historial aleatorio independiente (`data/mock/history.ts` sortea zona y sector; sólo 38/600 sectores tienen eventos y no coinciden con su estado: MZ-4-017 "Riego hace 1 h · Efectiva" con humedad 30 % y válvula "En cola"). | `SectorPage.tsx:43-57`, `history.ts:269-320` |
| S6 | P1 | El historial ignora `loading` y `error` de `useHistory()`: mientras carga (o si falla el backend) muestra "No hay acciones registradas" — un falso vacío. Se cargan **todas** las acciones y se filtran en cliente, aunque `GET /api/historial?sector=` existe (`HistorialController.java:27-33`). | `SectorPage.tsx:36, 43-57` |
| S7 | P1 | El subtítulo promete "lectura → decisión → acción" pero la descripción sólo arma `decisión · acción`; la **lectura** (el dato que justificó la acción) se descarta. Se recorta a 5 en silencio, sin "ver todo". `key={i}` por índice. | `SectorPage.tsx:48, 52`; `SectorHistory.tsx:16, 26` |
| S8 | P1 | Estado vacío del historial sin estilo: usa `var(--surface-sunken)` y `var(--border)`, inexistentes. | `SectorHistory.module.css:100, 102` |
| S9 | P1 | **Sector offline:** `Nivel de confianza del modelo %` (sin número: `conf` es `null`), barra vacía, `Severidad —`, rótulo `Imagen cenital · hace 25 min` sin imagen; el modal abre un cuadrado gris de 760 px con una hoja como si fuera una foto. La caja de advertencia offline nunca aparece porque exige `estado !== 'Sin diagnóstico'` y el offline siempre es "Sin diagnóstico". El seguimiento dice "El sector opera dentro de los parámetros esperados" (falso). Los actuadores se muestran como si fueran lecturas vigentes. | `DiagnosisCard.tsx:48, 75, 97`; `PostActionCard.tsx:54`; `shotsB/sector-offline-scrolled.png` |
| S10 | P1 | El texto offline del dibujo dice "Sin señal **del nodo testigo de la zona**", mientras el panel de esa zona muestra el nodo reportando hace 25 min. | `SectorPage.tsx:116-121` |
| S11 | P1 | **La "antigüedad de la imagen" es la de la lectura del nodo**, no la de la captura: `ago = zona.lectura.ago`. Se usa en la miniatura y en el modal ("imagen cenital hace 25 min"). | `SectorPage.tsx:62`; `DiagnosisCard.tsx:48`; `CapturaModal.tsx:50` |
| S12 | P1 | **Modal de captura dibuja la hoja-placeholder encima de la foto real** (la hoja de 220 px se renderiza siempre). El comentario de cabecera ("el modelo todavía no entrega capturas reales") quedó viejo. Además la foto se recorta con `cover` a un cuadrado (no es la imagen "sin procesar" que muestra Diagnósticos). | `CapturaModal.tsx:5-8, 58-60`; `shots/st-sector-captura.png` |
| S13 | P2 | Si la URL de la imagen falla, la miniatura (fondo CSS) queda en blanco con texto blanco: no hay respaldo, a diferencia de `DiagCard` (`onError`). | `sectorDetail.ts:24`; `DiagnosisCard.tsx:27-31` |
| S14 | P1 | **Sano con caja "puede ejecutar acciones correctivas automáticas"**: para un plantín sano el mensaje no tiene sentido (no hay nada que corregir). Umbral 85 % hardcodeado (ver §1.4). | `DiagnosisCard.tsx:81-94`; `sectorDetail.ts:25` |
| S15 | P2 | **"Bandeja" aparece activa** en la tira de jerarquía aunque se esté en Sector (`activos={[2, 3]}`). La vista no tiene nivel bandeja. | `SectorPage.tsx:83` |
| S16 | P2 | **Label `microaspersor (1 por sector)` encima de los tubetes**: el texto (azul 9,5 px) se dibuja en `aspersor.y + 20`, sobre los círculos de las bandejas inferiores; ilegible (contraste ~2:1 sobre el rayado). | `SectorDiagram.tsx:98-100`; `geometriaSector.ts:208-212` |
| S17 | P2 | Mismo mensaje repetido **4 veces** en la misma vista: subtítulo del dibujo, leyenda ("Los 100 tubetes llevan el diagnóstico del sector"), "¿Qué estoy viendo?" y la caja de alcance. Y se contradicen en detalle: "100 tubetes" vs "~100 tubetes", "nodo testigo de la zona" en el subtítulo de un sector vs "no está en este sector". | `SectorPage.tsx:111-112, 125-131`; `SectorDiagram.tsx:127`; `AlcanceDiagnostico.tsx` |
| S18 | P3 | Gramática en la caja de alcance: tras el chip `a futuro` la oración arranca en minúscula ("una segunda pasada…"), queda como fragmento. | `AlcanceDiagnostico.tsx:12-13` |
| S19 | P2 | **El motivo del encabezado oculta una de las dos causas.** MZ-4-001: "Humedad de sustrato 30%" en el encabezado, pero el diagnóstico es Plaga foliar Alta 97 %; MZ-2-001: "Humedad de sustrato 40%" con Clorosis Media. El generador elige una u otra (`generators.ts:249-258`). | `SectorPage.tsx:96` |
| S20 | P2 | ID de sector inexistente → `return null`: página en blanco con el título "Sector". | `SectorPage.tsx:60` |
| S21 | P2 | La spec pide "Volver a la vista previa"; el breadcrumb siempre lleva a la zona. Viniendo del Panel general, de una alerta o del historial, el regreso se pierde. Breadcrumb "Vivero" vs botón "Panel general" del mapa: dos nombres para el mismo destino. | `SectorPage.tsx:67-80`; spec `sector-detail` |
| S22 | P3 | `aria-label` del SVG dice "4 bandejas de 25 **plantines**" y el resto de la UI "tubetes". | `SectorDiagram.tsx:35` |
| S23 | P3 | Mediasombra tiene `active: false` siempre, así que su punto nunca se enciende, aunque esté al 60 %. | `sectorDetail.ts:45-50` |

### 3.4 Problemas de UX

- **Jerarquía invertida.** A 1440×900 la primera pantalla es el dibujo explicativo (~650 px) y el **diagnóstico queda bajo el pliegue** (empieza en y≈955, `shots/desk-sector.png`). El dibujo no aporta información propia del sector salvo el color (que ya está en el badge): es material de onboarding, no de operación.
- **Navegación duplicada y ambigua:** breadcrumb + tira de jerarquía con aspecto de botones (pastillas con borde, ícono y fondo oscuro) que no se pueden clickear. Título repetido 4 veces (topbar, breadcrumb, h2, texto del SVG).
- **"¿Por qué está crítico?" no tiene respuesta completa:** dos causas independientes (ambiente de zona + plantín) y se muestra una. No hay un bloque "Causa ambiental: humedad de sustrato de MZ-4 30 % (óptimo 42–68 %) → ver zona".
- **Acciones que faltan:** pedir nueva captura (clave para "No concluyente"; ya existe `POST /api/capturas/ordenes`, requiere posición de riel), ver diagnósticos anteriores del sector, ver historial completo del sector (`/historial?sector=` — hoy la página de historial no lee parámetros), sector anterior/siguiente, marcar como revisado. Si el producto lo admite, override manual (pausar automatismo / regar ahora) — hoy no hay soporte en backend.
- **Actuadores sin contexto temporal:** "Dosificando" ¿desde cuándo? ¿cuánto? ¿qué regla lo ordenó? El dato vive en el historial pero no se conecta. "En cola" no dice posición en la tanda ni estimación.
- **Seguimiento sin sujeto:** "Antes 52% → Ahora 61%" sin decir de qué métrica, de qué acción, ni de cuándo es "antes".
- **Estados vacíos engañosos** (S6, S9) y sin estado de carga ni error en la página.
- **Densidad baja en la columna derecha** (360 px con dos tarjetas cortas) y alta en la izquierda (todo apilado).

### 3.5 Problemas visuales/UI

- Colores Bootstrap (#fff3cd/#856404) en la advertencia offline, ajenos a la paleta (`DiagnosisCard.module.css:131,137`).
- El "Severidad —" es un chip gris con un guion: ruido visual.
- Barra de confianza siempre verde de marca sin marca del umbral; la pista es `--line2` sobre blanco (1,15:1), casi invisible.
- Columna de hora del historial de 84 px fija (`SectorHistory.module.css:40-41`) en gris `--faint`.
- El chip `a futuro` en naranja (2,63:1) es el único naranja de la página fuera de "estado", y compite con el semáforo.
- El dibujo usa textos de 9,5 px en coordenadas de viewBox: a 1024 px quedan en ~5 px (`shotsB/sector-1024.png`).
- Tamaños de título de tarjeta consistentes (16 px), pero el h2 del sector (24 px) compite con el título del topbar (mismo texto).

### 3.6 Accesibilidad

- `ProgressBar` sin `role="progressbar"`/`aria-valuenow` (0 progressbars en el árbol de accesibilidad).
- El modal de captura no atrapa el foco ni lo devuelve a la miniatura al cerrar.
- El texto "Ampliar" sólo aparece en hover/foco; en táctil no hay pista de que la miniatura se amplía (el cursor `zoom-in` no existe en táctil).
- Estado del actuador sólo por color del punto (verde/naranja/gris) — el texto ayuda, pero el punto es redundante y no tiene alternativa.
- `<details>` correcto; los `<b>` dentro del párrafo son semánticamente `<strong>`.
- Encabezados: h1 (topbar) "Sector MZ-1-010" + h2 "MZ-1-010" redundante; el resto h3, orden razonable.
- Contraste: hora del historial y estado vacío en `--faint` (2,64:1); texto azul del microaspersor 3,09:1 sobre `--bg` y ~2:1 sobre los tubetes.

### 3.7 Responsive (390 px)

- Con el sidebar fijo el contenido queda en ~130 px: títulos de tarjeta partidos palabra por palabra ("El / sector, / tal / como / es…"), el SVG del sector reducido a un rectángulo vacío, la tira de jerarquía en 5 renglones, la miniatura del diagnóstico desbordando y las tarjetas de actuadores/seguimiento cortadas a la derecha (`shots/mob-sector.png`, página de 631 px de ancho).
- Aun sin sidebar, `DiagnosisCard` no envuelve (miniatura fija de 150 px + columna), y el dibujo con texto en viewBox sería ilegible.

### 3.8 Propuestas de rediseño — Sector

**Alta**
1. **Nueva jerarquía de la página:**
   1. Encabezado compacto: `MZ-1-010 · Crítico` + **dos líneas de causa** ("Plantín: Daño fúngico, severidad alta, 91 %" / "Ambiente de MZ-1: en rango ✓" o "Humedad de sustrato 30 % (óptimo 42–68 %) → Ver MZ-4").
   2. **Diagnóstico** como tarjeta principal con foto grande (≥ 320 px), fecha/hora real de la captura, confianza con marca del umbral (leído de `diagnostico.confianza-minima`) y mensajes por caso: Sano → "No requiere acción"; < umbral → "No concluyente: no dispara dosificación" + botón **Pedir nueva captura**; ≥ umbral con anomalía → "Habilita acción automática".
   3. **"Qué está haciendo el sistema"**: actuadores con desde-cuándo, qué regla y vínculo al evento; la cola de riego con posición ("En cola · 23 de 87").
   4. **Seguimiento** derivado del último `ActionRecord.evo` real del sector (el DTO ya trae `evo`): métrica, acción de origen, hora, latencia real en minutos, variación en pp y veredicto con signo y con `seguimientoDeltaMin`; "Ahora" coloreado por el estado de la métrica.
   5. **Historial** con lectura → decisión → acción completas, estados de carga/error, y "Ver historial completo" (`/historial?sector=…`, usando el filtro del backend).
   6. El dibujo y la jerarquía, a un "¿Cómo leer esta vista?" colapsado (o a la ayuda/onboarding), sin repetir el mensaje en 4 lugares.
2. **Corregir los casos borde:** offline sin "%" vacío ni "imagen cenital"; texto offline que diga qué se perdió; no afirmar "opera dentro de parámetros" si no hay datos; respaldo `onError` en la miniatura; quitar la hoja sobre la foto del modal y mostrar la imagen completa (`contain`).
3. **Un solo rastro de navegación** (breadcrumb) + "‹ Volver a {origen}" cuando se llegó desde otra vista (estado de navegación de react-router), como pide la spec.

**Media**
4. Diagnósticos anteriores del sector (mini línea de tiempo de capturas con miniaturas) — permite ver evolución del plantín.
5. Sector anterior/siguiente dentro de la zona; atajo "Siguiente a revisar".
6. Unificar colores con tokens (`--warn-ink`, `--warn-soft`) y definir/usar tokens de superficie para estados vacíos.
7. Layout responsive: una columna < 860 px con el orden de prioridad anterior; miniatura del diagnóstico arriba a ancho completo en mobile.

**Baja**
8. Corregir "Bandeja" activa (o eliminar la tira); gramática de la caja "a futuro"; `aria-label` del SVG; punto de mediasombra con estado propio.

---

## 4. Pantalla 3 — Diagnósticos de IA (`/diagnosticos`)

Archivos: `features/diagnostics/DiagnosticsPage.tsx`, `components/DiagFilters.tsx`, `DiagCard.tsx`, `PhotoModal.tsx`; datos `data/mock/generators.ts:414-459`, `data/mock/capturasDemo.ts`.

### 4.1 Propósito y tareas del usuario

Revisar lo que detectó el modelo en todo el vivero: qué anomalías, dónde, con qué confianza y severidad; validar visualmente las fotos; detectar diagnósticos dudosos (no concluyentes / baja confianza) para repetir capturas; saltar al sector afectado. Tareas:
1. ¿Qué es lo más grave y lo más reciente?
2. Filtrar por anomalía, severidad, zona, sector, fecha, confianza.
3. Abrir la foto en grande y compararla con el diagnóstico.
4. Ir al sector para ver qué se hizo.

### 4.2 Inventario de información

**Topbar** — `Diagnósticos de IA` / `414 diagnósticos registrados`. Sidebar: ítem "Diagnósticos de IA" con badge naranja `414`.

**Barra de filtros**
- `Estado / anomalía` (select): Todas · Estrés solar · Clorosis · Plaga foliar · Daño fúngico · No concluyente.
- `Severidad` (select): Todas · Alta · Media · Baja.
- Contador a la derecha: `414 diagnósticos` (se actualiza con el filtro; `0 diagnósticos` en vacío).
- Estado local (no persiste en la URL; se pierde al volver).

**Grilla** — 4 columnas fijas, 414 tarjetas en una sola página (scroll de ~27.400 px). Cada tarjeta es un `<button>`:
- Miniatura 120 px de alto: foto real (`loading="lazy"`) o gradiente por clase + hoja; chip `DG-010` sobre la imagen.
- Estado `Clorosis` + badge `Media` (o `Alta`, `—`).
- `MZ-2-001 · Macro-zona 2`.
- `Confianza 88%` + barra verde.
- `hace 16 min`.
- Hover: borde de marca, sombra y elevación de 2 px.
- Orden: por la cadena de tiempo (`'hace 16 min' < 'hace 17 min' …`), así que arrancan los de MZ-2 (DG-010 en adelante).

**Modal de foto** (estado)
- Tarjeta de 680 px: cabecera de 340 px con la foto (`contain`) sobre el gradiente de la clase (las franjas laterales quedan del color de la clase: naranja/ocre para Estrés solar, gris para No concluyente), botón redondo ✕ (`aria-label="Cerrar modal"`), chip `Imagen cenital original sin procesar` o `Sin fotografía asociada`.
- Cuerpo: `Estrés solar` + `Severidad Media`; grilla `Sector MZ-2-004` · `Macro-zona Macro-zona 2` · `Confianza 90%` (verde de marca).
- Cierra con ✕ o clic en el fondo. **No** con Escape.

**Datos disponibles no mostrados:** `concluyente` (≥ umbral), hora real (no existe: se usa la de la lectura de la zona), ID de captura, estado del sector hoy, si ya hubo acción por ese diagnóstico.

### 4.3 Bugs e inconsistencias

| # | Sev. | Hallazgo | Dónde |
|---|---|---|---|
| D1 | P1 | **Sin estado vacío.** Severidad "Baja" (0 resultados en la demo: ningún diagnóstico tiene "Baja") deja la página en blanco con "0 diagnósticos" a la derecha. La spec lo exige. | `DiagnosticsPage.tsx:44-48`; `shotsB/diag-baja.png` |
| D2 | P1 | **Opciones de filtro hardcodeadas y desalineadas:** sin "Daño biótico", "Ácaro" ni "Sano" (que el backend acepta); con "Baja" que no aparece nunca; sin opción para severidad "—" (60 No concluyentes no se pueden aislar por severidad). | `DiagFilters.tsx:4-5` |
| D3 | P1 | **Orden por recencia roto:** se ordena por la *cadena* "hace N min" (`localeCompare`): "hace 4 min" quedaría después de "hace 28 min" y "hace 1 h" antes que "hace 16 min". Hoy no se nota por casualidad (las 6 horas de la demo son de dos dígitos). El backend replica el error (`NurseryService.java:290`, `Comparator.comparing(DiagnosisCard::time)`). | `generators.ts:455` |
| D4 | P1 | **La "hora" del diagnóstico es la de la lectura del nodo de la zona** (`agoPorZona`): los 414 diagnósticos tienen sólo 6 horas distintas, una por zona. | `generators.ts:432, 451` |
| D5 | P1 | **Diagnósticos duplicados y contradictorios del mismo sector y la misma hora:** MZ-1-036 = DG-002 "Estrés solar" **y** DG-412 "No concluyente"; MZ-1-062 = DG-004 "Estrés solar" y DG-414 "No concluyente"; MZ-1-051 aparece dos veces "No concluyente". Se agregan 3 "No concluyente" a sectores en observación que ya tenían diagnóstico. | `generators.ts:436-454` |
| D6 | P1 | **Escape no cierra el modal** (verificado). Sin `role="dialog"`/`aria-modal`, sin mover el foco: queda en la tarjeta de atrás. La spec `diagnostico-consulta` lo marca como implementado. | `PhotoModal.tsx:29` (overlay sin rol ni manejo de teclado) |
| D7 | P1 | **Selects sin nombre accesible:** los `<label>` no están asociados (`htmlFor`/`id`); el árbol de accesibilidad los expone como `combobox ""`. Y `outline: none` sin reemplazo: **foco invisible**. | `DiagFilters.tsx:20, 36`; `DiagFilters.module.css:39` |
| D8 | P2 | La spec pide en la tarjeta "badge de confianza en verde + indicador de que el motor puede actuar" si ≥ 85 %: no existe (`concluyente` no se usa en `DiagCard`). La barra es siempre verde aunque sea 65 %. | `DiagCard.tsx:66-72` |
| D9 | P2 | El modal no muestra la antigüedad ni el ID; la spec pide "identificada con el sector, el estado diagnosticado y la antigüedad". | `PhotoModal.tsx:63-82` |
| D10 | P2 | Las franjas del letterbox se pintan con el gradiente de la clase: parecen un marco de color que "codifica" la clase y se confunden con la foto. | `PhotoModal.module.css:45-53` + `PhotoModal.tsx:33` |
| D11 | P2 | Contador duplicado: "414 diagnósticos registrados" (topbar) + "414 diagnósticos" (barra) + badge "414" en el sidebar. El badge naranja sugiere "pendientes" cuando es el total histórico. | `DiagnosticsPage.tsx:27`; `DiagFilters.tsx:51-53` |
| D12 | P3 | Estilo inline en el contador (`style={{ color, fontFamily }}`) en vez de CSS Module. | `DiagFilters.tsx:52` |

### 4.4 Problemas de UX

- **414 tarjetas sin paginar ni virtualizar**, sin agrupar: 27.000 px de scroll; se renderizan 414 botones con imagen en el DOM (las imágenes son lazy, los nodos no). No hay forma razonable de encontrar "lo de hoy en MZ-4".
- **Sin búsqueda ni filtros clave:** por macro-zona, por sector (texto), por fecha/rango, por confianza (≥ umbral / bajo umbral), por "requiere acción" (con anomalía y concluyente). Sin orden configurable (recencia, severidad, confianza).
- **Unidad equivocada para la tarea:** se listan diagnósticos individuales, pero la pregunta del productor es por sector ("¿qué tiene hoy MZ-1-036?"). Con duplicados contradictorios la lista confunde. Falta una vista "último diagnóstico por sector" con historial desplegable.
- **Callejón sin salida:** ni la tarjeta ni el modal llevan al sector (`/sector/:id`), ni a "pedir nueva captura", ni al motor de reglas. Para ver qué se hizo hay que ir a mano al Panel → zona → sector.
- **Jerarquía de la tarjeta:** lo más visible sobre la foto es `DG-010` (ID interno, sin valor para el usuario); el sector, que es lo accionable, está en 12 px gris. La severidad en badge chico; la confianza ocupa más que el estado.
- **"No concluyente"** no explica qué hacer (repetir captura) ni por qué (confianza < umbral).
- **Filtros no persisten** (estado local): volver desde otra vista los resetea; no se pueden compartir enlaces filtrados. No hay "limpiar filtros".
- **Modal pequeño para validar a ojo** (cabecera fija de 340 px, la foto queda en ~510 px de ancho a 1440): sin zoom, sin anterior/siguiente para recorrer resultados del filtro con el teclado.

### 4.5 Problemas visuales/UI

- Tarjetas de altura variable a anchos chicos (títulos de dos líneas), badges recortados ("Me", "Medi") a 768 px.
- La miniatura de 120 px de alto con `cover` corta la hoja a una franja: la foto pierde valor diagnóstico.
- Badge `—` gris para No concluyente: ruido sin significado.
- Hora en `--faint` 11 px (2,64:1).
- Barra de confianza idéntica a la del sector pero más fina; sin marca de umbral.
- Selects nativos con flecha SVG custom: bien; pero sin estado de foco visible (ver D7).

### 4.6 Accesibilidad

- Nombre accesible de cada tarjeta = concatenación larga ("Captura cenital del sector MZ-2-001 DG-010 Clorosis Media MZ-2-001 · Macro-zona 2 Confianza 88% hace 16 min"): repite el sector y empieza por el alt de la imagen. Mejor `aria-label` armado ("Clorosis, severidad media, sector MZ-2-001, confianza 88 %, hace 16 min") y la imagen con `alt=""` dentro del botón.
- Modal: sin `role="dialog"`, sin trampa de foco, sin Escape, sin devolución de foco.
- Selects sin nombre y sin foco visible (D7). `ProgressBar` sin rol.
- Severidad sólo por color del badge (el texto ayuda: "Alta/Media").
- Hover con `transform` sin `prefers-reduced-motion`.

### 4.7 Responsive (390 px)

- 4 columnas fijas (`DiagnosticsPage.module.css:6`): a 390 px las tarjetas miden ~15 px de ancho (tiras verticales de foto, `shots/mob-diagnosticos.png`); a 768 px, ~100 px con badges cortados (`shotsB/diag-768.png`). Filtros apilados ocupan media pantalla sin quedar fijos.
- El modal tiene `padding: 32px` y cabecera de 340 px fija: en mobile la foto queda chica y el cuerpo en 3 columnas apretadas (`PhotoModal.module.css:105`).

### 4.8 Propuestas de rediseño — Diagnósticos

**Alta**
1. **Estado vacío** con texto ("No hay diagnósticos con Severidad: Baja") y botón "Limpiar filtros".
2. **Filtros completos y en la URL** (`?estado=&sev=&zona=&sector=&desde=&conf=`): macro-zona, búsqueda por sector, rango de fechas, confianza (≥ umbral / bajo umbral), "con foto". Opciones de estado/severidad **derivadas de la taxonomía** (idealmente del backend o de los datos), incluyendo "Daño biótico", "Ácaro", "Sano" y "Sin severidad". Chips de filtros activos.
3. **Orden correcto por timestamp real** (agregar `ts` a `DiagnosisCard` en front y back; dejar `time` sólo para mostrar) + selector de orden (recientes, severidad, confianza).
4. **Modal accesible y útil:** `role="dialog"`, Escape, trampa y devolución de foco; foto grande (alto según viewport) con zoom; antigüedad e ID de captura; **botones "Abrir sector"** y **"Pedir nueva captura"**; navegación ← → entre resultados filtrados.
5. **Paginación o virtualización** (p. ej. 48 por página o *infinite scroll* con ventana) y/o **agrupación** "último diagnóstico por sector" con contador de diagnósticos previos.
6. **Responsive:** `grid-template-columns: repeat(auto-fill, minmax(240px, 1fr))`; 1 columna en mobile con tarjeta horizontal (miniatura a la izquierda).

**Media**
7. Tarjeta reordenada: sector + zona como título (lo accionable), anomalía + severidad debajo, ID `DG-` fuera de la foto (o eliminado), marca del umbral en la barra y sello "Habilita acción" / "No concluyente — repetir captura".
8. Resumen arriba de la grilla (barras o chips por anomalía con conteo, clicables como filtro): "Estrés solar 153 · Clorosis 83 · Plaga foliar 65 · Daño fúngico 53 · No concluyente 60".
9. Deduplicar/evitar diagnósticos contradictorios en el mock y, en el real, mostrarlos como historial del sector.

**Baja**
10. Quitar el gradiente de las franjas del letterbox (fondo neutro oscuro); revisar el badge del sidebar (total → "nuevos desde tu última visita" o sin badge); `prefers-reduced-motion` en el hover.

---

## 5. Navegación entre pantallas (zona → sector → diagnóstico → historial)

| Desde \ Hacia | Zona | Sector | Diagnósticos | Historial | Motor de reglas |
|---|---|---|---|---|---|
| **Zona** | — | ✓ (celda, fila) | ✗ | ✗ | ✗ |
| **Sector** | ✓ (breadcrumb) | ✗ (sin ant./sig.) | ✗ (ni del sector) | ✗ (sólo 5 últimas, sin "ver todo") | ✓ "Ver última evaluación del motor" |
| **Diagnósticos** | ✗ | ✗ | — | ✗ | ✗ |
| **Historial** (fuera de alcance) | — | — | — | — | — |

- Diagnósticos es un **callejón sin salida**; el sector no sabe volver al origen (spec "Volver" incumplida); ni Historial ni Diagnósticos aceptan parámetros de URL, así que no se pueden armar enlaces contextuales (`features/historial`, `features/diagnostics` no usan `useSearchParams`; el backend de historial ya filtra por `sector`, `zona`, `tipo`, `desde`, `hasta`).
- Propuesta: un patrón único de **"enlaces contextuales"** en el encabezado de cada vista (zona: Diagnósticos de la zona · Historial de la zona · Nodo en Hardware; sector: Diagnósticos del sector · Historial del sector · Motor · Pedir captura; diagnóstico: Abrir sector) + soporte de query params en Historial y Diagnósticos.

---

## 6. Propuestas priorizadas (consolidadas, pensando en portarlas al sistema real)

### Alta
1. **No mostrar datos inventados en `http`:** quitar del modo real el histórico sintético (sparklines + inspector) hasta tener endpoint de series, y reemplazar el seguimiento de `buildSectorDetail` por el `evo` del último `HistorialEvento` del sector. (§1.1, M1-M2, S1-S4)
2. **Reconciliar el sector** (actuadores ↔ seguimiento ↔ historial) y mostrar las **dos causas** del estado (ambiente de zona + plantín). (S5, S19)
3. **Reordenar la página de sector**: diagnóstico y "qué está haciendo el sistema" arriba; dibujo explicativo colapsado. (§3.4)
4. **Inspector de métrica honesto**: ejes, banda óptima dibujada, escala que incluya la banda, colores por tramo, encabezado sin superposición, coherencia entre rangos. (M3-M7)
5. **"A revisar" agrupada por causa** y separación motivo/severidad; incluir sin señal. (M8-M11)
6. **Diagnósticos**: estado vacío, filtros completos en URL, orden por `ts` real, modal accesible con "Abrir sector"/"Pedir nueva captura", paginación o agrupación por sector. (D1-D9)
7. **Accesibilidad base**: `inert` en inspector cerrado, nombres accesibles de celdas y selects, foco visible, `role="dialog"` + Escape, `role="progressbar"`, estado no sólo por color, contraste de `--faint` y de números de celda. (§2.6, §3.6, §4.6)
8. **Responsive**: sidebar colapsable global; layouts apilados en mapa (< 1200 px), sector (< 860 px) y grilla de diagnósticos con `auto-fill`. (§1.5, §2.7, §3.7, §4.7)
9. **Leer umbrales y parámetros de la configuración** (`diagnostico.confianza-minima`, `seguimientoLatenciaMin`, `seguimientoDeltaMin`) en vez de 85 / "2 h" / 6. (§1.4)

### Media
10. Capas en la grilla de la zona (estado · diagnóstico · riego · insumos · antigüedad de captura) y tooltip rico accesible. (§2.8.4, §2.8.9)
11. Cabecera de diagnóstico de zona en una frase. (§2.8.1)
12. Bullet-chart actual vs. óptimo en las tarjetas de métrica; agrupar ambiente/nutrición; leyenda del `*`. (§2.8.7)
13. Enlaces contextuales entre vistas + query params en Historial y Diagnósticos + "Volver a origen". (§5, S21)
14. Historial del sector con lectura → decisión → acción, carga/error y "ver todo". (S6-S8)
15. Unificar nomenclatura de estados ("Sin señal" vs "Fuera de servicio"), renombrar subtítulos "Sector norte" de las macro-zonas, alinear taxonomía con el backend ("Daño biótico", "Ácaro"), "Variación (pp)" en vez de "Delta". (§1.2-1.3)
16. Tokens: reemplazar hex sueltos por `--*-ink/--*-soft`; definir `--surface-sunken` y `--border` o usar los existentes. (§1.7)

### Baja
17. Casos borde del dibujo (label del microaspersor, "Bandeja" activa, `aria-label` "plantines"), gramática de "a futuro", comentarios desfasados, `id` de gradiente único, mensaje al redirigir por zona inválida, sidebar badge con semántica de "nuevos".

---

## 7. Anexo — evidencias

- Capturas: `scratchpad/shots/` (previas) y `scratchpad/shotsB/` (nuevas).
- Sondas: `scratchpad/probeB/b.ts` (conteos, duplicados, series por rango, seguimiento), `probeB/h.ts` (historial vs. estado del sector), `probeB/a11y.mjs` y `a11y2.mjs` (árbol de accesibilidad, foco en inspector oculto), `probeB/shotsB.mjs`, `shotsC.mjs`.
- Contrastes WCAG medidos: `--faint`/blanco 2,64 · `--faint`/`--bg` 2,35 · `--muted`/blanco 4,69 · `--warn` texto/blanco 2,43 · `--ok` texto/blanco 3,26 · número de celda (blanco 82 %) sobre verde 2,7 / naranja 2,09 / gris 1,92 · "Sin señal" 1,85 · naranja vs verde entre celdas 1,34 · pista de barra 1,15 · azul microaspersor/`--bg` 3,09.
