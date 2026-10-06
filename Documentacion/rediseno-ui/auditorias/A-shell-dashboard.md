# Auditoría UI/UX — A. Shell de la app + Panel general

> Alcance: `src/components/layout/*`, `src/components/ui/*`, `src/styles/*`, `src/hooks/PageMeta.tsx`
> (+ `NurseryContext.tsx`, que es parte del shell aunque viva en hooks) y la pantalla **Panel general**
> (`/`, `src/features/dashboard/**`).
> Todas las rutas `src/...` son relativas a `Desarrollo/frontend/`. Las del backend, a `Desarrollo/backend/`.
>
> **Método.** Lectura de código completa del alcance; volcado de los datos reales del mock con la
> semilla de `.env.demo` (`VITE_MOCK_SEED=20260613`) ejecutando `buildNursery()` con `vite-node`;
> capturas a 390 / 1024 / 1280 / 1440 / 1920 px con Playwright; recorrido con teclado; cálculo de
> contraste WCAG 2.x sobre los valores de `tokens.css` y simulación de daltonismo (Machado 2009).
> Contraste cruzado con la pantalla Hardware y con `NurseryService.java` (lo que el sistema real va
> a mandar), porque las propuestas se van a portar.
>
> Capturas de apoyo nuevas en `scratchpad/shotsA/` (`mob-viewport.png`, `mob-alerts.png`,
> `w1024-estado.png`, `w1280-estado.png`, `w1920-viewport.png`, `focus-*.png`, `click-parcela-mz4.png`).

---

## 0. Resumen ejecutivo

