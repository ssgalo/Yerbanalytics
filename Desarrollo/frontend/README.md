# Yerbanalytics · Frontend

Dashboard de monitoreo IA del vivero de yerba mate. React + TypeScript + Vite.

Replica el diseño de alta fidelidad `Yerbanalytics.dc.html` (Claude Design) con una
arquitectura mantenible y lista para conectarse al backend.

## Requisitos

- Node.js 18+
- npm 10+

## Puesta en marcha

```bash
cp env.example .env      # configurá las variables (ver abajo)
npm install
npm run dev              # http://localhost:5173
```

> Nota: el harness no permite versionar archivos `.env*`, por eso la plantilla se
> llama `env.example` (sin punto). Copiala a `.env`.

## Scripts

| Script | Qué hace |
|--------|----------|
| `npm run dev` | Dashboard contra el **backend real** (HMR) — http://localhost:5173 |
| `npm run dev:demo` | Dashboard en **modo estático**: demo ilustrativa, sin backend |
| `npm run build` | Type-check + build de producción (`dist/`) |
| `npm run build:demo` | Build de la demo estática |
| `npm run preview` | Sirve el build |
| `npm run lint` | ESLint (0 warnings permitidos) |
| `npm run format` | Prettier |
| `npm test` | Tests (Vitest) |

### Los dos modos

`VITE_DATA_SOURCE` es el modo de operación de la app, y es el **único** punto de decisión del
origen de datos: rige para *todas* las secciones —vivero, mapa, sector, diagnósticos, hardware,
configuración, historial y topología—, así que nunca conviven en pantalla datos mock con datos
del backend.

| | `npm run dev` | `npm run dev:demo` |
|---|---|---|
| `VITE_DATA_SOURCE` | `http` | `mock` (preconfigurado en `.env.demo`) |
| Qué muestra | el vivero real | una demo determinística |
| Para qué | operar y probar el sistema | mostrar cómo se vería, sin levantar nada |
| Necesita backend | sí | no |

Se resuelve **al arrancar**, así que cambiar de modo exige reiniciar el dashboard. A cambio,
ninguna vista consulta el modo al backend: el backend no tiene modos.

> ¿Querés probar el sistema real sin hardware físico? Eso no es el modo estático: es el
> **simulador** (`Desarrollo/simulador/`), una app aparte que publica telemetría al broker
> como si fuera un nodo ESP32. Corré `npm run dev` acá y el simulador en su carpeta.

## Variables de entorno

Solo las variables con prefijo `VITE_` llegan al navegador.

| Variable | Default | Descripción |
|----------|---------|-------------|
| `VITE_DATA_SOURCE` | `mock` | Modo de la app: `http` (backend real) o `mock` (demo estática). Rige para todas las secciones |
| `VITE_API_BASE_URL` | `http://localhost:8000/api` | URL del backend (modo `http`) |
| `VITE_MOCK_SEED` | `20260613` | Semilla del generador determinístico (modo `mock`) |

## Arquitectura

Feature-based + atomic design. La regla de oro: **la UI nunca sabe de dónde vienen
los datos**, solo consume la capa de datos.

```
src/
├── styles/        tokens.css (design tokens), global.css (reset, fuentes, animaciones)
├── types/         domain.ts — contratos del dominio (Sector, Diagnosis, Metric, ...)
├── lib/           rng.ts — RNG determinístico (mulberry32)
├── data/          ← CAPA DE DATOS (patrón Repository)
│   ├── repository.ts      interface DataRepository
│   ├── mock/              MockRepository + generadores (portados del diseño)
│   ├── http/              HttpRepository (cliente del backend)
│   ├── selectors.ts       derivaciones puras (detalle de sector)
│   └── index.ts           getRepository() — factory por entorno
├── hooks/         NurseryContext (provider + useNurseryData), PageMeta, useSectorDetail
├── components/
│   ├── ui/        átomos: Card, Badge, StatusDot, ProgressBar, Sparkline, Icon
│   └── layout/    AppLayout, Sidebar, Topbar, AlertsDropdown
├── features/      ← una carpeta por vista del producto
│   ├── dashboard/      Panel general
│   ├── map/            Mapa de producción
│   ├── sector/         Detalle de sector
│   ├── diagnostics/    Diagnósticos de IA
│   └── placeholder/    Módulos no incluidos en la demo
├── router.tsx     rutas (react-router)
├── App.tsx        NurseryProvider + RouterProvider
└── main.tsx       entrypoint
```

### Del mock al backend real

El diseño genera todos los datos en el cliente con un RNG sembrado. Acá eso vive
detrás de `MockRepository`. Cuando el backend exista:

1. Implementás los endpoints (empezando por `GET /nursery`).
2. En `.env`: `VITE_DATA_SOURCE=http` y `VITE_API_BASE_URL=<tu-backend>`.

**No se toca ni un componente.** Esa es la ventaja del patrón Repository.

## Sistema de diseño

Los tokens del diseño viven en `src/styles/tokens.css` como CSS custom properties.
Los estilos estáticos van en CSS Modules; los valores dinámicos (color de un sector,
ancho de una barra) se aplican inline porque dependen de datos en runtime.

## Documentación de cambios

Este frontend se planificó con **OpenSpec** (spec-driven development). Ver el cambio
`openspec/changes/add-monitoring-frontend/` en la raíz del repo: proposal, design,
tasks y specs por capacidad.
