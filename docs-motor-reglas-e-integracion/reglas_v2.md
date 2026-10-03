# Reglas agronómicas del motor de decisiones — v2

> **Versión 2 · 28/09/2026.** Reemplaza a `reglas.md` a partir de la revisión del equipo
> (`review_reglas.txt`). Las secciones 1 a 8.4 aplican lo que se decidió en la revisión. Las que
> todavía no se revisaron (8.5, 10) sólo se adaptaron para que no contradigan esos cambios, y
> están marcadas con **🔶 Sin revisar**. Al final, el **Anexo A** resume qué cambió respecto de la
> v1 e incluye la equivalencia de IDs.
>
> Estos son los **valores de fábrica** que pide HU-15 CA-01. Cada número es un **parámetro
> configurable** (§11), no una constante de código.

---

## 0. Cómo leer este documento

| Campo | Qué dice |
|---|---|
| **Cuando** | Las condiciones, que tienen que cumplirse **todas a la vez** salvo que diga "alguna". |
| **Entonces** | La acción: qué actuador, sobre qué alcance y **cuánto**. |
| **Alerta** | Severidad según HU-10 (`INFO` · `WARNING` · `CRITICAL`). |
| **Por qué** | La razón agronómica. Sirve de "condición desencadenante" para la trazabilidad de HU-11. |

Prefijos: **S** seguridad · **R** riego · **M** mediasombra · **N** nutrición · **F** reglas por
diagnóstico · **P-D** precondiciones de dosificación · **E** efectividad · **O** operación sin internet.

---

## 1. Supuestos de referencia

### 1.1 Cultivo y estructura

| Supuesto | Valor de referencia |
|---|---|
| Especie y etapa | *Ilex paraguariensis*, plantín en vivero hasta el trasplante |
| Contenedor | Bandeja de **25 celdas de 100 cm³** (~28 × 27 cm), sustrato de corteza de pino compostada con **fertilizante de liberación lenta de base** (2–3 kg/m³, práctica habitual) |
| Sector | **4 bandejas = 100 plantines**, en 2 × 2: ocupa **≈ 0,3 m²** (≈ 0,56 × 0,54 m). Es la unidad mínima de actuación para riego y dosificación, y lo que abarca una foto |
| Macro-zona (MZ) | 100 sectores con **1 nodo testigo** cuya lectura vale para toda la MZ (HU-03 CA-02). 10 MZ = 100.000 plantines |
| Mediasombra | Malla de ~50 % de sombra con **dos estados**: **cerrada** (desplegada, sombra) o **abierta** (retraída, sol pleno). Se comanda **por MZ** |

> **Por qué 0,3 m² y no 1 m².** Con bandejas de 25 celdas, 100 plantines ocupan ~0,3 m²
> (≈ 330 plantines/m², densidad normal de vivero). Se mantuvieron los 100 plantines por sector para
> no cambiar la estructura 10 × 100 × 100. **Hay que corregir el "~1 m²" de la introducción.**

### 1.2 Hardware y comunicación

| Supuesto | Valor de referencia |
|---|---|
| Riego | **En campo:** 1 electroválvula + 1 microaspersor por sector. **En el prototipo** no hay electroválvula: riega una bomba de agua, y "abrir el riego" significa encenderla |
| Caudal del emisor | **30 L/h** nominal. Las reglas usan el **caudal calibrado** (§1.2.1) |
| Caudalímetro | Se incorpora en la versión final: mide el volumen real y detecta fallas hidráulicas (S-05). Hasta entonces, el volumen se estima por tiempo |
| Dosificación | Cada insumo tiene su **tanque de solución lista**, que prepara un operario, y su **bomba dosificadora**. No se mezcla con el agua de riego |
| Sectores regando a la vez por MZ | **10**, para no superar la capacidad de la línea |
| Telemetría | **El backend le pide la lectura al nodo** cada **4 h**, en horarios fijos: 02, 06, 10, 14, 18 y 22 h |
| Errores del ESP32 | Se publican en un **tópico propio de errores** (fin de carrera, caudalímetro). Ver S-05 y §13 |
| Batería | El nodo reporta siempre 100 %, así que **no hay regla de batería** |
| Luminosidad | **% de un LDR**. Se supone que **≥ 85 % equivale a sol directo** (hay que calibrarlo con un luxómetro) |
| Humedad de sustrato | % del sensor capacitivo. Se supone que **80 % está cerca de la saturación** |

> **Por qué 4 h alcanza.** Un sector consume entre 5 y 10 puntos de humedad en las 4 h más
> calurosas, así que entre dos lecturas no puede pasar de 45 % (riego) a menos de 35 % (crítico).
> **Chequeo en campo:** si R-02 se dispara más de una vez por semana en una MZ, el intervalo es
> largo para esa época y hay que bajarlo a 2 h.

#### 1.2.1 Cómo se mide lo que se aplica

- **Agua:** `volumen = caudal calibrado × tiempo`. El caudal se calibra **por aforo**: se abre el
  emisor 1 min sobre un recipiente graduado. Con caudalímetro, se usa el volumen que él mide.
- **Soluciones:** `mL = caudal calibrado de la bomba (mL/min) × tiempo`. Las bombas peristálticas son
  precisas (±5 %), pero hay que recalibrarlas cada mes y cada vez que se cambia la manguera.
- **Stock:** el backend descuenta cada aplicación del volumen que cargó el operario en el tanque (§8.2).

### 1.3 Diagnóstico de IA

- **Una pasada de captura por día**, a hora fija (fábrica **09:00**). A esa hora ya no hay rocío y la
  malla está abierta en todos los perfiles y etapas (§7), así que todas las fotos tienen una luz pareja.
- Clases: **Sano · Estrés solar · Clorosis · Plaga foliar · Daño fúngico**. Si la confianza es menor
  al umbral, la captura cuenta como **No concluyente** y no dispara nada.
- **Umbral de confianza: 70 %** (configurable). Se ajusta con el set de validación del modelo: se
  toma el menor umbral con el que la **precisión es ≥ 90 %**. *(HU-04 CA-02/03 dice 85 %: hay que
  actualizarla.)*
- Severidad: **leve · moderada · alta**.
- Un diagnóstico es **vigente** si sale de la **última captura del sector**, tiene **≤ 48 h** y su
  confianza supera el umbral. Si la pasada diaria no se completa, se emite `WARNING` "Pasada de
  captura incompleta" y los sectores siguen con su captura anterior mientras tenga ≤ 48 h.

### 1.4 Métrica derivada: déficit de presión de vapor (DPV)

El **backend** lo calcula con cada lectura y lo guarda como una métrica más, que el front muestra:

```
es  = 0,6108 · e^(17,27·T / (T + 237,3))      [kPa]   T = temperatura del aire (°C)
DPV = es · (1 − HR/100)                        [kPa]   HR = humedad ambiental (%)
```

