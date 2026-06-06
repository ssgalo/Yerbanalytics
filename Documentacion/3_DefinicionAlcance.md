# 3. Definición de alcance

**Proyecto:** Yerbanalytics - Sistema de Monitoreo Inteligente de Yerba Mate

## Historia de Revisión

| Fecha | Versión | Descripción | Autor |
| --- | --- | --- | --- |
| 30/05/2026 | 2.0 | Redacción del punto 3. | Equipo 111 |
| 06/06/2026 | 3.0 | Refinamiento del punto 3 en su totalidad. Actualización del Visual Story Mapping, consolidación definitiva del Product Backlog (ordenado por prioridad y temática), definición exhaustiva de los Criterios de Aceptación para cada historia, y mayor precisión en los Criterios de Completado. | Equipo 111 |

## Convenciones

- **Roles:** las historias se redactan desde la perspectiva de los distintos perfiles de usuario del sistema: **Productor Viverista**, **Ingeniero Agrónomo**, **Operario**, **Administrador** y **Usuario del sistema**. Las funcionalidades de actuación autónoma se expresan como *"Como Productor Viverista quiero que el sistema..."*.
- **Prioridad:** 1-Alta / 2-Media / 3-Baja.
- **Status:** todas las historias inician en `To Do` (backlog inicial sin desarrollar).
- **IDs:** formato `#HU-0XX`.

---

## 3.1 Visual Story Mapping

El backlog se organiza en cuatro releases incrementales, agrupando las historias de usuario según el momento del ciclo de vida del producto en que aportan valor.

![Visual Story Mapping Yerbanalytics](Visual%20Story%20Mapping%20Yerbanalytics.png)

| Release | Nombre | Historias de usuario |
| --- | --- | --- |
| **R1** | Monitoreo y Visualización Base | #HU-01, #HU-02, #HU-03, #HU-14, #HU-17, #HU-18, #HU-20, #HU-21 |
| **R2** | Diagnóstico Inteligente y Alertas | #HU-04, #HU-05, #HU-09, #HU-10 |
| **R3** | Automatización Agronómica | #HU-06, #HU-07, #HU-08, #HU-15, #HU-19 |
| **R4** | Trazabilidad Completa y Operación Offline | #HU-11, #HU-12, #HU-13, #HU-16 |

- **R1 - Monitoreo y Visualización Base**
- **R2 - Diagnóstico Inteligente y Alertas**
- **R3 - Automatización Agronómica**
- **R4 - Trazabilidad Completa y Operación Offline**

---

## 3.2 Product Backlog

A continuación se presenta el Product Backlog compuesto por las Historias de Usuario que engloban la funcionalidad core del sistema, priorizadas jerárquicamente según su valor de negocio y criticidad para el ciclo de vida del cultivo, y agrupadas internamente por temática operativa.

