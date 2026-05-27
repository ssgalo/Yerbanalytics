> Desarrollo del Template SCRUM - Punto 1

# 1. Resumen Preliminar

**Proyecto:** Yerbanalytics - Sistema de Monitoreo Inteligente de Yerba Mate

## 1.1 Objetivos del Proyecto

Se han establecido los siguientes objetivos principales:

- Desarrollar un sistema IoT y de software capaz de monitorear variables de la tierra e imágenes de plantas jóvenes de yerba mate.
- Procesar la información recolectada mediante un modelo de Inteligencia Artificial para clasificar el estado de la planta, nivel de confianza y severidad de posibles anomalías.
- Realizar acciones correctivas en el cultivo mediante actuadores, informar al productor mediante un panel de control centralizado, registrar el historial de decisiones tomadas y hacer seguimiento del estado post-acción para evaluar su efectividad.
- Integrar datos climáticos de APIs externas para enriquecer el algoritmo de toma de decisiones.

## 1.2 Breve Descripción del Proyecto

Yerbanalytics es un ecosistema agrotech enfocado en la industria yerbatera, compuesto por infraestructura de hardware y una plataforma web central.

Su núcleo funcional opera sobre la etapa más crítica de la cadena productiva de la yerba mate: el desarrollo controlado del plantín previo al trasplante a campo. La Ilex paraguariensis, nombre científico de la yerba mate, es una especie nativa de la selva paranaense que en sus primeras semanas de vida requiere condiciones muy precisas de humedad, nutrición y exposición lumínica para desarrollarse correctamente.

El sistema monitorea el estado hídrico y nutricional del sustrato en tubetes de alta densidad. En lugar de medir cada maceta, distribuimos un número estratégico de sensores en "tubetes testigo". Gracias a que las condiciones dentro del vivero son muy uniformes, un solo sensor nos permite conocer con gran precisión el estado de salud de un bloque de hasta 10.000 plantines.

Además, captura imágenes del follaje mediante un módulo de visión computacional montado sobre un sistema de riel automatizado. Estas imágenes son procesadas por un modelo de inteligencia artificial entrenado estrictamente para esta especie, capaz de diagnosticar estrés solar (quemadura), clorosis (déficit nutricional), plaga foliar y daño fúngico en etapas tempranas. Adicionalmente, el sistema integra pronósticos meteorológicos externos para anticipar condiciones de riesgo y ajustar sus decisiones de actuación.

Como resultado del diagnóstico, el sistema ejecuta acciones correctivas de forma autónoma: gestiona el riego sobre las bandejas de plantines afectados; dosifica nutrientes, acaricidas y fungicidas directamente en la línea de irrigación, sólo en los sectores correspondientes; y despliega o retira la mediasombra de forma gradual para implementar el proceso de rustificación lumínica (proceso biológico mediante el cual se retira progresivamente la sombra y el agua para inducir estrés controlado en el plantín, logrando que su tallo se endurezca y se vuelva leñoso antes del trasplante al campo). Esto lo hará específicamente en la zona afectada, sin desperdiciar insumos en el resto de la producción sana.

Toda la operación del sistema se centraliza en una plataforma web a la que el productor accede desde cualquier dispositivo. Desde allí puede visualizar las métricas del sustrato y las condiciones ambientales, consultar los diagnósticos emitidos por el modelo de inteligencia artificial y acceder al historial completo de acciones ejecutadas por el sistema junto a las condiciones que las originaron.

> _foto ejemplar del prototipo al que se intentará llegar_

### 1.2.1 O.L.A

#### Objetivos específicos

- **Monitorear variables críticas del sustrato y del ambiente**

  Capturar de manera periódica humedad del sustrato, temperatura, humedad ambiental, niveles de nutrientes y radiación/luminosidad, para conocer el estado fisiológico de los plantines y del entorno inmediato.

- **Detectar de forma temprana anomalías propias de la yerba mate**

  Analizar imágenes de los plantines mediante un modelo de inteligencia artificial entrenado específicamente para identificar estados relevantes del cultivo, como plantín sano, estrés solar o quemadura, clorosis asociada a déficit nutricional, daño fúngico y síntomas compatibles con plagas foliares tempranas.

- **Automatizar decisiones agronómicas sobre sectores específicos**

  Ejecutar acciones correctivas localizadas, como riego por microaspersión, dosificación de nutrientes o fitosanitarios por línea de irrigación y ajuste de mediasombra, según las condiciones detectadas por sensores, visión artificial y datos climáticos externos.

- **Implementar una lógica de rustificación controlada**

  Gestionar la transición gradual de los plantines desde condiciones de alta protección hacia un nivel progresivo de exposición lumínica, con el fin de fortalecer su estructura antes del trasplante.

- **Centralizar la información y la trazabilidad del sistema**

  Proveer una plataforma web donde el productor pueda visualizar métricas, diagnósticos, alertas, acciones ejecutadas y su evolución temporal, generando historial técnico para seguimiento y mejora continua.

