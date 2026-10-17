# Change: add-seguridad-lectura-hardware

> Rama: `feat/reglas-v2-seguridad-lectura-hardware`. Cubre **S-03** y **S-04** (`reglas_v2` §4). Índice: `reglas-v2-overview/README.md`.
> **Depende de:** `add-control-demo-reglas`.

## Why

La v2 pide dos guardas de seguridad que el motor no tiene:

- **S-03 Lectura inválida.** Un valor fuera del rango físico (humedad fuera de 0–100 %, temperatura fuera de
  −10…60 °C, pH fuera de 3–9, CE fuera de 0–10 dS/m, luz fuera de 0–100 %) hoy entra al snapshot como si fuera real.
  La auditoría no encontró validación de rangos físicos en `mqtt/` ni en `NurseryService`.
- **S-04 Hardware incompleto.** Un sector sin electroválvula o bomba mapeada (o una MZ sin mediasombra) no debe
  recibir acciones autónomas (HU-18 CA-04). `HardwareService` registra los dispositivos por tipo
  (`nodo_testigo`, `electrovalvula`, `bomba_peristaltica`, `mediasombra`) pero ninguna regla consulta si el
  conjunto está completo.

## What Changes

- **S-03:** validación de rango físico por métrica al ingerir la lectura. Un valor inválido pide **relectura
  inmediata** (hasta 2 reintentos); si alguno es válido se usa y no se alerta; si los tres fallan, **esa métrica**
  se descarta en el ciclo (las reglas que la usan no corren, las demás sí) y queda `WARNING`; si falla en el
  ciclo siguiente, `CRITICAL` "Sensor de [métrica] con falla".
- **S-04:** una regla de seguridad nueva `HardwareIncompletoRule` (prioridad 0.5, entre el bloqueo y la
  frescura) que aborta lo autónomo del alcance si falta un actuador requerido. Sin alerta nueva.
- Los rangos físicos viven en el catálogo de parámetros (familia SEGURIDAD), no en constantes.

## Out of scope

- Fallas que reporta el ESP32 (S-05) → `add-fallas-esp32`.
- Reintentos de pedido por nodo sin respuesta (S-02) → `align-reglas-existentes-v2`.
