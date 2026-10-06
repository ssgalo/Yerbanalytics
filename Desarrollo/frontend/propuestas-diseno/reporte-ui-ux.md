# Auditoría UI/UX — Dashboard Yerbanalytics

> Relevamiento hecho sobre la app en modo demo (`npm run dev:demo`), a 1440×900 y 390×844, más
> lectura del código de `Desarrollo/frontend/src/`. Contrastes según WCAG 2.1; daltonismo por
> simulación de protanopía y deuteranopía. Base de las 5 propuestas de esta carpeta.

## 1. Estado actual

### 1.1 Paleta (`src/styles/tokens.css`, espejada en `theme.ts`)

| Rol | Token | Hex | Notas |
|---|---|---|---|
| Fondo | `--bg` | `#F3F2EA` | Crema/papel cálido |
| Superficie | `--card` | `#FFFFFF` | Tarjetas |
| Texto | `--ink` | `#16241D` | Verde casi negro |
| Secundario | `--muted` | `#6A776E` | 4.69:1 sobre card; **4.18:1 sobre bg (no llega a AA)** |
| Terciario | `--faint` | `#97A299` | **2.64:1 sobre card**: no sirve para texto |
| Bordes | `--line` / `--line2` | `#E7E5D9` / `#F0EFE7` | |
| Marca | `--g900` `--g800` `--g700` `--brand` | `#0C2319` `#11352A` `#1A4D3B` `#1E6A4E` | |
| Saludable | `--ok` / soft / ink | `#3FA06A` / `#E7F1EA` / `#2E7A4F` | |
| En observación | `--warn` / soft / ink | `#E0972C` / `#FBF0DC` / `#8A5A10` | |
| Crítico | `--crit` / soft / ink | `#DD5238` / `#FBE6E0` / `#8F2B18` | |
| Fuera de servicio | `--off` / soft | `#A9B2AB` / `#EEF0EC` | |
| Info | `--info` / soft / ink | `#3B82F6` / `#E3EDFD` / `#1F4FA8` | Azul de Tailwind, frío frente al resto |
| Acento | `--orange` / soft | `#EC6A1E` / `#FBE7D7` | Ítem activo, "Ver todos →", banner de lluvia |
| Crema sobre oscuro | `--cream` / `--cdim` / `--cfaint` | `#ECF1E9`, al 62 % y al 40 % | |

Colores fuera de los tokens:
- **Severidad**: segundo juego de colores de texto, `#A66A12` y `#A8331C` (`data/mock/specs.ts:24-29`,
  `KpiRow.tsx:257,267`), distinto de `--warn-ink` y `--crit-ink`.
- **Acciones** (`specs.ts:202-223`): riego `#2A6E8C`/`#E2EEF3`, insumo `#5A4B9E`/`#EDEAF6`,
  mediasombra `#8A6A22`/`#F3ECDD`.
- **Miniaturas** de diagnósticos con gradientes radiales.
- **DAG y timeline**: el grafo del motor (`RuleGraph.tsx:33-88`) y el timeline del historial
  (`HistorialTimeline.module.css:239`, `#0A0F1A`, índigo `#A5B4FC`) usan slate/indigo de Tailwind.
- **Tokens inexistentes**: `--surface-sunken` y `--border` (`SectorHistory.module.css:100-102`,
  `EventoMeta.module.css:14`, `HistorialTimeline.tsx:146,151`): esos fondos y bordes no se dibujan.

### 1.2 Tipografía
- Space Grotesk (`--font-display`, 400–700) para títulos, números e IDs; Hanken Grotesk
  (`--font-body`, 400–800) para el cuerpo.
- **22 tamaños distintos** (9.5 a 34 px), ninguno tokenizado. Los más usados: 13 px (80 usos),
  11 (38), 12 (32), 12.5 (31), 11.5 (26). 26 usos por debajo de 11 px.
- Jerarquía: título de topbar 21/600 · título de tarjeta 16–17/600 · número de KPI 34/700 · ID de
  zona en el plano 21/800 · etiquetas 12.5/600 `--muted` · metadatos 10.5–11.5 `--faint`.

### 1.3 Espaciado, radios, sombras, densidad
- Sin escala de espaciado: paddings sueltos (18/19, 20/22, 14/15, 11/13). Gap entre tarjetas 16 px;
  padding del contenido `26px 32px 40px`.
- 17 radios distintos (dominan 10, 12, 16, 18). 15 sombras distintas de un solo uso, sobre un diseño
  casi plano con bordes de 1 px.
- 112 `style={{}}` inline, varios de layout (p. ej. `DashboardPage.tsx:29-37`).
- Densidad media-alta: el dashboard mide 2320 px de alto a 1440 de ancho; Diagnósticos **27 454 px**
  (414 tarjetas sin paginar).

