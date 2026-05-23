# Prompts visuales para la entrega del MVP (expo con stands)

> Prompt para pasar a una IA generadora de imágenes (Midjourney, DALL·E, Flux, etc.).
> Escrito en inglés a propósito: los modelos de imágenes rinden mucho mejor en inglés.
> Anclado al MVP **vivero-primario** (con el rulo demostrado como clase de escala) y a la
> imagen de referencia que ya generamos y nos gustó.

## Notas de uso

- `--ar 16:9 --style raw` es sintaxis de **Midjourney**. Si usás **DALL·E** o **Flux**, ignorá esa línea.
- **Para que la imagen salga MUY parecida a la que te gustó:** usá la imagen original como **referencia (img2img / image prompt)**. En Midjourney pegá la URL de la imagen al inicio del prompt; en DALL·E/Flux subila como imagen de referencia. El texto solo no garantiza la misma composición.
- Si la imagen sale recargada, **quitá elementos** del prompt.
- El texto "YERBANALYTICS" puede salir mal escrito (los modelos son malos con el texto). Si te importa el logo, agregalo después en Canva/Figma.

---

## 🌱 PROMPT — Stand vivero + escala (versión mejorada)

> Cambios respecto a la imagen original: cámara sobre **riel/gantry** (no brazo), **bandeja de plantines + 2-3 plantines**, **malla de media sombra (sombráculo)**, **gateway + mini-diagrama de red** (escalabilidad), **sin panel solar**. Se mantiene la detección de **RULO** y todo el estilo/branding que ya gustó.

```
Professional, photorealistic event photograph of a premium university engineering 
final-project expo booth for an agritech IoT startup called "YERBANALYTICS". Keep 
the same polished style, warm wood exhibition table, lighting and overall layout as 
a high-end trade show: LEFT a transparent acrylic node box, CENTER the plants and 
sensing, RIGHT a large dashboard monitor and a tablet.

Backdrop: a softly-lit fabric banner with the YERBANALYTICS logo (a stylized yerba 
mate leaf fused with a glowing green-and-terracotta circuit-node) and the tagline 
"Monitoreo inteligente de la yerba mate", plus small feature icons (IoT inteligente, 
Visión por IA, Riego preciso, Datos que transforman). Blurred expo hall with people 
in the background.

LEFT — the smart node: a clear laser-cut acrylic enclosure labeled "YERBANALYTICS 
SMART NODE" revealing a Raspberry Pi 4 and an ESP32 board with tidy color-coded 
wiring, small status LEDs and an antenna. Beside it, a small GATEWAY device with 
antennas, and a little acrylic info card showing a clean network topology diagram 
"Nodo → Gateway → Nube" with simple node icons (communicating scalability).

CENTER — a small NURSERY (vivero) scene under a miniature shade-mesh canopy 
(sombráculo) overhead that filters light: a seedling tray with several yerba mate 
plantlets in rigid plastic tubetes/cells, plus two or three individual young yerba 
mate plants (Ilex paraguariensis, glossy serrated dark-green leaves) in pots with 
reddish substrate. A small engraved label reads "Ilex paraguariensis (Yerba Mate)". 
Thin capacitive soil-moisture probes and small temperature/humidity sensors are 
inserted, neatly labeled ("Humedad", "Temp. Suelo", "T°/HR Aire"). One front 
plantlet shows slightly deformed, curled tender shoots (rulo symptom). Above the 
seedlings, a horizontal CAMERA RAIL / linear gantry spans across the tray, with a 
small machine-vision camera mounted on a sliding carriage pointing down at the 
plants (one camera scanning many seedlings).

To the right of the plants: a clear water reservoir labeled "DEPÓSITO DE AGUA", and 
two small pumps labeled "BOMBA DE AGUA" and "BOMBA DOSIFICADORA" with clear tubing 
running to the trays (irrigation + NPK fertigation).

RIGHT — a sleek monitor showing a modern dark-mode dashboard UI titled "YERBANALYTICS": 
live line charts ("Humedad del suelo 42%", "Temperatura del aire 24.3°C", "Humedad 
relativa 68%"), a left nav menu (Resumen, Planta, Sensores, Cámara IA, Alertas, Riego, 
Historial, Configuración), a live camera-feed panel labeled "Cámara IA – En vivo" with 
a green bounding box "RULO detectado — 92% — severidad: media", an "Alertas activas" 
column with colored cards, and an "Historial de acciones" timeline ("Riego iniciado", 
"Imagen analizada – RULO 92%", "Fertilización dosificada 50 ml"). A tablet on a wooden 
stand mirrors the app with a "Riego Automático" toggle. A small acrylic card reads 
"Tecnología hecha en Misiones" with a small red province silhouette.

Composition: wide horizontal three-quarter front angle, eye-level, everything sharp 
with a shallow-depth-of-field bokeh background. Bright, clean, diffused modern expo 
lighting with soft shadows. Color palette: yerba green, terracotta red, crisp white, 
warm wood, with glowing teal-and-green UI light. Mood: innovative, credible, polished 
— "agtech startup at a prestigious university science fair".

High-resolution photorealistic product and event photography, shot on a 35mm lens, 
sharp focus, professional color grading. --ar 16:9 --style raw

Negative prompt: no solar panel, no cluttered cables, no messy desk, no gibberish 
text, no warped logos, no distorted hands or faces, no cartoon style, no oversaturation.
```
