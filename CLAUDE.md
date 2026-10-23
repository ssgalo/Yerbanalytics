# Yerbanalytics

> Plataforma de monitoreo con IA para viveros de yerba mate. Detecta anomalías en
> plantines (clorosis, estrés solar, daño biótico, etc.) a partir de imágenes
> cenitales y ejecuta acciones correctivas autónomas (riego, insumos, mediasombra).
> Caso piloto: Vivero San Ignacio, Misiones, Argentina.

Proyecto universitario. Este archivo es la fuente de contexto para asistentes de IA
y para cualquiera que llegue nuevo al repo. **Mantenelo actualizado.**

> **Qué va acá y qué no.** Este archivo orienta: qué hay, dónde está, y qué decisiones
> no conviene revertir sin entenderlas. El **cómo** —levantar cada cosa, configurarla,
> depurarla, los trámites de entorno— vive en el README de cada área. Si algo se está
> explicando en detalle acá, probablemente le falte un enlace y le sobre texto.

---

## 1. Mapa del repositorio

```
Yerbanalytics/
├── Desarrollo/
│   ├── frontend/      Dashboard React + TS + Vite (ver §4)
│   ├── backend/       API REST Spring Boot (Java 17, puerto 8000)
│   ├── camara/        App de captura (PWA iOS) — proyecto propio (ver §6.1)
│   ├── camara-android/ App de captura nativa Android — proyecto propio (ver §6.1)
│   ├── simulador/     Simulador de hardware — proyecto propio y borrable (ver §6.2)
│   ├── servicio-inferencia/ Daemon Python: YOLOv8 (`modelo_final.pt`) + módulo de diagnóstico (ver §5)
│   ├── contratos/     Contratos versionados entre la plataforma y sus dispositivos
│   ├── certs/         CA local y certificados TLS de la LAN (no se versionan)
│   ├── embebido/      Firmware ESP32 (nodo testigo y actuadores); fuente del contrato MQTT. `prototipo_hardware/` = sketches del hardware real, incl. el riel por MQTT (§6.1)
│   └── Modelo_IA/     Modelo de visión: datasets, notebooks, resultados (ver §5)
├── Documentacion/     Documentos de negocio, alcance, arquitectura, entregas
├── docs-motor-reglas-e-integracion/  Reglas agronómicas v2, su comparación con el motor, circuito de sensado, guía del ESP32 (ver §6)
├── openspec/          Spec-driven development (ver §3)
├── .claude/           Skills y comandos de OpenSpec para Claude Code
└── README.md
```

Cinco áreas de trabajo: **frontend**, **backend**, **camara**, **Modelo_IA** y
**servicio-inferencia**. El frontend consume el backend vía `VITE_DATA_SOURCE=http`; el servicio de
inferencia toma las capturas pendientes, diagnostica y registra por la API, como un cliente más.

`Desarrollo/camara/` y `Desarrollo/simulador/` son **proyectos aparte a propósito**, y por
motivos distintos. La cámara es la implementación de referencia de un contrato, no la
definitiva: si mañana el cliente es una app Android, se borra el directorio y nada más se
entera. El simulador es una herramienta de prueba que no debería tener presencia en el
sistema: se borra la carpeta y el vivero sigue funcionando igual, esperando telemetría de
hardware real.

---

## 2. Dominio (glosario)

- **Plantín**: unidad de análisis. El modelo diagnostica UN plantín por imagen
  (el riel/gantry lo aísla y encuadra).
- **Sector**: agrupación de ~100 tubetes con 1 microaspersor. El vivero tiene 600
  sectores en 6 macro-zonas (MZ-1 … MZ-6). **La electroválvula y la bomba son por sector; el
  sensado y la mediasombra, NO** (son de la macro-zona).
- **Nodo testigo**: hay UNO por macro-zona. Su lectura vale para los 100 sectores
  de esa MZ, así que las métricas se modelan y se muestran a nivel macro-zona
  (`Zona.lectura`), no de sector. Lo que diferencia a un sector de otro dentro de
  una zona es el diagnóstico de IA de su plantín.
- **Métricas sensadas (10)**: humedad de sustrato, humedad ambiental, temperatura
  del aire, luminosidad y temperatura del sustrato (ambiente); CE, pH, N, P y K
  (nutrición). Las cinco de la sonda de suelo son **informativas**
  (`afectaEstado: false`): se muestran y colorean, pero no cambian el estado de
  los sectores hasta validar sus rangos con el vivero.
  > Ojo con dos claves del contrato MQTT: `ce` llega en µS/cm y se persiste en
  > dS/m; `uv` es **% de luz de un LDR**, no radiación UV (la clave se conserva
  > por compatibilidad con el firmware). Fuente de verdad:
  > `Desarrollo/embebido/comun/contrato.h`.
- **Métrica derivada (+1)**: el **DPV** (déficit de presión de vapor, kPa) no se sensa: la
  ingesta lo calcula con la temperatura del aire y la humedad ambiental de cada lectura
  (`readings/VaporPressureDeficit`), lo guarda en la zona y en `zone_reading`, y el dashboard
  lo muestra con su tramo. Son 10 sensadas + 1 derivada; es informativo y no está en el
  contrato MQTT. Lo usan las reglas de hongos y plagas (F-F1, F-F2, F-P1).
- **Estados de salud**: `Saludable` · `En observación` (warning) · `Crítico` ·
  `Fuera de servicio` (offline).
- **Diagnósticos de IA**: Sano, Clorosis (trastorno nutricional), Estrés solar,
  Daño biótico (plagas/hongos — ácaro, plaga foliar, daño fúngico), No concluyente.
  Uno por sector: **estado + nivel de severidad** (Baja/Media/Alta, sólo anómalos; sale de la
  *cantidad* de hojas) **+ nivel de confianza** (Baja/Media/Alta, salvo No concluyente; sale del
  score promedio en las bandas de su clase). `conf` numérico = score promedio × 100.
- **Criterio de diagnóstico**: bandas de severidad por clase y orden de criticidad; lo edita el
  agrónomo (Configuración → Criterio de diagnóstico). Los umbrales y bandas de **confianza** son del
  equipo y viven en el entorno del servicio de inferencia, no en el dashboard.
- **Actuadores**: electroválvula (riego) y bomba peristáltica (insumos), por sector; **mediasombra, una por
  macro-zona** (se registra como el nodo testigo, con la zona; su `shade SET` va por el comando de zona).
- **Orden de captura**: pedido de una foto cenital, con el sector y la **posición de riel**.
  Es la entidad de primera clase del pipeline de visión: la imagen se sube citando su
  identificador, y sin esa correlación el sistema no sabría de qué sector es la foto.
