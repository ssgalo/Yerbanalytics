## 1. Objetivos

### Objetivo general

Diseñar e implementar un sistema inteligente de monitoreo, diagnóstico y actuación para plantines de yerba mate (Ilex paraguariensis) en una unidad de producción de alta densidad, integrando sensores, visión computacional, inteligencia artificial, automatización de actuadores y un panel web centralizado, con el fin de optimizar la sanidad, la nutrición, el riego y el proceso de rustificación de los plantines.

### Objetivos específicos

- **Monitorear variables críticas del sustrato y del ambiente.** Capturar de manera periódica humedad del sustrato, temperatura, humedad ambiental, niveles de nutrientes y radiación/luminosidad, para conocer el estado fisiológico de los plantines y del entorno inmediato.
- **Detectar de forma temprana anomalías propias de la yerba mate.** Analizar imágenes de los plantines mediante un modelo de inteligencia artificial entrenado específicamente para identificar estados relevantes del cultivo, como plantín sano, estrés solar o quemadura, clorosis asociada a déficit nutricional, estrés hídrico, daño fúngico y síntomas compatibles con plagas foliares tempranas.
- **Automatizar decisiones agronómicas sobre sectores específicos.** Ejecutar acciones correctivas localizadas, como riego por microaspersión, dosificación de nutrientes o fitosanitarios por línea de irrigación y ajuste de mediasombra, según las condiciones detectadas por sensores, visión artificial y datos climáticos externos.
- **Implementar una lógica de rustificación controlada.** Gestionar la transición gradual de los plantines desde condiciones de alta protección hacia un nivel progresivo de exposición lumínica, con el fin de fortalecer su estructura antes del trasplante.
- **Centralizar la información y la trazabilidad del sistema.** Proveer una plataforma web donde el productor pueda visualizar métricas, diagnósticos, alertas, acciones ejecutadas y su evolución temporal, generando historial técnico para seguimiento y mejora continua.
- **Integrar información externa de clima para mejorar la toma de decisiones.** Incorporar pronósticos y variables meteorológicas de APIs externas para anticipar condiciones de riesgo (lluvia inminente, picos de radiación UV) que alimenten el algoritmo de toma de decisiones, evitando tanto la saturación hídrica accidental del sustrato como la exposición solar no planificada durante la rustificación.
- **Reducir el uso ineficiente de agua e insumos.** Ejecutar acciones únicamente sobre las zonas que lo requieran, evitando el tratamiento indiscriminado de toda la superficie y mejorando la eficiencia operativa y económica.

## 2. Límites del proyecto

### 2.1. Incluido en el proyecto

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
- Bot de Telegram para envío de notificaciones y alertas?

### 2.2. No incluido en el proyecto

- Monitoreo de plantaciones adultas de yerba mate en campo abierto.
- Automatización de grandes extensiones agrícolas o lotes a cielo abierto.
- Riego por goteo o infraestructura de riego para terreno abierto.
- Automatización de mallas o sistemas de protección pensados para plantas adultas en suelo.
- Gestión de otras especies vegetales fuera de yerba mate.
- Comercialización de insumos, gestión contable, facturación o ERP agrícola.
- Predicción de rendimiento industrial de cosecha o trazabilidad de la etapa de secado, molienda o empaquetado.
- Integración con maquinaria pesada o vehículos autónomos de campo.
- Automatización de poda, trasplante o cosecha.
- Sistema médico o sanitario para personas.
- Entrenamiento de un modelo universal para todas las enfermedades de todas las plantas.
- Desarrollo de una aplicación móvil nativa (iOS/Android).

## 3. Alcance

### Alcance funcional

Yerbanalytics se enfocará en el desarrollo inicial controlado de plantines de yerba mate, donde el control ambiental, hídrico y nutricional es crítico para asegurar plantines sanos y aptos para el trasplante. El sistema operará sobre bloques homogéneos de producción, donde un conjunto reducido de sensores representará una zona mayor bajo condiciones similares, y donde la visión computacional permitirá una inspección más precisa a nivel visual.

El proyecto abarcará el ciclo completo de monitoreo y respuesta dentro de esta etapa:

- recolección de datos,
- análisis por IA,
- decisión automatizada,
- ejecución de actuadores,
- registro de la acción realizada,
- visualización del resultado en la plataforma web.

### Alcance técnico

El sistema incluirá:

- Capa de sensado para humedad de sustrato, temperatura, humedad ambiental, nutrientes y radiación/luz.
- Capa de visión para captura y análisis de imágenes de plantines.
- Capa de inteligencia para clasificación del estado sanitario y fisiológico.
- Capa de actuación para riego, fertirriego, aplicación localizada y control de mediasombra.
- Capa de gestión para trazabilidad, historial y monitoreo remoto.

### Alcance de negocio

El proyecto apunta a resolver un problema real y específico de la producción de yerba mate en etapa inicial: evitar pérdidas por estrés hídrico, daño fúngico, quemaduras, deficiencias nutricionales y manejo ineficiente de insumos. La propuesta busca diferenciarse de un sistema genérico de cuidado de plantas al trabajar con:

- una especie concreta,
- un entorno productivo definido,
- patologías y respuestas agronómicas propias de la yerba mate,
- y una lógica de automatización pensada para rustificación y sanidad de plantines, no para jardinería general.

### Alcance operativo

La solución será aplicable a sectores de producción homogéneos donde la repetición del patrón de cultivo permita inferir el estado de bloques completos a partir de sensores representativos. La operación contemplará una arquitectura escalable para cubrir unidades de producción de entre 50.000 y 100.000 plantines, organizados en sectores de 100 tubetes cada uno (equivalente a una superficie de aproximadamente 1 m² por sector, cubierta por un único microaspersor). Para una instalación de referencia de 100.000 plantines, la topología comprende:

- 1.000 sectores de actuación, cada uno gobernado por un microaspersor individual dentro de macro-zonas hidráulicas gestionadas por electroválvulas maestras.
- 10 nodos sensores testigo, uno por macro-zona, que infieren el estado del sustrato de los 100 sectores circundantes gracias a la homogeneidad microclimática del entorno de cría controlada.
- 1 sistema Gantry de visión computacional que recorre secuencialmente los 1.000 sectores a lo largo de la jornada, entregando diagnóstico visual de alta granularidad sin requerir unidades de cámara adicionales.
