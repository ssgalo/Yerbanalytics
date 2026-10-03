## Why

Quien no conoce el dominio no entiende qué mira. El Panel general muestra "0 ok · 0 alerta · 1 s/s"
sobre celdas sin contexto, y no hay nada que explique la jerarquía física
**Vivero → Macro-zona (área con 1 nodo testigo) → Sector (4 bandejas) → Bandeja (25 plantines)**.
Además `ViveroOverview.tsx` tiene un bug visible: la grilla de sectores usa
`repeat(n, 1fr)` con `aspect-ratio: 1` sin tope, así que con 1 sector por fila la única celda ocupa
todo el ancho de la tarjeta (el "cuadro gris gigante").

El diseño ya se validó como prototipo estático (`prototipo-vivero-ux.html`); este cambio lo lleva al
frontend real, conectado al `DataRepository`.

## What Changes

- **Panel general**: `ViveroOverview` se reemplaza por dos bloques.
  - *Plano del vivero*: cada macro-zona dibujada como parcela (borde punteado, textura, color por su
    peor estado, badge "MZ-N", ícono de nodo testigo). Tocar una parcela desplaza hasta el bloque
    detallado de la zona. Resumen del vivero en una frase y tira "Cómo leer esta pantalla".
  - *Estado del vivero*: un bloque por macro-zona con resumen en una frase, contadores en palabras,
    chip del nodo testigo (batería/señal) en el encabezado de la zona y grilla de sectores con celdas
    de tamaño acotado (corrige el bug del cuadro gigante).
- **Detalle del sector** (`/sector/:id`): breadcrumb Vivero / zona / sector, explicador de jerarquía,
  dibujo SVG del sector físico (4 bandejas × 25 tubetes, microaspersor compartido, riel), nodo
  testigo mostrado aparte del dibujo, y un `<details>` "¿Qué estoy viendo?". Los 100 tubetes se
  pintan de forma uniforme con el diagnóstico del sector. Se conserva todo lo existente
  (diagnóstico con captura, actuadores, seguimiento, historial).
- **Sin cambios** en backend, contrato de datos ni `DataRepository`. El detalle de zona (`/mapa?zona=`,
  con las 10 métricas y el panel de sensado) se conserva y se enlaza desde cada bloque de zona.

## Capabilities

### Modified Capabilities

- `dashboard`: la "Vista general del vivero" pasa a ser Plano + Estado por macro-zona.
- `sector-detail`: se agrega el dibujo físico del sector, el breadcrumb y el explicador.

## Impact

- `Desarrollo/frontend/src/features/dashboard/` (componentes nuevos, `ViveroOverview` eliminado) y
  `src/features/sector/`; token `--off-soft` en `tokens.css`; tres íconos nuevos en `Icon.tsx`.
- Lógica pura nueva con tests: resúmenes en palabras, peor estado de zona, pluralización,
  columnas de la grilla, geometría del sector.

## Fuera de alcance

- **No se indica de qué plantín/tubete salió la foto**: ese dato no va a existir. El dibujo no
  distingue tubetes; el texto sólo dice que el diagnóstico surge de una foto del sector y se aplica
  al sector completo.
- El switch "vivero real / prueba" y demás andamiaje del prototipo.
- Cambios en `/mapa` (detalle de zona).
