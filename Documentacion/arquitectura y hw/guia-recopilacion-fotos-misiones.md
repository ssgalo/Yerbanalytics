# Guía de campo — Recopilación de fotos de yerba mate (Misiones)

> **Para qué sirve este documento:** es el instructivo operativo, paso a paso, para juntar las fotos reales de yerba mate que necesita el modelo de IA. Está pensado para imprimir/llevar y seguir en el campo.
>
> **Por qué es crítico:** Misiones es la **única fuente de yerba real** y no hay revancha fácil. La data donante (té, café) carga el entrenamiento; **estas fotos son las que VALIDAN que el modelo sirve en yerba.**
>
> **Fundamento:** las recomendaciones de cantidades y método están alineadas con la literatura de datasets de enfermedades de plantas (FieldPlant, datasets smartphone de campo, PlantVillage) y estudios de cuántos datos necesita el transfer learning (ver Fuentes al final).

---

## 1. Principio rector (leer antes que nada)

**Importan más plantines DISTINTOS que muchas fotos del mismo plantín.**
30 fotos de 30 plantas distintas valen muchísimo más que 30 fotos de la misma planta. ¿Por qué? Porque al modelo le enseñás **variedad real** (distintos tamaños, tonos, fondos, severidades), no el mismo individuo repetido. La cantidad de fotos no es la métrica: la **diversidad** lo es.

Y de ahí sale la regla anti-trampa más importante 👇

> 🔒 **Regla de oro contra el "leakage":** todas las fotos de UN MISMO plantín tienen que terminar en la MISMA bolsa (o todas en entrenamiento, o todas en test). Si la foto de la planta #7 cae en train y otra foto de esa misma planta #7 cae en test, el modelo "ya la vio" → la métrica sale inflada y mentirosa. **Por eso conviene numerar cada planta** (ver sección 5).

---

## 2. Cuántas fotos — objetivo por clase

Basado en la literatura: con transfer learning, **8 fotos por clase ya empiezan a servir, 15-20 es usable, y 100+ es cómodo.** Como las usamos sobre todo para **validar**, apuntamos a un mínimo digno y, si se puede, más.

| Clase | Mínimo viable (solo test) | Ideal (test + algo de train) | Plantines DISTINTOS (ideal) |
| --- | --- | --- | --- |
| **Sano** | 20 | 60–100 | ≥15 |
| **Clorosis** | 15 | 50 | ≥10 |
| **Estrés solar** | 15 | 40 | ≥10 |
| **Daño biótico** | 15 | 40 | los que encuentres |
| **Colapso** (opcional) | 10 | 30 | los que encuentres |

- **Objetivo total realista:** ~250–400 fotos reales bien etiquetadas. Si conseguís más, mejor.
- **Sacá MUCHAS más de las que creés** y descartás después. El almacenamiento es gratis; volver a Misiones, no.
- **Si llegás justo de tiempo:** priorizá **Sano + Clorosis + Estrés solar** (las más fáciles de encontrar y etiquetar con seguridad).

---

## 3. Condiciones de captura — el punto que REFINA el informe

⚠️ **Corrección importante respecto al informe inicial:** ahí sugerí "fondo neutro". La literatura muestra algo clave: los modelos entrenados con **fondo artificial (cartulina gris, hoja arrancada)** —como PlantVillage— **fracasan en el campo real.** Y nuestro gantry NO va a ver fondos neutros: va a ver el tubete, el sustrato, plantas vecinas.

**Conclusión:** hay que fotografiar **en el vivero, en condiciones reales (in situ), con la planta entera en su lugar** — para que el entrenamiento se parezca al despliegue. NO arranques hojas ni uses cartulina. El fondo realista del vivero es parte de lo que el modelo tiene que aprender a tolerar.

La tensión a equilibrar:
- **Consistencia** (distancia/encuadre fijos) → ayuda con pocos datos.
- **Variedad realista** (luz, hora, plantas distintas) → ayuda a generalizar.
- **El equilibrio:** encuadre y distancia **consistentes**, pero condiciones de vivero **reales y variadas**.

---

## 4. Cómo sacar cada foto (protocolo de toma)

