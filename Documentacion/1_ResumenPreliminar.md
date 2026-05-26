## 1.1 Objetivos del Proyecto

Se han establecido los siguientes objetivos principales:

- Desarrollar un sistema IoT y de software capaz de monitorear variables de la tierra e imágenes de plantas jóvenes de yerba mate.
- Procesar la información recolectada mediante un modelo de Inteligencia Artificial para clasificar el estado de la planta, nivel de confianza y severidad de posibles anomalías.
- Realizar acciones correctivas en el cultivo mediante actuadores, informar al productor mediante un panel de control centralizado, registrar el historial de decisiones tomadas y hacer seguimiento del estado post-acción para evaluar su efectividad.
- Integrar datos climáticos de APIs externas para enriquecer el algoritmo de toma de decisiones.

## 1.2 Breve Descripción del Proyecto

Yerbanalytics es un ecosistema agrotech enfocado en la industria yerbatera, compuesto por infraestructura de hardware y una plataforma web central.

Su núcleo funcional opera sobre la etapa más crítica de la cadena productiva de la yerba mate: el desarrollo controlado del plantín previo al trasplante a campo. La Ilex paraguariensis, nombre científico de la yerba mate, es una especie nativa de la selva paranaense que en sus primeras semanas de vida requiere condiciones muy precisas de humedad, nutrición y exposición lumínica para desarrollarse correctamente.

El sistema monitorea el estado hídrico y nutricional del sustrato en tubetes de alta densidad. En lugar de medir cada maceta, distribuimos un número estratégico de sensores en "tubetes testigo". Gracias a que las condiciones dentro del módulo de desarrollo de plantines son muy uniformes, un solo sensor nos permite conocer con gran precisión el estado de salud de un bloque de hasta 10.000 plantas.

Además, captura imágenes del follaje mediante un módulo de visión computacional montado sobre un sistema de riel automatizado. Estas imágenes son procesadas por un modelo de inteligencia artificial entrenado estrictamente para esta especie, capaz de diagnosticar estrés solar (quemadura), clorosis (déficit nutricional), plaga foliar (ácaros) y daño fúngico (hongos) en estadios tempranos. Adicionalmente, el sistema integra pronósticos meteorológicos externos para anticipar condiciones de riesgo y ajustar sus decisiones de actuación.

Como resultado del diagnóstico, el sistema ejecuta acciones correctivas de forma autónoma: gestiona el riego sobre las bandejas de plantines afectados; dosifica nutrientes, acaricidas y fungicidas directamente en la línea de irrigación, sólo en los sectores correspondientes; y despliega o retira la mediasombra de forma gradual para implementar el proceso de rustificación lumínica (proceso biológico mediante el cual se retira progresivamente la sombra y el agua para inducir estrés controlado en el plantín, logrando que su tallo se endurezca y se vuelva leñoso antes del trasplante al campo). Esto lo hará específicamente en la zona afectada, sin desperdiciar insumos en el resto de la producción sana.

Toda la operación del sistema se centraliza en una plataforma web a la que el productor accede desde cualquier dispositivo. Desde allí puede visualizar las métricas del sustrato y las condiciones ambientales, consultar los diagnósticos emitidos por el modelo de inteligencia artificial y acceder al historial completo de acciones ejecutadas por el sistema junto a las condiciones que las originaron.

## 1.3 Beneficios al Negocio

Como resultado de la implementación de nuestro sistema, el productor tendrá los siguientes beneficios:

- **Intervención temprana y automatizada sobre el cultivo:** La combinación de sensores de sustrato, visión artificial e integración climática permite detectar problemas en estadios iniciales y actuar de forma autónoma mediante los actuadores, reduciendo el daño al cultivo y el costo de remediación.
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
