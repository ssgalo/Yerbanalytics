# MVP — Nodo de Establecimiento (Vivero primario)

> **Propósito.** Definir un MVP técnicamente rico, agronómicamente honesto y demostrable en un prototipo de banco, para el Proyecto Final de Carrera (Ing. Informática). El criterio de éxito NO es la venta, sino: (1) complejidad técnica digna de una Ingeniería, (2) resolución de un problema REAL, (3) un modelo de negocio coherente y honesto que lo sostenga.
>
> **Decisión de alcance tomada:** el **vivero** es el contexto **primario** (donde se demuestra el bucle completo). El **campo (años 1-2)** queda como **teoría de escalabilidad** (sección 9). La opción "Nodo Bi-modo" fue evaluada y descartada por riesgo de cronograma.

---

## 1. Idea en una frase

**Un nodo autónomo que protege a la planta de yerba mate en su fase más vulnerable, empezando por el VIVERO: detecta por visión artificial las amenazas foliares reales del plantín (damping-off, clorosis, plagas de brotes tiernos), acciona físicamente sobre la infraestructura de riego/fertirriego que ya existe, integra el clima y registra cada decisión de forma trazable — con un diseño que escala del vivero al campo durante los dos primeros años de la planta.**

---

## 2. Por qué el vivero es el contexto primario

El vivero es donde **todo se alinea** para cerrar el bucle `sensar → inferir → actuar` de forma honesta y demostrable:

| Condición | En el VIVERO | Por qué importa |
|---|---|---|
| ¿La visión artificial funciona bien? | **Sí, mejor que en ningún lado** | Luz controlada (sombráculo 50-70%), fondo homogéneo (bandejas), plantines regulares |
| ¿Existe infraestructura para ACTUAR? | **Sí** | Riego por aspersión + fertirriego NPK ya instalados → el nodo solo comanda, no construye |
| ¿Hay urgencia real que justifique automatizar? | **Sí** | El damping-off mata la tanda en HORAS |
| ¿Una cámara cubre muchas plantas? | **Sí** | Miles de plantines en pocos m² → densidad altísima |
| ¿Energía y conectividad razonables? | **Sí** | Cerca del galpón → luz de red y WiFi estable |

> **El campo no se abandona: se hereda.** La misma planta, tras 6 meses a 2 años en el vivero, se trasplanta y pasa sus 2 años críticos a campo. El nodo protege la **etapa biológica** (planta joven), no un lugar. Por eso el diseño escala naturalmente del vivero al campo (sección 9).

---

## 3. Alcance honesto: qué hace y qué NO hace

Esto es lo que vuelve creíble el proyecto ante un profesor, aunque no sea experto: **no prometemos nada que la física no permita.**

### Sí hace (bucle sensar → inferir → actuar, en el vivero)
- **Detecta** por visión, con confianza y severidad:
  - **Clorosis** (déficit de N / Fe / Zn / Mg → hojas amarillas).
  - **Damping-off / "mal del tallito"** (plantín caído, marchito, lesión oscura en la base).
  - **Plagas de brotes tiernos** (pulgón / ácaro / cochinilla).
  - **Plantín sano** (clase de referencia).
- **Sensa**: humedad y temperatura del sustrato, temperatura y humedad del aire, radiación/luz, e imagen del follaje.
- **Decide** cruzando visión + sensores + pronóstico climático.
- **Actúa** físicamente sobre infraestructura existente: **fertirriego NPK** (dosificación), **aspersión** (riego en lluvia fina) y **regulación de sombra/ventilación**.
- **Monitorea el post-acción**: vuelve a medir/fotografiar para evaluar si la intervención funcionó.
- **Registra** cada evento → decisión → acción → resultado (trazabilidad auditable).

### NO hace (y lo decimos explícitamente)
- **No diagnostica el "Mal de Tela".** Hongo topográfico e incurable; no se detecta en hoja para accionar, se PREVIENE no plantando en los bajos. Fuera del bucle → se propone como **módulo de software topográfico** (sección 11).
- **No detecta el taladro.** Barrena DENTRO del tronco; una cámara de follaje no lo ve.
- **No riega un yerbal adulto a campo.** La yerba es de SECANO; no existe red de riego fija. La planta adulta es rústica (raíz a 4 m) y no necesita el nodo.

---

## 4. El problema real que resuelve

