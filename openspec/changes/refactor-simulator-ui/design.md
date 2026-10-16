# Design: refactor-simulator-ui

## Context

El simulador de hardware (`Desarrollo/simulador/`) es un proceso independiente (Node/Express en `:5180` con cliente MQTT y proxy inverso a `:8000`) con interfaz React/TypeScript. Cumple el rol de nodo físico ESP32 en el sistema, publicando telemetría en `nursery/zone/{zonaId}/telemetry` y consumiendo la API de plataforma a través de `/backend/**`.

Actualmente:
- La UI se presenta como una única columna continua que acumula topología, emisión automática, alta de sensor, tarjetas de sensores con las 10 métricas, controles de demo (botón "Leer ahora", clima simulado, toggles de reglas) y panel de cámara.
- Para verificar el comportamiento del motor de reglas ante situaciones de estrés (riego por déficit, déficit crítico, saturación, lluvia o picos UV), el usuario debe conocer los umbrales configurados, calcular manualmente qué valores de telemetría o clima enviar, escribir cada campo a mano y disparar el pulso.
- La vista de *Demo Expo* (`/demo-expo`), que controla la pasada del riel y las secuencias de actuadores en vivo, se ubica indebidamente en `Desarrollo/frontend/`, en contradicción con la regla de diseño de `CLAUDE.md` §6.2 que prohíbe interfaces de demo en la aplicación productiva.

## Goals / Non-Goals

### Goals
- **Estructuración en 5 Apartados Funcionales**: Reorganizar la interfaz del simulador mediante una barra de navegación por apartados/pestañas:
  1. *Configuración General* (Topología, Sensores simulados, alta en Hardware del backend, consulta de configuración/umbrales).
  2. *Telemetría y Reglas* (Lectura puntual, envío manual por métrica o todo, emisión automática, control ON/OFF de reglas, bypass de cooldowns, simulación climática).
  3. *Automatización de Reglas* (Selector interactivo con checkboxes de reglas a disparar, cálculo automático de telemetría y clima según umbrales vigentes, orquestación de cooldowns, publicación MQTT y pulso con motor).
  4. *Cámara* (Dispositivo de captura, subida directa auto-simulada, pedido de captura por SSE/polling, registro manual de diagnósticos).
  5. *Demo Expo* (Pasada del riel en vivo paso a paso y secuencias guionadas directas a actuadores de riego, mediasombra y lectura).
- **Disparador Inteligente de Reglas**: Permitir que el operador seleccione una o más reglas a forzar, y que el simulador configure la telemetría adecuada basándose en los umbrales vigentes del backend (`/api/rules/parametros`) o sus valores de fábrica por defecto, ejecute el pulso `LEER_AHORA` y publique la telemetría requerida.
- **Migración Limpia de Demo Expo**: Mudar las funcionalidades de la pasada y las secuencias al simulador, removiendo `/demo-expo` del dashboard de producción (`Desarrollo/frontend/`).
- **Desacoplamiento Absoluto**: Mantener al backend 100% desacoplado del simulador; toda interacción continúa canalizándose por la API pública a través del proxy `/backend/**`.

### Non-Goals
- Modificar el motor de reglas en el backend ni agregar nuevas reglas agronómicas.
- Crear tablas, columnas o endpoints específicos del simulador en el backend.
- Cambiar los contratos MQTT existentes (`contrato.h`, `ContratoNodo.java`, `server/contract.ts`).
- Alterar la lógica interna de la app de captura de cámara (`Desarrollo/camara/` o `camara-android/`).

---

## Decisions

### D1: Navegación de la UI por Apartados (Tabs / Segmented View)

**Decisión**: Implementar una barra de navegación superior (pestañas o control segmentado persistente) con 5 apartados:
1. `configuracion`: Configuración general del vivero y hardware.
2. `telemetria`: Consola interactiva de telemetría, reglas demo y clima.
3. `automatizacion`: Disparador automático de telemetría basado en reglas agronómicas.
4. `camara`: Banco de pruebas de captura cenital y diagnósticos.
5. `demo-expo`: Pasada del riel y secuencias de actuadores.

*Alternativas consideradas*:
- *Scroll vertical único (como hoy)*: Descartado; la adición de Demo Expo y la automatización de reglas alargaría la página excesivamente, degradando la experiencia de demostración.
- *Rutas independientes con react-router*: El simulador es una SPA ligera de una sola página. Un estado de pestaña en memoria (`activeTab: string`) con sincronización en `window.location.hash` (`#configuracion`, `#automatizacion`, etc.) es más liviano, no agrega dependencias pesadas y permite recargar manteniendo la sección activa.

### D2: Arquitectura del Generador y Ejecutor de Telemetría Automática

