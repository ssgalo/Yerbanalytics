# Design: add-secuencias-demo-expo

> Las §1 (MQTT) y §2.7 (REST) son la **interfaz cerrada** entre las pistas A (backend), B (frontend)
> y C (firmware + contrato). Nadie las cambia sin avisar a las otras dos.
>
> Fuente de la decisión: `docs-motor-reglas-e-integracion/analisis-demo-expo-vs-vivero.md` §9.

## 0. Flujo

```
Dashboard ──POST /api/secuencias {tipo, parametros}──▶ Backend (SecuenciaService, tick 1 s)
                                                        │ GuardiaHardware: ¿pasada o secuencia en curso? → 409
RIEGO        ABRIR    ──valve ON {durationSec: N+10}──▶ nursery/zone/Z/sector/S/command ──▶ ESP32 (bomba)
                      ◀──────────── ACK SUCCESS ────── nursery/zone/Z/sector/S/ack
             ESPERAR  N s (reloj del backend)
             CERRAR   ──valve OFF──▶ … ◀── ACK SUCCESS
MEDIASOMBRA  DESPLEGAR ──shade SET {targetPct: 0}──▶ … motor DC hasta el final de carrera ◀── ACK SUCCESS
             ESPERAR  N s
             ENROLLAR ──shade SET {targetPct: 100}──▶ … ◀── ACK SUCCESS
LECTURA      PEDIR    ──LEER_AHORA──▶ nursery/zone/Z/command ──▶ ESP32 lee sus sensores
             ESPERAR_TELEMETRIA ◀── nursery/zone/Z/telemetry (la de siempre: también la ingiere el motor)
             MOSTRAR  valores de esa lectura
Dashboard: GET /api/secuencias/actual cada 1 s mientras corre.
```

Las secuencias **comandan el actuador directo y no pasan por el motor**. No hay `if (demo)`: son una
capacidad más del backend, como la pasada, que funciona siempre y no condiciona ningún otro
comportamiento. El interruptor Demo Expo sólo oculta la pestaña.

---

## 1. Contrato MQTT (interfaz A ↔ C)

### 1.1 Comando de actuador (ya existe, sin cambios)

`nursery/zone/{zonaId}/sector/{sectorId}/command`, lo publica `engine/ComandoActuadorPublisher` (QoS 1
por `MqttConfig`; `contrato.h` dice QoS 2, el firmware suscribe 1). Las secuencias usan **sólo** estos:

```json
{"commandId":"<uuid>","actuador":"valve","accion":"ON","parametros":{"durationSec":20}}
{"commandId":"<uuid>","actuador":"valve","accion":"OFF","parametros":{}}
{"commandId":"<uuid>","actuador":"shade","accion":"SET","parametros":{"targetPct":0}}
{"commandId":"<uuid>","actuador":"shade","accion":"SET","parametros":{"targetPct":100}}
```

- `valve ON durationSec` es la **red de seguridad del firmware**: si el backend muere a mitad, el
  nodo cierra solo. Por eso el backend pide `N + timeout-ack-valvula-seg` y cierra él con `OFF` a los
  N s. Tope: `ContratoNodo.DURACION_VALVULA_MAX_SEG` (1200).
- `targetPct` es **apertura**: 0 = desplegada, 100 = enrollada. El hardware de la expo es binario y
  sólo acepta 0 o 100 (§4.4). No cambia el contrato.

### 1.2 ACK de actuador (ya existe en `contrato.h`; el backend empieza a escucharlo)

`nursery/zone/{zonaId}/sector/{sectorId}/ack`. Formato de `comun/util_json.cpp::serializarAck`:

```json
{"commandId":"<uuid>","status":"SUCCESS","detalle":{"tipo":"ok","durationSec":20}}
{"commandId":"<uuid>","status":"ERROR","detalle":{"tipo":"falla_mecanica"}}
```

- `detalle` es un **objeto** (en el evento del riel es un string): el backend lo lee como árbol JSON
  tolerante.
- Un único ACK por comando, al **terminar** de cumplirlo: válvula abierta/cerrada; mediasombra al tocar
  el final de carrera. No hay `ACEPTADO` como en el riel.
- `detalle.tipo` en `ERROR`, valores ya usados por `embebido/actuacion/` más uno nuevo:
  `comando_invalido`, `duracion_invalida`, `actuador_desconocido`, `falla_mecanica`, `sin_cambio`
  (éste va con `SUCCESS`), y **`reemplazado`** (nuevo: un comando posterior interrumpió el movimiento
  de la mediasombra). Se documentan en `contrato.h`.
