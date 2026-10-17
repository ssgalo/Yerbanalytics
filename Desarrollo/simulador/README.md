# Simulador de hardware

Ocupa el lugar del hardware físico del vivero: publica telemetría en el broker MQTT como si
fuera un nodo ESP32, y ejercita el ciclo de captura del riel.

> **Es un proyecto aparte, y eso es el punto.** Borrá esta carpeta y el sistema sigue
> funcionando igual: el backend no tiene una sola línea —código, tabla, propiedad o
> configuración— que dependa de que el simulador exista. Si no está, el vivero simplemente
> espera telemetría de hardware real, que es su comportamiento de producción.

---

## Arranque rápido

```bash
cd Desarrollo/simulador
cp env.example .env      # y completá BACKEND_USUARIO/BACKEND_CLAVE (ver "Cuenta de servicio")
npm install
npm run dev              # http://localhost:5180
```

Se levanta con su propio comando: no hace falta reiniciar ni el backend ni el dashboard para
usarlo.

El `start-all` de la raíz también lo arranca, como comodidad para el trabajo diario. Lo trata
como **opcional**: si esta carpeta no existe, omite ese paso y levanta el resto del sistema
igual. El simulador es una conveniencia del launcher, nunca una dependencia.

Para que la telemetría llegue a algún lado necesitás además:

| | Para qué | Sin eso |
|---|---|---|
| **Mosquitto** en `:1883` | publicar lecturas | el envío falla con un mensaje claro |
| **Backend** en `:8000` | topología, cámara y diagnósticos | el simulador arranca igual y lo avisa |
| **Cuenta de servicio** en el `.env` | que el backend acepte esas peticiones | la telemetría sale igual; el resto lo rechaza el backend |

La barra de estado de arriba muestra los dos, así que si algo no anda se ve de entrada cuál
de los dos falta. Del backend distingue además si está levantado pero rechaza la cuenta del
simulador (punto ámbar, con el motivo).

---

## Qué hace

- **Sensores simulados** — alta por serial/MAC y macro-zona. Es una lista **del simulador**,
  separada del registro de hardware del sistema: crear uno acá no da de alta ningún
  dispositivo. Para que un nodo además actualice su estado técnico (batería, señal, último
  contacto), registralo en la sección Hardware del dashboard con el **mismo serial/MAC**.
- **Envío de lecturas** — cada métrica por separado, con su propia fecha/hora opcional, o las
  diez juntas con batería y señal. Fecha vacía = ahora. Las métricas que no mandás conservan
  su último valor en el vivero.
- **Emisión automática** — lecturas al azar para todas las macro-zonas, para darle actividad de
  fondo al vivero. Arranca siempre apagada, porque encendida pisa lo que cargaste a mano.
  Sensing is backend-driven: there is no interval. On activation it sends one boot round, then
  answers each `LEER_AHORA` the backend publishes on `nursery/zone/{id}/command` with one reading
  for that zone (only simulated macro-zones, only while on).
- **Topología** — regenera la grilla (N macro-zonas × M sectores) para que coincida con el
  hardware a simular.
- **Control de reglas y automatización** — con el backend en modo demo, apaga reglas y acorta sus
  cooldowns (*Telemetría → Control de reglas*), inyecta clima (con marcas horarias, UV máximo del día y
  un modo "sin pronóstico") y arma las condiciones para que una regla dispare en el siguiente pulso
  (*Automatización*). Ver "Cómo agregar el escenario de una regla".
- **Fuente de telemetría** — con el backend en modo demo, **toda** la telemetría del simulador sale por
  `nursery/demo/zone/{id}/telemetry` (tópico aparte del ESP32, sin carrera) y un interruptor en *Telemetría*
  ("Telemetría que usa el backend: ESP32 real / Simulador") elige cuál lee el backend. Los comandos a los
  actuadores siguen en los tópicos del ESP32. Sin modo demo publica en el tópico regular, como siempre.