| DPV | Lectura agronómica | Lo usan |
|---|---|---|
| < 0,3 kPa | Aire saturado: follaje mojado por horas, **riesgo fúngico** | F-F1, F-F2 |
| 0,5 – 1,2 kPa | Rango confortable | — |
| 1,2 – 2,0 kPa | Demanda alta | — |
| ≥ 2,0 kPa | **Calor seco**: favorece a los ácaros | F-P1 |

---

## 2. Principios del motor

1. **La seguridad manda.** Si una regla S bloquea, ninguna otra ejecuta. Orden de evaluación:
   **S** → **supervivencia** (R-02, M-02) → **sanidad** (F, N-02) → **rutina** (R-01, M-01, N-01).
2. **Se decide con la última lectura y la última captura.** Una regla que usa sensores sólo corre si
   la lectura del último ciclo es válida (S-02, S-03). Una regla por diagnóstico sólo corre con un
   diagnóstico **vigente** (§1.3).
3. **El clima sólo agrega protección, nunca la quita.** El pronóstico puede posponer un riego o
   cerrar la malla, pero nunca dejar al plantín con menos agua o menos sombra de la que pide su
   estado. Con déficit crítico se riega aunque se anuncie lluvia.
4. **En la mediasombra gana cerrar.** Si dos reglas piden estados distintos, se cierra. La sombra de
   más se corrige mañana; la hoja quemada no.
5. **La dosis nunca pasa la de la etiqueta.** Ante un problema más severo se actúa antes o sobre más
   sectores, nunca con más concentración.
6. **Riego y dosificación se cortan por tiempo calculado, no por sensor.** La lectura llega cada 4 h
   y un riego dura minutos. El backend calcula volumen y tiempo antes de abrir, la orden viaja con su
   duración y **el ESP32 corta solo** al cumplirla, aunque se pierda la orden de cierre.
7. **Cuándo evalúa el motor:** con cada lectura (cada 4 h), con cada captura (1 por día) y en los
   cambios de franja horaria (mediasombra y ventanas de aplicación). Las **lecturas de
   verificación** (E-01) sirven para evaluar, no disparan reglas.
8. **Una alerta por evento.** Una regla alerta cuando se activa, no en cada evaluación mientras siga
   activa.
9. **Horario local** (America/Argentina/Buenos_Aires) en todas las ventanas.

---

## 3. Rangos de referencia por métrica

| Métrica | Crítico bajo | Alerta baja | **Óptimo** | Alerta alta | Crítico alto | La usan |
|---|---|---|---|---|---|---|
| Humedad de sustrato (%) | < 35 | 35 – 45 | **50 – 70** | 75 – 80 | > 80 | R-01…R-04, P-D5, F-C1, F-F1, F-F2, F-S1, E-01 |
| Humedad ambiental (%) | < 35 | 35 – 50 | **60 – 85** | 85 – 90 | > 90 en 2 lecturas seguidas | DPV |
| Temperatura del aire (°C) | ≤ 2 | 2 – 8 | **18 – 30** | 32 – 35 | ≥ 35 | M-02, P-D3, F-F2, F-S1 |
| Luminosidad (% LDR) | — | — | según horario | ≥ 85 con ≥ 32 °C | — | M-02, F-S1 |
| Temperatura de sustrato (°C) | ≤ 8 | 8 – 15 | **18 – 28** | 30 – 32 | ≥ 32 | Sólo alerta |
| CE (dS/m) | < 0,3 | 0,3 – 0,5 | **0,6 – 1,2** | 1,5 – 2,0 | > 2,0 | N-02, F-C1 |
| pH | < 4,5 | 4,5 – 5,0 | **5,0 – 6,0** | 6,0 – 6,5 | > 6,5 | F-C1 |
| N (mg/kg) | < 20 | 20 – 40 | **40 – 100** | 100 – 150 | > 150 | Sólo alerta |
| P (mg/kg) | < 5 | 5 – 10 | **10 – 30** | 30 – 50 | > 50 | Sólo alerta |
| K (mg/kg) | < 30 | 30 – 60 | **60 – 150** | 150 – 250 | > 250 | Sólo alerta |

> N, P y K **se muestran y colorean pero no disparan acciones**. Las sondas NPK de bajo costo los
> estiman a partir de la conductividad, y ningún actuador del sistema los corrige de forma directa.
>
> La yerba mate es **acidófila** (suelo nativo de Misiones: pH 4,5 – 5,5). Con pH > 6,5 se bloquea
> la absorción de hierro y manganeso, y aparece clorosis aunque haya nutrientes.

---

## 4. Seguridad (S)

Se evalúan **antes que cualquier otra regla**. Si bloquean, bloquean.

**S-01 · Bloqueo manual**
- **Cuando** un Operario activó el bloqueo sobre el sector o sobre su macro-zona (HU-19).
- **Entonces** ninguna regla autónoma ejecuta sobre ese alcance: riego cerrado y bombas detenidas.
  Si el bloqueo es de la MZ, además la mediasombra queda quieta.
- **Alerta**: `INFO` al activar y al reanudar. Al reanudar se evalúa con la telemetría actual, sin
  acciones retroactivas (HU-19 CA-04).
- **Implementación**: el front todavía no tiene esta función (§13).

**S-02 · Nodo sin respuesta**
- **Cuando** el backend pide la lectura y el nodo testigo no responde en **60 s**.
- **Entonces** reintenta **enseguida**, hasta **2 veces** con **1 min** de espera, sin esperar al
  próximo ciclo de 4 h. Mientras no haya lectura válida, en la MZ no corren las reglas que dependen
  de sensores. El horario de la mediasombra (M-01) sigue, y M-02 sigue con el pronóstico.
  - **1.er y 2.º intento fallido** → `WARNING` "Nodo sin respuesta (1/3)" y "(2/3)".
  - **3.er intento fallido** → `CRITICAL` "Nodo fuera de servicio". Se ordena el **estado seguro** en
    la MZ: riego cerrado, bombas detenidas y **mediasombra cerrada**. El backend sigue intentando
    cada **15 min**.
  - **Cuando vuelve a responder** → `INFO`, y se evalúa con la lectura nueva, sin acciones retroactivas.
- **Por qué**: sin la humedad no se puede regar con criterio. Cerrar la malla baja la transpiración
  hasta que alguien revise el nodo.

**S-03 · Lectura inválida**
- **Cuando** un valor está fuera del rango físico posible: humedad < 0 o > 100 %, temperatura
  < −10 o > 60 °C, pH < 3 o > 9, CE < 0 o > 10 dS/m, luz < 0 o > 100 %.
- **Entonces** pide la lectura de nuevo **enseguida** (hasta 2 reintentos). Si algún reintento da un
  valor válido, se usa ese y no se alerta. Si los 3 son inválidos, **esa métrica** se descarta en el
  ciclo: no corren las reglas que la usan, pero las demás sí.