En el vivero, **el productor pierde tandas enteras y no llega a reaccionar a tiempo**:
- El **damping-off** pudre la base del plantín y mata en horas (el peor enemigo del vivero, según el productor).
- La **clorosis** por déficit de micronutrientes frena el desarrollo: hoja amarilla, plantín débil que no sirve para trasplante.
- Los **pulgones/ácaros** chupan la savia de los brotes tiernos.
- En el **vivero orgánico**, sin rescate químico, un descontrol de humedad un día de calor hace que el hongo malo le gane al bueno y se pierden miles de plantines.

El nodo ataca esa ventana de riesgo donde una decisión a tiempo salva la tanda. Cuando esa planta pasa a campo, el mismo principio la cuida en sus 2 años de establecimiento (donde aparecen rulo y golpe de sol — ver sección 9).

---

## 5. Arquitectura técnica (aquí vive la complejidad de Ingeniería)

```
┌──────────────────────────── NODO (vivero) ───────────────────────────────────┐
│                                                                                │
│  [Cámara] ──► Raspberry Pi 4/5  ──►  Inferencia Edge AI (TFLite)               │
│              (+ Coral USB opc.)      CNN: clorosis / damping-off / plaga / sano│
│                                      → clase + confianza + severidad           │
│                                                                                │
│  [Sensores] ──► ESP32 ──► humedad/temp sustrato, temp/hum aire, radiación      │
│                                                                                │
│        MOTOR DE DECISIONES LOCAL (offline-first): reglas + umbrales            │
│        cruzando visión + sensores + pronóstico climático                       │
│                          │                                                     │
│                          ▼                                                     │
│  [Actuadores] ◄── ESP32 ── fertirriego NPK (bomba peristáltica)                │
│                            · aspersión (bomba/válvula) · sombra/ventilación    │
│                                                                                │
│        Almacenamiento local (SQLite) + buffer → sincroniza con la nube         │
└────────────────────────────────────┬───────────────────────────────────────┘
                                      │ MQTT (WiFi)
                                      ▼
┌──────────────────────────── BACKEND (nube) ──────────────────────────────────┐
│  Broker MQTT (Mosquitto) → API (FastAPI)                                      │
│  Series temporales (TimescaleDB/InfluxDB) + relacional (PostgreSQL)           │
│  Integración clima (Open-Meteo / SMN / INTA)  ·  Motor de decisiones central  │
│  Pipeline MLOps: ingesta de imágenes → etiquetado → reentrenamiento → versión │
└────────────────────────────────────┬───────────────────────────────────────┘
                                      ▼
                         FRONTEND WEB (React): tablero de métricas,
                         alertas, diagnósticos, historial de acciones,
                         seguimiento post-acción
```

### Capas y stack
- **Capa física (nodo):** ESP32 para sensores/actuadores; Raspberry Pi 4/5 para la cámara y la **inferencia Edge AI** (un microcontrolador solo no corre una CNN real — esta separación es deliberada y defendible).
- **Capa de inferencia:** modelo CNN cuantizado a **TensorFlow Lite** corriendo localmente → cumple la promesa de "funciona sin internet".
- **Capa de decisión:** motor de reglas que combina **visión + sensores + clima**, con umbrales de confianza y severidad.
- **Capa de comunicación:** **MQTT**; arquitectura *offline-first* con buffer local y sincronización eventual.
- **Capa de backend:** ingesta, almacenamiento (series temporales + relacional), motor central y **pipeline MLOps**.
- **Capa de presentación:** tablero web de supervisión y trazabilidad.

---

## 6. El bucle cerrado, con ejemplos de decisión reales

| Detección / lectura | Cruce con clima/contexto | Acción física | Post-acción (verificación) |
|---|---|---|---|
| **Clorosis** detectada (confianza > 0.85) | — | **Ajustar fertirriego NPK / micronutrientes** | Re-fotografiar en N ciclos; ¿revierte el amarillamiento? |
| **Damping-off** (síntomas tempranos) | Humedad de aire alta + calor | Reducir riego + aumentar ventilación + alertar (convencional: fungicida) | Re-medir humedad; re-inspeccionar foco |
| Humedad de sustrato < umbral | Día de calor intenso | Activar aspersión (lluvia fina) | Re-medir humedad tras N min |
| Radiación alta sobre plantín | Pronóstico de temp. extrema | Regular sombra/ventilación | Re-medir temp./radiación incidente |
| **Plaga de brote** (pulgón/ácaro) | — | Dosificar acaricida (o biocontrol si orgánico) + alerta | Re-fotografiar brote; evaluar avance |

