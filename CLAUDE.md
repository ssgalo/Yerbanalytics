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
│   ├── contratos/     Contratos versionados entre la plataforma y sus dispositivos
│   ├── certs/         CA local y certificados TLS de la LAN (no se versionan)
│   └── Modelo_IA/     Modelo de visión: datasets, notebooks, resultados (ver §5)
├── Documentacion/     Documentos de negocio, alcance, arquitectura, entregas
├── openspec/          Spec-driven development (ver §3)
├── .claude/           Skills y comandos de OpenSpec para Claude Code
└── README.md
```

Cuatro áreas de trabajo: **frontend**, **backend**, **camara** y **Modelo_IA**. El frontend
consume el backend vía `VITE_DATA_SOURCE=http`; la integración con el modelo de IA es futura.

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
  sectores en 6 macro-zonas (MZ-1 … MZ-6). **Los actuadores son por sector; el
  sensado NO.**
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
- **Estados de salud**: `Saludable` · `En observación` (warning) · `Crítico` ·
  `Fuera de servicio` (offline).
- **Diagnósticos de IA**: Sano, Clorosis (trastorno nutricional), Estrés solar,
  Daño biótico (plagas/hongos — ácaro, plaga foliar, daño fúngico), No concluyente.
- **Actuadores**: electroválvula (riego), bomba peristáltica (insumos), mediasombra.
- **Orden de captura**: pedido de una foto cenital, con el sector y la **posición de riel**.
  Es la entidad de primera clase del pipeline de visión: la imagen se sube citando su
  identificador, y sin esa correlación el sistema no sabría de qué sector es la foto.
- **Captura**: la imagen recibida y archivada. El JPEG vive en el filesystem; en la base sólo
  queda su metadata.
- **Dispositivo de captura**: el equipo que toma las fotos (hoy un iPhone con la PWA de
  `Desarrollo/camara/`). Distinto del **hardware** del vivero (nodos y actuadores), que se
  registra aparte.
- **Rustificación**: endurecimiento progresivo del plantín antes del trasplante
  (plan por días, regula mediasombra).

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

Dashboard de monitoreo. **React 18 + TypeScript + Vite.** Replica el diseño
`Yerbanalytics.dc.html` (Claude Design) de forma exacta.

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
  > Ninguna vista consulta el modo al backend — el backend no tiene modos.
- **Tipos de dominio** (`src/types/domain.ts`): contratos compartidos.
- **Átomos UI** (`src/components/ui/`): Card, Badge, StatusDot, ProgressBar,
  Sparkline, Icon.
- **Shell** (`src/components/layout/`): AppLayout, Sidebar, Topbar, AlertsDropdown.
- **Features** (`src/features/`): una carpeta por vista — dashboard, map, sector,
  diagnostics, placeholder.
- **Routing**: react-router (`src/router.tsx`).

### Convenciones de estilo
- Design tokens en `src/styles/tokens.css` (CSS custom properties).
- Estilos estáticos → **CSS Modules**. Valores dinámicos (color por estado, ancho
  por %) → inline `style={{}}` (dependen de datos en runtime).
- Tipografías: Space Grotesk (`--font-display`, títulos/números) + Hanken Grotesk
  (`--font-body`, cuerpo).

### Scripts
`npm run dev` (sistema real) · `dev:demo` (demo estática) · `build` · `build:demo` ·
`preview` · `lint` (0 warnings) · `format` · `test`.

Detalle completo en `Desarrollo/frontend/README.md`.

---

## 5. Modelo de IA (`Desarrollo/Modelo_IA/`)

Modelo de visión para clasificar el estado del plantín.

- **Enfoque**: transfer learning con **MobileNetV3-Large** (TF/Keras), single-label
  en el MVP. Unidad = un plantín.
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
> ni borra lo obsoleto, así que hay dos scripts para correr a mano:
> - `resources/migracion-manual.sql` — el sensado se mudó de `sector` a `zona`.
> - `resources/migracion-quitar-simulador.sql` — baja de `modo_operacion` y `sensor_simulado`,
>   que eran estado de una herramienta de prueba dentro de la base de producción (ver §6.2).
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
- `POST /api/diagnosticos` → **alta de diagnóstico**. Camino único: lo usa una carga manual
  hoy y lo usará el servicio de inferencia mañana. Sin variantes, sin marca de origen y sin
  ningún estado global que lo condicione (ver §6.1).

### Convenciones internas
- Entidades con Lombok + `JpaRepository` + service + controller.
- Datos iniciales en `src/main/resources/data.sql`.
- Para features nuevas, espejar el patrón existente (entity → repo → service → controller → seed).
- **`spring.jpa.open-in-view=false`.** No revertir sin leer el porqué en el README del
  backend: con el stream SSE de órdenes, cada dispositivo conectado retendría una conexión
  JDBC permanente.
- **El backend sólo *consume* MQTT.** No tiene publicador ni debería tenerlo: quien publica es
  el hardware, o el simulador que lo reemplaza (§6.2).
- **El backend no tiene modos de operación.** Ningún endpoint, tabla ni propiedad depende de
  que el sistema esté "en simulación": se comporta siempre como en producción.

---

## 6.1 Captura de imágenes (`Desarrollo/camara*/` + `Desarrollo/contratos/`)

Un teléfono montado en el riel hace de cámara cenital. Cubre HU-04 CA-01 y habilita HU-05 CA-02.
**No incluye el modelo de IA**: hasta que exista, el diagnóstico se carga a mano desde el
simulador, por el mismo camino exacto que va a usar el modelo.

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
- **El simulador no tiene ni un endpoint propio**: usa el de emisión de órdenes (el del futuro
  planificador) y el de alta de diagnósticos (el de la futura inferencia).
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
- **Su estado vive en `simulador/data/`**, nunca en la base del vivero.
- **No tiene superficie de API propia en el sistema**: topología, órdenes de captura,
  dispositivos y diagnósticos son endpoints públicos, los mismos que usarán el planificador de
  pasadas y el servicio de inferencia. **No toca `/api/camara/v1/**`**, que es el contrato del
  dispositivo.
- **El contrato MQTT queda espejado en tres lugares** (firmware, `ContratoNodo.java`,
  `simulador/server/contract.ts`). Fuente de verdad: `Desarrollo/embebido/comun/contrato.h`.

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

---

## 8. Referencias rápidas

| Tema | Dónde |
|------|-------|
| Diseño del dashboard | `Desarrollo/frontend/Yerbanalytics.dc.html` |
| Cómo correr el simulador | `Desarrollo/simulador/README.md` |
| Cómo correr el frontend | `Desarrollo/frontend/README.md` |
| Plan del frontend (SDD) | `openspec/changes/add-monitoring-frontend/` |
| Contrato de la cámara | `Desarrollo/contratos/camara/v1/` (OpenAPI + referencia) |
| Cómo correr la app de cámara (PWA) | `Desarrollo/camara/README.md` |
| Cómo generar la APK de cámara | `Desarrollo/camara-android/README.md` |
| Curación de datasets | `Desarrollo/Modelo_IA/informe-curacion-datasets.md` |
| Negocio / alcance | `Documentacion/` |