- **Integrar información externa de clima para mejorar la toma de decisiones**

  Incorporar pronósticos y variables meteorológicas de APIs externas para anticipar condiciones de riesgo (lluvia inminente, picos de radiación UV) que alimenten el algoritmo de toma de decisiones, evitando tanto la saturación hídrica accidental del sustrato como la exposición solar no planificada durante la rustificación.

- **Reducir el uso ineficiente de agua e insumos**

  Ejecutar acciones únicamente sobre las zonas que lo requieran, evitando el tratamiento indiscriminado de toda la superficie y mejorando la eficiencia operativa y económica.

#### 2. Límites del proyecto

##### 2.1. Incluido en el proyecto

- Monitoreo de plantines de yerba mate en etapa temprana de desarrollo.
- Sensores para variables críticas del sustrato y del ambiente.
- Captura de imágenes mediante un sistema de visión computacional montado sobre riel automatizado.
- Modelo de IA entrenado para detectar estados y anomalías específicas de la yerba mate.
- Integración con APIs climáticas externas.
- Automatización de riego por microaspersión.
- Dosificación localizada de nutrientes y tratamientos correctivos mediante bombas peristálticas.
- Control automatizado de mediasombra para acompañar el proceso de rustificación.
- Panel web centralizado para monitoreo, diagnóstico, alertas e historial.
- Registro de decisiones y eventos del sistema.
- Lógica de operación por sectores o bloques de plantines.
- Estrategia de operación offline o con conectividad intermitente, con sincronización posterior.

##### 2.2. No incluido en el proyecto

- Monitoreo de plantaciones adultas de yerba mate en campo abierto.
- Automatización de grandes extensiones agrícolas o lotes a cielo abierto.
- Riego por goteo o infraestructura de riego para terreno abierto.
- Automatización de mallas o sistemas de protección pensados para plantas adultas en suelo.
- Gestión de otras especies vegetales fuera de yerba mate.
- Comercialización de insumos, gestión contable, facturación o ERP agrícola.
- Predicción de rendimiento industrial de cosecha o trazabilidad de la etapa de secado, molienda o empaquetado.
- Integración con maquinaria pesada o vehículos autónomos de campo.
- Automatización de poda, trasplante o cosecha.
- Entrenamiento de un modelo universal para todas las enfermedades de todas las plantas.
- Desarrollo de una aplicación móvil nativa (iOS/Android).

#### 3. Alcance

**Alcance funcional**

Yerbanalytics se enfocará en el cultivo controlado de plantines de yerba mate, donde el control ambiental, hídrico y nutricional es crítico para asegurar plantines sanos y aptos para el trasplante. El sistema operará sobre bloques homogéneos de producción, donde un conjunto reducido de sensores representará una zona mayor bajo condiciones similares, y donde la visión computacional permitirá una inspección más precisa a nivel visual.

El proyecto abarcará el ciclo completo de monitoreo y respuesta dentro de esta etapa:

- recolección de datos,
- análisis por IA,
- decisión automatizada,
- ejecución de actuadores,
- registro de la acción realizada,
- visualización del resultado en la plataforma web.

**Alcance técnico**

El sistema incluirá:

- Capa de sensado para humedad de sustrato, temperatura, humedad ambiental, nutrientes y radiación/luz.
- Capa de visión para captura y análisis de imágenes de plantines.
- Capa de inteligencia para clasificación del estado sanitario y fisiológico.
- Capa de actuación para riego, fertirriego, aplicación localizada y control de mediasombra.
- Capa de gestión para trazabilidad, historial y monitoreo remoto.

**Alcance de negocio**

El proyecto apunta a resolver un problema real y específico de la producción de yerba mate en etapa inicial: evitar pérdidas por quemaduras, deficiencias nutricionales, daño fúngico y plagas foliares. La propuesta busca diferenciarse de un sistema genérico de cuidado de plantas al trabajar con:

- una especie concreta,
- un entorno productivo definido,
- patologías y respuestas agronómicas propias de la yerba mate,
- y una lógica de automatización pensada para rustificación y sanidad de plantines, no para jardinería general.

**Alcance operativo**

La solución será aplicable a viveros dentro de los campos donde la repetición del patrón de cultivo permita inferir el estado de bloques completos a partir de sensores representativos. La operación contemplará una arquitectura escalable para cubrir unidades de producción de entre 50.000 y 100.000 plantines, organizados en sectores de 100 tubetes cada uno (equivalente a una superficie de aproximadamente 1 m² por sector, cubierta por un único microaspersor). Para una instalación de referencia de 100.000 plantines, la topología comprende:

- 1.000 sectores de actuación, cada uno gobernado por un microaspersor individual dentro de macro-zonas hidráulicas gestionadas por electroválvulas maestras.
- 10 nodos sensores testigo, uno por macro-zona, que infieren el estado del sustrato de los 100 sectores circundantes gracias a la homogeneidad microclimática del vivero.
- 1 sistema de visión computacional con rieles automatizados que recorre secuencialmente los 1.000 sectores a lo largo de la jornada, entregando diagnóstico visual de alta granularidad sin requerir unidades de cámara adicionales.

## 1.3 Beneficios al Negocio