**Decisión**: Crear un módulo `RuleAutomationEngine` en el frontend del simulador que resuelva el mapeo entre reglas agronómicas y telemetría requerida:

1. **Lectura de Umbrales**:
   - Consulta `GET /backend/api/rules/parametros` para obtener los valores vigentes de los parámetros (`riego.umbral-humedad`, `riego.umbral-critico`, `riego.saturacion-bloqueo`, `riego.lluvia-probabilidad`, `riego.lluvia-mm`, `mediasombra.uv-umbral`, etc.).
   - Si el backend está desconectado o el endpoint no responde, utiliza un catálogo local de valores de fábrica (`DEFAULT_THRESHOLDS`) idéntico a las definiciones de `ParametrosRiego`, `ParametrosMediasombra`, etc.
2. **Matriz de Reglas y Condiciones**:
   - **R-01 (Riego por déficit hídrico)**:
     - Requiere: `umbralCritico <= humSus < umbralHumedad` (ej. si crítico = 35% y umbral = 45%, fija `humSus = 40%`).
     - Clima: Sin lluvia inminente (`probLluvia = 10%`, `precipMm = 0`).
     - Cooldown: Pone cooldown de `CicloLecturaRiegoRule` a 0 en demo.
   - **R-02 (Déficit hídrico crítico)**:
     - Requiere: `humSus < umbralCritico` (ej. `humSus = 28%`).
     - Cooldown: Pone cooldown de tope (`cap`) de R-02 a 0 en demo.
   - **R-04 (Sustrato saturado)**:
     - Requiere: `humSus >= saturacionBloqueo` (ej. `humSus = 78%`) o con alerta `humSus >= saturacionAlerta` (ej. `humSus = 82%`).
   - **R-03 (Posponer por lluvia)**:
     - Requiere: Condición de riego (`humSus = 40%`) Y clima con `probLluviaPct >= lluviaProbabilidad` (ej. 80%) y `precipMm >= lluviaMm` (ej. 8 mm).
   - **ShadingRule (Control de mediasombra)**:
     - Requiere: Clima simulado con `uvIndex >= uvUmbral` (ej. `uvIndex = 8.0`).
   - **SupplyRule (Dosificación de insumos)**:
     - Requiere: Diagnóstico IA en estado `critical` con confianza >= 85% para el sector seleccionado.
3. **Flujo de Ejecución en un Clic**:
   - Al presionar **"Ejecutar reglas seleccionadas"**:
     1. Si está en modo demo y alguna regla tiene cooldowns, envía `PUT /backend/api/demo/rules/cooldowns/zero`.
     2. Si la regla requiere condiciones meteorológicas (lluvia o UV), llama a `POST /backend/api/demo/weather` con los valores calculados.
     3. Para la macro-zona destino (o la asociada al sensor seleccionado), publica el payload de telemetría MQTT directamente al broker vía `POST /api/telemetry`.
     4. Dispara el pulso de lectura con motor llamando a `POST /backend/api/pulsos { ejecutarMotor: true }`.
     5. Presenta una tarjeta de resultado detallando las métricas emitidas, las condiciones aplicadas y el estado del pulso.

*Alternativas consideradas*:
- *Que el backend ejecute la lógica de generación*: Descartado categóricamente. Violaría la regla de oro: *"El backend no conoce al simulador"*. El cálculo del escenario pertenece exclusivamente al simulador.

### D3: Sincronización y Configuración General con el Backend

**Decisión**: Enriquecer el apartado de Configuración General:
- **Topología**: Mantener la regeneración (`POST /api/topologia`) indicando macro-zonas y sectores.
- **Sensores Simulados y Flota de Hardware**:
  - Al dar de alta un sensor simulado (Serial/MAC + Macro-zona), ofrecer un checkbox opcional: *"Registrar también en la flota de Hardware del backend (`POST /api/hardware`)"*.
  - Esto evita la molestia manual de tener que ir al dashboard productivo a registrar el dispositivo para que el nodo aparezca conectado y con métricas técnicas.
  - El simulador utiliza el endpoint público existente `POST /api/hardware` a través de su proxy.
- **Visualización de Configuración**:
  - Pestaña informativa que muestra los parámetros y umbrales vigentes del sistema (`/api/rules/parametros`) para que el usuario sepa con qué valores está decidiendo el motor en ese momento.

### D4: Mudanza de Demo Expo al Simulador y Limpieza del Frontend

**Decisión**:
1. **En `Desarrollo/simulador/`**:
   - Crear el componente `DemoExpoSection` conteniendo:
     - `PasadaPanel`: consume `/backend/api/pasadas/**` para iniciar, cancelar y ver el avance visual paso a paso de la pasada del riel (con fotos y diagnósticos).
     - `SecuenciasPanel`: consume `/backend/api/secuencias/**` para comandar Riego (segundos), Mediasombra (espera desplegada) y Lectura de sensores (con o sin motor).
   - Reutilizar la lógica de estado de los hooks `usePasada` y `useSecuencia` adaptados a las llamadas al proxy del simulador (`/backend`).