- **Happy path de la demo** (pestaña *Demo Expo*, junto a la pasada del riel y las secuencias) — un botón que evalúa
  riego, insumo y mediasombra **de a una carga por vez** (tres pulsos con motor, en ese orden) sobre el primer sector de la primera macro-zona. Antes de cada pulso deja encendidas sólo las reglas ejecutoras del paso (estado de demo, restaurado al terminar); espera a que la carga anterior TERMINE (válvulas de la zona sin «Regando»/«En cola»; bomba sin «Dosificando»; plazo = duración esperada + 120 s, con aviso si se agota) y suma 5 s antes de la siguiente. Así el ESP32 no arranca todas las cargas juntas. Con la fuente
  en **Simulador** genera todo: lectura (`humSus` entre el umbral crítico y el de riego, `temp` crítica), diagnóstico de
  IA nuevo (por el flujo de captura) y UV por encima del umbral; pone los cooldowns en 0 y saltea la ventana horaria
  R-05. Con la fuente en **ESP32 real** no inyecta nada (sólo quita un clima simulado): usa el sensor, el pronóstico y
  el diagnóstico de una pasada del riel previa, y avisa si no hay uno utilizable. Al final lee la traza del Inspector y
  muestra, por regla, si actuó y por qué. Lógica en `src/happyPath.ts`; reutiliza los escenarios R01 + SUPPLY + SHADING.
- **Cámara del riel** — vincular el teléfono, pedir una captura, ver la foto llegar y cargar a
  mano el diagnóstico que devolvería el modelo.

---

## Por qué publica directo al broker

```
simulador ──MQTT──► mosquitto:1883 ──► backend (ingesta normal)
```

El simulador ocupa **exactamente** el lugar del ESP32: mismo topic
(`nursery/zone/{zonaId}/telemetry`), mismo payload, mismas unidades. El backend lo ingiere por
su pipeline de siempre y no tiene forma de saber que del otro lado no había un nodo.

Antes esto pasaba por un endpoint del backend (`POST /api/simulacion/telemetria`), que
publicaba al broker para que el mensaje atravesara el pipeline real. Es decir: el backend se
publicaba telemetría a sí mismo, y cargaba con un controlador, un servicio, un publicador MQTT
y dos tablas que no servían a ningún caso de uso del vivero.

Como el navegador no habla MQTT sobre TCP, el simulador necesita su propio proceso. De ahí que
sea una app Node y no una SPA suelta.

### Comandos y acks

El simulador también escucha los comandos del backend (`nursery/zone/+/sector/+/command`) y los
muestra en la consola. Para las macro-zonas que simula (las que tienen un sensor simulado) además
**responde el ack** en `nursery/zone/{zona}/sector/{sector}/ack`, como el firmware: el riego al
cumplir `durationSec`, el insumo a los 10 s (o según `ml`/`durationSec`) y la mediasombra a los 3 s.
Sin esto, el backend registraría una alerta `ActuadorSinRespuesta` por cada comando de la demo. Los
comandos de otras zonas son de un nodo real y sólo se loguean. Las reglas están en
`server/contract.ts` (`simulatedAck`).

---

## Por qué el backend se consume a través de `/backend/**`

La UI **nunca** llama al backend directo: pega contra `/backend/**` de su propio servidor, que
reenvía.

```
navegador :5180 ──► server :5180 ──► backend :8000
        mismo origen,        servidor a servidor,
        sin CORS             sin CORS
```

Si llamara directo, el backend tendría que listar `http://localhost:5180` entre sus orígenes
permitidos — y **esa línea sería un rastro del simulador en el sistema**, justo lo que se
quiere evitar. Así la dirección del backend es configuración del simulador (`.env`), no del
sistema: si el backend se muda de puerto, tocás esto y nada más.

Un efecto secundario que conviene conocer: el backend devuelve la imagen de una captura como
ruta relativa (`/api/capturas/{id}/imagen`), porque no conoce su propia URL pública. El cliente
le antepone `/backend`; sin eso el navegador la buscaría en el origen de la página y la foto no
aparecería.

---

## Cuenta de servicio

El backend exige sesión de usuario en toda `/api/**`, y el simulador entra como cualquier otro
cliente: con una cuenta de rol **Servicio** cuyas credenciales viven en **su** `.env`.

```
BACKEND_USUARIO=simulador
BACKEND_CLAVE=...
```