- **Alerta**: `WARNING` "Lectura inválida en [métrica]". Si la misma métrica vuelve a fallar en el
  ciclo siguiente → `CRITICAL` "Sensor de [métrica] con falla: revisar".

**S-04 · Hardware incompleto**
- **Cuando** al sector (o a su MZ, en el caso de la mediasombra) le falta mapear algún actuador
  requerido (HU-18 CA-04).
- **Entonces** ninguna regla autónoma ejecuta sobre ese alcance.
- **Alerta**: ninguna nueva (ya se avisó al configurar).

**S-05 · Falla reportada por el ESP32**

El ESP32 detecta estas fallas por su cuenta, corta el actuador en el momento y publica el error en
su tópico. El motor reacciona así:

| Error | Lo detecta | Entonces | Alerta |
|---|---|---|---|
| **Sin caudal**: riego encendido y caudalímetro en 0 a los **10 s** | Caudalímetro | Se marca el sector "Falla hidráulica" y queda fuera del riego y la dosificación automáticos hasta que se registre la reparación (HU-21 CA-04/05) | `CRITICAL`. Si la humedad de la MZ está debajo de 45 %, el texto pide **riego manual** del sector |
| **Caudal excesivo**: > **150 %** del nominal durante **30 s** (rotura o emisor desprendido) | Caudalímetro | Igual que la anterior | `CRITICAL` |
| **Falla de mediasombra**: el fin de carrera no se activó en el tiempo estipulado (HU-08 CA-03) | Fin de carrera | Se corta el motor, se marca "Falla mecánica en mediasombra" y la malla no se vuelve a mover hasta registrar la reparación | `CRITICAL`. Si el fin de carrera de **cerrada** no está activo (la malla quedó abierta, del todo o en parte), el texto pide **cubrir a mano** |

> Hasta que se instale el caudalímetro, las fallas hidráulicas no se detectan. Sólo queda E-01 como
> indicio (el riego no sube la humedad).

**S-06 · Acción sin efectividad**
- **Cuando** la evaluación de §9 catalogó como "Sin efectividad" la última acción de ese tipo.
- **Entonces** se bloquea la repetición autónoma de esa acción hasta que alguien registre la
  revisión (HU-12 CA-03):

  | Acción evaluada | Alcance del bloqueo | Excepción |
  |---|---|---|
  | Riego (E-01) | Toda la MZ (el sensor es de la MZ) | **R-02 sigue**, con un máximo de 1 riego cada 12 h por sector |
  | Fertilizante correctivo (E-02) | El sector | El plan de nutrición (N-01) sigue |
  | Fitosanitario (E-03) | El sector | — |

- **No aplica a la mediasombra**: la malla siempre tiene que poder cerrarse.
- **Alerta**: `CRITICAL` pidiendo revisión física.
- **Por qué la excepción de R-02**: bloquear todo el riego puede matar el plantín antes de que llegue
  la revisión. El tope de 12 h evita que un sensor roto que marca "seco" inunde la MZ.

---

## 5. Riego (R)

**Fórmula de volumen** (la usan todas las reglas de riego):

```
V (L por sector) = (65 − humedad actual) × 0,2          máximo 6 L
tiempo           = V / caudal calibrado                  (con 30 L/h: 2 min por litro)
```

> **De dónde sale el 0,2.** 100 celdas × 100 cm³ = 10 L de sustrato, así que subir 10 puntos retiene
> ~1 L. Del agua que tira el emisor, más o menos la mitad termina dentro de las celdas; el resto cae
> entre celdas, fuera de las bandejas o drena. Por eso hacen falta ~2 L cada 10 puntos.
>
> Ejemplo: humedad 45 % → V = 20 × 0,2 = **4 L** → **8 min** de riego.

**Cómo se riega una MZ:** sector por sector, **en orden de numeración**, de a **10 sectores a la
vez**. No hay prioridades entre sectores.

**R-01 · Riego por déficit hídrico**
- **Cuando** la humedad de sustrato de la última lectura es menor a **45 %**
  **y** la hora está dentro de la **ventana de riego (06:00 a 18:00)**
  **y** no aplican R-03 ni R-04.
- **Entonces** regar cada sector de la MZ con el volumen de la fórmula, salvo los que estén en pausa
  por R-06.
- **Alerta**: `INFO`. Al terminar se registra sector, duración, volumen y condición (HU-06 CA-05).
- **Por qué**: 45 % deja margen antes del punto crítico. Regar hasta 65 % y no hasta saturación
  evita la pudrición de raíz que pide evitar HU-06.

**R-02 · Déficit hídrico crítico**
- **Cuando** la humedad de sustrato de la última lectura es menor a **35 %**.
- **Entonces** regar **a cualquier hora** con **6 L por sector** (el máximo), **aunque se anuncie
  lluvia** y aunque el sector esté en pausa por R-06.
- **Alerta**: `CRITICAL` "Déficit hídrico crítico".
- **Por qué**: en una celda de 100 cm³ la reserva de agua se agota en horas, y la marchitez
  permanente llega antes que la lluvia anunciada.

**R-03 · Posponer por lluvia**
- **Cuando** se cumplen las condiciones de R-01
  **y** el pronóstico da probabilidad de lluvia ≥ **70 %** con ≥ **5 mm** en las próximas **4 h**
  (o sea, antes de la próxima lectura).
- **Entonces** no regar en este ciclo. La regla no hace nada más: en la próxima lectura, si no llovió,
  la regla que corresponda (R-01 o R-02) decide sola.
- **Alerta**: `INFO` "Riego pospuesto por pronóstico de lluvia" (HU-06 CA-02).

**R-04 · Sustrato saturado**
- **Cuando** la humedad de sustrato es ≥ **75 %**.
- **Entonces** se bloquea todo riego autónomo en la MZ.
- **Alerta**: ninguna hasta **80 %**. Con ≥ 80 % → `WARNING` "Sustrato saturado, riesgo de asfixia
  radicular y hongos".
- Esta regla sólo mira el riego. Las condiciones de las aplicaciones están en §8.3.

**R-05 · Riego fuera de ventana**
- **Cuando** es **después de las 18:00 y antes de las 06:00**.
- **Entonces** sólo puede regar R-02. R-01 espera a la lectura de las 06:00.
- **Por qué**: con el follaje mojado toda la noche se dan condiciones ideales para *Cylindrocladium*,
  *Colletotrichum* y el mal de los almácigos. La lectura de las 18:00 todavía entra en la ventana: en
  verano, que es cuando hace falta, quedan casi 2 h de luz para que se seque la hoja.

**R-06 · Pausa después de una aplicación**
- **Cuando** al sector se le aplicó fitosanitario o fertilizante hace menos de **6 h**.
- **Entonces** R-01 no riega ese sector, que espera a la próxima lectura. R-02 sí puede regarlo.
- **Por qué**: el agua lava el producto de la hoja antes de que haga efecto. Las aplicaciones de la
  tarde (17–19 h, §8.3) casi no chocan con esta pausa, porque después de las 18:00 no hay riego normal.

