## Context

El simulador nació como una vista más del dashboard y fue creciendo hacia adentro del
sistema. Hoy está repartido en tres lugares:

- **Backend**: `SimulacionController` expone `/api/simulacion/**` (estado, sensores,
  telemetría); `SimulacionService` guarda el modo en la tabla `modo_operacion` y los sensores
  en `sensor_simulado`; `MqttTelemetrySimulator` publica ruido periódico; y
  `MqttTelemetryPublisher` existe **sólo** para esos dos caminos —el backend real no publica
  MQTT, únicamente consume.
- **Frontend**: `src/simulador/` (app Vite propia en el :5180) y `src/features/simulacion/`
  comparten `node_modules`, tokens, átomos, tipos y la capa `src/data/` con el dashboard.
  `DataRepository` carga con seis métodos de simulación y cinco de cámara que el dashboard
  nunca llama.
- **Base de datos del vivero**: dos tablas que no describen nada del vivero.

Hay además un defecto de diseño heredado que este cambio resuelve de paso. El "modo de
operación" gobierna únicamente `NurseryContext`; el resto de las vistas
(`useConfig`, `useHardware`, `useHistory`, `useTopologia`) usa `getRepository()`, que sigue
`VITE_DATA_SOURCE`. Con el modo en estático y `VITE_DATA_SOURCE=http`, el mapa muestra la
demo hardcodeada mientras Hardware y Configuración consultan el backend real: dos fuentes
distintas en la misma pantalla.

Restricciones que enmarcan el diseño:

- El broker Mosquitto ya existe y el firmware ESP32 ya publica contra él en TCP 1883. El
  contrato de claves y unidades vive en `Desarrollo/embebido/comun/contrato.h`.
- Un navegador no puede hablar MQTT sobre TCP.
- Los endpoints de topología, cámara y diagnósticos son **superficie pública** de la
  plataforma: los usará el planificador de pasadas y el servicio de inferencia. No se tocan.
- El repo ya tiene precedente de proyecto satélite independiente: `Desarrollo/camara/`.

## Goals / Non-Goals

**Goals:**

- Que `Desarrollo/simulador/` sea borrable: sin esa carpeta, backend, dashboard y app de
  cámara compilan y funcionan idénticamente.
- Que el backend no tenga una sola línea —código, tabla, propiedad, origen CORS o
  comentario— que dependa de que exista un simulador.
- Que el simulador arranque por su cuenta, con su propio comando, sin ser parte del arranque
  del sistema.
- Que el modo simulación sea **exactamente** el sistema en producción, más la capacidad de
  inyectar valores de hardware a mano.
- Que el modo estático sea una demo ilustrativa **completa**: todas las secciones mockeadas,
  ninguna a medias.
- Conservar toda la funcionalidad actual del simulador.

**Non-Goals:**

- Alternar entre estático y simulación sin reiniciar el dashboard. Se acepta el reinicio a
  cambio de sacar el modo del backend (decisión del usuario).
- Reescribir la UI del simulador para que se parezca al dashboard. Estrena UI mínima propia.
- Tocar el firmware, el contrato MQTT, el contrato de la cámara o los endpoints públicos.
- Compartir código entre simulador y frontend por medio de un paquete o workspaces.
- Cambiar en lo más mínimo el ciclo de captura. El simulador **nunca le habla a la app de
  cámara**: emite la orden al backend, el backend la empuja al dispositivo por SSE, el
  dispositivo sube la imagen y el simulador consulta el avance de la orden. Los cuatro
  endpoints que intervienen —emisión, consulta, servido de imagen y contrato
  `/api/camara/v1/**`— quedan intactos. Vinculación de dispositivo, pedido de captura,
  seguimiento hasta la foto y carga manual del diagnóstico siguen funcionando igual.

## Decisions

### 1. El simulador publica MQTT directo al broker, como un nodo más

Hoy el envío manual viaja `navegador → POST /api/simulacion/telemetria → backend publica →
broker → backend ingiere`. El backend se publica telemetría a sí mismo, dando la vuelta
completa sólo para que el mensaje atraviese el pipeline real.

Pasa a ser `simulador → broker → backend ingiere`. El simulador ocupa exactamente el lugar
del ESP32: mismo topic `nursery/zone/{zonaId}/telemetry`, mismo payload, mismas unidades.
El backend no se entera de quién publicó, que es justamente el punto.

Como un navegador no habla MQTT sobre TCP, el simulador necesita un proceso propio. Se elige
**app Node** (React + Vite para la UI, Express + cliente MQTT del lado servidor).