| ID | Como | Quiero | Para | Prioridad | Status |
| --- | --- | --- | --- | --- | --- |
| #HU-03 | Productor Viverista | Que el sistema capture periódicamente la información del clima y suelo | Mantener el flujo de datos sin depender de intervenciones manuales | 1-Alta | To Do |
| #HU-09 | Productor Viverista | Que el sistema integre pronósticos meteorológicos externos | Anticipar condiciones de riesgo (lluvia, picos UV) y ajustar sus decisiones automáticas | 1-Alta | To Do |
| #HU-04 | Productor Viverista | Que el sistema analice visualmente las plantas mediante inteligencia artificial | Detectar anomalías propias de la yerba mate (hongos, clorosis, plagas y estrés solar) en etapa temprana | 1-Alta | To Do |
| #HU-15 | Ingeniero Agrónomo | Configurar umbrales, límites operativos máximos y planes de rustificación | Calibrar las decisiones del sistema a la realidad del vivero y evitar sobredosis | 1-Alta | To Do |
| #HU-06 | Productor Viverista | Que el sistema ejecute el riego de forma autónoma por sector | Mantener la humedad del sustrato dentro de los umbrales sin generar pudrición | 1-Alta | To Do |
| #HU-07 | Productor Viverista | Que el sistema dosifique pesticidas y nutrientes automáticamente | Mitigar afecciones directamente en el sector comprometido sin desperdiciar insumos | 1-Alta | To Do |
| #HU-08 | Productor Viverista | Que el sistema controle la mediasombra de forma gradual | Ejecutar el plan biológico de rustificación de manera autónoma | 1-Alta | To Do |
| #HU-19 | Operario | Interrumpir o forzar manualmente el accionamiento de un sector | Intervenir directamente en emergencias o realizar mantenimiento físico seguro | 1-Alta | To Do |
| #HU-13 | Productor Viverista | Que el sistema físico siga operando y tomando decisiones sin conexión a internet | Garantizar la protección de las plantas ante cortes prolongados de la red | 1-Alta | To Do |
| #HU-18 | Administrador | Mapear la distribución del vivero y registrar el hardware instalado | Habilitar la trazabilidad espacial y asociar cada equipo físico a su sector en el software | 1-Alta | To Do |
| #HU-14 | Productor Viverista | Visualizar el estado de la producción mediante un mapa de los sectores | Localizar visual y espacialmente dónde están los problemas en el vivero | 2-Media | To Do |
| #HU-02 | Productor Viverista | Visualizar las métricas de sustrato y ambiente | Conocer el estado fisiológico e hídrico de los sectores | 2-Media | To Do |
| #HU-05 | Productor Viverista | Consultar los diagnósticos emitidos con su nivel de confianza y severidad | Entender el estado sanitario de la producción | 2-Media | To Do |
| #HU-10 | Productor Viverista | Recibir alertas clasificadas por severidad ante anomalías o acciones críticas | Estar informado sin recorrer el vivero | 2-Media | To Do |
| #HU-11 | Productor Viverista | Consultar el historial completo de cada acción ejecutada y su justificación | Auditar el comportamiento del sistema y respaldar mis controles de calidad | 2-Media | To Do |
| #HU-12 | Productor Viverista | Visualizar la evolución del sector luego de un tiempo prudencial post-acción | Evaluar la efectividad real de la intervención del sistema | 2-Media | To Do |
| #HU-17 | Productor Viverista | Acceder a la plataforma desde distintos dispositivos y tamaños de pantalla | Supervisar la producción de forma remota | 2-Media | To Do |
| #HU-21 | Administrador | Visualizar el estado técnico del hardware (batería, señal, fallas físicas) | Detectar equipos caídos o rotos para ejecutar el recambio preventivo | 2-Media | To Do |
| #HU-01 | Usuario del sistema | Iniciar sesión de forma segura bajo mi rol asignado | Acceder a la plataforma con los permisos y vistas que me corresponden | 3-Baja | To Do |
| #HU-20 | Administrador | Gestionar los roles y permisos de los usuarios de la plataforma | Proteger la infraestructura y evitar modificaciones agronómicas por personal no autorizado | 3-Baja | To Do |
| #HU-16 | Productor Viverista | Exportar reportes de trazabilidad en distintos formatos | Respaldar auditorías sanitarias y procesar el consumo de insumos en mis sistemas contables | 3-Baja | To Do |

> **Total: 21 historias de usuario.**

---

## 3.3 Criterios de aceptación

### Historia de Usuario: #HU-01
*Como **Usuario del sistema** quiero iniciar sesión de forma segura bajo mi rol asignado para acceder a la plataforma con los permisos y vistas que me corresponden.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un usuario registrado con credenciales válidas y un rol asignado (Administrador, Ingeniero Agrónomo, Productor Viverista, Operario) | ingresa su usuario y contraseña correctos | el sistema lo autentica y lo redirige al tablero principal con las funciones exclusivas de su perfil habilitadas. |
| CA-02 | un usuario que ingresa una contraseña o usuario incorrecto | intenta iniciar sesión | el sistema rechaza el acceso y muestra un mensaje de error genérico (ej. "Credenciales incorrectas") sin revelar qué campo específico falló. |
| CA-03 | un usuario con una sesión activa en la plataforma | permanece inactivo durante el tiempo máximo de seguridad configurado | el sistema cierra la sesión automáticamente y solicita una nueva reautenticación para continuar operando. |

---

### Historia de Usuario: #HU-02
*Como **Productor Viverista** quiero visualizar las métricas de sustrato y ambiente más recientes para conocer el estado fisiológico e hídrico de mis sectores en el vivero.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | que el Gateway procesó telemetría reciente de los sensores del vivero | el usuario abre el panel de métricas de un sector | el sistema muestra la lectura con su marca temporal (timestamp) y las unidades exactas: temperatura (°C), humedad ambiental y de sustrato (%), nivel de nutrientes/CE (dS/m) y radiación lumínica (W/m² o UV Index). |
| CA-02 | un repositorio de lecturas en la base de datos de series temporales | el usuario selecciona una vista de evolución histórica | el sistema permite filtrar mediante rangos fijos agronómicos: 24 horas (curva de secado), 7 días (ciclo de riego), 30/60 días (fase de rustificación) y un rango personalizado limitado a un máximo de 90 días. |
| CA-03 | un sensor testigo que no reporta su paquete de datos | supera el umbral de espera corto configurado por el Watchdog (ej. 2 horas sin reportar) | el sistema muestra el último dato conocido resaltado en color ámbar, indicando el estado "Señal Intermitente / Desactualizado". |
| CA-04 | un sensor que se mantiene sin conexión prolongada | supera el umbral crítico del Watchdog (ej. 24 horas continuas sin heartbeat) | el sistema lo marca como "Fuera de Servicio", anula la actuación autónoma en esa macro-zona y emite una alerta sugiriendo revisión física o recambio de batería. |
| CA-05 | un valor recolectado que rompe los límites agronómicos seguros configurados | el usuario visualiza el panel de ese sector | el sistema resalta el valor visualmente en rojo como "Condición de Riesgo". |

