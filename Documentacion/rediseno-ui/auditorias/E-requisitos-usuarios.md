# E · Requisitos y usuarios para el rediseño del dashboard

> Análisis funcional / UX research. **No modifica el repo.** Fecha de corte: 06/10/2026.
> Raíz del repo: `/home/user/Yerbanalytics`. Todas las rutas de este informe son relativas a esa raíz.
> Frontend = `Desarrollo/frontend/src/` (se abrevia `src/`).

---

## 0. Antes de empezar: qué fuente manda cuando se contradicen

Se leyeron: `CLAUDE.md`; `Documentacion/1_ResumenPreliminar.md`, `2_ModeloDeNegocio.md`,
`3_DefinicionAlcance.md`, `5_ReleasePlanning.md`; `Documentacion/extras/*.md`;
`Documentacion/arquitectura y hw/*.md`; el Story Map `Documentacion/entregas/v3/Visual Story Mapping
Yerbanalytics.png`; las 33 specs vivas de `openspec/specs/`; los cambios `redesign-estado-vivero`,
`add-monitoring-frontend`, `move-sensado-macrozona` (archivado), `add-dashboard-simulacion`
(archivado), `add-topologia-visual` (archivado), `extract-simulador-standalone`,
`add-catalogo-umbrales-reglas`, `add-pasada-riel`, `finish-release-2`, `release-2-overview`,
`use-real-data-dashboard`, `visualize-rule-dag`, `add-servicio-inferencia`,
`add-configurable-intervals`; `docs-motor-reglas-e-integracion/reglas_v2.md` y
`diferencias-motor-reglas-vs-reglas-v2.md`; el diseño original `Desarrollo/frontend/Yerbanalytics.dc.html`;
y el código actual del frontend (y del backend donde hizo falta confirmar algo).

**Jerarquía de verdad usada en este informe:**

1. `CLAUDE.md` + el código actual → decisiones vigentes.
2. `docs-motor-reglas-e-integracion/reglas_v2.md` (28/09/2026) → intención agronómica más reciente; corrige
   varias HU (lo dice explícitamente).
3. `openspec/specs/*` → requisitos, pero varias tienen notas de estado viejas (ver abajo).
4. `Documentacion/3_DefinicionAlcance.md` (06/06/2026) → HU y CA originales.

**Contradicciones entre fuentes que el rediseño tiene que saber:**

| Tema | Lo que dice cada fuente | Impacto en la UI |
|---|---|---|
| Plan de releases | `3_DefinicionAlcance.md §3.1` agrupa R1–R4 de una forma; `5_ReleasePlanning.md §5.2` de otra. **Vale la de `5_ReleasePlanning`** (la usa `openspec/changes/release-2-overview/README.md`). | Hoy estamos en R3 sprint 2 (03/10–17/10: HU-14, HU-21). **R4 (17/10–07/11) trae HU-01, HU-11, HU-12, HU-16, HU-17, HU-20**: login, roles, exportación y responsive. El rediseño tiene que dejarles lugar ya. |
| Umbral de confianza del diagnóstico | HU-04 CA-03 y HU-07 CA-01/02: 85 %. `reglas_v2.md §1.3`: **70 % configurable** ("hay que actualizar la HU"). Backend: parámetro `diagnostico.confianza-minima` en el catálogo. UI: **"85 %" escrito a mano** en `src/features/sector/components/DiagnosisCard.tsx` y `src/data/mock/sectorDetail.ts` (`conf >= 85`). | La UI tiene que leer el umbral del catálogo, nunca hardcodearlo. |
| Mediasombra | HU-08 y `CLAUDE.md §2`: actuador **por sector**, % de apertura. `reglas_v2.md §6`: **por macro-zona**, sólo **abierta / cerrada**, regulada por franjas horarias. Código: % por sector (`ShadingRule`). | Hoy el sector muestra "Mediasombra · Apertura N %". Si se adopta v2, la mediasombra sube a la macro-zona. Decisión abierta: no diseñar algo que asuma una u otra sin cerrarla. |
| Severidad del diagnóstico | UI: Alta / Media / Baja. `reglas_v2.md §1.3`: leve / moderada / alta. `Desarrollo/servicio-inferencia/src/model.py` (`_map_severidad`): **la deriva de la confianza** (≥ 80 → Alta). | Severidad y confianza hoy son el mismo número disfrazado. La UI no debe presentarlas como dos evidencias independientes hasta que el modelo emita severidad real. |
| Niveles de alerta | HU-10 y backend: `INFO / WARNING / CRITICAL`. Spec `alertas-inteligentes`: CRÍTICA / ALTA / MEDIA / INFO. Historial los traduce (Informativa / Advertencia / Crítica, `src/features/historial/eventoPresentacion.ts`); la campana muestra el texto crudo en inglés (`src/components/layout/AlertsDropdown.tsx`). | Unificar en tres niveles en castellano. |
| Taxonomía de diagnósticos | Modelo: Sano, Clorosis, Estrés solar, **Daño biótico** (+ Colapso opcional) (`Documentacion/arquitectura y hw/informe-ia-plan-entrenamiento.md §2.2`). Backend acepta 8 estados (`DiagnosticoService.ESTADOS`: + No concluyente, Ácaro, Plaga foliar, Daño fúngico). Filtro de la UI: Estrés solar, Clorosis, Plaga foliar, Daño fúngico, No concluyente (`src/features/diagnostics/components/DiagFilters.tsx:4`). | Un diagnóstico "Daño biótico", "Ácaro" o "Sano" **no se puede filtrar** hoy. |
| Superficie del sector | `1_ResumenPreliminar.md`: ~1 m². `reglas_v2.md §1.1`: ≈ 0,3 m² (4 bandejas × 25). | Usar "100 plantines en 4 bandejas", no m². |
| Specs con estado viejo | `simulacion-modo-vista` describe un modo persistido en el backend: **obsoleta** desde `extract-simulador-standalone` (el backend no tiene modos). `alertas-inteligentes` dice "Frontend OK". `motor-reglas` usa nombres de clases viejos (`RiegoRule`, `InsumoRule`). `historial-trazabilidad` pide timeline cronológico y hoy es "por ciclo" (`HistorialTimeline.tsx:95`). | No tomar esas specs como verdad para el rediseño sin cruzarlas con el código. |

---

## 1. Personas y roles

`3_DefinicionAlcance.md` (Convenciones) define cuatro roles con login (HU-01 CA-01, HU-20 CA-01):
**Productor Viverista, Ingeniero Agrónomo, Operario, Administrador** (+ "Usuario del sistema" genérico).
El modelo de negocio (`2_ModeloDeNegocio.md §2.2.3–2.2.4`) agrega un quinto público real que hoy ya
tiene pantalla: **quien mira una demo** (ferias, demostración con prototipo, tribunal).

### Resumen

| Persona | Dónde y con qué | Frecuencia | Objetivo en una frase |
|---|---|---|---|
| **P1 · Productor viverista** | Oficina/casa (notebook) y **celular**, a distancia del vivero | Diaria, sesiones cortas; mensual para reportes | "Saber de un vistazo si está todo bien y, si no, dónde y qué hizo el sistema." |
| **P2 · Ingeniero agrónomo** | Escritorio, a veces remoto (asesor propio, de cooperativa o INTA) | Semanal / por campaña; picos al calibrar o al iniciar la rustificación | "Calibrar las decisiones y auditar que el sistema actúe con criterio agronómico." |
| **P3 · Operario** | **En el vivero**, celular en mano, al sol, con guantes, con conectividad intermitente | Varias veces al día, reactivo | "Frenar, forzar o reparar algo físico ya, sin que el sistema me pise." |
| **P4 · Administrador / técnico instalador** | Notebook o tablet en el vivero durante la instalación; oficina para recambios | Intensa en la puesta en marcha; ocasional después | "Dejar el vivero mapeado, con cada equipo atado a su lugar, y saber qué hay que recambiar." |
| **P5 · Visitante de feria / prospecto / tribunal** | Pantalla grande o notebook en un stand; demo sin backend (`npm run dev:demo`) | Eventual | "Entender en un minuto qué hace el sistema y verlo actuar en vivo." |

### P1 · Productor viverista