- *Alternativa descartada — SPA pura con MQTT-over-WebSockets*: eliminaría el servidor, pero
  obliga a habilitar un listener WS en Mosquitto, es decir, configuración fuera de la carpeta
  del simulador; además el repo ya descartó MQTT-over-WS en el contrato de la cámara y no
  conviene reintroducirlo por una herramienta de prueba.
- *Alternativa descartada — segundo proyecto Spring Boot*: consistente con el stack del
  backend, pero es un módulo Maven y una JVM entera para una herramienta de prueba, y arrastra
  la tentación de volver a compartir entidades con el backend.

### 2. Un solo proceso y un solo puerto: Vite en modo middleware dentro de Express

El servidor Express del simulador monta Vite como middleware en desarrollo y sirve el build
estático en producción. Un comando, un puerto (`5180`), el mismo comportamiento en ambos
modos. Evita el arreglo típico de dos procesos coordinados con `concurrently` y un proxy de
Vite hacia el servidor propio.

### 3. El simulador proxea el backend, para que el backend no tenga que conocerlo

Si el navegador del simulador llamara directo a `:8000`, el backend tendría que listar el
origen `http://localhost:5180` en `yerbanalytics.cors.origins`. Eso **es** un rastro del
simulador en el backend, justo lo que se busca eliminar.

En cambio, la UI del simulador llama siempre a su propio origen (`/backend/**`) y Express
reenvía al backend. Desde el punto de vista del backend, el simulador es un cliente HTTP
cualquiera, sin CORS de por medio. La URL del backend es configuración del simulador
(`.env` propio), no del sistema.

Consecuencia a cuidar: el backend devuelve la imagen de una captura como ruta relativa
(`/api/capturas/{id}/imagen`), porque no conoce su propia URL pública. El simulador debe
resolverla contra el proxy; si no, el navegador la busca en el origen de la página y la foto
no aparece. Es la misma trampa que el dashboard ya documenta en `absolutizarImagen`.

### 4. El contrato MQTT se espeja, no se comparte

El simulador necesita las claves y unidades de `contrato.h` (`ce` en µS/cm, `uv` como % de
luz del LDR). Ya hay dos espejos de ese contrato —el firmware y `ContratoNodo` del backend—
y el simulador será el tercero, en un archivo propio con el puntero explícito a la fuente de
verdad, tal como lo hace `ContratoNodo`.

Se acepta la duplicación: el precio de compartir sería que el simulador dejara de ser
borrable. El contrato es chico y estable, y el riesgo de desincronización se acota con la
prueba de extremo a extremo (enviar desde el simulador y verificar el valor persistido).

### 5. El estado del simulador vive en su carpeta, en un archivo JSON

Sensores simulados y preferencias del simulador se guardan en `datos/` dentro del proyecto,
en JSON, ignorado por Git. No hace falta una base: es una lista corta que sólo el simulador
lee y escribe. Borrar la carpeta borra los datos, que es exactamente el comportamiento
buscado.

*Alternativa descartada — SQLite*: más maquinaria de la necesaria y un binario nativo que
complica la instalación en Windows, sin ganancia para una lista de sensores.

### 6. El modo de operación es `VITE_DATA_SOURCE`, aplicado en un único punto

`getRepository()` ya decide la fuente por `VITE_DATA_SOURCE` para todas las vistas.
`NurseryContext` deja de consultar el modo al backend y pasa a usar `getRepository()` como
todos los demás. Con eso:

- `VITE_DATA_SOURCE=http` (`npm run dev`) → **todas** las secciones contra el backend real.
  Es el sistema en producción; el simulador simplemente le inyecta hardware simulado.
- `VITE_DATA_SOURCE=mock` (`npm run dev:demo`) → **todas** las secciones desde el mock
  determinístico. Demo ilustrativa, no funcional, sin backend.

La decisión pasa de runtime a arranque. Es la concesión que habilita sacar el modo del
backend, y de paso elimina el estado híbrido que hoy es posible.

### 7. `DataRepository` se reduce al dominio del dashboard

Se quitan los seis métodos de simulación (`getSimulacionEstado`, `setModoSimulacion`,
`getSensoresSimulados`, `crearSensorSimulado`, `eliminarSensorSimulado`, `enviarTelemetria`)
y los cinco de cámara (`getDispositivosCamara`, `generarCodigoVinculacion`,
`emitirOrdenCaptura`, `getOrdenCaptura`, `crearDiagnostico`), que sólo consumía el panel del
simulador. El simulador se lleva su propio cliente HTTP, chico y directo, sin patrón
Repository: no tiene dos orígenes entre los cuales elegir.