---

### Historia de Usuario: #HU-03
*Como **Productor Viverista** quiero que el sistema capture periódicamente la información del clima y suelo para mantener el flujo de datos sin depender de intervenciones manuales.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un microcontrolador testigo operativo en el vivero | su reloj interno cumple el intervalo de medición fijo y hardcodeado en el firmware (ej. cada 30 minutos) calculado por ingeniería | el hardware despierta, registra las variables del sustrato y ambiente, las asocia a su macro-zona, transmite el paquete de datos y retorna al modo de ultra bajo consumo (Deep Sleep). |
| CA-02 | la recepción de un paquete de datos proveniente de un "tubete testigo" | la base de datos recibe y procesa la lectura | el sistema asocia y replica matemáticamente esos valores a los 100 sectores que conforman esa misma macro-zona hidráulica del vivero. |
| CA-03 | que el microcontrolador despierta para tomar la lectura periódica | detecta que el Gateway o la red de comunicación local no están disponibles | almacena el registro temporalmente en su memoria flash interna (Buffer) para no perder el historial de la curva de secado y reintenta el envío en el siguiente ciclo. |

---

### Historia de Usuario: #HU-04
*Como **Productor Viverista** quiero que el sistema analice visualmente las plantas mediante inteligencia artificial para detectar anomalías (hongos, clorosis, plagas y estrés solar) en etapa temprana.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el sistema de visión activo y moviéndose por los rieles del vivero | se posiciona sobre las bandejas correspondientes a un sector específico | captura una imagen cenital de los plantines y la asocia directamente a las coordenadas de ese sector y al timestamp exacto. |
| CA-02 | que el sistema de visión ha capturado la imagen de un sector | el modelo de IA procesa la imagen de forma local | clasifica la imagen en uno de los 5 estados base: Sano, Estrés Solar, Clorosis, Plaga Foliar o Daño Fúngico, asignando un porcentaje de confianza y un nivel de severidad. |
| CA-03 | el modelo de IA evaluando un fotograma del cultivo | el diagnóstico devuelto no supera el umbral de Confidence Score mínimo aceptable (ej. < 85%) | el sistema etiqueta el diagnóstico como "No Concluyente", aborta cualquier disparador de acción correctiva automática y solicita validación visual humana en la plataforma. |
| CA-04 | un diagnóstico visual de IA que clasifica un sector con "Daño Fúngico" | el sistema cruza este dato con el sensor testigo y detecta que la humedad del sustrato supera el 80% | el motor de reglas deduce una alta probabilidad de presencia de hongo y genera la alerta sanitaria correspondiente. |
| CA-05 | que el sistema de visión captura una imagen sin acceso a la red externa | el modelo Edge ejecuta el análisis visual | procesa la clasificación localmente, informa al motor de reglas del nodo para actuar in situ, y encola el diagnóstico para sincronizarlo cuando retorne la conexión. |

---

### Historia de Usuario: #HU-05
*Como **Productor Viverista** quiero consultar los diagnósticos emitidos con su nivel de confianza y severidad para entender el estado sanitario de la producción.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | una alerta o inferencia generada y sincronizada por el sistema de IA | el Productor Viverista accede a la vista de Diagnósticos de la plataforma | visualiza un reporte listando la patología o estrés detectado, el sector físico afectado dentro del vivero, el Confidence Score de la IA y la severidad asignada. |
| CA-02 | un diagnóstico de anomalía en el historial de un sector | el usuario hace clic o selecciona dicho registro | el sistema despliega la fotografía cenital original sin procesar, capturada por el sistema de visión, que desencadenó dicha clasificación. |
| CA-03 | un historial masivo de diagnósticos y evaluaciones diarias | el usuario requiere priorizar la revisión | el sistema provee herramientas para filtrar y agrupar los resultados por: Tipo de Anomalía, Nivel de Severidad, Rango de Fechas, Macro-zona y Sector. |