- **Captura**: la imagen recibida y archivada. El JPEG vive en el filesystem; en la base sólo
  queda su metadata.
- **Dispositivo de captura**: el equipo que toma las fotos (hoy un iPhone con la PWA de
  `Desarrollo/camara/`). Distinto del **hardware** del vivero (nodos y actuadores), que se
  registra aparte.
- **Pulso de sensado**: cada `intervaloSensadoMinutos` (240 por defecto, franjas desde las 02:00) el
  backend pide lectura a todas las macro-zonas y el motor decide con la primera respuesta de cada una.
  **Es el único momento en que decide el motor**: no hay barrido, y cualquier otra lectura sólo
  actualiza valores. Tres pulsos seguidos sin respuesta ponen el nodo fuera de servicio
  (`WARNING`, `WARNING`, `CRITICAL`) y la MZ pasa a **estado seguro** (riego cerrado, bombas detenidas,
  mediasombra cerrada); con el nodo caído se le vuelve a pedir lectura cada 15 min. Los plazos y reintentos de la
  lectura son parámetros `sensado.*` del catálogo. También se pide a mano (`POST /api/pulsos`, con o sin motor).
- **Diagnóstico nuevo**: las reglas que usan el diagnóstico de IA (hoy la de insumo) actúan una vez
  por diagnóstico; queda marcado como usado en el sector. La telemetría no toca el diagnóstico.
- **Aplicación (de insumo)**: orden de fertilizante o fitosanitario a un sector (`supply/ApplicationService`). Toda aplicación pasa por
  ahí: la regla decide **qué** insumo y el servicio verifica las precondiciones P-D1…P-D5, publica `pump ON {ml}` y descuenta el tanque.
  Lo que no es el momento queda **pendiente** y se reintenta en la próxima ventana apta (07–10 / 17–19 de fábrica).
- **Tanque (de insumo)**: stock de un insumo por macro-zona; lo carga el operario y cada aplicación lo descuenta. Un tanque sin registrar
  es un stock desconocido, no un tanque lleno. **Reingreso**: tras un fitosanitario el sector queda 24 h con el reingreso restringido
  (`Sector.reentryUntil`); sólo informa, no bloquea actuadores.
- **Plan de nutrición**: fertilizante foliar en **todos los sectores de la macro-zona cada N días** (3–30, fábrica 7; lo fija el agrónomo en el
  detalle de la zona), sin mirar lo que se mide (N-01), y adelantado si la CE media baja de 0,5 dS/m sin fertilizar en 5 días (N-02). El motor
  lo pide en una **tanda** (un pedido por sector) y el intervalo corre desde que se aplicó el último sector (`nutrition/`).
- **Rustificación**: endurecimiento progresivo del plantín antes del trasplante. Plan **por
  macro-zona** (desde su detalle): etapas de N días con una ventana horaria en que la mediasombra
  está abierta (sólo abre o cierra del todo). Sin plan, rige el horario por defecto de Rustificación.
  Pasada la última etapa, la última se sostiene hasta que alguien elimina el plan. **No es parte del
  motor**: lo ejecuta `HorarioMediasombraService`, que actúa sólo en los bordes de la ventana; el motor
  puede cerrar la malla igual (pico de UV) y queda así hasta la próxima apertura.
- **Roles** (fijos): Administrador, Ingeniero Agrónomo, Productor Viverista, Operario y
  **Servicio** (cuentas de integraciones: simulador, servicio de inferencia; no para personas).
- **Permiso**: código del catálogo fijo en `seguridad/Permiso.java` (`reglas.ver`,
  `reglas.editar`, `usuarios.gestionar`…). Uno de edición no implica el de lectura.
- **Matriz de permisos**: qué permisos tiene cada rol. La edita el Administrador desde la vista
  Usuarios; se siembra con la matriz por defecto sólo si está vacía.
- **Auditoría de seguridad**: registro append-only y encadenado por hash de los cambios sobre
  usuarios, matriz y política de sesión. No confundir con el **historial** (HU-11), que registra
  las acciones agronómicas.

---

## 3. OpenSpec — estándar de desarrollo

Los cambios sustanciales se documentan con **OpenSpec** (`@fission-ai/openspec`,
spec-driven development) ANTES de implementar. Es el estándar del repo.

- Carpeta: `openspec/` (raíz). `specs/` = specs vivas; `changes/` = propuestas
  activas; `changes/archive/` = cerradas.
- Comandos (Claude Code): `/opsx:propose "idea"` → `/opsx:apply` → `/opsx:sync` →
  `/opsx:archive`. Requiere reiniciar el IDE para que aparezcan en autocomplete.
- Flujo de un cambio: `proposal.md` (por qué/qué) → `design.md` (decisiones
  técnicas) → `specs/<capacidad>/spec.md` (requisitos) → `tasks.md` (checklist).
- Cambio de referencia ya hecho: `openspec/changes/add-monitoring-frontend/`
  (construcción completa del frontend).

> Concepto > código: primero el QUÉ y el POR QUÉ, después el CÓMO.

---

## 4. Frontend (`Desarrollo/frontend/`)

Dashboard de monitoreo. **React 18 + TypeScript + Vite.** Nació replicando `Yerbanalytics.dc.html`
(Claude Design); el rediseño en curso lo reemplaza pantalla por pantalla, siempre sobre el
**sistema de diseño** (ver "Convenciones de estilo").

### Arranque rápido
```bash
cd Desarrollo/frontend
cp env.example .env
npm install
npm run dev          # http://localhost:5173
```

### Arquitectura (feature-based + atomic design)
- **Capa de datos** (`src/data/`): patrón Repository. La UI consume una interface
  `DataRepository`; no sabe si detrás hay un mock determinístico o el backend real.
  Se elige con `VITE_DATA_SOURCE` (`mock` | `http`). Migrar a backend = cambiar una
  variable de entorno, sin tocar componentes.
  > **`VITE_DATA_SOURCE` es el modo de operación de la app**, y es el **único** punto de
  > decisión: rige para *todas* las secciones, así que nunca conviven en pantalla datos mock
  > con datos del backend. `http` es el sistema real (producción); `mock` es la demo
  > ilustrativa, no funcional y sin backend (`npm run dev:demo`, preconfigurado en
  > `.env.demo`). Se resuelve al arrancar: cambiar de modo exige reiniciar el dashboard.
  > Ninguna vista consulta el modo al backend.
  > **Sin controles de demo:** Ninguna interfaz ni botón de demo interactiva (bypass de reglas,
  > cooldowns en cero, botón "Leer ahora", clima ficticio) vive en este dashboard; todo control de demo vive
  > exclusivamente en el simulador (`Desarrollo/simulador/`). **El interruptor de una regla en Motor de reglas no es un
  > control de demo**: es una operación del vivero (ver §6, "Interruptor operativo vs. controles de demo").
