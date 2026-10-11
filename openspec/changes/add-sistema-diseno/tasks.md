# Tasks: add-sistema-diseno

## 1. Base
- [x] 1.1 `tokens.css`: valores nuevos con los nombres de siempre, tokens nuevos, retiros
- [x] 1.2 `theme.ts` espejado
- [x] 1.3 `index.html`: Archivo (wdth 62–125, wght 100–900)
- [x] 1.4 `global.css`: foco visible, reduced motion, cifras tabulares, `.sr`, keyframes con tokens

## 2. Componentes (`src/components/ui/`)
- [x] 2.1 `Button`, `Chip`, `StateGlyph`, `Callout`
- [x] 2.2 `Field`, `Input`, `Select`, `InputUnit`
- [x] 2.3 `Segmented`, `FilterChip`, `Switch`
- [x] 2.4 `SaveBar`, `Skeleton`, `EmptyState`, `OfflineBanner`
- [x] 2.5 `CompositionBar`, `RangeBar`, `ConfidenceBar`, `SectorCell`
- [x] 2.6 `Card`, `Badge`, `ProgressBar`, `StatusDot` alineados a los tokens
- [x] 2.7 Tests de accesibilidad de los componentes con estado

## 3. Migración
- [x] 3.1 Usos de `--g900/--g800/--g700`, `--orange*`, `--cream/--cdim/--cfaint`
- [x] 3.2 `color: var(--faint)` de texto → `--muted`; `color: var(--warn)` → `--warn-ink`
- [x] 3.3 `font-stretch: var(--w-exp)` junto a cada `--font-display`
- [x] 3.4 Hex de la paleta vieja en CSS/TSX y en el mock
- [x] 3.5 Valores hex de las paletas del backend

## 4. Catálogo y cierre
- [x] 4.1 `/sistema-diseno` sólo en desarrollo
- [x] 4.2 `CLAUDE.md` §4 y README del frontend
- [x] 4.3 `npm test`, `npm run lint`, `npm run build`; tests del backend afectados
- [x] 4.4 Verificación visual en el navegador