### 1.4 Shell
- **Sidebar fija de 256 px** en `g900`: logo con gradiente `brand→#2C8A66` ("Yerbanalytics /
  MONITOREO IA"); grupo Principal (Panel general, Diagnósticos de IA con badge naranja del total,
  Demo Expo opcional); grupo Gestión (Historial, Configuración, Motor de reglas, Hardware,
  Topología). Ítem activo: naranja al 16 %, texto `#FCE3D0`, borde izquierdo de 3 px. Pie "Sistema
  en línea · sincronizado hace 40 segundos", fijo en código (`Sidebar.tsx:93-102`).
- **Topbar de 74 px** blanca al 85 % con blur: título y subtítulo; píldora de clima (21 °C · UV 7 ·
  Parcial nublado); campana con contador rojo y desplegable de 380 px; usuario "Mariano Duarte ·
  Productor Viverista", fijo en código (`Topbar.tsx:35-39`).
- **Sin ningún breakpoint.**

### 1.5 Componentes
- Átomos: `Card` (blanca, borde `--line`, radio 18), `Badge` (pill 10.5/700, fondo soft + texto ink),
  `StatusDot` (con pulso), `ProgressBar` (7 px), `Sparkline` (SVG sin ejes), `Icon` (27 íconos de
  trazo 1.8, estilo Lucide), `Glyph`.
- Celdas de estado de 10–22 px (heatmap), parcelas con borde punteado, badges Alta/Media/Baja.
- DAG con `@xyflow/react` sobre lienzo oscuro. Formularios con `select` nativos, inputs numéricos,
  toggle y barra Descartar/Guardar.

### 1.6 Contenido por vista
- **Panel general (`/`)**: KPIs (saludables 179/600 con barra "30 % en parámetros óptimos"; en alerta
  411 con "206 observación · 205 crítico"; "Hardware fuera de servicio" 10; "Acciones autónomas hoy"
  41 en tarjeta verde oscura: 28 riego · 7 insumo · 6 sombra). Plano del vivero: 6 parcelas con borde
  del peor estado y "N sectores necesitan atención". Estado del vivero: 6 bloques por zona (badge,
  nodo batería · dBm, resumen, contadores, heatmap 10×10, "Ver detalle →"). Columna derecha de 360 px:
  "Atención prioritaria" (7 sectores con contador 411) y "Clima y riesgo" (21 °C, UV 7 Alto, banner de
  lluvia, 4 franjas horarias). Abajo: "Actividad del sistema" (timeline) y "Diagnósticos recientes".
- **Macro-zona (`/mapa?zona=`)**: grilla de 100 sectores con resumen arriba; 10 tiles de valores
  sensados (etiqueta, valor coloreado, unidad, rango ideal, sparkline; `*` = rango provisional) con
  batería, señal y antigüedad; lista "Sectores a revisar"; inspector de métrica con gráfico de área
  y selector 24h/7d/30d.
- **Sector (`/sector/:id`)**: migas + escalera Vivero → Macro-zona → Sector → Bandeja → Tubete;
  encabezado con ID, estado, motivo y "Ver última evaluación del motor"; diagrama SVG (riel con
  cámara, 4 bandejas de 25 tubetes, microaspersor, nodo testigo); diagnóstico de IA (foto 150 px,
  barra de confianza, umbral 85 %); actuadores (electroválvula, bomba, mediasombra) y seguimiento
  post-acción (Antes → Ahora, delta, veredicto).
- **Diagnósticos**: 2 selects, contador, grilla de 4 columnas con 414 tarjetas; modal con foto.
- **Historial**: 6 filtros y acordeón Ciclo → Macro-zona → Sector → DAG, todo colapsado.
- **Configuración**: tabla de umbrales 10 métricas × 6 inputs, límite de insumo, plan de
  rustificación, seguimiento post-acción, frecuencias, toggle Demo Expo, "Restablecer / Guardar".
- **Motor de reglas**: Parámetros (buscador, filtro por rama, "Sólo modificados", tarjetas por regla
  con emoji, R-0x, clase Java, prioridad, chips) e Inspector (MZ/sector/origen, auto-refresh 5 s,
  "Qué pasó" y DAG oscuro con nodos Pasó/Bloqueó/Omitida).
- **Hardware**: 5 KPIs, filtros, "Registrar dispositivo", "Sectores con mapeo incompleto" y tabla
  (dispositivo, MAC, ubicación, batería, dBm, último update, estado, "Recambiar").
- **Topología**: 4 inputs, total, vista previa.
- **Demo Expo**: titular en vivo, badge "En curso", Cancelar/Iniciar, barra de progreso y 5 pasos.
  Es la vista más clara y operativa del sistema.

## 2. Hallazgos priorizados

### Alta
- **A1. En un teléfono la app no se puede usar.** Sidebar fija de 256 px, grilla `'1fr 360px'`
  inline (`DashboardPage.tsx:32,57`), KPIs `repeat(4,1fr)`, columnas mínimas de 430 y 300 px en la
  vista de zona. A 390 px todas las vistas desbordan (scrollWidth 627–704 px). → Sidebar a íconos
  <1024 px, navegación inferior <768 px, grillas `auto-fit/minmax`, vista "Mi recorrido" móvil.
