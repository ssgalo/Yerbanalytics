# Tasks: add-monitoring-frontend

## 1. Scaffold y tooling
- [x] 1.1 `package.json` con Vite + React 18 + TS + react-router + Vitest
- [x] 1.2 `vite.config.ts`, `tsconfig.json`, `tsconfig.node.json`
- [x] 1.3 ESLint + Prettier (`.eslintrc.cjs`, `.prettierrc`)
- [x] 1.4 `.gitignore`, `index.html`, `env.example`
- [x] 1.5 `README.md` del frontend

## 2. Sistema de diseño
- [x] 2.1 `src/styles/tokens.css` — todos los tokens de color del HTML como `:root` vars
- [x] 2.2 `src/styles/global.css` — reset, fuentes (Space Grotesk + Hanken Grotesk), keyframes (ybFade, ybPulse, ybBlink), scrollbar
- [x] 2.3 `src/styles/theme.ts` — espejo TS de tokens para uso puntual en JS

## 3. Dominio y datos
- [x] 3.1 `src/types/domain.ts` — tipos del dominio
- [x] 3.2 `src/lib/rng.ts` — `createRng`, `pick`, `rr` (portados EXACTOS)
- [x] 3.3 `src/data/mock/specs.ts` — specs de métricas, zonaDefs, paletas, tints, sevMap
- [x] 3.4 `src/data/mock/generators.ts` — porta `build()`, `makeSector()`, `series()`, `pathFrom()`
- [x] 3.5 `src/data/mock/sectorDetail.ts` — porta `buildDetail()`
- [x] 3.6 `src/data/repository.ts` — interface `DataRepository`
- [x] 3.7 `src/data/http/httpRepository.ts` — cliente HTTP del backend
- [x] 3.8 `src/data/index.ts` — factory `getRepository()` según `VITE_DATA_SOURCE`
- [x] 3.9 `src/hooks/NurseryContext.tsx` (Provider + `useNurseryData`)
- [x] 3.10 Test: `generators.test.ts` valida fidelidad/determinismo del RNG

## 4. Átomos de UI compartidos
- [x] 4.1 `Icon.tsx` + `Glyph` — set de íconos SVG del diseño
- [x] 4.2 `Card.tsx`, `Badge.tsx`, `StatusDot.tsx`
- [x] 4.3 `ProgressBar.tsx`, `Sparkline.tsx`

## 5. App shell (layout)
- [x] 5.1 `Sidebar.tsx`
- [x] 5.2 `Topbar.tsx`
- [x] 5.3 `AlertsDropdown.tsx`
- [x] 5.4 `AppLayout.tsx` + `PageMeta` (título dinámico)

## 6. Feature: Dashboard (Panel general)
- [x] 6.1 KpiRow, ViveroOverview
- [x] 6.2 PriorityCard, WeatherCard
- [x] 6.3 ActivityFeed, RecentDiagnostics
- [x] 6.4 `DashboardPage.tsx`

## 7. Feature: Mapa de producción
- [x] 7.1 ZonaTabs, SectorGrid
- [x] 7.2 ZoneSummary, ProblemsList
- [x] 7.3 `MapPage.tsx`

## 8. Feature: Detalle de sector
- [x] 8.1 DiagnosisCard, ActuatorsCard
- [x] 8.2 MetricTile, MainChart
- [x] 8.3 PostActionCard, SectorHistory
- [x] 8.4 `SectorPage.tsx`

## 9. Feature: Diagnósticos de IA
- [x] 9.1 DiagFilters, DiagCard
- [x] 9.2 PhotoModal
- [x] 9.3 `DiagnosticsPage.tsx`

## 10. Feature: Placeholder
- [x] 10.1 `PlaceholderPage.tsx`

## 11. Routing y wiring
- [x] 11.1 `router.tsx`
- [x] 11.2 `App.tsx`
- [x] 11.3 `main.tsx`

## 12. Cierre
- [x] 12.1 `npm install` y `npm run build` en verde
- [x] 12.2 `npm run lint` sin errores · `npm test` en verde
- [ ] 12.3 Verificación visual contra el HTML de referencia (pendiente: revisión humana)
