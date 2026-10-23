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
  riego, insumo y mediasombra en **un solo pulso con motor**, con las tres reglas como están, sobre el primer sector de la primera macro-zona. Con la fuente en **Simulador** enciende la dosificación (`SupplyRule`, que el backend arranca retirada), saltea la ventana horaria y el límite diario de aplicación y asegura stock en los tanques de la zona (ver «Aplicación de insumos»); con la fuente en **ESP32 real** no toca ninguna regla y sólo avisa si la dosificación sigue retirada. No espera que una acción termine: el nodo (el ESP32 o el simulador) serializa las cargas y el motor puede ordenarlas juntas. La tarjeta muestra, por regla, lo que dijo la traza y el avance de la acción leído del estado de los actuadores: válvula `En cola` → «en cola», `Regando` → «en curso», `Cerrada` → «hecho»; bomba `En cola` / `Dosificando` / `En espera`. La mediasombra no tiene estado derivado del nodo: queda «ordenada». Con la fuente
  en **Simulador** genera todo: lectura (`humSus` entre el umbral crítico y el de riego, `humAmb` crítica), diagnóstico de
  IA nuevo (por el flujo de captura) y UV por encima del umbral; pone los cooldowns en 0 y saltea la ventana horaria
  R-05. Con la fuente en **ESP32 real** no inyecta nada (sólo quita un clima simulado): usa el sensor, el pronóstico y
  el diagnóstico de una pasada del riel previa, y avisa si no hay uno utilizable. Al final lee la traza del Inspector y
  muestra, por regla, si actuó y por qué. Lógica en `src/happyPath.ts`; reutiliza los escenarios R01 + SUPPLY + SHADING + APPLICATION_PRECONDITIONS.
- **Abrir / cerrar mediasombra** (pestaña *Demo Expo*, tarjeta de *Secuencias*) — dos botones de un clic y una sola
  acción: *Abrir* enrolla (`targetPct` 100) y *Cerrar* despliega (`targetPct` 0). Pasan por la API de secuencias del
  backend (`MEDIASOMBRA_ABRIR` / `MEDIASOMBRA_CERRAR`), no por un publish directo, así el seguimiento de comandos y el
  estado del backend los ven. Se deshabilitan mientras corre otra secuencia o una pasada.
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
comandos de otras zonas son de un nodo real y sólo se loguean. Las reglas de cada ack están en
`server/contract.ts` (`simulatedAck`).

**La mediasombra es de la macro-zona** (una por zona): el backend manda su `shade SET` por el comando de zona
(`nursery/zone/{zona}/command`, con `"actuador":"shade"`, el mismo tópico del `LEER_AHORA`) y el simulador contesta el ack
por `nursery/zone/{zona}/ack`. Abrir y cerrar la malla desde *Demo Expo* (secuencias) funciona igual que antes.

**Cola de cargas del nodo.** El simulador se comporta como el firmware (`actuacion/cola_cargas`): corre a lo sumo
`SIM_NODE_MAX_LOADS` cargas a la vez (riego de un sector, insumo de un sector, movimiento de la mediasombra; por
defecto **1**, lo que aguanta la fuente del prototipo) y encola el resto en orden de llegada. Con la cola mandan los
mismos ACK que el nodo: `EN_CURSO` con `detalle.tipo` `en_cola` (con `posicion` y `esperaMaxSec`) o `iniciado`, y el
final de siempre (`SUCCESS`/`ERROR`). Un `valve OFF` / `pump OFF` actúa al instante y descarta lo encolado del mismo
sector y actuador (`ERROR cancelado`); un `shade SET` con `parametros.prioridad = "INMEDIATA"` (sólo lo manda el
estado seguro) no espera turno. Cambio de comportamiento: antes un OFF no cortaba el ON simulado en curso; ahora lo
corta con `cancelado`, como el firmware. Hay **una cola por macro-zona** (el simulador no sabe qué nodo atiende qué
sectores; la zona es el nodo testigo): con el límite en 1, una tanda de 10 válvulas se riega de a una, igual que en el
prototipo. La lógica es pura y vive en `server/nodeQueue.ts` (reloj y timers inyectados, probada con reloj falso).

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

**Aplicación de insumos (add-dosificacion-base).** Toda aplicación pasa por el `ApplicationService` del backend, que verifica
las precondiciones P-D1…P-D5 (diagnóstico vigente, intervalo y una por día, ventana 07–10 / 17–19, ≤ 30 °C, sin lluvia, humedad
de sustrato ≥ 45 %) y el stock del tanque antes de publicar la orden de bomba. Lo que no cumple queda pendiente y se reintenta.