---

### Historia de Usuario: #HU-06
*Como **Productor Viverista** quiero que el sistema ejecute el riego de forma autónoma por sector para mantener la humedad del sustrato dentro de los umbrales sin generar pudrición.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un sector cuya humedad de sustrato cae por debajo del umbral mínimo configurado | la telemetría del nodo testigo tiene una antigüedad menor a 2 horas y no hay bloqueo manual activo | el motor de decisiones ordena activar el relé de la electroválvula de ese sector. |
| CA-02 | un sector que registra déficit hídrico | la API meteorológica integrada confirma lluvia inminente en la zona que supera el umbral de precipitaciones configurado | el sistema pospone el riego para evitar la saturación hídrica del sustrato. |
| CA-03 | un sector que requiere riego | el usuario activó un bloqueo manual o el sensor testigo lleva más de 2 horas sin reportar | el sistema anula la actuación autónoma para evitar inundaciones a ciegas y emite una alerta de "Riego abortado". |
| CA-04 | que el sistema envió la orden de abrir la electroválvula | el caudalímetro no detecta flujo de agua tras 10 segundos, o la presión de la línea cae a cero | el sistema cierra inmediatamente el relé, marca el sector con "Falla Hidráulica" y notifica la anomalía crítica. |
| CA-05 | que la electroválvula de un sector se encuentra abierta ejecutando un riego | se cumple el tiempo máximo configurado o la telemetría alcanza el umbral de corte | el sistema cierra el relé y registra en el historial el sector, la duración, el volumen emitido y la condición que originó el riego. |

---

### Historia de Usuario: #HU-07
*Como **Productor Viverista** quiero que el sistema dosifique pesticidas y nutrientes automáticamente para mitigar afecciones directamente en el sector comprometido sin desperdiciar insumos.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un diagnóstico visual de plaga, daño fúngico o déficit nutricional | el modelo arroja un Confidence Score superior al 85% y la dosis calculada no supera el límite de seguridad | el sistema activa la bomba inyectando los mililitros exactos del insumo en la línea de ese sector. |
| CA-02 | una imagen con posibles anomalías detectadas | el modelo arroja un Confidence Score menor al 85% | el sistema no inyecta químicos, cataloga el diagnóstico como "No Concluyente" y solicita validación visual humana. |
| CA-03 | un diagnóstico positivo de afección con confianza mayor al 85% | la inyección requerida hace que el sector supere el límite máximo de mililitros de químico permitidos por cada 24 horas | el sistema bloquea la aplicación para evitar intoxicar la planta y emite una alerta crítica de "Límite químico diario alcanzado". |
| CA-04 | que la bomba peristáltica está inyectando un insumo en un sector | finaliza de emitir los mililitros exactos calculados para la dosis | el sistema detiene la bomba y registra en el historial el sector, el tipo de químico, el volumen exacto aplicado y el diagnóstico que lo originó. |

---

### Historia de Usuario: #HU-08
*Como **Productor Viverista** quiero que el sistema controle la mediasombra de forma gradual para ejecutar el plan biológico de rustificación de manera autónoma.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un plan de rustificación activo en el vivero | avanza el día en el cronograma o el sensor local detecta un pico de radiación UV por encima del umbral de estrés | el motor paso a paso despliega o retrae gradualmente la mediasombra hasta la posición calculada. |
| CA-02 | que las reglas automáticas exigen un movimiento de la lona | un Operario o Productor activa la pausa manual desde la plataforma | el actuador ignora el plan biológico y mantiene su posición estática actual hasta que la reanudación sea autorizada. |
| CA-03 | que el controlador envía la orden de mover la malla | el driver del motor detecta un pico de corriente sostenido (indicando atasco en guías) o no se activa el fin de carrera en el tiempo estipulado | el sistema corta la energía del motor inmediatamente para evitar que se queme y alerta sobre la "Falla mecánica en mediasombra". |
| CA-04 | que el motor está moviendo la mediasombra | el actuador alcanza la posición de destino (porcentaje de apertura) calculada | el sistema detiene el motor y registra en el historial el nuevo estado de cobertura y el factor (plan o clima) que motivó el ajuste. |

---