- El ESP32 publica QoS 0 (PubSubClient); el backend suscribe QoS 1. Sin retain.

### 1.3 Comando de zona "leer ahora" (nuevo — cambio de contrato)

| | Valor |
|---|---|
| Tópico | `nursery/zone/{zonaId}/command` |
| Dirección | backend → nodo testigo de la zona |
| QoS | backend publica 1; el nodo suscribe 1. Sin retain |
| Respuesta | **La telemetría de siempre** por `nursery/zone/{zonaId}/telemetry`, **sin** `commandId` |

```json
{"commandId":"<uuid>","accion":"LEER_AHORA","parametros":{}}
```

- A nivel **zona** porque el sensado es por macro-zona (un nodo testigo por MZ, `CLAUDE.md` §2).
- Sin `actuador`: no es un actuador. `commandId` va igual, para el log del nodo y dedupe.
- Sin ACK. El nodo que no puede leer no publica nada y el backend lo ve como timeout.
- Correlación: la **primera telemetría de esa zona recibida después del pedido** (hora de recepción
  del backend, no el `timestamp` del payload: el ESP32 sin NTP manda segundos desde el arranque).

### 1.4 Dónde se documenta (los espejos)

| Espejo | Qué agrega | Pista |
|---|---|---|
| `Desarrollo/embebido/comun/contrato.h` (**fuente de verdad**) | Sección "Comando de zona": tópico (`contratoTopicComandoZona`), `ACCION_LEER_AHORA`, regla de correlación. En "Ack": valores de `detalle.tipo` (incluye `reemplazado`) | C |
| `backend/.../mqtt/ContratoNodo.java` | `TOPIC_COMANDO_ZONA`, `ACCION_LEER_AHORA`, `TOPIC_ACK` (patrón `+`), `STATUS_SUCCESS`/`STATUS_ERROR` | A |
| `Desarrollo/simulador/server/contract.ts` | `zoneCommandTopic(zonaId)`, `READ_NOW_ACTION`, tipo `ZoneCommand` y tipo `ActuatorAck`. **Sin comportamiento** (en inglés, como el resto del archivo) | C |
| `vivero_esp32_red/vivero_esp32_red.ino` | `#define` locales "espejo de contrato.h" (el sketch no incluye `comun/`) | C |

---

## 2. Backend (pista A)

### 2.1 Reutilizar o duplicar la pasada

| Pieza | Decisión | Por qué |
|---|---|---|
| Máquina de pasos | **Nueva** (`SecuenciaService`), calcando el patrón de `PasadaRielService` | La pasada está probada con hardware (03/10) y la expo es inminente. Generalizarla en un "motor de pasos" la reescribe sin ganancia: sus pasos (captura, `ACEPTADO`, republicar) no se parecen a los de un actuador |
| Exclusión mutua | **Compartida** (`GuardiaHardware`) | Hay un único ESP32 y dos servicios que lo comandan. Es la única regla que tiene que valer entre ambos |
| DTO y endpoints | **Propios** (`/api/secuencias`) | `Pasada` tiene claves de captura que acá no significan nada. Mezclarlos ensucia un contrato ya consumido por el front |
| Publicación de actuadores | **Reusar** `engine/ComandoActuadorPublisher` sin tocarlo | Es el único que conoce el tópico; inyectarlo no modifica `engine/` |
| Patrón interno | Igual que la pasada: cola sin lock desde Paho, `tick()` synchronized en carril propio, foto inmutable `volatile`, `Clock relojVivero` | Ya se sabe que funciona y el revisor lo reconoce |

### 2.2 Piezas

