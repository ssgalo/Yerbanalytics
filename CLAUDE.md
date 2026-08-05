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

`Desarrollo/camara/` es un **proyecto aparte a propósito**: es la implementación de referencia
de un contrato, no la definitiva. Si mañana el cliente es una app Android, se borra el
directorio y nada más se entera.

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
`npm run dev` · `build` · `preview` · `lint` (0 warnings) · `format` · `test`.

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

> **Migración manual pendiente**: el sensado se mudó de `sector` a `zona`.
> `ddl-auto=update` agrega columnas pero no migra datos ni borra las obsoletas.
> Ver `resources/migracion-manual.sql` y el README del backend.

### Arranque rápido
```bash
cd Desarrollo/backend
./mvnw spring-boot:run   # escucha en http://localhost:8000
```

### Endpoints principales
- `GET /api/nursery` → snapshot del vivero (`NurseryData` en `frontend/src/types/domain.ts`).
  Cada zona trae `lectura` (las 10 métricas evaluadas) y `nodo` (batería/señal del testigo).
- `POST /api/diagnosticos` → **alta de diagnóstico**. Camino único: lo usa el panel de
  simulación hoy y lo usará el servicio de inferencia mañana. Sin variantes, sin marca de
  origen y sin depender del modo de operación (ver §6.1).

### Convenciones internas
- Entidades con Lombok + `JpaRepository` + service + controller.
- Datos iniciales en `src/main/resources/data.sql`.
- Para features nuevas, espejar el patrón existente (entity → repo → service → controller → seed).
- **`spring.jpa.open-in-view=false`.** No revertir sin leer el porqué en el README del
  backend: con el stream SSE de órdenes, cada dispositivo conectado retendría una conexión
  JDBC permanente.

---

## 6.1 Captura de imágenes (`Desarrollo/camara/` + `Desarrollo/contratos/`)

Un iPhone montado en el riel hace de cámara cenital. Cubre HU-04 CA-01 y habilita HU-05 CA-02.
**No incluye el modelo de IA**: hasta que exista, el diagnóstico se carga a mano desde el
simulador, por el mismo camino exacto que va a usar el modelo.

**El entregable es el contrato, no la PWA.** El cliente final probablemente sea una app
Android, y el criterio de aceptación es literal: *escribir esa app no debe requerir tocar el
backend.*

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
- **HTTPS no es opcional** para la app de cámara: el backend abre un conector adicional en el
  8443 y deja el 8000 en HTTP. Los certificados no se versionan, así que **en un clon nuevo
  hay que generarlos** (`Desarrollo/certs/generar-certificados.sh`). Sin ellos el backend
  arranca y el dashboard funciona; sólo la cámara queda sin poder conectarse.

El porqué de cada una, cómo levantarlo, el trámite de la CA en el iPhone y el diagnóstico de
fallas: `Desarrollo/camara/README.md`, `Desarrollo/contratos/camara/v1/README.md` y el README
del backend.

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
| Cómo correr el frontend | `Desarrollo/frontend/README.md` |
| Plan del frontend (SDD) | `openspec/changes/add-monitoring-frontend/` |
| Contrato de la cámara | `Desarrollo/contratos/camara/v1/` (OpenAPI + referencia) |
| Cómo correr la app de cámara | `Desarrollo/camara/README.md` |
| Curación de datasets | `Desarrollo/Modelo_IA/informe-curacion-datasets.md` |
| Negocio / alcance | `Documentacion/` |
