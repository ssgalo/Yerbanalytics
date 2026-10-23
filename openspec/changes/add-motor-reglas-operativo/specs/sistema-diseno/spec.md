## ADDED Requirements

### Requirement: Interfaz sin emojis
El dashboard SHALL NOT mostrar emojis ni pictogramas Unicode, vengan del frontend, del mock o de un texto del backend
(etiquetas de reglas incluidas). Un ícono, un estado o una marca SHALL dibujarse con `Icon`, `StateGlyph` o `Chip`. Un test
SHALL fallar si aparece un carácter `\p{Extended_Pictographic}` en `src/**/*.{ts,tsx,json}`.

#### Scenario: Emoji en una etiqueta
- **WHEN** se agrega un emoji a un texto de `src/` o del fixture del mock
- **THEN** el test de la interfaz sin emojis falla

#### Scenario: Condición fija en el grafo
- **WHEN** el grafo del motor marca una condición que no se edita
- **THEN** usa `Icon name="lock"` con texto accesible

### Requirement: Velo de diálogos con token
El velo detrás de un diálogo modal SHALL usar el token `--scrim` de `tokens.css`, no un `rgba` suelto.

#### Scenario: Popup del razonamiento
- **WHEN** se abre el razonamiento del motor desde el historial
- **THEN** el fondo del velo es `var(--scrim)`

### Requirement: Tokens de «Compartido»
`tokens.css` SHALL definir `--shared-soft`, `--shared-line` y `--shared-ink` (con su espejo en `theme.ts` si se necesita en JS)
para marcar un parámetro que usan varias reglas. No son colores de estado ni de acento. El catálogo `/sistema-diseno` SHALL
listarlos.

#### Scenario: Catálogo
- **WHEN** se abre `/sistema-diseno`
- **THEN** la sección Color muestra los tres tokens de Compartido