| Archivo | Rol |
|---|---|
| `mqtt/ContratoNodo.java` | Constantes del §1.3 y del ACK |
| `mqtt/ComandoZonaPublisher.java` | Publica el §1.3 por `MqttCommandGateway`. Devuelve `Resultado(publicado, commandId, error)` |
| `mqtt/AckActuador.java` | `record AckActuador(String commandId, String status, JsonNode detalle, String zonaId, String sectorId)`, tolerante a claves desconocidas |
| `mqtt/AckActuadorReceiver.java` | Deserializa, saca zona/sector del tópico y llama a `SecuenciaService.registrarAck`. JSON roto → WARN |
| `mqtt/LecturaZonaReceiver.java` | Saca `zonaId` del tópico, deserializa `MqttTelemetryPayload` y llama a `SecuenciaService.registrarTelemetria(zonaId, payload, reloj.millis())`. **No** toca `NurseryService` |
| `mqtt/MqttConfig.java` | Dos adaptadores **nuevos** (`-ack` y `-lectura`); el de telemetría y el del riel quedan idénticos |
| `service/UsoDelHardware.java` | `interface UsoDelHardware { Optional<String> ocupadoPor(); }` — mensaje 409 si está en uso |
| `service/GuardiaHardware.java` | Exclusión mutua (§2.4) |
| `service/PasadaRielService.java` | **Único toque a la pasada**: implementa `UsoDelHardware` e inicia a través del guardia |
| `service/SecuenciaService.java` | Orquestador (§2.3) |
| `service/SecuenciaRechazadaException.java` | 409 · `service/SecuenciaInvalidaException.java` → 400 |
| `config/SecuenciaProperties.java` | `@ConfigurationProperties("yerbanalytics.secuencia")` |
| `config/SchedulersConfig.java` | Bean `secuenciaScheduler` (1 hilo, prefijo `secuencia-sched-`) |
| `controller/SecuenciaController.java`, `dto/Secuencia.java`, `dto/PasoSecuencia.java`, `dto/LecturaSecuencia.java`, `dto/IniciarSecuencia.java` | REST §2.7 |

Adaptadores nuevos (mismo molde que `rielInboundAdapter`):

```java
// clientId + "-ack",     tópico ContratoNodo.TOPIC_ACK = "nursery/zone/+/sector/+/ack" → ackActuadorReceiver
// clientId + "-lectura", tópico ${yerbanalytics.mqtt.telemetry-topic}                  → lecturaZonaReceiver
```

El segundo se suscribe al **mismo** tópico que la ingesta, con su propio cliente: así la telemetría
que alimenta al motor queda byte a byte igual y la secuencia se entera sin acoplarse a
`NurseryService`. Costo: el broker entrega cada lectura dos veces al backend (despreciable).

Propiedades (`application.properties`):

```properties
yerbanalytics.secuencia.timeout-ack-valvula-seg=10
yerbanalytics.secuencia.timeout-ack-mediasombra-seg=45
yerbanalytics.secuencia.timeout-lectura-seg=20
yerbanalytics.secuencia.tick-ms=1000
```

- 45 s de mediasombra: el firmware se rinde a los 30 s (`mediasombra_enrollar`, `vivero_esp32_red.ino:596`).
- 10 s de válvula: abrir es instantáneo; cubre la latencia del broker con margen.

### 2.3 Máquina de estados

Estados de paso `PENDIENTE → EN_CURSO → OK | ERROR`, o `OMITIDO`. Estados de secuencia
`EN_CURSO → COMPLETADA | FALLIDA | CANCELADA`. Mismos strings que la pasada.

| Secuencia | n=1 | n=2 | n=3 (**paso seguro**) |
|---|---|---|---|
| `RIEGO` | `ABRIR` valve ON | `ESPERAR` N s | `CERRAR` valve OFF |
| `MEDIASOMBRA` | `DESPLEGAR` shade 0 | `ESPERAR` N s | `ENROLLAR` shade 100 |
| `LECTURA` | `PEDIR` LEER_AHORA | `ESPERAR_TELEMETRIA` | `MOSTRAR` (sin paso seguro) |

**Paso de comando** (ABRIR, CERRAR, DESPLEGAR, ENROLLAR), al entrar: publica, guarda `commandId` y
`publicadoEn`.
- Publicación fallida → `ERROR PUBLICACION_FALLIDA`.
- ACK `SUCCESS` con su `commandId` → `OK`.
- ACK `ERROR` → `ERROR`, `codigoError = detalle.tipo` en mayúsculas (`FALLA_MECANICA`…), detalle legible (§2.5).
- Sin ACK a `timeout-ack-*-seg` → `ERROR ACTUADOR_SIN_RESPUESTA`. **Sin republicar**: el ACK de
  actuador no tiene `ACEPTADO`, así que no hay forma de distinguir "no llegó" de "está moviéndose".

**ESPERAR**: al entrar guarda `esperaHasta = ahora + N s`; `OK` cuando `ahora >= esperaHasta`.