---

## 6. Mediasombra (M)

**Vocabulario:** **cerrada** = malla desplegada (sombra); **abierta** = malla retraída (sol pleno).
No hay posiciones intermedias: la exposición se regula con **cuántas horas y en qué franja** está
abierta la malla. Se comanda **por MZ**, sin ajustes por sector. Los diagnósticos de los sectores
influyen en la decisión de toda la MZ (F-S2). **De noche siempre está cerrada.** Si dos reglas se
contradicen, **gana cerrar** (principio 4).

> **La franja más dura es 12:00–15:00.** En Misiones el mediodía solar cae cerca de las 12:40 (la
> hora oficial es UTC−3 y la longitud es ~55° O), y el pico de temperatura llega hacia las 15:00.
> Por eso es lo último que se destapa en el plan.

**M-01 · Horario base**
- **Cuando** empieza una franja nueva en el horario de la MZ, que depende de su estado:
  - **sin plan de rustificación** → **perfil de crecimiento** (tabla de abajo);
  - **con plan activo o finalizado** → horario de la etapa del día (§7).
- **Entonces** llevar la malla al estado que indica el horario.
- **Alerta**: ninguna. Se registra el estado y el factor "horario" o "plan" (HU-08 CA-04).

**Perfil de crecimiento (por defecto)**

| Franja | Estado |
|---|---|
| 08:00 – 10:00 | **Abierta** (sol suave de la mañana) |
| El resto del día y toda la noche | **Cerrada** |

> **Por qué**: en la etapa de crecimiento el plantín se cría bajo 50–80 % de sombra. Dos horas de
> sol temprano suman luz sin calor ni estrés, y lo van acostumbrando.

**M-02 · Protección por calor y sol extremo** *(une el pico de luminosidad, el calor extremo y el UV extremo)*
- **Cuando** la hora está entre **11:00 y 16:00** y se cumple **alguna** de estas:
  - **calor extremo**: temperatura ≥ **35 °C**;
  - **sol directo con calor**: luminosidad ≥ **85 %** y temperatura ≥ **32 °C**;
  - **UV extremo con calor**: índice UV pronosticado del día ≥ **11** y temperatura ≥ **32 °C**.

  *Temperatura* es la mayor entre la última lectura y el pronóstico para esa hora (la lectura llega
  cada 4 h y el pronóstico cubre el hueco).
- **Entonces** malla **cerrada hasta las 16:00**, en cualquier perfil o etapa. No se reabre antes
  aunque la condición desaparezca: con la malla cerrada el LDR deja de ver el sol, y la regla oscilaría.
- **Alerta**: `INFO` (factor "clima", HU-08 CA-01/04). Con ≥ 35 °C → `WARNING`; con ≥ 38 °C →
  `CRITICAL` "Calor extremo".
- **Por qué**: en rustificación el sol pleno **es** el objetivo, así que el sol solo no alcanza para
  cerrar. Lo peligroso es el sol **con** calor: a partir de 35 °C cae la fotosíntesis de la yerba,
  y la celda negra puede pasar los 40 °C en la zona de raíces. Se pierde una tarde de sol, no el
  plantín. El UV es complementario: avisa que hay sol fuerte cuando la lectura del LDR es vieja.

---

## 7. Plan de rustificación

El plan **sólo regula la mediasombra**: no toca el riego ni la nutrición. Es **fijo**: avanza por
calendario, sin adelantos, pausas ni retrocesos automáticos. Las protecciones (M-02, F-S2) pueden
cerrar la malla algunas horas, pero no mueven el calendario.

**Ciclo de vida (por MZ)**

| Acción | Quién | Qué pasa |
|---|---|---|
| **Iniciar** | Ingeniero Agrónomo | El día de inicio es el día 1. El horario de la etapa 1 rige desde la próxima franja |
| **Cancelar** | Ingeniero Agrónomo | La MZ vuelve **en el momento** al perfil de crecimiento. `INFO` |
| **Fin** (automático) | — | Pasado el día 45, la MZ **sigue con el horario de la etapa 4** hasta que se resetee. `INFO` "Plan finalizado: plantines listos para trasplante" |
| **Resetear** | Ingeniero Agrónomo | La MZ vuelve al perfil de crecimiento (por ejemplo, cuando entra un lote nuevo) |

> **Cuándo iniciarlo** (lo decide el agrónomo; el sistema no lo sugiere): de referencia, entre los
> **6 y 8 meses** desde el repique, 20 a 45 días antes del despacho. En la zona suele hacerse en
> **febrero–marzo**, para plantar en otoño–invierno (FCF-UNaM).

**Etapas** (45 días de fábrica)

| Etapa | Días | Abierta (sol) | Cerrada (sombra) | Horas de sol |
|---|---|---|---|---|
| Perfil de crecimiento (sin plan) | — | 08–10 | resto | 2 h |
| **1 · Mañana** | 1 – 10 | 07–11 | 11–07 | 4 h |
| **2 · Mañana y tarde** | 11 – 20 | 07–11 y 16–19 | 11–16 y noche | 7 h |
| **3 · Protección central** | 21 – 32 | 07–12 y 15–19 | 12–15 y noche | 9 h |
| **4 · Sol pleno** | 33 – 45 | 07–19 | noche | 12 h |
| Fin | > 45 | igual que la etapa 4, hasta resetear | | 12 h |

```
Hora de inicio →   07 08 09 10 11 12 13 14 15 16 17 18
Crecimiento        ░░ ██ ██ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░    2 h
Etapa 1 (1–10)     ██ ██ ██ ██ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░    4 h
Etapa 2 (11–20)    ██ ██ ██ ██ ░░ ░░ ░░ ░░ ░░ ██ ██ ██    7 h
Etapa 3 (21–32)    ██ ██ ██ ██ ██ ░░ ░░ ░░ ██ ██ ██ ██    9 h
Etapa 4 (33–45)    ██ ██ ██ ██ ██ ██ ██ ██ ██ ██ ██ ██   12 h

██ abierta (sol)   ░░ cerrada (sombra)   Entre las 19:00 y las 07:00, siempre cerrada.
```

> **Por qué así.** La luz se suma de lo más suave a lo más duro: primero la mañana (fresca y húmeda),
> después la tarde y al final el centro del día. La franja 12–15 es lo último que se destapa. Como
> la malla sólo abre o cierra, la dosis de sol se regula con horas y no con porcentaje.

---

## 8. Dosificación (N y F)

### 8.1 Insumos

Sólo hay **dos insumos**. Un operario los prepara ya diluidos y los carga en su tanque, así que la
bomba aplica la solución tal cual, sin mezclarla con el riego.

