# Informe de curación de datasets — Yerbanalytics

> Trabajo de revisión, mapeo y limpieza de los datasets donantes para el modelo de visión del MVP (detección de anomalías en plantines de yerba mate).
>
> **Fecha:** 2026-06-10 · **Estado:** las 5 clases tienen donante · baseline de 3 clases listo.

---

## 1. Resumen ejecutivo

Tras revisar visualmente cada dataset, el proyecto quedó con **4 datasets limpios** que cubren **las 5 clases** del modelo.

| Clase del modelo | Fuente | Imágenes |
|---|---|---|
| **Sano** | Tea Leaf Healthy (12.829) + RoCoLe healthy (791) | ~13.620 |
| **Clorosis** | CoLeaf N + Fe + Mg + Mn (291) | 291 |
| **Daño biótico** | Tea Leaf 5 enfermedades (67.500) + RoCoLe ácaro (167) + RoCoLe roya (602) | ~68.000 |
| **Estrés solar** | Tea Leaf Diseases / Sunlight Scorching (1.712 raw + 4.009 aug) | 1.712 |

**Listo para el primer baseline:** Sano / Clorosis / Daño biótico (submuestreando a ~300–500 por clase para emparejar con Clorosis).

---

## 2. El criterio que guió toda la revisión

> Un dataset donante vale por cuánto **se parece visualmente su tejido** a la hoja de yerba mate (homología fenotípica del tejido), **NO** por el nombre de la carpeta ni por lo que diga el paper.

¿Por qué? Porque el **transfer learning aprende la firma visual del tejido**, no la etiqueta. Por eso cada dataset se aceptó o se descartó **mirando las fotos con el ojo**, no leyendo descripciones.

---

## 3. Estado actual en disco

Ruta base: `Desarrollo/Modelo_IA/datasets/`

```
datasets/
├── CoLeaf/                                  → Clorosis
│   ├── healthy/        (6)    descartable
│   ├── iron-Fe/        (65)
│   ├── magnesium-Mg/   (79)
│   ├── manganese-Mn/   (83)
│   └── nitrogen-N/     (64)
├── RoCoLe A robusta coffee leaf images dataset/   → ácaro + roya + sano
│   ├── Photos/         (1.560 sueltas)
│   └── Annotations/    (CSV/JSON con las clases)
├── Tea Leaf Dataset/                        → Sano + Daño biótico
│   ├── Healthy Leaves/ (12.829)
│   └── Diseased Leaves/ (5 × 13.500 = 67.500)
└── Tea Leaf Diseases Dataset .../           → Estrés solar
    └── Research Dataset/
        ├── Raw Dataset/Sunlight Scorching/             (1.712)
        └── Augmented Dataset/Augmented_Sunlight Scorching/ (4.009)
```

---

## 4. Decisiones por dataset

### ✅ Tea Leaf Disease Dataset — ACEPTADO
- **Rol:** Sano + Daño biótico. Es el mejor donante: tejido = hoja coriácea real, muy parecido a yerba.
- **Mapeo:** `Healthy_leaves` → Sano. Las 5 enfermedades (`Blister_Blight`, `Brown_Blight`, `Leaf_Red_Rust`, `Red_Spider_Mite`, `Tea_Mosquito_Bug`) → todas **Daño biótico**.
- **Caveats:**
  - Hojas recortadas sobre **fondo blanco** (problema tipo PlantVillage). Corregible: el tejido es correcto, sólo el fondo no.
  - **Pre-aumentado** (los 13.500 redondos no son originales). Usar sólo para *training*, **nunca** para validación/test.

### ✅ RoCoLe (Robusta Coffee Leaf) — ACEPTADO, falta filtrar
- **Rol:** ácaro (Red Spider Mite) sobre café, con **fondo de campo real** → complementa el fondo blanco del Tea Leaf.
- **Distribución (1.560):** healthy 791 · rust_level_1 344 · **red_spider_mite 167** · rust_level_2 166 · rust_level_3 62 · rust_level_4 30.
- **Veredicto visual:** tejido y fondo excelentes; **pero** el síntoma del ácaro es **sutil** y por amarilleo puede **confundirse con Clorosis** (otra clase). → Requiere curación a ojo: descartar las fotos donde el ácaro no se ve.
- **Estructura:** no viene en carpetas; las clases están en `Annotations/*.csv`. Hay que filtrar por script.

### ✅ CoLeaf — ACEPTADO para Clorosis
- **Mapeo (confirmado a ojo, no por nombre):** `healthy` → Sano (sólo 6 imgs, **descartable**); `nitrogen-N`, `iron-Fe`, `magnesium-Mg`, `manganese-Mn` → todas **Clorosis** (total 291). `iron-Fe` es clorosis intervenal de manual.
- **Problema:** desbalance brutal — sólo 6 sanas. CoLeaf **no sirve para Sano**; Sano sale de Tea Leaf + RoCoLe.

### ✅ Tea Leaf Diseases Dataset (`mkzyfj8bkj`) — ACEPTADO sólo para Estrés solar
- **Ojo:** es **otro dataset**, distinto del Tea Leaf viejo (`94fzcdz8gz`). Mismo cultivo (té), distinta fuente, clases y estructura.
- **Rol:** aporta la clase que faltaba → **Estrés solar** (`Sunlight Scorching`, 1.712 raw + 4.009 aug). Sus demás clases (Red Spider, Red Rust, Heliopeltis, Thrips, Healthy) son **redundantes** con lo que ya hay → se borraron.
- **Ventaja sobre el viejo:** separa **Raw** de **Augmented** en carpetas → permite validación honesta con las raw.
- **Veredicto visual de Sunlight Scorching:**
  - ✅ Tejido coriáceo de té, buen análogo de yerba.
  - ✅ Síntoma **distinguible**: bronceado/pardeo en ápice y bordes (necrosis marginal), distinto del amarilleo difuso de Clorosis → bajo riesgo de solape.
  - ⚠️ **Fondo blanco** recortado (pese al título "Field Diagnosis") → mismo caveat PlantVillage que el Tea Leaf viejo. No resuelve el fondo.