**PEDIR**: publica `LEER_AHORA`, guarda `pedidoEn`; publicado → `OK` al instante; falla → `ERROR PUBLICACION_FALLIDA`.
**ESPERAR_TELEMETRIA**: la primera telemetría de `zonaId` con `recibidaEn >= pedidoEn` → guarda la
lectura y `OK`. Sin lectura a `timeout-lectura-seg` → `ERROR SIN_LECTURA`.
**MOSTRAR**: `OK` al entrar (existe para que el front tenga un paso donde poner los valores).

**Transiciones** (tras actualizar el paso en curso, en el mismo tick):
- Paso `OK` → arranca el siguiente; si era el último → `COMPLETADA` si ningún paso quedó en `ERROR`,
  si no `FALLIDA`.
- RIEGO / MEDIASOMBRA: error en n=1 o n=2 → los `PENDIENTE` salvo el paso seguro quedan `OMITIDO` y
  arranca el paso seguro. **Siempre se cierra la válvula y se enrolla la mediasombra**, aunque el
  ABRIR haya fallado: un ACK perdido no prueba que la bomba no arrancó.
- Error en el paso seguro → `FALLIDA`, sin reintento. Para `CERRAR`, el detalle avisa que el nodo
  apaga solo a los `durationSec`.
- LECTURA: cualquier error → `OMITIDO` lo pendiente y `FALLIDA`.
- `error` de la secuencia = `detalle` del primer paso en ERROR.

**Cancelar** (`EN_CURSO` y no cancelada ya): `cancelacionSolicitada = true`.
- RIEGO / MEDIASOMBRA: el paso en curso (si no es el seguro) → `OMITIDO` "Cancelada por el operador",
  los `PENDIENTE` → `OMITIDO`, arranca el paso seguro. Termina `CANCELADA` cuando el paso seguro
  cierra (OK o ERROR). Si se cancela durante DESPLEGAR, el `ENROLLAR` interrumpe el movimiento en el
  firmware (§4.4, "el último gana").
- LECTURA: todo lo no terminado → `OMITIDO` y `CANCELADA` al instante (no hay nada que dejar seguro).

### 2.4 Guardia del hardware (compartido con la pasada)

```java
@Component
public class GuardiaHardware {
    private final ObjectProvider<UsoDelHardware> usos;   // la pasada y las secuencias, sin ciclo de beans
    public synchronized <T> T conHardwareLibre(UsoDelHardware solicitante,
                                               Function<String, RuntimeException> rechazo,
                                               Supplier<T> iniciar) { … }
}
```

- Recorre los `UsoDelHardware` **distintos del solicitante**; si alguno devuelve un motivo, lanza
  `rechazo.apply(motivo)` sin publicar nada. Si no, ejecuta `iniciar` **con el lock del guardia
  tomado**: dos `POST` simultáneos (una pasada y una secuencia) no pueden pasar los dos.
- **Sin estado propio**: el "ocupado" se deriva de la foto `volatile` de cada servicio
  (`EN_CURSO` → ocupado). No hay nada que liberar, así que no puede quedar trabado por un error o un
  reinicio.
- Orden de locks siempre guardia → servicio; `ocupadoPor()` lee la foto sin lock. Sin deadlock.
- Mensajes: "Hay una pasada del riel en curso." / "Hay una secuencia de {tipo} en curso."
- La exclusión **dentro** de cada servicio (una pasada a la vez, una secuencia a la vez) la sigue
  resolviendo cada uno, con su mensaje de siempre.
- Lo que el guardia **no** cubre: los comandos del motor de reglas (despacho de riego, mediasombra)
  al mismo sector. Es el sistema real; ver §6.

### 2.5 Detalles legibles

| `codigoError` | `detalle` |
|---|---|
| `ACTUADOR_SIN_RESPUESTA` | "{La válvula / La mediasombra} no respondió en N s. ¿El ESP32 está encendido y conectado al broker?" |
| `PUBLICACION_FALLIDA` | "No se pudo publicar el comando al broker: {mensaje}" |
| `FALLA_MECANICA` | "La mediasombra no llegó al final de carrera." |
| `REEMPLAZADO` | "El movimiento fue interrumpido por otro comando." |
| `COMANDO_INVALIDO`, `DURACION_INVALIDA`, `ACTUADOR_DESCONOCIDO` | "El ESP32 rechazó el comando ({tipo})." |
| `SIN_LECTURA` | "La zona {Z} no publicó una lectura en N s. ¿El nodo tiene sensores configurados?" |
| CERRAR en ERROR (sufijo) | " El nodo apaga la bomba solo a los {durationSec} s." |

Código desconocido → "El actuador informó un error: {tipo}".

