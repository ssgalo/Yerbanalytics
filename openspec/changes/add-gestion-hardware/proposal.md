# Change: add-gestion-hardware

## Why

La sección **Hardware** es hoy un placeholder (`/hardware` → `PlaceholderPage`
titulado "Estado del hardware"); el ítem ya está reservado en el sidebar bajo el grupo
"Gestión". El Product Backlog la define en dos historias de prioridad alta (Release R1):

- **HU-18** — el **Administrador** quiere **mapear la distribución del vivero y
  registrar el hardware instalado** para habilitar la trazabilidad espacial y asociar
  cada equipo físico a su sector. Sus CA exigen: alta de cada dispositivo (nodo sensor
  testigo, electroválvula, bomba peristáltica) por serial/MAC asociado a su
  sector/macro-zona (CA-02), rechazo de serial/MAC duplicado o de un segundo actuador
  del mismo tipo en un sector (CA-03), y señalización de los sectores con mapeo
  incompleto, donde la actuación autónoma queda deshabilitada (CA-04).
- **HU-21** — el **Administrador** quiere **visualizar el estado técnico del hardware**
  (batería, señal, fallas físicas) para detectar equipos caídos y ejecutar el recambio
  preventivo. Sus CA exigen: panel por dispositivo con tipo, sector/zona, batería,
  señal, último update y estado operativo (CA-01); batería bajo el umbral → "Batería
  Baja" + WARNING (CA-02); sin reportar por encima del umbral crítico → "Fuera de
  Servicio" + alerta (CA-03); fallas físicas (Falla Hidráulica, falla mecánica de
  mediasombra) reflejadas y asociadas al sector hasta su reparación (CA-04); y un flujo
  de recambio que reutiliza el registro, limpia la avería y reanuda el monitoreo (CA-05).

Hoy hay una **brecha real**: la telemetría MQTT ya transporta `mac` y `battery`
(`MqttTelemetryPayload`) pero **se descartan** en `NurseryService.updateTelemetry()`.
No existe ninguna entidad de dispositivo/nodo, ni columnas de batería/señal/falla, ni
seed de hardware. Sin un registro de dispositivos de primera clase, ninguna de las dos
historias puede sostenerse.

## What Changes

- **Registro de dispositivos** en el backend: nueva entidad `dispositivo` (nodo
  testigo, electroválvula, bomba peristáltica, mediasombra) con serial/MAC único,
  vínculo a sector o macro-zona, y telemetría técnica (batería, señal, último update,
  falla).
- **Estado técnico derivado** por watchdog (HU-21): batería bajo el umbral → "Batería
  Baja"; sin heartbeat por encima del umbral corto → "Señal intermitente"; por encima
  del umbral crítico → "Fuera de servicio"; una falla física mantiene el equipo como
  averiado hasta su recambio.
- **Heartbeat por telemetría MQTT** (cierra la brecha): la telemetría de cada
  macro-zona actualiza la batería/señal/último update de su nodo testigo. Se agrega
  `signal` al payload y el simulador emite una MAC distinta por macro-zona.
- **Validación del alta** (HU-18 CA-03): se rechaza serial/MAC duplicado y un segundo
  actuador del mismo tipo en un sector; se detectan los **sectores con mapeo
  incompleto** (CA-04).
- **Endpoints** `GET /api/hardware` (flota + KPIs + sectores incompletos),
  `POST /api/hardware` (alta validada) y `PUT /api/hardware/{id}` (recambio).
- **Capa de datos del frontend**: `DataRepository` gana `getHardware()`,
  `registerDevice()` y `replaceDevice()`; mock determinístico (con casos de batería
  baja, intermitente, fuera de servicio y avería) y cliente HTTP.
- **Vista Hardware** real: KPIs de flota, tabla de dispositivos con su estado técnico,
  filtros (tipo/estado/zona), alta y mapeo con validación en cliente, panel de sectores
  incompletos y acción de recambio. Reemplaza el placeholder de `/hardware`.

## Impact

- Affected specs: `gestion-hardware` (frontend, nueva), `hardware-persistencia`
  (backend, nueva).
- Affected code:
  - Backend `Desarrollo/backend/`: nuevos `model/DispositivoEntity`,
    `repository/DispositivoRepository`, `dto/Dispositivo`, `dto/SectorIncompleto`,
    `dto/HardwareData`, `service/HardwareService`, `service/HardwareInvalidoException`,
    `service/HardwareConflictoException`, `controller/HardwareController`; edición de
    `mqtt/MqttTelemetryPayload` (campo `signal`), `mqtt/MqttTelemetrySimulator` (MAC por
    zona), `service/NurseryService` (heartbeat), `resources/data.sql` (seed) y
    `resources/application.properties` (umbrales).
  - Frontend `Desarrollo/frontend/`: nuevos `features/hardware/`, `data/mock/hardware.ts`,
    `hooks/useHardware.ts`; edición de `types/domain.ts`, `data/repository.ts`,
    `data/http/httpRepository.ts`, `data/mock/mockRepository.ts`, `router.tsx`.
- No toca Modelo_IA.

## Non-Goals

- **Generación dinámica de la topología** (HU-18 CA-01: que el Administrador defina N
  macro-zonas × M sectores): la grilla ya está representada por el seed existente
  (6 macro-zonas × 100 sectores). Este cambio trabaja el **registro y mapeo de
  dispositivos sobre esa topología existente**; reemplazar el seed fijo por un
  generador administrable es un cambio mayor.
- **Gating por rol** (solo Administrador): depende de HU-01/HU-20 (autenticación
  inexistente hoy); la vista se expone sin auth, consistente con el resto de la app.
- **Aplicación física** de la deshabilitación de actuación en sectores incompletos: se
  detecta y se muestra; la ejecución vive en las HU de actuación (HU-06/07/08).
- **Generación de la falla física** desde el motor (caudalímetro/driver del motor):
  HU-21 modela y muestra la avería y su recambio; el disparo real de la falla es de las
  HU de actuación. El seed incluye un caso de avería para ejercitar la vista.