### Historia de Usuario: #HU-09
*Como **Productor Viverista** quiero que el sistema integre pronósticos meteorológicos externos para anticipar condiciones de riesgo (lluvia, picos UV) y ajustar sus decisiones automáticas.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el motor de reglas evaluando las variables del entorno | solicita el pronóstico externo mediante una petición asíncrona y responde exitosamente en menos de 10.000 ms | el sistema incorpora las variables (probabilidad de lluvia, índice UV) al árbol de decisiones sin interrumpir la lectura del hardware local. |
| CA-02 | una consulta asíncrona en curso hacia la API climática | el servidor externo no responde tras un timeout estricto de 15.000 ms | el sistema ejecuta hasta 3 reintentos espaciados utilizando una estrategia de exponential backoff. |
| CA-03 | que los reintentos fallaron o la API externa devuelve errores 5xx | el motor necesita tomar una decisión sobre los actuadores | el sistema ignora temporalmente la API, registra un warning de degradación de servicio, y opera utilizando exclusivamente la telemetría de los sensores físicos del vivero. |

---

### Historia de Usuario: #HU-10
*Como **Productor Viverista** quiero recibir alertas clasificadas por severidad ante anomalías o acciones críticas para estar informado sin recorrer el vivero.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un evento desencadenado por el motor de reglas o por el hardware | el sistema procesa el evento | lo clasifica por severidad (INFO, WARNING, CRITICAL) y lo registra permanentemente en el historial de la plataforma para auditoría. |
| CA-02 | un evento ya clasificado en el backend | su severidad es catalogada como CRITICAL o WARNING | dispara la alerta instantáneamente mediante el servicio de mensajería Push configurado. Si es INFO, no emite Push y solo se registra en web. |
| CA-03 | el envío exitoso de una alerta externa | el usuario visualiza el mensaje | el texto incluye estrictamente: nivel de severidad, timestamp, sector afectado, variable desencadenante y la acción que el sistema ejecutó o bloqueó. |
| CA-04 | una alerta crítica o de advertencia activa en el panel web | el Productor Viverista la revisa y toma conocimiento | el sistema le permite marcarla como "Atendida" o "Leída", removiéndola de la bandeja de notificaciones activas sin eliminarla del historial. |

---

### Historia de Usuario: #HU-11
*Como **Productor Viverista** quiero consultar el historial completo de cada acción ejecutada y su justificación para auditar el comportamiento del sistema y respaldar mis controles de calidad.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el registro histórico de un sector | el usuario visualiza el detalle de una intervención automática | el sistema muestra la cadena completa: Lectura original/Diagnóstico IA → Decisión del motor → Acción física ejecutada. |
| CA-02 | el historial completo de acciones de la plataforma | el usuario requiere procesar auditorías sanitarias o logísticas | el sistema permite filtrar los registros por: Sector/Macro-zona, Tipo de acción (Riego, Insumo, Mediasombra), y Rango de fechas. |
| CA-03 | un evento ya registrado y finalizado en la base de datos | un usuario de cualquier rol intenta editarlo o eliminarlo a través de la interfaz | el sistema bloquea la acción, garantizando que el historial sea inalterable para respaldar certificaciones. |

---

### Historia de Usuario: #HU-12
*Como **Productor Viverista** quiero visualizar la evolución del sector luego de un tiempo prudencial post-acción para evaluar la efectividad real de la intervención del sistema.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | una acción correctiva ejecutada sobre un sector | transcurre el período de latencia biológica/física configurado en los umbrales agronómicos para ese tipo específico de acción | el sistema captura una nueva lectura de la variable afectada y la asocia al evento original en la base de datos. |
| CA-02 | que el período de seguimiento ha finalizado | el motor lógico compara la métrica desencadenante original contra la nueva lectura | el sistema clasifica la acción como "Efectiva" si el Delta superó el umbral de recuperación esperado, y lo muestra en el historial. |
| CA-03 | una acción ejecutada | el sistema evalúa el seguimiento y determina que la variable no mejoró según el Delta esperado | cataloga la acción como "Sin Efectividad", bloquea la repetición autónoma de la misma tarea para evitar riesgos físicos, y emite una alerta CRITICAL exigiendo revisión física. |

---

### Historia de Usuario: #HU-13
*Como **Productor Viverista** quiero que el sistema físico siga operando y tomando decisiones sin conexión a internet para garantizar la protección de las plantas ante cortes prolongados de la red.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | una pérdida de conexión a internet en el vivero | el microcontrolador local evalúa la telemetría del sensor testigo contra los umbrales de humedad cacheados en su memoria | ejecuta el riego o detiene actuadores de forma autónoma, sin necesidad de consultar a la nube central. |
| CA-02 | que el sistema opera en modo offline | las reglas de negocio requieren evaluar el clima externo antes de actuar | el sistema asume un entorno conservador (ignora la predicción), basando su accionar exclusivamente en los sensores físicos locales. |
| CA-03 | que la conexión a internet ha sido restablecida | el nodo físico reconecta con la API central | sincroniza todos los eventos y acciones ejecutadas durante el apagón, respetando el timestamp original de cada suceso sin generar duplicados. |