### 2.6 Destino y validación de `iniciar()`

En este orden; lo que falle **no publica nada**:

1. `tipo` desconocido o parámetro fuera de rango → **400** `{"error"}` (`SecuenciaInvalidaException`).
   - RIEGO: `duracionSeg` entero en `1..ContratoNodo.DURACION_VALVULA_MAX_SEG - timeout-ack-valvula-seg`; default 10.
   - MEDIASOMBRA: `esperaSeg` en `0..600`; default 10.
   - LECTURA: sin parámetros.
2. Guardia (§2.4) y "Ya hay una secuencia en curso." → **409**.
3. Destino, resuelto **al iniciar** y fijo en la secuencia: la macro-zona de menor número (mismo
   criterio que la pasada, orden numérico del sufijo) y, para RIEGO/MEDIASOMBRA, su primer sector por
   id. Sin topología → 409 "No hay topología configurada." *(Decisión abierta 1.)*

### 2.7 REST (interfaz A ↔ B)

Un **solo recurso** para las tres, igual que la pasada: hay una secuencia actual, y seguirla o
cancelarla es lo mismo cualquiera sea el tipo. El tipo es un dato del pedido. Tres rutas
(`/api/secuencias/riego`…) triplicarían el `GET`/cancelar sin decir nada nuevo.

| Método y ruta | Respuesta |
|---|---|
| `POST /api/secuencias` body `{"tipo": "RIEGO", "parametros": {"duracionSeg": 15}}` | `202` + `Secuencia` · `400 {"error"}` · `409 {"error"}` |
| `GET /api/secuencias/actual` | `200` + `Secuencia` (en curso o la última) · `204` si no hubo |
| `POST /api/secuencias/actual/cancelar` | `200` + `Secuencia` · `409 {"error": "No hay una secuencia en curso."}` |

`Secuencia` (riego a mitad de la espera):

```json
{
  "id": "1d2c…", "tipo": "RIEGO", "estado": "EN_CURSO",
  "zonaId": "MZ-1", "sectorId": "MZ-1-001",
  "parametros": {"duracionSeg": 15, "esperaSeg": null},
  "iniciadaEn": 1760000000000, "finalizadaEn": null,
  "cancelacionSolicitada": false, "error": null,
  "lectura": null,
  "pasos": [
    {"n": 1, "tipo": "ABRIR", "estado": "OK", "codigoError": null, "detalle": null,
     "commandId": "7a1e…", "esperaHasta": null, "iniciadoEn": 1760000000010, "terminadoEn": 1760000000400},
    {"n": 2, "tipo": "ESPERAR", "estado": "EN_CURSO", "codigoError": null, "detalle": null,
     "commandId": null, "esperaHasta": 1760000015400, "iniciadoEn": 1760000000400, "terminadoEn": null},
    {"n": 3, "tipo": "CERRAR", "estado": "PENDIENTE", "codigoError": null, "detalle": null,
     "commandId": null, "esperaHasta": null, "iniciadoEn": null, "terminadoEn": null}
  ]
}
```

`lectura` (sólo LECTURA, `null` hasta que llega):

```json
{"recibidaEn": 1760000003200,
 "metricas": {"humSus": 41.0, "humAmb": 63.0, "temp": 22.5, "tempSuelo": null, "uv": 78.0,
              "ce": 1.2, "phSuelo": null, "n": null, "p": null, "k": null}}
```

- `ce` en **dS/m** (convertida con `ContratoNodo.ceADsPorM`), como en el resto de la API; `uv` es % de luz.
- Timestamps en ms epoch. Todas las claves presentes siempre (con `null`).
- `tipo` del paso: `ABRIR | ESPERAR | CERRAR | DESPLEGAR | ENROLLAR | PEDIR | ESPERAR_TELEMETRIA | MOSTRAR`.

### 2.8 Concurrencia, estado y reinicio

- **Memoria**, no BD, por el mismo motivo que la pasada: una a la vez, dura segundos, nadie más la
  consulta. Se conserva la última.
- Hilos: `registrarAck` y `registrarTelemetria` corren en hilos de Paho y **sólo encolan** (la
  telemetría ya sellada con `recibidaEn`). `tick()` (`@Scheduled(fixedDelayString =
  "${yerbanalytics.secuencia.tick-ms:1000}", scheduler = "secuenciaScheduler")`) consume, publica y
  avanza. `iniciar`/`cancelar`/`tick` son `synchronized`; `estado()` lee la foto sin lock.