- **Tipos de dominio** (`src/types/domain.ts`): contratos compartidos.
- **Componentes del sistema de diseño** (`src/components/ui/`): Button, Chip, StateGlyph, Callout,
  Field/Input/Select/InputUnit, Segmented, FilterChip, Switch, SaveBar, Skeleton, EmptyState,
  OfflineBanner, CompositionBar, RangeBar, SectorCell, ScrollArea (grilla desplazable
  sin barra, con fade y flechas en los bordes); y los previos Card, Badge,
  StatusDot, ProgressBar, Sparkline, Icon.
- **Shell** (`src/components/layout/`): AppLayout, Sidebar, Topbar, AlertsDropdown. En el Sidebar,
  **Configuración** es un grupo desplegable (Umbrales —la vista `/configuracion`, sólo las bandas de alerta de las
  métricas—, Rustificación —`/rustificacion`: horario por defecto de la mediasombra y resumen del plan de cada macro-zona—,
  Criterio de diagnóstico, Motor de reglas, Hardware, Topología, Usuarios, Preferencias); se abre
  solo al entrar a una de sus vistas. Umbrales, Rustificación y Preferencias editan partes de la
  misma configuración agronómica (`useConfigDraft`): cada una guarda el recurso entero. Criterio de
  diagnóstico (`/criterio-diagnostico`) es otro recurso, con su propio borrador. La sidebar se
  colapsa a íconos (botón de la Topbar, borde de la sidebar o Ctrl/⌘ + B; estado en
  `hooks/SidebarContext`, recordado por navegador).
- **Features** (`src/features/`): una carpeta por vista — dashboard, map, sector,
  diagnostics, reglas, historial, demo-expo, placeholder. **Motor de reglas** (`/reglas`) es la vista del motor: pestaña
  *Reglas* (cada regla con su interruptor operativo y sus parámetros, por objetivo) e *Inspector* (el grafo de reglas con la última
  evaluación de un sector). **Demo Expo** (`/demo-expo`) dispara y muestra en vivo la pasada del riel;
  se ve u oculta con un interruptor en Configuración → Preferencias.
- **Routing**: react-router (`src/router.tsx`).

### Convenciones de estilo (sistema de diseño)
Spec: `openspec/changes/add-sistema-diseno/`. Catálogo vivo en `/sistema-diseno` (sólo `npm run dev`).
Toda pantalla nueva o rediseñada lo respeta; **si cambia algo del sistema, se actualiza todo lo
que lo usa** (`src/styles/tokens.test.ts` falla si vuelve un token retirado o un hex viejo).
- **Todo color sale de un token** de `src/styles/tokens.css` (espejo JS: `theme.ts`). Nada de hex
  sueltos en los módulos. Los únicos saturados son los de estado (`--ok/--warn/--crit/--off`) y
  `--info` ("el sistema actuó"). El único oscuro es `--art` (fotos, login, grafos del motor); la bandeja (`--tray`) va del tono del fondo.
- **Texto de estado con su tinta** (`--x-ink`), nunca con el relleno; `--faint` no es para texto;
  bordes de campo con `--line-field`.
- **El estado tiene forma además de color** (`StateGlyph`, `SectorCell`): círculo, medio círculo,
  rombo, aro.
- **Sin emojis** en la interfaz ni en los textos que manda el backend (etiquetas de reglas incluidas): los íconos salen
  de `Icon` y los estados de `StateGlyph`.
- **Tipografía**: Archivo en tres anchos. Toda regla con `--font-display` lleva
  `font-stretch: var(--w-exp)`; tablas y metadatos, `var(--w-cond)`.
- Estilos estáticos → **CSS Modules**. Valores dinámicos (color por estado, ancho
  por %) → inline `style={{}}` (dependen de datos en runtime), siempre con `var(--token)`.
- Los colores que manda el backend en los DTO (`NurseryConstants` y servicios) son copia de estos
  tokens: si cambia un valor acá, cambia allá.

### Scripts
`npm run dev` (sistema real) · `dev:demo` (demo estática) · `build` · `build:demo` ·
`preview` · `lint` (0 warnings) · `format` · `test`.

Detalle completo en `Desarrollo/frontend/README.md`.

---

## 5. Modelo de IA (`Desarrollo/Modelo_IA/`)

Modelo de visión para diagnosticar el estado del sector.

- **Modelo vigente**: **YOLOv8 de detección** (`Desarrollo/servicio-inferencia/modelo_final.pt`,
  no se versiona; `imgsz=1024`; 5 clases: sano, clorosis, estrés solar, plaga foliar y daño
  fúngico —`colapso_hongos` en el checkpoint—). Detecta hojas; no clasifica la imagen entera.
  (Los notebooks de esta carpeta empezaron con MobileNetV3-Large como clasificador.)
- **Módulo de diagnóstico** (`servicio-inferencia/src/diagnosis.py`, lógica pura): filtro por
  umbral de clase → cajas superpuestas (NMS calibrado: nivel de confianza, luego criticidad, luego
  score) → severidad por cantidad → confianza por promedio → gana la mayor relevancia
  (severidad + confianza), desempata la criticidad. **El filtro va antes que los solapes, a
  propósito**: una caja inválida no debe poder borrar a una válida de otra clase. Spec y porqués:
  `openspec/changes/add-diagnostico-por-detecciones/`.
- **El diagnóstico describe; el motor decide.** El módulo no conoce reglas ni acciones.
- **Datasets**: NO se versionan en Git (pesados; viven en Google Drive y se
  re-descargan de sus fuentes/DOIs). Donantes curados por homología fenotípica del
  tejido (CoLeaf, RoCoLe, Tea Leaf, etc.). Ver `datasets/README.md` y
  `informe-curacion-datasets.md`.
- **Criterio de datasets**: un donante vale por cuánto se parece visualmente su
  tejido a la hoja de yerba, NO por la etiqueta del paper.