Como resultado de la implementación de nuestro sistema, el productor tendrá los siguientes beneficios:

- **Intervención temprana y automatizada sobre el cultivo:** La combinación de sensores de sustrato, visión artificial e integración climática permite detectar problemas en etapas iniciales y actuar de forma autónoma mediante los actuadores, reduciendo el daño al cultivo y el costo de remediación.
- **Uso eficiente de insumos:** Las decisiones de riego, fertilización y aplicación de acaricidas/fungicidas se basan en el estado real del sustrato, del plantín y las condiciones climáticas previstas, evitando aplicaciones genéricas o innecesarias y reduciendo el gasto en agua e insumos agronómicos.
- **Trazabilidad de decisiones agronómicas:** El historial de acciones ejecutadas por el sistema vinculado a las detecciones y condiciones que las originaron, ofrece respaldo técnico para auditorías, análisis de rendimiento por sector y mejora continua del manejo del cultivo.
- **Reducción de la dependencia de inspecciones presenciales:** El monitoreo automatizado mediante hardware distribuido en el cultivo de plantines permite detectar y responder a problemas sin requerir recorridas diarias, reduciendo el costo logístico y operativo de supervisión humana que, ante una detección tardía, podría comprometer la totalidad de la plantación joven.

## 1.4 Plan a Alto Nivel

El proyecto se estructurará bajo el siguiente cronograma de hitos críticos:

- **Hito 1 (Mes 1 - Mayo):** Definición del alcance del proyecto y armado de documentación. Investigación y compra de componentes de hardware. Inicio de recolección de imágenes del dataset.
- **Hito 2 (Mes 2 - Junio):** Definición de Arquitectura, diseño de base de datos. Continuación de la recolección y etiquetado del dataset.
- **Hito 3 (Mes 3 - Julio):** Ensamblaje y validación del prototipo: integración de sensores, pruebas de lectura de variables del sustrato, y primeras pruebas de activación de actuadores en entorno controlado. Comienzo del entrenamiento del modelo de IA.
- **Hito 4 (Mes 4 - Agosto):** Desarrollo core del backend, integración con APIs climáticas externas. Modelo de IA entrenado e integrado al backend.
- **Hito 5 (Mes 5 - Septiembre):** Conexión real entre el prototipo y el backend. Desarrollo del frontend web: visualización de métricas, alertas, diagnósticos, historial de acciones.
- **Hito 6 (Mes 6 - Octubre):** Finalización del desarrollo del backend y frontend. Realización de pruebas, integración end-to-end validada.
- **Hito 7 (Mes 7 - Noviembre):** Producto final con todos los módulos integrados, bugs resueltos, documentación completa y prototipo ya funcional.

## 1.5 Riesgos Identificados

Se han identificado los siguientes riesgos potenciales que podrían impactar el desarrollo:

- **Retrasos en importación/envío de hardware:** Si los sensores necesarios demoran en llegar, entonces se retrasará la integración IoT.
  - Mitigación: Identificar proveedores locales alternativos como plan B, priorizando componentes con stock local para el prototipo inicial.
  - Utilizar datos simulados (mocks) de los sensores para avanzar con el software mientras llega el hardware.
- **Falta de dataset para la IA:** Si no conseguimos suficientes imágenes clasificadas de hojas de yerba mate enfermas/sanas, entonces el modelo tendrá baja precisión.
  - Mitigación: Iniciar la recolección desde el Hito 1.
  - Aplicar técnicas de Data Augmentation a las imágenes conseguidas o usar modelos pre-entrenados (Transfer Learning).
  - Explorar datasets públicos de enfermedades en plantas similares.
- **Falta de disponibilidad del productor e ingeniero agrónomo:** Si el productor y el ingeniero agrónomo no están disponibles para compartir su expertise de la plantación y para validar datos, entonces se retrasará el desarrollo del producto y comprometerá la precisión de las detecciones y acciones recomendadas.
  - Mitigación: Programar reuniones semanales sincrónicas, comunicarse asincrónicamente con el productor y el ingeniero.
  - No depender de un único contacto, identificar algún stakeholder alternativo como cooperativas yerbateras u otros productores independientes.
- **Conectividad inestable en la zona de plantación:** Si la señal de datos en la plantación de Misiones es débil o intermitente, entonces el hardware no podrá enviar los datos al servidor.
  - Mitigación: Diseñar la arquitectura con modo offline tal que el modelo de IA corre localmente en el hardware, almacena datos y diagnósticos localmente, y sincroniza con el servidor cuando recupera conectividad.
- **Dificultad de acceso al suministro eléctrico:** Si el punto de instalación del hardware se encuentra alejado de la red eléctrica, entonces dependeremos de baterías, lo que compromete el funcionamiento continuo del sistema si no se gestiona correctamente el consumo energético del hardware.
  - Mitigación: Diseñar el sistema para bajo consumo desde el inicio, con modo Deep Sleep para que solo despierte al tomar y enviar datos.
  - Cotizar y presupuestar panel solar y batería LiPo como alternativa de alimentación autónoma.
