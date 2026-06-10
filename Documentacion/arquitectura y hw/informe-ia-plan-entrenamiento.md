# Informe IA — Plan de entrenamiento del modelo de visión (Yerbanalytics)

> **Objetivo del documento:** dejar por escrito TODO lo que hay que hacer, de acá en adelante, para empezar a entrenar el modelo de IA que detecta anomalías en plantines de yerba mate — con las decisiones tomadas, sus por qué, ventajas/desventajas y los pasos concretos.
>
> **Estado actual del proyecto:** solo documentación. Este plan es el puente hacia los primeros **avances técnicos**.
>
> **Alcance de referencia:** HU-04 (análisis visual con IA), HU-05 (consulta de diagnósticos), y la fusión con el motor agronómico (HU-06/07/08).

---

## 1. Resumen ejecutivo

| Decisión | Qué se definió | Por qué |
| --- | --- | --- |
| **Unidad de imagen** | Un **plantín** por foto | El riel/gantry aísla mecánicamente cada planta → el modelo hace **clasificación**, no detección |
| **Tarea** | **Clasificación** de imagen | Más fácil de entrenar, menos datos, sin etiquetar cajas. "El riel es el detector" |
| **Clases** | Sano, Clorosis, Estrés solar, Daño biótico (+ **Colapso opcional**) | Cada clase mapea a una **acción agronómica** distinta |
| **Modelo (provisional)** | **MobileNetV3-Large** (transfer learning) | Liviano (apto edge), defendible, swappable |
| **Estrategia de datos** | **Transfer learning** desde ImageNet + datasets donantes + yerba real | 0% de datasets públicos de yerba mate |
| **Cómputo** | GPU local 8 GB **o** Google Colab (gratis) | Entrenar es liviano; 8 GB sobra |
| **Despliegue** | Hoy **PC**; mañana **edge** (Raspberry) | Hoy itera rápido; el modelo liviano deja la puerta del edge abierta |

> **Mensaje central:** el esfuerzo caro de este proyecto **no es el modelo** (se cambia en una tarde), **son los DATOS reales de yerba**. Ahí va la energía.

---

## 2. Decisiones de diseño (con su justificación)

### 2.1 Encuadre: un plantín + clasificación
El riel con cámara se posiciona y frena sobre **cada plantín**, lo encuadra, saca la foto, y el modelo responde una sola pregunta: *"esta planta, ¿qué tiene?"* → `Clorosis 0.91`.

- **Concepto clave:** la "detección" (ubicar qué planta y dónde) la resuelve **el hardware**, no la red. El riel **es** el detector → el modelo solo clasifica.
- **Ventajas:** clasificación es mucho más fácil de entrenar que detección, necesita menos imágenes, y **no requiere etiquetar bounding boxes** (el trabajo de anotación más pesado).
- **Costo honesto:** a nivel plantín entero se **pierden señales chicas y locales** (una colonia de ácaros en una hoja, una mancha fúngica incipiente). Por eso el daño biótico se trata como clase gruesa (ver 2.2).
- **Evolución futura (esto le gusta al jurado):** en producción, el riel puede hacer una segunda pasada con zoom a nivel hoja y escalar de clasificación → detección, sin rehacer el sistema.

### 2.2 Clases del MVP
Criterio de elección: **cada clase dispara una acción distinta del motor agronómico.** Si dos clases producen la misma acción, no se separan (todavía).

| Clase | ¿Se ve a nivel plantín? | Acción agronómica asociada |
| --- | --- | --- |
| **Sano** | ✅ fácil | Ninguna |
| **Clorosis** | ✅ muy visible (amarillamiento) | Fertirriego / nutrientes (HU-07) |
| **Estrés solar** | ✅ visible (blanqueo, necrosis clara) | Desplegar mediasombra (HU-08) |
| **Daño biótico** (plaga + hongo) | ⚠️ sutil | Fitosanitario + alerta (HU-07/10) |
| **Colapso** (damping-off) — *opcional* | ✅ muy visible (geometría cambia) | Alerta crítica + intervención manual |

