# Design: add-dashboard-simulacion

## Context

La ingesta real es: ESP32 → broker MQTT (`tcp://localhost:1883`) → adapter inbound
(`MqttConfig`) → `MqttTelemetryReceiver.processMessage` → `NurseryService.updateTelemetry`
→ persistencia por sector. El `MqttTelemetrySimulator` es un `@Scheduled` que hoy
publica ruido aleatorio a las 6 zonas cada 10 s, gateado por un `@Value` de arranque
(`simulator.enabled`). El registro de hardware (`DispositivoEntity` / `HardwareService`)
ya modela un `nodo_testigo` por macro-zona y su heartbeat se alimenta de la telemetría.

El objetivo es una herramienta de simulación **manual** que reutilice ese pipeline con
la mayor fidelidad, sin duplicar la mecánica MQTT ni tocar el dominio.

## Goals / Non-Goals

- **Goal**: alternar en runtime entre datos estáticos y simulación manual (con el modo y
  los sensores **persistidos**); enviar lecturas por MQTT real (por métrica o conjunto);
  que la vista siga el modo; regenerar la topología desde el flujo de simulación.
- **Non-Goal**: telemetría por sector, histórico de mediciones, persistir el simulador
  automático, migrar la demo estática al backend, auth por rol. (Ver `proposal.md`.)

> **Nota de consolidación**: este cambio absorbe las refinaciones que originalmente se
> llevaron como `refine-simulacion-topologia` (métricas parciales, sensores desacoplados
> del hardware, zonas desde la topología, heartbeat por serial/MAC) y `persist-simulacion-
> modo` (persistencia del modo/sensores, la vista sigue el modo, regenerar desde
> simulación), más los arreglos operativos (CORS `:5180`, simulador en la raíz, `.env`
> http, borrado en bloque al regenerar). No fueron features nuevas sino la construcción y
> puesta a punto del mismo dashboard.

## Decisions

### 1. El modo vive en runtime y persiste en DB
El `@Value("${...simulator.enabled}")` del simulador se lee una sola vez al iniciar, así
que un switch de UI no podría afectarlo. Se introduce `SimulacionService` con el modo y un
flag `autoSimuladorActivo` (default `false`). El modo es la **fuente de verdad** del switch
y se **persiste en base de datos** (`ModoOperacionEntity`, fila única `id=1`, sembrada en
`estatico` por `data.sql`): sobrevive al reinicio del backend y al refresco del frontend, y
lo comparten el dashboard (`:5173`) y el simulador (`:5180`), que son orígenes distintos
(por eso no alcanza `localStorage`). El `autoSimuladorActivo` queda efímero (en memoria),
consistente con su naturaleza de ruido de demostración. El simulador automático consulta
este servicio en cada tick en vez del `@Value`.

### 2. Extraer un `MqttTelemetryPublisher` reutilizable
La lógica de conectar/publicar/desconectar hoy está inline en `MqttTelemetrySimulator`.
Se extrae a `MqttTelemetryPublisher.publish(zonaId, payload)` (crea `MqttClient`,
conecta con `automaticReconnect`, publica `MqttMessage` QoS 1 en
`nursery/zone/{zonaId}/telemetry`, desconecta). Lo usan tanto el simulador como el
endpoint manual → una sola implementación de la mecánica MQTT. El publisher usa un
`clientId` propio para no colisionar con el inbound adapter ni con el simulador.

### 3. El envío manual atraviesa el pipeline real (no un atajo a `updateTelemetry`)
`POST /api/simulacion/telemetria` **publica al broker** en vez de invocar
`NurseryService` directo. Así se ejercita exactamente el mismo camino que el hardware
real (adapter → receiver → service), que es el sentido de "simular MQTT". Costo: exige
el broker corriendo; si no está, el publish falla y el endpoint responde error (el
frontend lo muestra). Se acepta ese costo por fidelidad.

### 4. Fecha/hora opcional
El payload lleva `timestamp` (epoch ms). Si el request no lo trae (o es null), el
backend usa `System.currentTimeMillis()`. El frontend, con `<input type="datetime-local">`,
convierte a epoch ms si el usuario cargó una fecha; si lo dejó vacío, omite el campo.

### 5. Gating del simulador automático
El scheduled `publishTelemetry()` corre sólo si `modo == SIMULACION` **y**
`autoSimuladorActivo == true`. Por defecto ambos están en reposo, de modo que:
- **Estático**: nadie publica; los datos quedan como el seed (o el último valor).
- **Simulación**: por defecto sólo entran los envíos manuales; el usuario puede
  encender el simulador automático si quiere "vida" de fondo. `application.properties`
  cambia `simulator.enabled` a `false` (deja de arrancar pisando la demo).

### 6. Los sensores simulados son una lista propia, persistida y desacoplada del hardware
Un sensor simulado es un **emisor** que existe por MQTT **antes** de estar registrado en el
sistema; por eso NO se modela como alta de hardware. Viven en su propia tabla
(`SensorSimuladoEntity`: `serial_key` único normalizado, `serial` display, `zona_id`, orden
de alta por `id`) gestionada bajo `/api/simulacion/sensores` (`GET`/`POST`/`DELETE`), sin
tocar el registro de hardware. La `SimulacionPage` lista estos sensores (no `useHardware`) y
las macro-zonas del alta salen de la **topología vigente** (`useTopologia`), no de una
constante. El envío usa `serial` + `zonaId`. Al ingerir, el **heartbeat** se ata al
dispositivo **cuyo serial/MAC coincide** (no por zona): registrar el hardware con el mismo
serial es lo que atribuye las lecturas a ese nodo, mientras los sectores de la zona se
reflejan siempre.

