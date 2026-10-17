# Change: add-operacion-sin-internet

> Rama: `feat/reglas-v2-sin-internet`. Cubre **O-01…O-05** (`reglas_v2` §10, marcada 🔶 *Sin revisar*) y HU-13.
> **Depende de:** `add-control-demo-reglas`, `add-mediasombra-v2`, `add-dosificacion-base`.
> Es el último cambio de la cadena: toca riego, mediasombra y aplicaciones. Índice: `reglas-v2-overview/README.md`.

## Why

HU-13 pide que, sin conexión, el sistema siga decidiendo con supuestos conservadores. Lo único que se pierde sin
internet es el **pronóstico** (lluvia, temperatura, UV), y el supuesto seguro depende de la acción (v2 §10):

| Regla | Supuesto |
|---|---|
| O-01 Riego | Sin pronóstico, **no llueve**: R-03 no pospone y R-01 riega |
| O-02 Mediasombra | M-02 usa sólo la última lectura; M-01 sigue por horario |
| O-03 Aplicaciones | P-D4 se desactiva y P-D3 usa sólo la lectura |
| O-04 Plan de rustificación | Sigue normalmente (depende del reloj) |
| O-05 Sincronización | Al volver la conexión se suben los eventos con su timestamp original, sin duplicados |

Estado hoy (verificado en el código): **O-01 ya se cumple de hecho** — `PosponerPorLluviaRule` documenta que sin
pronóstico las comparaciones quedan `SIN_DATO` y no pospone. Pero es un efecto colateral silencioso: el propio
`OpenMeteoWeatherClient` advierte que, si el hilo de refresco se cuelga, "R-03 pasa a 'sin dato' en silencio". No hay
un **modo sin pronóstico** explícito, ni aviso, ni garantía de que el resto de las reglas (M-02, P-D3, P-D4) hagan lo
mismo, ni sincronización.

## What Changes

- **Estado de pronóstico explícito** (`DISPONIBLE | VENCIDO | AUSENTE`) en `WeatherService`. La vigencia ya existe (el pronóstico
  vencido se usa hasta 4 × TTL de 15 min = 60 min y luego se descarta); se la nombra y se la avisa, no se agrega un parámetro.
- **Contrato único para las reglas:** cada regla que usa el pronóstico declara su supuesto seguro y lo registra en la
  traza ("sin pronóstico: se asume que no llueve"). Test de contrato por regla (R-03, M-02, P-D3, P-D4, F-*).
- **Alerta** `INFO` al entrar/salir del modo sin pronóstico (una por evento).
- **O-04:** test de que el plan avanza con el reloj sin red.
- **O-05:** decidir y, si corresponde, implementar la sincronización (ver design D-4: depende de la arquitectura).

## Out of scope

- Reglas nuevas. Este cambio sólo asegura el comportamiento sin red de las que ya existen.
