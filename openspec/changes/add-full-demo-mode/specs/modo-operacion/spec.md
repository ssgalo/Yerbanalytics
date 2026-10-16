## ADDED Requirements

### Requirement: Configuración de Modo de Operación y Feature Flag
El backend SHALL permitir configurar el modo de operación mediante la propiedad `yerbanalytics.mode` (con valores `REAL` o `SIMULATED`, siendo `REAL` el valor por defecto) y la propiedad booleana complementaria `yerbanalytics.demo.enabled` (default `false`). Ambas propiedades SHALL admitir sobrescritura por variables de entorno nativas de Spring Boot (`YERBANALYTICS_MODE` y `YERBANALYTICS_DEMO_ENABLED`), facilitando su parametrización en contenedores Docker y Docker Compose sin reconstruir imágenes.

#### Scenario: Modo Real por defecto
- **GIVEN** una instancia del backend arrancada sin variables de entorno adicionales
- **WHEN** se inspecciona la configuración activa
- **THEN** `yerbanalytics.mode` es `REAL`, `yerbanalytics.demo.enabled` es `false`, y los endpoints bajo `/api/demo/**` no se encuentran disponibles (retornan 404)

#### Scenario: Activación de Modo Simulado vía Variable de Entorno
- **GIVEN** el backend iniciado con la variable de entorno `YERBANALYTICS_MODE=SIMULATED` o `YERBANALYTICS_DEMO_ENABLED=true`
- **WHEN** un cliente autenticado realiza peticiones a `/api/demo/**`
- **THEN** los servicios de demo y sus endpoints responden normalmente

### Requirement: Cero Rastro en Modo Real
En modo `REAL`, el sistema SHALL garantizar que no exista ningún comportamiento alterado ni consumo de recursos derivado de las capacidades de demo. Los beans asociados a demo (`DemoRuleExecutionPolicy`, `SimulatedWeatherClient`, `DemoController`) SHALL NOT registrarse en el contexto de Spring (`@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")`), y ninguna regla del motor podrá ser bypaseada.

#### Scenario: Petición a endpoint demo en modo Real
- **GIVEN** el backend corriendo en modo `REAL`
- **WHEN** se realiza una petición HTTP `GET /api/demo/rules`
- **THEN** el servidor responde 404 Not Found
