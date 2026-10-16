## ADDED Requirements

### Requirement: Consulta de umbrales agronómicos del backend con fallback a defaults
El simulador SHALL consultar los parámetros y umbrales vigentes del motor de reglas a través del endpoint público `/backend/api/rules/parametros`. Si la llamada falla o el backend no está disponible, el simulador SHALL utilizar un catálogo local de valores de fábrica por defecto para cada parámetro (humedad de riego = 45%, umbral crítico = 35%, saturación bloqueo = 75%, saturación alerta = 80%, probabilidad de lluvia = 70%, lluvia mm = 5 mm, umbral UV = 7).

#### Scenario: Backend disponible y parámetros actualizados
- **WHEN** el usuario ingresa al panel de automatización de reglas con el backend operativo
- **THEN** el simulador obtiene y muestra los umbrales vigentes reales configurados en el sistema

#### Scenario: Backend inaccesible y uso de defaults
- **WHEN** la consulta de parámetros al backend falla o no hay conexión
- **THEN** el simulador utiliza los valores por defecto del catálogo de fábrica sin interrumpir la interfaz

### Requirement: Selección múltiple de reglas agronómicas para ejecución automática
El simulador SHALL presentar una lista interactiva de reglas agronómicas con casillas de verificación (checkboxes), permitiendo al operador seleccionar una o más reglas que desea forzar en la evaluación:
- Riego por déficit hídrico (`RiegoPorDeficitRule` / R-01)
- Déficit hídrico crítico (`DeficitCriticoRule` / R-02)
- Sustrato saturado (`SustratoSaturadoRule` / R-04)
- Posponer por lluvia (`PosponerPorLluviaRule` / R-03)
- Control de mediasombra (`ShadingRule`)
- Dosificación de insumo (`SupplyRule`)

#### Scenario: Selección de una regla
- **WHEN** el usuario marca el checkbox de una regla (ej. Déficit crítico R-02)
- **THEN** el simulador previsualiza los valores calculados de telemetría y clima necesarios para satisfacer su condición

#### Scenario: Detección de incompatibilidades entre reglas
- **WHEN** el usuario selecciona dos reglas con requisitos de humedad contradictorios (ej. R-02 con humedad < 35% y R-04 con humedad >= 75%)
- **THEN** el simulador muestra una advertencia visual de conflicto y sugiere ejecutarlas por separado

### Requirement: Generación y despacho automático de telemetría y clima
Al presionar el botón "Ejecutar reglas seleccionadas", el simulador SHALL orquestar de manera autónoma las siguientes acciones:
1. Si el backend está en modo demo y hay cooldowns activos, ponerlos en 0 llamando a `PUT /backend/api/demo/rules/cooldowns/zero`.
2. Si la regla requiere condiciones meteorológicas (lluvia para R-03 o radiación UV para ShadingRule), inyectar el pronóstico ficticio vía `POST /backend/api/demo/weather`.
3. Publicar directamente al broker MQTT (`nursery/zone/{zonaId}/telemetry`) el payload de telemetría ajustado con los valores requeridos para la macro-zona seleccionada.
4. Si alguna regla seleccionada decide con el diagnóstico de IA (`SupplyRule`), registrar antes del pulso un diagnóstico previo, según el requisito "Diagnóstico de IA previo para reglas que lo consumen".
5. Disparar el pulso de lectura con motor mediante `POST /backend/api/pulsos` con `{ ejecutarMotor: true }`.

#### Scenario: Ejecución de R-01 (Riego por déficit)
- **WHEN** el usuario ejecuta R-01 con umbral crítico en 35% y umbral de riego en 45%
- **THEN** el simulador envía telemetría con humedad de sustrato en un valor intermedio (ej. 40%), sin lluvia ficticia, y dispara el pulso con motor

#### Scenario: Ejecución de R-02 (Déficit crítico)
- **WHEN** el usuario ejecuta R-02 con umbral crítico en 35%
- **THEN** el simulador envía humedad de sustrato inferior al umbral (ej. 28%), resetea el cooldown `cap` en demo, y dispara el pulso con motor

#### Scenario: Ejecución de ShadingRule (Mediasombra por pico UV)
- **WHEN** el usuario selecciona ShadingRule con umbral UV en 7
- **THEN** el simulador inyecta un pronóstico climático con índice UV >= 7 (ej. 8), envía la telemetría del sensor y dispara el pulso con motor

#### Scenario: Colisión por operación en curso
- **WHEN** el disparo del pulso retorna HTTP 409 porque ya hay una pasada o secuencia ejecutándose
- **THEN** el simulador informa claramente que el hardware está ocupado y sugiere reintentar al finalizar

### Requirement: Diagnóstico de IA previo para reglas que lo consumen
Cuando el operador selecciona `SupplyRule`, el simulador SHALL registrar un diagnóstico de IA para un sector de la macro-zona destino ANTES de disparar el pulso, porque el motor decide únicamente en el pulso y la regla actúa una sola vez por diagnóstico nuevo. El diagnóstico SHALL tener estado anómalo, severidad y confianza `Alta` y un score no menor a `diagnostico.confianza-minima` (85 por defecto si el backend no responde), y SHALL registrarse por el camino único del sistema: orden de captura, imagen subida como dispositivo y `POST /backend/api/diagnosticos`, sin endpoints ni marcas propias del simulador. Como la regla solo dosifica con el sector en estado crítico y ese estado sale de las métricas, el simulador SHALL además enviar una temperatura del aire crítica (40 °C) sin alterar la humedad de sustrato. Si la zona destino no tiene sectores, el simulador SHALL bloquear la ejecución e informarlo.

#### Scenario: Ejecución de SupplyRule
- **WHEN** el usuario ejecuta SupplyRule con confianza mínima en 85%
- **THEN** el simulador registra un diagnóstico con confianza ≥ 85% en un sector de la zona destino, y recién después dispara el pulso con motor

#### Scenario: Falla el registro del diagnóstico
- **WHEN** la orden de captura, la subida de la imagen o el alta del diagnóstico fallan
- **THEN** el simulador no dispara el pulso e informa el error en la traza de ejecución

#### Scenario: Zona sin sectores
- **WHEN** la macro-zona del sensor destino no tiene sectores en la topología vigente
- **THEN** el botón de ejecutar queda deshabilitado y se explica el motivo
