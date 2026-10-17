# Change: add-dosificacion-base

> Rama: `feat/reglas-v2-dosificacion-base`. Cubre **§8.1–8.3** de `reglas_v2` (insumos, ejecución de una aplicación,
> precondiciones **P-D1…P-D5**) y la **vigencia del diagnóstico** (§1.3). Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`, `add-seguridad-lectura-hardware`.

## Why

Todas las reglas N y F de la v2 terminan en lo mismo: "aplicar 100 mL de [insumo] en el sector en la próxima ventana
apta". Eso hoy no existe. La dosificación actual es otra cosa:

- `SupplyRule` activa **una bomba genérica** (`ACTIVAR_BOMBA`) si el sector está en estado `critical` y la
  confianza ≥ `diagnostico.confianza-minima`. No sabe de insumos, dosis en mL, tiempo por caudal, ni diagnóstico.
- `DailyDoseLimitRule` limita a `insumo.max-dosis-24h` (fábrica 1) dosis por sector en 24 h. La v2 la reemplaza por
  **intervalos mínimos por insumo** (5 d fertilizante, 7 d fitosanitario) y "no más de una aplicación por día".
- Ninguna precondición de la v2 existe: ventana 07–10 / 17–19 con ≤ 30 °C (P-D3), lluvia ≥ 5 mm en 6 h (P-D4),
  humedad de sustrato ≥ 45 % (P-D5), ni el orden fitosanitario-antes-que-fertilizante (P-D2).
- No hay stock de tanques ni reingreso restringido tras el fitosanitario.

Sin esta base, cada cambio de reglas N/F reimplementaría las precondiciones por su cuenta.

## What Changes

- **Dos insumos** (`FERTILIZANTE`, `FITOSANITARIO`), cada uno con su bomba y su tanque; dosis y intervalo en el catálogo.
- **Servicio de aplicación** `ApplicationService`: dado (sector, insumo, regla) verifica las precondiciones, publica la
  orden de bomba con el insumo y el volumen en mL (el contrato ya soporta `ml`: el ESP32 lo convierte a tiempo y corta solo), registra la aplicación
  (sector, insumo, mL, hora, regla), marca el reingreso (fitosanitario, 24 h) y descuenta del tanque.
- **Cola de aplicaciones pendientes**: lo que no cumple precondiciones se reintenta en la **próxima ventana apta**
  (la v2 dice "se vuelve a evaluar en la próxima ventana"); no se pierde.
- **Stock de tanques:** carga del operario, descuento por aplicación, `WARNING` bajo 20 %, `CRITICAL` y bloqueo en 0.
- **Diagnóstico vigente (P-D1):** el de la última captura del sector, ≤ 48 h, con confianza ≥ umbral.
- **Se retiran `SupplyRule` y `DailyDoseLimitRule`** y se migra su parámetro.
- Frontend: carga de tanques (v2 §13) y vista de aplicaciones/stock.

## Out of scope

- Las reglas que *deciden* aplicar (N-01, N-02, F-*) → cambios propios; acá sólo la maquinaria que ejecutan.
- La instalación física de una bomba por insumo en el vivero. **Sí está en alcance** el cambio de software que la representa:
  hoy hay una bomba por sector y el comando no lleva insumo, así que se agrega el insumo al contrato y al registro de dispositivos
  (ver `design.md` D-2; es un cambio de alcance real, con firmware y migración).