| Insumo | Uso | Forma | **Dosis por sector** | Intervalo mínimo en el sector |
|---|---|---|---|---|
| **Fertilizante foliar NPK + micronutrientes** | Plan de nutrición y clorosis | Solución lista, a la concentración de la etiqueta | **100 mL** (≈ 1 mL por plantín) | 5 días |
| **Fitosanitario genérico** (fungicida + insecticida/acaricida) | Daño fúngico y plaga foliar | Caldo listo, a la dosis del marbete | **100 mL** | 7 días |

> **Productos.** Sólo se cargan productos con **registro SENASA** vigente para el uso, y la etiqueta
> **manda** sobre cualquier número de esta tabla. Si el marbete dice que el caldo pierde eficacia
> pocas horas después de preparado, el operario lo prepara cuando se anuncia una aplicación (las
> alertas F avisan antes de la ventana).
>
> **Por qué foliar.** El sustrato ya trae fertilizante de liberación lenta. Lo que agrega el sistema
> es el complemento foliar que usan los viveros tecnificados, y por eso alcanza con poco volumen.

### 8.2 Cómo se ejecuta una aplicación

1. Verificar las precondiciones (§8.3).
2. Encender la bomba del insumo durante `t = dosis / caudal calibrado`. La orden lleva su duración y
   el ESP32 corta solo (principio 6).
3. Registrar sector, insumo, volumen, hora y regla que la disparó.
4. **Fitosanitario:** marcar el sector con **reingreso restringido 24 h** (o lo que diga el marbete)
   y aplicar la pausa de riego (R-06).
5. **Stock:** descontar el volumen del tanque. Si queda menos del **20 %** → `WARNING` "Reponer
   [insumo]". Con el tanque vacío no se aplica, y la alerta pasa a `CRITICAL`.

### 8.3 Precondiciones de toda aplicación

Además de las reglas S, una aplicación sólo ocurre si se cumplen todas:

- **P-D1** (sólo las que dispara un diagnóstico) el diagnóstico es **vigente** (§1.3).
- **P-D2** en el sector se cumplió el **intervalo mínimo** del insumo **y** no hubo otra aplicación
  ese día. Si ese día corresponden fertilizante y fitosanitario, va primero el fitosanitario y el
  fertilizante pasa al día siguiente.
  *Por qué:* como cada insumo tiene su tanque y su bomba, ya no hay incompatibilidades dentro de la
  línea. Lo que queda es el riesgo sobre la hoja: dos productos juntos pueden causar fitotoxicidad,
  y el segundo lava al primero.
- **P-D3** la hora está en una **ventana de aplicación**, **07:00–10:00** o **17:00–19:00**, y la
  temperatura (última lectura o pronóstico para esa hora) es ≤ **30 °C**. Si no se cumple, no se hace
  nada: la regla se vuelve a evaluar en la próxima ventana.
  *Por qué:* se aplica temprano a la mañana o al final de la tarde, con menos sol y calor (ideal por
  debajo de 25 °C). Con más de 30 °C el producto se evapora antes de actuar, y con sol fuerte quema
  la hoja. La ventana de la tarde además aprovecha que de noche no hay riego (R-06).
- **P-D4** el pronóstico no da ≥ **5 mm** de lluvia en las próximas **6 h**.
- **P-D5** la humedad de sustrato es ≥ **45 %**, o el sector se regó después de la última lectura.
  *Por qué:* un plantín con falta de agua absorbe peor y sufre más la fitotoxicidad. Primero se riega
  y después se aplica. No hace falta un tope por arriba porque la aplicación es foliar.

### 8.4 Nutrición (N)

**Plan de nutrición:** fertilizante foliar **cada 7 días** en todos los sectores de la MZ, **sin
depender de los valores que se miden**. El intervalo se configura por MZ.

> Durante la rustificación conviene dar menos nitrógeno para no estimular brote tierno. El plan de
> rustificación no lo cambia solo: si el agrónomo quiere espaciar la fertilización, alarga el
> intervalo de la MZ (por ejemplo, a 14 días) al iniciar el plan.

**N-01 · Aplicación del plan de nutrición**
- **Cuando** en la MZ pasaron los días del intervalo desde la última aplicación del plan.
- **Entonces** aplicar **100 mL de fertilizante** en cada sector de la MZ en la próxima ventana apta
  (§8.3). Si un sector no cumple sus precondiciones, se reintenta en la ventana siguiente.
- **Alerta**: `INFO`.
- **Por qué**: la celda tiene poco volumen de sustrato y se lava con cada riego. El fertilizante de
  base se va agotando, y sin aporte regular la clorosis aparece sola.

**N-02 · CE baja**
- **Cuando** la CE media de las últimas 24 h (6 lecturas) es menor a **0,5 dS/m**
  **y** no hubo fertilización en la MZ en los últimos **5 días**.
- **Entonces** adelantar N-01 a la próxima ventana apta.
- **Alerta**: `INFO` "CE baja: la fertilización de base del sustrato podría estar agotada".

### 8.5 Reglas por diagnóstico (F) — 🔶 Sin revisar

> Esta sección se adaptó a los cambios anteriores: hay un solo fitosanitario, un solo fertilizante,
> captura diaria, mediasombra por MZ y ya no existen R-07, R-09, M-06 ni el quelato. Falta la
> revisión del equipo.

#### Clorosis (F-C)

La clorosis es un **síntoma** con varias causas posibles. Antes de fertilizar se descartan las que
el fertilizante no corrige.

**F-C1 · Clorosis con causa no nutricional**
- **Cuando** hay diagnóstico Clorosis vigente **y** se cumple **alguna** de estas:
  - humedad de sustrato media de las últimas 48 h > **80 %** → probable **asfixia radicular**;
  - CE ≥ **2,0 dS/m** → probable **salinidad** (lavar con riego abundante, a mano);
  - pH ≥ **6,5** → probable **clorosis férrica** por pH alto (corregir a mano).
- **Entonces** no se aplica fertilizante correctivo en el sector.
- **Alerta**: `WARNING` con la causa probable.

**F-C2 · Clorosis nutricional**
- **Cuando** hay diagnóstico Clorosis vigente y no hay ninguna causa de F-C1. **Durante la
  rustificación**, sólo cuenta la severidad moderada o alta, porque un amarillamiento leve es
  esperable con el cambio de luz.
- **Entonces** aplicar **fertilizante (100 mL)** en el sector en la próxima ventana apta. Se repite
  mientras siga el diagnóstico, respetando el intervalo mínimo, y se evalúa con E-02.
- **Alerta**: `WARNING`. Con severidad alta → `CRITICAL`.

#### Daño fúngico (F-F)

**F-F1 · Daño fúngico**
- **Cuando** hay diagnóstico Daño fúngico vigente **y** se cumple **alguna** de estas:
  - severidad **moderada o alta**;
  - ambiente húmedo: humedad de sustrato > **80 %**, o DPV < **0,3 kPa** en 2 lecturas seguidas de
    las últimas 24 h;
  - el diagnóstico aparece en **3 capturas diarias seguidas**.