- **En desarrollo ya viene creada.** El `env.example` del backend la declara en
  `YERBANALYTICS_USUARIOS_INICIALES` con la misma clave que trae `env.example` de acá, así que
  copiando ambos `.env` entra sola. Es una entrada de configuración, no código: el backend no
  tiene usuario, rol ni permiso programado para el simulador, y borrar esta carpeta deja, como
  mucho, una cuenta sin uso que se da de baja desde la vista Usuarios (o se quita de la lista).
- **Si la da de alta el Administrador** desde el dashboard, nace con clave temporal: hasta que se
  cambia, el backend responde `403` a todo. Entrá una vez al dashboard con esa cuenta, cambiá la
  clave y poné la nueva en `BACKEND_CLAVE`. La barra de estado lo avisa ("clave temporal").
- **Cómo la usa.** El servidor inicia sesión la primera vez que necesita el backend, guarda la
  cookie `YERBA_SESION` en memoria y la adjunta a todo lo que reenvía `/backend/**` y al sondeo
  de estado (`server/backend-session.ts`). La cookie que traiga el navegador no se reenvía nunca:
  el backend ve la cuenta del simulador y nada más.
- **Sesión vencida.** Los sondeos son `GET` y no cuentan como actividad, así que la sesión vence
  por inactividad como cualquier otra. Ante un `401` reinicia sesión y reintenta **una sola
  vez**; si varias peticiones lo reciben a la vez, abren una única sesión nueva entre todas.
- **Sin credenciales** no intenta iniciar sesión: el backend rechaza las peticiones, la barra
  dice "sin credenciales" y la publicación de telemetría por MQTT sigue funcionando, porque no
  pasa por el backend. Si el backend rechaza las credenciales, dice "credenciales rechazadas" y
  no vuelve a probarlas hasta 30 s después.
- **`/api/camara/v1/**` no lleva la sesión.** Es el contrato del dispositivo, con sus propios
  tokens; un `401` ahí es del dispositivo y reiniciar la sesión no lo arreglaría.

**Lo que el rol Servicio no trae por defecto.** Regenerar la topología (`POST /api/topologia`)
pide `topologia.gestionar`, y el rol no lo tiene: el panel muestra el `403` del backend. Hacelo
desde el dashboard, o que el Administrador le agregue ese permiso al rol en la matriz — sabiendo
que vale para todas las cuentas de servicio, no sólo para la del simulador. La emisión
automática tampoco puede leer el intervalo de sensado (`configuracion.ver`) y usa
`AUTO_EMISSION_INTERVAL_MS`.

---

## Cómo agregar el escenario de una regla

Una regla del backend se **apaga** desde el panel con sólo existir (`GET /api/demo/rules` lista toda
pieza de decisión, también las que corren por macro-zona o como servicio). Para poder **accionarla** el
simulador necesita su *escenario*: qué telemetría, clima y diagnóstico previo hacen que dispare en el
siguiente pulso. Mientras no lo tenga, el panel la muestra en gris ("sin escenario").

1. Crear `src/scenarios/<regla>.ts` con un `RuleScenario` (contrato en `src/scenarios/types.ts`):
   - `key`, `label`, `description`, `branch`.
   - `backendRules`: los nombres con que el backend la lista en `/api/demo/rules`.
   - `needsCooldownReset` / `needsDiagnosis`.
   - `apply({ thresholds })`: función **pura** que devuelve un `ScenarioPatch` con la telemetría
     (cada métrica con la línea que ve el operador), el clima y el diagnóstico que necesita. Los
     umbrales salen siempre de `thresholds` (el catálogo del backend), nunca de números sueltos.
   - `conflicts`, si es incompatible con otra (se declara de un solo lado; la detección es simétrica).
   - `applyOrder`: si dos escenarios piden la misma métrica gana el de mayor orden.
2. Sumarlo a `SCENARIOS` en `src/scenarios/index.ts`.
3. Escribir su test (`tsx --test`) y agregarlo al script `test` de `package.json`.

`ruleConditions.ts` no se toca: no conoce ninguna regla en particular.

