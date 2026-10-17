# Change: add-fallas-esp32

> Rama: `feat/reglas-v2-fallas-esp32`. Cubre **S-05** (`reglas_v2` §4) y el registro de reparaciones (HU-21 CA-04/05). Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`, `add-seguridad-lectura-hardware`.

## Why

La v2 reparte la detección de fallas: **el ESP32 las detecta, corta el actuador y las publica en un tópico
propio de errores**; el motor reacciona marcando el sector o la mediasombra como averiados y sacándolos de lo
autónomo hasta que alguien registre la reparación. Hoy existe sólo una parte:

- El contrato ya tiene el ack con `falla_mecanica` (`contrato.h`) y `SeguimientoComandosService` deja una
  alerta de historial `FallaActuador`.
- Pero **no hay un tópico de errores** en `contrato.h` (los tópicos son ingesta, comando, ack, zona y riel),
  ni caudalímetro en el firmware de producción (sólo un sketch de prototipo, `test_caudalimetro`).
- La alerta no tiene consecuencias: el sector sigue siendo candidato a riego y la mediasombra se sigue moviendo.
  El campo `dispositivo.falla` existe y `HardwareService` lo usa para el estado operativo, pero nadie lo escribe
  desde un error del nodo (no verificado: quién lo escribe hoy; ver tarea 0.1).

## What Changes

- **Contrato MQTT:** tópico de errores `nursery/zone/{zona}/errores` con payload
  `{ nodo, sector, tipo, valor, timestamp }` y tipos `SIN_CAUDAL`, `CAUDAL_EXCESIVO`,
  `MEDIASOMBRA_FIN_DE_CARRERA`. Fuente: `contrato.h`; espejos: `ContratoNodo.java` y `simulador/server/contract.ts`.
- **Backend:** receptor del tópico; marca "Falla hidráulica" (sector) o "Falla mecánica en mediasombra" (MZ) y
  los saca de lo autónomo; alertas `CRITICAL` con los textos de la v2 (riego manual / cubrir a mano).
- **Reparación:** acción "registrar reparación" (API + UI) que levanta la marca. Es la que usa S-06 también.
- **Firmware:** publicar los errores; leer los dos fines de carrera de la malla (cerrada/abierta) e informar el
  estado real. Caudalímetro: **sólo contrato y simulación**; el hardware se incorpora en la versión final (v2 §1.2).
- **Simulador:** puede inyectar los tres errores (herramienta de prueba; el backend no lo conoce).

## Out of scope

- El hardware del caudalímetro en el vivero real.
- La efectividad del riego (E-01) como indicio de falla hidráulica → `add-efectividad-y-s06`.