---

### Historia de Usuario: #HU-14
*Como **Productor Viverista** quiero visualizar el estado de la producción mediante un mapa de los sectores para localizar visual y espacialmente dónde están los problemas en el espacio de producción.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | que el Administrador ha mapeado la infraestructura física | el usuario abre la vista del mapa de producción | el sistema renderiza una cuadrícula interactiva representando la disposición real de las macro-zonas y sectores. |
| CA-02 | sectores con diferentes niveles de alertas activas | el usuario visualiza el mapa general | los sectores se colorean dinámicamente según su estado (ej. verde sano, rojo alerta crítica), permitiendo ubicar focos de riesgo de un vistazo. |
| CA-03 | que el usuario interactúa con la cuadrícula espacial | selecciona o hace clic sobre un sector específico del mapa | el sistema despliega un panel lateral o modal con las métricas actuales, diagnósticos y estado de los actuadores de ese sector. |

---

### Historia de Usuario: #HU-15
*Como **Ingeniero Agrónomo** quiero configurar umbrales, límites operativos máximos y planes de rustificación para calibrar las decisiones del sistema a la realidad del espacio de producción y evitar sobredosis.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el primer encendido del sistema en un entorno nuevo | el Agrónomo ingresa al panel de configuración | el sistema ya tiene cargados valores seguros de fábrica para la Ilex paraguariensis, garantizando el cuidado inmediato sin requerir calibración inicial. |
| CA-02 | que el Agrónomo ingresa un parámetro válido | guarda los cambios en el panel web | el sistema actualiza la base de datos, registra en el historial al usuario/timestamp, y encola la nueva regla para sincronizarla con el hardware en su próximo ciclo. |
| CA-03 | que el Agrónomo intenta modificar un parámetro | ingresa un valor fuera del rango fisiológico permitido | el sistema bloquea el cambio, advierte el riesgo biológico y exige ingresar un número válido. |
| CA-04 | el panel de configuración de actuación de agua | el Agrónomo establece las reglas para la electroválvula | debe definir un tiempo máximo de apertura continua y un volumen máximo diario, asegurando un límite físico contra inundaciones. |
| CA-05 | el panel de configuración de inyección de nutrientes o pesticidas | el Agrónomo habilita la bomba peristáltica | debe definir una dosis máxima en mililitros por sector cada 24 horas, bloqueando físicamente cualquier sobredosis. |
| CA-06 | el módulo de configuración de la mediasombra | el Agrónomo diseña el plan de exposición al sol | debe establecer el cronograma de días y el porcentaje máximo de apertura permitida por etapa. |
| CA-07 | el panel de acciones automáticas | el Agrónomo define las reglas de seguimiento de una acción | establece el tiempo de latencia de espera y el Delta mínimo de mejora exigido para catalogar la acción como efectiva. |

---

### Historia de Usuario: #HU-16
*Como **Productor Viverista** quiero exportar reportes de trazabilidad en distintos formatos para respaldar auditorías sanitarias y procesar el consumo de insumos en mis sistemas contables.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el panel de generación de reportes | el usuario selecciona un rango temporal válido (Fecha Desde ≤ Fecha Hasta) y los sectores de interés | el sistema le permite elegir el formato de salida: PDF (lectura inalterable para auditorías sanitarias) o CSV/Excel (datos crudos de consumos y eventos para integración en sistemas contables). |
| CA-02 | la generación exitosa de un reporte | el usuario abre el archivo exportado | el documento contiene obligatoriamente: Marca temporal exacta, ID del Sector, Condición desencadenante, Acción Ejecutada (con volúmenes/duración si aplica), y el Delta de efectividad alcanzado. |
| CA-03 | que el archivo está listo para la descarga | el navegador solicita guardar el archivo | el sistema autogenera el nombre bajo el formato estricto: Yerbanalytics_[TipoReporte]_[FechaInicio]-[FechaFin].[Extension] (Ej: Yerbanalytics_Sanidad_20260401-20260430.pdf). |
| CA-04 | un rango de fechas y sectores seleccionados en el panel | el usuario solicita el reporte y la consulta a la base de datos arroja 0 resultados | el sistema bloquea el botón de descarga y muestra un warning en la interfaz indicando "No hay registros para los criterios seleccionados", evitando generar un archivo vacío. |

---

