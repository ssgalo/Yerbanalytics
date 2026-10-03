## Context

El frontend consume sólo `DataRepository`; `VITE_DATA_SOURCE` decide mock o http y ningún componente
consulta el modo. El prototipo trae datos falsos y una taxonomía fija: se descartan. Se usan
`Zona`, `Sector`, `Status` y `NodoTestigo` de `domain.ts`.

## Decisions

### D1 — Lógica pura aparte del JSX, con tests
`features/dashboard/resumenVivero.ts` (pluralización, peor estado de zona, resumen de zona y de
vivero, contadores, columnas de grilla) y `features/sector/geometriaSector.ts` (bandejas y tubetes
dentro del viewBox). Los componentes sólo dibujan. Es lo que se testea con vitest (entorno node).

### D2 — Peor estado de zona
`lectura.stale` → `offline`; si no, crítico > observación; si todos los sectores están sin señal →
`offline`; si no, `ok`. El resumen "Sin datos del sensor" sale de `lectura.stale`.

### D3 — Grilla acotada, respetando la disposición configurada
Columnas = `min(layout.sectoresPorFila, total)` con pistas `minmax(10px, 22px)` y
`justify-content: center`: el tamaño de celda no depende de cuántos sectores haya. Se conserva la
disposición configurada en la topología (a diferencia del prototipo, que usa `ceil(sqrt(n))`). Las
macro-zonas por fila también salen de `layout`, vía variable CSS (`--cols`) con colapso a 1 columna
en pantallas angostas.

### D4 — Navegación conservadora
Parcela → scroll + destello al bloque de la zona (estado en `DashboardPage`, destello declarativo
por `key`). Celda → `/sector/:id`. Cada bloque suma un enlace "Ver detalle de la zona" a
`/mapa?zona=`, para no perder el panel de sensado, que el prototipo no cubre.

### D5 — El dibujo del sector no señala ningún tubete
Sin dato de qué plantín se fotografió (y sin que vaya a existir), los 100 tubetes se pintan igual con
el color del sector. La leyenda y las notas dicen que el diagnóstico surge de UNA foto del sector y
se aplica al sector completo. El riel se dibuja de lado a lado con la cámara centrada sobre el
sector, sin apuntar a un tubete. El nodo testigo va fuera de la caja del sector: es de la zona.

### D6 — Diagnóstico a nivel sector
El `DiagnosisCard` existente (captura, severidad, confianza) queda visible siempre. Se le suma, junto al dibujo, una
nota con el alcance del diagnóstico y la etiqueta "a futuro" (más fotos por sector).

### D7 — Tokens
Se agrega `--off-soft` (existía sólo como hex suelto). Íconos nuevos (`sensor`, `wifi-off`,
`camera`) en el registro de `Icon`.
