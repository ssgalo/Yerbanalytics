# Cuestionables y Mitigaciones — Puntos 1 y 2

> **Qué es este documento.** Tras la re-ingeniería de diseño del producto (MVP **vivero-primario**, ver `Documentacion/MVP/1_NodoDeEstablecimiento.md`), cada cuestionable detectado en los Puntos 1 y 2 deja de ser una duda abierta y pasa a tener una **respuesta de mitigación** y un **estado**. También se indica **qué corregir** en cada punto.
>
> **Perfil que origina la duda:** 🌱 Agronómico · 🏗️ Arquitectura/SW · 💼 Negocio.
>
> **Estados:** 🟢 Resuelto por el rediseño · 🟡 A corregir en el documento · 🔵 A verificar (viaje a Misiones / agrónomo).

## Criterio de fondo (el filtro que ordena todo)

El objetivo es **aprobar Proyecto Final de Carrera (Ing. Informática)**, no vender. La cátedra evalúa **complejidad técnica + problema real + modelo honesto**, NO viabilidad comercial. Por eso los cuestionables se dividen en dos clases:

- **FÍSICO-AGRONÓMICOS** (🌱🏗️): son **sagrados**. Si el sistema promete algo que la física/biología no permite, ahí sí mentimos. Condicionan el diseño y ya están resueltos por el rediseño.
- **COMERCIALES** (💼): **no condicionan el MVP académico**. Se mantienen reencuadrados de forma honesta para que el modelo sea creíble, pero no gatillan decisiones de ingeniería.

---

# PUNTO 1 — Resumen Preliminar (a grandes rasgos)

> El Punto 1 se mantiene a **alto nivel**. El alcance detallado (arquitectura, clases de visión, escalabilidad, stack) se desarrolla en el **Punto 3**, tomando la información de `1_NodoDeEstablecimiento.md`.

### Cuestionables y mitigaciones

- 🌱🏗️ **El riego automatizado a campo no tiene infraestructura sobre la cual actuar (secano).**
  → **Mitigación:** el bucle de riego se demuestra en el **vivero**, donde el riego por aspersión y el fertirriego YA existen. A campo, la acción de agua se reencuadra como **alerta de riego de emergencia** (no como riego fijo automático). `🟢 Resuelto`

- 🌱 **La mediasombra automatizada es de vivero/plantín, no de planta adulta.**
  → **Mitigación:** el actuador de sombra se aplica al **plantín** (sombráculo en vivero, "tablita" digital en campo joven), donde es real y mecánicamente viable. `🟢 Resuelto`

- 🏗️ **¿Dónde vive la cámara? El Punto 1 dice nodo fijo; el Punto 2 dice visión "acoplada a tractores".**
  → **Mitigación:** cámara **fija** (vivero, sobre riel/gantry) y **captura móvil periódica** a escala de campo. Se elimina la visión en tractor (cosecha 1 vez/año, productor mediano sin máquina). `🟡 A corregir`