- **Quién.** Dueño o gerente del vivero; el usuario de referencia del diseño ("Mariano Duarte ·
  Productor Viverista", `src/components/layout/Topbar.tsx`). Segmentos: productor con vivero propio,
  vivero dedicado, cooperativa (`2_ModeloDeNegocio.md §2.2.1`). "Carecen de personal para reaccionar
  a tiempo"; usan "la plataforma web como tablero de supervisión e historial, mientras el sistema
  interviene físicamente de forma autónoma".
- **Contexto.** "Accede desde cualquier dispositivo" (`1_ResumenPreliminar.md §1.2`; HU-17). Recibe
  alertas por **WhatsApp** (decisión en `Documentacion/extras/Notificaciones-WhatsApp-Cloud-API.md`;
  `Analisis-OLA.md §4` la presenta como "el canal móvil sin construir una app") y entra al dashboard
  desde el link de la alerta, o sea: **muchas sesiones empiezan en el celular, en una alerta**.
- **Objetivos.** Saber si hay problema y dónde (HU-14); entender el estado sanitario (HU-05);
  enterarse sin recorrer el vivero (HU-10); auditar qué hizo el sistema y por qué (HU-11/12);
  respaldar controles de calidad y procesar consumos de insumos en su contabilidad (HU-16).
- **Pantallas.** Panel general (diaria), alertas, Diagnósticos de IA, Sector (desde alerta o mapa),
  Historial, exportación (falta).
- **Dolores hoy.** Indicadores que no dicen lo que parecen (ver §4.2); campana que no se puede marcar
  como leída; nada responsive.

### P2 · Ingeniero agrónomo

- **Quién.** Calibra el sistema (HU-15) y valida sus decisiones. El modelo de negocio lo nombra en
  "Validación agronómica: auditar las decisiones tomadas por el sistema" (`2_ModeloDeNegocio.md
  §2.2.7`). En `reglas_v2.md §7` es el **único** que inicia, cancela o resetea el plan de
  rustificación por macro-zona.
- **Contexto.** Escritorio; sesiones largas y analíticas; probablemente no está todos los días en el
  vivero (`1_ResumenPreliminar.md §1.5`, riesgo de disponibilidad).
- **Objetivos.** Que los umbrales reflejen la realidad del vivero sin riesgo de sobredosis (HU-15
  CA-03/04/05); decidir cuándo empieza la rustificación de cada MZ; revisar los diagnósticos "No
  concluyentes" (HU-04 CA-03, HU-07 CA-02); entender por qué el motor decidió algo (Inspector).
- **Pantallas.** Configuración agronómica, Motor de reglas (Parámetros + Inspector), Macro-zona
  (sensado e histórico), Diagnósticos, Historial.
- **Dolores hoy.** Los umbrales están repartidos entre dos pantallas (Configuración y Motor de
  reglas); el plan de rustificación no se puede iniciar desde la UI (depende de una property vacía,
  `yerbanalytics.nursery.sowing-date-iso=` en `Desarrollo/backend/src/main/resources/application.properties:108`);
  el histórico de métricas es sintético (ver §4.2).

### P3 · Operario

- **Quién.** Personal de campo. Según `reglas_v2.md`: prepara las soluciones y **carga los tanques**
  (§8.1), riega a mano cuando hay falla hidráulica y **cubre a mano** cuando falla la mediasombra
  (S-05), activa el **bloqueo manual** (S-01, HU-19), y registra la revisión física después de una
  acción "Sin efectividad" (S-06). También es quien monta y mantiene el celular del riel
  (`Desarrollo/camara-android/README.md`).
- **Contexto.** En el vivero, bajo malla o al sol, con las manos ocupadas, con señal débil (Misiones;
  `2_ModeloDeNegocio.md §2.2.8`, Starlink). **Celular**, botones grandes, pocos pasos, lectura bajo
  luz fuerte. HU-17 CA-02 pide un banner de "Modo sin conexión" que **deshabilite los controles de
  actuación** cuando no hay red.
- **Objetivos.** Ubicar un sector físico por su número (está parado frente a él); frenar la
  actuación autónoma de un sector o MZ y reanudarla (HU-19 CA-01/04); forzar un actuador en
  emergencia, con advertencia si excede un límite (HU-19 CA-02/03); registrar reparaciones y recargas.
- **Pantallas.** Hoy ninguna le sirve: **no existe una sola acción de operario en el dashboard**.
- **Restricción de diseño.** Es el usuario con menos tolerancia a jerga técnica y a navegación
  profunda.

### P4 · Administrador / técnico instalador

- **Quién.** Mapea el vivero y registra el hardware (HU-18), vigila la flota (HU-21), gestiona
  usuarios (HU-20). En la práctica comercial es el **equipo técnico de Yerbanalytics** que "instala y
  calibra todo el equipamiento… y provee los accesos" (`2_ModeloDeNegocio.md §2.2.3, §2.2.7`), más un
  encargado del vivero para recambios.
- **Contexto.** Puesta en marcha en el campo (tablet/notebook, tipeando seriales/MAC); después,
  mantenimiento preventivo y recambios esporádicos.
- **Objetivos.** Generar la grilla (HU-18 CA-01), dar de alta cada equipo sin duplicados (CA-02/03),
  saber qué sectores quedan sin actuación autónoma (CA-04), detectar equipos caídos o con batería baja
  (HU-21), **vincular el celular de la cámara** (hoy sólo se puede desde el simulador, §3.12).
- **Pantallas.** Hardware, Topología, (falta) Usuarios y roles, (falta) Dispositivos de captura.

### P5 · Visitante de feria / prospecto / tribunal

- **Quién.** Las ferias son "la principal vía para darnos a conocer" y la demostración con prototipo
  es "el canal decisivo para cerrar la venta" (`2_ModeloDeNegocio.md §2.2.3`). Además, es una tesis:
  hay un tribunal.
- **Contexto.** Pantalla grande, alguien del equipo maneja; o demo sin backend (`npm run dev:demo`,
  `VITE_DATA_SOURCE=mock`).
- **Objetivos.** Entender la jerarquía vivero → macro-zona → sector → bandeja; ver el ciclo completo
  "foto → IA → decisión → acción".
- **Pantallas.** Demo Expo (`/demo-expo`, oculta tras un interruptor), Panel general con "Cómo leer
  esta pantalla" (`src/features/dashboard/components/ComoLeer.tsx`), Sector con su dibujo físico y
  "¿Qué estoy viendo?" (`src/features/sector/SectorPage.tsx`). El cambio `redesign-estado-vivero`
  nació justamente porque "quien no conoce el dominio no entiende qué mira" (`proposal.md`).

> **No es persona del dashboard:** el equipo que prueba con el simulador. El simulador es otra app
> (`:5180`) y por decisión **no aparece en el dashboard** (`openspec/specs/simulacion-captura/spec.md`,
> "La sección no aparece en el dashboard"; `CLAUDE.md §6.2`). Ojo: varias pantallas actuales
> (Inspector, historial "por Ciclo", Demo Expo) hoy hablan más el idioma de ese equipo que el del
> vivero.

### Matriz persona × pantalla (frecuencia esperada)

| Pantalla | P1 Productor | P2 Agrónomo | P3 Operario | P4 Admin | P5 Expo |
|---|---|---|---|---|---|
| Panel general | **Diaria** | Semanal | Diaria (móvil) | Ocasional | **Siempre** |
| Alertas (bandeja) | **Diaria** (desde WhatsApp) | Semanal | **Diaria** | Ocasional (hardware) | — |
| Macro-zona | Semanal | **Semanal** (sensado, histórico, rustificación) | Diaria (bloquear MZ) | Ocasional | Demo |
| Sector | Diaria (desde alerta) | Semanal | **Diaria** (acciones) | Ocasional | Demo |
| Diagnósticos de IA | Semanal | **Semanal** (revisar no concluyentes) | — | — | Demo |
| Historial / reportes | Semanal / mensual | Semanal | Ocasional | Ocasional | — |
| Configuración agronómica + Motor de reglas | Sólo lectura | **Edición** | — | — | — |
| Hardware | Ocasional | — | Ocasional (recambio) | **Edición** | — |
| Topología | — | — | — | **Una vez** | — |
| Usuarios y roles (falta) | — | — | — | **Edición** | — |
| Demo Expo | — | — | — | — | **Siempre** |

---

## 2. Historias de usuario y criterios de aceptación que tocan la UI, por pantalla

Fuente de HU/CA: `Documentacion/3_DefinicionAlcance.md §3.3`. Estado medido contra el código actual
(no contra las notas de estado de las specs).
**Leyenda:** **Sí** = cubierto · **Parcial** = cubierto a medias o con datos no reales · **No** = no existe.

### 2.1 Shell (sidebar, topbar, campana, sesión) — `src/components/layout/`

| HU / CA | Qué pide | Estado | Evidencia / nota |
|---|---|---|---|
| HU-01 CA-01/02/03 | Login por rol, error genérico, cierre por inactividad | **No** | Usuario fijo "Mariano Duarte · Productor Viverista" (`Topbar.tsx`). Llega en R4. |
| HU-20 CA-01/02/03 | Gestión de usuarios y roles, invalidar sesión, auditoría | **No** | No hay sección ni endpoint. |
| HU-17 CA-01 | Operable de 320 px a 1080p sin scroll horizontal (Dashboard, Historial, Configuración) | **No** | Sidebar fijo de 256 px sin breakpoint (`Sidebar.module.css:2`); `DashboardPage.tsx` usa `gridTemplateColumns: '1fr 360px'` inline. Algunas vistas tienen `@media` sueltos. |
| HU-17 CA-02 | Banner rojo "Modo sin conexión", datos cacheados, **controles de actuación y configuración deshabilitados** | **No** | No hay service worker ni banner. |
| HU-17 CA-03 | Pantalla de navegador no soportado | **No** | — |
| HU-10 CA-01 | Alertas clasificadas INFO/WARNING/CRITICAL y registradas en el historial | **Parcial** | El backend sí registra eventos `Alerta` con nivel (`HistorialService.registrarAlerta`), pero **la campana no los usa**: en modo `http` la arma con los 5 primeros sectores de "atención prioritaria", con hora "ahora" (`NurseryService.buildAlertsFromHistorial`). Dos fuentes distintas para "alertas". |
| HU-10 CA-02 | Push (WhatsApp) para CRITICAL/WARNING | **No** | No hay integración. |
| HU-10 CA-03 | Texto con severidad, timestamp, sector, variable y acción ejecutada o bloqueada | **Parcial** | Campana: nivel (en inglés), "ahora", sector, mensaje. Sin variable ni acción. |
| HU-10 CA-04 | Marcar "Atendida/Leída" sin borrarla del historial | **No** | Hay un campo `read`, pero ningún control; no hay `PATCH` (`finish-release-2/tasks.md` sin hacer). |
| Spec `app-shell` "Indicador de estado del sistema" / HU-13 CA-03 | Mostrar si el edge está sincronizado | **Parcial (falso)** | "Sistema en línea · Edge activo · sincronizado · hace 40 segundos" está **escrito a mano** (`Sidebar.tsx:84-90`). |
| HU-09 (visibilidad) | Clima en la topbar | **Parcial** | Real (Open-Meteo), pero en modo degradado muestra **"0 °C · UV 0 · Sin datos"** porque el backend manda `0.0` (`NurseryService.buildWeatherFromForecast`). |

### 2.2 Panel general (`/`) — `src/features/dashboard/`

| HU / CA | Qué pide | Estado | Evidencia / nota |
|---|---|---|---|
| HU-14 CA-01 | Cuadrícula interactiva con la disposición real de MZ y sectores | **Sí** | "Plano del vivero" + "Estado del vivero", respetan `layout` de la topología (`PlanoVivero.tsx`, `EstadoVivero.tsx`, `ZonaBlock.tsx`). |
| HU-14 CA-02 | Sectores coloreados por estado | **Sí** | Leyenda: Saludable · En observación · Crítico · Sin señal. |
| HU-14 CA-03 | Clic en sector → panel lateral o modal con métricas, diagnóstico y actuadores | **Parcial** | Navega a la página `/sector/:id` (no panel), y las métricas están en la macro-zona **por decisión** (§4, D-02). |
| Spec `dashboard` KPIs | Saludables, en alerta, hardware fuera de servicio, acciones del día | **Parcial** | El KPI "Hardware fuera de servicio · Nodos testigo sin reportar" **cuenta sectores** sin señal, no equipos (`NurseryService.java:237`, `KpiRow.tsx:69-76`): con un solo nodo caído dice "100". |
| Spec `dashboard` "Atención prioritaria" | Sectores críticos/alerta | **Parcial** | Recortada a 7 sectores en el backend (`NurseryService.java:330`) sin aviso. Además, como el sensado es por MZ, una MZ seca pone a sus 100 sectores en alerta y la lista se llena de 7 sectores iguales de la misma zona. |
| HU-09 CA-01/03 | Clima, UV, pronóstico, riesgo | **Sí** con degradado mejorable | `WeatherCard.tsx`. |
| HU-11 (resumen) | Actividad del sistema: decisiones y su condición | **Sí** | `ActivityFeed.tsx`. |
| HU-05 (resumen) | Diagnósticos recientes | **Sí** | `RecentDiagnostics.tsx`, con foto real si existe. |

### 2.3 Macro-zona (`/mapa?zona=MZ-N`) — `src/features/map/`

Entrada **sólo** desde el Panel general (decisión D-01). Título: nombre de la MZ.

| HU / CA | Qué pide | Estado | Evidencia / nota |
|---|---|---|---|
| HU-02 CA-01 | Lectura con timestamp y unidades exactas | **Sí** (con desvío deliberado) | Panel "Valores sensados · nodo testigo", 10 métricas, antigüedad (`SensadoCard.tsx`). La HU pide radiación en W/m² o índice UV; el sensor es un LDR y se muestra **Luminosidad en %** (decisión D-05). |
| HU-02 CA-02 | Histórico: 24 h, 7 d, 30/60 d y personalizado ≤ 90 d | **Parcial** | Hay 24 h / 7 d / 30 d. **La serie es sintética también en modo `http`**: se genera con `series(seed, valorActual, …)` (`src/data/selectors.ts:81-102`); el backend no persiste series (non-goal de `move-sensado-macrozona/design.md`). |
| HU-02 CA-03 | Ámbar "Señal intermitente / Desactualizado" a las ~2 h | **Parcial** | Un único booleano `stale` (`LecturaZona.stale`); no hay escalón intermedio. |
| HU-02 CA-04 | "Fuera de servicio" a las ~24 h, anula actuación, alerta | **Parcial** | El motor anula (`StaleSensorRule`) y alerta; la UI no distingue el escalón. |
| HU-02 CA-05 | Valor fuera de límites en rojo | **Sí** | Color por estado. Las 5 métricas de suelo se pintan pero no cambian el estado (D-03) y dicen "rango provisional" (`SensadoChart.tsx:32`). |
| Spec `production-map` | Grilla entera sin scroll, celdas cuadradas, resumen que hace de leyenda, lista "a revisar" sin truncar | **Sí** | `SectorGrid.tsx`, `ZoneSummary.tsx`, `ProblemsList.tsx`. |
| HU-08 / `reglas_v2 §7` | Estado del plan de rustificación de la MZ (día, etapa) e iniciar/cancelar/resetear | **No** | Ver §3.10. |
| HU-19 CA-01 (alcance MZ) | Bloquear toda la MZ | **No** | Ver §3.1. |

### 2.4 Sector (`/sector/:id`) — `src/features/sector/`

| HU / CA | Qué pide | Estado | Evidencia / nota |
|---|---|---|---|
| HU-05 CA-02 | Ver la foto cenital original del diagnóstico | **Sí** | `DiagnosisCard.tsx` + `CapturaModal.tsx` (se cierra con Esc, botón o clic afuera). |
| HU-04 CA-03 / HU-07 CA-02 | "No concluyente" → **solicitar validación visual humana en la plataforma** | **No** | Sólo existe el estado "No concluyente"; no hay cola de revisión ni acción. Ver §3.6. |
| HU-14 CA-03 (actuadores) | Estado de electroválvula, bomba, mediasombra | **Sí** (sólo lectura) | `ActuatorsCard.tsx`: Regando / En cola / Cerrada; Dosificando; "Apertura N %". |
| HU-12 CA-01/02 | Antes/ahora, latencia, delta, veredicto | **Parcial (falso)** | `PostActionCard` se alimenta de `buildSectorDetail` (`src/data/mock/sectorDetail.ts:66-80`), que **inventa** el "antes" (`ahora < 45 ? ahora + 11 : ahora - 9`) también en modo `http`. El seguimiento real existe en el Historial (backend `HistorialService.evaluarSeguimiento`). |
| HU-11 CA-01 (por sector) | Cadena lectura → decisión → acción del sector | **Parcial** | Últimas 5 acciones reales (`SectorPage.tsx`, `useHistory`), resumidas como "decisión · acción". |
| HU-19 CA-01..04 | Bloquear, forzar actuador, aviso de límite excedido, reanudar | **No** | Ver §3.1 y §3.2. |
| Pedido de captura | "Pedir una foto nueva" del sector | **No** | Ver §3.7. |
| `redesign-estado-vivero` | Breadcrumb, explicador de jerarquía, dibujo físico, "¿Qué estoy viendo?" | **Sí** | `JerarquiaExplainer`, `SectorDiagram`, `AlcanceDiagnostico`. |
| Inspector del motor | "Ver última evaluación del motor" | **Sí** | Link a `/reglas?tab=inspector&sector=…`. |

### 2.5 Diagnósticos de IA (`/diagnosticos`) — `src/features/diagnostics/`

| HU / CA | Qué pide | Estado | Evidencia / nota |
|---|---|---|---|
| HU-05 CA-01 | Patología, sector, confianza, severidad | **Sí** | `DiagCard.tsx` con miniatura real y carga diferida. |
| HU-05 CA-02 | Foto original al seleccionar | **Sí** | `PhotoModal.tsx`. |
| HU-05 CA-03 | Filtrar y **agrupar** por tipo, severidad, **rango de fechas, macro-zona y sector** | **Parcial** | Sólo tipo y severidad; y la lista de tipos no coincide con la taxonomía del backend (falta "Daño biótico", "Ácaro", "Sano"). |
| HU-04 CA-03 | Validación humana de no concluyentes | **No** | Ver §3.6. |
| Badge del menú | Cantidad de "diagnósticos activos" (spec `app-shell`) | **Parcial** | Cuenta todos (`stats.diagCount`), no los que piden atención. |

### 2.6 Historial (`/historial`) — `src/features/historial/`

| HU / CA | Qué pide | Estado | Evidencia / nota |
|---|---|---|---|
| HU-11 CA-01 | Cadena completa lectura/diagnóstico → decisión → acción | **Sí** | Más chips de volumen, duración, regla y nivel de alerta (`EventoMeta.tsx`) y un grafo del motor. |
| HU-11 CA-02 | Filtros por sector/MZ, tipo (Riego, Insumo, Mediasombra) y fechas | **Sí** | + resultado y tipo "Alerta" (`HistorialFilters.tsx`). |
| HU-11 CA-03 | Inalterable desde la UI | **Sí** | Sin editar ni borrar. |
| HU-12 CA-01/02 | Seguimiento post-acción con veredicto | **Sí** | Datos reales del backend (`ActionRecord.evo`). |
| HU-12 CA-03 | "Sin efectividad" → bloquea repetición + CRITICAL + pide revisión física | **Parcial** | El motor bloquea en parte (S-06 parcial); **no hay dónde registrar la revisión** (`reglas_v2.md §13`). |
| HU-16 CA-01..04 | Exportar PDF/CSV con nombre normalizado y bloqueo si no hay resultados | **No** | Ver §3.8. |
| HU-19 CA-02/03 | Intervención manual registrada ("Manual con límite excedido") | **No** | No existen intervenciones manuales. |
| Ojo | El grafo del historial **infiere** el recorrido por regex sobre el texto (`src/components/DAGViewer/RuleGraph.tsx:125-140`); el del Inspector usa la **traza real**. Dos grafos del mismo motor con distinta fidelidad. |

### 2.7 Configuración (`/configuracion`) — `src/features/configuracion/`

| HU / CA | Qué pide | Estado | Evidencia / nota |
|---|---|---|---|
| HU-15 CA-01 | Valores de fábrica precargados | **Sí** | "Restablecer valores de fábrica". |
| HU-15 CA-02 | Guardar, auditar usuario/timestamp | **Parcial** | Muestra "Última edición: …", pero sin login el usuario no es real. |
| HU-15 CA-03 | Bloquear valores fuera del rango fisiológico | **Sí** | `src/lib/configValidation.ts`. |
| HU-15 CA-04 | Tiempo máximo de apertura y volumen máximo diario de riego | **Parcial** | Se mudaron al catálogo del Motor de reglas (`LimitesActuadoresForm.tsx`, comentario inicial); el volumen máximo diario "se dio de baja". |
| HU-15 CA-05 | Dosis máx por sector cada 24 h que **bloquee** la sobredosis | **Parcial / contradictorio** | El campo existe pero dice "**No intervienen en decisiones del motor: sólo se informan en los textos**" (`LimitesActuadoresForm.tsx`). La sección se llama "Topes informativos". |
| HU-15 CA-06 | Plan de mediasombra: días y % de apertura por etapa | **Sí** (modelo viejo) | `RustificacionPlanForm.tsx`. `reglas_v2 §7` propone horarios por franja y por MZ. |
| HU-15 CA-07 | Latencia y delta de efectividad | **Sí** | `SeguimientoForm.tsx`. |
| `add-configurable-intervals` | Intervalos de sensado e inferencia | **Sí** | "Frecuencia Operativa". |
| `add-pasada-riel` | Interruptor "Demo Expo" | **Sí** | `DemoExpoSwitch.tsx` (se guarda al instante, aparte del formulario). |

### 2.8 Motor de reglas (`/reglas`) — `src/features/reglas/`

| Origen | Qué pide | Estado | Nota |
|---|---|---|---|
| HU-15 + `add-catalogo-umbrales-reglas` | Catálogo único de parámetros por regla, búsqueda, "sólo modificados", edición en lote validada | **Sí** | Pestaña Parámetros. |
| HU-11 (explicabilidad) + `visualize-rule-dag` | Por qué el motor decidió X en un sector | **Sí** | Pestaña Inspector, con "recibido vs. umbral". Lenguaje muy técnico (ver §5.3). |

### 2.9 Hardware (`/hardware`) — `src/features/hardware/`

| HU / CA | Qué pide | Estado | Nota |
|---|---|---|---|
| HU-21 CA-01 | Por dispositivo: tipo, ubicación, batería, señal, último update, estado | **Sí** | Sin la cámara ni el riel (ver §3.12). |
| HU-21 CA-02 | "Batería baja" + alerta WARNING | **Parcial** | Marca visual. `reglas_v2 §1.2`: "el nodo reporta siempre 100 %: no hay regla de batería". |
| HU-21 CA-03 | "Fuera de servicio" en el panel y en el mapa | **Parcial** | En el mapa se ve el sector "sin señal", no el equipo. |
| HU-21 CA-04 | Avería física asociada al sector | **Parcial** | Se muestra `falla`, pero no hay fuente: el tópico de errores del ESP32 (S-05) no existe. |
| HU-21 CA-05 | Recambio reutilizando el registro | **Sí** | `AltaHardwareForm.tsx` modo recambio. |
| HU-18 CA-02/03/04 | Alta, duplicados, sectores incompletos | **Sí** | `SectoresIncompletos.tsx`. |

### 2.10 Topología (`/topologia`) — `src/features/topologia/`

| HU-18 CA-01 | Generar la grilla, preview, disposición por fila, confirmación destructiva | **Sí** | `TopologiaPage.tsx`, `TopologiaPreview.tsx`. |
|---|---|---|---|

### 2.11 Demo Expo (`/demo-expo`) — `src/features/demo-expo/`

| Origen | Qué pide | Estado | Nota |
|---|---|---|---|
| HU-04 CA-01 + `add-pasada-riel` | Mover el riel, capturar 2 sectores, ver foto y diagnóstico en vivo | **Sí** | Disparo manual. En `mock` simula la pasada. |

### 2.12 HU sin pantalla propia pero con efectos que la UI tiene que mostrar

| HU | Lo que debería verse | Hoy |
|---|---|---|
| HU-03 (captura periódica) | Antigüedad de la lectura, buffer offline | Se ve "hace N min" en la MZ. |
| HU-06 (riego autónomo) | Regando / en cola / pospuesto / abortado y por qué | Actuador por sector + historial. "Riego abortado" sólo en historial. "Falla hidráulica" no existe (sin caudalímetro). |
| HU-07 (dosificación) | Dosificando, límite diario alcanzado, **stock del tanque** (`reglas_v2 §8.2`) | Actuador + historial; stock no existe. |
| HU-13 (offline) | Si el sistema está decidiendo sin internet y cuándo sincronizó | Indicador falso en el sidebar. |

### 2.13 Resumen por HU

| HU | Tema | Estado UI | | HU | Tema | Estado UI |
|---|---|---|---|---|---|---|
| 01 | Login por rol | No | | 12 | Evolución post-acción | Parcial (real en Historial, inventado en Sector) |
| 02 | Métricas sustrato/ambiente | Parcial (histórico sintético) | | 13 | Offline | No |
| 03 | Captura periódica | Sí (indirecto) | | 14 | Mapa de sectores | Sí |
| 04 | Análisis IA | Parcial (sin validación humana) | | 15 | Configuración | Parcial (dosis informativa, partida en 2 pantallas) |
| 05 | Consulta de diagnósticos | Parcial (filtros) | | 16 | Exportación | No |
| 06 | Riego autónomo | Parcial (visible) | | 17 | Multidispositivo | No |
| 07 | Dosificación | Parcial (visible, sin stock) | | 18 | Mapeo y hardware | Sí |
| 08 | Mediasombra / rustificación | Parcial (no se puede iniciar ni pausar) | | 19 | Bloqueo / forzado manual | **No** |
| 09 | Pronóstico | Sí (degradado mejorable) | | 20 | Roles y permisos | No |
| 10 | Alertas | Parcial (fuente equivocada, sin "leída", sin WhatsApp) | | 21 | Estado técnico del hardware | Parcial (sin cámara/riel, sin fallas) |
| 11 | Historial | Sí (sin exportar) | | | | |

---

## 3. Funciones previstas sin pantalla o escondidas

Para cada una: qué dice la fuente, cómo está el backend, qué hay en la UI y dónde debería vivir.

### 3.1 Bloqueo manual y reanudación (HU-19 CA-01/04 · S-01)

- **Spec.** `openspec/specs/bloqueo-manual/spec.md`: bloqueo por sector o por MZ (sector `null`), con
  usuario y motivo; desactivar por id; sin edición; registro en historial de activación y reactivación.
  `reglas_v2.md §4 S-01`: con bloqueo de MZ la mediasombra queda quieta; `INFO` al activar y al
  reanudar; reanudar sin acciones retroactivas. `§13`: "Bloquear / reanudar por sector y por MZ. **Hoy
  no existe.**"
- **Backend.** `ManualLockRule` + entidad existen; **no hay controller** (`finish-release-2/tasks.md`,
  todo sin marcar). `diferencias-motor-reglas-vs-reglas-v2.md §1`: "no existe ningún camino que cree un
  bloqueo manual".
- **UI.** Nada.
- **Dónde debería vivir.** (a) **Sector**: acción primaria "Bloquear este sector" con motivo, visible
  y grande para el operario; si está bloqueado, banner persistente arriba con quién/cuándo/por qué y
  "Reanudar". (b) **Macro-zona**: "Bloquear toda la MZ". (c) **Shell**: indicador global "N bloqueos
  activos" (los bloqueos olvidados son el riesgo operativo). (d) Celdas de la grilla con una marca
  de bloqueado (candado), distinta del color de salud. (e) Historial: tipo "Bloqueo manual".
- **Cuidado.** Requiere rol (Operario/Agrónomo); HU-17 CA-02 pide deshabilitarlo sin conexión.

### 3.2 Forzado manual de actuadores (HU-19 CA-02/03) y pausa manual de mediasombra (HU-08 CA-02)

- **Spec.** Forzar válvula, bomba o mediasombra; ejecutar ya; registrar usuario/actuador; si excede un
  límite, advertir y pedir confirmación "bajo su responsabilidad" → historial "Manual con límite
  excedido". Pausa de la mediasombra hasta autorizar la reanudación.
- **Backend.** No existe.
- **UI.** `ActuatorsCard.tsx` es sólo lectura.
- **Dónde.** En la tarjeta de actuadores del Sector (y de la MZ si la mediasombra sube a la zona,
  `reglas_v2 §6`), como acción secundaria con diálogo de confirmación de dos pasos. Nunca un toggle
  suelto.

### 3.3 Alertas inteligentes (HU-10)

- **Spec.** `openspec/specs/alertas-inteligentes/spec.md`: listado reciente (24 h configurable), por
  sector/zona/severidad, marcar como leída, badge con el color de la severidad más alta. HU-10 CA-02:
  push para WARNING/CRITICAL (WhatsApp, `Notificaciones-WhatsApp-Cloud-API.md`).
- **Backend.** Las alertas del motor **ya se persisten** como eventos `Alerta` del historial con nivel
  (`HistorialService.registrarAlerta`); no hay `/api/alertas` ni "leída".
- **UI.** La campana muestra otra cosa (sectores prioritarios). Las alertas reales sólo se ven
  filtrando el Historial por tipo "Alerta".
- **Dónde.** (a) Campana = atajo a las no atendidas, con nivel en castellano y link al sector/MZ.
  (b) **Bandeja de alertas** como sección propia (es la puerta de entrada desde WhatsApp): filtros por
  nivel/MZ/estado, "Marcar como atendida", agrupación por evento de zona (una alerta de MZ no son 100
  alertas de sector). (c) En Configuración/Preferencias: destinatarios y consentimiento (opt-in) de
  WhatsApp.

### 3.4 Pronóstico climático (HU-09)

- **Spec.** `openspec/specs/pronostico-climatico/spec.md`: el pronóstico pospone riegos y protege por UV;
  si la API cae, se opera degradado.
- **Backend.** Implementado (Open-Meteo, cache, reintentos).
- **UI.** Topbar + "Clima y riesgo". Escondido: **el efecto** del clima en las decisiones ("riego
  pospuesto en 23 sectores por lluvia probable"); el estado degradado se pinta como 0 °C.
- **Dónde.** Mantener la tarjeta en el Panel general, sumando "qué cambió el sistema por el clima
  hoy" (derivado del historial: `Pospuesta`). En degradado: "Pronóstico no disponible — el sistema
  decide sólo con los sensores", sin números.

### 3.5 Plan de rustificación por macro-zona (HU-08 · `reglas_v2 §7`)

Ver 3.10 (se agrupa con el resto de acciones del agrónomo).

### 3.6 Validación humana de diagnósticos (HU-04 CA-03 · HU-07 CA-02)

- **Spec.** "No concluyente" → aborta la acción y **solicita validación visual humana en la
  plataforma**.
- **Backend.** El estado "No concluyente" existe; no hay entidad ni endpoint de validación.
- **UI.** Nada; el filtro "No concluyente" es lo único.
- **Dónde.** Diagnósticos de IA, pestaña o filtro por defecto **"Para revisar"** (y que el badge del
  menú cuente esos, no el total). Acción: ver la foto grande y confirmar / corregir la clase.
- **Cuidado (decisión fuerte).** `CLAUDE.md §6.1`: "La tabla `diagnostico` no tiene columna de origen"
  y el alta es un camino único. Una validación humana no puede convertirse en una "marca de origen"
  ni en un alta paralela sin pasar por OpenSpec. Se puede modelar como un diagnóstico nuevo sobre la
  misma captura por el mismo `POST /api/diagnosticos`, o como una entidad aparte de revisión; hay que
  decidirlo antes de dibujar la pantalla.

### 3.7 Pedido manual de captura ("pedir una foto nueva")

- **Spec / código.** `POST /api/capturas/ordenes` existe (`openspec/specs/captura-imagenes/spec.md`) y lo
  usan el simulador y el planificador de pasadas. `src/data/repository.ts` (comentario inicial) declara
  a propósito que las órdenes **no** están en `DataRepository` porque "ninguna vista las consume".
- **UI.** Sólo la pasada de Demo Expo (2 sectores fijos).
- **Dónde.** Sector ("Pedir foto nueva"), y en la cola "Para revisar" de Diagnósticos (re-capturar un
  no concluyente). Requiere agregar el método al repositorio de forma explícita y **no** tocar
  `/api/camara/v1/**` (contrato del dispositivo).

### 3.8 Exportación de reportes (HU-16)

- **Spec.** PDF (auditoría) o CSV/Excel (contable); contenido mínimo (timestamp, sector, condición,
  acción con volumen/duración, delta); nombre `Yerbanalytics_[Tipo]_[Desde]-[Hasta].[ext]`; botón
  bloqueado con "No hay registros para los criterios seleccionados".
- **Backend / UI.** No existe. Planificado R4 sprint 1 (17/10–31/10).
- **Dónde.** Botón "Exportar" en Historial que reutiliza los filtros vigentes; idem en Diagnósticos
  (reporte "Sanidad"). No hace falta una sección "Reportes" aparte salvo que aparezcan reportes de
  consumo de insumos (stock, §3.11).

### 3.9 Login, usuarios y roles (HU-01 · HU-20)

- **No existe nada.** Llega en R4. **Dónde:** pantalla de login; menú de usuario en la topbar (hoy un
  nombre fijo); sección **Usuarios y roles** en Administración. El sidebar debe filtrar ítems por rol
  (ver §6.3).

### 3.10 Acciones del agrónomo sobre la rustificación (HU-08 · HU-15 CA-06 · `reglas_v2 §7`)

- **Spec.** Iniciar / cancelar / resetear el plan **por MZ**; fin automático al día 45 con `INFO`
  "Plan finalizado: plantines listos para trasplante"; sólo el rol Ingeniero Agrónomo (`§13`).
- **Backend.** Hoy el plan cuenta días desde una property global vacía por defecto
  (`sowing-date-iso`), así que en la práctica **no corre**.
- **UI.** Sólo la plantilla del plan en Configuración.
- **Dónde.** En la Macro-zona: tarjeta "Rustificación" con estado (sin plan / día 12 de 45, etapa 2 /
  finalizado), próxima franja y acciones del agrónomo. La plantilla queda en la sección de umbrales.
  En el Panel general, la parcela de la MZ podría llevar un chip "Rustificando · día 12".

### 3.11 Registros del operario: revisión tras "Sin efectividad", reparaciones y recarga de tanques

- **Spec.** `reglas_v2.md §13 Front`: "Registrar la **revisión humana** (S-06), las **reparaciones**
  (S-05) y la **recarga de tanques** (§8.2)". §8.2: stock con aviso al 20 % y CRITICAL con tanque vacío.
- **Backend.** No existe (salvo el recambio de hardware).
- **Dónde.** Revisión: en el Sector/MZ bloqueados por S-06 ("Registrar revisión y reanudar").
  Reparación: en Hardware, junto al recambio ("Marcar como reparado"). Stock: nueva vista
  **Insumos** (dos tanques: fertilizante y fitosanitario) con "Registrar recarga", y un indicador en
  el Panel general cuando hay stock bajo.

### 3.12 Dispositivos de captura (cámara del riel) y estado del riel

- **Spec.** `openspec/specs/camara-dispositivos/spec.md`: vinculación por código de un solo uso,
  heartbeat con estado operativo (operativo / intermitente / fuera de servicio), listado de
  dispositivos (`GET /api/camara/dispositivos`), baja.
- **UI.** **Sólo en el simulador** (`openspec/specs/simulacion-captura/spec.md`, "Visibilidad del
  dispositivo de cámara"; `Desarrollo/camara-android/README.md:164-175`: "Simulador — de acá sale el
  código de vinculación"). El Hardware del dashboard sólo conoce nodo testigo, electroválvula, bomba y
  mediasombra (`Dispositivo.tipo` en `src/types/domain.ts`).
- **Por qué importa.** El simulador tiene que poder borrarse "sin tocar nada" (`CLAUDE.md §6.2`). Si se
  borra hoy, **nadie puede vincular ni dar de baja el celular del riel en producción**. Es una
  función del sistema escondida en una herramienta de prueba.
- **Dónde.** Hardware → pestaña "Cámara y riel": estado del celular (último heartbeat, capturas
  OK/fallidas), "Generar código de vinculación", "Dar de baja"; estado del riel (último evento
  `nursery/rail/event`). Usa endpoints públicos, no del simulador.

### 3.13 Pasada de captura diaria

- **Spec.** `reglas_v2.md §1.3`: una pasada por día a las 09:00; `WARNING` "Pasada de captura
  incompleta"; diagnóstico vigente si tiene ≤ 48 h.
- **UI.** Sólo Demo Expo, manual, 2 sectores. La "vigencia" del diagnóstico no se muestra.
- **Dónde.** Panel general (una línea: "Última pasada: hoy 09:00 · 598/600 sectores") y en el Sector
  ("Foto de hace 3 días — vencida, no dispara acciones"). Demo Expo sigue siendo la vista de
  presentación.

### 3.14 Estado de conexión, sincronización y modo sin conexión (HU-13 · HU-17 CA-02)

- **UI.** Indicador inventado en el sidebar.
- **Dónde.** Shell: indicador real con la hora del último dato recibido y de la última evaluación;
  banner rojo de "Sin conexión" que deshabilita las acciones de §3.1/§3.2 y la edición de umbrales.

### 3.15 Histórico real de métricas y DPV

- **Histórico.** HU-02 CA-02 pide series reales con 60 d y rango personalizado ≤ 90 d; hoy son
  sintéticas (§2.3). Hasta que el backend persista series, la UI **no debería** mostrar el gráfico en
  modo `http` como si fuera real (o rotularlo explícitamente como ilustrativo).
- **DPV.** `reglas_v2.md §1.4` y `§13`: "Mostrar el DPV junto con las otras métricas". No existe. Va en
  el panel de sensado de la MZ.

### 3.16 Seguimiento post-acción en el Sector

- Existe en Historial con datos reales; en el Sector se inventa (§2.4). Debe leerse de los eventos del
  historial del sector (último evento con `evo`).

---

## 4. Decisiones de diseño previas que hay que respetar

Cada una con su porqué. Revertir cualquiera **pasa por OpenSpec** (`CLAUDE.md §3`).

### 4.1 Decisiones vigentes

| # | Decisión | Por qué | Fuente | Qué implica para el rediseño |
|---|---|---|---|---|
| D-01 | **La macro-zona no está en el menú.** Se entra eligiendo una MZ en el Panel general; adentro no hay selector de zonas, sólo se vuelve; `/mapa` sin zona redirige a `/`. | Sin una zona elegida la vista no tiene qué mostrar; es un nivel de detalle, no un destino. | `openspec/specs/app-shell/spec.md`; `openspec/specs/sensado-macrozona/spec.md` ("Acceso exclusivo desde el panel general"); comentario en `src/components/layout/Sidebar.tsx:17-19`; `move-sensado-macrozona/proposal.md`. | No poner "Mapa de producción" en el sidebar (el diseño original sí lo tenía, `Yerbanalytics.dc.html:48`, y se sacó a propósito). Si se quiere un buscador "Ir a sector/MZ" o cambiar de MZ desde adentro, es un cambio de spec. |
| D-02 | **El sensado es de la macro-zona, no del sector.** El sector no muestra métricas; vuelve a su MZ para verlas. | Hay **un** nodo testigo por MZ: los 100 sectores comparten físicamente la lectura. Mostrar métricas por sector "sugiere —falsamente— que cada sector tiene instrumentación propia" y la vista "va a mentir sobre el origen del dato". Lo que distingue a un sector es su diagnóstico de IA. | `move-sensado-macrozona/proposal.md` y `design.md` (D1, D2, D8); `openspec/specs/sector-detail/spec.md` ("muestra sólo lo que es propio del sector"); `openspec/specs/data-layer/spec.md`; `CLAUDE.md §2`. | Ninguna tarjeta de métricas en el Sector; sí un vínculo claro a "las condiciones de su macro-zona". El nodo testigo se dibuja **fuera** del sector (`redesign-estado-vivero/design.md` D5). Consecuencia a diseñar: los estados por sensado son de **zona** aunque se pinten por sector (§6.1). |
| D-03 | **Las 5 métricas de la sonda de suelo son informativas** (`afectaEstado: false`): temperatura del sustrato, pH, N, P, K. Se muestran y colorean, no cambian el estado. | Sus rangos son **provisionales**, no validados con el vivero; con 10 métricas evaluadas "el mapa podría ponerse rojo de golpe" y disparar actuaciones innecesarias. | `move-sensado-macrozona/design.md` (Risks, Open Questions); `openspec/specs/sensado-macrozona/spec.md` ("Métricas nuevas informativas…"); `CLAUDE.md §2`. | Diferenciarlas visualmente (subgrupo "Nutrición del sustrato", etiqueta "rango provisional"); nunca usarlas para KPIs de salud. |
| D-04 | **Un único modo de datos: `VITE_DATA_SOURCE`.** `http` = sistema real; `mock` = demo ilustrativa. Rige para todas las secciones; nunca conviven datos mock y reales; cambiar exige reiniciar; ninguna vista le pregunta el modo al backend. | Antes el "modo" vivía en el backend y producía pantallas a medias (mapa en demo, hardware real). Sacarlo era sacar el simulador del sistema. | `CLAUDE.md §4`; `extract-simulador-standalone/proposal.md`; `Desarrollo/frontend/README.md` ("Los dos modos"). | Nada de "toggle demo/real" en la UI. Y lo inverso: en `http` no debería aparecer **ningún** dato generado (hoy se violan dos, §4.2). |
| D-05 | **"Luminosidad (%)" no es "Radiación UV".** La clave `uv` del contrato MQTT es % de un LDR. El índice UV real viene del **pronóstico**. | Honestidad respecto del hardware; la clave se conserva por compatibilidad con el firmware. | `move-sensado-macrozona/design.md` D5; `CLAUDE.md §2`. | Rotular "Luminosidad (sensor)" e "Índice UV (pronóstico)" como cosas distintas. |
| D-06 | **CE se muestra en dS/m** (llega en µS/cm y se normaliza en la ingesta). | Una sola conversión, en un solo lugar; evita errores ×1000 en el motor. | `move-sensado-macrozona/design.md` D4. | La UI no convierte unidades. |
| D-07 | **"Plano del vivero" y "Estado del vivero" son dos bloques distintos.** Plano = vista espacial de las MZ como parcelas con su peor estado y un resumen en una frase + "Cómo leer esta pantalla"; tocar una parcela **desplaza** hasta su bloque. Estado = un bloque por MZ con resumen en palabras, contadores sin abreviaturas, chip del nodo testigo y grilla de sectores; celda → Sector; "Ver detalle de la zona" → `/mapa?zona=`. | "Quien no conoce el dominio no entiende qué mira" ("0 ok · 0 alerta · 1 s/s"); hacía falta explicar la jerarquía Vivero → MZ → Sector → Bandeja; y había un bug de "cuadro gris gigante" con 1 sector por fila. | `openspec/changes/redesign-estado-vivero/proposal.md`, `design.md` (D2–D4), `specs/dashboard/spec.md`. | Se puede rediseñar la forma, pero conservar: el nivel "plano" (orientación espacial), el resumen en lenguaje natural, los contadores sin abreviar, el peor-estado-por-zona y la navegación conservadora. Celdas entre 10 y 22 px, centradas (D3). |
| D-08 | **El dibujo del sector no señala ningún tubete**; los 100 se pintan igual. | El diagnóstico sale de **una** foto del sector y se aplica al sector entero; el dato de qué plantín se fotografió no existe ni va a existir. | `redesign-estado-vivero/design.md` D5, D6; `proposal.md` (Fuera de alcance). | No inventar "plantín afectado" ni zoom a un tubete. |
| D-09 | **La disposición de la grilla es configurable** (`macroZonasPorFila`, `sectoresPorFila`) y la respetan Panel general y Macro-zona. | El vivero piloto es 6 × 100, pero la instalación de referencia es 10 × 100 (`1_ResumenPreliminar.md §3`); la grilla tiene que parecerse al vivero real. | `archive/2026-06-30-add-topologia-visual/proposal.md`; specs `dashboard`, `production-map`, `gestion-topologia`. | Ningún layout fijo 3 × 2 o 10 × 10. Probar con 10 MZ y con 1 sector por fila. |
| D-10 | **En la Macro-zona la grilla entra completa sin scroll, con celdas cuadradas; la lista "Sectores a revisar" no se trunca** (scrollea en su tarjeta). | "Recortarla en silencio escondería sectores que requieren atención." | `openspec/specs/production-map/spec.md`. | Aplicar el mismo criterio a "Atención prioritaria" del Panel (hoy truncada a 7). |
| D-11 | **El inspector de métricas de la MZ no tapa los botones de las métricas** y empieza cerrado. | El usuario quiere saltar entre métricas sin cerrar; un modal "rompe la lectura comparativa". | `openspec/specs/sensado-macrozona/spec.md`; `move-sensado-macrozona/design.md` D6. | Mantener el patrón "lista de métricas + detalle lateral". |
| D-12 | **El historial es inalterable** desde la UI. | Respaldo para certificaciones y auditorías. | HU-11 CA-03; `openspec/specs/historial-trazabilidad/spec.md`. | Ningún editar/borrar; las correcciones son eventos nuevos. |
| D-13 | **Cada umbral se edita en un solo lugar.** `umbral_metrica` (Configuración) sólo define estado y color; los umbrales que compara una regla viven en el **catálogo de parámetros** (Motor de reglas). | "Para no tener dos lugares que editen lo mismo"; antes los umbrales estaban en cinco lugares distintos. | `CLAUDE.md §6`; `add-catalogo-umbrales-reglas/proposal.md`; comentario en `LimitesActuadoresForm.tsx`. | Se pueden reagrupar las pantallas (§6), pero sin duplicar un parámetro en dos formularios. |
| D-14 | **Demo Expo: el interruptor sólo oculta la pestaña.** Las pasadas son capacidad del sistema, no un "modo demo"; la ruta existe siempre. | El backend no tiene modos; una preferencia de visualización no debe acoplarse al motor. | `add-pasada-riel/proposal.md` y `design.md §2.7`. | Si se integra la pasada a la operación real, Demo Expo queda como una vista de presentación encima, no como lógica aparte. |
| D-15 | **Diagnóstico: camino único y sin marca de origen.** Manual y del modelo son la misma fila; los diagnósticos registrados y los derivados del estado del sector conviven "sin que el usuario deba distinguirlos". | Que lo que se ensaya a mano sea literalmente lo que corre solo. | `CLAUDE.md §6, §6.1`; `DiagnosticoService.java` (javadoc); `openspec/specs/diagnostico-consulta/spec.md`. | No mostrar "cargado a mano" vs. "del modelo". Ver la tensión con la validación humana (§3.6). |
| D-16 | **El simulador no existe para el dashboard.** Ni sección, ni ruta, ni endpoint propio. | Borrar `Desarrollo/simulador/` no debe requerir tocar nada. | `CLAUDE.md §6.2`; `openspec/specs/simulacion-captura/spec.md`. | No traer al dashboard la carga manual de diagnósticos ni el envío de telemetría. Sí traer lo que es del sistema y hoy sólo está ahí (vinculación de cámara, §3.12). |
| D-17 | **Las órdenes de captura y la cámara no están en `DataRepository`** a propósito; `/api/camara/v1/**` es contrato versionado del dispositivo. | Superficie pública para el planificador y la inferencia, no para vistas. | `src/data/repository.ts` (comentario); `CLAUDE.md §6.1`. | Sumar "pedir foto" o "dispositivos de captura" es una decisión explícita (agregar al repo vía endpoints públicos), nunca usar el contrato del dispositivo. |
| D-18 | **Sistema de diseño:** tokens en `src/styles/tokens.css`, CSS Modules para lo estático e inline sólo para valores de datos; Space Grotesk (títulos/números) + Hanken Grotesk (cuerpo). | Fidelidad al diseño y mantenibilidad. | `CLAUDE.md §4`; `add-monitoring-frontend/design.md` §2. | El rediseño puede cambiar la estética, pero respetando este mecanismo. |
| D-19 | **Volver del sector lleva a su MZ con la zona ya elegida** (breadcrumb Vivero / MZ / Sector). | La MZ es donde está la explicación ambiental del estado del sector. | `openspec/specs/sensado-macrozona/spec.md` ("Vuelta del sector a su macro-zona"). | Mantener el breadcrumb jerárquico como navegación principal de detalle. |
| D-20 | **Regenerar la topología es destructivo y pide confirmación; cambiar la disposición no.** | Regenerar descarta dispositivos e historial. | `openspec/specs/gestion-topologia/spec.md`. | Separar visualmente "forma de la grilla" de "cómo se dibuja". |

### 4.2 Lugares donde hoy se violan esas decisiones (deuda que el rediseño debería cerrar, no copiar)

1. **Series históricas sintéticas en modo `http`** (`src/data/selectors.ts:81-102`) → choca con D-04.
2. **Seguimiento post-acción inventado en el Sector** (`src/data/mock/sectorDetail.ts:66-80`) → D-04.
3. **"Sistema en línea · Edge activo · sincronizado · hace 40 segundos"** escrito a mano (`Sidebar.tsx`).
4. **Usuario fijo** "Mariano Duarte" (`Topbar.tsx`).
5. **KPI "Hardware fuera de servicio" que cuenta sectores** (`KpiRow.tsx` + `NurseryService.java:237`).
6. **"Atención prioritaria" truncada a 7** en el backend → criterio de D-10.
7. **Campana alimentada por sectores prioritarios** en vez de por las alertas registradas.
8. **Umbral 85 % hardcodeado** en textos y en `concluyente` → D-13.
9. **Clima degradado pintado como "0 °C · UV 0"**.
10. **"Dosis máx." presentada como límite pero "informativa"** → contradice HU-15 CA-05 y HU-07 CA-03.
11. **Opciones del filtro de Diagnósticos desalineadas** con la taxonomía aceptada por el backend.

---

## 5. Vocabulario del dominio

### 5.1 Glosario canónico que la UI debería usar siempre igual

| Término | Definición para el usuario | Evitar / notas |
|---|---|---|
| **Vivero** | Todo el establecimiento (Vivero San Ignacio). | — |
| **Macro-zona (MZ-N)** | Área de ~100 sectores con **un** nodo testigo cuya lectura vale para toda el área. | No "zona" a secas en títulos; no "Sector norte/centro/sur" como subtítulo (choca con *sector*, ver 5.2). |
| **Sector (MZ-3-077)** | 100 plantines en 4 bandejas, con un microaspersor. Unidad de riego, de dosificación y de foto. | No "parcela" ni "lote". |
| **Bandeja** | 25 tubetes (5 × 5). | — |
| **Plantín** / **tubete** | La planta / su contenedor. | "Tubete testigo" (`1_ResumenPreliminar.md`) confunde: usar *nodo testigo*. |
| **Nodo testigo** | El equipo sensor de la MZ. | Hoy conviven "nodo testigo", "sensor testigo" (`PlanoVivero.tsx`) y "tubete testigo". Elegir **nodo testigo**. |
| **Estados de salud** | **Saludable · En observación · Crítico · Sin señal** (sector cuya MZ no tiene lectura vigente). | Ver choque "Sin señal" vs "Fuera de servicio" en 5.2. No "ok", "s/s", "Observación" a secas. |
| **Estados de equipo** | **Operativo · Señal intermitente · Fuera de servicio · Averiado · Batería baja**. | Reservar "Fuera de servicio" para equipos. |
| **Métricas** | Humedad de sustrato · Humedad ambiental · Temperatura del aire · Temperatura del sustrato · Nutrientes (CE, dS/m) · pH del sustrato · Nitrógeno · Fósforo · Potasio (mg/kg) · Luminosidad (%) · (futuro) DPV (kPa). | Nunca "Radiación UV" para la luminosidad. |
| **Diagnóstico de IA** | Lo que el modelo concluye de la foto del sector. Clases: **Sano · Clorosis · Estrés solar · Daño biótico** (con subtipos Plaga foliar / Ácaro / Daño fúngico si el modelo los da) · **No concluyente** · **Sin diagnóstico**. | `clases-enfermedades-y-terminologia-misiones.md §0`: "no son enfermedades, son estados del plantín". No "Hongos" (spec `diagnostico-consulta`). |
| **Confianza** | % de seguridad del modelo. Por debajo del **umbral de confianza** (parámetro) el diagnóstico no dispara acciones. | No mostrarla como si fuera severidad. |
| **Severidad** | Gravedad del daño: **Leve · Moderada · Alta** (`reglas_v2 §1.3`). | Hoy derivada de la confianza (§0). |
| **Vigente** | Diagnóstico de la última foto, ≤ 48 h, sobre el umbral. | Concepto nuevo de v2, hoy invisible. |
| **Actuadores** | **Electroválvula** (riego) · **Bomba dosificadora/peristáltica** (insumo) · **Mediasombra** (abierta = sol, cerrada = sombra) · **Riel** con la cámara. | Definir si mediasombra es por sector (%) o por MZ (abierta/cerrada) antes de rotular. |
| **Acciones** | **Riego · Dosificación (fertilizante / fitosanitario) · Mediasombra**. | "Insumo" es el tipo técnico; para el usuario conviene nombrar el producto. |
| **Resultado de una acción** | **Efectiva · En seguimiento · Sin efectividad · Pospuesta · Abortada**. | — |
| **Nivel de alerta** | **Informativa · Advertencia · Crítica** (= INFO / WARNING / CRITICAL). | Nunca el código en inglés en pantalla. |
| **Bloqueo manual / Reanudar** | Suspensión de toda acción automática sobre un sector o MZ. | No "pausa" (eso es de la mediasombra, HU-08 CA-02). |
| **Rustificación** | Endurecimiento previo al trasplante; plan por etapas y días. **Perfil de crecimiento** = sin plan. | — |
| **Captura / Orden de captura / Pasada del riel** | La foto archivada / el pedido de foto / el recorrido del riel que pide varias. | — |
| **Dispositivo de captura** | El celular del riel. | Distinto de "hardware" del vivero (`CLAUDE.md §2`). |

### 5.2 Choques de nombres que existen hoy

| Choque | Dónde | Recomendación |
|---|---|---|
| **"Sector norte / centro / sur"** como subtítulo de una macro-zona | `NurseryConstants.ZONA_DEFS`, `src/data/mock/specs.ts:169-174`; se ve en el subtítulo de la MZ "Sector norte · 100 sectores" (`MapPage.tsx:71`) | Cambiar a "Ala norte / Franja centro / Sur" o quitarlo. *Sector* es una unidad del dominio. |
| **"Sin señal" vs "Fuera de servicio"** para el mismo estado del sector | Leyendas del Panel y la MZ dicen "Sin señal"; el badge del sector dice "Fuera de servicio" (`NurseryConstants.java:25`); `CLAUDE.md §2` dice "Fuera de servicio (offline)" | Sector: "Sin señal" (no está roto, su nodo no reporta). Equipo: "Fuera de servicio". Actualizar el glosario de `CLAUDE.md` en el cambio que lo resuelva. |
| **"Hardware fuera de servicio"** que cuenta sectores | `KpiRow.tsx` | "Macro-zonas sin lectura" (cuenta zonas) o "Equipos fuera de servicio" (cuenta equipos de Hardware). |
| **Nodo / sensor / tubete testigo** | `PlanoVivero.tsx` ("sensor testigo"), `1_ResumenPreliminar.md` | Nodo testigo. |
| **Niveles de alerta** en dos idiomas | Campana (CRITICAL) vs Historial (Crítica) | Castellano. |
| **"Plaga foliar / Daño fúngico" vs "Daño biótico"** | Filtro de Diagnósticos vs modelo | Daño biótico como clase, con subtipo opcional. |
| **"Mapa de producción"** | Nombre del feature `map/` y del diseño original; hoy la vista es "la macro-zona" | Llamarla **Macro-zona N** en títulos y breadcrumb. |

### 5.3 Jerga técnica que hoy se filtra a la UI

| Texto | Dónde | Por qué molesta | Alternativa |
|---|---|---|---|
| "Edge activo · sincronizado" | `Sidebar.tsx` | "Edge" es arquitectura, y además es falso | "Último dato recibido hace 3 min" |
| "Telemetría (al llegar una lectura)" / "Barrido (cada 5 min)" | `InspectorTab.tsx` | Mecanismos internos del motor | "Al llegar una lectura" / "Revisión periódica" |
| "Pipeline de decisión automática", "Historial de acciones por Ciclo", "🧠 Ver razonamiento del motor" | `HistorialTimeline.tsx` | "Pipeline", "Ciclo" (que en realidad agrupa por fecha) y emoji | "Cómo decidió el sistema" |
| Nombre de clase de regla cuando falta el nombre legible (`nombresReglas[r.regla] ?? r.regla` → `DeficitCriticoRule`) | `EventoMeta.tsx` | Nombre de clase Java | Siempre el nombre legible del catálogo o el ID de `reglas_v2` (R-02) |
| "CRITICAL", "WARNING" | `AlertsDropdown.tsx` | Inglés | Crítica, Advertencia |
| "Intervalos de sensado (IoT) y ejecución de inferencia (IA)", "Frecuencia Operativa" | `ConfiguracionPage.tsx` | IoT, inferencia | "Cada cuánto se mide" / "Cada cuánto se analizan las fotos" |
| Señal en **dBm** | Hardware, chip del nodo | Pocos saben leer −78 dBm | Barras + "buena / débil / sin señal" (dBm en tooltip para el admin) |
| "Pasada del riel: del dashboard al ESP32, al celular y a la IA" | `DemoExpoPage.tsx` | Aceptable para la expo técnica, no para un productor | "Mirá cómo el riel fotografía y la IA diagnostica, en vivo" |
| "Topes informativos" | Configuración | Contradice la idea de "límite" | Decidir si es límite (y aplicarlo) o quitarlo |
| "Serial / MAC" | Hardware | Correcto para P4 | Mantener, sólo en Administración |

---

## 6. Recomendaciones de arquitectura de información

### 6.1 Principios

1. **Ordenar por pregunta del usuario, no por módulo técnico.** "¿Está todo bien?" → "¿Qué tengo que
   hacer?" → "¿Qué pasó y por qué?" → "¿Cómo lo calibro?" → "¿Cómo está instalado?".
2. **La jerarquía física es la navegación de detalle** (Vivero → Macro-zona → Sector) y vive en el
   contenido + breadcrumb, **no en el menú** (D-01, D-19).
3. **Separar "causa de zona" de "causa de sector".** Lo que viene del sensado es de la MZ (D-02); lo
   que viene de la foto es del sector. Atención prioritaria y alertas deberían agrupar por MZ cuando la
   causa es la lectura ("MZ-3: sustrato seco · 100 sectores") y listar sectores sólo cuando la causa es
   el diagnóstico. Si no, una MZ seca inunda las listas.
4. **Acciones donde está el objeto.** Bloquear/forzar en el Sector y la MZ; recambio/reparación en el
   equipo; validar en el diagnóstico. Las secciones de "Operación" son listas de lo activo, no el lugar
   para ejecutar.
5. **Mostrar sólo datos reales en `http`** (D-04). Si un dato no existe todavía, estado vacío honesto.
6. **Mobile-first para P3**, desktop-first para P2/P4 (HU-17).

### 6.2 Navegación propuesta

Orden de arriba hacia abajo según frecuencia ponderada de las personas (§1, matriz).

```
VIVERO  (todos los roles, uso diario)
  1. Panel general                ← home; Plano + Estado + atención + clima + actividad
       └─ Macro-zona N            (contextual, no en el menú — D-01)
            └─ Sector MZ-N-xxx    (contextual)
  2. Alertas                      ← bandeja; la campana es su atajo (§3.3)  [badge: no atendidas]
  3. Diagnósticos de IA           ← con "Para revisar" primero (§3.6)       [badge: para revisar]
  ·  Demo Expo                    ← sólo si el interruptor está encendido (D-14)

OPERACIÓN  (Operario, Productor)
  4. Bloqueos e intervenciones    ← lo activo ahora: bloqueos, forzados, revisiones pendientes (S-06)
  5. Insumos                      ← stock de tanques y recargas (§3.11) — cuando exista la dosificación v2

TRAZABILIDAD  (Productor, Agrónomo)
  6. Historial                    ← con Exportar (§3.8) y el "cómo decidió" del motor

AGRONOMÍA  (Agrónomo edita; Productor lee)
  7. Umbrales y reglas            ← una sola sección con pestañas:
        · Estados de las métricas (bandas de color, hoy "Umbrales de métricas")
        · Parámetros del motor (catálogo)
        · Rustificación (plantilla del plan; el inicio por MZ vive en la Macro-zona — §3.10)
        · Seguimiento y frecuencias
  8. Inspector del motor          ← o pestaña de 7; enlazado desde Sector e Historial

INSTALACIÓN  (Administrador)
  9. Hardware                     ← Nodos y actuadores · Cámara y riel (§3.12) · Sectores incompletos
 10. Topología
 11. Usuarios y roles             ← HU-20 (R4)
 12. Preferencias                 ← Demo Expo, notificaciones WhatsApp
```

Notas sobre esta propuesta:

- **Configuración y Motor de reglas se unen en "Umbrales y reglas"** sin duplicar parámetros: cada
  pestaña edita lo suyo (D-13). Hoy el agrónomo tiene que saber que "volumen de riego" está en una
  pantalla y "dosis" en otra.
- **"Preferencias" saca de Configuración agronómica** lo que no es agronomía (el switch de Demo Expo ya
  se guarda aparte, `DemoExpoSwitch.tsx`).
- **Demo Expo queda en "Vivero"** porque su público (P5) entra por el Panel general y vuelve a él; si se
  agrega la pasada diaria (§3.13), su estado operativo va al Panel y a Hardware, y Demo Expo sigue siendo
  sólo la vista de presentación.
- **Los grupos "Operación" e "Instalación" no se muestran a quien no tiene el rol** (§6.3).

### 6.3 Visibilidad por rol (propuesta para HU-20)

| Sección | Productor | Agrónomo | Operario | Administrador |
|---|---|---|---|---|
| Panel general, MZ, Sector (lectura) | Sí | Sí | Sí | Sí |
| Alertas | Sí (atender) | Sí | Sí (atender) | Sólo hardware |
| Diagnósticos de IA | Sí | Sí (validar) | Lectura | — |
| Bloquear / reanudar / forzar | Sí | Sí | **Sí** | — |
| Iniciar / cancelar rustificación | Lectura | **Sí** | — | — |
| Bloqueos e intervenciones, Insumos | Sí | Sí | **Sí** | — |
| Historial + exportar | **Sí** | Sí | Lectura | Lectura |
| Umbrales y reglas | Lectura | **Edición** | — | — |
| Hardware | Lectura | — | Recambio / reparación | **Sí** |
| Topología, Usuarios y roles | — | — | — | **Sí** |

Coherente con HU-20 ("evitar modificaciones agronómicas por personal no autorizado") y
`reglas_v2 §13` ("sólo con el rol Ingeniero Agrónomo").

### 6.4 Móvil (Operario y Productor desde WhatsApp)

- **Barra inferior con 4 destinos**: Vivero · Alertas · **Buscar sector** · Bloqueos. El sidebar de
  256 px no entra en 320 px (HU-17 CA-01).
- **"Ir a sector"**: campo para tipear "3-077" (el operario está parado frente al cartel del sector).
  Ojo: es una puerta nueva a Sector/MZ; para la MZ choca con D-01 → se resuelve con un cambio de spec
  (alcanza con permitir llegar al Sector, que ya enlaza a su MZ).
- **Links profundos desde WhatsApp** a `/sector/:id` o a la alerta, que funcionen sin pasar por el
  Panel.
- **Acciones grandes y confirmadas**, deshabilitadas sin conexión (HU-17 CA-02).
- La grilla de 100 celdas por MZ en 320 px da celdas de ~26 px con 10 por fila: viable, pero el Plano
  (parcelas) debería ser lo primero en móvil y la grilla on-demand.

### 6.5 Flujos y vínculos cruzados que la IA tiene que garantizar

| Desde | Hacia | Para qué |
|---|---|---|
| Alerta (campana, bandeja, WhatsApp) | Sector o MZ de la alerta | Entender y actuar |
| Celda de la grilla / Atención prioritaria | Sector | Ver diagnóstico y actuar |
| Parcela del Plano | Bloque de la MZ (scroll) → "Ver detalle de la zona" | D-07 |
| Sector | Su MZ (breadcrumb) | Condiciones ambientales que explican el estado (D-02, D-19) |
| Sector / evento del Historial | Inspector del motor con ese sector | "¿Por qué?" |
| Inspector | Parámetro en "Umbrales y reglas" (`?regla=`) | Calibrar (ya existe) |
| Diagnóstico | Foto + Sector + "Pedir foto nueva" | Revisar y re-capturar |
| Equipo en Hardware | Sector o MZ donde está | Ubicarlo físicamente |
| KPI del Panel | Lista filtrada (sectores, equipos, acciones del día en Historial) | Que cada número sea clicable |

### 6.6 Qué hay que pasar por OpenSpec antes de dibujar

1. Fuente de las alertas de la campana y "atendida" (HU-10) → completar `finish-release-2`.
2. Bloqueo manual y forzado (HU-19) → endpoint + UI.
3. Validación humana de diagnósticos sin violar D-15.
4. Mediasombra por sector (%) vs por MZ (abierta/cerrada) → decide si el actuador vive en el Sector o en la MZ.
5. Umbral de confianza 70 % vs 85 % y su lectura desde el catálogo.
6. Severidad real del modelo vs derivada de la confianza.
7. Dispositivos de captura y vinculación en Hardware (sacarlos de la dependencia del simulador).
8. "Ir a sector" / cambio de MZ desde adentro (toca D-01).
9. Unificación de Configuración + Motor de reglas en una sección (toca specs `configuracion-agronomica` y la de catálogo).
10. Taxonomía de diagnósticos única (modelo, backend, filtros).
11. Glosario: "Sin señal" vs "Fuera de servicio" y el subtítulo "Sector norte" de las MZ (actualizar `CLAUDE.md §2`).
12. Specs obsoletas a limpiar para que no confundan: `simulacion-modo-vista`, notas de estado de
    `alertas-inteligentes`, `bloqueo-manual`, `motor-reglas`; `historial-trazabilidad` vs "por ciclo".
