# Design: add-operacion-sin-internet

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| Pronóstico | `be/engine/weather/`: `WeatherClient`, `OpenMeteoWeatherClient` (refresco por hilo), `WeatherService` (caché), `WeatherForecast` |
| R-03 sin pronóstico | `PosponerPorLluviaRule`: comparaciones `SIN_DATO`, no pospone ⇒ **O-01 se cumple** |
| Despacho de riego | `DespachoRiego` revalida R-03 al abrir (`lluviaPospone`); sin pronóstico, no pospone |
| Vigencia del pronóstico | Verificado en `WeatherService`: TTL de caché 15 min (`yerbanalytics.weather.cache-ttl-ms=900000`); el pronóstico vencido se sigue usando hasta `FACTOR_USABLE_VENCIDO = 4` TTL (60 min) y, pasado eso, `getForecastSinEspera()` devuelve `null`. O sea, **ya existe una vigencia de 1 h**; no hay un parámetro nuevo que crear |
| Pronóstico simulado | `setMockForecast` lo devuelve siempre, sin vencimiento; el simulador no puede hoy simular su ausencia (se agrega en `add-control-demo-reglas`) |
| Aviso de pronóstico caído | Ninguno; el cliente sólo lo advierte en un comentario |
| M-02, P-D3, P-D4 | No existen todavía (otros cambios) |
| Sincronización | *No verificado:* no hay cola local de eventos. La arquitectura asume el backend en el vivero (la LAN y el broker son locales) y el pronóstico como única dependencia externa |

## Decisiones

### D-1 — Modo sin pronóstico explícito
`WeatherService.estado()` ⇒ `DISPONIBLE` (en caché y usable: hoy ≤ 4 × TTL = 60 min), `VENCIDO` (hubo, pero ya no es usable) o `AUSENTE` (nunca
hubo). Se **reutiliza** la vigencia existente (`cache-ttl-ms` × `FACTOR_USABLE_VENCIDO`); no se crea `pronostico.vigencia-max`. El
`RuleContext.forecast()` ya llega `null` cuando no es usable (verificado), así que ninguna regla usa un dato viejo: lo nuevo es **nombrar el estado**,
avisarlo y garantizar el supuesto de cada regla.

### D-2 — Supuesto seguro declarado por la regla
`Rule` suma `default String supuestoSinPronostico()` (vacío si no usa pronóstico). `Evaluacion` lo registra cuando el
contexto no trae pronóstico, para que el Inspector muestre "asumido: no llueve". Reglas y supuestos:
| Regla | Supuesto |
|---|---|
| R-03 (`PosponerPorLluviaRule`) | no llueve ⇒ no pospone |
| M-02 | temperatura = última lectura; UV no se considera |
| P-D3 | temperatura = última lectura |
| P-D4 | no bloquea |
| `DespachoRiego` | no pospone al revalidar |

### D-3 — Alertas
`INFO` "Sin pronóstico: el sistema decide con las lecturas y supuestos conservadores" al entrar, `INFO` al volver.
Una por evento (principio 8). No por zona: es global.

### D-4 — O-05 Sincronización (DECISIÓN ABIERTA, bloqueante para esa tarea)
La v2 la escribe pensando en un nodo/edge que decide solo. En el sistema actual el backend corre en el vivero y las
decisiones y el historial se guardan localmente; "subir eventos al volver la conexión" sólo tiene sentido si hay un
destino remoto (nube) que hoy **no existe**. Opciones:
- (a) Declarar O-05 **no aplicable** hoy, dejarlo documentado en la v2 y en el índice. *Default recomendado.*
- (b) Diseñar la sincronización contra un destino remoto: es otro cambio, con su propio alcance y seguridad.
Este cambio hace (a) y deja registrado el criterio para reabrirlo.

### D-5 — O-04
Test de integración con reloj inyectado: el día del plan avanza y los horarios rigen sin pronóstico ni red.

### D-6 — Parámetros
Ninguno nuevo (D-1). El TTL sigue siendo la propiedad `yerbanalytics.weather.cache-ttl-ms`.