- Telemetría encolada **antes** del pedido se descarta por `recibidaEn < pedidoEn`, aunque se procese
  en el tick siguiente.
- ACKs de comandos ajenos (los del motor) → DEBUG y se descartan.
- **Reinicio a mitad**: la secuencia se pierde (`GET` → 204). La bomba se apaga sola a los
  `durationSec`; la mediasombra puede quedar desplegada (se corre otra secuencia o se enrolla por serie).

### 2.9 Garantía: el motor de reglas no cambia

- No se modifica ningún archivo de `engine/`, ni `NurseryService`, ni el adaptador/flujo de
  telemetría ni el del riel de `MqttConfig`.
- La lectura pedida **sí** la evalúa el motor (entra por la ingesta normal) y puede encolar un riego
  real. A propósito: es el sistema real (§9.3 del análisis).
- Verificación: `git diff --stat main -- Desarrollo/backend/src/main/java/com/yerbanalytics/backend/engine`
  vacío y `./mvnw test` completo en verde.

---

## 3. Frontend (pista B)

### 3.1 Datos

`types/domain.ts`:

```ts
export type TipoSecuencia = 'RIEGO' | 'MEDIASOMBRA' | 'LECTURA';
export type TipoPasoSecuencia =
  | 'ABRIR' | 'ESPERAR' | 'CERRAR' | 'DESPLEGAR' | 'ENROLLAR' | 'PEDIR' | 'ESPERAR_TELEMETRIA' | 'MOSTRAR';
export interface ParametrosSecuencia { duracionSeg?: number | null; esperaSeg?: number | null }
export interface PasoSecuencia {
  n: number; tipo: TipoPasoSecuencia; estado: EstadoPaso; codigoError: string | null;
  detalle: string | null; commandId: string | null; esperaHasta: number | null;
  iniciadoEn: number | null; terminadoEn: number | null;
}
export interface LecturaSecuencia { recibidaEn: number; metricas: Record<string, number | null> }
export interface Secuencia {
  id: string; tipo: TipoSecuencia; estado: EstadoPasada; zonaId: string; sectorId: string | null;
  parametros: ParametrosSecuencia; iniciadaEn: number; finalizadaEn: number | null;
  cancelacionSolicitada: boolean; error: string | null; lectura: LecturaSecuencia | null;
  pasos: PasoSecuencia[];
}
```

`DataRepository` suma:

```ts
/** Rechaza con SecuenciaRechazadaError (mensaje del 400/409). */
iniciarSecuencia(tipo: TipoSecuencia, parametros?: ParametrosSecuencia): Promise<Secuencia>;
/** null si no hubo ninguna desde que arrancó el backend (204). */
getSecuenciaActual(): Promise<Secuencia | null>;
/** Rechaza con SecuenciaRechazadaError si no hay una en curso. */
cancelarSecuencia(): Promise<Secuencia>;
```

- `SecuenciaRechazadaError` en `data/secuenciaError.ts`, exportado desde `data/index.ts`.
- **http**: rutas del §2.7; 400 y 409 → `SecuenciaRechazadaError` con el `error` del body.
- **mock**: `data/mock/secuenciaMock.ts` con la función pura
  `simularSecuencia(tipo, parametros, inicioMs, ahoraMs, canceladaEnMs | null): Secuencia`.
  Duraciones: ABRIR/CERRAR 1 s, DESPLEGAR/ENROLLAR 4 s, ESPERAR = parámetro, PEDIR 0,5 s,
  ESPERAR_TELEMETRIA 2 s; lectura de demostración tomada de la zona del mock. `MockRepository`
  aplica la **misma exclusión** que el guardia: iniciar una secuencia con la pasada mock en curso (o al
  revés) rechaza con el mismo mensaje que el backend.

### 3.2 Hook `hooks/useSecuencia.ts`

`{ secuencia, cargando, error, iniciando, iniciar(tipo, parametros), cancelar() }`, calcado de
`usePasada`: un `getSecuenciaActual()` al montar, polling cada **1000 ms** sólo mientras
`estado === 'EN_CURSO'`, limpia al desmontar. `SecuenciaRechazadaError` → `error` con su mensaje; el
resto → "No se pudo contactar al backend". Sin fase de "esperar diagnóstico".

### 3.3 Vista en `features/demo-expo/`

- `DemoExpoPage.tsx`: debajo de la pasada, sección **"Secuencias"** con `SecuenciasPanel`.
  Subtítulo de `usePageTitle`: "Pasada del riel y secuencias de actuadores en vivo".
