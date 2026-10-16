# Proposal: refactor-simulator-ui

## Why

El simulador de hardware (`Desarrollo/simulador/`) es la herramienta central para pruebas ciberfísicas y demostraciones de Yerbanalytics, permitiendo emular nodos ESP32, inyectar telemetría y ejercitar la cámara y el motor de reglas sin hardware físico. Sin embargo, su interfaz actual presenta limitaciones operativas:

1. **Falta de modularización visual y ergonomía**: Todos los paneles conviven verticalmente sin una navegación organizada por secciones o pestañas temáticas, dificultando su uso durante pruebas y demostraciones.
2. **Desconexión con la configuración del backend**: El simulador maneja sensores simulados locales pero no facilita alinear de manera directa la flota con el registro de hardware (`/api/hardware`) ni consultar los umbrales y topología vigentes del backend.
3. **Flujo manual y propenso a errores para inducir reglas**: Actualmente, para demostrar que una regla del motor se dispara (por ejemplo, *Déficit Crítico R-02* o *Sustrato Saturado R-04*), el operador debe recordar manualmente los umbrales configurados en el backend, calcular valores válidos de humedad o clima, ingresar cada número en el formulario del sensor, verificar que no haya cooldowns bloqueantes y presionar "Leer ahora". Se necesita un mecanismo automatizado con selección por checkboxes (análogo a la lista de control de reglas) que configure la telemetría idónea según los umbrales vigentes del backend y ejecute el pulso automáticamente.
4. **Acoplamiento de demo en el frontend productivo**: La sección *Demo Expo* (`/demo-expo`), que dispara la pasada del riel y secuencias de actuadores en vivo, reside actualmente en `Desarrollo/frontend/`. Esto viola el principio arquitectónico fundamental de `CLAUDE.md` (§4 y §6.2): *"Toda funcionalidad y control de demo vive exclusivamente acá [en el simulador], NUNCA en la app productiva"*.

Reorganizar la UI del simulador en apartados estructurados, incorporar el disparador automático de telemetría por reglas y trasladar Demo Expo al simulador cumple estrictamente con el desacoplamiento arquitectónico y maximiza la potencia demostrativa del sistema.

## What Changes

- **Estructuración de la UI del Simulador en 5 Apartados Claros**:
  - **Apartado 1: Configuración General**:
    - Visualización y regeneración de topología del vivero (`/api/topologia`).
    - Gestión de sensores simulados (alta con serial/MAC y macro-zona asociada a la topología).
    - Alta directa o sincronización con el registro de hardware del backend (`/api/hardware`) para que el nodo testigo figure en la flota con su estado técnico (batería, señal, heartbeat).
    - Visualización de parámetros y umbrales operativos vigentes consultados desde el backend (`/api/rules/parametros` y `/api/configuracion`).
  - **Apartado 2: Telemetría, Reglas y Clima**:
    - Disparo de pedido de telemetría a nodos (comando `LEER_AHORA` vía `/api/pulsos` o secuencia de lectura).
    - Formulario de envío manual de telemetría por métrica o en bloque (10 métricas, batería, señal, timestamp).
    - Control de emisión automática periódica de telemetría para actividad de fondo.
    - Control de reglas demo: lista interactiva con interruptores ON/OFF (`/api/demo/rules`), ajuste de cooldowns por regla (`cooldownMinutes`), variables de bypass (como el tope de déficit crítico) y botón "Todos los CDs en 0".
    - Simulación de clima: inyección de pronóstico meteorológico ficticio en memoria (`/api/demo/weather`) con temperatura, humedad relativa, probabilidad de lluvia, precipitación y radiación UV, más botón para restablecer a Open-Meteo.
  - **Apartado 3: Automatización de Telemetría para Ejecución de Reglas**:
    - Panel interactivo con selector múltiple (checkboxes) de reglas agronómicas ejecutables:
      - R-01: Riego por déficit hídrico (`RiegoPorDeficitRule`).
      - R-02: Déficit hídrico crítico (`DeficitCriticoRule`).
      - R-04: Sustrato saturado con bloqueo y alerta (`SustratoSaturadoRule`).
      - R-03: Posponer riego por lluvia inminente (`PosponerPorLluviaRule`).
      - Shading: Control de mediasombra por pico UV (`ShadingRule`).
      - Supply: Dosificación de insumo por diagnóstico IA crítico (`SupplyRule`).
    - Lectura inteligente de los umbrales configurados en el backend (`/backend/api/rules/parametros`) con valores de fábrica (defaults) como fallback robusto.
    - Generador automático de payload de telemetría y condiciones ambientales idóneas según las reglas seleccionadas.
    - Ejecución orquestada en un clic: reseteo opcional de cooldowns en modo demo (`/api/demo/rules/cooldowns/zero`), inyección de clima si aplica (`/api/demo/weather`), publicación de la telemetría ajustada al broker MQTT (`nursery/zone/{zonaId}/telemetry`), y disparo del pulso de sensado con motor (`POST /api/pulsos { ejecutarMotor: true }`).
    - Panel de feedback con resumen de la telemetría inyectada y resultado de la evaluación.
  - **Apartado 4: Sección de la Cámara**:
    - Monitoreo del dispositivo de captura enrolado (estado, heartbeat, métricas de éxito/error).
    - Generación de código de vinculación para nuevas cámaras.
    - Subida directa auto-simulada (permite subir imágenes locales directamente asignadas a un sector, simulando ser el dispositivo).
    - Emisión y seguimiento en tiempo real de órdenes de captura por SSE/polling (`/api/capturas/ordenes`).
    - Carga manual de diagnóstico para ejercitar el pipeline previo al modelo de visión (`/api/diagnosticos`).
  - **Apartado 5: Demo Expo (Migrada desde el Frontend)**:
    - Traslado completo de la experiencia de Demo Expo al simulador:
      - Pasada del riel en vivo: iniciar (`POST /api/pasadas`), cancelar (`POST /api/pasadas/actual/cancelar`), seguimiento del riel paso a paso por los dos sectores, captura y diagnóstico de IA en vivo.
      - Secuencias directas de actuadores: tarjetas de comando guionado para Riego (apertura programada en segundos), Mediasombra (despliegue y enrollado) y Lectura de sensores (con o sin motor de reglas), con visualización de progreso y exclusión mutua de hardware (código 409).