Los **endpoints permanecen** en el backend. No son del simulador: `POST /api/capturas/ordenes`
es el del futuro planificador y `POST /api/diagnosticos` el del futuro servicio de inferencia.

### 8. Dos requisitos vuelven a su capacidad correcta

`simulacion-ingesta` acumuló requisitos que no son del simulador sino del sistema, y que
deben sobrevivir a la eliminación de esa capacidad:

- *Heartbeat del nodo por coincidencia de serial/MAC* → `sensado-persistencia`. Es cómo la
  ingesta actualiza el estado técnico del nodo, valga la telemetría de un ESP32 o del
  simulador.
- *Regeneración de topología robusta bajo concurrencia* → `topologia-persistencia`. El
  borrado en bloque protege a cualquier regeneración, no sólo a la disparada desde el
  simulador.

## Risks / Trade-offs

- **El contrato MQTT queda espejado en tres lugares** (firmware, `ContratoNodo`, simulador) →
  Cada espejo apunta explícitamente a `contrato.h` como fuente de verdad, y la verificación
  de extremo a extremo del cambio incluye enviar `ce` desde el simulador y comprobar que se
  persiste en dS/m. Un desfasaje se detecta en el primer envío.

- **Alternar de modo exige reiniciar el dashboard** → Aceptado explícitamente. Se mitiga con
  dos scripts nombrados (`npm run dev` / `npm run dev:demo`) y documentación en el README,
  para que el cambio de modo sea un comando y no un trámite.

- **El simulador deja de compartir tipos con el frontend y puede desincronizarse del backend**
  (p. ej. si cambia el DTO de una orden de captura) → El simulador consume superficie pública
  y versionada; un cambio incompatible ahí ya es un problema para el planificador y la
  inferencia, no sólo para el simulador. Se acota declarando en el simulador sólo los campos
  que usa.

- **La eliminación de las tablas es irreversible y `ddl-auto=update` no la hace** → Se entrega
  un script de migración manual siguiendo el patrón ya existente en el repo
  (`migracion-manual.sql`, `migracion-captura-imagenes.sql`). El `DROP` va documentado y
  aparte, para que se corra a conciencia. Los datos que se pierden son sensores de prueba y
  un modo que deja de existir: no hay información del vivero en juego.

- **Se pierde el envío manual mientras el simulador no esté levantado** → Es la definición
  del cambio, no un efecto colateral: el sistema sin simulador se comporta como en
  producción, esperando telemetría de hardware real.

- **`npm install` extra y un `node_modules` más en el repo** → Consecuencia asumida de la
  independencia. El simulador se instala sólo cuando se lo va a usar, y su ausencia no impide
  levantar el sistema.

- **Un desarrollador podría "arreglar" algo agregando un endpoint de simulación al backend**,
  reintroduciendo el acoplamiento → Queda registrado como invariante en `CLAUDE.md` y como
  requisito verificable en las specs (`el backend no conoce al simulador`).

## Migration Plan

1. **Crear** `Desarrollo/simulador/` completo y verificarlo contra el backend actual, que
   todavía conserva su código de simulación. Los dos caminos conviven en este punto.
2. **Migrar el dashboard**: `NurseryContext` a `getRepository()`, podar `DataRepository` y
   eliminar `src/simulador/`, `src/features/simulacion/`, hooks, config y scripts de Vite.
3. **Podar el backend**: eliminar las 9 clases, la semilla de `modo_operacion`, las
   propiedades `mqtt.simulator.*` y el origen `:5180` de CORS; simplificar
   `DiagnosticoControllerTest`.
4. **Migración de base**: correr el script que da de baja `modo_operacion` y `sensor_simulado`.
5. **Verificar la independencia**: renombrar temporalmente `Desarrollo/simulador/` y comprobar
   que backend, dashboard (en ambos modos) y app de cámara siguen funcionando.

*Rollback*: los pasos 1 y 2 son reversibles por Git. El paso 4 no lo es en cuanto a datos
—los sensores simulados se pierden—, pero el esquema se recrea solo si se revierte el
código, porque `ddl-auto=update` vuelve a crear las tablas a partir de las entidades.

## Open Questions

Ninguna abierta. Las tres decisiones que estaban en duda —cómo publica MQTT el simulador,
dónde vive el modo y qué hacer con la UI compartida— fueron resueltas con el usuario antes de
redactar este documento y están registradas en las decisiones 1, 6 y en el Non-Goal de la UI.