**El truco multimodal (activo más fuerte del proyecto):** la cámara dice *"daño biótico 0.84"* y **el sensor desambigua si es plaga u hongo**: humedad de sustrato >80% → hongo (fungicida + bajar riego); humedad normal → plaga (pesticida). Así **no se le pide a la cámara un imposible** (distinguir plaga de hongo a nivel planta con pocos datos): la distinción la resuelve la **fusión de señales**. Esto es la frase *"la cámara aporta evidencia y el motor agronómico fusiona señales"* hecha ingeniería.

**Sobre la clase Colapso (damping-off del tallito — *Rhizoctonia* / *Fusarium* / *Pythium*):**

> ⚠️ **Nota de nomenclatura (corregida):** esta clase es el **damping-off** ("volcado de plantines" / "mal de los almácigos"), un complejo de **hongos de suelo** que pudre el plantín a la altura del **cuello**, en **vivero**. NO confundir con el **"mal de la tela"** (*Ceratobasidium niltonsouzanum*), que es un hongo **foliar de plantas adultas en plantación** (hojas secas colgando de hilos marrones; foco en Andresito, norte de Misiones) → ese caso, si aparece, va dentro de **Daño biótico**, no acá.

- Es **opcional / nice-to-have**. Su inclusión depende de **cuántas fotos se consigan en Misiones**.
- Si **no se consigue dato suficiente, se descarta sin problema** y se trabaja con 4 clases. Es un **agregado de valor**, no un compromiso asumido.

### 2.3 Modelo: MobileNetV3-Large (provisional, a confirmar con el equipo)
Ver análisis comparativo completo en `modelos-ia-propuestos.md`. Resumen:
- **MobileNetV3-Large** ⭐: liviano (apto edge, coherente con HU-04 CA-05 y HU-13), enfoque de transfer learning de manual (defendible), pesos ImageNet disponibles.
- Alternativas válidas: **EfficientNet-B0/Lite0** (máxima precisión liviana), **MobileNetV2** (precedente directo del paper de Palma Aceitera en vivero), **ResNet50** (baseline de comparación), **YOLOv8-cls** (facilísimo + mismo ecosistema para detección futura).
- **No hay lock-in:** el backbone es intercambiable. Cambiar de modelo = cambiar pocas líneas + re-entrenar (minutos a un par de horas). Lo profesional es **probar 2–3 y elegir por F1 sobre yerba real.**

### 2.4 ¿Una etiqueta o varias por planta? (single-label vs multi-label)
Una planta puede tener **más de una condición a la vez** (ej. clorosis + daño biótico). Esto define cómo se configura la salida del modelo:

- **Single-label (softmax):** el modelo elige UNA clase; las clases compiten (suman 100%). Más simple y **coincide con los datasets donantes** (que traen una enfermedad por imagen).
- **Multi-label (sigmoid):** cada clase es un sí/no **independiente** → puede reportar "Clorosis 0.8 Y Biótico 0.7" a la vez. Maneja co-ocurrencia, pero **exige etiquetas multi-label** (cada foto marcada con TODAS sus condiciones), más difíciles de conseguir.

**Decisión:** un **único modelo** — NO uno por enfermedad (eso multiplica el problema de datos, desperdicia el backbone compartido y encarece el edge al correr N modelos). Para el MVP se usa **single-label (softmax)** por simplicidad y compatibilidad con la data donante, **reportando el top-2 con su confianza** para insinuar co-ocurrencia. El diseño queda listo para **evolucionar a multi-label** (cambio menor: softmax → sigmoid, loss → binary cross-entropy) cuando haya etiquetas que lo respalden. Además, la co-ocurrencia también se resuelve por **fusión multimodal** (la humedad del sensor desempata plaga vs hongo).

---

## 3. El problema central: la brecha de datos

**Existe 0% de datasets públicos de imágenes foliares de yerba mate** (confirmado en la investigación del equipo). No se puede entrenar desde cero. La única salida viable —y estándar en la industria— es:

### Transfer Learning + Domain Adaptation
Un modelo pre-entrenado en millones de imágenes (ImageNet) **ya sabe** reconocer bordes, texturas, colores y formas. No se entrena desde cero: solo se le **enseña** que *"esto, en yerba mate, significa clorosis"*. Por eso **no hacen falta miles de fotos** de yerba: con pocas, más augmentation y datos donantes de especies parecidas, alcanza para un MVP defendible.

---

## 4. Pipeline de entrenamiento (2 etapas)

