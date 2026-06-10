# Datasets — dónde están y cómo obtenerlos

> Los datasets **NO** se versionan en Git (pesan ~6,6 GB / ~88.000 imágenes).
> Viven en Google Drive y se pueden re-descargar desde sus fuentes originales.
> Esta carpeta sólo conserva este README (ver `.gitignore`).

## 📍 Dónde están

| Ubicación | Ruta |
|---|---|
| **Google Drive** | `⟨pegar acá el link/carpeta de tu Drive⟩` |
| **En Colab (montado)** | `/content/drive/MyDrive/Yerbanalytics/datasets/` |
| **Local (este repo, ignorado)** | `Desarrollo/Modelo_IA/datasets/` |

## 📦 Fuentes originales

| Dataset | Fuente / DOI | Licencia | Alimenta la clase |
|---|---|---|---|
| **Tea Leaf Disease** | Mendeley `10.17632/94fzcdz8gz` | — | Sano + Daño biótico |
| **Tea Leaf Diseases** (Field Diagnosis) | Mendeley `10.17632/mkzyfj8bkj.1` | CC BY 4.0 | Estrés solar (sólo `Sunlight Scorching`) |
| **RoCoLe** (Robusta Coffee Leaf) | Mendeley `10.17632/c5yvn32dzg.2` | — | Daño biótico (ácaro + roya) + Sano |
| **CoLeaf** | (deficiencias nutricionales de café) | — | Clorosis |
| **BRACOL** *(opcional, sin usar)* | Mendeley `10.17632/yy2k5y8mxg` | — | Refuerzo Daño biótico (sin ácaro) |

> El detalle de qué se aceptó/descartó y por qué está en
> [`../informe-curacion-datasets.md`](../informe-curacion-datasets.md).

## 🌳 Estructura de carpetas esperada

```
datasets/
├── CoLeaf/                          → Clorosis
│   ├── healthy/                     (descartable)
│   ├── iron-Fe/
│   ├── magnesium-Mg/
│   ├── manganese-Mn/
│   └── nitrogen-N/
├── RoCoLe A robusta coffee leaf images dataset/   → ácaro + roya + sano
│   ├── Annotations/                 (CSV/JSON con las clases)
│   └── Photos/                      (1.560 imágenes sueltas)
├── Tea Leaf Dataset/                → Sano + Daño biótico
│   ├── Diseased Leaves/
│   │   ├── Blister_Blight/
│   │   ├── Brown_Blight/
│   │   ├── Leaf_Red_Rust/
│   │   ├── Red_Spider_Mite/
│   │   └── Tea_Mosquito_Bug/
│   └── Healthy Leaves/Healthy_leaves/
└── Tea Leaf Diseases .../           → Estrés solar
    └── Research Dataset/
        ├── Augmented Dataset/Augmented_Sunlight Scorching/
        └── Raw Dataset/Sunlight Scorching/
```

## 🚀 Cómo reconstruir

**Montar el Drive en Colab (recomendado):**
```python
from google.colab import drive
drive.mount('/content/drive')
# datasets en /content/drive/MyDrive/Yerbanalytics/datasets/
```

Si trabajás en local, copiá las carpetas desde tu Drive respetando la
estructura del árbol de arriba.