- **Entonces** aplicar **fitosanitario (100 mL)** en el sector en la próxima ventana apta. Se evalúa
  con E-03.
- Si no se cumple ninguna (hongo leve, recién aparecido y con ambiente seco), no se aplica y se
  espera a las próximas capturas.
- **Alerta**: `WARNING` "Alta probabilidad de hongo". Con severidad alta → `CRITICAL`.
- **Por qué**: con el ambiente seco un hongo leve avanza despacio, y conviene confirmarlo antes de
  aplicar químicos.

**F-F2 · Condición predisponente (sin diagnóstico)**
- **Cuando** el DPV es < **0,3 kPa** en 2 lecturas seguidas (≈ HR ≥ 90 % durante 4 a 8 h)
  **y** la temperatura está entre **18 y 28 °C**
  **y** la humedad de sustrato es ≥ **75 %**.
- **Entonces** no se aplica nada.
- **Alerta**: `WARNING` "Condiciones favorables a enfermedades fúngicas".
- **Por qué**: 18–28 °C con la hoja mojada es la ventana de infección de *Cylindrocladium*,
  *Colletotrichum* y los hongos del mal de los almácigos. Con la captura diaria el primer síntoma se
  ve al día siguiente.

#### Plaga foliar (F-P)

**F-P1 · Plaga foliar**
- **Cuando** hay diagnóstico Plaga foliar vigente **y** se cumple **alguna** de estas:
  - severidad **moderada o alta**;
  - clima de ácaros: DPV ≥ **2 kPa** en alguna lectura de las últimas 72 h (calor seco);
  - el diagnóstico aparece en **3 capturas diarias seguidas**.
- **Entonces** aplicar **fitosanitario (100 mL)** en el sector en la próxima ventana apta. Se evalúa
  con E-03.
- Si no se cumple ninguna, no se aplica (`INFO`) y se espera a las próximas capturas.
- **Alerta**: `WARNING`. Con severidad alta → `CRITICAL`.
- **Por qué**: con calor seco el ácaro rojo completa su ciclo en 7 a 10 días. Esperar la
  confirmación es darle una generación entera.

#### Estrés solar (F-S)

No usa químicos: se corrige con agua y sombra.

**F-S1 · Estrés solar con radiación alta (sector)**
- **Cuando** hay diagnóstico Estrés solar vigente
  **y** en las últimas 48 h la luz fue ≥ **80 %** en alguna lectura entre las 10 y las 16 h, o la
  temperatura llegó a ≥ **32 °C**.
- **Entonces**, si la humedad es menor a **55 %**, se hace un riego de reposición en el sector (con
  la fórmula de volumen), dentro de la ventana de riego y respetando R-04 y R-06.
- **Alerta**: `WARNING`. Con severidad alta → `CRITICAL`.

**F-S2 · Estrés solar extendido (MZ)**
- **Cuando** ≥ **10 %** de los sectores de la MZ cumplen F-S1 en la última captura.
- **Entonces** la mediasombra de la MZ queda **cerrada de 11:00 a 16:00 durante 3 días**, en
  cualquier perfil o etapa. El calendario del plan sigue corriendo.
- **Alerta**: `WARNING` "Estrés solar en el N % de la MZ: evaluar cancelar el plan de rustificación".
- **Por qué**: la malla es de toda la MZ, así que la decide el conjunto. Un sector aislado no mueve
  la malla de otros 99.

**F-S3 · Estrés solar sin radiación alta**
- **Cuando** hay diagnóstico Estrés solar vigente y en las últimas 48 h la luz fue < **60 %** y la
  temperatura < **30 °C**.
- **Entonces** ninguna acción. Si falta agua, ya riega R-01.
- **Alerta**: `WARNING` "Síntoma no explicado por radiación: posible estrés hídrico o problema de
  raíz. Inspeccionar".
- **Por qué**: en la foto, la quemadura de sol y la marchitez por falta de agua se ven parecidas. Si
  no hubo sol fuerte, cerrar la malla no resuelve nada.

#### Reglas transversales

**F-04 · Foco**
- **Cuando** ≥ **3 sectores** de la misma MZ tienen el **mismo** diagnóstico biótico vigente (Daño
  fúngico o Plaga foliar) en **72 h**.
- **Entonces** aplicar fitosanitario también a los **8 sectores lindantes** de cada afectado, aunque
  estén sanos. Se respeta §8.3, salvo P-D1.
- **Alerta**: `CRITICAL` "Foco de [diagnóstico] en MZ-N".
- **Por qué**: con microaspersión, las esporas y los ácaros viajan con la gota y el viento al sector
  de al lado. Tratar el anillo frena la propagación con mucho menos producto que tratar la MZ entera.

**F-05 · Sano después de un tratamiento**
- **Cuando** un sector tratado (con fertilizante correctivo o fitosanitario) vuelve a tener un
  diagnóstico Sano vigente.
- **Entonces** se cierra el seguimiento como "Efectiva".
- **Alerta**: `INFO`.

**F-06 · Tope de aplicaciones**
- **Cuando** el sector ya recibió **3 fitosanitarios en 30 días**.
- **Entonces** la siguiente aplicación queda bloqueada hasta que alguien registre la revisión.
- **Alerta**: `WARNING` "Tope de aplicaciones: hay un solo producto y no se puede rotar el principio
  activo (riesgo de resistencia)".

---

## 9. Evaluación de efectividad (E)

Aplica HU-12: pasada la latencia, se compara la métrica original con la nueva. Si no alcanza el
delta, la acción queda "Sin efectividad" y entra S-06. **Sólo se evalúan las acciones que tienen una
métrica clara y cuyo bloqueo no pone en riesgo al plantín.**

| Regla | Acción evaluada | Latencia | Métrica | **Delta para "Efectiva"** |
|---|---|---|---|---|
| E-01 | Riego (R-01, R-02) | **Lectura de verificación** 30 min después del riego del sector del nodo testigo | Humedad de sustrato de la MZ | **≥ +8 puntos** |
| E-02 | Fertilizante correctivo (F-C2) | 14 días desde la primera aplicación | Diagnóstico del sector | **Sano, o severidad ≥ 1 nivel menor** |
| E-03 | Fitosanitario (F-F1, F-P1, F-04) | 7 días | Diagnóstico del sector y de sus vecinos | **La severidad no aumenta y no aparece ningún vecino nuevo con el mismo diagnóstico** |

> **E-01.** R-01 riega desde menos de 45 % hasta 65 %, así que la suba esperada es de al menos
> 20 puntos. Pedir +8 tolera el drenaje y el error del sensor, y aun así detecta el emisor tapado,
> el sensor fuera de la celda o la bomba que no bombea. La lectura de verificación la pide el
> backend fuera del ciclo de 4 h y no dispara reglas (principio 7).
>
> **E-03 pide "no aumenta" en lugar de "Sano"** porque el daño de un hongo o un ácaro queda visible
> en la hoja aunque el problema ya esté controlado. Lo que se exige es que no avance.
>
> **No se evalúan:** la **mediasombra** (nunca se bloquea) ni el **plan de nutrición** ni **N-02**:
> son aplicaciones foliares preventivas y no hay una métrica que muestre su efecto en pocos días.