2. **En `Desarrollo/frontend/`**:
   - Eliminar `DemoExpoPage.tsx`, sus subcomponentes en `features/demo-expo/` y sus tests.
   - Eliminar la ruta `/demo-expo` de `router.tsx`.
   - Eliminar el ítem "Demo Expo" de `Sidebar.tsx`.
   - Eliminar el interruptor de Demo Expo de `PreferencesPage.tsx` y `DemoExpoSwitch.tsx`.
   - Limpiar las referencias en `DataRepository` que eran exclusivas de la vista del frontend si no son requeridas por otras partes de la app.

*Permisos del Backend*:
- El endpoint `/api/pasadas/**` y `/api/secuencias/**` requiere el permiso `PASADAS_OPERAR` en `MapaPermisos.java`.
- Dado que el simulador se autentica con la cuenta `simulador` (rol `SERVICIO`), el rol `SERVICIO` debe poseer `PASADAS_OPERAR` y `PASADAS_VER` en la matriz de permisos para operar pasadas y secuencias sin requerir intervención manual en la base. Documentamos la verificación y configuración de este permiso.

### D5: Estilo Visual y Consistencia

**Decisión**: Mejorar `styles.css` del simulador manteniendo su principio de diseño:
- El simulador mantiene estilos propios y desacoplados del sistema de diseño del dashboard productivo (no importa tokens ni CSS Modules del frontend).
- Se moderniza la paleta con acentos nítidos, tipografía limpia, estados visuales claros (verde éxito, ámbar alerta, rojo crítico, gris neutro) y tarjetas con bordes suaves (`border-radius: 10px`), headers consistentes y feedback visual inmediato ante acciones asíncronas.

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| **[Riesgo]** El backend tiene `PASADAS_OPERAR` restringido y rechaza llamadas de Demo Expo desde la cuenta de servicio con 403. | **Mitigación**: Se documenta la asignación de permisos `PASADAS_OPERAR` y `PASADAS_VER` al rol `SERVICIO` o mediante la matriz en desarrollo. La UI del simulador muestra claramente el mensaje de error del backend en caso de 403. |
| **[Riesgo]** Las reglas agronómicas tienen umbrales editados por el usuario y el simulador usa valores desactualizados. | **Mitigación**: El disparador automático consulta siempre `/backend/api/rules/parametros` en tiempo real antes de calcular la telemetría, y sólo recurre a los defaults de fábrica si la llamada falla. |
| **[Riesgo]** Múltiples reglas seleccionadas tienen requisitos contradictorios de humedad (ej. R-02 < 35% y R-04 >= 75%). | **Mitigación**: El selector de reglas valida compatibilidad: si se seleccionan reglas con requisitos mutuamente excluyentes, la UI alerta al operador y sugiere ejecutarlas secuencialmente o prioriza la regla más restrictiva. |
| **[Riesgo]** Un pulso de lectura manual falla por colisión (409) con una pasada o secuencia en curso. | **Mitigación**: El simulador captura el código HTTP 409 y despliega un aviso amigable: *"Ya hay una operación de hardware en curso. Esperá que termine"*. |

---

## Migration Plan

1. **Fase 1: Preparación de la API en el Simulador**:
   - Ampliar `Desarrollo/simulador/src/api.ts` con clientes para `/api/pasadas/**`, `/api/secuencias/**`, `/api/rules/parametros` y `/api/hardware`.
2. **Fase 2: Componentes Nuevos del Simulador**:
   - Implementar la barra de navegación por apartados (`NavigationTabs.tsx`).
   - Implementar el módulo de Configuración General ampliado (`GeneralConfigSection.tsx`).
   - Reorganizar el panel de Telemetría, Reglas y Clima (`TelemetryRulesSection.tsx`).
   - Construir el panel de Automatización de Reglas (`RuleAutomationSection.tsx`).
   - Construir la sección de Demo Expo en el simulador (`DemoExpoSection.tsx`).
3. **Fase 3: Limpieza del Frontend Productivo**:
   - Retirar la vista `/demo-expo`, enlaces del sidebar y switch de preferencias en `Desarrollo/frontend/`.
   - Ejecutar la suite de tests y linter del frontend para asegurar cero regresiones.
4. **Fase 4: Validación Integral**:
   - Verificar compilación y tests del backend (`./mvnw test`).
   - Verificar compilación y tests del simulador (`npm test` y typecheck).
   - Probar flujo punta a punta en el navegador (`http://localhost:5180`).
