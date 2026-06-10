# Las 5 clases del modelo — qué es cada una y cómo se la conoce en Misiones (Yerbanalytics)

> **Para qué sirve este documento:** explicar, con detalle agronómico y terminología real de Misiones, cada una de las clases que el modelo de visión de Yerbanalytics clasifica en los plantines de yerba mate. Sirve para entender el dominio, defender el proyecto y mapear cada clase a su acción agronómica.
>
> **Fuente del alcance:** `informe-ia-plan-entrenamiento.md` (sección 2.2, clases del MVP) y `guia-recopilacion-fotos-misiones.md` (sección 6). Investigación complementaria: INTA Cerro Azul, INYM, SENASA y bibliografía citada al final.

---

## 0. Concepto clave: no son todas "enfermedades"

El modelo **NO clasifica enfermedades**. Clasifica **5 estados del plantín**, y cada uno dispara una **acción agronómica distinta**. Son cosas de naturaleza muy diferente entre sí:

| Clase | ¿Qué es realmente? | Causa | Acción agronómica |
| --- | --- | --- | --- |
| **Sano** | Estado de referencia | — | Ninguna |
| **Clorosis** | Trastorno **nutricional / fisiológico** | Deficiencia de nutrientes / pH | Fertirriego / nutrientes (HU-07) |
| **Estrés solar** | Daño **físico / abiótico** | Exceso de luz / radiación | Desplegar mediasombra (HU-08) |
| **Daño biótico** | Daño por **seres vivos** | Plagas (insectos/ácaros) + hongos | Fitosanitario + alerta (HU-07/10) |
| **Colapso** (opcional) | **Muerte / marchitez** del plantín | Hongos de suelo (damping-off) | Alerta crítica + intervención manual |

> **Por qué importa:** ante un jurado agrónomo, lo correcto no es decir "detectamos 5 enfermedades", sino **"detectamos anomalías de origen nutricional, abiótico y biótico"**. La lógica que ordena todo es **estado → acción**.

---

## 1. Clorosis (trastorno nutricional)

**Qué es:** la planta pierde el verde porque **no puede sintetizar clorofila**. La clorofila depende de nutrientes (nitrógeno, hierro, magnesio, azufre); si faltan, la hoja se amarillea.

**El detalle fino — el patrón del amarillamiento dice qué nutriente falta:**
- **Nitrógeno (N):** amarillea primero en hojas **viejas** (de abajo), de forma pareja. El N es móvil: la planta lo "roba" de las hojas viejas para alimentar las nuevas.
- **Hierro (Fe):** amarillea en hojas **jóvenes** (de arriba), con las **nervaduras verdes** (clorosis intervenal). El Fe no es móvil.
- **Azufre (S) / Magnesio (Mg):** tonos intermedios; hojas jóvenes verde claro.

**En Misiones:** entra fuerte el **pH del sustrato**. La yerba quiere suelo **ácido (pH 5.3–6.5)**; si el sustrato se sale de ese rango, los nutrientes se "bloquean" aunque estén presentes → clorosis. Por eso, en vivero, la clorosis suele ser un problema de **sustrato**, no de falta de fertilizante.

**Acción:** fertirriego / corrección de nutrientes (HU-07).

---

## 2. Estrés solar (daño abiótico)

**Qué es:** la yerba mate, de chica, es una planta de **sotobosque** — crece bajo la sombra de árboles más grandes. El plantín es **sensible al sol directo**: el exceso de radiación quema el tejido.

**Cómo se ve:** blanqueo, manchas claras y necrosis (tejido muerto) en las zonas **más expuestas** — puntas, bordes, la cara que mira al sol.

**En Misiones (clave para el proyecto):** en los viveros los plantines se crían bajo **media sombra (50–70%)**, con malla o árboles nodriza (angico, bracatinga). Existe la fase de **"rustificación"**: se **retira gradualmente la media sombra** para que el plantín se aclimate antes de ir a campo. Si se lo destapa de golpe o demasiado rápido, **se quema**. Por eso el estrés solar aparece típicamente en **plantines recién sacados de la media sombra**. Al trasplante incluso se les coloca un **"poncho"** de paja para protegerlos del sol y el viento.

**Acción:** desplegar mediasombra (HU-08). Es una clase separada porque la respuesta es **física**, no química.

---

## 3. Daño biótico (plagas + hongos)

Es la clase **gruesa** y la más difícil: junta todo lo causado por seres vivos. En Misiones, cada plaga tiene nombre propio. Estas son las **plagas reales del yerbal** (INTA Cerro Azul / INYM):

