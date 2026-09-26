## MODIFIED Requirements

### Requirement: Paneles laterales y de actividad
El Panel general SHALL mostrar: atención prioritaria (sectores críticos/alerta),
clima y riesgo (temp, UV, pronóstico en base a datos reales de Open-Meteo), feed de actividad del sistema (obtenido desde el historial persistido real), y diagnósticos recientes (mostrando las imágenes reales de las capturas fotográficas). 
Si no hay actividad reciente, el feed de actividad SHALL mostrar un estado vacío (empty state) graceful. Si no hay diagnósticos recientes, SHALL mostrar un estado vacío.

#### Scenario: Pulso en críticos
- **WHEN** un sector prioritario es crítico
- **THEN** su indicador usa la animación de pulso (`ybPulse`)

#### Scenario: Imágenes reales en diagnósticos
- **WHEN** un diagnóstico reciente contiene una url de captura fotográfica real (`imagenUrl`)
- **THEN** el panel la renderiza como fondo en la tarjeta en lugar de usar un gradiente de color genérico

#### Scenario: Sin actividad del sistema
- **WHEN** el historial de acciones del sistema está vacío (ej. al inicio de ciclo)
- **THEN** el feed de actividad muestra el mensaje "Sin actividad registrada — el motor aún no ejecutó acciones en este ciclo."