**Escenarios que no son telemetría.** Un escenario también puede pedir `silenceZone: true` (la zona destino
deja de contestar `LEER_AHORA`, sin publicar lectura) y `pulses: N` (pide N pulsos con motor, uno tras otro,
esperando que termine cada uno: `src/pulseChain.ts`). Es lo que hace **Nodo sin respuesta (S-02)**
(`NODE_OFFLINE`, accionable sobre `EscalamientoNodo`): silencia la zona y encadena 3 pulsos, los que
declaran el nodo fuera de servicio y ordenan el estado seguro. Con los plazos de fábrica cada pulso tarda
~3 min en darse por perdido; para acortarlo, bajar `sensado.espera-respuesta` y `sensado.reintentos` en el
dashboard. La zona sigue muda hasta pulsar «Volver a responder» (`PUT /api/silenced-zones/{zona}`), y la
**emisión automática tiene que estar activa**: con la emisión apagada ninguna zona contesta, silenciada o no.
Los controles de la pieza `EscalamientoNodo` (apagar, bypass `safe-state`, cooldown `retry-offline`) están en
Telemetría → Control de reglas.

**Bloqueo manual (S-01).** El escenario `LOCK` (accionable sobre `ManualLockRule`) publica el sustrato seco de R-01 y
pide `manualLock`: antes del pulso crea un bloqueo por `POST /backend/api/bloqueos`, sobre toda la zona o sobre un
sector (selector «Alcance del bloqueo»). El pulso con motor no debe ordenar riego y la traza del sector muestra el
bloqueo como causa. El bloqueo queda vigente hasta que se lo levanta en «Bloqueos vigentes» (`DELETE
/backend/api/bloqueos/{id}`); si ya había uno sobre ese alcance se usa ese. No se puede combinar con R-04 ni con
«Nodo sin respuesta»: la causa de que no se riegue sería ambigua. La cuenta de rol Servicio necesita
`bloqueos.ver` y `bloqueos.gestionar` (vienen en la matriz por defecto; en una base ya sembrada, correr
`migracion-permisos-bloqueo.sql` del backend).

**Lecturas fijadas.** La respuesta a un `LEER_AHORA` es la lectura con la que decide el motor, y la emisión automática la
arma al azar: una lectura que el escenario publicó antes del pulso queda pisada por ella. Por eso un escenario puede pedir
`pinnedReadings`: métricas que viajan en **cada** respuesta de la zona destino (`PUT /api/pinned-readings/{zona}`, en memoria,
siempre arranca vacío) hasta pulsar «Soltar» en «Lecturas fijadas». No se publican como una lectura suelta: tienen que estar en
la respuesta al pulso. Los valores **no se recortan** (ni el servidor ni los campos de las tarjetas de sensor), así que también se
puede cargar a mano una lectura imposible en *Envío de lecturas*. Los dos escenarios siguientes la usan, y **ambos necesitan la
emisión automática activa** (con la emisión apagada nadie contesta el pulso).

**Lectura inválida (S-03).** El escenario `INVALID_READING` (accionable sobre `ReadingValidationGuard`) fija `humSus` = el
máximo del catálogo (`seguridad.rango-hum-max`) + 12 (112 % con los valores de fábrica): el pulso, la relectura y las siguientes son
todas inválidas y se ve el flujo entero. El backend pide hasta `seguridad.relecturas-invalidas` relecturas, descarta esa métrica en
el ciclo (R-01…R-06 quedan «sin dato») y deja un `WARNING` «Lectura inválida en Humedad de sustrato». El `CRITICAL` aparece si la
métrica vuelve a fallar en el **ciclo de lectura siguiente** (por defecto, 4 h después; dos pulsos del mismo ciclo cuentan como
uno). No se combina con R-01…R-04, el bloqueo manual, el nodo mudo ni «Hardware incompleto». Los controles de la pieza
`ReadingValidationGuard` (apagar, bypass `retries`) están en Telemetría → Control de reglas.