- 📐 **Ángulo:** **cenital** (desde arriba), igual que mira la cámara del riel. Es el ángulo que importa para el despliegue.
- 📏 **Distancia/encuadre:** el plantín **llenando el cuadro**, distancia y altura lo más constantes posible. Si conseguís un soporte/palo de selfie/mini-trípode para fijar la altura, mejor.
- 💡 **Luz:** difusa y pareja (día nublado o a la sombra, o misma hora del día). **Evitá sol directo duro y contraluz** (generan sombras y brillos que confunden). Variar un poco la luz entre plantas distintas está bien (da robustez), pero nunca foto quemada o muy oscura.
- 📱 **Dispositivo:** un buen celular alcanza. **Usá siempre el mismo** y **anotá el modelo** (la literatura recomienda documentar el equipo: resolución, etc.). Cámara limpia, foco hecho, máxima resolución.
- 🚫 **Sin filtros ni edición.** Foto cruda. Nada de retoques de Instagram.
- 🔢 **Cantidad por planta:** 2–4 tomas por plantín (un par de cenitales + algún ligero ángulo). Recordá: todas las de esa planta van juntas a la misma bolsa (regla de oro).

---

## 5. Etiquetado y metadatos (esto te salva en casa)

El error más caro es volver con 400 fotos y no saber cuál es cuál. Para cada planta:

- 🏷️ **Confirmá la clase con un agrónomo** si tenés acceso (la literatura insiste: etiquetas validadas por experto = oro). Si no, anotá tu mejor criterio y marcá las dudosas.
- 📝 **Planilla simple** (papel o celular) con una fila por planta: `ID planta | clase | severidad (leve/media/alta) | notas | (humedad/sector si lo sabés)`.
- 🗂️ **Convención de nombres de archivo** sugerida:
  ```
  yerba_[CLASE]_[IDplanta]_[N].jpg
  ej: yerba_clorosis_p07_02.jpg
  ```
  Así el nombre ya te dice clase y de qué planta es (clave para no romper la regla de oro al separar train/test).
- 📸 **Truco de campo:** sacá primero una foto a un papelito con el ID/clase de la planta, y después las fotos de esa planta. Así quedan agrupadas y ordenadas en la galería.
- ✅ **Priorizá casos NÍTIDOS:** una clorosis clarísima etiquetada con seguridad vale más que un caso ambiguo. Las etiquetas confiables son críticas.

---

## 6. Qué buscar en cada clase

- **Sano:** plantines vigorosos, verde parejo, sin manchas. Los más fáciles y abundantes — aprovechá para juntar muchos y de plantas distintas.
- **Clorosis:** amarillamiento (general o entre nervaduras). Muy visible. Buscá distintos grados.
- **Estrés solar:** blanqueo, manchas claras o necrosis en zonas más expuestas (puntas, bordes, cara al sol). Más probable en plantines recién sacados de la media sombra.
- **Daño biótico (plaga + hongo, todo junto):** cualquier daño visible — mordeduras, perforaciones, manchas, moteado, telarañas, polvillo blanco, costras. No hace falta distinguir qué bicho/hongo es (eso lo desambigua el sensor después). Juntá variedad.
- **Colapso (opcional):** plantines caídos, marchitos, tallito podrido en la base, muertos. Si los ves, fotografialos; habilitan la clase extra.

---

## 7. Plan B si una clase no aparece

Es probable que no encuentres todas las clases el mismo día. Opciones, en orden:
1. **Preguntá al viverista/agrónomo** dónde hay casos (ej. "¿tenés un sector con plantas amarillas / quemadas?"). Ellos saben dónde está el problema.
2. **Inducir/buscar activamente:** los plantines recién salidos de media sombra suelen mostrar estrés solar; los sectores con riego excesivo, hongos.
3. **Dejá esa clase con menos fotos** y reforzala con data donante en el entrenamiento (la yerba real, aunque sean pocas, igual valida).
4. **Vía remota (sección 8)** para completar lo que falte.
5. Si es **Colapso** y no aparece → se descarta sin drama (es opcional).

---

## 8. Vía remota: pedir fotos a viveros por Instagram / WhatsApp

Excelente idea para **ir adelantando antes del viaje** y sumar **diversidad de zonas** (distintos viveros = mejor generalización). Pero ojo, tiene reglas.

**Ventajas:**
- Empezás a juntar dato **ya**, sin esperar el viaje.
- Más variedad de viveros/condiciones → el modelo generaliza mejor.

**Desventajas (a manejar):**
- **Inconsistencia:** cada uno saca la foto distinta (cámara, luz, encuadre). → Hay que dar un mini-instructivo.
- **Etiquetas poco confiables:** el viverista no es agrónomo; puede llamar "hongo" a algo que no lo es. → Vos/un agrónomo tienen que **confirmar** cada foto.
- **Permiso de uso:** pedí autorización para usar las fotos en el proyecto (consentimiento simple).

**Cómo usarlas bien:**
- Las fotos remotas (etiqueta menos confiable, condiciones variadas) → mejor para **enriquecer el ENTRENAMIENTO** (diversidad).
- El **TEST** reservalo para las fotos que sacás vos con etiqueta confirmada por agrónomo. (No querés medir tu modelo contra etiquetas dudosas.)

