# Demo Expo y vivero: cómo conviven

> **Estado: análisis en curso.** Borrador del 08/10/2026, actualizado el 09/10. La ESP32 no está
> disponible hasta el sábado 10/10: se puede adelantar código y contrato, pero nada se prueba con
> hardware hasta entonces.
>
> **Ya decidido (09/10):** lo que se implementa sí o sí son las **secuencias guionadas** del §9.
> Son tres botones nuevos en Demo Expo (riego, mediasombra y lectura de sensores), calcados de la
> pasada del riel. Las tres opciones del §5 (comandos manuales, "leer ahora" y escenarios del
> simulador) quedan para discutir en el equipo.
>
> **Implementado (09/10):** las tres secuencias están en código, sin probar con hardware: ver el
> estado en el [§9.5](#95-estado-de-la-implementación).

## 1. El problema

El motor de reglas se pensó para el vivero de Misiones: un nodo testigo por macro-zona que
publica cada cierto intervalo, umbrales agronómicos reales y tiempos de horas.

La demo es otra cosa. Es en Buenos Aires, sin telemetría periódica, y la idea es **disparar los
casos a mano** frente a los profes, que probablemente quieran ver el motor funcionando.

**Prioridad: la demo.** La instalación en el vivero no se hace, al menos por ahora.

## 2. Tesis: la expo es un vivero más

El repo ya decidió que **el backend no tiene modos** (`CLAUDE.md` §6 y §6.2). Meter un
`if (demo)` sería volver atrás.

Entre el vivero y la expo no cambia el **código**, cambian los **datos**. La expo es un vivero
chiquito: 1 zona, 2 sectores, en Buenos Aires, con umbrales calibrados para que las cosas pasen
en minutos y no en horas.

> **Regla para no caer en un modo encubierto:** si algo de la expo necesita una rama en el código,
> está mal. Se resuelve con configuración, con el catálogo de parámetros o con el seed.

Esto vale también para el interruptor "Demo Expo" que ya existe: sólo muestra u oculta una
pestaña. No debe condicionar ningún comportamiento.

## 3. Cómo se dispara hoy el motor (verificado en el código)

El motor **no tiene un reloj propio**. Evalúa en dos momentos:

| Origen | Dónde | Qué hace |
|---|---|---|
| `TELEMETRIA` | `NurseryService.java:587` | Llega una lectura por MQTT y se evalúan las reglas. **Esto es lo que dispara acciones.** |
| `BARRIDO` | `NurseryWatchdog.java:157` | Barrido periódico del watchdog, sin métricas frescas. Detecta lo que quedó sin datos. |

El "cada X tiempo" del vivero lo pone **el ESP32**, que publica a intervalos. El motor sólo
reacciona.

**Conclusión:** la periodicidad es del emisor, no del motor. Disparar una regla es lo mismo que
publicar una lectura que la haga disparar, así que **no hay que tocar el motor**.

## 4. Qué separa hoy al vivero de la expo

| Tema | Hoy (vivero) | Qué pasa en la expo | Dónde se resuelve |
|---|---|---|---|
| Disparo manual de actuadores | **No existe endpoint** para comandar válvula ni mediasombra a mano | Es lo primero que se quiere mostrar | Capacidad nueva, **de producción** (un operador real también necesita un override manual) |
| Tiempos del riego | R-01 → 600 s / 5 L, pausa tras aplicar, tope diario | Nadie espera 10 minutos frente a un stand | Catálogo de parámetros (perfil "expo") |
| Ventana horaria (R-04) | Riega sólo en ciertas horas | Puede **bloquear** el riego justo a la hora de la expo | Catálogo |
| Pronóstico (R-05) | `yerbanalytics.weather.lat/lon` = Misiones (`application.properties:116`) | Una lluvia en Misiones pospondría el riego en Buenos Aires | Propiedad de despliegue (`lat/lon` de Buenos Aires) |
| Vigencia de la lectura | Sin lectura en ~90 s (`seguridad.antiguedad-max-lectura`), la zona pasa a "sin señal" y el despacho de riego **pausa** | Entre disparo y disparo, el dashboard muestra la zona caída y el riego se frena | Umbral de vigencia en el perfil expo, o dejarlo y usarlo como caso a mostrar |
| Topología | 600 sectores (`data.sql`) | 1 zona x 2 sectores (la base de dev ya está así) | Seed / topología |

## 5. Las tres formas de disparar, y dónde vive cada una

### 5.1 Comandos manuales de actuadores (no pasan por el motor)

Botones en la pestaña Demo Expo: **regar N segundos**, **desplegar** la mediasombra y
**enrollarla**.

- Es una capacidad de producción, no de demo: el operador del vivero también la necesita.
- Hace falta: endpoint en el backend, publicación MQTT al actuador, y que `vivero_esp32_red`
  escuche esos comandos. Hoy ese sketch sólo escucha el riel. Válvula, bomba y mediasombra por
  MQTT quedaron **fuera de alcance** en `add-pasada-riel`, así que esto lo reabre.
- A verificar: si los comandos manuales tienen que interactuar con `ManualLockRule` (que ya
  existe), para que el motor no pise lo que el operador acaba de hacer.

### 5.2 "Leer ahora" (opción B): sensor real → motor → actuador real

1. El backend publica un comando, por ejemplo `nursery/zone/{zonaId}/command` con la acción
   "leer ahora" (el tópico es tentativo).
2. El ESP32 lo recibe, lee sus sensores **reales** y publica la telemetría por el tópico de
   siempre.
3. El motor evalúa como con cualquier lectura. No sabe que fue a pedido.

- Es el mismo patrón que el riel: lo ordena el backend y lo ejecuta el ESP32.
- También es de producción: un operador riega a mano y quiere ver ya cómo quedó el sustrato.
- **Es un cambio de contrato**: va en `Desarrollo/embebido/comun/contrato.h` y en sus espejos
  (`ContratoNodo.java`, `simulador/server/contract.ts`, firmware).
- Limitación: en Buenos Aires no se controla qué valor sale, así que no se puede garantizar qué
  regla dispara. Se puede ayudar con estímulos físicos: maceta seca o mojada, linterna sobre el
  LDR (`uv` es % de luz, no radiación UV).

### 5.3 Escenarios del simulador: lectura inventada → motor → actuador real

En la UI del simulador (`:5180`), botones de escenario como "sustrato seco" o "mucha luz" que
publican esa lectura al broker.

- El simulador **ya publica** al broker con el mismo payload que el firmware, así que sólo
  faltan los presets en su UI.
- El sensor es simulado pero el actuador es **real**: la válvula se abre de verdad. El resultado
  está garantizado y el backend no se entera.
- Discurso honesto frente a los profes: "simulamos el sensor, todo lo demás es real".

### 5.4 Descartado: inventar lecturas desde el dashboard

Un botón en la pestaña Demo que "genere una lectura" obligaría al backend a aceptar telemetría
inventada por HTTP. Es una puerta para inyectar datos en producción, y es el acoplamiento que se
sacó al separar el simulador. Que el dashboard publique directo al broker es lo mismo con otra
cara: lo convierte en emisor de telemetría.

> **Regla: las lecturas inventadas viven SÓLO en el simulador. El dashboard pide cosas reales.**

## 6. Mapa propuesto

| Dónde | Botón | Qué es | Estado |
|---|---|---|---|
| Pestaña Demo Expo | Pasada del riel | Mueve el riel y saca dos fotos | Ya existe, se mantiene |
| Pestaña Demo Expo | Regar N s · Desplegar · Enrollar | Comando manual de actuador | Nuevo: endpoint + firmware + front |
| Pestaña Demo Expo | Leer ahora | Sensor real → motor → actuador real | Nuevo: contrato + firmware + endpoint |
| Simulador (`:5180`) | Escenarios | Lectura inventada → motor → actuador real | Nuevo: sólo presets en la UI |

Qué muestra cada una frente a los profes:

- Los **comandos manuales** muestran que el hardware responde.
- **Leer ahora** muestra el flujo 100 % real.
- El **simulador** muestra el motor con resultado garantizado.
- El **Inspector de `/reglas`** (grafo + traza de la última evaluación) es la vidriera del motor:
  "esta regla evaluó esto, por esto, y disparó esto".

## 7. Orden tentativo

1. Comandos manuales: riego por N segundos, desplegar y enrollar la mediasombra.
2. Perfil de parámetros "expo" y `lat/lon` de Buenos Aires.
3. Escenarios en el simulador.
4. "Leer ahora" (contrato nuevo).
5. Al final, el flujo completo sensores → reglas → actuadores con hardware real.

## 8. Preguntas abiertas

- [ ] ¿Cierra la división del §5.4, con las lecturas inventadas sólo en el simulador?
- [ ] ¿Qué hardware va a estar físicamente en el stand? Qué sensores van conectados al ESP32, y
      si la válvula mueve agua de verdad.
- [ ] La mediasombra, ¿es un motor con dos sentidos, un servo, un relé? Define el comando.
      Antecedente: el 27/09 se decidió (en las reglas v2, todavía sin reflejar en el código) que
      sea **binaria** (abierta/cerrada) y **una por macro-zona**, no por sector y no por
      porcentaje. Eso favorece un comando desplegar/enrollar. Pero el seed actual todavía tiene
      mediasombras por sector (`data.sql`, `DEV-103`, `DEV-106`…): hay que confirmar contra qué
      se modela el comando.
- [ ] ¿El perfil "expo" de parámetros se carga con un script SQL, con `PUT
      /api/rules/parametros`, o con un seed aparte?
- [ ] La vigencia de ~90 s: ¿se estira en el perfil expo o se muestra como caso?
- [ ] ¿Cómo interactúan los comandos manuales con `ManualLockRule`? Dato verificado: hoy **ningún
      código crea un `ManualLockEntity`**, así que la regla existe pero nunca se activa.

## 9. Decidido: secuencias guionadas desde Demo Expo

Son tres demos "estáticas": cada una sigue un **plan fijo de pasos**, se dispara con un botón de
la pestaña Demo Expo y se sigue en vivo, igual que la pasada del riel.

> **Comandan el actuador directo y NO pasan por el motor de reglas.** El motor se muestra con los
> escenarios del simulador (§5.3) y con el Inspector de `/reglas`. Esto no rompe la regla del
> §2: no hay ninguna rama `if (demo)`. Una secuencia es una capacidad más del backend, como la
> pasada, y no condiciona ningún otro comportamiento.

### 9.1 Las tres secuencias

| Secuencia | Pasos | Comando MQTT | Cuándo termina bien |
|---|---|---|---|
| **Riego** | ABRIR → ESPERAR N s → CERRAR | `valve ON {durationSec}` y después `valve OFF`, por el tópico de comando del sector | Con ACK `SUCCESS` de los dos comandos |
| **Mediasombra** | DESPLEGAR → ESPERAR → ENROLLAR | `shade SET {targetPct: 0}` y después `shade SET {targetPct: 100}` | Con ACK `SUCCESS` al tocar cada final de carrera |
| **Lectura** | PEDIR → ESPERAR TELEMETRÍA → MOSTRAR | Comando nuevo de zona: "leer ahora" | Cuando llega la telemetría de esa zona posterior al pedido |

### 9.2 Lo que comparten con la pasada (`PasadaRielService`)

- Estado **en memoria**, sin entidad JPA, con estados por paso (PENDIENTE, EN_CURSO, OK, ERROR,
  OMITIDO) y por secuencia (EN_CURSO, COMPLETADA, FALLIDA, CANCELADA).
- **Una sola a la vez**, y tampoco mientras corre una pasada: hay un único ESP32. Si hay otra en
  curso, la API responde 409.
- **Cancelar** omite los pasos pendientes y deja el actuador en un estado seguro: válvula cerrada
  o mediasombra enrollada.
- Hay un **timeout por paso**: si no llega el ACK o la telemetría a tiempo, el paso falla.
- El frontend hace **polling** de la secuencia actual, como `usePasada`, y tiene su versión mock
  para `VITE_DATA_SOURCE=mock`.

### 9.3 Decisiones de detalle

- **El riego se comanda como `valve`, aunque en el stand lo mueva una bomba.** `vivero_esp32_red`
  no tiene electroválvula: tiene un driver de bomba (IN3/IN4/ENB). El backend habla de dominio
  ("regá") y el firmware de la expo decide qué hardware lo cumple. Así el comando es el mismo que
  usará el vivero real. `pump` queda para los insumos.
- **La mediasombra es binaria en el hardware y porcentual en el contrato.** `targetPct` es
  apertura, así que 0 % es desplegada y 100 % es enrollada. El firmware mueve el motor hasta el
  final de carrera que corresponda (pines 32 y 33). No hace falta cambiar el contrato.
- **El backend tiene que empezar a escuchar el ACK de los actuadores.** *(Hecho: ahora lo escucha.)*
  Antes no lo hacía, y sin ACK una secuencia no sabe si el paso se cumplió. El tópico y el formato ya están en
  `contrato.h` (`.../sector/{id}/ack`, `SUCCESS` o `ERROR`).
- **"Leer ahora" sí es un cambio de contrato.** Va en `contrato.h` y en sus espejos
  (`ContratoNodo.java`, `simulador/server/contract.ts`). La telemetría de respuesta es la de
  siempre, sin `commandId`: el backend la correlaciona como la primera lectura de esa zona
  posterior al pedido.
- **La lectura entra por la ingesta normal, así que el motor la evalúa.** Puede encolar un riego
  real si el sustrato da seco. La secuencia no lo evita, a propósito: es el sistema real. Lo que
  hace es mostrar los valores y la traza de la evaluación.

### 9.4 Lo que falta saber del hardware

- [ ] ¿Qué sensores van a estar conectados al ESP32 del stand? Hoy `vivero_esp32_red` **no lee
      ninguno**. Sin este dato, la secuencia de lectura queda lista en el backend y el frontend, y
      el firmware con la lectura de sensores como hueco a completar.
- [ ] ¿La bomba mueve agua de verdad en el stand?
- [ ] ¿Qué sector de la topología de la expo representa a la bomba y a la mediasombra? Todo
      comando va por sector (`nursery/zone/{z}/sector/{s}/command`).

### 9.5 Estado de la implementación

Cambio OpenSpec: [`openspec/changes/add-secuencias-demo-expo/`](../openspec/changes/add-secuencias-demo-expo/)
(proposal, design, specs y tasks). Mergeado en tres PRs, uno por pista:

| Pista | PR | Qué quedó |
|---|---|---|
| Firmware + contrato | #29 | `contrato.h` y `contract.ts` con el comando de zona; `vivero_esp32_red` con válvula (bomba), mediasombra, ACK y "leer ahora" |
| Frontend | #30 | Sección "Secuencias" en Demo Expo, `useSecuencia`, mock |
| Backend | #31 | `/api/secuencias`, escucha del ACK, `LEER_AHORA`, guardia compartido con la pasada |

| Punto | Estado |
|---|---|
| Las tres secuencias (§9.1), estado en memoria, cancelar, una a la vez y 409 cruzado con la pasada | Implementado y testeado (sin hardware) |
| El backend escucha el ACK de los actuadores (§9.3) | Implementado |
| "Leer ahora" en `contrato.h`, `ContratoNodo.java` y `contract.ts` | Implementado |
| Firmware de bomba, mediasombra y "leer ahora" | Compila; **sin probar con hardware** |
| Lectura de sensores en el firmware | **Hueco marcado** (`leer_sensores()`), a completar según §9.4 |
| Puesta en marcha con el ESP32 (E.1–E.6 del cambio) | Pendiente, desde el 10/10 |

Desvío respecto de lo que se pensó acá: la lectura entra por un segundo adaptador MQTT del backend
(`-lectura`), así que el broker entrega cada telemetría dos veces; el de la ingesta normal no se tocó.

Las preguntas del §9.4 **siguen abiertas**: ninguna se respondió porque todavía no hay respuestas del
hardware. El sector destino se resuelve por ahora como la zona de menor número y su primer sector
(topología 1×2 del stand), y el firmware lo fija con `NODO_ZONA_ID` / `NODO_SECTOR_ID` en `config.h`.

## 10. Referencias

- Pasada del riel: `openspec/changes/add-pasada-riel/` (su `proposal.md` lista lo que quedó fuera
  de alcance).
- Contrato MQTT: `Desarrollo/embebido/comun/contrato.h`; riel en `mqtt/ContratoRiel.java`.
- Secuencias guionadas: `openspec/changes/add-secuencias-demo-expo/`; README del backend, sección
  "Secuencias de la Demo Expo".
- Firmware de la expo: `Desarrollo/embebido/prototipo_hardware/vivero_esp32_red/`.
- Motor: `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/engine/` (reglas en
  `engine/rules/`, catálogo en `engine/parametros/`).
- Diferencias motor vs reglas v2: `diferencias-motor-reglas-vs-reglas-v2.md`.