> **El caso estrella (lazo cerrado COMPLETO):** la cámara detecta **clorosis** → el motor decide → **acciona el fertirriego NPK que ya está instalado** → re-fotografía y verifica si revierte. Sensar → inferir → actuar → verificar, sin trampa, sobre infraestructura real. **Eso es exactamente la consigna del proyecto.**

---

## 7. Modelo de visión artificial (el corazón, y tu diferencial)

- **Clases (v1, vivero):** `clorosis`, `damping_off`, `plaga_brote`, `sana`. Ampliable a tareas de **conteo de plantines** y **medición de altura** (¿listo para rustificación?) en v2.
- **Clases de escala (campo):** `rulo`, `golpe_de_sol` — la misma arquitectura de red, solo cambia el dataset. *(El rulo es, agronómicamente, una amenaza de campo → clase de escala.)* **Decisión del equipo: el rulo SÍ se demuestra físicamente en el MVP.** Se capturarán fotos de rulo en el viaje a Misiones para entrenar la clase, y se traerá un **plantín real con rulo** para que la cámara lo detecte en vivo. Así el MVP exhibe que el modelo cubre también el campo. El documento mantiene explícito qué es de vivero (bucle completo) y qué es de escala (rulo) — no se confunde *alcance teórico* con *demostración*.
- **Salidas:** clase + **confianza** + **severidad** (estimada por % de área foliar afectada / nº de plantines comprometidos).
- **Técnica:** *transfer learning* sobre red liviana (MobileNetV2 / EfficientNet-Lite) → *fine-tuning* con dataset propio → cuantización a TFLite para el edge.
- **Dataset propio (acceso real vía Tío Pablo — lo que nadie más tiene):**
  1. Protocolo de captura en el vivero/chacra de Andresito: plantines con clorosis, focos de damping-off, brotes con plaga y plantines sanos, en distintas horas y condiciones. **En el viaje a Misiones se capturarán además fotos de brotes con RULO** para entrenar esa clase de escala (que se demuestra en el MVP).
  2. **Etiquetado validado agronómicamente** (Tío Pablo / ingeniero agrónomo confirma cada etiqueta).
  3. *Data augmentation* (rotación, brillo, recorte) para multiplicar muestras.
  4. Versionado del dataset y del modelo (MLOps).
- **Honestidad sobre el sensor NPK:** los sensores NPK baratos son imprecisos. Se usan como variable de **apoyo** del fertirriego en vivero, no como verdad absoluta. A campo, el problema real del suelo es el **pH ácido / aluminio**, que se corrige con calcáreo UNA vez antes de plantar (decisión puntual, no telemetría).

---

## 8. Qué se demuestra en el prototipo de banco

Un **plantín de yerba en bandeja/tubete** (o maceta) instrumentado:
- ESP32 + sensores (humedad/temp sustrato, temp/hum aire, luz/UV) + Raspberry Pi con cámara.
- Actuadores a escala: **bomba peristáltica** (fertirriego/dosificación), **mini bomba/aspersor** (riego), **micro-servo** (regulación de sombra/ventilación).
- El modelo clasifica una hoja real → el motor decide → el actuador se acciona → el tablero web muestra el evento, la decisión y el resultado.

> **El MVP es una combinación pensada para mostrar lo máximo posible:** junto a los plantines de vivero (donde el bucle cierra completo con el fertirriego), se exhibe **un plantín real con RULO traído de Misiones** para demostrar la detección por visión de una amenaza de campo. La distinción vivero (bucle) vs. escala (rulo) queda explícita en este documento; en la demo física se enseñan ambas.

**Resultado demostrable de punta a punta:** lectura → inferencia → acción → verificación → trazabilidad. La consigna del proyecto, funcionando físicamente.

---

## 9. Escalabilidad: del nodo único a la red (la teoría que se defiende)

El MVP de banco es **la unidad atómica** (un nodo, un plantín). La escala NO se hace clonando el nodo completo por planta — eso sería carísimo y un error de diseño. Se escala **separando las variables según su naturaleza espacial**:

| Tipo de variable | Naturaleza | Cómo escala |
|---|---|---|
| **Ambientales** (temp, humedad, luz, humedad de sustrato) | Compartidas por muchas plantas vecinas | Pocos **nodos sensores por ZONA/microclima**, no por planta |
| **Visión** (salud individual) | Por planta | **Vivero:** una cámara fija o sobre **riel/gantry** cubre miles de plantines. **Campo:** **captura móvil periódica** (celular del productor, rover o drone) |
| **Actuación** | Por sector | **Electroválvulas sectorizadas** sobre la red de aspersión/fertirriego existente |