---

## 10. Operación sin internet (O) — 🔶 Sin revisar

HU-13 pide que, sin conexión, el nodo decida con los umbrales cacheados y "asuma un entorno
conservador". Lo que falta sin internet es el **pronóstico**, y el supuesto seguro depende de la
acción:

| Regla | Qué falta | Supuesto seguro |
|---|---|---|
| **O-01** Riego | Pronóstico de lluvia | **Asumir que no llueve**: R-03 se desactiva y R-01 riega. Un riego de más se drena; uno de menos mata. |
| **O-02** Mediasombra | Pronóstico de temperatura y UV | M-02 usa sólo la última lectura (temperatura y luz). El horario (M-01) sigue igual. |
| **O-03** Aplicaciones | Pronóstico de lluvia y temperatura | P-D4 se desactiva y P-D3 usa sólo la lectura. Demorar un tratamiento cuesta más que perder una aplicación por lluvia. |
| **O-04** Plan de rustificación | Nada (depende del reloj) | Sigue normalmente. |
| **O-05** Sincronización | — | Al volver la conexión se suben todos los eventos con su timestamp original y sin duplicados (HU-13 CA-03). |

---

## 11. Parámetros configurables (valores de fábrica para HU-15)

El **rango permitido** es el que usa HU-15 CA-03 para rechazar valores fuera de lo fisiológico.

### Telemetría y diagnóstico

| Parámetro | Fábrica | Rango permitido |
|---|---|---|
| Intervalo de lectura | 4 h (02, 06, 10, 14, 18, 22 h) | 1 – 6 h |
| Tiempo de espera de respuesta del nodo | 60 s | 10 – 300 s |
| Reintentos (sin respuesta o lectura inválida) | 2, cada 1 min | 1 – 5 / 1 – 10 min |
| Reintento con el nodo fuera de servicio | cada 15 min | 5 – 60 min |
| Lectura de verificación después del riego | 30 min | 15 – 60 min |
| Hora de la pasada de captura | 09:00 | — |
| Antigüedad máxima de la captura | 48 h | 24 – 72 h |
| Confianza mínima del diagnóstico | 70 % | 50 – 95 % |

### Riego

| Parámetro | Fábrica | Rango permitido |
|---|---|---|
| Umbral de riego (humedad de sustrato) | 45 % | 35 – 60 % |
| Humedad objetivo | 65 % | 55 – 75 % |
| Umbral crítico | 35 % | 25 – 40 % |
| Humedad de bloqueo por saturación (alerta) | 75 % (80 %) | 65 – 85 % |
| Litros por punto de déficit | 0,2 L | 0,1 – 0,5 L |
| Volumen máximo por evento | 6 L | 3 – 10 L |
| Sectores a la vez por MZ | 10 | 1 – 100 (según la línea) |
| Ventana de riego normal | 06:00 – 18:00 | — |
| Lluvia para posponer: probabilidad / milímetros / ventana | 70 % / 5 mm / 4 h | 50 – 95 % / 2 – 20 mm / 2 – 12 h |
| Pausa de riego después de una aplicación | 6 h | 2 – 24 h |

### Mediasombra

| Parámetro | Fábrica | Rango permitido |
|---|---|---|
| Perfil de crecimiento (horario abierta) | 08:00 – 10:00 | — |
| Horarios y días de las etapas del plan | tabla de §7 | — |
| Duración del plan | 45 días | 20 – 60 días |
| Ventana de protección (M-02) | 11:00 – 16:00 | — |
| Temperatura de calor extremo | 35 °C | 32 – 40 °C |
| Temperatura que, con sol o UV, cierra la malla | 32 °C | 28 – 35 °C |
| Temperatura crítica (alerta) | 38 °C | 35 – 42 °C |
| Luz de sol directo (LDR) | 85 % | 60 – 100 % |
| Índice UV extremo | 11 | 8 – 13 |
| Estrés solar extendido: % de sectores / días de protección | 10 % / 3 días | 5 – 50 % / 1 – 7 días |

### Dosificación

| Parámetro | Fábrica | Rango permitido |
|---|---|---|
| Dosis de fertilizante por sector | 100 mL | 20 – 500 mL |
| Dosis de fitosanitario por sector | 100 mL | 20 – 500 mL |
| Intervalo mínimo del fertilizante / del fitosanitario | 5 / 7 días | 3 – 14 / 5 – 21 días |
| Intervalo del plan de nutrición (por MZ) | 7 días | 3 – 30 días |
| Ventanas de aplicación | 07:00 – 10:00 y 17:00 – 19:00 | — |
| Temperatura máxima para aplicar | 30 °C | 25 – 32 °C |
| Humedad de sustrato mínima para aplicar | 45 % | 35 – 60 % |
| Lluvia que impide aplicar | 5 mm en 6 h | 2 – 20 mm / 2 – 12 h |
| Reingreso después del fitosanitario | 24 h (o marbete) | — |
| Sectores para declarar foco | 3 en 72 h | 2 – 10 |
| Tope de fitosanitarios por sector | 3 en 30 días | 2 – 5 |
| Aviso de stock bajo en el tanque | 20 % | 10 – 50 % |

### Seguimiento

| Parámetro | Fábrica | Rango permitido |
|---|---|---|
| Caudal de rotura (lo aplica el ESP32) | 150 % del nominal por 30 s | 120 – 200 % |
| Tiempo sin caudal para declarar falla (ESP32) | 10 s | 5 – 60 s |
| Latencias y deltas de efectividad | ver §9 | — |
| Riego exceptuado del bloqueo (R-02) | 1 cada 12 h | 6 – 24 h |

---

## 12. Pendiente de validar con el vivero

1. **Geometría real del sector** (bandejas de 25 × 100 cm³, ~0,3 m²) y alcance del emisor: que no
   moje los sectores vecinos. Esto cambia el 0,2 L por punto de §5.
2. **Calibración del sensor de humedad** contra el método gravimétrico en este sustrato: a qué % del
   sensor corresponden la capacidad de contenedor y la saturación.
3. **Aforo** del emisor y de las bombas dosificadoras, y **capacidad de la línea** (define los 10
   sectores a la vez).
4. **Calibración del LDR** contra un luxómetro a sol pleno y bajo malla (define el 85 % de M-02).
5. **Porcentaje de sombra** de la malla instalada.
6. **Fertilizante foliar y fitosanitario genérico** concretos: registro SENASA, dosis de etiqueta,
   período de reingreso y vida útil del caldo.
7. **Horarios del plan de rustificación y sus 45 días**, contra lo que hace hoy el vivero y según la
   época del año.
