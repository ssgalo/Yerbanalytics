# Yerbanalytics — Qué cambió y por qué (resumen para el equipo)

> **Para qué es esto.** Explicar, en criollo, la re-ingeniería de diseño que hicimos: **lo que teníamos antes vs. lo que decidimos ahora**, y sobre todo **POR QUÉ**. Si entendemos el porqué, defendemos el proyecto parados.

---

## TL;DR (si solo leés esto)

1. **Movimos el MVP del yerbal adulto a campo → al VIVERO (planta joven).** Es donde la visión artificial rinde más y donde el bucle "detectar → actuar" CIERRA de verdad (el riego y el fertirriego ya existen).
2. **Sacamos todo lo que la realidad del productor desmiente** (riego automático a campo, cámara en tractor, mal de tela detectable, sensores NPK a campo). El campo queda como **teoría de escalabilidad**, no como el MVP.
3. **El criterio es académico, no comercial:** la cátedra evalúa complejidad técnica + problema real + honestidad. NO evalúa si se vende.

---

## ¿Por qué cambiamos? (la razón de fondo)

Cruzamos nuestro diseño original con la **realidad del productor** (llamada con el Tío Pablo, productor de Andresito, Misiones — está documentada en `ConocimientoProductor.md`). Resultado: **varios supuestos no se sostenían físicamente.** Y un proyecto que promete algo que la biología no permite, se cae ante cualquier profesor, aunque no sea experto.

Así que rediseñamos para que TODO lo que decimos sea **real y demostrable**.

---

## Antes vs. Ahora (la tabla que hay que entender)

| Tema | ❌ ANTES (lo que teníamos) | ✅ AHORA (lo que decidimos) | Por qué |
|---|---|---|---|
| **Contexto del MVP** | Yerbal adulto a campo, 5 hectáreas | **Vivero (planta joven)** + establecimiento | La planta adulta es rústica y casi no necesita ayuda; el riesgo real está en la planta joven |
| **El bucle "actuar"** | Riego automático sectorizado a campo | **Fertirriego / aspersión en vivero** (ya existen) | La yerba a campo es de **secano**: NO hay red de riego fija sobre la cual actuar |
| **Media sombra** | Automatizada sobre yerbal adulto | Sobre **plantín** (sombráculo / tablita) | En planta adulta de varios metros no es realista; en plantín sí |
| **Visión artificial** | Plagas (taladro, etc.) desde **tractores** | Foliar y detectable: **clorosis, damping-off, plaga de brote** (vivero) + **rulo, golpe de sol** (escala) | La cosecha es 1 vez/año y el productor mediano no usa máquina → cámara en tractor = inútil. El taladro vive dentro del tronco → no se ve |
| **Dónde vive la cámara** | En maquinaria móvil | **Fija sobre riel en vivero** / captura móvil a campo | Una cámara fija cubre miles de plantines; el tractor pasa una vez al año |
| **Sensores NPK** | Red NPK en campo | **Apoyo del fertirriego en vivero** | Los NPK baratos son imprecisos y el problema del suelo a campo es el pH/aluminio (se corrige una vez, antes de plantar) |
| **Mal de tela** | Se "detectaba" con la cámara | **Fuera del bucle** → módulo de software topográfico | Es un hongo incurable que se PREVIENE no plantando en los bajos, no se cura |
| **Dataset de imágenes** | Listado como recurso que "teníamos" | **Recurso a construir**: fotos propias en Misiones + etiquetado del agrónomo | Era nuestro riesgo #1; ahora tenemos un plan real |
| **Demo física** | Una "planta" genérica | **Prototipo de banco**: plantín + ESP32 + Raspberry Pi + sensores + actuadores a escala | Es lo que realmente podemos construir y mostrar |
| **Escalabilidad** | Red a 5 ha (poco aterrizada) | **Teoría**: nodos por zona + gateway + backend multi-zona | Se diseña, no se construye. Igual que "Nodo MVP vs red" |

---

## Cómo escala cada cosa (la pregunta que nos van a hacer)