- **`SupplyRule` y `DailyDoseLimitRule` arrancan retiradas** en el backend (`yerbanalytics.reglas.retiradas`): figuran
  «deshabilitadas» en la traza hasta que las reglas de aplicación de la v2 las reemplacen. Con el modo demo encendido se las
  vuelve a encender desde Telemetría → Control de reglas (el happy path en modo Simulador lo hace solo).
- **`ApplicationPreconditions`** es una pieza controlable más (Control de reglas): se apaga (no se aplica nada), se saltea cada
  guarda (`diagnosis`, `daily`, `window`, `temperature`, `rain`, `moisture`, `stock`) y se acortan los intervalos
  (`interval-fertilizer`, `interval-fungicide`, en minutos; 0 los elimina).
- El escenario **`APPLICATION_PRECONDITIONS`** es un *modificador*: no acciona una regla sino que deja cumplidas P-D3…P-D5 (`temp`
  22 °C, `humSus` 55 %, clima sin lluvia) y pide `ensureTankStock`: antes del pulso registra (20 L) o repone los tanques de la zona
  destino por `POST /backend/api/tanques/{zona}/{insumo}/recarga`. **No puede mover el reloj**: la ventana horaria se saltea con
  el bypass `window`. Se combina con la regla que pide la aplicación (hoy `SUPPLY`). Necesita que la cuenta de rol Servicio tenga
  `insumos.ver` e `insumos.gestionar` (vienen en la matriz por defecto; en una base ya sembrada, correr
  `migracion-permisos-insumos.sql` del backend).
- **`SUPPLY` ya no usa la temperatura** para volver crítico al sector: usa `humAmb` = 98 %. Con calor (> 30 °C) P-D3 no deja aplicar,
  así que un sector crítico por calor jamás podría dosificarse.

**Plan de nutrición (add-plan-nutricion · N-01 y N-02).** Son reglas **de macro-zona**: el motor las corre una vez por zona,
después de evaluar sus sectores, en el pulso con motor. Aparecen en Telemetría → Control de reglas como cualquier otra pieza
(`NutritionPlanRule` y `LowEcRule`, rama `INSUMO`) y se pueden apagar.

| Escenario | Qué hace | Qué se ve |
|---|---|---|
| `NUTRITION_PLAN` «Plan de nutrición (N-01)» | Lleva el cooldown `interval` de `NutritionPlanRule` a 0 (el plan vence en el próximo pulso) y asegura stock en los tanques de la zona (`ensureTankStock`) | El pulso pide fertilizante para los sectores de la zona (cada uno se aplica en su próxima ventana apta) y queda un `INFO` en el historial de la zona. En el detalle de la macro-zona, la tarjeta «Plan de nutrición» pasa a «Aplicando» o, si ya se aplicaron todos, a la próxima fecha |
| `LOW_EC` «CE baja (N-02)» | Fija `ce` = 0,3 dS/m (el umbral `nutricion.ce-baja` menos 0,2) en la **respuesta al pulso** (`pinnedReadings`), activa el bypass `samples` de `LowEcRule` (necesita el backend en modo demo) y lleva el cooldown `no-fertilized` a 0 | N-02 adelanta el plan en ese mismo pulso (N-01 corre justo después) y deja el `INFO` «CE baja: la fertilización de base del sustrato podría estar agotada»; la tarjeta dice «Adelantado por CE baja» o «Aplicando» |

- `NUTRITION_PLAN` y `LOW_EC` **no se combinan con «Nodo sin respuesta»** (el motor no decide sin respuesta); `NUTRITION_PLAN` tampoco con
  `LOCK` (con la zona bloqueada el plan queda vencido esperando). Entre sí sí se combinan, y con `APPLICATION_PRECONDITIONS` también:
  dejan cumplidas P-D3…P-D5 para ver los sectores aplicados en el mismo pulso (la ventana horaria se saltea con el bypass `window`).
- Si la zona ya tiene una **tanda abierta** (sectores esperando su ventana) o el plan ya está adelantado, N-01 no vuelve a pedir y N-02 no vuelve a
  avisar: es a propósito (una alerta por evento). Esperá a que termine o mirá el estado en la tarjeta de la zona.
- Los bypasses que pide un escenario (`bypassRules`) se **suman** a los que el operador ya tenía activos (`src/bypassFlow.ts`); nunca los reemplazan. Con
  el backend sin modo demo la corrida sigue y lo avisa en su registro.
- La emisión automática tiene que estar activa en `LOW_EC` (la CE viaja en la respuesta al pulso); al terminar, soltá la zona en «Lecturas fijadas».