El Panel general **no responde bien a la pregunta que tiene que responder** ("¿hay algo que requiera
que yo intervenga ahora, dónde y por qué?"). Tres causas de fondo:

1. **Los números se contradicen entre sí y con otras pantallas.** El KPI "Hardware fuera de servicio
   **10** · Nodos testigo sin reportar" es imposible (hay 6 nodos, y los 6 reportan); la pantalla
   Hardware dice **2** fuera de servicio, y los chips de nodo del propio panel dicen que los 6 están
   bien. La campana, la tarjeta de prioridad y el plano cuentan historias distintas del mismo sector
   (MZ-2-070 es "Plaga foliar · Alta" en una tarjeta y "Clorosis detectada (confianza 88%)" en la
   alerta, en la misma pantalla).
2. **La información está duplicada y la causa está ausente.** El vivero se dibuja dos veces (Plano +
   Estado del vivero, mismas 6 zonas, misma frase), pero **la causa real del problema no aparece en
   ningún lado**: MZ-4 tiene 98 sectores críticos por humedad de sustrato 30 % y MZ-5 por sustrato
   saturado 82 %; ninguna de esas dos lecturas se muestra en el Panel, y "Atención prioritaria" (que
   lista 7 de 411, ordenados alfabéticamente) ni siquiera menciona MZ-4 ni MZ-5.
3. **Todo es rojo, todo pide atención, nada se puede accionar.** Con la regla "peor estado manda",
   las 6 parcelas y los 6 bloques son rojos (incluso MZ-1 y MZ-6, con 89–90 % sanos). Las alertas no
   se pueden abrir ni marcar como atendidas; los KPIs no llevan a ningún lado; la actividad no lleva
   al historial.

A eso se suman: shell no responsive (a 390 px el contenido queda en **134 px** y la página scrollea
en horizontal hasta 640 px), **layout roto a 1024 px** (las grillas de sectores se superponen),
estados hardcodeados que mienten ("Sistema en línea · hace 40 segundos", "Mariano Duarte"), y una
paleta de estado con contraste insuficiente (ámbar `--warn` 2,43:1 sobre blanco; gris `--off`
2,18:1; `--faint` 2,64:1) que además depende sólo del color (verde vs. rojo con deuteranopía:
ΔE 31, ambos quedan caqui).

---

# PARTE 1 — SHELL

## 1.1 Propósito y tareas del usuario

El shell es lo único que el usuario ve en **todas** las pantallas. Tiene que contestar, sin que
navegue:

- **¿Dónde estoy?** (título de vista, ítem activo del menú, migas).
- **¿El sistema está vivo y los datos son frescos?** Es crítico en un sistema que actúa solo:
  si el backend o los nodos se cayeron, el productor tiene que saberlo antes de confiar en un número.
- **¿Hay algo urgente?** (campana de alertas, con acceso directo a lo afectado).
- **¿Cómo llego a X?** (navegación a vistas operativas y de administración).
- **¿Qué tiempo hace / va a hacer?** (relevante porque el motor pospone riegos por lluvia).
- **¿Quién soy / salir?**

Usuarios: productor/a viverista (uso frecuente, probablemente desde el celular o una tablet **en el
vivero, al sol**) y personal técnico (desktop, configuración y hardware).

## 1.2 Inventario de información del shell (no perder nada en el rediseño)

### Sidebar (`src/components/layout/Sidebar.tsx`)

| Elemento | Contenido actual (demo) | Origen |
|---|---|---|
| Logo | Ícono `leaf` blanco sobre gradiente verde | hardcode, `Sidebar.tsx:58-60` |
| Marca | "Yerbanalytics" + "MONITOREO IA" (uppercase CSS) | hardcode, `:62-63` |
| Sección | "PRINCIPAL" | `:67` |
| Ítem | "Panel general" → `/` (activo con `end`) | `:21` |
| Ítem | "Diagnósticos de IA" → `/diagnosticos` + **badge "414"** (`stats.diagCount`) | `:22`, `:45` |
| Ítem condicional | "Demo Expo" → `/demo-expo` (sólo si el interruptor de Configuración está encendido; va justo después de Diagnósticos) | `:26`, `:53` |
| Sección | "GESTIÓN" | `:74` |
| Ítems | "Historial" `/historial` · "Configuración" `/configuracion` · "Motor de reglas" `/reglas` · "Hardware" `/hardware` · "Topología" `/topologia` | `:28-34` |
| No está en el menú (a propósito) | Detalle de macro-zona `/mapa?zona=` y detalle de sector `/sector/:id` | comentario `:17-19` |
| Tarjeta de estado | Punto verde + "Sistema en línea" / "Edge activo · sincronizado" / "hace 40 segundos" | **hardcode**, `:81-91` |
| Estado activo | Fondo naranja 16 %, texto `#fce3d0`, borde izquierdo naranja 3 px | `Sidebar.module.css:93-97` |

### Topbar (`src/components/layout/Topbar.tsx`)

| Elemento | Contenido (demo) | Origen |
|---|---|---|
| Título `<h1>` | "Panel general" | `usePageTitle` de cada vista |
| Subtítulo | "Vivero San Ignacio · Misiones, AR" | hardcode en `DashboardPage.tsx:15` |
| Chip clima | Ícono sol (siempre, color `--warn`) · "21°C" · "UV 7 · Parcial nublado" | `weather.tempC/uv/cond`, `Topbar.tsx:19-27` |
| Campana | Ícono + badge rojo con no leídas ("5") | `AlertsDropdown` |
| Separador vertical | — | `:31` |
| Usuario | "Mariano Duarte" / "Productor Viverista" + avatar "MD" | **hardcode**, `:33-39` |

### Panel de alertas (`src/components/layout/AlertsDropdown.tsx`)

Encabezado "Alertas activas" + "5 sin atender". Cada alerta: punto de color, **nivel en inglés**,
hora absoluta, id de sector, mensaje. Valores reales del mock (`generators.ts:484-521`):

| Nivel | Hora | Sector | Mensaje |
|---|---|---|---|
| CRITICAL | 14:08 | MZ-1-010 | Daño fúngico confirmado + humedad de sustrato 84%. Dosificación de fungicida en curso. |
| CRITICAL | 13:41 | MZ-5-042 | Falla hidráulica: caudalímetro sin flujo tras abrir electroválvula. Sector marcado para revisión. |
| WARNING | 13:20 | MZ-2-091 | Nodo testigo con batería baja (18%). Recambio preventivo sugerido. |
| WARNING | 12:55 | MZ-2-070 | Clorosis detectada (confianza 88%). A la espera de validación de dosis nutricional. |
| WARNING | 11:30 | MZ-4-005 | Sensor sin reporte hace 2 h — señal intermitente. Mostrando último dato conocido. |

Campos del tipo `Alert` (`domain.ts:188-195`): `level`, `color`, `time`, `sectorId`, `msg`, `read`.
`read` existe pero nada lo cambia. **Acciones disponibles hoy: ninguna** (sólo abrir/cerrar).

### Contenedor y estados globales

- `AppLayout.tsx:9-22`: sidebar + columna (topbar + `<main>` scrolleable, padding 26/32/40).
- `NurseryContext.tsx:38-52`: **carga** = texto plano "Cargando vivero…" a pantalla completa, sin
  shell; **error** = texto rojo "No se pudo cargar el vivero: {mensaje}" a pantalla completa.
  Polling cada **5 s** (`:30`).
- `PageMeta.tsx`: título/subtítulo de la topbar. Valor inicial "Panel general" (`:22`).
- `index.html`: `<title>` fijo "Yerbanalytics · Monitoreo IA", `lang="es"`, fuentes de Google.

### Átomos UI (`src/components/ui/`)

`Card` (superficie blanca radio 18), `Badge` (recibe colores crudos `soft`/`ink`), `StatusDot`
(punto, acepta animación CSS como string), `ProgressBar` (div sin semántica), `Sparkline` (SVG),
`Icon` (registro de 26 íconos, `aria-hidden`) y `Glyph` (path dinámico que **llega desde el DTO**).

## 1.3 Bugs e inconsistencias del shell

| # | Problema | Dónde |
|---|---|---|
| S1 | **"Sistema en línea · Edge activo · sincronizado · hace 40 segundos" es un literal.** Siempre verde, siempre "40 segundos", aunque el backend esté caído, el broker no publique o los 6 nodos estén mudos. En un sistema que actúa solo, es el dato más peligroso de mentir. | `Sidebar.tsx:81-91`; color `#4fd787` hardcodeado `Sidebar.module.css:133` |
| S2 | **Usuario hardcodeado** "Mariano Duarte / Productor Viverista / MD". No hay sesión, menú, ni salir. En otra parte del mock el "usuario" que guarda configuración es "Ingeniero Agrónomo" (`mockRepository.ts`, `saveConfig`). "Viverista" no va con mayúscula. | `Topbar.tsx:35-38` |
| S3 | **Un error transitorio tumba la app entera y para siempre.** Si *un* poll de los que corren cada 5 s falla (modo `http`), `setError` reemplaza toda la UI —incluidas Configuración, Hardware y Reglas, que no dependen del vivero— por un texto rojo, y como `error` nunca se limpia, la app no se recupera aunque los polls siguientes anden. | `NurseryContext.tsx:26`, `:38-44` |
| S4 | **La carga bloquea toda la app** (no hay shell, ni skeleton): `NurseryProvider` envuelve al router (`App.tsx:7-9`), así que hasta `/configuracion` espera al snapshot del vivero. | `App.tsx`, `NurseryContext.tsx:46-52` |
| S5 | **Badge de la campana muestra "0" en rojo** cuando no hay alertas (se renderiza siempre). Pasa de verdad: con una topología recién generada `alerts = []`. Además, con lista vacía el panel queda en blanco, sin mensaje. | `AlertsDropdown.tsx:28`, `:37-54` |
| S6 | **Niveles en inglés** ("CRITICAL", "WARNING") en una UI en español; el backend también los manda así (`NurseryService.java:448`). | `AlertsDropdown.tsx:44` |
| S7 | **Hora absoluta ("14:08") en la campana vs. "hace X min" en todo el resto.** En el sistema real llega el literal **"ahora"** para todas (`NurseryService.java:451`). | `generators.ts:488-517` |
| S8 | **Las alertas no son de alertas:** en el backend real se derivan de los primeros 5 ítems de "Atención prioritaria" (`buildAlertsFromHistorial(priority)`, `NurseryService.java:443-452`). La campana es una copia de la tarjeta de prioridad con otro formato, y `read` siempre es `false` ⇒ el badge nunca baja. | backend |
| S9 | **La alerta de batería baja se ata a un *sector*** ("MZ-2-091 · Nodo testigo con batería baja (18%)"), pero el nodo testigo es de la *macro-zona*. Y el porcentaje no coincide con nada: el chip del nodo de MZ-2 en el Panel dice **30 %**, la pantalla Hardware dice **16 %**. | `generators.ts:503-504`, `hardware.ts:83` |
| S10 | **"MZ-4-005 · Sensor sin reporte hace 2 h — señal intermitente"**: en el Panel el nodo de MZ-4 reportó "hace 17 min" (chip verde, 49 %); en Hardware el de MZ-4 está **Fuera de servicio, hace 1 d**, y el intermitente es **MZ-3**. Tres versiones del mismo hecho. | `generators.ts:517-518`, `hardware.ts:84-85` |
| S11 | **Ninguna vista de detalle marca un ítem activo**: en `/mapa?zona=MZ-2` y `/sector/:id` el menú queda sin selección (Panel general usa `end`). El usuario pierde la referencia de dónde está. | `Sidebar.tsx:21`, ver `desk-mapa.png` |
| S12 | **El diseño de referencia tenía "Mapa de producción" en Principal** (`Yerbanalytics.dc.html:46-48`); se quitó, y la única puerta a las zonas es scrollear el Panel. | `Sidebar.tsx:17-23` |
| S13 | **Badge "414" de Diagnósticos de IA sin significado declarado** (¿pendientes? ¿nuevos? ¿totales?). Es el total de diagnósticos no-sanos *incluyendo 3 duplicados "No concluyente"*; en color naranja de "notificación", sugiere 414 cosas sin ver. | `Sidebar.tsx:45`, `generators.ts:436-456` |
| S14 | **El clima de la topbar siempre lleva un sol en ámbar**, también con "Parcial nublado", de noche o con lluvia. Con la API caída el backend manda `tempC 0.0, cond "Sin datos", uv 0` y la topbar muestra **"0°C · UV 0 · Sin datos"** como si fuera un dato. | `Topbar.tsx:20`, `NurseryService.java:402-408` |
| S15 | **"UV" ambiguo**: el chip muestra el índice UV del pronóstico, pero en el dominio la clave `uv` del nodo es **% de luz de un LDR** (CLAUDE.md §2). El feed de actividad mezcla ambos ("Pico de radiación UV 9 detectado", `specs.ts:281`). | `Topbar.tsx:24` |
| S16 | **`document.title` no cambia por ruta** (siempre "Yerbanalytics · Monitoreo IA"): pestañas, historial del navegador y lectores de pantalla no distinguen vistas. Además el título inicial de la topbar es "Panel general" en *cualquier* ruta hasta que corre el efecto de la vista (parpadeo en deep-links). | `index.html:11`, `PageMeta.tsx:22` |
| S17 | **`theme.ts` es un espejo desactualizado de `tokens.css`** (le faltan `--off-soft`, `--info*`, `--*-ink`, `--on-brand`). Dos fuentes de verdad de color, más los hex sueltos de `specs.ts` (`C`, `sevMap`, `resMap`, `ACT`) y de `selectors.ts:128-134`. | `styles/theme.ts` |
| S18 | **Comentario falso**: "no hay token --cdim disponible" — sí existe (`tokens.css:54`), y el valor inline usado (0,55) difiere del token (0,62). | `KpiRow.module.css:32-33` |
| S19 | `var(--surface-sunken)` no existe en tokens (cae al fallback). | `ActivityFeed.module.css:97` |
| S20 | **Presentación dentro del contrato de datos**: el DTO trae `color`, `sevSoft/sevInk`, `tint/ink`, **paths SVG** (`ActionEvent.path`) y hasta **la animación CSS** (`PriorityItem.pulse = 'ybPulse 2s infinite'`), y el backend real los manda igual (`NurseryService.java:338`). Cualquier rediseño de color obliga a tocar el backend. | `domain.ts:142-185`, `generators.ts:411` |

## 1.4 Problemas de UX del shell

- **Arquitectura de navegación.** "Gestión" mezcla lo operativo diario (Historial) con lo de
  administración de baja frecuencia (Configuración, Motor de reglas, Hardware, Topología). El
  productor no necesita Topología ni Motor de reglas en el primer nivel; el técnico sí. Falta un
  lugar para **Macro-zonas** (hoy sólo se llega scrolleando el Panel) y para **Alertas** (no hay
  página; la campana es el único acceso y no deja nada rastreable).
- **No hay migas en el shell**: el detalle de zona agrega su propio "‹ Panel general" (`MapPage`),
  el sector otro; el shell no lo provee de forma uniforme.
- **La campana no lleva a ningún lado**: cada alerta es un `<div>` (`AlertsDropdown.tsx:39`), sin
  link al sector/zona/dispositivo, sin "marcar como atendida", sin "ver todas" (→ Historial filtrado
  por `Alerta`, que sí existe en `/historial`). "5 sin atender" promete una acción que no existe.
- **Sin feedback de frescura**: no hay "Actualizado hace X s" real ni indicación de que el polling
  falló. Combinado con S1 y S3, el usuario no puede distinguir "todo bien" de "estoy mirando datos
  viejos".
- **Clima duplicado**: chip en topbar + tarjeta "Clima y riesgo" en el Panel, con los mismos datos
  (21 °C, UV 7, Parcial nublado). El chip no es clickeable.
- **Usuario sin menú**: el bloque de usuario parece interactivo (avatar) pero no hace nada.
- **Demo Expo** aparece/desaparece del menú según un interruptor de Configuración; correcto como
  feature, pero no hay indicación de que el sistema está "en modo expo" mientras corre una pasada.

## 1.5 Problemas visuales del shell

- **El naranja de marca compite con la semántica de estado.** El ítem activo, el badge de
  Diagnósticos, "Ver todos →" y el banner de lluvia usan `--orange #EC6A1E`, que contra `--warn
  #E0972C` tiene un contraste de **1,30:1** (son prácticamente el mismo color). El ojo lee "ítem
  activo = advertencia" y "414 = algo en warning".
- **Escala tipográfica sin sistema**: en el alcance conviven 10 / 10,5 / 11 / 11,5 / 12 / 12,5 /
  13 / 13,5 / 14 / 15 / 16 / 17 / 21 / 24 / 34 px. Tarjetas hermanas usan título 16 px (Prioridad,
  Clima, Diagnósticos) y 17 px (Plano, Estado, Actividad).
- **Radios y paddings sin sistema**: 8, 9, 10, 11, 12, 13, 14, 16, 18 px; tarjetas con padding
  18/19 (KPIs, Prioridad, Clima, Diagnósticos) y 20/22 (Plano, Estado, Actividad).
- **El átomo `Card` se usa sólo en Plano y Estado**; KpiRow, PriorityCard, WeatherCard, ActivityFeed
  y RecentDiagnostics reimplementan `.card` en su CSS (radio 16 vs 18).
- **Íconos duplicados fuera del registro**: el sol de `WeatherCard.tsx:18-30` y el triángulo de
  `:51-64` son SVG inline distintos de `Icon name="sun"`/`"alert"`; la hojita de
  `RecentDiagnostics.tsx:41-53` duplica `leaf-simple`.
- Microdetalles: el texto "MONITOREO IA" a 10,5 px con tracking 0,14 em en `--cfaint` es casi
  ilegible; el `lineHeight` del bloque de marca y del usuario se setean inline.

## 1.6 Accesibilidad del shell

- **Sin "Saltar al contenido"**: con teclado hay 7 links de menú + campana antes del contenido.
- **Campana**: sin `aria-expanded`, `aria-haspopup`, `aria-controls`; el nombre accesible es
  "Alertas" sin la cantidad; no cierra con **Escape** (sólo `mousedown` afuera, `:13-22`); al abrir
  no se mueve el foco; el panel no tiene rol (`dialog`/`region`) ni título asociado; los ítems no son
  focuseables.
- **Foco visible**: no hay estilo global `:focus-visible`; se depende del default del navegador
  (en el sidebar oscuro el anillo blanco por defecto se ve — `focus-sidebar.png` — pero en botones
  "transparentes" sobre `--bg` el anillo negro por defecto es el único indicio).
- **Contraste** (detalle en §3): secciones "PRINCIPAL/GESTIÓN" `--cfaint` 3,41:1 a 10 px (falla);
  badge de la campana blanco sobre `--crit` 3,93:1 a 10,5 px (falla); metadatos de alerta en
  `--faint` 2,64:1 (falla); "UV 7 · Parcial nublado" `--muted` 10,5 px sobre topbar translúcida
  ~4,6:1 (límite).
- **Movimiento**: `ybPulse` (infinito), `ybFade` y `flashPulse` no respetan
  `prefers-reduced-motion` (`global.css:56-84`).
- **`ProgressBar`** no expone `role="progressbar"`/`aria-valuenow`; `StatusDot` es puramente visual
  (correcto si siempre va con texto, pero en Prioridad el estado *sólo* está en el punto).
- **Positivo**: `lang="es"`, `NavLink` agrega `aria-current="page"`, `Icon` es `aria-hidden`, la
  topbar usa `<h1>` y las tarjetas `<h2>`.

## 1.7 Responsive del shell

Medido a **390 × 844** (`shotsA/mob-viewport.png`):

- El sidebar mantiene **256 px** fijos (`Sidebar.module.css:2`), `<main>` queda en **134 px**.
- El documento scrollea en horizontal hasta **640 px** (`scrollWidth`); dentro de `<main>` el
  contenido mide **682 px** (la grilla `1fr 360px` no cede).
- La topbar mantiene 74 px de alto con contenido de 384 px en 134 px: el `<h1>` "Panel general" se
  parte en dos líneas y **queda cortado por arriba**; el usuario se parte en "Mariano / Duarte /
  Productor / Viverista".
- El panel de alertas mide 380 px fijos anclado a la derecha de la campana: **se sale por la
  izquierda de la pantalla**, el título "Alertas activas" y los niveles quedan fuera de vista
  (`shotsA/mob-alerts.png`).
- `#root { height: 100vh }` (`global.css:26-29`) en iOS Safari deja contenido detrás de la barra del
  navegador; corresponde `100dvh`.

**Comportamiento propuesto**

| Ancho | Shell |
|---|---|
| ≥ 1280 | Sidebar expandido 240 px; topbar completa. |
| 1024–1279 | Sidebar **colapsado a íconos** (64–72 px, con tooltip y etiqueta en hover/foco). |
| < 1024 | Sidebar como **drawer** (botón hamburguesa en la topbar, `inert` al cerrar, Escape cierra, foco atrapado). Opcional en ≤ 600: **tab bar inferior** con 4 destinos (Panel, Zonas, Diagnósticos, Alertas) + "Más". |
| < 600 | Topbar de 56 px: título 1 línea con ellipsis, campana; clima y usuario se mueven al drawer/“Más”. Panel de alertas como **sheet a pantalla completa** (no dropdown). |

---

# PARTE 2 — PANEL GENERAL (`/`)

## 2.1 Propósito y tareas del usuario

Es la pantalla de llegada, varias veces por día. La decisión que se toma acá es de **triage**:

1. **¿Tengo que intervenir ahora?** — algo que el sistema *no* puede resolver solo: sensor caído,
   falla hidráulica, diagnóstico de IA no concluyente o de severidad alta que pide validación, límite
   químico alcanzado, riego abortado por seguridad.
2. **¿Dónde?** — qué macro-zona, cuántos sectores, y si el problema es **ambiental** (de la zona: lo
   ve el nodo testigo y afecta a los 100 sectores) o **del plantín** (de un sector: lo ve la IA).
   Esta distinción es *el* concepto del dominio (CLAUDE.md §2) y hoy el Panel no la hace visible.
3. **¿Qué está haciendo el sistema solo?** — riegos en curso/en cola, dosificaciones, riegos
   pospuestos por lluvia; para confiar en la autonomía o frenarla.
4. **¿Puedo confiar en los datos?** — frescura de cada nodo, batería, señal.
5. **¿El clima cambia algo en las próximas horas?** — lluvia que pospone riego, UV/radiación que
   mueve la mediasombra.

Lo que debería verse **primero** (arriba, sin scroll en desktop y en la primera pantalla del
celular): un resumen de una línea del estado + la lista corta de **incidencias accionables agrupadas
por causa**, y el estado de las 6 macro-zonas con su causa. El resto (feed, diagnósticos recientes,
pronóstico detallado, grilla de 600 celdas) es secundario.

## 2.2 Inventario de información (valores reales del mock, semilla 20260613)

### Totales que alimentan la vista (`stats`)

`total 600 · sano 179 · warning 206 · critical 205 · offline 10 · alerta 411 · sanoPct 30 ·
actToday 41 · actRiego 28 · actInsumo 7 · actSombra 6 · diagCount 414`.
Diagnósticos: Estrés solar 153 · Clorosis 83 · Plaga foliar 65 · Daño fúngico 53 · No concluyente
60 (57 de sectores + 3 duplicados) · Sano 179 · Sin diagnóstico 10.

### 2.2.1 Fila de KPIs (`KpiRow.tsx`)

| KPI | Valor | Secundario | Color |
|---|---|---|---|
| Sectores saludables | **179** / 600 | barra 30 % · "30% del vivero en parámetros óptimos" | `--ok` |
| Sectores en alerta | **411** | chips "206 observación" (ámbar) · "205 crítico" (rojo) | `--warn` |
| Hardware fuera de servicio | **10** | "Nodos testigo sin reportar" | `--off` (gris) |
| Acciones autónomas hoy | **41** | "28 riego · 7 insumo · 6 sombra" | tarjeta verde oscura |

Ninguno es clickeable.

### 2.2.2 Plano del vivero (`PlanoVivero.tsx` + `ComoLeer.tsx`)

- Título "Plano del vivero"; resumen con punto rojo: **"411 de 600 sectores necesitan atención."**
  (`resumenVivero.ts:44-64`; otras variantes: "El vivero está saludable: los N sectores están bien."
  y "· N zonas sin datos del sensor").
- Ayuda: "Cada parcela de abajo es una macro-zona del vivero. Tocá una para ir a su detalle."
- 6 parcelas-botón (168 px fijos), borde punteado del color del peor estado, cada una con: id
  ("MZ-1"), ícono + "sensor testigo", y frase de `resumenZona` ("9 sectores necesitan atención" /
  "Todo bien" / "Sin datos del sensor"). Tocar ⇒ **scroll + destello** al bloque de esa zona (no
  navega).
- Tira "Cómo leer esta pantalla: Vivero → Macro-zona (área con 1 sensor testigo) → Sector (4
  bandejas) → Bandeja (25 plantines)".

