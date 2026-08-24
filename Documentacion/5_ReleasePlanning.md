# 5. Release planning - Plan de versiones

**Proyecto:** Yerbanalytics - Sistema de Monitoreo Inteligente de Yerba Mate

## Historia de Revisión

| Fecha | Versión | Descripción | Autor |
| --- | --- | --- | --- |
| 06/06/2026 | 3.0 | Redacción completa del punto 5. | Equipo 111 |

---

## 5.1 Estimación Story Points

Para estimar el esfuerzo de cada historia de usuario del backlog de Yerbanalytics, el equipo adoptó la técnica de **Planning Poker**, que combina la opinión de todos los integrantes del equipo para asignar un valor de esfuerzo relativo (los Story Points) a cada historia de usuario. En lugar de estimar en horas o días, se estima la complejidad, el riesgo y el volumen de trabajo de una historia en relación con las demás.

Cada integrante recibe un mazo de cartas con valores basados en la secuencia de Fibonacci: **1 – 2 – 3 – 5 – 8 – 13 – 21**.

Se usa Fibonacci porque, a medida que una historia es más grande, crece también la incertidumbre sobre su estimación. Los saltos cada vez mayores entre los números (1, 2, 3, 5, 8…) reflejan esa imprecisión natural y obligan al equipo a tomar decisiones claras en lugar de discutir diferencias mínimas.

### Proceso aplicado

- Se explica la historia. El Product Owner presenta la historia de usuario y se aclaran dudas sobre su alcance y criterios de aceptación.
- Cada miembro piensa una estimación de forma individual y en silencio, para no influenciar al resto.
- Todos muestran su carta al mismo tiempo, revelando su estimación de manera simultánea.
- Si hay diferencias grandes, se discuten. Quienes dieron el valor más alto y el más bajo explican su razonamiento, ya que suelen surgir detalles técnicos o de alcance que no todos habían considerado.
- Se vuelve a votar las veces que sea necesario hasta llegar a un consenso sobre el valor final.

### Interpretación de la escala

| Story Points | Interpretación |
| --- | --- |
| 1 - 2 | Historia muy simple, bien comprendida, sin incertidumbre. |
| 3 | Historia sencilla con poco esfuerzo de desarrollo. |
| 5 | Complejidad media. Requiere coordinación entre componentes. |
| 8 | Historia compleja, con riesgo técnico o integración de hardware/software. |
| 13 | Muy compleja. Involucra múltiples capas, lógica autónoma o tolerancia a fallos. |
| 21 | Historia épica de máxima complejidad e incertidumbre. |

### Estimación por historia de usuario

| ID HU | Nombre HU | Story Points |
| --- | --- | --- |
| HU-01 | Inicio de sesión seguro según rol asignado | 3 |
| HU-02 | Visualización de métricas de sustrato y ambiente | 3 |
| HU-03 | Captura periódica de datos de clima y suelo | 5 |
| HU-04 | Análisis visual de plantas mediante inteligencia artificial | 21 |
| HU-05 | Consulta de diagnósticos con nivel de confianza y severidad | 5 |
| HU-06 | Riego autónomo por sector | 8 |
| HU-07 | Dosificación automática de pesticidas y nutrientes | 8 |
| HU-08 | Control gradual de la mediasombra (rustificación) | 8 |
| HU-09 | Integración de pronósticos meteorológicos externos | 5 |
| HU-10 | Alertas clasificadas por severidad | 3 |
| HU-11 | Consulta del historial completo de acciones y su justificación | 5 |
| HU-12 | Visualización de la evolución del sector post-acción | 5 |
| HU-13 | Operación autónoma del sistema físico sin conexión a internet | 13 |
| HU-14 | Visualización del vivero mediante mapa de sectores | 5 |
| HU-15 | Configuración de umbrales, límites y planes de rustificación | 8 |
| HU-16 | Exportación de reportes de trazabilidad en distintos formatos | 3 |
| HU-17 | Acceso multidispositivo y diseño responsivo | 3 |
| HU-18 | Mapeo del vivero y registro del hardware instalado | 8 |
| HU-19 | Interrupción o forzado manual del accionamiento de un sector | 5 |
| HU-20 | Gestión de roles y permisos de usuarios | 5 |
| HU-21 | Visualización del estado técnico del hardware | 5 |

> **Total: 134 Story Points** distribuidos en 21 historias de usuario.

---

## 5.2 Plan de versiones

### Release 1 - Sensado, Diagnóstico Visual y Riego Automático

**HUs Incluidas:** HU-02, HU-03, HU-04, HU-06

**Objetivo:** Disponer de una primera versión capaz de capturar de forma autónoma la información del vivero, analizarla mediante visión por inteligencia artificial y ejecutar el riego automáticamente, validando el funcionamiento integral de la infraestructura IoT y el motor de acción.

**Alcance:** Esta versión permite al productor comenzar a monitorear su vivero de forma remota y consultar las métricas de sustrato y ambiente más recientes de cada sector, obtener un diagnóstico visual temprano de anomalías (hongos, clorosis, plagas y estrés solar) y contar con el riego autónomo por sector que mantiene la humedad del sustrato dentro de los umbrales. Incorpora la captura periódica y autónoma de datos de suelo como base del flujo de información.

**Observaciones:**
- Sienta la base de sensado y combina el monitoreo con la primera acción correctiva automática (riego).
- Integra el modelo de IA de visión para el diagnóstico temprano de la yerba mate sin necesidad de recorrer el vivero.
- Mantiene el flujo de datos sin intervención manual mediante la captura periódica y autónoma de datos de suelo.

