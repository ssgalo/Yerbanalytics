# Análisis crítico del OLA — Yerbanalytics

> **Propósito:** Revisión técnica del documento `OLA.md` (Objetivos, Límites, Alcance) antes de avanzar en la implementación.
> **Enfoque:** Detectar inconsistencias, riesgos técnicos y vacíos que puedan comprometer la tesis.
> **Fecha:** 2026-05-27
> **Estado:** Para debate de equipo.

---

## Lectura global

El OLA está **bien escrito y demuestra dominio real del problema agronómico** — eso es lo difícil y ya está logrado. Lo que le falta es **rigor en las costuras**:

- Las promesas de actuación **"localizada"** no cierran con la topología hidráulica descrita.
- No hay **estrategia de datos** para el modelo de IA (probablemente el mayor riesgo del proyecto).
- Faltan los aspectos de **seguridad / failsafe** propios de un sistema ciberfísico que dispensa agua y químicos.

Eso es lo que separa un OLA "lindo" de uno "a prueba de tribunal".

El análisis se organiza en cuatro bloques:

1. 🔴 Inconsistencias (resolver sí o sí)
2. 🟡 Dudas técnicas (riesgos)
3. 🟢 Cosas que faltan (sugerencias para agregar)
4. ⚠️ Actualización obligatoria (ya desactualizado)

---

## 🔴 1. Inconsistencias

### A. La más grave: tratamiento "localizado" vs. topología hidráulica

Los objetivos repiten la palabra **"localizado"**:

- *"Ejecutar acciones correctivas **localizadas**... dosificación de nutrientes o fitosanitarios **por línea de irrigación**"* (Objetivo específico)
- *"Ejecutar acciones **únicamente sobre las zonas que lo requieran**"* (Objetivo de reducción de insumos)
- *"Dosificación **localizada** de nutrientes"* (Incluido)

Pero el **Alcance operativo** define estas granularidades:

| Capacidad | Granularidad | Cantidad |
|---|---|---|
| Riego (microaspersión) | Por **SECTOR** (~1 m²) | 1.000 microaspersores |
| Visión / diagnóstico IA | Por **SECTOR** | 1.000 sectores |
| Dosificación nutrientes / fitosanitarios | Por **MACRO-ZONA** ("por línea de irrigación", electroválvulas maestras) | 10 macro-zonas (100 sectores c/u) |

**El problema:** la IA detecta daño fúngico en **un sector** (1 m²), pero la hidráulica solo permite dosificar fitosanitario a **toda la macro-zona** (100 m², 10.000 plantines). Eso **contradice directamente** los objetivos de "acción localizada" y "reducir el uso ineficiente de insumos": se estaría tratando 100 sectores para corregir uno.

**Cómo resolverlo:**
- Opción 1: aclarar explícitamente que **el riego es por sector** pero **la fertirrigación/fitosanidad es por macro-zona**, asumiendo esa limitación de diseño de forma honesta.
- Opción 2: repensar la topología hidráulica para permitir dosificación de menor granularidad.

> No se puede prometer "localizado" y entregar "por macro-zona" sin aclararlo. Es lo primero que va a cuestionar el tribunal.

### B. Operación offline vs. dependencias de conexión

"Incluido" dice: *"Estrategia de operación offline o con conectividad intermitente, con sincronización posterior."*

Pero el sistema depende de:
- **APIs climáticas externas** (requieren internet)
- **Panel web centralizado** (internet)
- **Alertas por WhatsApp** (internet)

**Cómo resolverlo:** aclarar el modelo de degradación:
- Qué funciona **offline** (ej: actuación local autónoma con la última configuración conocida).
- Qué requiere **conexión** (clima, alertas, dashboard).

Hoy se lee como una contradicción porque no está explicado.

---

## 🟡 2. Dudas técnicas (riesgos)

### C. "Niveles de nutrientes" — ¿cómo se miden realmente?

Medir "niveles de nutrientes" en sustrato con un sensor es de lo más difícil que hay:

- Los sensores baratos tipo "NPK" son **notoriamente imprecisos**.
- Lo confiable de medir es **EC (conductividad eléctrica)** → da sales totales, **NO** nutrientes específicos — y **pH**.

**Acciones:**
1. **Definir qué se mide realmente** (¿EC? ¿NPK con qué sensor y qué precisión?). "Niveles de nutrientes" en abstracto es una promesa que la física no cumple fácil.
2. **Falta el pH.** Se mencionan nutrientes pero no pH, y en manejo nutricional el pH del sustrato es **crítico** (determina la disponibilidad de nutrientes). Es raro tener uno sin el otro.

### D. El dataset de IA — riesgo #1 del proyecto

El modelo debe clasificar **6 estados** específicos de yerba mate:
sano, estrés solar/quemadura, clorosis por déficit, estrés hídrico, daño fúngico, plagas foliares tempranas.

**Pregunta clave:** ¿de dónde sale el **dataset etiquetado** de plantines de yerba mate en cada uno de esos estados?