### 2.2.3 Estado del vivero (`EstadoVivero.tsx` + `ZonaBlock.tsx` + `NodoChip.tsx`)

- Título "Estado del vivero"; subtítulo "600 sectores · 6 macro-zonas · cada celda es 1 sector
  (~100 plantines)"; leyenda Saludable / En observación / Crítico / **Sin señal**.
- Un bloque por zona, `layout.macroZonasPorFila = 3` columnas, cada uno con: badge de id coloreado,
  "1 sensor testigo · 100 sectores · 10000 plantines", chip del nodo ("67% · -60 dBm", tooltip "Nodo
  testigo de la macro-zona (habla por todos sus sectores)"; ámbar si batería < 20; gris "Sin datos"
  si stale), frase de resumen coloreada, contadores en palabras, aviso "Nodo testigo sin datos — la
  lectura de esta macro-zona no es vigente" (sólo stale), grilla `sectoresPorFila = 10` de 100
  celdas-botón (tooltip nativo "MZ-4-006 · Crítico", `aria-label` "Sector MZ-4-006: Crítico", clic ⇒
  `/sector/:id`), link "Ver detalle de la zona →" (⇒ `/mapa?zona=`).

| Zona | Color | Frase | Contadores | Chip nodo | **Lectura del nodo (NO se muestra)** | **Actuadores (NO se muestra)** |
|---|---|---|---|---|---|---|
| MZ-1 | rojo | 9 sectores necesitan atención | 89 saludables · 7 en obs. · 2 críticos · 2 sin señal | 67 % · -60 dBm | hace 25 min · todo en rango | 100 válvulas cerradas · 2 bombas dosificando |
| MZ-2 | rojo | 97 … | 0 · 95 · 2 · 3 | 30 % · -56 dBm | hace 16 min · **Humedad sustrato 40 %** (obs.) | **10 regando, 87 en cola** |
| MZ-3 | rojo | 100 … | 0 · 97 · 3 · 0 | 68 % · -70 dBm | hace 24 min · **Humedad sustrato 41 %** (obs.) — **riego pospuesto por lluvia (80 %)** | 100 cerradas |
| MZ-4 | rojo | 98 … | 0 · 0 · 98 · 2 | 49 % · -87 dBm | hace 17 min · **Humedad sustrato 30 % (crítico, R-02)** | **9 regando, 89 en cola · 84 dosificando** |
| MZ-5 | rojo | 98 … | 0 · 0 · 98 · 2 | 64 % · -70 dBm | hace 28 min · **Sustrato saturado 82 % (crítico, R-04)** | cerradas · **86 dosificando** |
| MZ-6 | rojo | 9 … | 90 · 7 · 2 · 1 | 55 % · -62 dBm | hace 20 min · todo en rango | cerradas · 1 dosificando |

### 2.2.4 Atención prioritaria (`PriorityCard.tsx`)

Título + badge rojo **"411"**; 7 filas-botón (⇒ `/sector/:id`), cada una con punto rojo pulsante,
id, motivo y severidad:
MZ-1-010 Daño fúngico Alta · MZ-1-099 Estrés solar Alta · MZ-2-070 Plaga foliar Alta · MZ-2-075
Plaga foliar Alta · MZ-3-006 Estrés solar Alta · MZ-3-028 Plaga foliar Alta · MZ-3-057 Plaga foliar
Alta. Sin "ver todos", sin estado vacío.

### 2.2.5 Clima y riesgo (`WeatherCard.tsx`)