**Desglose y Planificación de Sprints:**
- Sprint 1 (30/05 al 13/06): HU-04
- Sprint 2 (13/06 al 27/06): HU-03, HU-04
- Sprint 3 (28/06 al 11/07): HU-03, HU-04
- Sprint 4 (11/07 al 25/07): HU-02, HU-04, HU-06
- Sprint 5 (25/07 al 01/08): HU-04, HU-06

**Fecha de entrega estimada:** 01/08

---

### Release 2 - Automatización Agronómica Configurable y Alertas Inteligentes

**HUs Incluidas:** HU-05, HU-07, HU-08, HU-09, HU-10, HU-15, HU-19

**Objetivo:** Convertir los diagnósticos del sistema en acciones agronómicas automáticas, calibrables por el agrónomo y supervisables de forma segura por el operario, manteniendo informado al productor mediante alertas e incorporando el pronóstico climático como insumo de decisión.

**Alcance:** Esta versión transforma al sistema en una herramienta de diagnóstico accionable: el productor consulta cada diagnóstico con su nivel de confianza y severidad, y el sistema dosifica pesticidas y nutrientes automáticamente sobre el sector comprometido y controla la mediasombra de forma gradual para ejecutar el plan de rustificación. El ingeniero agrónomo calibra umbrales para ajustar las decisiones a la realidad del vivero, mientras que el operario puede forzar o interrumpir manualmente el accionamiento de un sector ante emergencias o mantenimiento. Además, se suma la integración de pronósticos meteorológicos externos para anticipar condiciones de riesgo y un sistema de alertas clasificadas por severidad que comunica anomalías y acciones críticas sin necesidad de recorrer el vivero.

**Observaciones:**
- Cierra el ciclo diagnóstico-acción al dosificar insumos en el sector afectado y ejecutar la rustificación controlada como diferencial agronómico propio del producto.
- Suma la información meteorológica externa como insumo para anticipar condiciones de riesgo y ajustar las decisiones automáticas.
- Establece el sistema de notificaciones y alertas por severidad como canal de comunicación con el productor.
- Incorpora la operación manual y umbrales configurables.

**Desglose y Planificación de Sprints:**
- Sprint 1 (01/08 al 08/08): HU-05
- Sprint 2 (08/08 al 22/08): HU-08, HU-09, HU-15
- Sprint 3 (22/08 al 05/09): HU-07, HU-10, HU-15
- Sprint 4 (05/09 al 19/09): HU-15, HU-19

**Fecha de entrega estimada:** 19/09

---

### Release 3 - Trazabilidad Espacial, Monitoreo de Hardware y Operación Offline

**HUs Incluidas:** HU-13, HU-14, HU-18, HU-21

**Objetivo:** Dotar al sistema de la trazabilidad espacial, el monitoreo del estado técnico del hardware y la resiliencia ante cortes de conectividad necesarios para su puesta en marcha y su operación confiable en campo.

**Alcance:** Esta versión consolida la infraestructura y la robustez operativa del sistema en el vivero. El administrador mapea la distribución del vivero y registra el hardware instalado, habilitando la trazabilidad espacial, y el productor visualiza el estado de la producción sobre un mapa de sectores para localizar dónde están los problemas. Incorpora el monitoreo del estado técnico del hardware para el recambio preventivo de equipos caídos o rotos, y garantiza que el sistema físico siga operando y tomando decisiones sin conexión a internet, sincronizando los datos al recuperar la red.

**Observaciones:**
- Habilita la trazabilidad espacial al asociar cada equipo físico con su sector dentro del vivero y al localizar visualmente los problemas sobre el mapa.
- Incluye el monitoreo del estado técnico del hardware (batería, señal y fallas) para anticipar el recambio preventivo de equipos.
- El modo offline corre el modelo y almacena datos localmente, mitigando el riesgo de conectividad intermitente en el campo.

**Desglose y Planificación de Sprints:**
- Sprint 1 (19/09 al 03/10): HU-13, HU-18
- Sprint 2 (03/10 al 17/10): HU-14, HU-21

**Fecha de entrega estimada:** 17/10

---

### Release 4 - Trazabilidad Completa, Reportes y Seguridad de Acceso

**HUs Incluidas:** HU-01, HU-11, HU-12, HU-16, HU-17, HU-20

**Objetivo:** Incorporar trazabilidad auditable de las decisiones, la evaluación de su efectividad, reportes exportables y un esquema de acceso seguro y multiplataforma.

**Alcance:** Esta versión alcanza la propuesta de valor completa del producto. Provee un historial auditable de cada acción ejecutada junto a la condición que la originó y permite evaluar la evolución del sector luego de un tiempo prudencial post-acción para medir la efectividad real de la intervención. Habilita la exportación de reportes de trazabilidad en distintos formatos para auditorías y control de calidad. En el plano de acceso, incorpora el inicio de sesión seguro por rol, la gestión de roles y permisos de los usuarios y el acceso a la plataforma desde distintos dispositivos y tamaños de pantalla.

**Observaciones:**
- Consolida la trazabilidad de decisiones agronómicas y la evaluación de su efectividad como respaldo técnico para auditorías y mejora continua.
- Incluye la gestión de roles y permisos y el inicio de sesión seguro para proteger la infraestructura de modificaciones agronómicas no autorizadas.
- Habilita el acceso remoto multiplataforma para supervisar la producción desde cualquier dispositivo.

**Desglose y Planificación de Sprints:**
- Sprint 1 (17/10 al 31/10): HU-11, HU-12, HU-16, HU-20
- Sprint 2 (31/10 al 07/11): HU-01, HU-17
