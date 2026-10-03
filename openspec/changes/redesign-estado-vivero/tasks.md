# Tasks: redesign-estado-vivero

## 1. Fundamentos
- [x] 1.1 Token `--off-soft` y íconos `sensor`, `wifi-off`, `camera`
- [x] 1.2 Tests (rojos) de `resumenVivero.ts`
- [x] 1.3 Implementar `resumenVivero.ts`
- [x] 1.4 Tests (rojos) de `geometriaSector.ts`
- [x] 1.5 Implementar `geometriaSector.ts`

## 2. Panel general
- [x] 2.1 `NodoChip`, `PlanoVivero` y `ComoLeer`
- [x] 2.2 `EstadoVivero` y `ZonaBlock` con grilla acotada
- [x] 2.3 Integrar en `DashboardPage`; eliminar `ViveroOverview`

## 3. Detalle del sector
- [x] 3.1 `SectorDiagram` (SVG), leyenda y nodo testigo aparte
- [x] 3.2 Breadcrumb, explicador y `<details>` "¿Qué estoy viendo?"
- [x] 3.3 Nota de alcance del diagnóstico; integrar en `SectorPage` conservando lo existente

## 4. Cierre
- [x] 4.1 `npm test`, `npm run lint`, `npm run build`
- [x] 4.2 Arranque de `dev:demo` y verificación de rutas
- [ ] 4.3 Actualizar specs vivas (`dashboard`, `sector-detail`) al archivar