- 🏗️🌱 **El "dataset propietario" figura como recurso EXISTENTE, pero es una aspiración (riesgo #1).**
  → **Mitigación:** plan concreto de **dataset propio** con acceso real al vivero del Tío Pablo + **etiquetado validado por agrónomo** + *data augmentation* + *transfer learning*. Se reclasifica de "activo que tenemos" a "activo que construimos". `🟢 Resuelto`

- 🌱🏗️💼 **El MVP apuntaba al lugar de menor dolor (yerbal adulto rústico).**
  → **Mitigación:** el "Nodo MVP" se reorienta a la **planta joven (vivero + establecimiento)**, que es la ventana de riesgo real y donde el bucle sensar→inferir→actuar tiene sentido físico. `🟢 Resuelto`

- 🏗️ **Conectividad (Starlink) y energía asumidas como dadas, cuando son los cuellos de botella.**
  → **Mitigación:** arquitectura **offline-first** (inferencia y decisión locales, buffer y sync eventual) y, en el perfil de campo, **panel solar + batería + deep sleep**. En vivero hay luz de red y WiFi. *(El Punto 1 ya las lista como riesgos; reforzar.)* `🟢 Resuelto`

- 🏗️ **La "red híbrida a 5 ha" como horizonte de escalabilidad estaba poco aterrizada.**
  → **Mitigación:** la escala se diseña separando variables por naturaleza espacial (**ambientales → nodos por zona; visión → cámara fija/riel en vivero o captura móvil en campo; actuación → sectorizada**), con **gateway** y **backend multi-zona**. Coincide con la red híbrida del propio Punto 1. `🟢 Resuelto`

### ✏️ Qué corregir en el Punto 1

1. **Reescribir el despliegue a escala** para que no prometa riego sectorizado fijo a campo: aclarar **secano** + riego de **emergencia/alerta**.
2. **Quitar la "visión acoplada a maquinaria móvil/tractores"** como núcleo; reubicarla como **captura móvil de escala** (campo), no como el método principal.
3. **Reclasificar el dataset** de recurso existente a **recurso a construir** (mantenerlo como riesgo #1 con su mitigación).
4. **Alinear el "Nodo MVP"** con la planta joven / vivero (no una planta genérica adulta).
5. **Mantener todo a grandes rasgos** y remitir el alcance completo (arquitectura, clases, stack, escalabilidad) al **Punto 3** (fuente: `1_NodoDeEstablecimiento.md`).

---

# PUNTO 2 — Modelo de Negocio

## Dudas de fondo (cruzan todo el Punto 2)

- 🌱🏗️ **Catálogo de "afecciones detectables por visión" mal calibrado (incluía el taladro).**
  → **Mitigación:** se recalibra a lo **foliar y detectable**. Vivero: `clorosis`, `damping_off`, `plaga_brote`, `sana`. Escala/campo: `rulo`, `golpe_de_sol`. Se excluye el **taladro** (barrena dentro del tronco, no lo ve una cámara). `🟢 Resuelto`

- 🌱 **El "Mal de Tela" rompe el esquema detectar+actuar (hongo topográfico e incurable).**
  → **Mitigación:** sale del bucle de visión; su valor se traslada a un **módulo de software topográfico** (curvas de nivel/drenaje) que **previene** plantar en los bajos. `🟢 Resuelto`

## 2.2.1 Segmento de mercado

- 💼 **"Productor mediano" es un segmento angosto; ¿cierra el TAM/SAM?**
  → **Mitigación (comercial):** no condiciona el MVP académico. Se reencuadra el cliente hacia quien **opera un vivero** (productor con vivero propio o **cooperativa con vivero central**), donde el dolor y la densidad de valor son altos. `🟡 A corregir` `🔵 A verificar`
- 💼 **Falta el INYM como regulador del sector.**
  → **Mitigación:** agregar al **INYM** como actor de regulación/segmento (y en Socios). `🟡 A corregir`
- 💼🌱 **Orgánico vs. convencional define la actuación (el orgánico no admite químicos).**
  → **Mitigación:** el MVP apunta primero a **vivero convencional** (fertirriego/fungicida reales). Se contempla un **flag "orgánico"** que desactiva dosificación química y deja control ambiental + alerta. `🟢 Resuelto`

## 2.2.2 Propuesta de valor

- 💼🌱 **"Dosificación exacta sectorizada a campo" = agricultura de precisión de gama alta; ROI dudoso.**
  → **Mitigación:** se reemplaza por la **dosificación en vivero (fertirriego)**, que ya existe y cierra el bucle. La aplicación sectorizada a campo se baja a visión de escala. `🟢 Resuelto`
- 🌱 **La "red de sensores NPK" a campo casi no tiene caso de uso (el problema es pH/aluminio, decisión puntual).**
  → **Mitigación:** el NPK se usa como **variable de apoyo del fertirriego en vivero**, no como telemetría continua a campo. Se reconoce que el suelo se corrige con **calcáreo una vez antes de plantar**. `🟢 Resuelto`
- 💼 **La ORTIGA es un killer comercial (rechazo de lote); la visión de malezas no figura.**
  → **Mitigación:** se incorpora la **detección de malezas (ortiga)** como clase de **escala/campo** de alto valor (no en el MVP de vivero, pero sí en la visión de producto). `🟡 A corregir` `🔵 A verificar`
- 🏗️ **Contradicción: "todo corre local (Edge AI offline)" vs. "suscripción cloud mensual".**
  → **Mitigación:** se define qué entrega la nube que el edge no: **histórico multi-período, reentrenamiento (MLOps), trazabilidad y tablero multi-zona, soporte**. Eso justifica la cuota. `🟢 Resuelto`

## 2.2.3 Canales

- 💼 **El productor opera por WhatsApp/teléfono, no por portal de tickets.**
  → **Mitigación:** adoptar **WhatsApp/notificaciones** como canal principal hacia el productor (impacta la arquitectura: integración WhatsApp Business API / SMS). `🟡 A corregir`
- 💼 **Ferias y web B2B suenan a marketing urbano; el canal real es el vecino/cooperativa/INTA-INYM.**
  → **Mitigación:** reencuadrar canales de conocimiento hacia **cooperativas, INTA/INYM y boca a boca**. `🟡 A corregir` `🔵 A verificar`
- 💼 **"Demostraciones in situ": real pero caro y poco escalable.**
  → **Mitigación:** **nodo demo itinerante** o demo en una **chacra/vivero "vidriera"** de una cooperativa. `🟡 A corregir`
- 🌱 **Vocabulario foráneo ("parcelas").**
  → **Mitigación:** alinear el lenguaje al territorio: **"Rosado"**, chacra, yerbal, lote, líneo, tarefero, raído, tablita, despunte, recepo. `🟡 A corregir`

## 2.2.4 Relaciones con los clientes

- 💼 **La "Mesa de Ayuda B2B" como cara al productor está mal ubicada (lenguaje corporativo).**
  → **Mitigación:** la relación con el productor es **directa y humana (WhatsApp/teléfono)**. La mesa de ayuda queda como **función INTERNA** de trazabilidad/soporte, no como interfaz del productor. `🟡 A corregir`
- 🏗️💼 **La "co-creación" con cooperativas/agrónomos depende de un loop de datos que hoy no existe.**
  → **Mitigación:** se concreta vía el **pipeline MLOps + dataset etiquetado** (validación agronómica continua). Es una capacidad del diseño, no solo una promesa. `🟢 Resuelto`

## 2.2.5 Fuentes de ingresos

- 💼 **"Tarifa por hectárea" sobre márgenes finos y precio regulado.**
  → **Mitigación (comercial):** no condiciona el MVP académico. Reencuadrar el cobro a una unidad coherente con el vivero (**por nodo / por bandeja-capacidad / por temporada**), no por hectárea de yerbal adulto. `🟡 A corregir`
- 💼 **Hardware de margen bajo + roturas en campo: ¿quién absorbe el reemplazo?**
  → **Mitigación:** contemplar el **costo real de mantenimiento del parque** en el modelo (y diseño robusto para ambiente húmedo). `🟡 A corregir`

## 2.2.6 Recursos clave

- 🏗️🌱 **El dataset propietario se lista como activo existente (es a construir).**
  → **Mitigación:** ver Punto 1 — plan de captura propio + etiquetado validado. Reclasificar. `🟢 Resuelto`
- 🏗️🌱 **Sensores NPK imprecisos.**
  → **Mitigación:** uso como **apoyo en vivero**; se reconoce su imprecisión y no se le delega la decisión crítica. `🟢 Resuelto`
- 🌱 **Falta la infraestructura de riego como recurso (si se insiste con el actuador de riego a campo).**
  → **Mitigación:** ya no se insiste con riego fijo a campo. En vivero, el riego/fertirriego **lo provee el vivero** (el nodo solo comanda). `🟢 Resuelto`

## 2.2.7 Actividades clave

- 🏗️ **Conectividad (Starlink) y energía deberían ser actividades/recursos explícitos.**
  → **Mitigación:** agregar **aprovisionamiento y mantenimiento de conectividad + alimentación autónoma** como actividad operativa (perfil de campo). `🟡 A corregir`
- 🏗️💼 **El reentrenamiento (MLOps) implica subir imágenes: ¿costo de ancho de banda sobre satélite?**
  → **Mitigación:** la arquitectura sube **solo casos dudosos / submuestreo / compresión**, no imágenes masivas. Decisión de diseño explícita. `🟢 Resuelto`
- 💼 **"Resolución de problemas / mesa de ayuda" hacia el productor sobra.**
  → **Mitigación:** se mantiene como **trazabilidad/soporte interno**, no como tarea del productor. `🟡 A corregir`

## 2.2.8 Socios clave

- 💼 **Falta el INYM y cooperativas nominales de la zona.**
  → **Mitigación:** agregar **INYM** y la **Cooperativa Andresito** (socio/canal y punto de control de calidad). `🟡 A corregir`
- 💼 **La Cooperativa Andresito es el socio/canal concreto (secadero, trazabilidad, rechazo por ortiga).**
  → **Mitigación:** figurar explícita en Socios y Canales. `🟡 A corregir`
- 🏗️💼 **Starlink/ISP satelital es un socio clave, no un detalle.**
  → **Mitigación:** listar al proveedor de conectividad como **alianza estratégica** (riesgo de disponibilidad/costo). `🟡 A corregir`
- 💼 **Riesgo cambiario en la cadena de hardware.**
  → **Mitigación:** plan B de **proveedores locales** y componentes con stock nacional. `🟡 A corregir`

## 2.2.9 Estructura de costos

- 💼🏗️ **Faltan OPEX propios del contexto: conectividad satelital por nodo y ancho de banda de imágenes.**
  → **Mitigación:** incorporar **abono Starlink por antena** y el costo de datos (mitigado por submuestreo/compresión). `🟡 A corregir`
- 💼 **La logística de campo en Misiones es cara y específica.**
  → **Mitigación:** dimensionar el **costo variable de traslado/instalación/mantenimiento** con datos reales de la zona. `🟡 A corregir` `🔵 A verificar`

## 2.3 Oferta — Cuadro de competidores

- 💼 **Falta el competidor más importante: el STATUS QUO** (seguir haciendo lo de siempre).
  → **Mitigación:** agregar el **status quo** (recorrida manual, criterio propio, asesor puntual) como rival real a vencer. `🟡 A corregir`
- 💼 **Análisis corto (2 competidores extranjeros/genéricos).**
  → **Mitigación:** ampliar con **agtech argentina/regional** y soluciones de visión/plagas más directas. `🟡 A corregir` `🔵 A verificar`

## 2.4 Oferta — Productos complementarios

- 🏗️ **OpenWeatherMap: cobertura hiperlocal en Misiones a verificar; hay alternativas mejores.**
  → **Mitigación:** evaluar **Open-Meteo, SMN e INTA** (datos locales/oficiales); no depender de un solo proveedor extranjero. `🟢 Resuelto` `🔵 A verificar`
- 🌱🏗️ **El caso de uso del clima estaba mal planteado ("no regar si llueve").**
  → **Mitigación:** reorientar el dato climático a **ventana de plantación, riego de emergencia y timing de cosecha**. `🟢 Resuelto`
- 💼 **ERP de cooperativas: existen pero son heterogéneos y sin APIs modernas.**
  → **Mitigación:** mantener como **integración futura caso por caso** (no en el MVP); reconocer el costo. `🟡 A corregir`

## 2.5 Análisis económico-financiero

- 💼 **¿El ROI cierra en 5 hectáreas?**
  → **Mitigación (comercial):** no condiciona el MVP académico. Además, el **vivero tiene ROI más defendible** por la densidad de valor (miles de plantines por m², pérdida catastrófica de una tanda). El análisis financiero se plantea sobre el contexto vivero. `🟡 A corregir`

### ✏️ Qué corregir en el Punto 2 (resumen)

| Módulo | Corrección principal |
|---|---|
| 2.2.1 Segmento | Cliente = quien opera vivero (productor/cooperativa); agregar INYM; foco convencional |
| 2.2.2 Propuesta | Quitar dosificación sectorizada a campo y NPK telemétrico; centrar en bucle de vivero; malezas/ortiga como escala |
| 2.2.3 Canales | WhatsApp como canal principal; cooperativa/INTA-INYM; vocabulario local ("Rosado") |
| 2.2.4 Relaciones | Mesa de ayuda = interna; relación directa por WhatsApp |
| 2.2.5 Ingresos | Repensar unidad de cobro (por nodo/temporada, no por hectárea); contemplar mantenimiento |
| 2.2.6 Recursos | Dataset = a construir; matizar NPK |
| 2.2.7 Actividades | Agregar conectividad/energía; estrategia de ancho de banda; sacar helpdesk al productor |
| 2.2.8 Socios | Agregar INYM, Cooperativa Andresito, Starlink; proveedores locales |
| 2.2.9 Costos | Agregar OPEX Starlink + ancho de banda + logística de campo |
| 2.3 Competidores | Agregar status quo; ampliar con agtech regional |
| 2.4 Complementarios | Open-Meteo/SMN/INTA; reorientar uso del clima |
| 2.5 Financiero | Plantear sobre contexto vivero; nota académica |

---

# Cierre

## Oportunidades detectadas → dónde quedaron en el diseño

| Oportunidad (insumo original) | Estado en el MVP |
|---|---|
| El **vivero** como contexto del MVP | ✅ Adoptado como contexto **primario** |
| Foco en la **fase de establecimiento** (años 1-2) | ✅ Es la teoría de **escalabilidad** (vivero → campo) |
| **Software de topografía** (curvas de nivel/drenaje) | ✅ Propuesto como **módulo complementario** |
| Detección de **malezas (ortiga)** | 🔵 Clase de **escala/campo** a sumar en la visión de producto |

## A verificar en el viaje a Misiones / con el agrónomo (🔵)

1. ¿El cliente objetivo **opera un vivero propio** o compra los plantines a un tercero/cooperativa?
2. ¿El **vivero tiene energía e internet** razonables o también depende de soluciones autónomas?
3. ¿**Quién paga**: productor con vivero, cooperativa con vivero central, o vivero comercial?
4. Capturar **fotos de damping-off, clorosis, plaga de brote y RULO** para el dataset.
5. ¿La **cátedra acepta** acotar el MVP al vivero, con el campo como visión de escalabilidad?

> **Nota:** la decisión vivero vs. campo **ya está tomada** (vivero-primario). El análisis comparativo que motivó esa decisión vive ahora en `Documentacion/MVP/1_NodoDeEstablecimiento.md` §2.