- `components/SecuenciasPanel.tsx`: tres tarjetas, una por tipo, cada una con su botón:
  - **Riego**: input numérico "Segundos" (1–120, default 10) + **Regar**.
  - **Mediasombra**: input "Espera desplegada (s)" (0–600, default 10) + **Desplegar y enrollar**.
  - **Lectura**: **Leer sensores ahora**.
  - Los tres deshabilitados si hay una secuencia o una pasada `EN_CURSO` (ayuda visual; el backend
    decide con el 409). **Cancelar** visible si la secuencia está en curso y no se está cancelando.
- `components/SecuenciaProgreso.tsx`: badge (En curso / Completada / Falló / Cancelada / cancelando…),
  banner con `error`, y los tres pasos reusando `PasoItem` (ícono por estado):
  - ABRIR "Abrir la válvula de {sectorId}", CERRAR "Cerrar la válvula"; DESPLEGAR "Desplegar la
    mediasombra", ENROLLAR "Enrollarla"; ESPERAR con cuenta regresiva desde `esperaHasta`.
  - PEDIR "Pedir lectura a {zonaId}", ESPERAR_TELEMETRIA "Esperando la telemetría…", MOSTRAR con la
    tabla de `lectura.metricas` no nulas (etiqueta y unidad: `uv` como "Luz (%)", `ce` en dS/m) y link
    "Ver la evaluación en el Inspector" → `/reglas`.
  - ERROR: `detalle` en `var(--crit)`. OMITIDO: `detalle` o "Omitido".
- Textos de pasos y etiquetas en `secuenciaPresentacion.ts` (como `pasadaPresentacion.ts`).
- CSS Modules y tokens; dinámicos inline.

---

## 4. Firmware `vivero_esp32_red` (pista C)

> **No se puede probar con hardware hasta el sábado 10/10.** Antes de eso: compilar con
> `arduino-cli` (el mismo core y librerías que el 03/10) y revisar con el checklist de C.9.

### 4.1 Configuración (`config.example.h`)

```cpp
#define NODO_ZONA_ID            "MZ-1"       // zona del stand (topología 1x2)
#define NODO_SECTOR_ID          "MZ-1-001"   // sector cuyos actuadores son la bomba y la mediasombra
#define BOMBA_CAUDAL_PWM        200          // 0-255
#define LECTURA_SENSORES_HABILITADA 0        // 1 cuando se complete leer_sensores()
```

`config.h` sigue sin versionarse. Un `config.h` viejo sin `NODO_ZONA_ID` **no compila** (`#error`).

### 4.2 Suscripciones y callback

Al conectar, además de `nursery/rail/command`:
- `nursery/zone/{NODO_ZONA_ID}/sector/{NODO_SECTOR_ID}/command` QoS 1.
- `nursery/zone/{NODO_ZONA_ID}/command` QoS 1.

Los tópicos se arman una vez en `red_iniciar()` (`snprintf` a `char[]` globales). El callback despacha
**por tópico** a slots separados y sólo copia (igual que hoy: nunca publica dentro del callback):
- riel → el slot `entrante` de siempre, **sin cambios**;
- actuador → slot `act_entrante` (id, actuador, accion, durationSec, targetPct, valido, detalle);
- zona → flag `leer_hay` + id.

`loop()` suma `procesar_actuador()`, `procesar_lectura()` y `valvula_vigilar()` después de
`procesar_entrante()`.

### 4.3 Válvula (sobre el driver de la bomba)

- `valve ON durationSec`: valida `1..CONTRATO_VALVULA_DURACION_MAX_SEG` (si no, ACK `ERROR
  duracion_invalida`); `bomba_encender(BOMBA_CAUDAL_PWM)`; `valvula_cierra_en = millis() + durationSec*1000`;
  ACK `SUCCESS {tipo: ok, durationSec}`. Si ya estaba abierta, renueva el plazo.
- `valve OFF`: `bomba_apagar()`; ACK `SUCCESS` (`sin_cambio` si ya estaba apagada).
- `valvula_vigilar()`: vencido el plazo, `bomba_apagar()` sin ACK (red de seguridad, §1.1).
- `pump` → ACK `ERROR actuador_desconocido` (los insumos no están en este nodo).

### 4.4 Mediasombra (motor DC a final de carrera)

- `shade SET targetPct`: `0` → `mediasombra_desenrollar()`; `100` → `mediasombra_enrollar()`; otro
  valor → ACK `ERROR comando_invalido` (hardware binario).
