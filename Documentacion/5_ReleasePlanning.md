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

### Release 1 - Monitoreo y Visualización Base

**Objetivo:** Disponer de una primera versión capaz de capturar la información del vivero de forma automática y visualizarla en una plataforma web centralizada, validando el funcionamiento de la infraestructura IoT.

**Alcance:** Esta versión permite al productor comenzar a monitorear su vivero de forma remota, consultando las métricas de sustrato y ambiente de cada sector y el estado general de la producción sobre un mapa. Incorpora la captura periódica y autónoma de datos de suelo y clima, el acceso seguro por rol desde cualquier dispositivo y el registro espacial del hardware instalado.

**Observaciones:**
- Sienta la base de sensado y la plataforma web sobre la que se construyen las versiones siguientes.
- Habilita la trazabilidad espacial al asociar cada equipo físico con su sector dentro del vivero.
- Incluye el monitoreo del estado técnico del hardware (batería, señal y fallas) para el recambio preventivo.

**Fecha estimada:** Julio (31/07)

---

### Release 2 - Diagnóstico Inteligente y Alertas

**Objetivo:** Incorporar visión computacional e inteligencia artificial para detectar problemas sanitarios de la yerba mate.

**Alcance:** Esta versión transforma al sistema de un simple monitor de variables en una herramienta de diagnóstico temprano, analizando las imágenes de los plantines para identificar estrés solar, clorosis, daño fúngico y plagas foliares. El productor puede consultar cada diagnóstico con su nivel de confianza y severidad, y recibir alertas clasificadas sin necesidad de recorrer el vivero.

**Observaciones:**
- Integra el modelo de IA entrenado específicamente para los estados sanitarios de la yerba mate.
- Suma la información meteorológica externa como insumo para anticipar condiciones de riesgo.
- Establece el sistema de notificaciones y alertas por severidad como canal de comunicación con el productor.

**Fecha estimada:** Agosto (31/08)

---

### Release 3 - Automatización Agronómica

**Objetivo:** Permitir que el sistema tome decisiones y actúe automáticamente sobre el cultivo a partir de los datos y diagnósticos recolectados.

**Alcance:** Esta versión cierra el ciclo operativo al ejecutar acciones correctivas localizadas sobre los sectores afectados, incluyendo riego por microaspersión, dosificación de nutrientes y fitosanitarios, y control gradual de la mediasombra para la rustificación. El motor de decisiones combina sensores, visión y pronóstico climático, y el agrónomo puede calibrar umbrales, límites operativos y planes de rustificación.

**Observaciones:**
- El productor deja de sólo recibir información y diagnósticos: el sistema interviene físicamente y de forma autónoma.
- Incorpora la operación manual y los límites de seguridad para que el operario pueda forzar o interrumpir un actuador ante emergencias.
- Ejecuta la lógica de rustificación controlada como diferencial agronómico propio del producto.

**Fecha estimada:** 17/10

---

### Release 4 - Trazabilidad Completa y Operación Offline

**Objetivo:** Completar la visión integral de Yerbanalytics incorporando trazabilidad avanzada y resiliencia operativa ante cortes de conectividad.

**Alcance:** Esta versión alcanza la propuesta de valor completa del producto, con un historial auditable de cada acción ejecutada junto a la condición que la originó y la evaluación de su efectividad luego de un tiempo prudencial. Garantiza que el sistema físico siga operando y decidiendo sin conexión a internet, sincronizando al recuperarla, y habilita la exportación de reportes para auditorías y control de calidad.

**Observaciones:**
- Consolida la trazabilidad de decisiones agronómicas como respaldo técnico para auditorías y mejora continua.
- El modo offline corre el modelo y almacena datos localmente, mitigando el riesgo de conectividad intermitente en el campo.
- Incluye la gestión de roles y permisos para proteger la infraestructura de modificaciones no autorizadas.

**Fecha estimada:** 07/11