- **notebooks/**: entrenamiento (baseline). **resultados/**: matrices de confusión,
  curvas, informes de cada corrida.

> Taxonomía del modelo (5 clases del MVP) ≠ estados del dashboard. El frontend usa
> una presentación de diagnósticos; el modelo tiene sus clases. Ver informes.

---

## 6. Backend (`Desarrollo/backend/`)

App **Spring Boot 3.2.4** (Java 17) funcional. Stack: JPA + PostgreSQL, ingesta MQTT
(con simulador). El esquema lo crea Hibernate (`ddl-auto=update`) y se siembra con
`resources/data.sql` (600 sectores, 6 zonas).

> **Migraciones manuales pendientes.** `ddl-auto=update` agrega columnas pero no migra datos
> ni borra lo obsoleto, así que hay diecisiete scripts para correr a mano (ninguno se aplica solo; `migracion-captura-imagenes.sql` es DDL de
> referencia y no hace falta):
> - `resources/migracion-manual.sql` — el sensado se mudó de `sector` a `zona`.
> - `resources/migracion-catalogo-parametros.sql` — dos columnas de `configuracion_operativa` pasan
>   al catálogo de parámetros de reglas (y copia el umbral de riego editado).
> - `resources/migracion-reglas-riego.sql` — limpia lo que dejaron las reglas de riego v2 y baja
>   `riego_vol_max_diario_ml`.
> - `resources/migracion-quitar-simulador.sql` — baja de `modo_operacion` y `sensor_simulado`,
>   que eran estado de una herramienta de prueba dentro de la base de producción (ver §6.2).
> - `resources/migracion-pulso-sensado.sql` — borra `sensing_pull_interval_seconds` e
>   `intervalo_evaluacion_minutos`, que reemplazó el pulso de sensado.
> - `resources/migracion-diagnostico-detecciones.sql` — quita el `NOT NULL` de `diagnostico.conf`
>   (un No concluyente no tiene score).
> - `resources/migracion-confianza-70.sql` — borra las confianzas mínimas guardadas por encima de 95 %,
>   que el rango nuevo (50–95 %, fábrica 70 %) no admite.
> - `resources/migracion-mediasombra-binaria.sql` — borra `mediasombra.apertura-proteccion-uv` y
>   `mediasombra.apertura-maxima` del catálogo (la mediasombra es binaria: abierta o cerrada).
> - `resources/migracion-permisos-bloqueo.sql` — agrega `bloqueos.ver`/`bloqueos.gestionar` a la matriz de una
>   base ya sembrada (la matriz sólo se siembra si está vacía).
> - `resources/migracion-permisos-servicio-simulador.sql` — agrega `reglas.ver`, `pasadas.ver` y `pasadas.operar` al rol
>   Servicio (catálogo y traza del motor, pulsos, secuencias y pasadas); sin ellas dan 403.
> - `resources/migracion-permisos-simulacion-servicio.sql` — agrega `simulacion.gestionar` al rol Servicio (el simulador usa `/api/demo/**`); sin él da 403.
> - `resources/migracion-permisos-hardware-servicio.sql` — agrega `hardware.ver` al rol Servicio (el simulador lo lee
>   para su escenario de hardware incompleto); sólo lectura.
> - `resources/migracion-permisos-hardware-reparar.sql` — agrega `hardware.reparar` (Ingeniero, Operario y Servicio) y
>   `hardware.ver` al Operario a una matriz ya sembrada (S-05, registro de reparaciones).
> - `resources/migracion-permisos-insumos.sql` — agrega `insumos.ver`/`insumos.gestionar` (el Operario carga los tanques; el rol Servicio
>   los usa desde el simulador) a una matriz ya sembrada.
> - `resources/migracion-dosis-por-insumo.sql` — `insumo.dosis-ml` pasa a `insumo.dosis-fertilizante`/`insumo.dosis-fitosanitario`
>   (copia el valor editado al fertilizante).
> - `resources/migracion-mediasombra-por-zona.sql` — la mediasombra pasa del sector a la zona (una por zona), borra
>   `sector.actuador_shade` y `rustificacion_etapa`. **Sin ella no se pueden crear sectores** (la columna vieja es `NOT NULL`).
> - `resources/migracion-umbrales-al-motor.sql` — el seguimiento post-acción pasa al catálogo del motor y se borra
>   `insumo_dosis_max_24h_ml`. **Correr con el backend nuevo ya arrancado una vez** (crea `parametro_regla` y `regla_estado`);
>   sin ella no se puede crear una fila operativa nueva (columnas viejas `NOT NULL`).
>
> Detalle en el README del backend.

### Arranque rápido
```bash
cd Desarrollo/backend
./mvnw spring-boot:run   # escucha en http://localhost:8000
```

### Endpoints principales
- `GET /api/nursery` → snapshot del vivero (`NurseryData` en `frontend/src/types/domain.ts`).
  Cada zona trae `lectura` (las 10 métricas evaluadas) y `nodo` (batería/señal del testigo).
- `GET/PUT /api/rules/parametros`, `GET /api/rules/evaluaciones/{sectorId}`, `GET /api/rules/schema`
  → catálogo de umbrales del motor, traza de la última evaluación y grafo de reglas (ver más abajo).
  `PUT /api/rules/{name}/estado` → interruptor operativo de una regla (`reglas.editar`; apagar una protección pide motivo y confirmación).
- `POST /api/pasadas`, `GET /api/pasadas/actual`, `POST /api/pasadas/actual/cancelar` → pasada del
  riel (también diaria, a hora configurable: `pasadaDiariaActiva`/`pasadaDiariaHora` en la
  configuración); `GET/PUT /api/configuracion/demo-expo` → interruptor de la pestaña. Ver README del backend.
- `POST /api/pulsos {ejecutarMotor}` → pulso de sensado manual de todas las zonas, sólo por API; `409` si hay
  uno en curso. En Demo Expo, la lectura con motor es la secuencia `LECTURA` con `ejecutarMotor` (sólo la zona
  de la secuencia). Ver "Pulso de sensado" en el README del backend.
- `GET/POST /api/zonas/{zona}/rustificacion`, `POST /api/zonas/{zona}/rustificacion/cancelar` → plan de rustificación de la
  macro-zona (leer con `vivero.ver`; iniciar/eliminar con `configuracion.editar`). Ver "Mediasombra por macro-zona y plan de
  rustificación" en el README del backend.
- `PUT /api/zonas/{zona}/plan-nutricion {intervalDays}` → intervalo del plan de nutrición de la macro-zona (permiso `configuracion.editar`; 3–30
  días). El plan se lee de `GET /api/nursery` → `zonas[].nutritionPlan`. Ver "Plan de nutrición" en el README del backend.
- `GET /api/tanques`, `POST /api/tanques/{zona}/{insumo}/recarga {liters, capacityLiters?}` → tanques de insumo por macro-zona y
  registro de sus cargas (permisos `insumos.ver` / `insumos.gestionar`). Ver "Aplicación de insumos" en el README del backend.
- `GET/POST /api/bloqueos`, `DELETE /api/bloqueos/{id}` → bloqueo manual de la actuación autónoma por sector o
  macro-zona (S-01): al activar cierra válvulas y bombas y cancela el riego encolado; reanudar no repone nada.
  Un bloqueo es de un sector o de una zona, nunca de los dos. Ver "Bloqueo manual" en el README del backend.
- `POST /api/auth/login`, `GET /api/auth/perfil`, `POST /api/auth/actividad|logout`,
  `PUT /api/auth/clave` → sesión. `/api/usuarios/**`, `/api/roles/**`, `/api/seguridad/politica`,
  `GET /api/auditoria` → gestión de seguridad (HU-01 / HU-20). Ver "Autenticación y permisos" en
  el README del backend.
- `POST /api/secuencias`, `GET /api/secuencias/actual`, `POST /api/secuencias/actual/cancelar` →
  secuencias guionadas de Demo Expo (riego, mediasombra, lectura): comandan el actuador directo, sin
  pasar por el motor, y no corren a la vez que una pasada. Ver README del backend y
  `openspec/changes/add-secuencias-demo-expo/`.
- `POST /api/diagnosticos` → **alta de diagnóstico**. Camino único: lo usan la carga manual del
  simulador y el servicio de inferencia. Sin variantes, sin marca de origen y sin ningún estado
  global que lo condicione (ver §6.1). Lleva estado, `sev`, `confidence`, `conf` y `detections`, con
  coherencia exigida por estado; `GET /api/diagnosticos/{id}/detecciones` devuelve las cajas.
- `GET/PUT /api/configuracion/diagnostico` → criterio de diagnóstico (lee `diagnosticos.ver`, así
  lo lee la cuenta Servicio de la inferencia en cada ciclo; edita `configuracion.editar`).

### Convenciones internas
- Entidades con Lombok + `JpaRepository` + service + controller.
- Datos iniciales en `src/main/resources/data.sql`.
- Para features nuevas, espejar el patrón existente (entity → repo → service → controller → seed).
- **`spring.jpa.open-in-view=false`.** No revertir sin leer el porqué en el README del
  backend: con el stream SSE de órdenes, cada dispositivo conectado retendría una conexión
  JDBC permanente.
- **El backend consume la telemetría por MQTT y publica comandos a los actuadores y al riel.** No
  publica telemetría ni debería: quien la publica es el hardware, o el simulador que lo reemplaza
  (§6.2). Escucha además los eventos del riel (`nursery/rail/event`) y el ACK de los actuadores
  (`nursery/zone/+/sector/+/ack`), más los errores del nodo (`nursery/zone/+/errores`, S-05). El ACK lo consumen dos: `SeguimientoComandosService` (alerta en el historial
  si el nodo informa una falla o no contesta a tiempo; no cambia decisiones del motor, salvo la `falla_mecanica` de la malla, que se entrega a `HardwareFaultService`) y las secuencias de
  Demo Expo (`SecuenciaService`, que espera el ACK de cada paso). Los comandos de las secuencias salen por
  el mismo `ComandoActuadorPublisher`, así que el seguimiento también los vigila, a propósito.
- **El pasado de las lecturas vive en `zone_reading`** (una fila por lectura aceptada, con las diez métricas vigentes y su origen `PULSE`/`OTHER`). Las reglas lo consultan sólo por `readings/ReadingHistory`; nunca arman su propia tabla. Sin lecturas es `SIN_DATO`, no cero. Detalle en el README del backend.
- **Los umbrales de las reglas viven en un único catálogo de parámetros** (`engine/parametros/`),
  editable por `/api/rules/parametros`. Una regla no lleva constantes ni `@Value` para umbrales:
  los declara en `parametros()` y los lee por ahí. `umbral_metrica` sólo define estado y color.
- **La ingesta valida antes de escribir y el motor sabe qué hardware hay.** `ReadingValidationGuard` (S-03) descarta por
  métrica lo que cae fuera del rango físico del catálogo, con relecturas inmediatas, y `IncompleteHardwareRule` (S-04,
  prioridad 1) aborta la rama a la que le falta un actuador mapeado (`HardwareCoverage`, armada una vez por zona). Efecto a
  conocer: **un sector sin su electroválvula registrada no se riega solo**. Prioridades de las reglas: únicas (no hay
  fraccionarias); ver `ReglasArquitecturaTest`. Detalle en el README del backend ("Lectura inválida (S-03) y hardware incompleto
  (S-04)").
- **Las fallas del nodo marcan el dispositivo, y marcar es de un solo servicio.** El ESP32 detecta la falla, corta el actuador y la
  publica en `nursery/zone/{zona}/errores`; `HardwareFaultService` es el **único** que escribe `dispositivo.falla` (y la levanta al
  registrar la reparación, `POST /api/hardware/{id}/reparacion`, permiso `hardware.reparar`). `HydraulicFaultRule` (prioridad 2) y
  `ShadeFaultRule` leen la marca por `RuleContext.faults` y sacan al sector (o a la malla) de lo autónomo; R-02 no se exceptúa.
  Efecto a conocer: **un sector con falla hidráulica no se riega ni se dosifica solo hasta que alguien registre la reparación**.
  Detalle en el README del backend ("Fallas del nodo (S-05)").
- **Las aplicaciones de insumo se deciden en las reglas y se ejecutan en `ApplicationService`** (`supply/`), igual que el riego en su despacho:
  una regla emite `ACTIVAR_BOMBA` con `DetalleInsumo(supply, fromDiagnosis)` y **nunca publica la bomba**; el servicio verifica
  `ApplicationPreconditions` (P-D1…P-D5, `DemoControllable`), el stock del tanque, registra y descuenta. El backend manda **mililitros** (el
  firmware convierte a tiempo y corta solo; tope 50 ml por comando = `ContratoNodo.BOMBA_ML_MAX`) y hay una bomba por sector: el comando
  no lleva el insumo. **`SupplyRule` y `DailyDoseLimitRule` están retiradas** (arrancan apagadas, `yerbanalytics.reglas.retiradas`) hasta que
  entren las reglas por diagnóstico F-*, que son las que las reemplazan: hoy sólo el plan de nutrición (N-01) pide aplicaciones, y no decide con
  el diagnóstico. Detalle en el README del backend.
- **El riego se decide en las reglas y se ejecuta en un despacho por tandas** (cola, 10 válvulas por
  zona, revalidación antes de abrir). Qué cubre cada regla frente a `reglas_v2` y el riesgo de
  operar sin E-01/S-06: README del backend y
  `docs-motor-reglas-e-integracion/diferencias-motor-reglas-vs-reglas-v2.md`.
- **Toda la API exige sesión de usuario y permiso** (HU-01 / HU-20), salvo el login, `/ca.pem` y el
  contrato de cámara. Es *deny-by-default*: una ruta nueva queda cerrada hasta que se le declara el
  permiso en **`seguridad/MapaPermisos`**, que es el único lugar donde se asignan (nada de
  `@PreAuthorize` desperdigado); `MapaPermisosCoberturaTest` falla si falta. Sesiones opacas en
  tabla, no JWT: así la revocación y el cierre por inactividad son inmediatos y los decide el
  servidor. **Los `GET` no cuentan como actividad** (los sondeos no mantienen viva la sesión): no
  revertir sin leer el design de `add-login-y-permisos`.
- **Las cuentas iniciales salen de `Desarrollo/backend/.env`** (plantilla `env.example`, importado con
  `spring.config.import`): el Administrador (`YERBANALYTICS_ADMIN_*`) y una lista genérica
  `YERBANALYTICS_USUARIOS_INICIALES` (`usuario:ROL:clave,…`). Se crean si no existen y nunca se pisan.
  Sus claves de desarrollo coinciden con los `env.example` del simulador y de la inferencia.
- **La mediasombra es de la macro-zona, y todo lo suyo también** (`mediasombra/`): el equipo se registra con la zona, la
  posición vive en `zona.mediasombra_abierta`, el comando sale por `nursery/zone/{z}/command` y el ACK vuelve por
  `nursery/zone/{z}/ack`. Sus reglas (`ShadeFaultRule`, `ShadingRule`) son `ZoneRule`; el plan de rustificación y el
  horario por defecto los ejecuta `HorarioMediasombraService`, **fuera del motor** y sólo en los bordes de la ventana, así no
  pisa lo que decida una regla. El único que publica la malla es `MediasombraService` (el estado seguro y las secuencias
  publican por su cuenta y registran la posición).
- **Las reglas que deciden sobre una macro-zona entera son `ZoneRule`** (`engine/zone/`), no `Rule`: el plan de nutrición (N-01, N-02) es de la zona
  y la CE que lo adelanta también (hay un nodo testigo por zona). `ZoneRuleEngine` las corre **una vez por zona**, en `updateTelemetry` después de los
  sectores y sólo con el motor encendido (pulso con motor); decide la regla y ejecuta `ZoneActionExecutor`/`NutritionPlanService`, igual que el
  reparto del riego. Son `DemoControllable` y declaran sus umbrales en el catálogo como cualquier regla, y su traza se suma a la del sector en el
  Inspector. Antes de escribir una regla nueva, decidir si es de sector (`Rule`) o de zona (`ZoneRule`). Detalle en el README del backend ("Plan de nutrición").
- **Patrón para agregar una nueva regla agronómica**:
  - Toda pieza que decide una acción (regla por sector, por macro-zona o servicio) es un **`DemoControllable`** (`engine/overrides/`; `Rule` ya lo extiende): así aparece en `GET /api/demo/rules`, se apaga y declara sus `cooldownKeys()`/`bypassKeys()`. Las reglas nuevas también necesitan su escenario en el simulador (`Desarrollo/simulador/src/scenarios/`; ver su README).
  - Toda regla nueva debe seguir estrictamente el patrón de las existentes: implementar `Rule`, anotarse como `@Component`, definir `priority()` ascendente, `name()`, `label()` legible para el usuario, `branch()` (GLOBAL, RIEGO, INSUMO, MEDIASOMBRA, SEGUIMIENTO) y declarar sus umbrales en `parametros()`.
  - Toda regla se presenta con `code()` (el de `reglas_v2`, o ninguno), `objective()` y `effect()`, y declara `protection()` (texto de la consecuencia de apagarla) si su efecto es `PROTECTS`; `ReglasArquitecturaTest` lo verifica. El `label()` va sin emojis y sin el código.
  - **Interruptor de activación por regla**: lo gestiona `OperationalRuleStates` (estado operativo, `regla_estado`) y lo consulta el motor por `RuleOverrides` (`EffectiveRuleOverrides`: el override explícito de demo, si existe, y si no el operativo). Una regla no implementa su propio flag. Las retiradas (`yerbanalytics.reglas.retiradas`) son sólo el estado por defecto, mientras nadie decida otra cosa; apagada figura `OMITIDA_DESHABILITADA`.
  - **Flags de Cooldowns y variables de bypass en modo demo**: Si la regla impone restricciones temporales (esperas entre riegos, límites por hora, ventanas horarias, pausas tras insumos) o variables que condicionan la acción, debe implementar las flags de cooldown o variables de bypass correspondientes:
    - **Cada regla puede tener más de una flag de cooldown o variable a bypasear**: Por ejemplo, una regla puede tener un cooldown de ciclo de lectura Y una guarda de riego en curso, o una pausa tras aplicación Y un límite de dosis.
    - En producción (`yerbanalytics.mode=REAL`), cada regla aplica estrictamente los valores y cooldowns establecidos por el usuario en la configuración agronómica.
    - En modo demo (`yerbanalytics.mode=SIMULATED` / `yerbanalytics.demo.enabled=true`), los cooldowns y variables de bypass se pueden setear en 0 o conmutar independientemente vía `/api/demo/rules/{ruleName}`, permitiendo re-regar o re-actuar de inmediato sin esperar las 4 horas ni depender de la hora del día.
- **Fuente de telemetría en modo demo**: el simulador escribe en `nursery/demo/zone/{z}/telemetry` (tópico aparte del
  ESP32) y `GET/PUT /api/demo/telemetry-source` (`REAL_NODE` | `SIMULATOR`, en memoria) elige cuál procesa el backend
  (motor, pulso y lectura de Demo Expo). Los comandos siguen yendo a los actuadores del ESP32. Todo en el paquete
  `demo/`; con demo apagado no existe suscripción, endpoint ni bean (el código productivo sólo ve `TelemetryAdmission`).
- **Modos de operación y Feature Flags**:
  - Controlado por `yerbanalytics.mode` (`REAL` | `SIMULATED`) y `yerbanalytics.demo.enabled` (`false` | `true`), sobreescribible en Docker por `YERBANALYTICS_MODE` y `YERBANALYTICS_DEMO_ENABLED`.
  - En modo `REAL` (producción), el backend no expone ningún rastro de simulación: los endpoints `/api/demo/**` no se registran (retornan 404), no existen tablas demo en base de datos y todas las reglas respetan sus cooldowns productivos. El estado operativo de las reglas sí rige en `REAL`.
  - **Interruptor operativo vs. controles de demo.** Prender o apagar una regla es una **operación del vivero**: vive en
    Motor de reglas (`PUT /api/rules/{name}/estado`, permiso `reglas.editar`), se persiste en `regla_estado`, vale en modo
    `REAL` y sobrevive reinicios. Apagar una protección exige motivo y confirmación. Los **controles de demo** —apagar una
    regla para un escenario, cooldowns en cero, bypass de guardas, clima simulado, "Leer ahora"— siguen viviendo sólo en el
    simulador, en memoria, existen sólo en modo demo y, mientras están puestos, ganan sobre el estado operativo. El
    simulador no escribe el estado operativo.
  - **Aislamiento estricto: ninguna implementación de demo en la app productiva.** Todo control de demo (botón "Leer ahora", override de reglas para un escenario, bypass de cooldowns, clima simulado) DEBE vivir exclusivamente en el simulador (`Desarrollo/simulador/`). El frontend productivo (`Desarrollo/frontend/`) no debe tener ningún rastro, botón ni vista de demo; su único interruptor de reglas es el operativo.


## 6.1 Captura de imágenes (`Desarrollo/camara*/` + `Desarrollo/contratos/`)

Un teléfono montado en el riel hace de cámara cenital. Cubre HU-04 CA-01 y habilita HU-05 CA-02.
**No incluye el modelo de IA**: el diagnóstico lo registra el servicio de inferencia (§5), o se
carga a mano desde el simulador, por el mismo camino exacto.

**El entregable es el contrato, no una app.** Hay **dos clientes** del mismo contrato, y conviven
a propósito:

| Cliente | Qué es | Para qué está |
|---|---|---|
| `Desarrollo/camara/` | PWA sobre Safari iOS | Primera implementación; se hizo para un iPhone, sin herramientas para compilar nativo |
| `Desarrollo/camara-android/` | App Android nativa (Kotlin, CameraX) | El cliente de producción: opera con la pantalla apagada vía foreground service |

El criterio de aceptación del contrato era literal —*escribir la app Android no debe requerir
tocar el backend*— y **quedó ejercido**: la app nativa entró sin modificar una línea del backend
ni subir la versión del contrato. Dos clientes independientes contra la misma superficie es la
evidencia de que el contrato sirve; dar de baja la PWA es una decisión posterior, no una deuda.

**Quién pide las fotos.** Además del pedido manual, la **pasada del riel** (`POST /api/pasadas`,
botón de Demo Expo) mueve el riel por MQTT (`vivero_esp32_red`), emite las órdenes de captura de dos
sectores y vuelve a home. Probada con hardware el 03/10/2026; límites conocidos (final de carrera de
home, fuente del motor) en el README del sketch.

Invariantes a respetar al tocar esta área:

- **`/api/camara/v1/**` es superficie versionada.** La fuente de verdad es
  `Desarrollo/contratos/camara/v1/openapi.yaml`, igual que `contrato.h` lo es para el MQTT
  del ESP32. Agregar o cambiar rutas ahí **es cambiar el contrato**: se actualiza el OpenAPI
  y se corre la suite de conformidad. El resto de la API (dashboard, simulador, futuro
  planificador) queda fuera del contrato y un dispositivo no debe usarla.
- **Órdenes por SSE, imágenes por REST.** Se descartó MQTT-over-WebSockets a propósito,
  aunque el broker ya exista.
- **Los JPEG van al filesystem, no a `bytea`.**
- **La tabla `diagnostico` no tiene columna de origen** y `captura_id` es `NOT NULL`: un
  diagnóstico manual y uno del modelo son la misma fila porque son la misma operación.
- **La API de plataforma de cámara y captura exige sesión de usuario** (vinculación, dispositivos,
  órdenes, imágenes, diagnósticos), con permiso (`camara.gestionar`, `capturas.ordenar`,
  `capturas.ver`, `diagnosticos.registrar`). El contrato `/api/camara/v1/**` sigue con su token de
  dispositivo, en su propia cadena de Spring Security: las credenciales no se cruzan. La suite de
  conformidad prepara sus casos con una cuenta de plataforma (`PLATAFORMA_USUARIO`/`PLATAFORMA_CLAVE`).
- **El simulador no tiene ni un endpoint propio**: usa el de emisión de órdenes (el que también usa
  el planificador de pasadas) y el de alta de diagnósticos (el de la futura inferencia).
- **HTTPS no es opcional para la PWA**, y sí lo es para la app nativa. La restricción es del
  navegador, no del sistema: `getUserMedia` exige origen seguro y una página HTTPS no puede
  llamar a un endpoint HTTP. Por eso el backend abre un conector adicional en el 8443 y deja el
  8000 en HTTP. Los certificados no se versionan, así que **en un clon nuevo hay que generarlos**
  (`Desarrollo/certs/generar-certificados.sh`). La app Android no los necesita: habla HTTP contra
  el 8000 y admite igual `https://…:8443` sin recompilar, si se instala la CA en el teléfono.
  Es un desvío deliberado del §8 del contrato, documentado en el README de la app.

El porqué de cada una, cómo levantarlo, el trámite de la CA en el iPhone y el diagnóstico de
fallas: `Desarrollo/camara/README.md`, `Desarrollo/camara-android/README.md`,
`Desarrollo/contratos/camara/v1/README.md` y el README del backend.

---

## 6.2 Simulador de hardware (`Desarrollo/simulador/`)

Ocupa el lugar del hardware físico: publica telemetría en el broker como si fuera un nodo
ESP32, y ejercita el ciclo de captura del riel. Es una app Node independiente (React + Vite
para la UI, Express + cliente MQTT del lado servidor), en el `:5180`.

**El criterio de aceptación es literal: borrar la carpeta no debe requerir tocar nada.**

Invariantes a respetar al tocar esta área:

- **El backend no conoce al simulador.** Ni endpoint, ni entidad, ni tabla, ni propiedad, ni
  origen CORS, ni rama de código. Si arreglar algo del simulador parece requerir tocar el
  backend, la solución está mal.
- **Publica directo al broker**, en `nursery/zone/{zonaId}/telemetry`, con el mismo payload y
  las mismas unidades que el firmware. El backend lo ingiere sin distinguirlo de un nodo real.
  Se descartó pasar por un endpoint del backend: ése era el acoplamiento que se vino a sacar.
- **La UI habla con el backend a través de `/backend/**`**, el proxy de su propio servidor. Si
  llamara directo, el backend tendría que permitir su origen por CORS — y eso sería un rastro.
- **Entra con una cuenta de rol Servicio** (`BACKEND_USUARIO`/`BACKEND_CLAVE` en su `.env`). En
  desarrollo la crea el backend al arrancar porque figura en `YERBANALYTICS_USUARIOS_INICIALES` de
  su `.env` —una lista de configuración genérica—; el backend no tiene usuario, rol ni permiso
  programado para él. Su servidor inicia sesión y adjunta la cookie en el proxy. Sin credenciales, la
  telemetría MQTT sigue saliendo y su UI avisa que el backend rechaza el resto.
- **Con el backend en modo demo publica en `nursery/demo/zone/{z}/telemetry`** (no en el tópico del ESP32) y trae el
  interruptor de la fuente de telemetría; sin modo demo publica en el tópico regular, como hardware simulado.
- **Su estado vive en `simulador/data/`**, nunca en la base del vivero.
- **No tiene superficie de API propia en el sistema**: topología, órdenes de captura,
  dispositivos y diagnósticos son endpoints públicos, los mismos que usan el planificador de
  pasadas y el servicio de inferencia. **No toca `/api/camara/v1/**`**, que es el contrato del
  dispositivo.
- **Toda funcionalidad y control de demo vive exclusivamente acá, NUNCA en la app productiva.**
  El botón "Leer ahora", los controles de clima ficticio, el bypass de cooldowns y el override de activación de
  reglas para un escenario son herramientas del simulador (`:5180`); su interruptor, en modo demo, **gana** sobre el
  estado operativo (la leyenda «Apagada en el vivero» lo avisa) y nunca lo escribe: la cuenta Servicio no tiene `reglas.editar`. El frontend productivo (`Desarrollo/frontend/`)
  no contiene ninguna implementación, vista ni control de demo.
- **El contrato MQTT queda espejado en tres lugares** (firmware, `ContratoNodo.java`,
  `simulador/server/contract.ts`). Fuente de verdad: `Desarrollo/embebido/comun/contrato.h`.
  El del riel (`nursery/rail/*`) está en ese mismo header y en `mqtt/ContratoRiel.java`. Los errores del nodo
  (`nursery/zone/{zona}/errores`, S-05) también: `ContratoNodo.TOPIC_ERRORS*`, `NodeErrorType` y `buildNodeError` del simulador. El
  `shade SET` por el comando de zona y su ACK de zona (`nursery/zone/{zona}/ack`: `contratoTopicAckZona`, `ContratoNodo.TOPIC_ACK_ZONA`,
  `zoneAckTopic` del simulador) también. El comando de
  zona "leer ahora" (`nursery/zone/{z}/command`) también está en los tres espejos. Y los ACK intermedios del nodo
  (`status: "EN_CURSO"` con `detalle.tipo` `en_cola` / `iniciado`, `cola_llena`, las claves `posicion` / `esperaMaxSec`) y
  `parametros.prioridad = "INMEDIATA"` del `shade SET` de seguridad (cola de cargas del nodo, `add-cola-actuadores-nodo`):
  el nodo sirve a lo sumo `MAX_CARGAS_SIMULTANEAS` cargas a la vez y el simulador hace lo mismo (`server/nodeQueue.ts`,
  `SIM_NODE_MAX_LOADS`). Orden de despliegue: backend antes que firmware.
  Incluye la duración máxima de la válvula (1200 s): el backend nunca pide más y un `config.h`
  anterior del firmware no compila a propósito.

No confundir el simulador con el **modo estático** del dashboard: ése es
`VITE_DATA_SOURCE=mock` (§4), una demo ilustrativa sin backend. El simulador es lo contrario —
sirve para probar el sistema **real** inyectándole hardware simulado.

Detalle, arranque y diagnóstico de fallas: `Desarrollo/simulador/README.md`.

---

## 7. Convenciones del repo

- **Commits**: conventional commits (`feat:`, `fix:`, `docs:`, `chore:`…). Sin
  atribución de IA en los mensajes.
- **Datasets fuera de Git** (ver `.gitignore`). Se documentan, no se commitean.
- **Cambios grandes pasan por OpenSpec** antes de codear.
- **Archivos `.env`** no se versionan; usar `env.example` como plantilla.
- **Idioma**: español (Rioplatense) en docs y comentarios.
- **El código va en inglés** (identificadores, nombres de archivos, componentes, funciones, tipos y
  clases nuevos); todo lo demás —comentarios, docs, textos de la interfaz, commits— en español. El
  código existente en español se migra cuando se reescribe, no en cambios aparte.

---

## 8. Referencias rápidas

| Tema | Dónde |
|------|-------|
| Diseño del dashboard | Sistema de diseño (fila siguiente) · `Desarrollo/frontend/Yerbanalytics.dc.html` (diseño original) |
| Sistema de diseño | `openspec/changes/add-sistema-diseno/`, `src/styles/tokens.css`, `/sistema-diseno` en dev |
| Cómo correr el simulador | `Desarrollo/simulador/README.md` |
| Cómo correr el frontend | `Desarrollo/frontend/README.md` |
| Plan del frontend (SDD) | `openspec/changes/add-monitoring-frontend/` |
| Contrato de la cámara | `Desarrollo/contratos/camara/v1/` (OpenAPI + referencia) |
| Cómo correr la app de cámara (PWA) | `Desarrollo/camara/README.md` |
| Cómo generar la APK de cámara | `Desarrollo/camara-android/README.md` |
| Curación de datasets | `Desarrollo/Modelo_IA/informe-curacion-datasets.md` |
| Servicio de inferencia y módulo de diagnóstico | `Desarrollo/servicio-inferencia/README.md`, `openspec/changes/add-diagnostico-por-detecciones/` |
| Negocio / alcance | `Documentacion/` |
| Reglas agronómicas y su estado en el motor | `docs-motor-reglas-e-integracion/` (`reglas_v2.md`, `diferencias-motor-reglas-vs-reglas-v2.md`) |
| Secuencias de Demo Expo (riego, mediasombra, lectura) | `openspec/changes/add-secuencias-demo-expo/`, `Desarrollo/backend/README.md` ("Secuencias"), `docs-motor-reglas-e-integracion/analisis-demo-expo-vs-vivero.md` §9 |
| Pasada del riel (backend) y firmware del riel | `Desarrollo/backend/README.md` ("Planificador de pasadas"), `Desarrollo/embebido/prototipo_hardware/vivero_esp32_red/README.md`, `openspec/changes/add-pasada-riel/` |
| Del sensor al motor / conectar el ESP32 | `docs-motor-reglas-e-integracion/circuito-sensado-a-motor.md`, `conectar-esp32.md` |