"21°C" · "Parcial nublado · humedad 78%" · "Índice UV 7 · Alto" · banner naranja con triángulo
"Lluvia probable en ~3 h — riego autónomo pospuesto en 1 macro-zona." · 4 franjas sin rótulo de
magnitud: 15 h **10%** UV 7 · 18 h **60%** UV 3 · 21 h **80%** UV 0 · Mañana **25%** UV 6.
(En el sistema real el texto del banner varía: "Lluvia probable (N%) — riego autónomo puede
posponerse." / "Lluvia posible (N%). Monitoreo activo." / "Sin lluvia inminente (N%). Operación
normal." — `NurseryService.java:415-422` — y con la API caída: "Pronóstico no disponible (API
climática degradada).")

### 2.2.6 Actividad del sistema (`ActivityFeed.tsx`)

"Actividad del sistema" / "Decisiones autónomas y sus condiciones desencadenantes"; 8 entradas en
línea de tiempo, cada una con ícono por tipo, título "Acción · sector", tiempo relativo, badge de
resultado y detalle:

| Título | Hace | Resultado | Detalle |
|---|---|---|---|
| Riego ejecutado · MZ-1-010 | 6 min | Efectiva | Humedad de sustrato bajo umbral (38%). Microaspersor abierto 95 s · 0,42 L. |
| Riego pospuesto · MZ-1-036 | 22 min | Pospuesta | Déficit hídrico detectado pero API meteorológica confirma lluvia inminente (60%). |
| Dosificación de fungicida · MZ-1-051 | 48 min | En seguimiento | Daño fúngico (confianza 93%) + sustrato >80%. Bomba peristáltica inyectó 4,5 ml. |
| Apertura de mediasombra · MZ-1-062 | 1 h | Efectiva | Plan de rustificación día 12 · apertura gradual 35% → 45%. |
| Dosificación de nutrientes · MZ-1-067 | 1 h | En seguimiento | Clorosis por déficit nutricional (confianza 89%). Inyección de 3,0 ml de NPK. |
| Riego abortado · MZ-1-070 | 2 h | Abortada | Sensor testigo sin reporte hace 2 h. Actuación autónoma anulada por seguridad. |
| Retracción de mediasombra · MZ-1-080 | 3 h | Efectiva | Pico de radiación UV 9 detectado. Cobertura llevada a 70% para proteger plantines. |
| Dosificación bloqueada · MZ-1-095 | 4 h | Abortada | Límite químico diario alcanzado (sector ya recibió dosis máx. en 24 h). |

Estado vacío: "Sin actividad registrada — el motor aún no ejecutó acciones en este ciclo." No hay
link al Historial ni ítems clickeables.

### 2.2.7 Diagnósticos recientes (`RecentDiagnostics.tsx`)

"Diagnósticos recientes" + "Ver todos →" (⇒ `/diagnosticos`); 5 filas-botón (⇒ `/sector/:id`) con
foto (o gradiente + hojita si no hay imagen), diagnóstico, "sector · confianza N%" y badge de
severidad:
Clorosis MZ-2-001 88 % Media · Clorosis MZ-2-002 98 % Media · Clorosis MZ-2-003 93 % Media ·
Estrés solar MZ-2-004 90 % Media · Clorosis MZ-2-006 87 % Media.

### 2.2.8 Datos que existen y el Panel NO muestra (candidatos para el rediseño)

- `Zona.lectura.metrics` (las 10 métricas evaluadas, con estado) — **la causa** de los estados de zona.
- `Zona.lectura.ago` / `ts` — frescura de cada nodo ("hace 16 min").
- `Zona.name` ("Macro-zona 1") y `Zona.sub` ("Sector norte" — ojo: colisión con "sector", ver U12).
- `Zona.nodo.mac`, `nodo.bateriaBaja` (sólo como color del chip).
- `Sector.actuadores` — válvula `Regando`/`En cola`/`Cerrada`, bomba `Dosificando`/`En espera`,
  mediasombra 30–65 %. Agregado por zona daría "riego en curso 10/97".
- `Sector.reason` y `Sector.diagnosis.conf` (sólo en prioridad/tooltips).
- `DiagnosisCard.time` ("hace 16 min"), `zonaName`, `concluyente` — la tarjeta de recientes no
  muestra cuándo ni de qué zona.
- Los **60 "No concluyente"** (excluidos a propósito de recientes, `generators.ts:459`) — son
  justamente los que necesitan ojo humano.
- `Alert.read`.
- De otros endpoints, útiles en el Panel: KPIs de `/api/hardware` (operativos, batería baja, fuera
  de servicio, averiados, sectores con mapeo incompleto), la pasada actual del riel, conteo real de
  acciones del día del historial, día del plan de rustificación.

## 2.3 Bugs e inconsistencias del Panel general

### Datos que se contradicen (en la misma pantalla o entre pantallas)

| # | Problema | Evidencia | Dónde |
|---|---|---|---|
| D1 | **"Hardware fuera de servicio 10 · Nodos testigo sin reportar" es imposible**: hay **6** nodos testigo y los 6 reportan (ninguna zona `stale`, chips verdes). El 10 es la cantidad de **sectores** con estado `offline`, que el mock sortea por sector ("nodo del sector sin responder") aunque en el dominio **los sectores no tienen nodo**. La pantalla Hardware dice **2 fuera de servicio** (nodo MZ-4 + EV-5-042) y 1 intermitente (MZ-3). | `KpiRow.tsx:69-76`, `generators.ts:219-220`, `:382` | 
| D2 | **En el sistema real es peor**: si un nodo deja de reportar, sus 100 sectores pasan a `offline` (`NurseryService.java:148-160`) ⇒ el KPI dice "**100** Hardware fuera de servicio · Nodos testigo sin reportar" por **1** nodo. Con el seed recién cargado (nadie reportó) diría **600**. | `NurseryService.java:237` |
| D3 | **Tres verdades sobre los nodos**: Panel (chip) MZ-2 30 % · -56 dBm / MZ-4 49 % · -87 dBm verde; Hardware MZ-2 **16 % batería baja** · -78 / MZ-4 **Fuera de servicio, hace 1 d** · 90 %; Campana "MZ-2-091 batería baja 18 %", "MZ-4-005 señal intermitente hace 2 h". El chip del nodo no usa el watchdog que usa Hardware. | `generators.ts:352-358` vs `hardware.ts:82-87` vs `generators.ts:503-518` |
| D4 | **"Sectores en alerta 411" vs badge "Diagnósticos de IA 414"**: 411 sectores warning+critical; 414 = diagnósticos no sanos **+ 3 entradas "No concluyente" agregadas a sectores que ya tenían diagnóstico** (duplicados). Dos números parecidos, distintos, sin explicación. | `generators.ts:436-456` |
| D5 | **"Atención prioritaria 411" muestra 7** sin indicar que son 7 de 411 ni ofrecer el resto. El badge es rojo aunque 206 de los 411 son "observación". | `PriorityCard.tsx:21-23`, `generators.ts:403` |
| D6 | **La prioridad ordena por id alfabético** (`critical` primero, después `a.id.localeCompare(b.id)`) y corta en 7 ⇒ siempre salen MZ-1…MZ-3. **MZ-4 (98 críticos por humedad 30 %) y MZ-5 (98 críticos por saturación 82 %) no aparecen**, aunque son las emergencias del vivero. El backend real hace lo mismo (`NurseryService.java:325-330`). | `generators.ts:399-403` |
| D7 | **Misma pantalla, mismo sector, dos diagnósticos**: MZ-2-070 es "Plaga foliar · Alta" en Atención prioritaria y "Clorosis detectada (confianza 88%)" en la campana. | `generators.ts:510-511` |
| D8 | **Campana MZ-1-010: "humedad de sustrato 84%"** — MZ-1 tiene la humedad en rango; ninguna zona tiene 84 % (MZ-5 tiene 82 %). | `generators.ts:490` |
| D9 | **Actividad contradice al resto**: todas las acciones caen en MZ-1 (toma los primeros 8 sectores no-ok); "Riego ejecutado · MZ-1-010 (38 %)" pero MZ-1 está en rango y sus 100 válvulas están **Cerradas**; "Riego pospuesto · MZ-1-036 (60 %)" pero la zona pospuesta es **MZ-3 (80 %)**; "Fungicida · MZ-1-051 Daño fúngico (93 %)" pero MZ-1-051 es **No concluyente**; "Riego abortado · MZ-1-070 sensor sin reporte hace 2 h" pero el nodo de MZ-1 reportó hace 25 min. | `generators.ts:462-478`, `specs.ts:235-292` |
| D10 | **"Acciones autónomas hoy 41 (28/7/6)" es un literal** y no cuadra con nada: el feed muestra 8; hay 19 válvulas "Regando" + 176 "En cola" y **178 bombas "Dosificando"** ahora mismo; el Historial (mock) tiene 48 acciones en total. Hardware registra **3** bombas en todo el vivero. | `generators.ts:391-394` |
| D11 | **Plano y Estado ordenan las zonas distinto**: el Plano usa `flex-wrap` con parcelas de 168 px e ignora `layout.macroZonasPorFila`; a 1920 px muestra **6 × 1** mientras el Estado muestra **3 × 2** (`w1920-viewport.png`). Dos "mapas" del mismo vivero con disposiciones distintas en la misma pantalla. | `PlanoVivero.module.css:49-59` vs `EstadoVivero.tsx:25` |
| D12 | **"~100 plantines" vs "10000 plantines"**: el subtítulo aproxima, el bloque calcula exacto (y sin separador de miles). `ComoLeer` hardcodea "4 bandejas / 25 plantines" en lugar de usar `BANDEJAS`/`TUBETES_POR_SECTOR`. | `EstadoVivero.tsx:32-33`, `resumenVivero.ts:84-85`, `ComoLeer.tsx:15-19` |
| D13 | **Orden de diagnósticos por string**: `diagnoses.sort((a,b) => a.time.localeCompare(b.time))` compara "hace 16 min" con "hace 4 min" como texto ("hace 4" > "hace 16") — hoy da bien por casualidad. | `generators.ts:455` |
| D14 | **Una lectura vencida oculta diagnósticos válidos**: si el nodo de una zona deja de reportar, sus 100 sectores pasan a gris "Fuera de servicio", y el diagnóstico de IA del plantín (que no depende del sensor) desaparece del estado. Contradice el principio "lo que diferencia a un sector es el diagnóstico de su plantín". | `resumenVivero.ts:27-33`, `NurseryService.java:148-160` |
| D15 | **Recientes = 5 de la misma zona y misma hora** (MZ-2-001…006, todos "hace 16 min") y **excluye los No concluyentes**, que son los que piden revisión humana. Backend igual (`NurseryService.java:318-321`). | `generators.ts:459` |
| D16 | **El banner de lluvia es siempre naranja con triángulo de advertencia**, también cuando el texto es "Sin lluvia inminente. Operación normal." o "Pronóstico no disponible". Y con la API caída la tarjeta muestra **"0°C · Sin datos · humedad 0% · 0 · N/A"** y una grilla de pronóstico vacía. | `WeatherCard.tsx:49-66`, `NurseryService.java:402-408` |
| D17 | **Pronóstico con horas fijas** ("15 h, 18 h, 21 h, Mañana") en el mock, independientes de la hora real. | `generators.ts:89-94` |

### Copy, gramática e idioma

| # | Texto actual | Problema | Propuesta | Dónde |
|---|---|---|---|---|
| C1 | "205 crítico" | sin plural | "205 críticos" | `KpiRow.tsx:62` |
| C2 | "206 observación" | falta "en"; otra etiqueta que la leyenda | "206 en observación" | `KpiRow.tsx:52` |
| C3 | "30% del vivero en parámetros óptimos" | falso: "saludable" combina ambiente **y** diagnóstico | "30 % de los sectores sin problemas detectados" | `KpiRow.tsx:30` |
| C4 | "Hardware fuera de servicio" / "Nodos testigo sin reportar" | mide sectores, no hardware (D1) | ver propuesta P-A2 | `KpiRow.tsx:69-76` |
| C5 | "CRITICAL" / "WARNING" | inglés | "Crítica" / "Advertencia" / "Información" | `AlertsDropdown.tsx:44` |
| C6 | "Parcial nublado" | incorrecto | "Parcialmente nublado" | `generators.ts:525` |
| C7 | "Productor Viverista" | mayúscula | "Productor viverista" (y no hardcodeado) | `Topbar.tsx:36` |
| C8 | "Misiones, AR" | abreviatura innecesaria | "Misiones, Argentina" o sólo "San Ignacio, Misiones" | `DashboardPage.tsx:15` |
| C9 | "10000 plantines" | sin separador de miles | "10.000 plantines" (`Intl.NumberFormat('es-AR')`) | `resumenVivero.ts:85` |
| C10 | "Edge activo · sincronizado" | jerga en inglés para el productor | "Conectado · datos de hace 12 s" | `Sidebar.tsx:87` |
| C11 | "Tocá una para ir a su detalle" | no va al detalle: scrollea al bloque de abajo | "Tocá una para verla abajo" — o que sí navegue (ver P-A3) | `PlanoVivero.tsx:25-27` |
| C12 | "Sin señal" (leyenda/contadores) vs "Fuera de servicio" (tooltip, aria-label, `LAB`) vs "Sin datos del sensor" vs "Sin reporte de telemetría · señal perdida" | 4 nombres para un estado; el glosario (CLAUDE.md §2) dice **"Fuera de servicio"** | Unificar | `EstadoVivero.tsx:19`, `resumenVivero.ts:77`, `specs.ts:20`, `generators.ts:246` |
| C13 | "sensor testigo" / "1 sensor testigo" / "Nodo testigo" / "Nodos testigo" | mezcla; el glosario dice **nodo testigo** | "nodo testigo" siempre | `PlanoVivero.tsx:41`, `ZonaBlock.tsx:51`, `ComoLeer.tsx:11` |
| C14 | "Sectores en alerta" (KPI) vs "necesitan atención" (plano) vs "Atención prioritaria" vs "Alertas activas" (campana) | "alerta" significa 2 cosas | "Sectores con problemas" / "Alertas" sólo para la campana | varios |
| C15 | "Retracción de mediasombra … Cobertura llevada a 70%" | "retraer" = recoger, pero la cobertura **sube** | "Despliegue de mediasombra · cobertura 45 % → 70 %" | `specs.ts:279-281` |
| C16 | "Apertura de mediasombra … 35% → 45%" | ¿apertura o cobertura? ambiguo frente a C15 | Fijar una magnitud ("cobertura") en todo el sistema | `specs.ts:258-260` |
| C17 | "Pico de radiación UV 9 detectado" | el nodo no mide UV (LDR, % de luz) | "Luminosidad 92 % (sobre 85 %)" o citar el pronóstico como pronóstico | `specs.ts:281` |
| C18 | "API meteorológica confirma lluvia inminente (60%)" | 60 % no "confirma"; "API" es jerga | "Pronóstico: 60 % de lluvia en 3 h" | `specs.ts:246` |
| C19 | "Sin reporte de telemetría · señal perdida" (motivo de un *sector*) | los sectores no reportan telemetría | "Sin lectura del nodo de la zona" | `generators.ts:246` |
| C20 | "Clima y riesgo" | ¿qué riesgo? sólo hay lluvia/UV | "Clima y pronóstico" | `WeatherCard.tsx:12` |
| C21 | Contadores "0 en observación · … · 0 sin señal" pero "críticos" sólo si > 0 | regla inconsistente | Omitir todos los 0, o mostrar todos | `resumenVivero.ts:72-78` |
| C22 | "ml" | unidad | "mL" | `specs.ts:253,267` |
| C23 | "-60 dBm" | guion en vez de signo menos; dBm no le dice nada al productor | "Señal buena/regular/débil" (dBm en tooltip) | `NodoChip.tsx:16` |

### Hardcodeos

`Sidebar.tsx:84-89` (estado del sistema) · `Topbar.tsx:35-38` (usuario) · `DashboardPage.tsx:15`
(nombre y ubicación del vivero) · `ComoLeer.tsx:11-19` (jerarquía y cantidades) ·
`EstadoVivero.tsx:32-33` ("~100 plantines") · `ZonaBlock.tsx:51` ("1 sensor testigo") ·
`KpiRow.tsx:46,56` (hex `#A66A12`, `#A8331C` en vez de `--warn-ink`/`--crit-ink`) ·
`KpiRow.tsx:82` (gradiente inline) · `WeatherCard.module.css:74` (`#9A4410`) ·
`generators.ts:391-394` (acciones del día) · `generators.ts:484-521` (alertas) ·
`generators.ts:523-531` (clima) · `specs.ts:235-292` (feed). Ubicación del clima en el backend:
`OpenMeteoWeatherClient` usa por defecto **Posadas** (-25.29, -57.64), no San Ignacio (~-27.26,
-55.54), mientras el subtítulo dice "Vivero San Ignacio".

### Layout roto / estados rotos

| # | Problema | Dónde |
|---|---|---|
| L1 | **A 1024 px las grillas de sectores se superponen**: la columna izquierda queda en ~330 px, el media query del Estado es por *viewport* (`max-width: 900px`) y no por contenedor, así que siguen 3 bloques de **85 px** con heatmaps de 10 celdas que se pisan entre zonas; el chip de nodo se corta ("67% · -60") y "Ver detalle de la zona" se parte en 4 líneas (`shotsA/w1024-estado.png`). 1024 px es una tablet apaisada o una notebook chica — exactamente el dispositivo de campo. | `EstadoVivero.module.css:50-54`, `DashboardPage.tsx:29-37` |
| L2 | **A 1024 px el Plano apila las 6 parcelas en una columna** (parcela de 168 px fijos en ~330 px) ⇒ 6 × 125 px de scroll para un índice. | `PlanoVivero.module.css:57-59` |
| L3 | **A 1440 px el riel derecho deja ~600 px vacíos** debajo de "Clima y riesgo" mientras la columna izquierda sigue con el Estado del vivero (`desk-dashboard.png`). | `DashboardPage.tsx:29-51` |
| L4 | **El Plano no es fluido**: parcelas de 168 px fijos; a 1440 px entran 3 por fila y quedan ~170 px vacíos a la derecha de la tarjeta; a 1920 px, las 6 en una fila. | `PlanoVivero.module.css:57-59` |
| L5 | "1 sensor testigo · 100 sectores · 10000 / plantines" se parte feo en todos los anchos < 1600 px. | `ZonaBlock.tsx:50-52` |
| L6 | Estados vacíos ausentes: Prioridad con 0 ítems queda con el badge "0" rojo y la tarjeta vacía; Recientes vacía sin mensaje; Clima sin pronóstico deja la grilla vacía. | `PriorityCard.tsx:26-49`, `RecentDiagnostics.tsx:27-69`, `WeatherCard.tsx:69-77` |
| L7 | Zona stale: muestra a la vez el aviso "Nodo testigo sin datos…" **y** 100 celdas grises. Redundante y sin acción ("Ver nodo en Hardware"). | `ZonaBlock.tsx:62-89` |

## 2.4 Problemas de UX del Panel general

- **U1. La pantalla no prioriza.** El orden visual es: 4 KPIs → índice de zonas (Plano) → 600
  celdas → actividad. Lo accionable ("Atención prioritaria") está en el riel derecho, al costado
  del índice, y en mobile queda **debajo** de los KPIs y antes del plano, pero recortado. Ninguna
  pieza responde "qué hago ahora".
- **U2. Duplicación Plano ↔ Estado.** Las 6 parcelas repiten la frase de `resumenZona` que también
  está en cada bloque (`PlanoVivero.tsx:43` y `ZonaBlock.tsx:57-59`) y sólo agregan "sensor
  testigo" (que no informa nada: siempre hay 1). Ocupan ~350 px de la mejor zona de la pantalla
  para hacer de *índice de anclas*. El "plano" tampoco es un plano (no refleja posiciones reales), y
  el patrón de "terreno" sugiere una geografía que no existe.
- **U3. Agregación equivocada: el problema es por causa, la lista es por sector.** 196 sectores
  críticos de MZ-4/MZ-5 comparten **una** causa (humedad del sustrato de la zona); listarlos de a
  uno nunca cabría y, además, el orden alfabético los esconde (D6). La unidad de triage debería ser
  "incidencia" = (zona, causa) o (diagnóstico, severidad), con cantidad de sectores afectados.
- **U4. "Peor estado manda" vuelve todo rojo.** MZ-1 (89 % sanos, 2 críticos) se ve igual que MZ-4
  (98 % críticos). El color pierde poder discriminante: con 6 de 6 en rojo, el ojo no tiene dónde
  ir. Falta la **proporción** (barra apilada) y separar "estado ambiental de la zona" de
  "plantines con diagnóstico".
- **U5. La causa ambiental no está.** Los 10 valores del nodo testigo, que determinan el piso de
  estado de los 100 sectores, sólo se ven entrando al detalle de zona. En el Panel debería leerse
  "MZ-4 · Humedad de sustrato 30 % (mín. 32 %)".
- **U6. La autonomía no se ve.** El Panel no dice qué está haciendo el sistema **ahora** (riego en
  tanda: 10 regando + 87 en cola en MZ-2; riego pospuesto en MZ-3 por lluvia; dosificación en
  MZ-4/MZ-5). El KPI "41 acciones hoy" es un contador sin contexto, y el feed muestra eventos
  pasados sin estado actual.
- **U7. Nada es accionable desde los números.** KPIs, parcelas (sólo scrollean), frases de zona,
  contadores y alertas no llevan a listas filtradas. "Sectores en alerta 411" debería abrir la lista
  de esos 411; "Hardware…" la vista Hardware filtrada; "Acciones hoy" el Historial de hoy.
- **U8. Dos gestos parecidos con resultados distintos**: tocar la parcela del Plano ⇒ scroll en la
  misma página; tocar "Ver detalle de la zona" ⇒ navega. El copy de la parcela dice "ir a su
  detalle" (C11).
- **U9. El heatmap de 600 celdas es caro y poco útil en el Panel**: 600 botones de 10–22 px,
  información sólo por color, tooltip nativo lento y pobre ("MZ-4-006 · Crítico", sin motivo ni
  diagnóstico). Sirve para ver "manchas" (patrones espaciales), pero ese uso pide la disposición
  real del riel, que ya está en el detalle de zona.
- **U10. Diagnósticos recientes no sirve para la tarea de IA**: no dice cuándo, no dice zona,
  repite 5 de la misma tanda y omite los No concluyentes (60) que piden validación. Debería ser
  "Para revisar: 60 no concluyentes · 178 de severidad alta".
- **U11. Actividad sin salida**: sin "Ver historial", sin filtro, sin link al sector ni a la regla
  que decidió (el Historial ya tiene `regla`, `lectura → decisión → acción`).
- **U12. Terminología que choca con el dominio**: "Sector norte" (`Zona.sub`, `specs.ts:169-174`)
  usa "sector" para una región de macro-zonas, cuando "sector" es la unidad de ~100 tubetes. Se ve
  en el detalle de zona ("Macro-zona 2 · Sector norte · 100 sectores").
- **U13. "Cómo leer esta pantalla"** es útil la primera vez y ruido después; vive en el medio del
  flujo, no se puede cerrar, y su modelo (bandejas) no aparece en ninguna otra parte del Panel.
- **U14. Frescura invisible por zona**: `lectura.ago` existe ("hace 16 min") pero no se muestra; un
  chip de nodo verde con lectura de hace 40 min engaña.
- **U15. Densidad desbalanceada**: arriba, KPIs aireados con poca info; en el medio, 600 celdas
  densísimas; a la derecha, huecos (L3).

## 2.5 Problemas visuales / UI del Panel general

- **Rojo omnipresente**: 6 bordes punteados rojos + 6 badges rojos + 6 frases rojas + 7 puntos
  pulsantes rojos + badge "411" rojo + chip "205 crítico". Saturación de alarma ⇒ ceguera de
  alarma. El ámbar (`--warn`) y el naranja (`--orange`, banner de lluvia, "Ver todos") se confunden
  (1,30:1).
- **Bordes punteados** (`2px dashed`) en parcelas y bloques: transmiten "borrador/placeholder" y
  con el patrón diagonal de fondo suman ruido visual a una zona que ya tiene 100 celdas.
- **KPI "Hardware" en gris `--off`** (2,18:1): el indicador de *falla* es el menos visible de la
  fila. **KPI "Acciones"** en tarjeta oscura destaca como el más importante, cuando es el menos
  accionable.
- **Número de "Sectores en alerta" en ámbar** aunque la mitad son críticos; el color del número no
  refleja la severidad máxima ni la mezcla.
- **Jerarquía tipográfica plana dentro de las tarjetas**: id de zona 21 px 800 en la parcela vs
  15 px en el badge del bloque; frase de zona 13 px bold en color de estado (bajo contraste); los
  contadores 11,5 px muted. La frase importante (la causa) no existe.
- **Badges de severidad todos iguales** en Prioridad ("Alta" ×7) y en Recientes ("Media" ×5):
  información repetida que no discrimina.
- **Pronóstico**: el porcentaje de lluvia en verde de marca (`--brand`) sin rótulo ("10%" ¿de
  qué?), UV en 10 px `--faint` (2,35:1).
- **Iconografía**: sol fijo para cualquier condición; triángulo de advertencia para un aviso que
  puede ser bueno (D16); el ícono `sensor` (antena) a 12–13 px es ilegible como metáfora. **No hay
  emojis** en el alcance (bien).
- **Alineaciones**: en KPIs el número de la 1ª tarjeta está en línea base con "/ 600" y en las otras
  no (distinta altura de bloque ⇒ subtextos desalineados entre tarjetas: 7 px vs 13 px de margen,
  `KpiRow.module.css:80-90`). En `ZonaBlock` el chip del nodo salta de línea según el ancho
  (`flex-wrap`).

## 2.6 Accesibilidad del Panel general

- **621 tabulaciones para llegar al primer ítem de "Atención prioritaria"** (7 de menú + campana + 6
  parcelas + 600 celdas + 6 links). Medido con Playwright. El heatmap necesita *roving tabindex*
  (1 tab stop por zona, flechas adentro) o directamente no ser tabulable en el Panel.
- **Sólo color**: celdas, puntos de prioridad, bordes de parcela y bloque. Simulación (Machado
  2009): con **deuteranopía** `--ok` → `#928b6d` y `--crit` → `#9b8c33` (ΔE 31, ambos caqui; misma
  luminancia); con **protanopía** ok vs crit ΔE 17. Contraste de luminancia entre celdas: ok vs
  crit **1,21:1**, ok vs warn 1,34:1, warn vs crit 1,62:1. Hace falta un segundo canal (forma,
  ícono, patrón para crítico y para fuera de servicio).
- **No-texto contra fondo (WCAG 1.4.11, 3:1)**: celda ámbar sobre `--bg` 2,17:1, celda gris 1,94:1,
  celda verde 2,90:1 — fallan; borde de parcela offline 1,94:1.
- **Texto de estado con color de estado**: frase de zona en `--crit` sobre `--bg` 3,50:1, en
  `--warn` 2,17:1, en `--ok` 2,90:1 (13 px bold ⇒ necesita 4,5:1); badge "MZ-1" en `--crit` sobre
  `--crit-soft` 3,28:1; KPI 411 en `--warn` 2,43:1 (34 px ⇒ necesita 3:1, falla); KPI 10 en `--off`
  2,18:1 (falla); "7 · Alto" UV 2,43:1; "Ver todos →" naranja 3,16:1 a 12 px; badge "411" `--crit`
  sobre `--crit-soft` 3,28:1 a 10,5 px. Los tokens `--*-ink` (5,2–6,9:1) existen y no se usan acá.
- **`--faint` como color de texto** (2,35–2,64:1): ".sub" del bloque de zona (10,5 px), hora del
  feed, UV del pronóstico (10 px), "/ 600", etiquetas de "Cómo leer…".
- **Tamaño de target**: celdas de 10–22 px (10 px a 1024 y a 390 px), por debajo de 24 × 24 (WCAG
  2.2 SC 2.5.8); el "Ver detalle de la zona" es texto de 11,5 px sin padding (~16 px de alto).
- **Nombres accesibles**: la parcela se anuncia "MZ-1 sensor testigo 9 sectores necesitan
  atención" (correcto pero verboso; falta el estado). Las filas de Prioridad no anuncian el estado
  (sólo el punto de color lo indica) — "MZ-1-010 Daño fúngico Alta" no dice "crítico". Las celdas sí
  tienen `aria-label` (bien) y `:focus-visible` (bien, `ZonaBlock.module.css:173-176`).
- **Semántica**: los KPIs son `div` sueltos (convendría `<dl>` o `role="group"` con
  `aria-label`); el destello al llegar desde el plano no mueve el foco al bloque (el usuario de
  teclado queda en la parcela); `ProgressBar` sin rol; el pulso infinito sin
  `prefers-reduced-motion`.
- **Uso en exterior**: el productor mira esto al sol, en el vivero; contrastes de 2–3:1 son
  ilegibles en esas condiciones aunque pasen en una oficina.

## 2.7 Responsive del Panel general

**Hoy** (sin un solo breakpoint en `DashboardPage`/`KpiRow`):

- 390 px: KPIs `repeat(4,1fr)` en 134 px ⇒ se ve sólo la primera tarjeta cortada; la grilla `1fr
  360px` desborda; el Plano y el Estado quedan en columnas de ~100 px con texto de a una palabra por
  línea; celdas de 10 px; la página tiene ~6000 px de alto y scroll horizontal (`mob-dashboard.png`).
- 1024 px: layout roto (L1, L2).
- 1280 px: funciona pero celdas de 10,9 px y bloques de 170 px con texto partido.

**Comportamiento propuesto**

| Ancho | KPIs | Cuerpo | Zonas |
|---|---|---|---|
| ≥ 1440 | 4 en fila | 2 columnas: principal (incidencias + zonas) / riel (clima, actividad compacta, IA para revisar) | `macroZonasPorFila` del layout (3) |
| 1024–1439 | 4 en fila compactos (`repeat(auto-fit, minmax(200px,1fr))`) | 1 columna; el riel baja a una fila de 2 tarjetas | 3 ó 2 por fila según **container query** del bloque (no viewport) |
| 600–1023 | 2 × 2 | 1 columna | 2 por fila; heatmap colapsado detrás de "Ver sectores" |
| < 600 | **carrusel horizontal** o 2 × 2 de KPIs mínimos (número + etiqueta), o resumen en 1 línea | Orden: resumen → incidencias → zonas (lista) → clima → actividad → IA | **Lista** de 6 filas (id, estado, causa, barra apilada, frescura), sin heatmap; tocar ⇒ detalle de zona |

---

# 3. Tabla de contraste (WCAG 2.x, calculada sobre `tokens.css`)

| Par | Ratio | Uso | Resultado |
|---|---|---|---|
| `--ink` / `--card` | 16,10 | texto principal | OK |
| `--muted` / `--card` | 4,69 | subtítulos | OK (justo) |
| `--muted` / `--bg` | 4,18 | contadores de zona, `.sensor` | **Falla** (< 4,5) |
| `--faint` / `--card` | 2,64 | horas, "/ 600", metadatos de alerta | **Falla** |
| `--faint` / `--bg` | 2,35 | `.sub` de zona, UV del pronóstico | **Falla** |
| `--off` / `--card` | 2,18 | KPI "10", leyenda | **Falla** (incl. texto grande) |
| `--off` / `--bg` | 1,94 | frase/borde de zona sin datos | **Falla** |
| `--ok` / `--card` | 3,26 | KPI "179" (34 px) | OK grande / falla chico |
| `--ok` / `--bg` | 2,90 | frase "Todo bien" 13 px | **Falla** |
| `--warn` / `--card` | 2,43 | KPI "411", "7 · Alto" | **Falla** |
| `--warn` / `--bg` | 2,17 | frase de zona en observación | **Falla** |
| `--warn` / `--warn-soft` | 2,15 | badge MZ, chip nodo con batería baja | **Falla** |
| `--crit` / `--card` | 3,93 | — | Falla chico |
| `--crit` / `--bg` | 3,50 | frase de zona crítica 13 px | **Falla** |
| `--crit` / `--crit-soft` | 3,28 | badge "411", badge "MZ-4" | **Falla** |
| `#A66A12` / `--warn-soft` | 3,96 | chip "206 observación", sev "Media" | **Falla** (12 px) |
| `#A8331C` / `--crit-soft` | 5,55 | chip "205 crítico", sev "Alta" | OK |
| `--warn-ink` / `--warn-soft` | 5,24 | (token existente, sin usar acá) | OK |
| `--crit-ink` / `--crit-soft` | 6,92 | (token existente, sin usar acá) | OK |
| `#6A776E` / `#EEEDE5` | 3,99 | sev "—" | **Falla** |
| `--orange` / `--card` | 3,16 | "Ver todos →" 12 px | **Falla** |
| `#9A4410` / `--orange-soft` | 5,46 | texto del banner de lluvia | OK |
| `--brand` / `--card` | 6,51 | links verdes | OK |
| blanco / `--crit` | 3,93 | contador de la campana 10,5 px | **Falla** |
| crema 66 % / `--g900` | 6,96 | ítems del menú | OK |
| `--cfaint` / `--g900` | 3,41 | "PRINCIPAL", "GESTIÓN", "MONITOREO IA" | **Falla** (10 px) |
| `#fce3d0` / ítem activo | 11,13 | ítem activo | OK |
| crema 55 % / `--g700` | 3,76 | "Acciones autónomas hoy" | **Falla** |
| celda ok / warn / crit / off vs `--bg` | 2,90 / 2,17 / 3,50 / 1,94 | heatmap (no-texto, 3:1) | 3 de 4 **fallan** |
| celda ok vs crit | 1,21 | distinguibilidad por luminancia | depende sólo del tono |

---

# 4. Propuestas de rediseño priorizadas

> Criterio: primero lo que hace que el Panel **diga la verdad** y **priorice**; después lo que lo
> hace usable en el campo (mobile, contraste); al final la higiene del sistema de diseño. Cada
> propuesta indica qué implica para el **sistema real** (backend), porque se van a portar.

## Prioridad ALTA

**P-A1. Una sola fuente de verdad para el estado de los nodos y del hardware.**
- El chip del nodo, el KPI de hardware, las alertas y la pantalla Hardware tienen que derivarse del
  mismo cálculo (el watchdog de `HardwareService`: operativo / intermitente / fuera de servicio /
  batería baja).
- Backend: exponer en `NurseryData` por zona `nodo.estado` (`operativo|intermitente|fuera_de_servicio`)
  y `nodo.ultimoReporte` (ts), y en `stats` contadores de hardware reales
  (`nodosReportando`, `nodosTotales`, `dispositivosFueraDeServicio`, `bateriaBaja`). Dejar de
  sortear/contar sectores `offline` como "hardware".

**P-A2. KPIs que digan la verdad y lleven a algún lado.** Propuesta de fila:

| KPI | Valor (demo) | Secundario | Clic ⇒ |
|---|---|---|---|
| Sectores con problemas | **411** de 600 | barra apilada 179 sanos · 206 obs. · 205 críticos · 10 sin dato | lista de sectores filtrada |
| Nodos testigo | **4 de 6 operativos** (tomando los datos de Hardware: MZ-4 caído, MZ-3 intermitente) | "1 batería baja (MZ-2) · lectura más vieja: hace 3 h" | Hardware filtrado |
| Riego ahora | **19 regando · 176 en cola** | "1 zona pospuesta por lluvia (MZ-3)" | Historial / zona |
| Acciones hoy | **N** (contado del historial) | riego · insumo · mediasombra · abortadas | Historial de hoy |

Números en `--ink`; el estado va en un indicador (punto/ícono + texto), no en el color del número.

**P-A3. Fusionar "Plano" + "Estado del vivero" en un único "Macro-zonas".** Una tarjeta por zona,
respetando `layout.macroZonasPorFila`, con:
1. Id + nombre ("MZ-4 · Macro-zona 4") y **estado en texto + ícono** ("Crítico").
2. **Causa ambiental** (lo que la lectura del nodo tiene fuera de rango, sólo métricas con
   `afectaEstado`): "Humedad de sustrato **30 %** · mínimo 32 %".
3. **Barra apilada** de los 100 sectores (sanos/obs./críticos/sin dato) con números.
4. **Qué hace el sistema**: "Riego en tandas: 9 regando, 89 en cola" / "Riego pospuesto: lluvia 80 %
   a las 21 h" / "Dosificando en 84 sectores".
5. **Nodo**: "Actualizado hace 17 min · batería 49 % · señal débil" (dBm en tooltip); en ámbar/rojo
   si intermitente o caído, con link "Ver en Hardware".
6. CTA "Ver zona" (navega a `/mapa?zona=`). El heatmap de 100 celdas pasa a ser **opcional**
   (toggle "Ver sectores", 1 tab stop por zona, roving tabindex) o se queda sólo en el detalle.
- Backend: agregar por zona un resumen de causa (`lectura.fueraDeRango[]`) y de actuadores
  (`actuadores.regando`, `enCola`, `dosificando`, `riegoPospuesto`, `motivo`) para no recalcular
  600 sectores en el cliente.

**P-A4. "Atención prioritaria" → "Para atender ahora", agrupado por incidencia.**
- Unidad = **incidencia** (zona + causa ambiental, o diagnóstico + severidad, o falla de
  dispositivo), con sectores afectados, desde cuándo y qué está haciendo el sistema. Ejemplo con el
  demo:
  1. **MZ-4 · Déficit hídrico crítico** — humedad 30 % · 98 sectores · riego en tandas (9/98) · hace 17 min.
  2. **MZ-5 · Sustrato saturado** — 82 % · 98 sectores · riego bloqueado.
  3. **EV-5-042 · Falla hidráulica** — sin flujo tras abrir · 1 sector sin riego.
  4. **Nodo MZ-4 fuera de servicio** (según Hardware) — lecturas no vigentes.
  5. **Plaga foliar (Alta)** — 65 sectores en 5 zonas · dosificación automática.
  6. **60 diagnósticos no concluyentes** — requieren revisión de la foto.
- Orden: severidad × cantidad × antigüedad; **nunca** alfabético. Cada fila navega al contexto
  (zona, sector, dispositivo, diagnósticos filtrados). Pie: "Ver los 411 sectores".
- Backend: endpoint/sección `incidencias` calculada por el motor (ya conoce reglas y causas);
  `priority` actual queda como fallback.

**P-A5. Alertas de verdad (campana + página).**
- Entidad propia en backend (no derivada de `priority`), con `nivel` semántico
  (`critica|advertencia|info`), `ts`, `origen` (zona/sector/dispositivo/regla), `estado`
  (`nueva|vista|atendida`), mensaje y link. Frontend: niveles en español, tiempo relativo con hora
  absoluta en tooltip, clic ⇒ contexto, "Marcar como atendida", "Ver todas" (⇒ `/historial?tipo=Alerta`
  o una página `/alertas`), badge oculto si 0, estado vacío "Sin alertas activas".
- A11y: `aria-expanded`, `aria-controls`, Escape, foco al abrir y al cerrar, panel `role="dialog"`
  con `aria-labelledby`; nombre accesible "Alertas, 5 sin atender". Mobile: sheet a pantalla completa.

**P-A6. Estado del sistema real y resiliencia del shell.**
- Tarjeta de estado del sidebar derivada de datos: última respuesta OK del backend (ts del fetch),
  lectura más vieja entre nodos, nodos caídos. Estados: **En línea** (verde) / **Datos atrasados**
  (ámbar, "la lectura más vieja es de hace 45 min") / **Sin conexión** (rojo, "último dato hace 3
  min · reintentando"). Quitar "Edge".
- `NurseryProvider`: un error de poll **no** reemplaza la app; se conserva el último snapshot y se
  muestra un banner con "Reintentar". Separar el provider del vivero de las rutas que no lo usan
  (Configuración, Reglas, Topología). Carga: shell + skeletons, no texto plano.

**P-A7. Responsive del shell y del Panel** (tablas de §1.7 y §2.7): sidebar colapsable/drawer,
topbar compacta, KPIs `auto-fit`, una columna < 1200 px, **container queries** para las tarjetas de
zona, lista de zonas en mobile, `100dvh`. Arreglar L1 (1024 px) es bloqueante.

**P-A8. Contraste y no depender del color.**
- Texto de estado siempre con `--ok-ink` / `--warn-ink` / `--crit-ink` (ya existen) y agregar
  `--off-ink` (~`#5f6862`); `--faint` sólo para decoración; subir `--muted` sobre `--bg` (o no usar
  `--bg` detrás de texto chico).
- Segundo canal en todo lo que hoy es sólo color: ícono por estado (✓ / ! / ✕ / señal tachada como
  SVG del registro, no emoji), patrón rayado para "fuera de servicio" y borde/punto interior para
  "crítico" en las celdas.
- Separar el acento de marca del ámbar de advertencia: el ítem activo del menú y los links en verde
  de marca/crema; reservar naranja/ámbar para "observación".

**P-A9. Contrato semántico con el backend.** `PriorityItem`, `Alert`, `ActionEvent`,
`DiagnosisCard`, `Sector` deben traer **semántica** (`status`, `severity`, `tipo`, `resultado`) y no
`color`, `sevSoft/sevInk`, `tint/ink`, `path` SVG ni `pulse` CSS. El frontend mapea a tokens en un
único módulo (`presentacion/estado.ts`). Esto desbloquea cualquier rediseño visual sin tocar Java.

## Prioridad MEDIA

**P-M1. Navegación.** Principal: Panel general · **Macro-zonas** (lista/selector; `/mapa` sin zona
muestra el selector en lugar de redirigir) · Diagnósticos de IA · **Alertas**. Operación:
Historial. Administración (colapsable o sólo para rol técnico): Configuración · Motor de reglas ·
Hardware · Topología. Mantener activo "Panel general"/"Macro-zonas" en `/mapa` y `/sector/:id`;
migas en el shell ("Panel general › MZ-2 › MZ-2-070"). Badge de Diagnósticos = **pendientes de
revisión** (no concluyentes), con `aria-label` explícito, color neutro.

**P-M2. "IA: para revisar" en lugar de "Diagnósticos recientes".** Dos bloques: "No concluyentes
(60)" y "Severidad alta (178)", cada uno con 3–5 miniaturas, zona y hora, ordenados por `ts` real
(no por string). "Ver todos" lleva a Diagnósticos filtrado. Mostrar `time` y `zonaName`, que ya
llegan.

**P-M3. Actividad.** Link "Ver historial completo"; cada entrada clickeable (⇒ sector o evento del
historial con su cadena lectura → decisión → acción y la `regla`); agrupar eventos repetidos ("Riego
ejecutado en 10 sectores de MZ-2"); separar "en curso" de "terminado"; coherencia con el KPI
(mismo origen: historial del día).

**P-M4. Clima.** Un solo lugar (tarjeta) o chip clickeable que lleve a la tarjeta; ícono según
condición; banner con nivel según el riesgo (info / advertencia) y nombrando la zona afectada
("Riego pospuesto en MZ-3: 80 % de lluvia a las 21 h"); rotular "Prob. de lluvia" y "UV"; horas
reales; estado "Pronóstico no disponible" sin mostrar "0°C". Revisar la ubicación por defecto del
cliente de Open-Meteo (Posadas ≠ San Ignacio).

**P-M5. Vocabulario único** (aplicar en UI, mocks y backend):
`Saludable · En observación · Crítico · Fuera de servicio` (estados); **nodo testigo** (nunca
"sensor testigo"); **alerta** = notificación de la campana; **sectores con problemas** = KPI;
**cobertura de mediasombra** (no "apertura"); **luminosidad** (no "UV") para el LDR;
renombrar `Zona.sub` "Sector norte" → "Franja norte"/"Bloque norte".

**P-M6. Usuario y sesión.** Nombre/rol desde la sesión (o no mostrarlo si no hay auth); menú con
"Mi cuenta" / "Salir". Ubicación del vivero desde configuración, no literal.

**P-M7. Teclado y lector.** Skip link; `:focus-visible` global consistente (anillo de 2 px
`--brand` con offset, versión crema sobre el sidebar); heatmap con roving tabindex; mover el foco al
bloque al llegar desde una parcela/incidencia; `aria-label` con estado en filas de prioridad;
`document.title` por ruta ("Panel general · Yerbanalytics").

**P-M8. "Cómo leer esta pantalla"** → botón "?" en el encabezado de Macro-zonas con un popover,
mostrado abierto sólo la primera vez (preferencia local); cantidades derivadas de
`BANDEJAS`/`TUBETES_POR_SECTOR`.

**P-M9. Estado de zona más informativo que "peor estado".** Mantener el peor estado para el ícono,
pero el color de fondo/borde de la tarjeta según el **estado ambiental** de la zona (lectura del
nodo) y la barra apilada para los plantines. Una lectura vencida **no** debe borrar el diagnóstico
de IA de los sectores (D14): mostrar "Lectura no vigente" como aviso de la zona y conservar los
diagnósticos.

## Prioridad BAJA

**P-B1. Sistema de diseño.** Escala tipográfica (12 / 13 / 14 / 16 / 20 / 28 / 34), espaciado en
múltiplos de 4, radios 8 / 12 / 16; `Card` usado por todas las tarjetas (con `title`, `action`,
`footer`); `Badge` con variantes semánticas (`variant="critico"`) en lugar de colores crudos;
`theme.ts` generado desde `tokens.css` o eliminado; mover los hex de `specs.ts`/`selectors.ts` a
tokens.

**P-B2. Íconos.** Todo SVG desde el registro `Icon` (sol, alerta, hoja); agregar íconos de estado y
de condición climática; reemplazar el ícono `sensor` (antena) por uno legible a 12 px o acompañarlo
siempre de texto.

**P-B3. Formato numérico es-AR** con `Intl.NumberFormat`: "10.000", "1,4 dS/m", "30 %", "−60 dBm",
"mL".

**P-B4. Movimiento.** `@media (prefers-reduced-motion: reduce)` para `ybPulse`, `ybFade`,
`flashPulse`; limitar el pulso a la incidencia más grave (no 7 puntos pulsando).

**P-B5. Fondo y bordes.** Quitar los bordes punteados y el patrón diagonal de parcelas/bloques
(o reservarlos para "sin datos", donde el punteado sí comunica ausencia).

**P-B6. Modo "exterior" / alto contraste** (toggle en el menú de usuario): tokens con contraste ≥ 7:1
para uso al sol en el vivero.

---

# 5. Boceto de la estructura propuesta

**Desktop (≥ 1440)**

```
┌ Topbar ───────────────────────────────────────────────────────────────┐
│ Panel general · San Ignacio, Misiones   [Actualizado hace 8 s] [Alertas 5] │
└───────────────────────────────────────────────────────────────────────┘
[ Sectores con problemas 411/600 ▓▓▓░ ][ Nodos 4/6 ][ Riego ahora 19+176 ][ Acciones hoy N ]

┌ Para atender ahora (6) ─────────────────────┐ ┌ Clima y pronóstico ──────┐
│ ● MZ-4 Déficit hídrico crítico · 98 sect.   │ │ 21 °C parc. nublado      │
│ ● MZ-5 Sustrato saturado · 98 sect.         │ │ Riego pospuesto en MZ-3  │
│ ● EV-5-042 Falla hidráulica                 │ │ 15h 10% · 18h 60% · …    │
│ ▲ 60 diagnósticos no concluyentes           │ └──────────────────────────┘
│ Ver los 411 sectores →                      │ ┌ IA: para revisar ────────┐
└─────────────────────────────────────────────┘ │ 60 no concl. · 178 alta  │
┌ Macro-zonas (3 por fila, según Topología) ? ┐ └──────────────────────────┘
│ [MZ-1 ✓ ok amb. ▓▓▓▓░ 89/7/2/2 · nodo 25m]  │ ┌ Actividad (en curso/hoy)─┐
│ [MZ-2 ! hum 40% ▓▓░ riego 10+87 · nodo 16m] │ │ … · Ver historial →      │
│ [MZ-4 ✕ hum 30% ▓ riego 9+89 · nodo 17m]    │ └──────────────────────────┘
└─────────────────────────────────────────────┘
```

**Mobile (390)**: topbar 56 px (menú, título, campana) → línea de resumen ("411 de 600 con problemas ·
4/6 nodos operativos") → "Para atender ahora" (máx. 4 + ver todo) → lista de 6 zonas (estado, causa, barra,
frescura) → clima compacto → IA para revisar → actividad. Sin heatmap.

---

# 6. Checklist "no perder" para el rediseño

Datos y acciones que hoy existen en el shell/Panel y tienen que seguir estando (en algún lugar):
nombre de la app; navegación a las 8 vistas + Demo Expo condicional; contador de diagnósticos;
estado del sistema (ahora real); título/subtítulo por vista; temperatura, condición, UV, humedad,
texto de lluvia y 4 franjas de pronóstico; campana con nivel, hora, sector y mensaje; usuario/rol;
sanos/total/%; observación y críticos; fuera de servicio (redefinido); acciones del día por tipo;
frase-resumen del vivero; por zona: id, frase, contadores (sanos/obs./críticos/sin dato), batería y
señal del nodo, aviso de lectura no vigente, grilla de sectores con acceso a cada sector (al menos en
el detalle de zona), link al detalle; leyenda de colores; explicación de la jerarquía física;
prioridad por sector con motivo y severidad (ahora agrupada); 8 eventos de actividad con tipo,
sector, tiempo, resultado y detalle; diagnósticos con foto, estado, sector, confianza y severidad;
"Ver todos" de diagnósticos; destello al llegar a una zona.
