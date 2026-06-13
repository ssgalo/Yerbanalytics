# Spec: sector-detail

## ADDED Requirements

### Requirement: Diagnóstico de IA del sector
El detalle de sector SHALL mostrar el diagnóstico (estado, severidad, imagen
cenital, confianza con barra) y un aviso cuando la confianza supera el umbral (85%).

#### Scenario: Diagnóstico concluyente
- **WHEN** la confianza del modelo es ≥ 85%
- **THEN** se muestra el aviso de que el motor de reglas puede actuar

### Requirement: Estado de actuadores
El detalle SHALL mostrar el estado de electroválvula (riego), bomba peristáltica
(insumo) y mediasombra, con indicador de actividad.

#### Scenario: Actuador activo
- **WHEN** un actuador está operando (p. ej. electroválvula regando)
- **THEN** su fila muestra el estado y un indicador de actividad encendido

### Requirement: Métricas y tendencias
El detalle SHALL mostrar 5 tiles de métricas (humedad sustrato, humedad ambiental,
temperatura, CE, UV) con valor, sparkline y rango óptimo, más un gráfico principal
de humedad de sustrato con selector de rango (24h / 7d / 30d).

#### Scenario: Cambio de rango temporal
- **WHEN** el usuario cambia el rango del gráfico principal
- **THEN** la serie se recalcula para ese rango

### Requirement: Seguimiento post-acción e historial
El detalle SHALL mostrar el seguimiento antes/ahora cuando hubo acciones, y el
historial inalterable de acciones del sector (lectura → decisión → acción).

#### Scenario: Sector sano sin seguimiento
- **WHEN** el sector está sano (sin acciones recientes)
- **THEN** se muestra el mensaje de que opera dentro de parámetros

### Requirement: Navegación de retorno
El detalle SHALL ofrecer un botón "Volver" que regresa a la vista previa.

#### Scenario: Volver a la vista anterior
- **WHEN** el usuario hace clic en "Volver"
- **THEN** regresa a la vista desde la que abrió el sector