- **A2. Un solo error de red deja la app muerta** (`hooks/NurseryContext.tsx:26,38-44`): una falla
  del poll reemplaza toda la app por un texto rojo y no se limpia nunca. → Conservar el último
  snapshot, banner no bloqueante, limpiar al primer poll exitoso.
- **A3. Sin 404 ni "no encontrado"**: no hay `errorElement` ni ruta `*`; sector inexistente =
  pantalla en blanco (`SectorPage.tsx:60`).
- **A4. Color y números de estado engañan**: el KPI "Hardware fuera de servicio" cuenta sectores,
  no nodos (`KpiRow.tsx:279-288`, `generators.ts:381`); el plano pinta cada parcela por su peor
  estado (MZ-1 con 90 % sanos se ve tan roja como MZ-4); "Ahora" del post-acción siempre verde
  (`PostActionCard.tsx:33-36`); el mismo estado se llama "Fuera de servicio", "Sin señal" y
  "Sin datos".
- **A5. Datos escritos en el código que simulan estado real**: "Sistema en línea" y el usuario.
- **A6. Contraste y estados sólo por color**: `--faint` 2.64:1, `--muted` sobre bg 4.18:1, cifras
  KPI en `--warn` 2.43:1, blanco sobre ámbar ~2.4:1, naranja 3.16:1, chip de batería 2.15:1.
  Celdas de 10–22 px codificadas sólo por color (protanopía: saludable/crítico ΔE≈17). 26 textos
  de 9.5–10.5 px. → Variantes `-ink`, `--faint` ≈`#6F7A72`, glifo/patrón por estado, mínimo 11–12 px.
- **A7. Modales y desplegables sin accesibilidad**: sin `role="dialog"`, Esc, trampa de foco ni
  `aria-expanded`; labels sin asociar; foco visible en sólo 2 componentes.

### Media
- **M1. Panel general repetido y todo en rojo**: Plano y Estado del vivero repiten info; "Atención
  prioritaria" muestra 7 de 411 sin "ver todos". → Fusionar en un mapa con mini heatmap, cola de
  trabajo agrupada por causa, tendencia 24 h en KPIs.
- **M2. Diagnósticos no escala**: 414 tarjetas sin paginar, filtros incompletos, todo "hace 16 min",
  el modal no lleva al sector ni permite validar. → Paginación, "Ir al sector", "Confirmar / Corregir".
- **M3. Historial sin información visible**: filas iguales, plural mal, `mm/dd/yyyy`, niveles en
  inglés.
- **M4. Gráficos de sensado engañan**: sin bandas ni escala; 37–42 % parece un desplome.
- **M5. Sistema visual inconsistente**: 22 tamaños, 17 radios, 15 sombras, 2 juegos de severidad,
  DAG en slate de Tailwind, emojis junto a íconos de trazo.
- **M6. Navegación**: sin buscador "Ir a sector", Configuración y Motor de reglas se pisan, badge
  de 414 permanente.
- **M7. Alertas sin destino**: sin link, sin reconocer.
- **M8. En el sector, lo importante queda abajo**: el diagrama genérico ocupa ~650 px y el
  diagnóstico empieza a ~950 px.
- **M9. Configuración es una pared de 60 inputs**: sin representación visual de bandas; guardar al
  final.

### Baja
- B1 sin dark mode · B2 sin skeletons ni toasts · B3 `ybPulse` no respeta `prefers-reduced-motion` ·
  B4 "UV" del clima vs. luminosidad del LDR se confunden · B5 código muerto (`PlaceholderPage.tsx`) ·
  B6 tabla de Hardware mezcla nodos y actuadores.

## 3. Oportunidades de diseño

Principio: primero "qué hago ahora y dónde", después "por qué" (motor y DAG). Uso operativo, al sol
y con las manos ocupadas.

1. **"Tierra colorada"**: papel de bolsa de yerba `#F4EFE6`, superficie `#FFFDF8`, tinta `#1B2A20`;
   verde yerba `#2F5D3A`/`#1E3D27`; acento terracota `#B5452A`/`#C8643B`, separando el rojo de
   alarma (carmín frío `#C0263B` con glifo, o acento ocre `#A68A3A`). Trama de tubetes y hoja
   aserrada de *Ilex paraguariensis*. Display con carácter + mono tabular para IDs y lecturas.
2. **"Cuaderno de campo / plano técnico"**: vivero como plano de agrimensor con el riel encima;
   diagnósticos como fichas de herbario.
3. **"Modo campo / alto contraste"**: fondo blanco, estados sólidos con glifo, mínimo 14 px,
   objetivos de 48 px. Complementa un dark mode nocturno.
4. **Patrones**: cola de trabajo del día por causa; heatmap con segundo canal y zoom semántico;
   métricas como termómetro de 5 bandas; línea causal lectura → regla → acción → efecto; antigüedad
   del dato en todo el sistema; validación humana de la IA con contador de pendientes.
