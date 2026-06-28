# Yerbanalytics

> Plataforma de monitoreo con IA para viveros de yerba mate. Detecta anomalías en
> plantines (clorosis, estrés solar, daño biótico, etc.) a partir de imágenes
> cenitales y ejecuta acciones correctivas autónomas (riego, insumos, mediasombra).
> Caso piloto: Vivero San Ignacio, Misiones, Argentina.

Proyecto universitario. Este archivo es la fuente de contexto para asistentes de IA
y para cualquiera que llegue nuevo al repo. **Mantenelo actualizado.**

---

## 1. Mapa del repositorio

```
Yerbanalytics/
├── Desarrollo/
│   ├── frontend/      Dashboard React + TS + Vite (ver §4)
│   ├── backend/       API REST Spring Boot (Java 17, puerto 8000)
│   └── Modelo_IA/     Modelo de visión: datasets, notebooks, resultados (ver §5)
├── Documentacion/     Documentos de negocio, alcance, arquitectura, entregas
├── openspec/          Spec-driven development (ver §3)
├── .claude/           Skills y comandos de OpenSpec para Claude Code
└── README.md
```

Tres áreas de trabajo: **frontend**, **backend** y **Modelo_IA**. El frontend consume
el backend vía `VITE_DATA_SOURCE=http`; la integración con el modelo de IA es futura.

---

## 2. Dominio (glosario)

- **Plantín**: unidad de análisis. El modelo diagnostica UN plantín por imagen
  (el riel/gantry lo aísla y encuadra).
- **Sector**: agrupación de ~100 tubetes con 1 microaspersor. El vivero tiene 600
  sectores en 6 macro-zonas (MZ-1 … MZ-6).
- **Estados de salud**: `Saludable` · `En observación` (warning) · `Crítico` ·
  `Fuera de servicio` (offline).
- **Diagnósticos de IA**: Sano, Clorosis (trastorno nutricional), Estrés solar,
  Daño biótico (plagas/hongos — ácaro, plaga foliar, daño fúngico), No concluyente.
- **Actuadores**: electroválvula (riego), bomba peristáltica (insumos), mediasombra.
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

### Arranque rápido
```bash
cd Desarrollo/backend
./mvnw spring-boot:run   # escucha en http://localhost:8000
```

### Endpoints principales
- `GET /api/nursery` → snapshot del vivero (`NurseryData` en `frontend/src/types/domain.ts`)

### Convenciones internas
- Entidades con Lombok + `JpaRepository` + service + controller.
- Datos iniciales en `src/main/resources/data.sql`.
- Para features nuevas, espejar el patrón existente (entity → repo → service → controller → seed).

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
| Curación de datasets | `Desarrollo/Modelo_IA/informe-curacion-datasets.md` |
| Negocio / alcance | `Documentacion/` |