> ⚠️ **Descartamos la cadena de 5 datasets encadenados** (PlantVillage → Tea → CoLeaf → Dragon → Palma) que aparecía en la propuesta original: el *sequential fine-tuning* sufre **olvido catastrófico** y es imposible justificar el orden exacto. Se reemplaza por un esquema más limpio y defendible:

**Etapa 1 — Base pre-entrenada (ImageNet).**
Se parte de un MobileNetV3 con pesos de ImageNet. Esto ya aporta las características universales (bordes, texturas, color). *(PlantVillage como etapa separada es casi redundante con esto → no se usa como paso aparte.)*

**Etapa 2 — Fine-tuning sobre un pool combinado y re-etiquetado.**
Un **único** entrenamiento sobre un pool de imágenes de los datasets donantes **re-mapeadas a nuestras 5 clases**, **mezclando las fotos reales de yerba**. Técnica típica: congelar el backbone y entrenar la "cabeza" (clasificador) primero, luego descongelar las últimas capas para un ajuste fino.

**Validación / Test — SOLO con yerba real.**

### Las 3 reglas sagradas (no negociables)

1. 🔒 **El re-etiquetado es el trabajo intelectual real.** Mapear "categoría del dataset donante → nuestra clase" es nuestro aporte, no es automático (ver tabla en sección 5).
2. 🔒 **Se valida/testea SOLO con yerba real.** Se puede entrenar con té y café, pero solo se puede *demostrar* algo midiendo contra yerba. Esa es la métrica defendible.
3. 🔒 **Se separa train/val/test ANTES de aumentar, y se aumenta SOLO el train.** Si la misma hoja rotada cae en train y en test → accuracy inflada y mentirosa. Error clásico y fulminante en una defensa.

### Sobre Data Augmentation (con honestidad)
De cada foto se generan variaciones (rotación, zoom, brillo, contraste, flip, recorte, leve ruido). **Aclaración importante:** augmentation **no** crea muestras independientes nuevas — son variaciones correlacionadas de la misma imagen. Ayuda a la **robustez y a evitar overfitting**, pero la información real sigue siendo la cantidad de fotos originales. No se debe afirmar "tenemos 5000 muestras" si son 650 aumentadas.

### Estrategia de división Train / Validation / Test (clave con pocos datos reales)
El recurso más escaso es la **yerba real**; el split hay que diseñarlo para **cuidarla**, no para seguir un 80/10/10 mecánico (ese esquema asume UN dataset; acá hay dos poblaciones distintas: **donante** vs **yerba real**).

⚠️ **Anti-patrones a evitar:**
- **Gastar la mitad de la poca yerba real en validación** → deja el Test tan chico que el número final se vuelve ruidoso (con pocas fotos por clase, el accuracy salta por azar y no es defendible).
- **Diluir la validación con data donante** → tapa justo la señal de yerba que se quiere medir (el early stopping termina optimizando para el café, no para la yerba).

**Reglas del split:**
- 🔒 El **Test es sagrado**: solo yerba real, sacada de los tubetes del prototipo (mismas condiciones que el día de la defensa). No se toca durante el entrenamiento.
- 🔒 La yerba real se separa **POR PLANTA, no por imagen** (si dos fotos de la misma planta caen en sets distintos → leakage → número inflado).

**Según cuánta yerba real se consiga en Misiones:**

| Escenario | Train | Validation (early stopping) | Test |
| --- | --- | --- | --- |
| **Pocas fotos reales** (lo más probable) | Data donante | Held-out de **data donante** | **TODA** la yerba real |
| **Muchas fotos reales** | Data donante (+ parte de yerba) | Yerba real (split **por planta**) | Yerba real (split **por planta**) |

- Con **pocas** fotos: toda la yerba va al Test, y para que el número no sea ruidoso se usa **validación cruzada (k-fold)** sobre la yerba → se entrena/evalúa en varias rotaciones y se promedia → número **estable y defendible**.
- Recordar: el **augmentation se aplica DESPUÉS del split y SOLO al Train.**

---

## 5. Datasets donantes y re-etiquetado a nuestras clases

Datasets identificados en la investigación del equipo (ver `datasets_info_recopilada*.pdf`), mapeados a nuestras 5 clases:

| Nuestra clase | Datasets donantes a usar | Notas |
| --- | --- | --- |
| **Sano** | Tea Leaf (sanas), Dragon Fruit (2.242 sanas), cualquiera con clase healthy | Sobra material |
| **Clorosis** | CoLeaf (café, deficiencias N/Fe/Mg/etc.), Plant Disease/Chakraborty (clase "Chlorosis"), Ampalaya (deficiencias) | CoLeaf es el más fuerte (homología con yerba) |
| **Estrés solar** | Dragon Fruit (clase "Sunburn", 271 img), Ampalaya (déficit K → bordes quemados como proxy) | Dragon Fruit separa quemadura solar de necrosis fúngica |
| **Daño biótico** | Tea Leaf (ácaro rojo, chinche *Helopeltis*, tizón, antracnosis), Agarwood (plagas de artrópodos + hongos) | Plaga y hongo **juntos** en esta clase |
| **Colapso** *(opcional)* | Sin donante directo fuerte → depende de yerba real (Misiones) + augmentation | Por eso es la clase más débil en datos → opcional |

URLs principales (de la investigación): Tea Leaf y CoLeaf y Dragon Fruit en Mendeley Data / Kaggle; Agarwood en Mendeley; Ampalaya en Figshare. *(Links exactos en `datasets_info_recopilada.pdf`.)*

> **El re-etiquetado es trabajo manual de criterio:** hay que revisar las categorías de cada dataset y decidir a cuál de nuestras 5 clases corresponde cada carpeta de imágenes. Esto se documenta (es defendible y es nuestro aporte).

---

## 6. Misiones: protocolo de captura de datos

**El viaje es en ~1 mes y no se sabe cuántas fotos se podrán sacar.** Por eso planificamos para **los dos escenarios** y le buscamos la vuelta.

### El principio que ordena todo
Aunque consigas **pocas** fotos, esas fotos valen **oro** como **set de validación/test**: son la única forma de *demostrar* que el modelo anda en yerba real. Entrenás con té y café; **validás con yerba**.

### Escenario A — pocas fotos (15–40 por clase)
- Se usan **solo como test/validación** (no entran al entrenamiento).
- El entrenamiento lo carga la data donante + augmentation.
- Con 15–30 fotos reales por clase **ya se puede medir** y mostrar una matriz de confusión honesta.
- Priorizar: **Sano + las clases más fáciles y evidentes** (Clorosis, Estrés solar).

### Escenario B — muchas fotos
- Una parte entra al **fine-tuning** (mejora la adaptación de dominio).
- Otra parte se **aparta como test** (held-out), y nunca se usa para entrenar.

### Protocolo de captura (la CONSISTENCIA es reina)
Con pocos datos, cada variación innecesaria es algo más que el modelo tiene que aprender a tolerar. Minimizar el ruido:

- 📐 **Encuadre:** cenital (desde arriba, como el gantry), el plantín llenando el cuadro, **distancia y altura fijas**.
- 💡 **Luz:** difusa y consistente (día nublado o misma hora del día); evitar sombras duras y contraluz.
- 🎽 **Fondo:** neutro y constante si se puede (o el mismo tubete siempre).
- 🏷️ **Etiquetado en el momento:** foto + clase + (ideal) **confirmación de un agrónomo**. Anotar sector/condiciones.
- ✅ **Priorizar casos NÍTIDOS:** una clorosis clarísima etiquetada con seguridad vale más que un caso ambiguo. Las etiquetas confiables son críticas.
- 🐛 **Daño biótico:** fotografiar cualquier daño visible (mordeduras, manchas, telarañas) → todo a la misma bolsa.
- 💀 **Colapso:** si aparecen plantines caídos/marchitos/muertos, fotografiarlos (habilita la clase opcional).
- 📸 **Sacar MUCHAS más de las que creés** — el almacenamiento es gratis, se descarta después. Mismo dispositivo (un buen celular alcanza).

### "Buscarle la vuelta" (no depender 100% del viaje)
- **Empezar a juntar yerba YA**, antes del viaje: contactos de viveros, INTA, fotos que puedan mandar productores, plantas accesibles localmente. Cualquier yerba real suma.
- Augmentation para estirar las pocas reales (recordar: solo en train).
- La data donante carga el grueso del entrenamiento; las reales son para adaptar y validar.

---

## 7. Infraestructura de cómputo