### Historia de Usuario: #HU-17
*Como **Productor Viverista** quiero acceder a la plataforma desde distintos dispositivos y tamaños de pantalla para supervisar la producción de forma remota.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un dispositivo con la matriz mínima soportada (Android 10+, iOS 14+, Windows 10+, macOS 11+, Linux actual) ejecutando un navegador con soporte nativo ES6 (Chrome 90+, Safari 14+, Firefox 90+, Edge 90+) | el usuario ingresa a la plataforma web | la interfaz se adapta automáticamente a la resolución (desde 320px hasta 1080p), garantizando que el Dashboard, Historial y Configuración sean operables sin scroll horizontal. |
| CA-02 | que el usuario tiene la sesión web activa en cualquiera de los dispositivos soportados | su dispositivo pierde la conexión a internet | el Service Worker detecta la caída, muestra un banner rojo de "Modo Sin Conexión", mantiene los últimos datos cacheados en pantalla, y deshabilita todos los controles de actuación manual o configuración. |
| CA-03 | un dispositivo utilizando un motor web no soportado o carente de ES6 | el usuario intenta cargar la URL de la plataforma | el sistema bloquea el renderizado y muestra una pantalla estática solicitando la actualización a un navegador moderno por motivos de seguridad. |

---

### Historia de Usuario: #HU-18
*Como **Administrador** quiero mapear la distribución del vivero y registrar el hardware instalado para habilitar la trazabilidad espacial y asociar cada equipo físico a su sector en el software.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el Administrador realizando la configuración inicial de un vivero nuevo, sin topología cargada | define la estructura física del vivero indicando la cantidad de macro-zonas y de sectores por macro-zona (ej. 10 macro-zonas de 100 sectores cada una) | el sistema genera la grilla lógica que representa el vivero, asigna un identificador único e irrepetible a cada sector y a cada macro-zona, y deja la disposición disponible para el mapa de producción. |
| CA-02 | un equipo físico a registrar (nodo sensor testigo, microaspersor/electroválvula, bomba peristáltica) | el Administrador lo da de alta cargando su identificador de hardware (serial/MAC) y lo asocia a su sector o macro-zona correspondiente | el sistema vincula el dispositivo a su posición espacial, permitiendo que toda la telemetría y actuación se atribuya al sector correcto en la trazabilidad. |
| CA-03 | un identificador de hardware (serial/MAC) ya existente en el sistema, o un sector que ya tiene asignado un actuador del mismo tipo | el Administrador intenta registrar el dispositivo duplicado | el sistema bloquea el alta y advierte el conflicto, evitando ambigüedades de trazabilidad y dobles asignaciones sobre un mismo sector. |
| CA-04 | una topología cargada en la que uno o más sectores no tienen mapeado todo el hardware requerido (ej. un microaspersor sin asignar) | el Administrador intenta finalizar o activar la configuración del vivero | el sistema señala los sectores incompletos y advierte que la actuación autónoma permanecerá deshabilitada en ellos hasta completar el mapeo del equipamiento. |

---

### Historia de Usuario: #HU-19
*Como **Operario** quiero interrumpir o forzar manualmente el accionamiento de un sector para intervenir directamente en emergencias o realizar mantenimiento físico seguro.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | un Operario autenticado en la plataforma frente a una emergencia o una tarea de mantenimiento físico | activa el bloqueo manual sobre un sector o una macro-zona | el sistema suspende toda actuación autónoma (riego, dosificación y mediasombra) de ese alcance, deja los actuadores en estado seguro y mantiene el bloqueo hasta que se autorice la reanudación. |
| CA-02 | un sector en operación normal | el Operario fuerza manualmente el accionamiento de un actuador (ej. abrir la electroválvula, inyectar insumo o mover la mediasombra) | el sistema ejecuta la orden de inmediato y la registra en el historial como intervención manual de emergencia, dejando asentado el usuario, el timestamp y el actuador accionado. |
| CA-03 | un accionamiento manual que supera un límite agronómico configurado (tiempo máximo de apertura, volumen diario, dosis máxima de químico) | el Operario confirma la operación bajo su responsabilidad en el modo de emergencia | el sistema advierte el riesgo de exceder el límite pero ejecuta igualmente la acción, y la marca en el historial como "Manual con límite excedido" para su posterior auditoría. |
| CA-04 | un sector con bloqueo manual activo por mantenimiento o emergencia | el Operario finaliza la intervención y reactiva el modo autónomo | el sistema reanuda la evaluación de reglas y planes a partir de la telemetría real y actual del sector (sin ejecutar acciones retroactivas) y registra la reactivación en el historial. |

---