### Cómo escala cada componente (sensores · cámara · actuadores)

**🌡️ Sensores (variables ambientales — se comparten por zona, NO por planta)**
- **Vivero:** el ambiente bajo el sombráculo es uniforme → pocos **nodos sensores por mesada/sector** (temp/hum aire, luz; humedad de sustrato representativa por lote de bandejas) cubren miles de plantines. Energía de red, WiFi. *Escalar = sumar un nodo sensor por cada sector nuevo.*
- **Campo (años 1-2):** el lote NO es uniforme (lomas, bajos, curvas de nivel = microclimas distintos) → los nodos se ubican **por zona de microclima/topografía** (alineado con la red híbrida del Punto 1), priorizando las zonas más secas/vulnerables. Energía autónoma (solar + batería + deep sleep), conectividad LoRa/offline-first. *Escalar = un nodo por zona de manejo, nunca uno por planta.*

**📷 Cámara (salud individual — se resuelve por barrido, NO por cámara fija por planta)**
- **Vivero:** cámara sobre **riel/gantry motorizado** que se desplaza sobre las mesadas y escanea fila por fila → una sola cámara cubre miles de plantines, con luz pareja. *Escalar = extender el riel / agregar tramos por sombráculo.*
- **Campo (años 1-2):** plantas dispersas → no hay riel ni cámara por planta. **Captura móvil periódica:** (1) el productor/peón con el **celular** en sus recorridas (app que sube las fotos); (2) un **rover** que recorre las calles de 3 m (ya diseñadas para que pase el tractor); (3) a futuro, **drone** para cobertura general. Frecuencia: pasadas semanales/quincenales, no continua. *Es la parte más cara y difícil del campo — se reconoce. Escalar = más recorrido, no más hardware fijo.*

**⚙️ Actuadores (acción física — se sectorizan donde hay infraestructura)**
- **Vivero:** el nodo **comanda electroválvulas sectorizadas** sobre el riego/fertirriego que YA existe; un actuador de sombra por paño de malla; un punto de dosificación por sector de riego. *Un actuador acciona sobre cientos/miles de plantines. Escalar = un actuador por sector existente.*
- **Campo (años 1-2):** NO hay red de riego fija (secano) → la actuación física **no escala como automatismo**. Se degrada a **capa de alerta/recomendación**: el sistema avisa "zona X necesita agua/sombra" y el productor responde (tractor cisterna por los caminos de 100 m, tablita manual, aplicación localizada). *Escalar = el valor escala como decisión/alerta, no como hardware que actúa solo.*

> **La regla de oro de la escala:** sensores por **zona**, cámara por **barrido**, actuadores por **sector** (y a campo, por **alerta**). Nunca "un nodo completo por planta" — eso sería técnica y económicamente inviable.

**Arquitectura de la red (diseño teórico):**

```
        ☁️ BACKEND multi-zona / multi-lote
                   ▲
              📶 GATEWAY (agrega varios nodos, sincroniza)
              ╱        │              ╲
   [Nodo sensor]   [Nodo sensor]   [Cámara fija/riel (vivero)
    zona A           zona B          o captura móvil (campo)]
    (muchas          (muchas        → actuadores SECTORIZADOS
     plantas)         plantas)
```

### Del vivero al campo (la transición de escala)
- **Vivero (primario):** energía de red, WiFi estable, una cámara en riel sobre las mesadas, actuación sobre fertirriego/aspersión existente. El bucle cierra completo.
- **Campo años 1-2 (escala):** el **mismo nodo** se vuelve autónomo → **panel solar + batería + deep sleep**, **offline-first** (Starlink intermitente), **captura móvil** de imágenes (la visión continua por planta a campo es la parte cara y difícil — se reconoce explícitamente), y la acción de riego **se degrada a alerta** de riego de emergencia (no hay red fija). La visión (rulo, golpe de sol) y la sombra/"tablita" sí son acción directa.

> Esto **coincide con la "red híbrida" que ya describe `1_ResumenPreliminar.md`** (distribución de nodos por topografía/curvas de nivel, gateways, visión desacoplada del hardware fijo). El formato es el mismo que el Punto 1 usa con "Nodo MVP vs. red a 5 ha": **se construye la unidad, se diseña la red.**

---

## 10. Cuestionable → Solución (uno por uno)