- **🌀 "Rulo" / psílido de la yerba (*Gyropsylla spegazziniana*):** LA plaga estrella, la más frecuente y estable. La hembra, al poner huevos en los brotes, inyecta una **fitotoxina** que deforma la hoja y la **enrula** sobre sí misma (de ahí "rulo"). Ataca **brotes y hojas jóvenes**. Dicho misionero: *"el rulo siempre vuelve"*.
- **🪲 "Taladro" / tigre de la yerba (*Hedypathes betulinus*):** escarabajo (cerambícido). La larva **barrena el tronco** por dentro y puede matar la planta. La plaga más destructiva en plantas adultas.
- **🐛 "Marandová" / isoca de la yerba (*Perigonia lusca*):** larva (oruga) de una mariposa que produce **defoliación** (se come las hojas). "Marandová" es el nombre guaraní/regional.
- **🕷️ Ácaros (ej. ácaro bronceado *Dichopelnus notus*):** broncean y resecan el follaje.
- **🕸️ Hongos foliares:** manchas, antracnosis y el **"mal de la tela"** (*Ceratobasidium niltonsouzanum*): las hojas se secan y quedan colgando de un **hilo/telaraña marrón** (de ahí "tela"). Apareció con fuerza en el **norte de Misiones (Andresito)** y se expandió. **No hay producto autorizado por SENASA** para controlarlo: solo manejo cultural.

**El truco multimodal (activo fuerte del proyecto):** a la cámara NO se le pide distinguir plaga de hongo a nivel plantín (es un imposible con pocos datos). La cámara dice *"daño biótico 0.84"* y **el sensor de humedad desambigua**: sustrato muy húmedo (>80%) → probablemente **hongo**; humedad normal → probablemente **plaga**. *"La cámara aporta evidencia; el motor agronómico fusiona señales."*

**Acción:** alerta + acción fitosanitaria (HU-07/10) → ver sección 5.

---

## 4. Colapso / damping-off (clase OPCIONAL)

**Qué es:** el **damping-off** ("volcado de plantines" / "mal de los almácigos") es el clásico asesino de viveros. Un complejo de **hongos de suelo** — *Rhizoctonia*, *Fusarium*, *Pythium* — pudre el **tallito a la altura del cuello** (la base, donde toca el sustrato). El plantín se **dobla, se marchita y muere**. Es muy visible porque **cambia la geometría** de la planta (se cae), por eso es fácil de clasificar... cuando hay datos.

> ⚠️ **Nota de nomenclatura (corrección aplicada):** el "Colapso" es **damping-off** (hongos de suelo, **cuello**, **vivero**). NO es lo mismo que el **"mal de la tela"** (*Ceratobasidium niltonsouzanum*), que es un hongo **foliar de plantas adultas en plantación**. Son enfermedades distintas y bien conocidas en Misiones; confundirlas es un error frente a un agrónomo. El "mal de la tela", si aparece, va dentro de **Daño biótico**.

**Por qué es opcional:** no existe dataset donante directo fuerte; depende 100% de cuántas fotos se consigan en Misiones + augmentation. Si no hay dato suficiente, se descarta sin drama y se trabaja con 4 clases.

**Acción:** alerta crítica + intervención manual.

---

## 5. ¿Qué es la "acción fitosanitaria"?

"Fitosanitario" = **lo que protege la SANIDAD de la planta** (*fito* = planta). Es la respuesta que dispara la clase **Daño biótico**. No es un único producto: es una **decisión que depende de si el agente es plaga o hongo** — y ahí entra otra vez la fusión multimodal.

**El árbol de decisión que arma nuestro sistema:**

1. La cámara detecta **Daño biótico**.
2. El **sensor de humedad** del sustrato desambigua:
   - **Humedad alta (>80%) → sospecha de HONGO** → acción: **fungicida** + **bajar el riego** (sacarle la humedad que el hongo necesita).
   - **Humedad normal → sospecha de PLAGA (insecto/ácaro)** → acción: **insecticida / acaricida** específico.
3. En todos los casos: **alerta al productor** (HU-10) con el diagnóstico y la recomendación.

**Tipos de acción fitosanitaria, de menor a mayor intervención:**

