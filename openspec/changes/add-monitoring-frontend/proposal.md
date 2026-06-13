# Change: add-monitoring-frontend

## Why

Yerbanalytics tiene un diseño de alta fidelidad generado con Claude Design
(`Desarrollo/frontend/Yerbanalytics.dc.html`): un dashboard de monitoreo IA para
un vivero de yerba mate (600 sectores, 6 macro-zonas). Hoy ese diseño vive como
un único HTML con estilos inline y toda la lógica de negocio embebida en una
clase `DCLogic`. No es mantenible, no es testeable y no se integra con el backend.

Necesitamos un frontend React real, productivo y bien organizado, que:
- Replique el diseño **exactamente** (tokens, tipografías, animaciones, layout).
- Separe la lógica de dominio de la presentación.
- Esté preparado para consumir el backend real (hoy vacío) sin reescribir la UI.

## What Changes

- **Scaffold** de un proyecto Vite + React 18 + TypeScript en `Desarrollo/frontend/`.
- **Sistema de diseño** portado a tokens CSS + CSS Modules (fidelidad exacta).
- **Capa de datos** con patrón Repository: hoy devuelve el mock determinístico
  (RNG sembrado, portado tal cual del HTML); mañana, cambiando una variable de
  entorno, consume el backend HTTP. Sin tocar la UI.
- **Arquitectura feature-based + atomic design**: cada vista del producto es una
  feature autocontenida; los átomos de UI son compartidos.
- **5 vistas**: Panel general, Mapa de producción, Detalle de sector,
  Diagnósticos de IA, y Placeholder (Historial / Configuración / Hardware).
- **Routing real** con react-router (las vistas dejan de ser `if` y pasan a ser rutas).
- **Calidad**: ESLint + Prettier + Vitest, `.env.example`, README.

## Impact

- Affected specs: `dashboard`, `production-map`, `sector-detail`, `ai-diagnostics`,
  `app-shell`, `data-layer` (todas nuevas).
- Affected code: `Desarrollo/frontend/` (proyecto nuevo). El HTML original se
  conserva como referencia de diseño, no se borra.
- No toca backend ni Modelo_IA.
