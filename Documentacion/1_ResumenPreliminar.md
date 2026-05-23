# 1. Resumen Preliminar

## 1.1 Objetivos del Proyecto

Se han establecido los siguientes objetivos principales:

1. Desarrollar un sistema IoT y de software capaz de monitorear variables de la tierra e imágenes de plantas de yerba mate.

2. Procesar la información recolectada mediante un modelo de Inteligencia Artificial para clasificar el estado de la planta, nivel de confianza y severidad de posibles anomalías.

3. Realizar acciones correctivas en el cultivo mediante actuadores, informar al productor mediante un panel de control centralizado, registrar el historial de decisiones tomadas y hacer seguimiento del estado post-acción para evaluar su efectividad.

4. Integrar datos climáticos de APIs externas para enriquecer el algoritmo de toma de decisiones.

## 1.2 Breve Descripción del Proyecto

Yerbanalytics es una plataforma agrotech enfocada en la industria yerbatera, compuesta por infraestructura de hardware en terreno y una plataforma web central.

Su núcleo funcional se basa en, por un lado, monitorizar el estado vital y nutricional de la planta de yerba mate, y por otro, capturar imágenes del follaje que serán procesadas por un modelo de inteligencia artificial para emitir diagnósticos tempranos relativos a la detección y clasificación de plagas y afecciones foliares. Además, será complementado por pronósticos meteorológicos externos para que el sistema finalmente ejecute acciones correctivas (riego, mediasombra y dosificación) mediante hardware físico, registrando cada decisión en un historial trazable.

Para el marco de este proyecto final, la construcción física se limitará a un Nodo MVP. Esta unidad autónoma concentrará en un solo equipo todos los componentes (sensores, cámara y actuadores a escala), permitiendo validar el flujo completo de "lectura-inferencia-acción" sobre una planta en condiciones controladas.

Sin embargo, el diseño de la solución contempla un análisis teórico de escalabilidad comercial aplicado a un caso real: un lote productivo de 5 hectáreas en la provincia de Misiones. Para cubrir la totalidad de este cultivo con viabilidad financiera, la arquitectura del sistema evoluciona hacia una red híbrida. En este despliegue a escala, los nodos sensores se distribuirán estratégicamente basándose en la topografía de la chacra (pendientes, curvas de nivel y zonas bajas) utilizando algoritmos matemáticos de estimación para cubrir el 100% del suelo. Paralelamente, el componente de visión artificial se desacoplará del hardware fijo para integrarse en maquinaria móvil, y la actuación se gestionará mediante dispositivos de apertura y cierre automatizado (válvulas inteligentes) distribuidos por sectores.

## 1.3 Beneficios al Negocio

Como resultado de la implementación de nuestro sistema, el productor tendrá los siguientes beneficios:

- **Intervención temprana y automatizada sobre el cultivo:** La combinación de sensores de suelo, visión artificial e integración climática permite detectar problemas en estadios iniciales y actuar de forma autónoma mediante los actuadores, reduciendo el daño al cultivo y el costo de remediación.

- **Uso eficiente de insumos:** Las decisiones de riego, fertilización y aplicación de pesticidas se basan en el estado real del suelo y las condiciones climáticas previstas, evitando aplicaciones genéricas o innecesarias y reduciendo el gasto en agua e insumos agronómicos.

- **Trazabilidad de decisiones agronómicas:** El historial de acciones ejecutadas por el sistema vinculado a las detecciones y condiciones que las originaron, ofrece respaldo técnico para auditorías, análisis de rendimiento por sector y mejora continua del manejo del cultivo.

- **Reducción de la dependencia de inspecciones presenciales:** El monitoreo automatizado mediante hardware distribuido en el lote permite detectar y responder a problemas sin requerir recorridas diarias, reduciendo el costo logístico y operativo de supervisión en plantaciones de gran extensión.

## 1.4 Plan a Alto Nivel

El proyecto se estructurará bajo el siguiente cronograma de hitos críticos:

- **Hito 1 (Mes 1 - Mayo):** Definición del alcance del proyecto y armado de documentación. Investigación y compra de componentes de hardware. Inicio de recolección de imágenes del dataset.

- **Hito 2 (Mes 2 - Junio):** Definición de Arquitectura, diseño de base de datos. Continuación de la recolección y etiquetado del dataset.

- **Hito 3 (Mes 3 - Julio):** Ensamblaje y validación del prototipo del nodo: integración de sensores, pruebas de lectura de variables del suelo y aire, y primeras pruebas de activación de actuadores en entorno controlado. Comienzo del entrenamiento del modelo de IA.

- **Hito 4 (Mes 4 - Agosto):** Desarrollo core del backend, integración con APIs climáticas externas, motor de recomendaciones. Modelo de IA entrenado e integrado al backend.

- **Hito 5 (Mes 5 - Septiembre):** Conexión real entre el prototipo del nodo y el backend. Desarrollo del frontend web: visualización de métricas, alertas, diagnósticos, historial de acciones.

- **Hito 6 (Mes 6 - Octubre):** Finalización del desarrollo del backend y frontend. Realización de pruebas, integración end-to-end validada.

- **Hito 7 (Mes 7 - Noviembre):** Producto final con todos los módulos integrados, bugs resueltos, documentación completa y prototipo ya funcional.

## 1.5 Riesgos Identificados

Se han identificado los siguientes riesgos potenciales que podrían impactar el desarrollo:

### Retrasos en importación/envío de hardware

Si los sensores necesarios demoran en llegar, entonces se retrasará la integración IoT.

**Mitigación:** Identificar proveedores locales alternativos como plan B, priorizando componentes con stock local para el prototipo inicial. Utilizar datos simulados (mocks) de los sensores para avanzar con el software mientras llega el hardware.

### Falta de dataset para la IA

Si no conseguimos suficientes imágenes clasificadas de hojas de yerba mate enfermas/sanas, entonces el modelo tendrá baja precisión.

**Mitigación:** Iniciar la recolección desde el Hito 1. Aplicar técnicas de Data Augmentation a las imágenes conseguidas o usar modelos pre-entrenados (Transfer Learning). Explorar datasets públicos de enfermedades en plantas similares.

### Falta de disponibilidad del productor e ingeniero agrónomo

Si el productor y el ingeniero agrónomo no están disponibles para compartir su expertise de la plantación y para validar datos, entonces se retrasará el desarrollo del producto y comprometerá la precisión de las detecciones y acciones recomendadas.

**Mitigación:** Programar reuniones semanales sincrónicas, comunicarse asincrónicamente con el productor y el ingeniero. No depender de un único contacto, identificar algún stakeholder alternativo como cooperativas yerbateras u otros productores independientes.

### Conectividad inestable en la chacra

Si la señal de datos en la plantación de Misiones es débil o intermitente, entonces el hardware no podrá enviar los datos al servidor.

**Mitigación:** Diseñar la arquitectura con modo offline tal que el modelo de IA corre localmente en el hardware, almacena datos y diagnósticos localmente, y sincroniza con el servidor cuando recupera conectividad.

### Dificultad de acceso al suministro eléctrico

Si el punto de instalación del hardware en la plantación se encuentra alejado de la red eléctrica, entonces dependeremos de baterías, lo que compromete el funcionamiento continuo del sistema si no se gestiona correctamente el consumo energético del hardware.

**Mitigación:** Diseñar el sistema para bajo consumo desde el inicio, con modo Deep Sleep para que solo despierte al tomar y enviar datos. Cotizar y presupuestar panel solar y batería LiPo como alternativa de alimentación autónoma.