| Tipo | En qué consiste | Cuándo |
| --- | --- | --- |
| **Cultural / preventiva** | Desmalezado, eliminación de plantines enfermos (pre-inóculo), desinfección de herramientas, ajustar riego/ventilación | Siempre — es la base; para el "mal de la tela" es **la única vía** (no hay producto SENASA) |
| **Biológica (MIP)** | Favorecer enemigos naturales (depredadores/parasitoides). INTA Cerro Azul lo impulsa para rulo, taladro y marandová | Estrategia preferida y sustentable |
| **Química** | Aplicación dirigida de **fungicida** (hongos) o **insecticida/acaricida** (plagas), con producto **autorizado por SENASA** para yerba | Cuando lo cultural/biológico no alcanza |

> **Concepto importante:** Yerbanalytics **no aplica** el fitosanitario solo: **recomienda y alerta**. La decisión final y la aplicación las hace el productor/agrónomo. El valor del sistema es la **detección temprana** + la **recomendación correcta** (qué tipo de acción, gracias a la fusión cámara+sensor), no reemplazar el criterio agronómico. Además, el enfoque que prioriza Misiones (INTA) es el **Manejo Integrado de Plagas (MIP)**: lo químico es el último recurso, no el primero.

---

## Resumen mental

- **Clorosis** = problema de comida (nutrientes/pH). → **Nutrir.**
- **Estrés solar** = problema de sol (media sombra). → **Tapar.**
- **Daño biótico** = problema de bichos/hongos (rulo, taladro, marandová, ácaros, hongos foliares). → **Fitosanitario** (cultural → biológico → químico), desambiguado por sensor.
- **Colapso** = el plantín se muere por hongos de suelo (damping-off). → **Alerta crítica.**

La lógica que ordena todo: **estado → acción.**

---

## Fuentes

- [Qué plagas pueden encontrarse en un yerbal y cómo controlarlas — INYM](https://inym.org.ar/noticias/capacitacion/79312-que-plagas-pueden-encontrarse-en-un-yerbal-y-como-controlarlas.html)
- [Propuesta de MIP para el cultivo de la yerba mate — INTA Cerro Azul (Ohashi)](https://repositorio.inta.gob.ar/bitstream/handle/20.500.12123/3530/INTA_CRMisiones_EEACerroAzul_Ohashi_D_Propuesta_mip_para_el_cultivo_de_la_yerba_mate.pdf?sequence=1)
- [Los enemigos naturales también están actuando en los sistemas yerbateros — INTA Cerro Azul](https://repositorio.inta.gob.ar/bitstream/handle/20.500.12123/23245/INTA_CRMisiones_EEACerroAzul_Ohashi_DV_Los_Enemigos_naturales_tambi%C3%A9n_est%C3%A1n-actuando_en_los_sistemas_yerbateros.pdf?sequence=1&isAllowed=y)
- [El rulo siempre vuelve — Bichos de Campo](https://bichosdecampo.com/el-rulo-siempre-vuelve-los-yerbateros-de-misiones-alertaron-por-posibles-perdidas-frente-una-plaga-que-afecta-la-brotacion-de-las-plantas/)
- [Manejo integrado y control biológico, claves para la sanidad de la yerba mate — Canal 12 Misiones / INTA](https://www.canal12misiones.com/noticias-de-misiones/agro/control-biologico-yerba-mate-inta)
- [Mal de la tela en Yerba Mate (*Ceratobasidium niltonsouzanum*) — Herbario Virtual de Fitopatología, UBA](https://herbariofitopatologia.agro.uba.ar/?page_id=9378)
- [Mal de la tela: monitoreo en plantaciones de yerba mate y té — Argentina.gob.ar (SENASA)](https://www.argentina.gob.ar/noticias/mal-de-la-tela-monitoreo-en-plantaciones-de-yerba-mate-y-te)
- [Mal de la tela: recomendamos no aplicar productos químicos — INYM](https://inym.org.ar/noticias/capacitacion/78653-mal-de-la-tela-recomendamos-no-aplicar-productos-quimicos.html)
- [Comparación de sintomatologías nutricionales en el cultivo de Yerba Mate — Campo Agropecuario](https://campoagropecuario.com.py/comparacion-de-sintomatologias-nutricionales-en-el-cultivo-de-yerba-mate)
- [Yerba mate: mejorar la calidad a través de los sustratos — Argentina.gob.ar](https://www.argentina.gob.ar/noticias/yerba-mate-mejorar-la-calidad-traves-de-los-sustratos)
- [Buenas Prácticas Agrícolas en el cultivo de Yerba Mate — CASAFE](https://www.casafe.org/buenas-practicas-agricolas-en-el-cultivo-de-yerba-mate/)