### 7. El mock refleja los envíos
Para que la vista funcione sin backend (`VITE_DATA_SOURCE=mock`), el `MockRepository`
mantiene el modo en memoria y, al `enviarTelemetria`, actualiza en su snapshot los
sectores de la zona (valores de métricas, estado/color derivados con `metricStatus` y
las `specs`, y recalcula los agregados de zona y `stats`). No reproduce el broker, pero
da feedback visual coherente.

### 8. La simulación es una app standalone en su propio puerto
El panel de simulación **no** es una pestaña del dashboard: es una app aparte servida en
`localhost:5180`, aislada de la navegación principal. Es una herramienta de prueba/demo
y conviene mantenerla fuera del producto que ve el viverista. Se implementa como un
segundo entrypoint de Vite dentro del mismo proyecto frontend (`simulador.html` →
`src/simulador/main.tsx`, config `vite.simulador.config.ts` con `server.port = 5180`),
para **reutilizar** tipos, capa de datos (`getRepository()`) y componentes sin duplicar
el proyecto ni las dependencias. Trae su propio shell mínimo (`SimuladorApp` +
`SimuladorHeader`) en lugar del `Sidebar`/`Topbar`, que dependen del `NurseryProvider`;
la vista de simulación sólo necesita `PageMetaProvider`. Se corre con `npm run dev:sim`
y se empaqueta con `npm run build:sim` (salida `dist-simulador/`). El dashboard principal
(`:5173`) deja de exponer la ruta y el ítem de sidebar de simulación.

### 9. Métricas parciales en el envío y la ingesta
`EnvioTelemetria.Metrics` tiene las cinco métricas **opcionales**; el controller exige al
menos una (400 si vienen todas nulas). `NurseryService.updateTelemetry` deja de setear
incondicionalmente las raw readings: sólo actualiza las presentes y conserva el último valor
de las ausentes antes de recomputar estado/diagnóstico/actuadores. El hardware real manda
las cinco, así que su comportamiento no cambia; sólo se habilita el envío por métrica del
simulador (input + fecha/hora por métrica en `SensorSimCard`, además de "Enviar todo").

### 10. La vista sigue el modo persistido para elegir su fuente de datos
El seed del backend es 600 sectores offline; la "demo linda" (6 macro-zonas con valores
variados) es el **mock** del frontend. Por eso el modo mapea a la fuente de datos del
snapshot del vivero: `NurseryContext` lee el modo del backend (http) al montar y usa el
**mock** en estático y el **http** en simulación; ante fallo al leer el modo, degrada a
estático (mock) para que la demo siempre cargue. Es una generalización en runtime del patrón
Repository (`VITE_DATA_SOURCE`, hoy build-time) a un switch persistido. Se expone
`getHttpRepository()`/`getMockRepository()` como singletons; el estado de simulación se lee
siempre del http. El modo se toma al montar (se respeta al F5); no hay push en vivo.

### 11. Regenerar la topología desde el flujo de simulación, con borrado en bloque
El simulador expone (sólo en simulación) un control que reutiliza `generarTopologia`
(`regenerar=true`), que ya deja los sectores offline con lecturas `null` (sin históricos) y
limpia dispositivos/historial. El borrado pasó de `deleteAll()` (entidad por entidad, con
chequeo de versión) a **`deleteAllInBatch()`** (una sentencia `DELETE` por tabla): es rápido,
minimiza la ventana de bloqueo y evita el `ObjectOptimisticLockingFailureException` que
aparecía cuando un proceso en segundo plano (evaluación de historial) o una lectura
concurrente tocaban una fila durante la regeneración —lo que dejaba el botón colgado en
"Regenerando…"—. Como el borrado en bloque no dispara el cascade JPA, se borran los sectores
explícitamente antes que las zonas.

### 12. Puesta a punto operativa (CORS, raíz del simulador, entorno)
- **CORS**: al consumir el backend con URL absoluta, el navegador aplica CORS; se agrega
  `http://localhost:5180` a los orígenes permitidos (además de `:5173`).
- **Simulador en la raíz**: `vite.simulador.config.ts` reescribe `/` → `/simulador.html`
  (middleware de dev y de preview), así `localhost:5180` abre el simulador y no el dashboard.
- **Entorno**: el frontend consume el backend real (`VITE_DATA_SOURCE=http`,
  `VITE_API_BASE_URL=http://localhost:8000/api`) para que el modo/sensores persistan y se
  compartan entre ambas apps.

## Risks / Trade-offs

- **Dependencia del broker** (Decisión 3): sin Mosquitto el envío falla. Mitigación:
  error claro end-to-end; el modo mock no lo necesita.
- **Modo/sensores globales**: al ser proceso-único y sin auth, un solo modo compartido es
  suficiente; se persiste en DB (fila única de modo + tabla de sensores) y sobrevive al
  reinicio.
- **Regenerar bajo concurrencia extrema**: disparar varias regeneraciones simultáneas sigue
  dando 500 en las que pierden la carrera (dos transacciones recreando el mismo `MZ-1`); no
  es un flujo real de un solo usuario. El borrado en bloque cubre el caso normal.
- **Divergencia mock/derivación real**: el mock aproxima la derivación de estado del
  backend (igual criterio que la flota de hardware mock); puede no coincidir 1:1 con la
  lógica completa de `NurseryService`, pero alcanza para desarrollo de UI.

## Migration Plan

`ddl-auto=update` agrega las tablas nuevas (`modo_operacion`, `sensor_simulado`) sin tocar
las existentes; `data.sql` siembra el modo en `estatico` (idempotente). No hay migración de
datos. `simulator.enabled` pasa a `false`: en despliegues existentes el simulador automático
deja de arrancar solo; se activa desde el dashboard.