**Reglas bióticas (add-reglas-biotico · F-F1, F-F2, F-P1, F-04 y F-06).** `FungalDamageRule` (F-F1) y `FoliarPestRule` (F-P1) son reglas
**de sector** que actúan una vez por diagnóstico nuevo; `FungalConditionRule` (F-F2) y `OutbreakRule` (F-04) son **de macro-zona**. El tope de
fitosanitarios (F-06) es una precondición más de `ApplicationPreconditions`. Todas aparecen en Telemetría → Control de reglas y se pueden apagar.

| Escenario | Qué hace | Qué se ve |
|---|---|---|
| `FUNGAL_DAMAGE` «Daño fúngico persistente (F-F1)» | Registra un diagnóstico *Daño fúngico* de severidad **Baja**, activa el bypass `persistence` de `FungalDamageRule` (cuenta como visto en las capturas seguidas), lleva los cooldowns a 0 y asegura stock en los tanques | El pulso pide fitosanitario para el sector y deja un `WARNING` «Alta probabilidad de hongo». Sin el bypass, un caso leve con ambiente seco **espera** a las próximas capturas |
| `FOLIAR_PEST` «Plaga foliar (F-P1)» | Registra un diagnóstico *Plaga foliar* **Baja** y fija 38 °C y 25 % de humedad ambiente en la **respuesta al pulso** (DPV ≈ 5 kPa, sobre los 3 kPa del máximo del catálogo); activa el bypass `temperature` de `ApplicationPreconditions` (con más de 30 °C una aplicación se difiere) | El pulso pide fitosanitario por calor seco y deja un `WARNING` «Alta probabilidad de plaga foliar» |
| `FUNGAL_CONDITION` «Condición predisponente al hongo (F-F2)» | Fija aire saturado (98 %), 23 °C y sustrato en el umbral `biotico.humedad-predisponente` + 3 en la respuesta al pulso, y activa el bypass `readings` de `FungalConditionRule` (basta una lectura de DPV bajo el umbral) | Un `WARNING` «Condiciones favorables a enfermedades fúngicas» de la macro-zona; **no aplica nada** y no necesita diagnóstico. Avisa una sola vez por evento: si la lectura anterior ya cumplía, el pulso no repite el aviso |
| `OUTBREAK` «Foco biótico (F-04)» | Registra el mismo diagnóstico *Daño fúngico* **Baja** en **tres sectores contiguos del medio** de la zona (uno con su captura cada uno, por la API pública de diagnósticos), lleva los cooldowns a 0 y asegura stock | El pulso pide fitosanitario para los **lindantes** de los tres (aunque estén sanos) y deja un `CRITICAL` «Foco de daño fúngico en MZ-N». Un lindante bloqueado, sin bomba o con falla hidráulica queda fuera y el aviso lo dice. F-F1 espera en los tres (el caso es leve); los afectados que son lindantes entre sí se tratan como lindantes por F-04 |
| `FUNGICIDE_CAP` «Tope de fitosanitarios (F-06)» | Registra *Daño fúngico* **Alta** (razón de sobra para F-F1) y activa el bypass `cap` de `ApplicationPreconditions` | Un sector que ya recibió tres fitosanitarios en 30 días recibe el cuarto. **Para ver el bloqueo**, ejecutá `FUNGAL_DAMAGE` hasta acumular tres aplicaciones en el sector y una vez más **sin** este escenario: se rechaza con el motivo «F-06 … tope» y queda un `WARNING` hasta que alguien registre la revisión (`POST /api/sectores/{id}/revision-fitosanitario`) |

- Los cinco **no se combinan con «Nodo sin respuesta» ni con «Bloqueo manual»** (la aplicación no puede ni pedirse). Los que registran su propio diagnóstico
  (`SUPPLY`, `FUNGAL_DAMAGE`, `FOLIAR_PEST`, `FUNGICIDE_CAP`, `OUTBREAK`) **tampoco se combinan entre sí**: un sector tiene un solo diagnóstico. `FUNGAL_CONDITION`
  necesita el sustrato húmedo y no se combina con `R01` ni `R02`.
- Con `APPLICATION_PRECONDITIONS` y el bypass `window` se ve la aplicación en el mismo pulso; sin ellos queda diferida hasta la próxima ventana apta.
- `FOLIAR_PEST` y `FUNGAL_CONDITION` fijan lecturas en la **respuesta al pulso**: la emisión automática tiene que estar activa y, al terminar, soltá la zona
  en «Lecturas fijadas».
- Los lindantes de `OUTBREAK` salen de la disposición por fila de la topología (`sectoresPorFila`): es un supuesto a validar con el vivero (ver el README del backend).

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
