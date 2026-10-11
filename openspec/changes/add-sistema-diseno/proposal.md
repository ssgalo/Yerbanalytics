## Why

El rediseño de la interfaz parte de una base común:
tokens de color y tipografía, la forma de cada estado y un juego de componentes. Sin esa base,
cada pantalla que se porte va a inventar sus colores y sus controles, como pasa hoy (hex sueltos,
un naranja de marca casi igual al ámbar de observación, texto secundario a 2,6:1, el estado
transmitido sólo por color, que con daltonismo rojo-verde no se distingue).

Este cambio implementa **sólo el sistema de diseño**. Las pantallas se rediseñan después, en
cambios propios, y todas tienen que respetarlo.

## What Changes

- **Tokens** (`src/styles/tokens.css` y su espejo `theme.ts`): se **mantienen los nombres
  actuales y cambian sus valores** según la tabla de equivalencias del prototipo. Se agregan los
  que faltan (tintas, bordes de aviso, borde de campo, bandeja, escala tipográfica, anchos, radios,
  sombra y foco) y se retiran `--g900/--g800/--g700`, `--orange/--orange-soft` y
  `--cream/--cdim/--cfaint`.
- **Tipografía**: Archivo (variable, ejes de ancho y peso) en tres anchos: expandida para títulos
  y cifras, normal para el cuerpo, semicondensada para tablas y metadatos. Reemplaza a Space
  Grotesk + Hanken Grotesk.
- **Base global**: foco visible común, `prefers-reduced-motion`, cifras tabulares y clase `.sr`.
- **Estado con forma además de color**: círculo lleno (saludable), medio lleno (observación),
  rombo (crítico), aro (sin señal).
- **Componentes del sistema** en `src/components/ui/`: botón, chip de estado, glifo de estado,
  aviso en línea, campo / input / select / input con unidad, control segmentado, chips de filtro,
  interruptor, barra de guardado, esqueleto, estado vacío, banner sin conexión, barra de
  composición, barra de rango, barra de confianza y celda de sector (bandeja y grilla).
- **Catálogo** del sistema en `/sistema-diseno`, sólo en desarrollo (`import.meta.env.DEV`): es la
  referencia viva contra la que se compara una pantalla nueva.
- **Migración de lo que cambia**: todo módulo que use un token retirado, un color de la paleta
  vieja escrito a mano, o la tipografía display, se actualiza al sistema nuevo. Incluye las
  paletas de estado duplicadas en el mock (`data/mock/specs.ts` y afines) y en el backend
  (`NurseryConstants` y servicios que mandan pares de color), que hoy copian los valores viejos:
  en modo `http` los colores llegan del backend y tienen que coincidir.

## Capabilities

### New Capabilities

- `sistema-diseno`: tokens, tipografía, forma de los estados y componentes base que toda vista
  del dashboard usa.

## Impact

- `Desarrollo/frontend/`: `index.html` (fuente), `src/styles/*`, `src/components/ui/*` (nuevos y
  actualizados), CSS Modules y TSX que usan tokens retirados o hex de la paleta vieja, router
  (ruta de catálogo sólo en desarrollo).
- `Desarrollo/backend/`: sólo **valores** hex de las paletas que viajan en los DTO. Ni el contrato
  ni la forma de los DTO cambian.
- `CLAUDE.md` §4: tipografías y regla "todo color sale de un token".

## Fuera de alcance

- Rediseño de pantallas (plano con bandejas, shell responsive con barra inferior, receta de
  decisiones, etc.): cada una va en su cambio, sobre este sistema.
- Contrato semántico con el backend (que el DTO traiga estado y no colores): cambio aparte. Acá sólo
  se alinean los valores.
- El simulador (`Desarrollo/simulador/`) tiene su propia UI y no se toca.
