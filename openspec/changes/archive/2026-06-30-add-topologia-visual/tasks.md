## 1. Backend — persistencia de la disposición

- [x] 1.1 Crear `model/TopologiaLayoutEntity.java` (fila única `id=1`, columnas `macro_zonas_por_fila`, `sectores_por_fila`), siguiendo `ConfiguracionOperativaEntity`
- [x] 1.2 Crear `repository/TopologiaLayoutRepository.java` (`JpaRepository`)
- [x] 1.3 Sembrar la fila por defecto `(1, 3, 10)` en `resources/data.sql`
- [x] 1.4 Crear `dto/DisposicionTopologia.java` (record `{ int macroZonasPorFila, int sectoresPorFila }`)
- [x] 1.5 Agregar `macroZonasPorFila` y `sectoresPorFila` a `dto/TopologiaVivero.java` y a `dto/NuevaTopologia.java`

## 2. Backend — servicio, endpoint y snapshot

- [x] 2.1 `TopologiaService.getTopologia()` lee la disposición del repo y la incluye en `TopologiaVivero`
- [x] 2.2 `TopologiaService.generar(dto)` persiste la disposición del payload, clampada a las cantidades
- [x] 2.3 `TopologiaService.actualizarDisposicion(DisposicionTopologia)`: valida rangos (≤ macro-zonas / ≤ sectores por macro-zona) con `TopologiaInvalidaException`, guarda sin tocar zonas/sectores/dispositivos/historial
- [x] 2.4 `TopologiaController`: agregar `PUT /api/topologia/disposicion` → `actualizarDisposicion`, reusando el handler de `TopologiaInvalidaException` (400)
- [x] 2.5 Agregar la disposición a `dto/NurseryData.java` (campo `layout` o dos ints) y poblarla en `NurseryService` desde `TopologiaLayoutRepository`

## 3. Frontend — tipos y capa de datos

- [x] 3.1 `src/types/domain.ts`: extender `TopologiaVivero` y `NuevaTopologia` con `macroZonasPorFila`/`sectoresPorFila`; agregar `DisposicionTopologia`; agregar `layout` a `NurseryData`
- [x] 3.2 `src/data/repository.ts`: agregar `guardarDisposicion(input: DisposicionTopologia): Promise<TopologiaVivero>`
- [x] 3.3 `src/data/mock/topologia.ts`: constantes `DEFAULT_MACRO_ZONAS_POR_FILA=3`, `DEFAULT_SECTORES_POR_FILA=10`; `topologiaSummary` incluye disposición; nueva `disposicionError(...)` (espejo del backend)
- [x] 3.4 `src/data/mock/generators.ts`: `TopologiaGrid` incluye la disposición; `buildNursery` setea `NurseryData.layout`
- [x] 3.5 `src/data/mock/mockRepository.ts`: guardar disposición en `topologiaOverride`; implementar `guardarDisposicion` (solo invalida el resumen, no la flota/sectores); incluir `layout` en `getNursery`
- [x] 3.6 `src/data/http/httpRepository.ts`: enviar disposición en el payload de `generarTopologia`; `guardarDisposicion` → `PUT ${baseUrl}/topologia/disposicion`; mapear `layout` del snapshot
- [x] 3.7 `src/hooks/useTopologia.ts`: exponer `guardarDisposicion`

## 4. Frontend — preview interactivo

- [x] 4.1 Crear `src/features/topologia/components/TopologiaPreview.tsx`: miniatura que replica el panel general con celdas placeholder; columnas dinámicas vía inline `gridTemplateColumns`
- [x] 4.2 Implementar los dos grips de resize con Pointer Events (`setPointerCapture`): grip de macro-zona → `sectoresPorFila`; grip del contenedor → `macroZonasPorFila`; delta px → columnas (ancho de celda medido), snap a entero y clamp
- [x] 4.3 Crear `src/features/topologia/components/TopologiaPreview.module.css`
- [x] 4.4 `TopologiaPage.tsx`: integrar el preview, estado de disposición inicializado desde `data`, inputs numéricos sincronizados con el arrastre, acción "Guardar disposición" (no destructiva) y "Generar/Regenerar" incluyendo la disposición; extender `Topologia.module.css`

## 5. Frontend — aplicar la disposición a los paneles reales

- [x] 5.1 `ViveroOverview.tsx` + `.module.css`: aplicar `gridTemplateColumns` dinámico en `.zonaGrid` (macro-zonas/fila) y `.heatmap` (sectores/fila) desde `layout`; derivar el subtítulo hardcodeado de los datos
- [x] 5.2 `MapPage.tsx` + `SectorGrid.tsx` + `.module.css`: pasar `sectoresPorFila` desde el snapshot y aplicar columnas dinámicas; derivar el subtítulo y quitar el "10×10 / 100 sectores" fijo

## 6. Tests y verificación

- [x] 6.1 `src/data/mock/topologia.test.ts`: casos de defaults de disposición, summary con disposición y validación de rangos (`disposicionError`)
- [x] 6.2 `npm run lint` (0 warnings) y `npm run test` en verde
- [x] 6.3 Verificación manual: con default 3/10 panel general y mapa idénticos a hoy; arrastre y guardado de disposición funcionan; cambiar cantidades sigue pidiendo confirmación; backend valida 400 fuera de rango
