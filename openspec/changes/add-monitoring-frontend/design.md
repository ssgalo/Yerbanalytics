# Design: add-monitoring-frontend

## Context

El input es `Yerbanalytics.dc.html` (1001 líneas): HTML con estilos inline + una
clase `DCLogic` (`Component`) que genera toda la data con un RNG sembrado
determinístico (semilla `20260613`) y mantiene el estado de navegación con
`this.state` y `setState`. Es decir: el archivo mezcla **diseño**, **estado de
UI** y **lógica de dominio**. El objetivo es separar esas tres capas en una
arquitectura React mantenible **sin alterar el resultado visual ni los datos**.

## Goals / Non-Goals

**Goals**
- Fidelidad pixel-a-dato: misma data, mismos colores, misma tipografía, mismas animaciones.
- Separación de capas: dominio ⊥ datos ⊥ presentación.
- Preparado para backend real vía variable de entorno (sin reescribir UI).
- Bien organizado: cualquiera abre `src/features/` y entiende el producto.

**Non-Goals**
- No implementamos las vistas Historial / Configuración / Hardware (son placeholders
  en el diseño original).
- No implementamos backend ni autenticación real (el usuario "Mariano Duarte" es estático).
- No agregamos data nueva que el diseño no tenga.

## Decisions

### 1. Stack: Vite + React 18 + TypeScript
Vite por HMR y build rápido; React 18 estándar; TypeScript porque el dominio
tiene muchas formas de datos (Sector, Metric, Diagnosis, Action, Alert, Weather)
y los tipos previenen errores y documentan los contratos.

### 2. Estilos: CSS Modules + design tokens en `:root`
- Los tokens de color del HTML (`--brand`, `--ok`, `--warn`, `--crit`, ...) pasan
  a `src/styles/tokens.css` como custom properties globales.
- La estructura estática de cada componente vive en su `*.module.css`.
- Los valores **dinámicos** (color de un sector según su estado, ancho de una
  barra según un porcentaje) se aplican con `style={{}}` inline — uso legítimo
  porque dependen de datos en runtime.
- **Por qué no Tailwind**: el diseño ya define su sistema de tokens; CSS Modules
  + variables es la traducción más fiel y con menos fricción.

### 3. Datos: patrón Repository + factory por entorno
```
DataRepository (interface)
 ├── MockRepository   → porta build()/makeSector()/buildDetail() del HTML, RNG sembrado
 └── HttpRepository   → (placeholder) fetch al backend cuando exista
getRepository()        → elige según VITE_DATA_SOURCE ('mock' | 'http')
```
La UI solo conoce la **interface**. Cambiar de mock a backend real es cambiar una
variable de entorno, no tocar componentes. Esto es lo que justifica el `.env.example`.

### 4. Estado de UI: react-router + estado local
El `this.state` del HTML se descompone:
- `view` → ruta (`/`, `/mapa`, `/sector/:id`, `/diagnosticos`, `/historial`...).
- `sectorId` → param de ruta `/sector/:id`.
- `mapZona` → estado local de `MapPage` (default `MZ-1`).
- `alertsOpen`, `diagEstado`, `diagSev`, `photoId`, `range` → estado local del
  componente que los usa.
La data (cara de generar) se calcula una vez y se comparte con un hook
`useNurseryData()` sobre un Context, para no regenerar el RNG en cada navegación.

### 5. Arquitectura: feature-based + atomic
```
src/
  styles/      tokens.css, global.css (reset, fonts, keyframes, scrollbar)
  types/       domain.ts  (contratos del dominio)
  lib/         rng.ts     (RNG sembrado, pick, rr — portados exactos)
  data/        repository.ts, mock/, http/, index.ts (factory)
  hooks/       useNurseryData.ts
  components/
    ui/        Card, Badge, ProgressBar, Sparkline, StatusDot, Icon (átomos)
    layout/    AppLayout, Sidebar, Topbar, AlertsDropdown
  features/
    dashboard/      DashboardPage + components/
    map/            MapPage + components/
    sector/         SectorPage + components/
    diagnostics/    DiagnosticsPage + components/
    placeholder/    PlaceholderPage
  router.tsx   App.tsx   main.tsx
```

### 6. Fidelidad del RNG (riesgo clave)
El diseño se ve así por la semilla `20260613` y el orden exacto de llamadas a
`r()`. **El orden de las llamadas dentro de `build()` y `makeSector()` debe
preservarse byte-a-byte**, o los datos generados cambian. Es el punto más
sensible del port y se valida con un test snapshot de stats agregadas.

## Risks / Trade-offs

- **Riesgo**: divergencia del RNG → datos distintos al diseño. **Mitigación**:
  portar la lógica sin reordenar llamadas + test de stats (`sano`, `warning`,
  `critical`, `offline`, `diagCount`).
- **Trade-off**: estilos inline para valores dinámicos. Aceptado: es la forma
  correcta de mapear datos→estilo y mantiene el resto en CSS Modules.
- **Trade-off**: HttpRepository queda como stub. Aceptado: el backend no existe;
  lo importante es que el contrato esté listo.

## Migration Plan

1. Scaffold + tooling.  2. Tokens + global CSS.  3. Tipos + RNG + MockRepository
+ test de stats.  4. Layout (shell).  5. Features (5 vistas).  6. Router + wiring.
7. `.env.example` + README + build verde.
El HTML original permanece como referencia.