**La regla:** cada componente escala distinto. Nunca es "un nodo completo por planta" (eso sería carísimo e inviable).

| Componente | 🌱 Cómo escala en VIVERO | 🌳 Cómo escala en CAMPO (años 1-2) |
|---|---|---|
| **🌡️ Sensores** (ambiente) | Pocos nodos por **mesada/sector** (el ambiente es uniforme) cubren miles de plantines | Un nodo por **zona de microclima** (lomas, bajos, curvas de nivel), autónomo con **solar + batería** |
| **📷 Cámara** (salud por planta) | Una cámara sobre **riel/gantry** que barre las mesadas → cubre miles de plantines | **Captura móvil**: el **celular** del productor en sus recorridas / un **rover** por las calles de 3 m / drone a futuro |
| **⚙️ Actuadores** (acción) | **Electroválvulas por sector** sobre el riego/fertirriego que YA existe | No hay riego fijo (secano) → se degrada a **alerta/recomendación**; el productor actúa (tractor cisterna, tablita) |

**Frase para defenderlo:** *"Los sensores escalan por zona, la cámara por barrido y los actuadores por sector. No medimos ni actuamos planta por planta: aprovechamos que el ambiente y la infraestructura se comparten."*

---

## Lo que SE MANTIENE (no tiren nada de lo hecho)

El **núcleo técnico sigue intacto** — solo lo apuntamos al lugar correcto:
- IoT con sensores + actuadores.
- **Visión artificial (Edge AI)** corriendo local.
- **Motor de decisiones** que cruza visión + sensores + clima.
- **Integración climática** (ahora con Open-Meteo / SMN / INTA, no solo OpenWeatherMap).
- **Trazabilidad** y tablero web.
- **Pipeline MLOps** (dataset, reentrenamiento).
- El **cronograma de 7 hitos** y la idea de "Nodo MVP vs. red a escala".

> La complejidad de ingeniería que necesitamos para la nota **está toda ahí**. No perdimos ambición técnica: la enfocamos.

---

## El caso estrella (lo que mostramos funcionando)

**En el vivero, el bucle cierra completo:**

> La cámara detecta **clorosis** → el motor decide → acciona el **fertirriego que ya está instalado** → re-fotografía y verifica si la planta mejora.

Sensar → inferir → actuar → verificar, sin trampa. **Eso es exactamente la consigna del proyecto.**

Y como combinación para mostrar lo máximo: **traemos un plantín con RULO desde Misiones** y la cámara lo detecta en vivo (el rulo es de campo/escala, pero lo demostramos físicamente).

---

## Lo que decidimos NO hacer (y por qué)

- **Nodo Bi-modo** (un nodo que cambia solo entre "modo vivero" y "modo campo"): lo evaluamos y lo **descartamos**. Sumaba mucha complejidad de software y ponía en riesgo terminar en fecha. **Foco > ambición.**

---

## Qué falta / qué traemos del viaje a Misiones

- 📸 **Fotos para el dataset:** damping-off, clorosis, plaga de brote, plantines sanos y **rulo**.
- 🌱 **Un plantín con rulo** para la demo.
- ✅ Validar con el productor: ¿el cliente opera vivero propio o compra plantines?, ¿el vivero tiene luz/internet?

---

## Cómo explicarlo en una frase

> "Antes apuntábamos al yerbal adulto a campo, pero el productor nos confirmó que ahí no hay infraestructura para actuar (es a secano) y la cámara casi no sirve. Así que movimos el MVP al **vivero**, donde la planta joven está en riesgo real, la visión artificial rinde al máximo y podemos **detectar un problema y actuar de verdad** sobre el riego y la nutrición que ya existen. El campo queda como la visión de escalabilidad. Mismo músculo técnico, apuntado a donde el problema es real."

---

*Referencias: `Documentacion/MVP/1_NodoDeEstablecimiento.md` (diseño completo) · `cuestionables_punto1y2.md` (cuestionables y mitigaciones) · `Documentacion/ConocimientoProductor.md` (la realidad de campo).*
