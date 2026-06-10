# Modelos de IA propuestos — Yerbanalytics (detección de anomalías en plantines)

> Documento de trabajo para decidir **en equipo** qué arquitectura usar para el modelo de visión del MVP.
> No es una decisión definitiva: con transfer learning, el backbone es un **componente intercambiable** (ver sección final).

## Contexto y criterios de decisión

Antes de mirar modelos, fijamos las reglas del juego de NUESTRO caso (no es un paper genérico):

- **Tarea:** clasificación de imagen (no detección). El riel/gantry aísla **un plantín por foto** → el modelo solo responde "¿qué tiene esta planta?".
- **Clases (MVP):** Sano, Clorosis, Estrés solar, Daño biótico (+ Colapso opcional). 5 como máximo.
- **Datos escasos:** 0% de datasets públicos de yerba mate. Se entrena con **transfer learning** (datasets donantes de té, café, etc. + pocas fotos reales de yerba). Estimado realista: ~650 fotos reales + data augmentation.
- **Hoy corre en PC; mañana en el borde (edge).** La inferencia final debe poder correr en una Raspberry Pi sin internet (HU-04 CA-05 y HU-13). → conviene un modelo **liviano**.
- **Tiene que ser DEFENDIBLE.** Hay que poder responder "¿por qué este modelo?" con criterio técnico, no con "lo recomendó una IA".

Criterios de evaluación que usamos en la tabla:
1. **Defendibilidad** (¿es el enfoque estándar para esto?)
2. **Facilidad de uso** (curva de aprendizaje / cantidad de código)
3. **Aptitud para edge** (peso, params, despliegue en Raspberry)
4. **Rendimiento con pocos datos**
5. **Camino de evolución** (¿qué tan fácil es pasar a detección en el futuro?)

---

## Tabla comparativa

| Modelo | Params aprox. | Defendibilidad | Facilidad | Edge | Pocos datos | Evolución a detección |
| --- | --- | --- | --- | --- | --- | --- |
| **MobileNetV3-Large** | ~5,4 M | ✅ Alta | 🟡 Media | ✅ Excelente | ✅ Muy buena | 🟡 Cambiar framework |
| **MobileNetV2** | ~3,5 M | ✅ Alta | 🟡 Media | ✅ Excelente | ✅ Muy buena | 🟡 Cambiar framework |
| **EfficientNet-B0 / Lite0** | ~5,3 M | ✅ Alta | 🟡 Media | ✅ Muy buena | ✅ Excelente | 🟡 Cambiar framework |
| **ResNet50** | ~25 M | ✅ Alta (baseline clásico) | ✅ Alta | 🟡 Pesado para Pi | ✅ Buena | 🟡 Cambiar framework |
| **YOLOv8-cls (n/s)** | ~2,7–6,4 M | 🟡 Media | ✅ Muy alta | ✅ Buena (TFLite/ONNX) | ✅ Buena | ✅ Mismo ecosistema |
| **ConvNeXt-Tiny** | ~28 M | 🟡 Media (moderno) | 🟡 Media | 🟡 Pesado | 🟡 Media | 🟡 Cambiar framework |
| **ViT (Vision Transformer)** | ~22 M+ | ❌ Baja para este caso | 🟡 Media | ❌ Pesado | ❌ Necesita muchos datos | ❌ |

---

## Candidatos recomendados

### 1. MobileNetV3-Large  ⭐ (recomendación principal)
CNN diseñada por Google específicamente para correr en dispositivos móviles y edge.

**Pros**
- Diseñada para **edge**: liviana, rápida, corre bien en Raspberry. Coherente con HU-04 CA-05 y HU-13.
- Enfoque de transfer learning **de manual** → defensa técnica sólida ("¿por qué? porque es el estándar para clasificación liviana con pocos datos").
- Pesos pre-entrenados en ImageNet disponibles en Keras y PyTorch.
- Exporta fácil a TFLite/ONNX para el despliegue futuro.

**Contras**
- Algo menos preciso que modelos más pesados (diferencia chica y casi irrelevante con nuestras 5 clases).
- Pasar a detección en el futuro implica cambiar de framework.

