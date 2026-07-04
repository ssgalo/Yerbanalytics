# Change: add-dashboard-simulacion

## Why

El vivero se alimenta de telemetría que el hardware real (nodos ESP32) envía por
**MQTT** al topic `nursery/zone/+/telemetry`; el backend la ingiere
(`MqttTelemetryReceiver` → `NurseryService.updateTelemetry`) y actualiza los
sectores. Hoy, sin hardware físico conectado, la única fuente de datos "vivos" es el
**simulador automático** (`MqttTelemetrySimulator`), que cada 10 s publica valores
**aleatorios** a las 6 macro-zonas y los sobrescribe. Eso deja dos huecos:

- **No hay control manual**: no se puede decidir qué valor "envía" un sensor, ni
  cuándo, ni con qué fecha. Imposible reproducir un escenario concreto (una clorosis
  en MZ-3, un pico de UV, un sensor caído) para demostrar o probar el pipeline.
- **La demo estática y la simulación se pisan**: al arrancar, el seed deja los 600
  sectores `offline` (datos estáticos), pero el simulador automático los reemplaza a
  los 10 s con ruido aleatorio. No hay forma de quedarse en el estado estático ni de
  alternar deliberadamente entre "datos estáticos" y "simulación".

Se necesita un **dashboard de simulación** que permita alternar entre esos dos modos
y, en modo simulación, crear sensores y **enviar lecturas manualmente por MQTT real**,
tal como lo haría el hardware al conectarse. Es una herramienta de prueba/demo del
pipeline de ingesta, no un cambio del dominio.

## What Changes

- **Modo de operación en runtime** (nuevo `SimulacionService`, en memoria): un switch
  `estatico | simulacion`. En **estático** el sistema queda en reposo mostrando los
  datos sembrados; en **simulación** se habilita el envío manual. El simulador
  automático de 10 s queda **apagado por defecto** y sólo corre si, dentro de
  simulación, se activa explícitamente (para no pisar los valores manuales).
- **Envío manual de telemetría por MQTT real** (nuevo endpoint
  `POST /api/simulacion/telemetria`): arma el mismo payload que un ESP32
  (`MqttTelemetryPayload`) y lo **publica en el topic real**, atravesando el mismo
  pipeline de ingesta que el hardware. La fecha/hora es opcional: si no se envía, se
  usa la hora actual. Sólo se acepta en modo simulación (409 en estático).
- **Refactor**: se extrae la mecánica de publicación MQTT (conectar/publicar/desconectar)
  del simulador a un `MqttTelemetryPublisher` reutilizable, compartido por el simulador
  automático y el endpoint manual.
- **Creación y asignación de sensores simulados (persistidos, desacoplados del hardware)**:
  los sensores simulados (serial/MAC + macro-zona) viven en su **propia tabla** del backend
  —independiente del registro de hardware: crearlos NO registra un dispositivo— y siguen las
  macro-zonas de la **topología vigente** (`getTopologia()`). Al ingerir, el heartbeat del
  nodo se ata por **coincidencia de serial/MAC**, no por zona.
- **Envío por métrica o conjunto, con métricas parciales**: cada una de las cinco métricas
  puede enviarse por separado (o todas juntas); la ingesta conserva las métricas ausentes en
  lugar de pisarlas con `null`.
- **Persistencia del modo y de los sensores (DB)**: tanto el modo como la lista de sensores
  simulados se persisten en Postgres y sobreviven al reinicio del backend y al refresco del
  frontend.
- **La vista sigue el modo persistido**: el dashboard principal, al montar y tras cada F5,
  lee el modo del backend y elige su fuente de datos —estático → mock (demo hardcodeada de 6
  macro-zonas), simulación → http (vivero real, topología regenerable + MQTT)—.
- **Regenerar la topología desde el flujo de simulación**: en simulación se puede regenerar
  la grilla (N×M) para que coincida con el hardware a simular; deja los sectores sin
  históricos. El borrado de la grilla es **en bloque** (una sentencia por tabla) para no
  quedar bloqueado ni fallar por la actividad concurrente.
- **Puesta a punto operativa**: CORS habilitado también para el simulador (`:5180`); el
  simulador se sirve directamente en la raíz de su puerto; y el frontend consume el backend
  real (`VITE_DATA_SOURCE=http`).
- **Capa de datos del frontend**: `DataRepository` gana `getSimulacionEstado()`,
  `setModoSimulacion()` y `enviarTelemetria()`; implementación HTTP y mock
  determinístico (el mock refleja los envíos actualizando los sectores de la zona).
- **App de simulación en puerto propio (`localhost:5180`)**: el panel de simulación es
  una aplicación **standalone**, separada del dashboard principal (`:5173`) y aislada de
  su navegación. No es una pestaña del dashboard: no monta el sidebar ni el
  `NurseryProvider`; tiene su propio `index` (`simulador.html`), su entrypoint
  (`src/simulador/`) y su config de Vite (`vite.simulador.config.ts`). Reutiliza la
  misma capa de datos, tipos y componentes del frontend. Contiene el switch de modo, el
  alta de sensor (nodo testigo por macro-zona) y una tarjeta por sensor con inputs de
  las 5 métricas (+ batería/señal), fecha/hora opcional y botón Enviar.

## Impact

- Affected specs: `simulacion-ingesta` (backend, nueva), `simulacion-manual`
  (frontend, nueva), `simulacion-modo-vista` (frontend, nueva — la vista sigue el modo).
- Affected code:
  - Backend `Desarrollo/backend/`: nuevos `service/SimulacionService`,
    `mqtt/MqttTelemetryPublisher`, `controller/SimulacionController`,
    `dto/SimulacionEstado`, `dto/EnvioTelemetria`; edición de
    `mqtt/MqttTelemetrySimulator` (usa el publisher + gating por modo) y
    `resources/application.properties` (`simulator.enabled=false`).
  - Frontend `Desarrollo/frontend/`: nuevos `features/simulacion/`,
    `hooks/useSimulacion.ts`, `data/mock/simulacion.ts`; nueva app standalone
    `src/simulador/` (`main.tsx`, `SimuladorApp.tsx`, `SimuladorHeader.tsx`,
    `SimuladorApp.module.css`), `simulador.html`, `vite.simulador.config.ts` y scripts
    `dev:sim`/`build:sim`/`preview:sim` en `package.json`; edición de `types/domain.ts`,
    `data/repository.ts`, `data/http/httpRepository.ts`, `data/mock/mockRepository.ts`,
    `components/ui/Icon.tsx`. La ruta y el ítem de sidebar de simulación se **quitan** del
    dashboard principal (`router.tsx`, `components/layout/Sidebar.tsx`).
- No toca Modelo_IA.

## Non-Goals

- **Telemetría por sector individual**: el modelo actual mapea la telemetría a la
  macro-zona (un nodo testigo por zona aplica su lectura a todos sus sectores, como el
  piloto real). Sensores que reporten a un sector puntual exigirían extender el esquema
  y quedan fuera de alcance.
- **Series temporales de lecturas**: no se agrega una entidad de histórico de
  mediciones; cada envío actualiza el "último valor" del sector, igual que la ingesta
  real. "Sin históricos al regenerar" significa que la regeneración deja los sectores en
  `null`, no que se agregue un histórico de mediciones.
- **Persistir el simulador automático** (ruido periódico): su on/off es efímero; sólo se
  persisten el modo y los sensores simulados.
- **Migrar la demo estática a datos de backend**: la demo del modo estático sigue siendo el
  mock determinístico del frontend; no se construye un dataset demo en el backend.
- **Gating por rol**: consistente con el resto de la app, la vista se expone sin auth.
