## Why

El simulador está entretejido con el sistema que debería probar: vive dentro del proyecto
frontend, su estado (modo de operación y sensores simulados) ocupa dos tablas de la base de
datos de producción, y su envío manual de telemetría lo ejecuta el propio backend. Hoy no hay
forma de sacarlo: no existe una carpeta que se pueda borrar.

Eso tiene dos costos concretos. El backend carga con un controlador, un servicio, dos
entidades, dos repositorios, tres DTOs y un publicador MQTT que no sirven a ningún caso de
uso real del vivero. Y el "modo de operación" —que nació como interruptor del simulador—
terminó gobernando de qué fuente lee el dashboard, con el resultado incoherente de que en
modo estático el mapa muestra una demo hardcodeada mientras Hardware, Configuración,
Historial y Topología siguen consultando el backend real.

## What Changes

- **BREAKING** — El simulador se extrae a `Desarrollo/simulador/`, un proyecto Node
  independiente con su propio `package.json`, su propio arranque y su propio almacenamiento.
  Borrar esa carpeta SHALL dejar el sistema entero funcionando igual.
- **BREAKING** — El simulador deja de pedirle al backend que publique por él: publica la
  telemetría **directo al broker MQTT**, en el mismo topic y con el mismo contrato de claves
  que el firmware del ESP32. Para el backend, el simulador pasa a ser indistinguible de un
  nodo físico más.
- **BREAKING** — Se elimina del backend todo rastro del simulador: `SimulacionController`,
  `SimulacionService`, `MqttTelemetrySimulator`, `MqttTelemetryPublisher`,
  `ModoOperacionEntity`, `SensorSimuladoEntity`, sus repositorios, los DTOs
  `SimulacionEstado` / `SensorSimulado` / `EnvioTelemetria`, las propiedades
  `yerbanalytics.mqtt.simulator.*` y las tablas `modo_operacion` y `sensor_simulado`.
- **BREAKING** — El modo de operación deja de ser estado del backend. Pasa a ser
  `VITE_DATA_SOURCE`, la variable que el dashboard **ya usa**: `http` es el sistema real
  (idéntico a producción) y `mock` es la demo ilustrativa. Un único `getRepository()` la
  aplica a **todas** las secciones, así que el modo estático deja de mostrar secciones a
  medias. Alternar exige reiniciar el dashboard; es el precio aceptado por sacar el modo del
  backend.
- **BREAKING** — `DataRepository` pierde los seis métodos de simulación y los cinco de
  cámara: sólo los usaba el simulador. Los endpoints correspondientes del backend
  **permanecen**, porque son superficie pública de la plataforma (el futuro planificador de
  pasadas y el futuro servicio de inferencia).
- Los sensores simulados y el modo del propio simulador se persisten dentro de
  `Desarrollo/simulador/`, no en la base del vivero.
- El simulador conserva toda su funcionalidad actual: alta/baja de sensores, envío por
  métrica o completo con fecha/hora opcional, emisión automática periódica, regeneración de
  topología, panel de cámara con órdenes de captura y carga manual de diagnósticos.
- El simulador estrena UI mínima propia: no copia los tokens ni los átomos del dashboard,
  para no quedar atado a refactors de una UI que no es la suya.
- Dos requisitos que hoy viven en specs de simulación son en realidad del sistema y se
  mudan a su capacidad correcta: el heartbeat del nodo por coincidencia de serial/MAC y la
  robustez de la regeneración de topología bajo concurrencia.

## Capabilities

### New Capabilities

- `simulador-standalone`: el simulador como proyecto independiente — arranque propio y
  separado del sistema, stack y estado propios, cero superficie en el backend, y la garantía
  de que borrar su carpeta no rompe nada.
- `simulador-telemetria`: el simulador como emisor de hardware — gestión de sensores
  simulados propios, publicación directa al broker respetando el contrato del firmware,
  envío por métrica o completo con fecha/hora opcional, y emisión automática periódica.
