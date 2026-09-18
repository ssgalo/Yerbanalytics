# pronostico-climatico

> **Estado de implementación:** ✅ Implementado completamente.
> Clases: `WeatherClient` (interfaz), `OpenMeteoWeatherClient`, `WeatherService` (cache `AtomicReference` + retry backoff), `WeatherForecast` (record). `ClimaOverrideRule` y `MediasombraRule` consumen el forecast.

## Purpose

Integración del pronóstico meteorológico externo como insumo de decisión del motor
de reglas (HU-09). El `WeatherService` consulta la API climática con retry
exponencial y cache thread-safe, exponiéndolo al `RuleContext`. La `ClimaOverrideRule`
usa ese pronóstico para posponer el riego cuando hay lluvia inminente y ajustar
la mediasombra cuando el índice UV es elevado.

## Requirements

### Requirement: Obtención del pronóstico con retry y fallback

El backend SHALL obtener el pronóstico climático vigente antes de cada ciclo de
evaluación del motor. SHALL reintentar hasta 3 veces con backoff exponencial
(1 s → 2 s → 4 s) si la API no responde. Si todos los reintentos fallan, SHALL
operar en modo degradado (`forecast = null`) sin bloquear la evaluación. El error
SHALL registrarse como warning en el log.

#### Scenario: Pronóstico obtenido en el primer intento

- **WHEN** la API climática responde correctamente
- **THEN** el `WeatherService` retorna un `WeatherForecast` con `probLluviaPct`
  e `uvIndex` válidos

#### Scenario: API no disponible tras 3 reintentos

- **WHEN** la API climática falla en los 3 intentos consecutivos
- **THEN** `WeatherService.getForecast()` retorna `null` y el motor continúa
  la evaluación usando solo los datos de sensores

### Requirement: Cache thread-safe del pronóstico

El `WeatherService` SHALL mantener una cache del último pronóstico válido.
Si el pronóstico en cache es fresco (dentro del TTL configurado), SHALL retornarlo
directamente sin llamar a la API. La cache SHALL ser thread-safe para soportar el
trigger reactivo (MQTT) y el Watchdog proactivo ejecutándose en paralelo.

#### Scenario: Hit de cache

- **WHEN** se solicita el pronóstico y la cache contiene un `WeatherForecast`
  dentro del TTL configurado
- **THEN** el servicio lo retorna directamente sin llamar a la API

#### Scenario: Miss de cache

- **WHEN** la cache está vacía o el pronóstico expiró el TTL
- **THEN** el servicio llama a la API externa con retry

### Requirement: Postergación de riego por lluvia inminente

La `ClimaOverrideRule` SHALL posponer el riego autónomo cuando la probabilidad de
lluvia en el pronóstico supera el umbral configurado (`lluvia-umbral-pct`, default 60%).
La postergación SHALL ser bloqueante para `RiegoRule` pero SHALL permitir que
`InsumoRule` y `MediasombraRule` continúen evaluándose.

#### Scenario: Lluvia inminente pospone riego

- **WHEN** `probLluviaPct` del forecast supera o iguala el umbral configurado
- **THEN** la regla emite `POSTPONE_RIEGO` y el `ActionExecutor` registra en el
  historial el motivo de postergación sin activar la válvula

#### Scenario: Lluvia bajo umbral no altera el riego

- **WHEN** `probLluviaPct` está por debajo del umbral
- **THEN** la regla emite `NOOP_INFO` y la evaluación de `RiegoRule` continúa

#### Scenario: Sin pronóstico no se bloquea el riego

- **WHEN** `forecast` es `null` (modo degradado)
- **THEN** la regla emite `NOOP_INFO` indicando degradación y `RiegoRule` evalúa
  normalmente

### Requirement: Reducción de mediasombra ante pico UV

La `MediasombraRule` SHALL reducir la apertura de la mediasombra hasta una posición
protectora (máximo 30%, limitado por `mediasombraAperturaMaxPct`) cuando el índice
UV del pronóstico supera el umbral configurado (`uv-umbral`, default 7.0). Esta
condición SHALL tener precedencia sobre el plan de rustificación.

#### Scenario: Pico UV activa posición protectora

- **WHEN** `forecast.uvIndex()` supera o iguala el umbral UV configurado y la
  apertura actual del sector es mayor a la protectora
- **THEN** la regla emite `MOVER_MEDIASOMBRA` al porcentaje protector

#### Scenario: Mediasombra ya en posición protectora

- **WHEN** pico UV activo y la apertura actual ya está en la posición protectora
- **THEN** la regla emite `NOOP_INFO` sin cambiar el actuador

### Requirement: Configuración via properties

Los parámetros críticos del servicio climático SHALL ser configurables sin
recompilar, via variables de entorno inyectadas por Spring:

| Property | Default | Descripción |
|---|---|---|
| `yerbanalytics.weather.cache-ttl-ms` | 900000 (15 min) | TTL de la cache del pronóstico |
| `yerbanalytics.weather.max-retries` | 3 | Reintentos ante fallos de API |
| `yerbanalytics.weather.retry-base-ms` | 1000 | Espera base del backoff exponencial |
| `yerbanalytics.engine.lluvia-umbral-pct` | 60.0 | Umbral de probabilidad de lluvia |
| `yerbanalytics.engine.uv-umbral` | 7.0 | Umbral de índice UV para protección |

#### Scenario: Cambiar umbral de lluvia via property

- **WHEN** se configura `yerbanalytics.engine.lluvia-umbral-pct=40.0`
- **THEN** la regla pospone el riego al detectar una probabilidad de lluvia ≥ 40%

### Requirement: Swappeable sin tocar las reglas

El cliente de API climática SHALL implementar la interfaz `WeatherClient`, de modo
que el `WeatherService` no dependa de la implementación concreta. Esto permite
reemplazar `OpenMeteoWeatherClient` por otro proveedor o por un mock en tests sin
modificar ni el servicio ni las reglas.

#### Scenario: Cliente intercambiable

- **WHEN** se reemplaza `OpenMeteoWeatherClient` por otra implementación de `WeatherClient`
- **THEN** el `WeatherService` y las reglas funcionan sin cambio