### Historia de Usuario: #HU-20
*Como **Administrador** quiero gestionar los roles y permisos de los usuarios de la plataforma para proteger la infraestructura y evitar modificaciones agronómicas por personal no autorizado.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | el Administrador en el panel de gestión de usuarios | crea o edita un usuario y le asigna uno de los roles definidos (Administrador, Ingeniero Agrónomo, Productor Viverista u Operario) | el sistema aplica la matriz de permisos de ese rol, de modo que el usuario solo accede a las vistas y funciones habilitadas para su perfil. |
| CA-02 | un usuario con sesión activa cuyo rol es modificado, suspendido o revocado por el Administrador | el usuario intenta seguir operando en la plataforma | el sistema invalida su sesión vigente y le exige una nueva autenticación, aplicando los permisos actualizados o denegando el acceso según corresponda. |
| CA-03 | cualquier alta, baja o modificación sobre usuarios, roles o permisos | el Administrador guarda el cambio | el sistema lo asienta en un registro de auditoría inalterable indicando quién realizó el cambio, sobre qué usuario y el timestamp exacto. |

---

### Historia de Usuario: #HU-21
*Como **Administrador** quiero visualizar el estado técnico del hardware (batería, señal, fallas físicas) para detectar equipos caídos o rotos para ejecutar el recambio preventivo.*

| N° Criterio de aceptación | Dado... | Cuando... | Entonces... |
| --- | --- | --- | --- |
| CA-01 | la flota de hardware registrada y operativa en el vivero | el Administrador abre el panel de estado técnico del hardware | el sistema muestra, por cada dispositivo, su tipo, el sector o macro-zona asociado, el nivel de batería, la calidad de señal, la marca temporal del último update y su estado operativo (Operativo, Señal Intermitente o Fuera de Servicio). |
| CA-02 | un nodo sensor testigo cuyo nivel de batería cae por debajo del umbral mínimo configurado (ej. 20%) | la telemetría del dispositivo reporta el nivel bajo | el sistema marca el equipo como "Batería Baja" y emite una alerta WARNING sugiriendo el recambio preventivo antes de que el nodo quede fuera de servicio. |
| CA-03 | un dispositivo que deja de enviar su último update | supera el umbral crítico sin updates (ej. 24 horas continuas sin reportar) | el sistema lo marca como "Fuera de Servicio" en el panel técnico y en el mapa de producción, y emite la alerta de equipo caído para gestionar el recambio. |
| CA-04 | un actuador que reportó una falla física durante su operación (Falla Hidráulica en el riego o Falla mecánica en la mediasombra) | la falla queda registrada por el sistema | el sistema refleja la avería específica en el panel técnico, asociada a su sector, y mantiene el equipo señalizado como averiado hasta que se registre su reparación o recambio. |
| CA-05 | un dispositivo señalizado para recambio por batería agotada, caída o falla física | el Operario o Administrador reemplaza la pieza y da de alta el nuevo equipo asociándolo al sector (reutilizando el registro de hardware) | el sistema limpia el estado de avería, vincula el nuevo dispositivo a la posición espacial y reanuda el monitoreo normal del sector. |

---

## 3.4 Criterios de completado (Definition of Done)

Una historia de usuario se considerará finalizada cuando cumpla con los siguientes puntos:

### Calidad y validación técnica

- La lógica dura del sistema (algoritmos del motor de reglas, procesamiento de telemetría e inferencia) pasó las pruebas unitarias a nivel de código.
- Se realizó una validación End-to-End integrando el software con la infraestructura física. Toda instrucción originada en la plataforma (ya sea una decisión automatizada del sistema o un comando manual/configuración del usuario) impacta exitosamente en los componentes de hardware reales del prototipo (apertura de electroválvulas, lectura de sensores, desplazamiento del riel) o, en su defecto temporal, contra mocks físicos validados en el microcontrolador.
- Se corroboró el éxito de todos los Criterios de Aceptación establecidos para la historia.
- No existen bugs clasificados como Críticos o Bloqueantes (ej. fallos en el motor de decisiones, desconexión de la base de datos, cuelgues del hardware). Los bugs de severidad Baja o Menor (ej. desalineaciones de UI, errores de tipeo) no detienen el release; se documentan, se priorizan y se trasladan al Backlog como deuda técnica controlada.

### Gestión del desarrollo

- El código fuente y la documentación asociados a la historia pasaron por una revisión técnica entre pares (Pull Request aprobado).
- La implementación fue integrada (mergeada) correctamente en la rama destinada a validación del proyecto sin generar conflictos de regresión ni afectar el comportamiento del código existente.
- El comportamiento final de la historia, en su entorno integrado, fue mostrado y aprobado operativamente por el Product Owner.
