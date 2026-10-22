## ADDED Requirements

### Requirement: Botón de happy path en la Demo Expo
La pestaña *Demo Expo* del simulador SHALL ofrecer un botón que evalúe riego, insumo y mediasombra en un único pulso
con motor sobre el primer sector de la primera macro-zona con sectores, indicando qué sector es y qué modo usará.

#### Scenario: Deshabilitado mientras hay otra operación
- **WHEN** hay una pasada del riel o una secuencia en curso, o ya se está ejecutando el happy path
- **THEN** el botón no dispara una nueva corrida

### Requirement: Modo Simulador
Con la fuente de telemetría en `SIMULATOR`, el simulador SHALL generar una lectura coherente a partir del catálogo vivo
de umbrales para que las tres reglas actúen.

#### Scenario: Las tres reglas disparan
- **WHEN** se ejecuta el happy path con la fuente en Simulador
- **THEN** la humedad de sustrato queda entre el umbral crítico y el de riego, la temperatura deja el sector crítico, el clima simulado tiene UV sobre `mediasombra.uv-umbral`, se registra un diagnóstico de IA con confianza sobre la mínima antes del pulso, los cooldowns quedan en 0, R-05 se saltea y se pide un único pulso con motor

### Requirement: Modo ESP32 real
Con la fuente en `REAL_NODE`, el simulador SHALL NOT inyectar lecturas, diagnósticos, cooldowns ni bypasses.

#### Scenario: Clima simulado previo
- **WHEN** hay un clima simulado activo y se ejecuta el happy path con ESP32 real
- **THEN** se elimina el clima simulado y el modo "sin pronóstico" antes del pulso

#### Scenario: Sin diagnóstico utilizable
- **WHEN** el sector no tiene un diagnóstico anómalo con confianza suficiente
- **THEN** se advierte que la regla de insumo se omitirá y aun así se dispara el pulso

### Requirement: Resultado por regla
Tras el pulso el simulador SHALL mostrar, leyendo la traza del motor del sector, si riego, insumo y mediasombra actuaron y el motivo.

#### Scenario: Sin traza nueva
- **WHEN** el motor no publica una traza distinta de la previa dentro del plazo
- **THEN** se informa que no se pudo leer el resultado en lugar de mostrar una traza vieja

### Requirement: Aislamiento
El cambio SHALL usar sólo endpoints públicos por `/backend/**` y NOT modificar el backend ni el dashboard productivo.
