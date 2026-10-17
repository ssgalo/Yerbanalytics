# Design: add-fallas-esp32

## Estado verificado

| Pieza | Hoy |
|---|---|
| Ack con falla | `contrato.h`: `falla_mecanica` (mediasombra no tocó el final de carrera) |
| Alerta de falla | `service/SeguimientoComandosService.java`: regla `FallaActuador` al llegar un ack ERROR que es falla; `ActuadorSinRespuesta` si no hay ack |
| Consecuencia | Ninguna sobre las decisiones del motor (su Javadoc: "No cambia ninguna decisión del motor") |
| Campo de falla | `DispositivoEntity.falla` (String); `HardwareService` lo cuenta en `averiados` y lo usa para el estado operativo. **Verificado: nadie lo escribe**; el único `setFalla` del backend es `setFalla(null)` al recambiar un dispositivo (`HardwareService`, ~l.259). Hoy no hay ningún camino que marque una falla |
| Dónde viven los actuadores | Electroválvula, bomba y **mediasombra se registran por sector** (una de cada tipo por sector); el nodo testigo, por zona. La falla de mediasombra de la v2 es "de la MZ", pero el dato vive en el dispositivo del sector que informó |
| Tópico de errores | **No existe** en `contrato.h` |
| Caudalímetro | Sólo `embebido/prototipo_hardware/test_caudalimetro/`; el firmware de producción no lo menciona (búsqueda de "caudal" sólo devuelve prototipos) |
| Fines de carrera | Verificado en `embebido/actuacion/act_mediasombra.cpp`: el firmware lee **un final de carrera por sentido** (`pinFin(enrollada)`), y el ack ya distingue `falla_mecanica`. Lo que falta es publicarlo en el tópico de errores y exponer su estado |

## Decisiones

### D-1 — Tópico y payload
`nursery/zone/{zona}/errores`, QoS 1, retain no. Una sola vez por evento (el ESP32 no repite hasta que se
resuelve). `tipo` ∈ {`SIN_CAUDAL`, `CAUDAL_EXCESIVO`, `MEDIASOMBRA_FIN_DE_CARRERA`}; `valor` es el caudal
medido (L/h) o, para la malla, qué final de carrera falló (`CERRADA`|`ABIERTA`). `sector` va vacío en el error
de mediasombra (es de la MZ).

### D-2 — Dónde se guarda la falla
Se reutiliza `dispositivo.falla` (electroválvula o mediasombra del alcance) con el tipo como valor, y se agrega
`falla_desde` (epoch ms). `HardwareService` ya deriva el estado operativo desde ese campo ⇒ el dashboard lo ve
sin cambios. **Una sola fuente**: no se crea una tabla paralela de fallas.

### D-3 — Qué bloquea
- `SIN_CAUDAL`/`CAUDAL_EXCESIVO` ⇒ el sector sale del riego **y** de la dosificación automáticos. Se evalúa con
  una regla de seguridad `FallaHidraulicaRule` (aborta RIEGO e INSUMO del sector) junto a `HardwareIncompletoRule`
  (cambio `add-seguridad-lectura-hardware`); si ese cambio no se integró, queda propia y se unifican después.
- `MEDIASOMBRA_FIN_DE_CARRERA` ⇒ la malla de la MZ no se mueve (`FallaMediasombraRule`, rama MEDIASOMBRA).
- **R-02 no se exceptúa**: la v2 no lo dice. Una falla hidráulica del sector bloquea también a R-02; por eso el
  texto de la alerta pide riego **manual** si la humedad de la MZ < 45 %.

### D-4 — Textos de alerta
Tal cual la v2: `CRITICAL`; con falla hidráulica y humedad de la MZ < 45 %, el texto pide riego manual del
sector; con fin de carrera de "cerrada" inactivo (la malla quedó abierta), pide cubrir a mano.

### D-5 — Reparación
`POST /api/hardware/{dispositivoId}/reparacion {detalle}` limpia `falla`, registra quién y cuándo en el historial
(tipo "Reparación") y emite `INFO`. Permiso nuevo `hardware.reparar`. Es lo que S-06 reutilizará para sus revisiones.

### D-6 — Convivencia con el ack
`falla_mecanica` por ack y `MEDIASOMBRA_FIN_DE_CARRERA` por el tópico son el mismo hecho visto por dos
caminos. Para no alertar dos veces, el receptor deduplica por (zona, tipo) mientras `falla` siga puesta.

### D-7 — Caudalímetro sin hardware
El contrato y el receptor se prueban con el simulador. El firmware de producción no cambia en lo de caudal hasta
la versión final; la tarea de firmware se limita a la mediasombra (ya hay final de carrera).

## Preguntas abiertas
- ¿El nodo de la mediasombra publica en el tópico de su sector o de la zona? (v2 la trata por MZ.)
- ¿Quién puede registrar reparaciones: Operario o sólo Ingeniero Agrónomo? Default: ambos.