- `simulador-camara`: panel de cámara del simulador — órdenes de captura de prueba y carga
  manual del diagnóstico, siempre por endpoints públicos de la plataforma. Reemplaza a
  `simulacion-captura`, adaptado a que el panel ahora vive fuera del frontend.

### Modified Capabilities

- `data-layer`: el origen de datos pasa a ser único para toda la app (una sola decisión por
  `VITE_DATA_SOURCE`, sin que ninguna vista elija su propia fuente), y la interface
  `DataRepository` se reduce al dominio del dashboard, sin los métodos de simulación ni los
  de cámara.
- `sensado-persistencia`: recibe el requisito de heartbeat del nodo por coincidencia de
  serial/MAC, y el requisito del envío de las diez métricas deja de estar redactado en
  términos del dashboard de simulación para hablar de cualquier emisor.
- `topologia-persistencia`: recibe el requisito de regeneración robusta bajo concurrencia.
- `diagnosticos-registro`: el alta deja de tener que ser independiente de un "modo de
  operación" que ya no existe en el backend.

### Removed Capabilities

- `simulacion-ingesta`: desaparece como capacidad. La telemetría simulada entra por el mismo
  camino que la real, así que no hay nada específico que especificar; sus requisitos se
  reparten entre `simulador-telemetria` y las capacidades del sistema.
- `simulacion-manual`: absorbida por `simulador-standalone` y `simulador-telemetria`.
- `simulacion-modo-vista`: desaparece. El modo deja de ser una capacidad del sistema y pasa
  a ser la selección de origen que `data-layer` ya especifica.
- `simulacion-captura`: renombrada a `simulador-camara`.

## Impact

**Backend** (`Desarrollo/backend/`) — se eliminan 9 clases y 1 test se simplifica:
`controller/SimulacionController`, `service/SimulacionService`,
`mqtt/MqttTelemetrySimulator`, `mqtt/MqttTelemetryPublisher`, `model/ModoOperacionEntity`,
`model/SensorSimuladoEntity`, `repository/ModoOperacionRepository`,
`repository/SensorSimuladoRepository`, `dto/{SimulacionEstado,SensorSimulado,EnvioTelemetria}`.
Se tocan `application.properties` (props del simulador y origen CORS del :5180),
`data.sql` (semilla de `modo_operacion`) y `DiagnosticoControllerTest`. Hace falta un script
de migración manual para dar de baja las dos tablas, porque `ddl-auto=update` no las borra.

**API** — desaparece `/api/simulacion/**` completo (estado, sensores y telemetría). Ningún
otro endpoint cambia: los de topología, cámara y diagnósticos son públicos y siguen igual.

**Frontend** (`Desarrollo/frontend/`) — se van `src/simulador/`, `src/features/simulacion/`,
`hooks/useSimulacion.ts`, `hooks/useCamaraSim.ts`, `data/mock/simulacion.test.ts`,
`simulador.html`, `vite.simulador.config.ts` y los scripts `dev:sim` / `build:sim` /
`preview:sim`. Se reduce `data/repository.ts` y sus dos implementaciones, y se simplifica
`hooks/NurseryContext.tsx`, que deja de consultar el modo. Se agrega el script `dev:demo`.

**Simulador** (`Desarrollo/simulador/`, nuevo) — proyecto Node con React + Vite para la UI y
un servidor propio que habla MQTT contra el broker y guarda su estado en disco. Dependencia
nueva: un cliente MQTT de Node. No comparte `node_modules` ni configuración con el frontend.

**Infraestructura** — el simulador necesita el broker Mosquitto accesible en TCP 1883, el
mismo que ya usa el firmware. No requiere listeners ni configuración de broker adicionales.

**Documentación** — `CLAUDE.md` (§1 mapa del repo, §6 backend, y una sección nueva para el
simulador), el README del backend, el del frontend y un README propio del simulador.