| # | Cuestionable detectado | Solución en este MVP |
|---|---|---|
| 1 | No hay riego fijo a campo (secano) | El bucle de riego se demuestra en el VIVERO (infra existente). A campo, la acción se declara como alerta de emergencia. |
| 2 | Media sombra automatizada irreal en planta adulta | Se aplica a plantín (sombráculo en vivero, tablita en campo joven), donde es real. |
| 3 | Catálogo de visión irreal (taladro, etc.) | Se recalibra a lo foliar y detectable: clorosis, damping-off, plagas de brote (vivero); rulo, golpe de sol (campo). |
| 4 | Mal de Tela rompe "detectar + actuar" | Se excluye del bucle; se propone como módulo de software topográfico (prevención). |
| 5 | ¿Dónde vive la cámara? (contradicción tractor) | Cámara FIJA (vivero) / captura móvil periódica (campo). Sin tractor → sin contradicción. |
| 6 | Dataset propietario era una aspiración | Se construye dataset real con acceso al vivero del Tío Pablo + etiquetado validado por agrónomo. |
| 7 | Sensores NPK imprecisos | Uso como apoyo del fertirriego en vivero. A campo se reconoce que el problema es pH/aluminio (decisión puntual). |
| 8 | Conectividad inestable | Arquitectura offline-first: inferencia y decisión locales, buffer y sincronización eventual. |
| 9 | Energía en zona remota | Vivero: red. Campo: bajo consumo + deep sleep + panel solar + batería. |
| 10 | Edge offline vs. suscripción cloud | La nube aporta lo que el edge no: histórico multi-período, reentrenamiento, trazabilidad y tablero multi-zona. |
| 11 | Clima mal planteado ("no regar si llueve") | El clima se reorienta a ventana de plantación, riego de emergencia y timing; se sugiere Open-Meteo/SMN/INTA en vez de solo OpenWeatherMap. |
| 12 | Escala 1 planta = 1 nodo (error) | Se escala separando variables: sensores por zona, visión por riel/móvil, actuación sectorizada (sección 9). |

---

## 11. Complejidad técnica para la cátedra (resumen para defender la nota)

1. **Edge AI**: CNN cuantizada (TFLite) corriendo inferencia local en hardware embebido.
2. **Fusión de sensores + motor de decisiones** con umbrales de confianza/severidad.
3. **Integración de API climática** dentro de la lógica de decisión.
4. **Arquitectura distribuida offline-first** con sincronización eventual (sistemas distribuidos).
5. **Pipeline MLOps**: dataset propio, etiquetado, reentrenamiento, versionado de modelos.
6. **Modelado de datos** mixto: series temporales + relacional para trazabilidad auditable.
7. **Lazo de control cerrado** con realimentación post-acción.
8. **IoT embebido**: firmware, MQTT, gestión de actuadores; deep sleep en el perfil de campo.
9. **Diseño de escalabilidad** jerárquico (nodos por zona + gateway + captura de visión + backend multi-zona).

---

## 12. Módulo complementario sugerido (alto valor, bajo costo)

**Software de planificación de lote (topografía / curvas de nivel / drenaje).** Lo pidió el propio productor. Diseña curvas de nivel, marco de plantación y captación de agua, y **previene el mal de tela** (evita plantar en los bajos). Suma complejidad algorítmica (procesamiento de elevación/pendientes) y se alinea con los "algoritmos topográficos" del Punto 1. Módulo aparte, no el núcleo.

---

## 13. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Dataset insuficiente | Capturar desde ya en el vivero del Tío Pablo; *data augmentation*; *transfer learning*; datasets públicos de plagas/hongos foliares. |
| Hardware demora (importación) | Mocks de sensores/actuadores para avanzar el software en paralelo; proveedores locales. |
| Disponibilidad del productor/agrónomo | Reuniones planificadas; etiquetado por lotes asincrónico; conocimiento ya documentado en `ConocimientoProductor.md`. |
| Modelo poco preciso al inicio | Empezar con menos clases bien resueltas (clorosis + sana) antes de ampliar. |

---

## 14. Veredicto

**MVP enfocado, honesto y demostrable.** El vivero da el bucle completo y cerrado (visión → fertirriego → verificación) sobre infraestructura que ya existe, en un ambiente donde la visión artificial rinde al máximo. El campo (años 1-2) queda como teoría de escalabilidad rigurosa, alineada con la red híbrida que el Punto 1 ya plantea. Complejidad de ingeniería de sobra y un diferencial real: dataset propio de yerba mate.
