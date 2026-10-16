## ADDED Requirements

### Requirement: Fuente única de color
Todo color del dashboard SHALL salir de un token de `src/styles/tokens.css` (o de su espejo
`theme.ts` cuando hace falta en JS). Los únicos colores saturados SHALL ser los de estado
(`--ok`, `--warn`, `--crit`, `--off`) y el de acción del sistema (`--info`). El único fondo oscuro
SHALL ser `--tray`, reservado para lo que dibuja el vivero y superficies de arte.

#### Scenario: Token retirado
- **WHEN** se busca en `src/` un uso de `--g900`, `--g800`, `--g700`, `--orange`, `--orange-soft`,
  `--cream`, `--cdim` o `--cfaint`
- **THEN** no hay ninguno

#### Scenario: Paleta vieja
- **WHEN** se busca en `src/` y en el backend un hex de la paleta de estados anterior
  (#3FA06A, #E0972C, #DD5238, #A9B2AB, #3B82F6, #EC6A1E)
- **THEN** no hay ninguno

### Requirement: Contraste del texto
El texto SHALL tener al menos 4,5:1 sobre su fondo. El texto secundario SHALL usar `--muted`; el
texto de un estado SHALL usar su tinta (`--ok-ink`, `--warn-ink`, `--crit-ink`, `--off-ink`,
`--info-ink`), nunca el relleno. `--faint` SHALL usarse sólo para íconos y decoración. Los bordes de
campos SHALL usar `--line-field` (3:1).

#### Scenario: Texto en ámbar
- **WHEN** un texto comunica "en observación"
- **THEN** su color es `--warn-ink`, no `--warn`

### Requirement: Tipografía Archivo en tres anchos
El dashboard SHALL usar Archivo (variable) en tres anchos: expandida (`--w-exp`, 118 %) para
títulos, códigos y cifras; normal para el cuerpo; semicondensada (`--w-cond`, 86 %) para tablas y
metadatos. Toda regla con `--font-display` SHALL declarar `font-stretch: var(--w-exp)`.

#### Scenario: Título de página
- **WHEN** se muestra un título con la fuente display
- **THEN** se dibuja en Archivo expandida

### Requirement: El estado no depende sólo del color
Cada estado de salud SHALL tener además una forma: círculo lleno (Saludable), círculo medio lleno
con contorno (En observación), rombo (Crítico) y aro vacío (Fuera de servicio / sin señal). Los
glifos y celdas SHALL incluir el nombre del estado como texto accesible cuando no hay otro texto
que lo diga.

#### Scenario: Glifo de observación
- **WHEN** se dibuja `StateGlyph` con estado `warning`
- **THEN** se ve un círculo con la mitad inferior en ámbar y contorno en `--warn-ink`

### Requirement: Componentes del sistema
`src/components/ui/` SHALL ofrecer: `Button`, `Chip`, `StateGlyph`, `Callout`, `Field`, `Input`,
`Select`, `InputUnit`, `Segmented`, `FilterChip`, `Switch`, `SaveBar`, `Skeleton`, `EmptyState`,
`OfflineBanner`, `CompositionBar`, `RangeBar` y `SectorCell`. Una vista nueva o
rediseñada SHALL usarlos en lugar de reimplementar el control.

#### Scenario: Selección accesible
- **WHEN** se elige una opción de `Segmented` o se activa un `FilterChip`
- **THEN** el botón elegido expone `aria-pressed="true"` y el resto `"false"`

#### Scenario: Interruptor
- **WHEN** se renderiza un `Switch` encendido
- **THEN** tiene `role="switch"` y `aria-checked="true"`

#### Scenario: Campo con error
- **WHEN** un `Input` o `InputUnit` recibe `invalid`
- **THEN** expone `aria-invalid="true"` y borde `--crit`

### Requirement: Foco visible
Todo elemento enfocable con teclado SHALL mostrar el anillo `--focus-ring` (2 px de `--card` y 2 px
de `--ink`). Con `prefers-reduced-motion: reduce` las animaciones y transiciones SHALL anularse.

#### Scenario: Navegar con Tab
- **WHEN** el usuario enfoca un botón con el teclado
- **THEN** se ve el anillo de foco

### Requirement: Catálogo del sistema en desarrollo
En modo desarrollo el dashboard SHALL servir `/sistema-diseno` con los tokens, la tipografía, los
estados y todos los componentes. En el build de producción la ruta SHALL no existir.

#### Scenario: Build de producción
- **WHEN** se navega a `/sistema-diseno` en un build de producción
- **THEN** la ruta no está registrada