- Las dos funciones dejan de ser `void` y devuelven `int`: `MS_OK`, `MS_TIMEOUT` (30 s sin final de
  carrera → ACK `ERROR falla_mecanica`), `MS_INTERRUMPIDO`. Ya llamado `red_atender()` en el bucle,
  así que la red sigue viva.
- **El último gana**, como el riel: si durante el movimiento `act_entrante` trae otro `commandId`, se
  para el motor, se publica ACK `ERROR reemplazado` del viejo y `loop()` ejecuta el nuevo. Es lo que
  permite cancelar un DESPLEGAR mandando ENROLLAR. Mismo `commandId` → se ignora.
- Ya en el final de carrera pedido → ACK `SUCCESS sin_cambio` sin mover.

### 4.5 "Leer ahora" (con hueco marcado)

```cpp
// ============================================================
//  HUECO A COMPLETAR: lectura de sensores
//  No se sabe todavía qué sensores van conectados en el stand (análisis §9.4).
//  Completar leer_sensores() y poner LECTURA_SENSORES_HABILITADA en 1.
// ============================================================
bool leer_sensores(JsonObject metricas);   // true si cargó al menos una métrica
```

- Con `LECTURA_SENSORES_HABILITADA 0` (o si `leer_sensores` no cargó nada): log `[leer] sin sensores
  configurados` y **no publica**. El backend da `SIN_LECTURA` con un mensaje que lo explica.
- Con sensores: publica en `nursery/zone/{NODO_ZONA_ID}/telemetry` el payload del contrato
  (`mac`, `signal` = RSSI, `battery` ausente, `timestamp` en segundos, `metrics` con las claves de
  `contrato.h` y sus unidades: `ce` en µS/cm, `uv` en % de luz).

### 4.6 Idempotencia y ACK

- Ring de `commandId` **propio** de actuadores (4 entradas), aparte del del riel: repetido → se republica
  su último ACK si fue el último; si no, se ignora.
- El ACK se arma en `char[256]` y se publica fuera del callback; sin conexión, se guarda como pendiente
  y se publica al reconectar (mismo mecanismo que `ultimo_evento` del riel).
- Firmas sólo con tipos primitivos (prototipos de Arduino); ArduinoJson 7 (`JsonDocument`).

### 4.7 Lo que el sketch sigue sin hacer

Ningún muestreo periódico de telemetría (sólo a pedido), ningún `pump`, ninguna interacción con el
riel: el guardia del backend evita que un comando de actuador llegue mientras el riel se mueve. Si
igual llegara, el `loop()` lo atiende al terminar el tramo.

---

## 5. Decisiones abiertas (con default)

1. **Destino de riego y mediasombra**: *default* primer sector de la zona de menor número (igual que
   la pasada) y el firmware configurado con ese sector. Alternativa: `parametros.sectorId` en el
   `POST`. Depende de §9.4 del análisis.
2. **Republicar comandos de actuador sin ACK**: *default* no (timeout directo). Alternativa: agregar
   un ACK de aceptación al contrato, como el riel.
3. **Sin sensores**: *default* el firmware no publica y el backend da `SIN_LECTURA`. Alternativa: que
   el firmware publique telemetría vacía (descartada: el motor la evaluaría como zona sin datos).
4. **Rango de la UI para el riego**: *default* 1–120 s (el backend acepta hasta el tope del contrato).

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| Firmware sin probar hasta el 10/10 | Compilar con `arduino-cli` antes; checklist C.9; prueba con `mosquitto_pub/sub` (E.3) antes del dashboard |
| El motor comanda el mismo sector a la vez (p. ej. la lectura dispara un riego real) | Es el sistema real y se documenta; `ManualLockRule` queda para otro cambio |
| Otra telemetría de la zona (simulador encendido) se toma como respuesta | Correlación por "primera después del pedido", decidida; no correr el simulador durante la demo |
| ACK perdido (ESP32 publica QoS 0) | El paso seguro corre siempre; la bomba se apaga sola por `durationSec` |
| La bomba no mueve agua de verdad en el stand | No afecta el flujo: el ACK lo da el firmware al energizar el driver |
| Reinicio del backend con la mediasombra desplegada | Documentado (§2.8); se enrolla corriendo otra secuencia o por serie |
| Brownout al arrancar el motor DC o la bomba con WiFi | Mismo aviso que el riel (fuente aparte / capacitor) |