### 2. EfficientNet-B0 (o EfficientNet-Lite0 para edge)
La mejor relación precisión/eficiencia de su generación. Lite0 es la variante optimizada para móvil/edge.

**Pros**
- Suele dar la **mejor precisión** de las redes livianas con la misma cantidad de datos.
- Excelente con transfer learning y pocos datos.
- Muy bien documentada, comunidad enorme.

**Contras**
- B0 estándar usa operaciones (swish/SE) un poco menos amigables para algunos edge muy chicos → para eso existe **Lite0**.
- Mismo punto: evolucionar a detección = cambiar de framework.

### 3. MobileNetV2
La versión anterior de MobileNet. **Precedente directo:** el dataset de Palma Aceitera (citado por el equipo) la usó para detección en vivero en etapa de plantín, llegando a 97% de validación.

**Pros**
- Aún más liviana que la V3 → ideal si el edge es muy limitado.
- **Tenemos un paper análogo que la respalda** (vivero + plantín + MobileNetV2) → defensa fortísima.

**Contras**
- Algo inferior a V3/EfficientNet en precisión (diferencia menor).

### 4. ResNet50
El "caballo de batalla" clásico de la visión por computadora. Buen baseline.

**Pros**
- Robustísimo, ultra documentado, fácil de entrenar.
- Excelente como **baseline de comparación** ("probamos contra ResNet50 y obtuvimos X").

**Contras**
- **Pesado para edge** (~25 M params) → corre lento en Raspberry. Sirve más para PC/nube que para el nodo.
- Para nuestro objetivo edge, es matar una mosca a cañonazos.

### 5. YOLOv8-cls (n o s)
El modo clasificación de YOLOv8 (Ultralytics). La propuesta original del equipo.

**Pros**
- **Facilísimo de usar:** se entrena con prácticamente una línea de comando. Ideal si se va a "vibecodear".
- Augmentation y export a ONNX/TFLite **integrados**.
- **Ventaja única:** mismo ecosistema para evolucionar de clasificación → **detección** el día de mañana, sin cambiar de herramienta.

**Contras**
- YOLO nació para **detección**; el modo clasificación es un backbone reconvertido → **no es el estándar académico** para clasificación pura.
- La justificación "lo recomendó GPT" **no es defendible**: si se elige, hay que justificarlo por la facilidad + el camino a detección, no por autoridad.

---

## Modelos NO recomendados (y por qué)

- **ViT / Vision Transformers:** son hambrientos de datos. Con ~650 imágenes reales rinden peor que una CNN. Pesados para edge. Descartado para este caso.
- **ConvNeXt / modelos grandes modernos:** muy buenos pero pesados y sin ventaja real con tan pocas clases y datos. Overkill.
- **Entrenar desde cero (sin transfer learning):** inviable con 0 dataset de yerba. Ni se considera.

---

## Recomendación y nota clave sobre el "lock-in"

**Recomendación para arrancar:** **MobileNetV3-Large** con transfer learning desde ImageNet.
- Si pesa más la facilidad y el futuro detección → **YOLOv8-cls** (justificado con criterio).
- Si querés un baseline fuerte para comparar → sumar **ResNet50** como punto de referencia.

**MUY IMPORTANTE — elegir un modelo NO es casarse con él:**
Con transfer learning, el backbone es una **pieza intercambiable**. Todo el resto del pipeline (datos, etiquetas, splits, augmentation, loop de entrenamiento, métricas) se mantiene igual; cambiar de modelo es cambiar pocas líneas y re-entrenar. Re-entrenar uno de estos modelos sobre ~650 imágenes es cuestión de **minutos a un par de horas**.

> Por eso, lo profesional es **probar 2–3 backbones y comparar por F1 sobre el set de yerba real**, y quedarse con el mejor. Esa comparación es, en sí misma, metodología defendible.

**El esfuerzo caro de este proyecto NO es el modelo: son los DATOS** (conseguir y etiquetar las fotos reales de yerba en Misiones). Ahí hay que poner la energía.