- Entrenar un modelo específico de especie requiere **cientos o miles de imágenes etiquetadas por un experto agrónomo**.
- Probablemente sea el **cuello de botella de toda la tesis**.
- El OLA **no menciona la estrategia de datos**.

**Acciones:**
- Incluir en el alcance **cómo se genera/etiqueta el dataset**, o
- Acotar el problema (ej: empezar con menos clases y un dataset propio fotografiado en el vivero).

### E. Suposición de homogeneidad — 1 sensor por 100 m²

El modelo asume que *"la homogeneidad microclimática"* permite que **10 nodos sensores** infieran el estado de **1.000 sectores** (1 sensor cada 100 m² / 10.000 plantines).

- Es una **suposición fuerte**, y de ella depende toda la eficiencia de costos.
- El microclima varía: borde vs. centro del invernadero, cerca de ventilación, esquinas, etc.

**Acción:** para una tesis, esta suposición hay que **justificarla o validarla experimentalmente**, no darla por hecha. Es una hipótesis, y debe tratarse como tal.

### F. Latencia de la visión — 1 Gantry, ~1 recorrida por jornada

El Gantry recorre los 1.000 sectores *"a lo largo de la jornada"* → cada sector se inspecciona **~1 vez al día**.

- Un daño fúngico o estrés hídrico puede avanzar en **horas**.
- Latencia visual de hasta **24 h** en el peor caso.

**Acción:** el OLA debería **reconocer esta limitación temporal** y explicar cómo se compensa (ej: los sensores de sustrato actúan en tiempo real; la visión aporta diagnóstico de tendencia/granularidad fina).

---

## 🟢 3. Cosas que faltan (sugerencias para agregar)

1. **Failsafe / seguridad física.** El sistema dispensa **agua y fitosanitarios** automáticamente. ¿Qué pasa si un sensor falla y reporta "seco" estando inundado? ¿Sobre-riega? ¿Sobre-dosifica químicos? Se necesita **lógica de seguridad y límites (failsafe)**. En un sistema ciberfísico que actúa sobre el mundo real, **no es opcional**. Hoy no está en el OLA.

2. **Calibración y deriva de sensores.** Los sensores derivan con el tiempo. ¿Hay estrategia de calibración? Al menos mencionarlo.

3. **Autenticación / roles en el panel web.** El panel controla actuadores físicos. ¿Cualquiera entra? Mínimo: usuarios y autenticación. No se menciona.

4. **Criterios de éxito / métricas del proyecto.** ¿Cómo se va a **demostrar** que el sistema funciona? (% de reducción de agua, accuracy del modelo, % de detección temprana). Para una tesis, los criterios de éxito medibles son oro. (Se entiende que el OLA es alcance, no requisitos, pero vale enunciarlos.)

5. **Energía / alimentación.** Actuadores, Gantry, nodos sensores: ¿alimentación eléctrica?, ¿respaldo ante corte? Especialmente relevante si se habla de operación offline.

6. **Atributos de calidad (no funcionales).** Disponibilidad, latencia de actuación, mantenibilidad. Aunque sea un párrafo.

---

## ⚠️ 4. Actualización obligatoria (ya desactualizado)

En "Incluido" figura:

> *"Bot de Telegram para envío de notificaciones y alertas**?**"*

Esto **ya no es válido.** El equipo decidió **WhatsApp Cloud API** para MVP y producto final (documentado en `Notificaciones-WhatsApp-Cloud-API.md`).

**Acciones:**
- Reemplazar por: *"Notificaciones y alertas vía WhatsApp Cloud API"* y quitar el signo de pregunta.
- Nota a favor: en "No incluido" se descarta *"aplicación móvil nativa"* → la decisión de WhatsApp **refuerza** esto: WhatsApp es el canal móvil **sin construir una app**. Buen argumento para la defensa.

---

## Resumen de acciones priorizadas

| # | Hallazgo | Tipo | Prioridad |
|---|---|---|---|
| A | "Localizado" vs. dosificación por macro-zona | Inconsistencia | 🔴 Alta |
| D | Falta estrategia de dataset para la IA | Riesgo | 🔴 Alta |
| 1 | Falta failsafe / seguridad física | Vacío | 🔴 Alta |
| C | "Niveles de nutrientes" + falta pH | Riesgo | 🟡 Media |
| E | Suposición de homogeneidad sin validar | Riesgo | 🟡 Media |
| B | Offline vs. dependencias de conexión | Inconsistencia | 🟡 Media |
| F | Latencia de visión (1 recorrida/día) | Riesgo | 🟡 Media |
| ⚠️ | Telegram → WhatsApp desactualizado | Actualización | 🟢 Rápida |
| 3 | Autenticación del panel web | Vacío | 🟢 Baja |
| 4 | Criterios de éxito / métricas | Vacío | 🟢 Baja |
| 2 | Calibración de sensores | Vacío | 🟢 Baja |
| 5 | Energía / alimentación | Vacío | 🟢 Baja |
| 6 | Atributos de calidad (no funcionales) | Vacío | 🟢 Baja |
