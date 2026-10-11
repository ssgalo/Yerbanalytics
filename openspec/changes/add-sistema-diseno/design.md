## Context

El prototipo estático del rediseño trae una página "Sistema de diseño" con
tokens, tipografía, estados, componentes, estados de pantalla y una tabla de equivalencias contra
el `tokens.css` de hoy. Sus nombres de token (`--papel`, `--monte`, `--obs`…) son del prototipo.

## Decisions

### D1 — Se conservan los nombres de token; cambian los valores
Lo recomienda el propio prototipo: así un CSS Module que usa `--ink` o `--warn-soft` queda bien sin
tocarlo. Equivalencias (prototipo → token del repo):

| Prototipo | Token | Valor |
|---|---|---|
| papel / hoja / hoja-2 | `--bg` / `--card` / `--card-2` | #f1f3ee / #ffffff / #f7f8f5 |
| monte / monte-2 | `--ink` / `--ink-2` | #15231b / #2b3b32 |
| gris / gris-claro | `--muted` / `--faint` | #5b6a61 / #7d8a82 |
| linea / linea-suave / borde-campo | `--line` / `--line2` / `--line-field` | #dde2da / #e9ece6 / #8d978f |
| yerba / yerba-suave | `--brand` / `--brand-soft` | #2c6a4f / #e6efe9 |
| ok, obs, crit, off, agua | `--ok`, `--warn`, `--crit`, `--off`, `--info` (+ `-soft`, `-ink`) | ver tokens.css |
| bandeja / -2 / -linea | `--tray` / `--tray-2` / `--tray-line` | #1c2420 / #2a332e / #39433d |

Nuevos además: `--off-ink`, `--*-line` (borde de los avisos), `--on-tray`, `--on-tray-muted`,
escala `--t-12…--t-48`, anchos `--w-exp`/`--w-cond`, radios `--r-s/m/l`, `--shadow-overlay`,
`--focus-ring`.

### D2 — Retiros y a qué se migra
- `--g900/--g800/--g700`: había tres oscuros. Queda **uno**, `--tray`, y sólo para lo que dibuja el
  vivero (plano, grillas, el dibujo del sector) y superficies de arte como el panel del login. Un
  oscuro usado como *selección* (pestaña activa, botón elegido) pasa a `--ink`, que es el color
  del botón primario. Un oscuro usado como *texto* pasa a `--ink` o a `--brand`.
- El sidebar deja de ser oscuro: superficie `--card`, ítem activo con `--brand-soft` y una barra
  `--brand`. Sólo cambia el color; estructura y navegación quedan como están.
- Los grafos del motor (React Flow) siguen oscuros hasta su rediseño, pero sobre `--tray`.
- `--orange/--orange-soft`: se confundía con el ámbar (1,3:1). Lo que era advertencia pasa a
  `--warn-*`; lo que era "seleccionado" pasa a `--brand`.
- `--cream/--cdim/--cfaint`: texto sobre oscuro = `--on-tray` (#fff) y `--on-tray-muted` (#a9b5ae).
- `--faint` sigue existiendo pero **no es para texto** (3,6:1): los `color: var(--faint)` de texto
  pasan a `--muted`.
- `--warn` (ámbar, 2,4:1) **no es color de texto**: se usa `--warn-ink`. Lo mismo para cualquier
  estado: el relleno es `--x`, el texto es `--x-ink`.

### D3 — Archivo desde Google Fonts, con los dos ejes
`family=Archivo:wdth,wght@62..125,100..900`. El prototipo la embebe en base64 para ser un único
archivo; el dashboard ya cargaba sus fuentes de Google Fonts y sigue así. `--font-display` y
`--font-body` apuntan las dos a Archivo para no tocar 180 usos; lo que distingue al display es el
ancho, así que cada regla que usa `--font-display` declara también `font-stretch: var(--w-exp)`.

### D4 — Componentes sin dependencias, un CSS Module cada uno
Viven en `src/components/ui/`, son presentacionales, reciben el estado por props con el tipo de
dominio (`Status`) y no consultan datos. Accesibilidad incluida: `aria-pressed` en segmentado y
chips de filtro, `role="switch"` + `aria-checked` en el interruptor, `aria-invalid` en campos con
error, texto oculto (`.sr`) con el nombre del estado en glifos y celdas.

### D5 — El estado tiene forma
`StateGlyph` y `SectorCell` dibujan la forma con CSS (no SVG), igual que el prototipo: círculo
lleno, mitad inferior llena con contorno en tinta, rombo, aro. El contorno en tinta es lo que deja
al ámbar y al gris por encima de 3:1 sobre fondo claro.

### D6 — Catálogo sólo en desarrollo
`/sistema-diseno` se registra en el router sólo si `import.meta.env.DEV`. No aparece en el menú ni
en el build de producción, y no pide permiso: no muestra datos del vivero.

### D7 — Los colores que llegan del backend se alinean por valor
`NurseryConstants`, `HardwareService`, `DispositivoCamaraService`, `HistorialService` y
`TopologiaService` mandan pares hex. Se cambian sus valores a los tokens nuevos; la forma del DTO
no cambia. Que el backend deje de mandar colores es el "contrato semántico", fuera de alcance.

## Risks

- Cambia el aspecto de todas las pantallas a la vez (colores y fuente). Es buscado: es el sistema
  común. Las pantallas no cambian de estructura.
- Archivo depende de la red como antes Space Grotesk; sin red cae a `system-ui`.