La GPU importa para **entrenar** (velocidad). Para **inferir** (una foto por vez) alcanza hasta un CPU. Entrenar ESTO es liviano (transfer learning, modelo chico, pocas imágenes). La métrica clave es la **VRAM** de la placa.

| Nivel | VRAM | Ejemplo de placa | Experiencia |
| --- | --- | --- | --- |
| Mínimo | ~4 GB | GTX 1650 / 1050 Ti | Entrena, batches chicos, algo lento |
| **Recomendado** ✅ | **6–8 GB** | RTX 2060 / 3050 / **3060** | Cómodo, entrena en minutos |
| Sobrado | 12 GB+ | RTX 3060 12GB / 3090 / 4090 | Lujo, no se aprovecha en este caso |

### Opción elegida: PC local con GPU de 8 GB
El equipo cuenta con una placa de **8 GB** (la del hermano de un integrante) → cae en el nivel **recomendado**. **Alcanza y sobra** para entrenar cualquiera de los modelos propuestos sobre nuestro volumen de datos.
- **Ventaja:** control total, sin límites de tiempo de sesión, datos locales, iteración rápida.
- **Requisito:** instalar el entorno (Python + PyTorch o TensorFlow con soporte CUDA + drivers NVIDIA). Es la única curva de setup.

### Opción alternativa: Google Colab (gratis)
Notebook en la nube que da una **GPU Tesla T4 de 16 GB** gratis. **Más que suficiente** para este proyecto.
- **Ventajas:** cero instalación, entorno listo, ideal para arrancar rápido o para quien no tiene GPU; fácil de compartir entre compañeros.
- **Desventajas:** límites de tiempo de sesión (se desconecta tras inactividad / uso prolongado), hay que subir los datos a Drive, la sesión no es permanente (se reinicia el entorno).
- **Uso recomendado:** prototipado inicial y para que todo el equipo pueda correr el notebook sin depender de la PC con GPU.

### Opción de respaldo: Kaggle Notebooks (gratis)
Similar a Colab; ofrece GPU T4/P100 con ~30 horas semanales.
- **Ventajas:** cuota semanal generosa, datasets de Kaggle integrados (varios donantes están ahí).
- **Desventajas:** mismos límites de entorno en la nube que Colab.

> **Estrategia sugerida:** desarrollar/iterar en **Colab** (todos pueden correrlo) y/o entrenar las corridas serias en la **PC de 8 GB**. Son intercambiables: el mismo código corre en ambos.

---

## 8. Local (PC) hoy vs Edge (Raspberry) mañana

| | **PC local (HOY)** | **Edge / Raspberry (FUTURO)** |
| --- | --- | --- |
| **Entrenamiento** | Siempre acá (o Colab). Nunca se entrena en la Pi. | — |
| **Inferencia** | El servicio de IA Python corre en la PC (vía el contrato gRPC de la arquitectura) | El modelo corre **en el nodo, junto a la planta**, sin internet |
| **Ventajas** | Itera rápido, fácil de debuggear, sin límite de hardware, sirve para la demo | Autonomía real, sin depender de conectividad (HU-13, HU-04 CA-05), cumple la propuesta de valor |
| **Desventajas** | **No** demuestra todavía el valor "offline en el borde" (queda centralizado) | Cómputo limitado (exige modelo liviano), despliegue más complejo (conversión a TFLite/ONNX, cuantización), debug más difícil |

**El puente entre ambos mundos:** elegir **hoy** un modelo liviano (MobileNet) **deja la puerta del edge abierta a costo cero**. El día que se quiera mover la inferencia a la Raspberry, el modelo ya encaja; no hay que reentrenar con otra arquitectura. Por eso, aunque el prototipo corra en PC, la elección "para edge" es criterio, no capricho.

**Camino a edge (futuro):** entrenar en PC → exportar a **TFLite u ONNX** → (opcional) **cuantización** para achicar y acelerar → correr en la Raspberry. No es parte del MVP, pero queda documentado como evolución.

---

## 9. Métricas y qué es "defendible"

