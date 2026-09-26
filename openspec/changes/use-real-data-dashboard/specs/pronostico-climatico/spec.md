## MODIFIED Requirements

### Requirement: Obtención del pronóstico con retry y fallback

El backend SHALL obtener el pronóstico climático vigente de la API externa real de Open-Meteo antes de cada ciclo de
evaluación del motor, abandonando las respuestas de mock utilizadas en etapas tempranas. SHALL reintentar hasta 3 veces con backoff exponencial
(1 s → 2 s → 4 s) si la API no responde. Si todos los reintentos fallan, SHALL
operar en modo degradado (`forecast = null`) sin bloquear la evaluación. El error
SHALL registrarse como warning en el log.

#### Scenario: Pronóstico obtenido de la API real en el primer intento

- **WHEN** la API climática real de Open-Meteo responde correctamente
- **THEN** el `WeatherService` retorna un `WeatherForecast` con datos verídicos de `probLluviaPct`
  e `uvIndex` válidos

#### Scenario: API no disponible tras 3 reintentos

- **WHEN** la API climática falla en los 3 intentos consecutivos
- **THEN** `WeatherService.getForecast()` retorna `null` y el motor continúa
  la evaluación usando solo los datos de sensores
