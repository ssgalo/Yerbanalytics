## Why

Hoy generar la topología es solo cargar dos números (`macroZonas`, `sectoresPorMacroZona`)
en un formulario, sin forma de ver cómo quedará la grilla ni de controlar cómo se acomoda en
pantalla. Además, la disposición en filas del panel general y del mapa está hardcodeada en CSS
(`repeat(3,1fr)` para macro-zonas, `repeat(10,1fr)` para sectores), asumiendo el caso fijo
6×100: cualquier otra forma de vivero se sigue dibujando con esos valores. El Administrador
necesita una creación de topología **visual** y poder definir y previsualizar la disposición.

## What Changes

- Nuevo **preview interactivo** en la pestaña Topología que replica el aspecto del panel
  general usando los valores del formulario.
- El Administrador define la **disposición por fila**: cuántas macro-zonas por fila
  (`macroZonasPorFila`, default 3) y cuántos sectores por fila dentro de cada macro-zona
  (`sectoresPorFila`, default 10).
- La disposición se ajusta **arrastrando desde la esquina** del preview (estilo ventana de
  Windows) además de por input numérico. El resize controla **solo la disposición por fila**;
  las cantidades (`macroZonas`/`sectoresPorMacroZona`) siguen siendo inputs numéricos.
- La disposición se **persiste** y la aplican el panel general (`ViveroOverview`) y el mapa
  (`SectorGrid`) reales, reemplazando los `grid-template-columns` fijos por columnas dinámicas.
- Nueva acción **"Guardar disposición"**, **no destructiva** (no regenera la grilla). Cambiar
  las cantidades sigue disparando la regeneración destructiva con su confirmación actual.
- Backend: persiste la disposición en una entidad de fila única (patrón
  `ConfiguracionOperativaEntity`), expone `PUT /api/topologia/disposicion`, incluye la
  disposición en `GET /api/topologia` y en el snapshot `GET /api/nursery`.

Sin breaking changes: con la disposición default (3 / 10) el panel general y el mapa se ven
idénticos a hoy.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `gestion-topologia`: el panel de topología agrega preview interactivo, edición de la
  disposición por fila (resize + input) y una acción de guardado de disposición no destructiva.
- `topologia-persistencia`: el backend persiste y expone la disposición por fila y permite
  actualizarla sin regenerar la grilla.
- `dashboard`: el panel general renderiza las macro-zonas y los sectores según la disposición
  por fila configurada, en vez de una disposición fija.
- `production-map`: el mapa de producción renderiza los sectores de la macro-zona según la
  disposición por fila configurada, en vez de una disposición fija.

## Impact

- **Frontend** (`Desarrollo/frontend`): `src/types/domain.ts`, `src/data/repository.ts`,
  `src/data/mock/{topologia,generators,mockRepository}.ts`, `src/data/http/httpRepository.ts`,
  `src/hooks/useTopologia.ts`, nueva `src/features/topologia/components/TopologiaPreview.tsx`,
  `src/features/topologia/TopologiaPage.tsx`, `ViveroOverview`, `MapPage`/`SectorGrid` y sus
  CSS Modules; tests en `src/data/mock/topologia.test.ts`.
- **Backend** (`Desarrollo/backend`): nueva `TopologiaLayoutEntity` + repositorio, DTOs
  `TopologiaVivero`/`NuevaTopologia`/`DisposicionTopologia`, `TopologiaService`,
  `TopologiaController` (`PUT /api/topologia/disposicion`), `NurseryData`/`NurseryService`,
  seed en `resources/data.sql`.
- **API**: nuevo endpoint `PUT /api/topologia/disposicion`; campos nuevos en las respuestas de
  `GET /api/topologia` y `GET /api/nursery` (aditivos).