### ❌ Dragon Fruit — DESCARTADO (borrado)
- Es **cactus**, mal análogo de hoja sana. Además el ZIP real venía mal empaquetado.
- **Acción:** eliminado del disco (liberó 7,9 GB).

### ⏳ BRACOL (Brazilian Arabica) — BAJA PRIORIDAD
- Descargado (`Descargas/BRACOL_..._datasets.zip`, 164 MB), **sin extraer**.
- **No tiene ácaro.** Sólo minador, roya, cercospora, brown spot → refuerzo de Daño biótico general con fondo real.
- Como Daño biótico ya está enorme, aporta variedad de fondo pero no volumen. Decisión abierta.

---

## 5. Concepto clave — estructura de etiquetado de un dataset

Surgió al ver que RoCoLe no usa carpetas. **No está mal: es otra convención.** Cada una sirve a una tarea.

### Las dos formas

| Forma | Cómo etiqueta | La usan |
|---|---|---|
| **A — Carpeta por clase** | La subcarpeta donde vive la foto ES la etiqueta. | Tea Leaf, CoLeaf |
| **B — Archivo de anotaciones** | Fotos mezcladas + un CSV/JSON que mapea imagen → clase. | RoCoLe |

### Por qué a nosotros nos conviene la Forma A
1. **Los frameworks la leen solos:** PyTorch `ImageFolder` y Keras `image_dataset_from_directory` infieren la etiqueta del nombre de carpeta → cero parseo.
2. **Curación visual directa:** abrís la carpeta y ves sólo esa clase.
3. **Consistencia** con el resto del proyecto.

### Por qué la Forma B no es un error
RoCoLe trae **segmentación** (polígonos), **severidad** de roya y potencial multi-etiqueta. Eso una carpeta **no puede expresarlo** (una imagen vive en una sola carpeta). Para detección/segmentación, el CSV es la decisión correcta.

### La regla que se desprende
- **Single-label** (1 imagen = 1 clase) → **carpetas alcanzan**. Es el caso del MVP de Yerbanalytics.
- **Multi-label** (1 imagen = varias clases, ej. ácaro + clorosis en la misma hoja) → **se necesita archivo externo** con *multi-hot encoding* (una columna 0/1 por clase): CSV, JSON, Parquet, DataFrame o DB. El hack de "carpeta por combinación" explota en combinatoria (5 clases → hasta 31 combos) → inmantenible.

#### Ejemplo de multi-hot encoding

Cada fila es una imagen; cada columna, una clase. Una imagen puede tener varios `1` (de ahí "multi"):

| imagen | ácaro | clorosis | roya |
|---|:---:|:---:|:---:|
| `C1P33E1.jpg` | 1 | 1 | 0 |
| `C2P7E2.jpg` | 0 | 1 | 0 |

Esto una carpeta **no lo puede expresar**: `C1P33E1.jpg` tendría que vivir en `ácaro/` y en `clorosis/` a la vez.

> **Convertibilidad:** siempre se puede pasar de una forma a la otra con un script (CSV→carpetas o carpetas→CSV). No hay lock-in; se elige la forma que sirve a la tarea del momento.

---

## 6. Limpieza ejecutada esta sesión

- [x] **Dragon Fruit eliminado** (–7,9 GB). Descartado por ser cactus.
- [x] **CoLeaf reagrupado** bajo `CoLeaf/`. Antes sus 5 carpetas estaban sueltas en la raíz de `datasets/` → riesgo de que `ImageFolder` las tomara como clases. Corregido.
- [x] **Tea Leaf Diseases podado:** se borraron las clases redundantes (Red Spider, Red Rust, Heliopeltis, Thrips, Healthy) y quedó sólo `Sunlight Scorching` (raw + augmented).
- [x] **Zips redundantes borrados:** `RoCoLe.zip` (2,24 GB) y `Tea Leaf Diseases.zip` (1,5 GB), ambos ya extraídos. Se conserva `BRACOL.zip` (sin extraer, decisión abierta).

---

## 7. Pendientes y próximos pasos

| Pendiente | Detalle |
|---|---|
| **Curar RoCoLe** | Filtrar las 167 de ácaro desde el CSV y descartar las que no muestran síntoma (evita confusión con Clorosis). |
| **Curar Sunlight Scorching** | Inspección a ojo para descartar fotos donde el quemado no se distinga (evita ruido contra Daño biótico). |
| **Validación con yerba real** | Estrés solar (y todas las clases) se valida contra yerba real de Misiones (julio 2026); los donantes son sólo para *training*. |
| **Decidir BRACOL** | Extraer y sumar como refuerzo de Daño biótico, o descartar. Prioridad baja. |
| **Submuestreo para baseline** | Bajar las clases grandes a ~300–500/clase para emparejar con Clorosis (291). |

---

## 8. Recordatorios de diseño del modelo

- **Encuadre:** la unidad de imagen es **un plantín** (no hoja suelta, no bandeja). El gantry aísla cada plantín.
- **Single-label** en el MVP (una imagen = una clase).
- **Domain gap:** todos los donantes son hojas sobre fondo controlado/recortado vs. nuestro encuadre de plantín en vivero real. Sirven para *transfer learning*, pero la **validación/test se hace contra yerba real**.