8. **Umbral de confianza** con el set de validación del modelo (§1.3).
9. **Intervalo de telemetría** en pleno verano (chequeo de §1.2).
10. **Posición del nodo testigo**: que su sector sea representativo de la MZ.

---

## 13. Notas de implementación

**Backend**
- Pedir la lectura al nodo cada 4 h, alineada a las 02:00, con espera de respuesta y reintentos
  (S-02, S-03). Pedir la lectura de verificación 30 min después del riego del sector del nodo
  testigo (E-01).
- Calcular y guardar el **DPV** con cada lectura (§1.4).
- Suscribirse al **tópico de errores** del ESP32 (S-05).
- Calcular los tiempos de riego y de las bombas con los caudales calibrados, y llevar el **stock**
  de cada tanque (§8.2).
- Evaluar las reglas por evento (lectura, captura, cambio de franja) y no repetir alertas (§2).

**Front**
- **Bloquear / reanudar** por sector y por MZ (S-01). Hoy no existe.
- **Iniciar / cancelar / resetear** el plan de rustificación por MZ, sólo con el rol Ingeniero
  Agrónomo (§7).
- Registrar la **revisión humana** (S-06), las **reparaciones** (S-05) y la **recarga de tanques** (§8.2).
- Mostrar el DPV junto con las otras métricas.

**Firmware (ESP32)**
- Publicar los errores en su tópico, por ejemplo `.../errores`, con un payload como
  `{ nodo, sector, tipo, valor, timestamp }`. Tipos: `SIN_CAUDAL`, `CAUDAL_EXCESIVO` y
  `MEDIASOMBRA_FIN_DE_CARRERA`.
- Toda orden de riego o de bomba lleva su duración, y el ESP32 corta solo al cumplirla.
- Leer los dos fines de carrera de la malla e informar su estado real (lo necesita S-05).

---

## Anexo A · Cambios respecto de la v1

### Resumen

| Tema | v1 | v2 |
|---|---|---|
| Sector | 4 bandejas de 25 tubetes de 110 cm³ en ~1 m² | 4 bandejas de 25 celdas de 100 cm³ en ~0,3 m² |
| Telemetría | El nodo reporta cada 30 min | El backend pide la lectura cada 4 h, con reintentos inmediatos |
| Captura | Sin frecuencia fija, con órdenes de captura | 1 pasada por día. Las reglas usan la última captura |
| Confianza | 85 % | 70 %, calibrable con el set de validación |
| Caudalímetro | Se daba por instalado | Se incorpora en la versión final. Mientras tanto, aforo × tiempo |
| Fallas de hardware | Las detectaba el motor | Las detecta el ESP32 y las publica en un tópico de errores |
| Límites diarios, histéresis, batería | S-08, principio 7, S-12 | Eliminados |
| Riego | Ventana 06–20, intervalo de 90 min, turnos por prioridad, factores por diagnóstico | Ventana 06–18, sin intervalo mínimo, en orden de numeración, sin factores |
| Mediasombra | % de apertura por sector | Abierta/cerrada por MZ, regulada por horario; cerrada de noche |
| Plan de rustificación | Adaptativo (avanza, pausa y retrocede), también regulaba riego y nutrición | Fijo, por calendario; sólo mediasombra; lo inicia, cancela y resetea el agrónomo |
| Insumos | 5 productos (NPK, nitrato de potasio, quelato, fungicida, acaricida) inyectados en el riego | 2 soluciones listas: fertilizante foliar y fitosanitario genérico |
| Nutrición | Atada al riego y a la etapa del plan | Plan de nutrición propio (cada 7 días) + N-02 |
| Efectividad | 8 evaluaciones | 3 (riego, fertilizante correctivo, fitosanitario) |

### Equivalencia de IDs

| v1 | v2 |
|---|---|
| S-01 | S-01 |
| S-02, S-03 | **S-02** Nodo sin respuesta (por intentos fallidos) |
| S-04 | **S-03** Lectura inválida (con reintento) |
| S-05 | **S-04** |
| S-06, S-07 | **S-05** Falla reportada por el ESP32 |
| S-08, S-10, S-12 | Eliminadas |
| S-09 | **S-06** (con alcance y deltas definidos) |
| S-11 | Eliminada: ya no se inyecta en la línea de riego |
| R-01 … R-05 | R-01 … R-05 (ajustadas) |
| R-06 (enfriamiento), R-07, R-08, R-09 | Eliminadas. **R-06 nueva**: pausa después de una aplicación |
| M-01 | M-01 (perfil de crecimiento o etapa del plan) |
| M-02, M-03, M-04 | **M-02** (una sola regla con "alguna de") |
| M-05 | Eliminada: la malla queda cerrada toda la noche |
| M-06, M-08 | Eliminadas |
| M-07 | Eliminada: pasa al principio 4 ("gana cerrar") |
| P-01 … P-04 | Eliminadas: el plan es fijo (§7) |
| N-01, N-02 | N-01 (plan de nutrición), N-02 |
| N-03 | Eliminada |
| F-C0, F-C1, F-C2 | **F-C1** (causa no nutricional: sólo alerta) |
| F-C3, F-C4 | **F-C2** |
| F-F1, F-F2 / F-F3 | **F-F1** / **F-F2** |
| F-P1, F-P2, F-P3 | **F-P1** |
| F-S1 | **F-S1** (riego del sector) + **F-S2** (protección de la MZ) |
| F-S2 | **F-S3** |
| F-04, F-05 | F-04, F-05 |
| F-06 (rotación) | **F-06** (tope de aplicaciones) |
| E-01 … E-08 | E-01 … E-03 |
| O-03 | Eliminada (ya no existen R-08 ni M-05) |
| O-04 … O-06 | O-03 … O-05 |

## Anexo B · Fuentes consultadas

- FCF-UNaM, *Producción de plantines de yerba mate (Ilex paraguariensis)*, documento técnico 2021:
  envases (tubetes de 125/140 cm³, bandejas de 40 × 90 cm³ y de 25 × 100 cm³), sombra de 50–80 % en
  crecimiento, rustificación de 20 a 45 días entre los 6 y 8 meses (en general, febrero–marzo),
  fertilización de base de liberación lenta de 2–3 kg/m³ más foliares.
  <https://www.fcf.unam.edu.ar/modules/uploads/2017/03/DocumentoTecnicoYerba2021.pdf>
- Bandeja forestal de 25 celdas: 28 × 27 × 9 cm, ~100 cm³ por celda.
  <https://plasticosperu.com.ar/producto/bandeja-forestal-standard-25-celdas/>
- Momento de aplicación de fitosanitarios (temprano o al final de la tarde, 10–25 °C, evitar más de 30 °C):
  <https://www.reporteagricola.cl/noticia/noticias/2025/12/como-elegir-el-mejor-momento-del-dia-para-aplicar-fitosanitarios> ·
  <https://www.infocampo.com.ar/recomiendan-medir-la-temperatura-y-humedad-para-aplicar-fitosanitarios/>