- **Limpieza del Frontend Productivo (`Desarrollo/frontend/`)**:
  - **BREAKING (Frontend UI)**: Eliminación de la vista `/demo-expo`, remoción del ítem "Demo Expo" en el sidebar (`Sidebar.tsx`), y remoción del interruptor en Preferencias (`PreferencesPage.tsx` / `DemoExpoSwitch.tsx`).
  - El frontend de producción queda 100% libre de controles de demostración y botones de comando manual.
- **Invariante de Desacoplamiento del Backend**:
  - El backend NO agrega ningún endpoint exclusivo ni tabla nueva para el simulador. Consume únicamente endpoints existentes y públicos de la plataforma a través del proxy `/backend/**`.

## Capabilities

### New Capabilities
- `simulador-ejecucion-reglas-automatica`: Capacidad del simulador para consultar umbrales del catálogo del backend, seleccionar reglas agronómicas mediante checkboxes, generar la telemetría y clima apropiados, y disparar de forma automática el pulso de lectura con motor y publicación MQTT.
- `simulador-demo-expo`: Capacidad del simulador para hospedar la consola de Demo Expo (pasada del riel y secuencias directas de actuadores), operando mediante el proxy del simulador contra la API pública del backend.

### Modified Capabilities
- `simulador-standalone`: La UI del simulador se reestructura en 5 apartados funcionales independientes (Configuración general, Telemetría y reglas, Automatización de reglas, Cámara y Demo Expo), manteniendo su propia arquitectura sin dependencias del diseño del dashboard.
- `demo-expo`: La especificación de Demo Expo se traslada para regir sobre el simulador de hardware en lugar del dashboard del frontend productivo.
- `app-shell`: Se actualiza la especificación del shell de navegación del dashboard eliminando el enlace y estado condicional de Demo Expo.

## Impact

- **Simulador (`Desarrollo/simulador/`)**:
  - Nuevos componentes en `src/components/`: navegación por apartados/pestañas, panel de configuración general ampliado, panel de automatización de reglas (`RuleExecutionPanel`), y panel de Demo Expo (`DemoExpoSection`, `PasadaPanel`, `SecuenciasDirectasPanel`).
  - Ampliación de `src/api.ts` para cubrir endpoints de pasadas (`/api/pasadas/**`), secuencias (`/api/secuencias/**`), catálogo de parámetros (`/api/rules/parametros`) y hardware (`/api/hardware/**`).
  - Actualización de estilos en `src/styles.css` con estética moderna, limpia y modular.
- **Frontend (`Desarrollo/frontend/`)**:
  - Eliminación de la ruta `/demo-expo` en `router.tsx`.
  - Eliminación del enlace condicional en `Sidebar.tsx`.
  - Eliminación del interruptor en `PreferencesPage.tsx`.
  - Eliminación de la carpeta `src/features/demo-expo/` y sus tests asociados en el frontend.
- **Backend (`Desarrollo/backend/`)**:
  - Cero cambios de código ni de esquema: no se modifican controladores, ni entidades, ni reglas. El backend continúa exponiendo sus endpoints públicos y de demo exactamente igual.