**Mensaje tipo para mandar (editable):**

> Hola! Somos estudiantes de Ingeniería de la UNLaM, desarrollando un sistema de monitoreo para viveros de yerba mate (*Yerbanalytics*). Estamos armando un banco de fotos de plantines para entrenar un modelo que detecta problemas en etapa temprana. ¿Nos podrías mandar algunas fotos de plantines de yerba? Idealmente:
> - **Desde arriba** (cenital), la planta llenando la foto, con **luz pareja** (a la sombra o día nublado).
> - Si tenés casos de **plantas amarillas (clorosis), quemadas por sol, con manchas/bichos, o caídas/marchitas**, ¡mejor! Indicanos qué tiene cada una.
> - Sin filtros, lo más nítida posible.
>
> Las usaríamos solo con fines académicos. ¡Gracias, nos ayudan un montón! 🙌

**A quién pedirle:** viveros yerbateros, cooperativas, INTA Cerro Azul / Montecarlo, grupos de productores, facultades de agronomía de la zona (Misiones).

---

## 9. Kit a llevar a Misiones

- [ ] Celular con buena cámara (y **batería/power bank** — vas a sacar muchas).
- [ ] Espacio de almacenamiento libre (cientos de fotos en alta resolución).
- [ ] Soporte para altura fija (mini-trípode, palo, o algo improvisado).
- [ ] Objeto de **referencia de escala** (una regla o moneda en alguna foto por planta ayuda a dimensionar — opcional).
- [ ] Planilla impresa o app de notas para el etiquetado.
- [ ] Papelitos + fibrón para los IDs de planta (truco sección 5).
- [ ] Contacto de un **agrónomo/viverista** que confirme diagnósticos.
- [ ] Esta guía impresa.

---

## 10. Checklist final

**Antes del viaje:**
- [ ] Arrancar la vía remota (Instagram/WhatsApp) para ir juntando dato.
- [ ] Conseguir contacto de agrónomo/viverista en destino.
- [ ] Definir el celular que se usa y anotar su modelo.

**En el campo (por cada planta):**
- [ ] Foto del ID/clase → fotos cenitales de la planta (2–4).
- [ ] Encuadre lleno, luz pareja, sin sol duro, sin filtros.
- [ ] Anotar en la planilla: ID, clase, severidad, notas.
- [ ] Confirmar la clase con el agrónomo si se puede.

**Al volver:**
- [ ] Organizar por clase y por ID de planta (respetando la regla de oro).
- [ ] Apartar el set de TEST (yerba real, etiqueta confirmada) y NUNCA usarlo para entrenar.

---

## 11. Errores que te arruinan el dataset (evitar sí o sí)

- ❌ Mil fotos de la misma planta y pocas plantas distintas → poca diversidad.
- ❌ Fotos de la misma planta repartidas entre train y test → leakage, métrica falsa.
- ❌ Fondo artificial (cartulina, hoja arrancada) → no se parece al vivero real.
- ❌ Sol directo duro / contraluz / fotos quemadas u oscuras.
- ❌ Volver sin etiquetar (no saber qué clase es cada foto).
- ❌ Filtros o edición de la cámara/Instagram.
- ❌ Confiar a ciegas en la etiqueta de un no-experto (confirmá con agrónomo).

---

## Fuentes (investigación que respalda esta guía)

- [FieldPlant: A Dataset of Field Plant Images for Plant Disease Detection (ResearchGate)](https://www.researchgate.net/publication/369642460_FieldPlant_A_dataset_of_field_plant_images_for_plant_disease_detection_and_classification_with_deep_learning) — captura en campo, condiciones reales.
- [Smartphone image dataset for radish leaf disease (ScienceDirect)](https://www.sciencedirect.com/science/article/pii/S2352340924012253) — protocolo de captura con celular, documentar equipo, 15–20 tomas por hoja.
- [PlantVillage Dataset (GitHub / Figshare)](https://github.com/spmohanty/plantvillage-dataset) — fondo controlado y etiquetado por expertos (y su limitación en campo).
- [Transfer Learning in Image Classification: how much data do we need? (Medium / TDS)](https://medium.com/data-science/transfer-learning-in-image-classification-how-much-training-data-do-we-really-need-7fb570abe774) — 8 imágenes/clase ya sirven; ~800 dieron F1 0.79.
- [The Utility of Feature Reuse: Transfer Learning in Data-Starved Regimes (arXiv)](https://arxiv.org/pdf/2003.04117) — transfer learning efectivo con ~100 datos/clase.
- [The power of transfer learning in agricultural applications: AgriNet (NCBI)](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9794606/) — transfer learning en agricultura.