**Hardware incompleto (S-04).** El escenario `INCOMPLETE_HARDWARE` (accionable sobre `IncompleteHardwareRule`) fija el sustrato
seco de R-01 y le pide a la zona destino **un sector sin electroválvula registrada**: el pulso debe abortarle el riego con
«hardware incompleto» (sin alerta) mientras los sectores que sí la tienen riegan. **El simulador no da de baja ninguna válvula**:
el registro de hardware no tiene esa operación y él sólo lo lee (`GET /backend/api/hardware`); busca los sectores que ya no tienen
la suya (el `data.sql` de desarrollo equipa pocos, así que suelen sobrar) y los lista en la vista previa; su traza se mira en
Motor de reglas → Inspector. Si la zona no tiene ninguno, o el backend rechaza la lectura, el panel lo dice y no deja ejecutar. Necesita
que la cuenta de rol Servicio tenga `hardware.ver` (viene en la matriz por defecto; en una base ya sembrada, correr
`migracion-permisos-hardware-servicio.sql` del backend). No se combina con R-02, R-04, el bloqueo manual, el nodo mudo ni
«Lectura inválida»: la causa de que no se riegue sería ambigua. Los controles de la regla (apagar, bypass `hardware`) están en
Telemetría → Control de reglas.

**Fallas del nodo (S-05).** El ESP32 real detecta una falla, corta el actuador por su cuenta y la publica **una vez** en
`nursery/zone/{zona}/errores`; el backend marca el dispositivo como averiado y lo saca de lo autónomo hasta que alguien registra la
reparación. El simulador publica ese mismo mensaje en el broker (`POST /api/node-errors` de su servidor, que arma el payload del
contrato con `buildNodeError`): **no simula el hardware** detrás (el caudalímetro no existe todavía), sólo el aviso. Son cuatro
escenarios:

| Escenario | Qué hace | Qué se ve |
|---|---|---|
| `FAULT_NO_FLOW` «Sin caudal» | Publica `SIN_CAUDAL` en el sector elegido (uno de la zona **con** su electroválvula registrada) con el sustrato seco fijado | «Falla hidráulica» en Hardware, alerta `CRITICAL` que pide **riego manual**, y el pulso no riega ese sector (tampoco R-02) |
| `FAULT_OVERFLOW` «Caudal excesivo» | Publica `CAUDAL_EXCESIVO` (180 L/h) con el sustrato húmedo fijado | Lo mismo, pero la alerta **no** pide riego manual |
| `FAULT_SHADE_LIMIT` «Falla de mediasombra» | Publica `MEDIASOMBRA_FIN_DE_CARRERA` (final de «cerrada») sin sector | «Falla mecánica en mediasombra» en toda la zona, alerta `CRITICAL` que pide cubrir a mano; `ShadingRule` deja de mover la malla |
| `REPAIR` «Reparación» | Registra la reparación (`POST /backend/api/hardware/{id}/reparacion`) de **todos** los dispositivos averiados de la zona | La falla se levanta, queda el evento «Reparación» en el historial y el siguiente pulso vuelve a regar el sector |

Antes del pulso el panel espera (hasta unos 4 s) a que el registro de hardware muestre la marca; si no aparece avisa con «⚠» y sigue:
es justo lo que se ve con el receptor de errores apagado. **El simulador nunca marca ni limpia una falla por su cuenta**: publica el
aviso o usa el endpoint público de reparación, el mismo que el dashboard. Necesita que la cuenta de rol Servicio tenga `hardware.ver`
y `hardware.reparar` (vienen en la matriz por defecto; en una base ya sembrada, correr `migracion-permisos-hardware-servicio.sql` y
`migracion-permisos-hardware-reparar.sql` del backend). Las fallas de caudal necesitan la emisión automática activa (el sustrato viaja
en la respuesta al pulso). No se combinan con R-02, R-04, el bloqueo manual, el nodo mudo, «Lectura inválida» ni «Hardware incompleto»
(la falla de la mediasombra, sólo con el bloqueo, el nodo mudo y «Hardware incompleto»); «Sin caudal» y «Caudal excesivo» entre sí
tampoco, ni ninguna falla con la reparación. Los controles de las piezas (apagar el receptor `HardwareFaultService`, bypass `fault` de
`HydraulicFaultRule` y `ShadeFaultRule`) están en Telemetría → Control de reglas.

---

## Qué endpoints del sistema usa

Ninguno es del simulador. Todos son superficie **pública** de la plataforma:

| Endpoint | Quién lo va a usar en producción |
|---|---|
| `GET /api/topologia`, `GET /api/nursery` | el dashboard |
| `POST /api/topologia` | el panel de topología del dashboard |
| `POST /api/capturas/ordenes` | el planificador de pasadas del riel |
| `GET /api/capturas/ordenes/{id}` | idem |
| `GET /api/camara/dispositivos`, `POST /api/camara/vinculacion` | el backoffice |
| `POST /api/diagnosticos` | el servicio de inferencia |
| `GET /api/hardware`, `POST /api/hardware/{id}/reparacion` | el panel de Hardware del dashboard (la reparación la registra quien arregla el equipo) |
| `GET/PUT /api/demo/rules`, `POST/DELETE /api/demo/weather`, `GET/PUT /api/demo/weather/offline`, `GET/PUT /api/demo/telemetry-source` | sólo existen con el backend en modo demo (en producción dan 404) |

Por eso el recorrido que se ensaya acá es literalmente el que va a correr solo cuando exista el
modelo. **El simulador no toca `/api/camara/v1/**`**: ese es el contrato del dispositivo de
captura, y lo implementa la app de cámara.

Tampoco le habla nunca a la app de cámara. Emite la orden al backend, el backend la empuja al
teléfono por SSE, el teléfono sube la imagen, y el panel consulta el avance de la orden.

---

## Estructura

```
simulador/
├── server/          Express + Vite en modo middleware: un proceso, un puerto
│   ├── index.ts       arranque y cableado
│   ├── config.ts      configuración desde el entorno
│   ├── contract.ts    espejo de contrato.h (claves y unidades del nodo)
│   ├── mqtt.ts        publicador al broker
│   ├── store.ts       sensores simulados, persistidos en data/
│   ├── emission.ts    emisión automática, zonas silenciadas y lecturas fijadas
│   ├── api.ts         API interna (/api/**)
│   ├── proxy.ts       proxy al backend (/backend/**)
│   └── backend-session.ts  sesión de la cuenta de servicio ante el backend
├── src/             UI React, con estilos propios
│   ├── scenarios/     un escenario por regla (cómo se la acciona) y su registro
│   ├── happyPath.ts   decisión y orquestación del happy path de la Demo Expo (modo simulador / ESP32 real)
│   ├── pulseChain.ts  encadena N pulsos de sensado esperando que termine cada uno
│   ├── hardwareCoverage.ts  qué sectores de una zona no tienen electroválvula registrada (sólo lee el registro)
│   └── ruleConditions.ts  umbrales, conflictos y combinación de los escenarios elegidos
└── data/            estado local (no se versiona)
```

**Idioma del código**: inglés, salvo lo que ve el operario —textos de la interfaz, mensajes de
error que llegan a pantalla y valores de dominio como `Clorosis`—, que va en español. Es la
convención del repo (`CLAUDE.md` §7).

---

## Detalles que sorprenden

- **La emisión automática no se persiste.** Siempre arranca apagada: levantar el simulador
  nunca debería empezar a pisar valores por su cuenta.
- **El contrato MQTT está espejado en tres lugares** — el firmware, `ContratoNodo.java` del
  backend y `server/contract.ts` acá. La fuente de verdad es
  `Desarrollo/embebido/comun/contrato.h`; si cambia una clave o una unidad, hay que actualizar
  los tres. Se acepta la duplicación porque compartirlo con el backend haría que esta carpeta
  dejara de ser borrable.
- **`ce` viaja convertida.** Acá se trabaja en dS/m (la unidad de la plataforma) y se publica en
  µS/cm (la unidad de la sonda). Si mandás `1.4` y el vivero muestra `1400`, la conversión se
  rompió.
- **`uv` no es radiación UV**: es % de luz de un LDR. La clave se conserva por compatibilidad
  con el firmware ya escrito.
- **Los sensores viven en `data/`**, no en la base del vivero. Borrar la carpeta borra los
  sensores de prueba y nada más.

---

## Modo estático del dashboard

Si lo que querés es *mostrar* cómo se ve el sistema, no hace falta levantar nada de esto: el
dashboard tiene una demo ilustrativa completa, sin backend ni broker.

```bash
cd Desarrollo/frontend && npm run dev:demo
```

El simulador es para lo otro: probar el sistema **real** en funcionamiento, inyectándole
hardware simulado.