- **No** perseguir 99% de accuracy. El jurado valora **arquitectura, criterio técnico, integración y viabilidad**, no un número perfecto.
- **Métrica principal:** **F1-score por clase** sobre el **set de yerba real** (no accuracy global, que engaña con clases desbalanceadas).
- **Mostrar la matriz de confusión:** es honesta y muestra dónde se confunde el modelo (esperable: Daño biótico es la clase más difícil).
- **Manejar el desbalance:** "Sano" va a dominar → usar *class weights* o muestreo balanceado.
- **La narrativa defendible:** *"no hay datasets de yerba → transfer learning desde análogos fenotípicos (té/café) → validado contra fotos reales de yerba → fusión multimodal con sensores para la decisión final"*. Eso es ingeniería seria.

---

## 10. Pasos a seguir (roadmap)

### Fase 0 — Decisiones (✅ hecho)
Encuadre, clases, modelo provisional, estrategia de datos, cómputo.

### Fase 1 — Primer avance técnico (esta semana)
- [ ] Montar el entorno (Colab para arrancar, o PC 8 GB con CUDA).
- [ ] Bajar 2–3 datasets donantes (Tea Leaf, CoLeaf, Dragon Fruit) y explorarlos.
- [ ] Re-etiquetar/curar un subset a las 5 clases (documentar el mapeo).
- [ ] Entrenar un **primer baseline** con transfer learning (MobileNetV3), aunque sea **solo con data donante**. Objetivo: tener un modelo que clasifique algo, aunque sea flojo.
- [ ] Script de **inferencia**: toma una foto → devuelve `clase + confianza` (alineado al contrato de IA de la arquitectura).
- [ ] **Esto ya es un avance técnico mostrable** (se pasó de "solo documentación" a "modelo que infiere").

### Fase 2 — Pipeline limpio (pre-Misiones)
- [ ] Split train/val/test correcto; augmentation **solo en train**.
- [ ] Métricas (F1 por clase, matriz de confusión); manejo de desbalance.
- [ ] Comparar 2–3 backbones y elegir el mejor.
- [ ] Cerrar el **protocolo de captura** para Misiones.
- [ ] Empezar a juntar fotos de yerba real **antes** del viaje (no depender 100% del viaje).

### Fase 3 — Misiones (~1 mes)
- [ ] Ejecutar el protocolo de captura (consistencia + etiquetado + confirmación agronómica).

### Fase 4 — Post-Misiones
- [ ] Integrar fotos reales: fine-tuning final + **test set de yerba real**.
- [ ] Evaluar y elegir el modelo final.
- [ ] (Opcional) sumar **Colapso** si se consiguió dato suficiente.

### Fase 5 — Integración y futuro
- [ ] Servicio de IA en Python expuesto por gRPC (según la arquitectura de Agus).
- [ ] (Futuro) camino a edge: exportar a TFLite/ONNX → probar en Raspberry.

---

## 11. Riesgos y mitigaciones

| Riesgo | Mitigación |
| --- | --- |
| **0% datasets de yerba** | Transfer learning desde análogos (té, café, etc.) + augmentation |
| **Pocas fotos en Misiones** | Usarlas solo como test; entrenar con donantes; juntar yerba antes del viaje |
| **Daño biótico difícil a nivel plantín** | Clase gruesa + desambiguación por sensores (fusión multimodal) |
| **Overfitting con pocos datos** | Augmentation (solo train), congelar capas, regularización, early stopping |
| **Fuga de datos (leakage)** | Split ANTES de aumentar; test solo yerba real nunca vista |
| **Elegir "mal" modelo** | Sin lock-in: swapear es barato; comparar varios y elegir por F1 |
| **Colapso sin datos** | Es clase opcional; si no hay dato, se descarta sin mencionarlo |

---

## 12. Qué mostrar la semana que viene (avances técnicos)

Para pasar de "solo documentación" a "avance técnico" defendible, alcanza con:
1. **La estrategia escrita** (este informe + `modelos-ia-propuestos.md`).
2. **Un clasificador funcionando** con transfer learning, aunque esté entrenado **solo con data donante** (el dato real de yerba llega después de Misiones).
3. **Un script de inferencia** que tome una imagen y devuelva `clase + confianza` (el contrato de IA, listo para enchufar a la arquitectura).
4. **El plan de datos de Misiones** como demostración de criterio y viabilidad.

> Eso demuestra **automatización de lazo cerrado defendible**: visión → diagnóstico → (fusión con sensores) → acción. Que es exactamente lo que se valora.
