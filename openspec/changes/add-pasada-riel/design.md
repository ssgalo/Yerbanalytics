# Design: add-pasada-riel

> Las §1 (MQTT) y §2.6 (REST) son la **interfaz cerrada** entre las pistas A, B y C. Nadie las
> cambia sin avisar a las otras dos.

## 0. Flujo y tiempos

```
Dashboard ──POST /api/pasadas──▶ Backend (PasadaRielService, tick 1 s)
  1 MOVER   pos 1 ──nursery/rail/command──▶ ESP32 ──ACEPTADO / LLEGO──▶ nursery/rail/event ──▶ Backend
  2 CAPTURAR MZ-1-001 ──emitirOrden(sector, 1)──▶ SSE ──▶ celular ──REST──▶ orden RECIBIDA
  3 MOVER   pos 2 ── (igual que 1)
  4 CAPTURAR MZ-1-002 ── (igual que 2, posicionRiel 2)
  5 HOME          ── (igual que 1, accion HOME)
Inferencia (ya existe): tras 1 min sin capturas nuevas toma las dos y da de alta los diagnósticos.
Dashboard: GET /api/pasadas/actual cada 1 s mientras corre; cada 3 s mientras falte un diagnóstico.
```

Tiempos reales del riel (`vivero_esp32.ino:36-40,78`): `VELOCIDAD_NEMA 500` µs por medio paso →
1 ms/paso. Home→pos1 (21000 pasos) ≈ 21 s; pos1→pos2 ≈ 21 s; pos2→home ≈ 42 s; homing máximo 80 s
(`MAX_PASOS_HOMING 80000`). Una pasada completa ronda 2 min; los diagnósticos llegan ~1–1,5 min
después de la última foto.

---

## 1. Contrato MQTT del riel (interfaz A ↔ C)

| | Comando | Evento |
|---|---|---|
| Tópico | `nursery/rail/command` | `nursery/rail/event` |
| Dirección | backend → ESP32 | ESP32 → backend |
| QoS | publica 1 (`MqttConfig.java:101`), ESP32 suscribe 1 | ESP32 publica 0 (PubSubClient no publica QoS 1, ver `comun/net_mqtt.cpp:68-77`), backend suscribe 1 |
| Retain | no | no |

Un solo riel, sin id en el tópico (YAGNI: el día que haya dos, se versiona).

### 1.1 Comando

Mismas claves que el comando de actuadores (`contrato.h:56-59`): `commandId`, `actuador`, `accion`,
`parametros`.

```json
{"commandId":"5f0c9a7e-2b1d-4c47-9a51-0f3e6c2d8b10","actuador":"rail","accion":"IR_A","parametros":{"posicion":1}}
```
```json
{"commandId":"a1b2c3d4-0000-4000-8000-000000000005","actuador":"rail","accion":"HOME","parametros":{}}
```

- `commandId`: UUID v4 (36 caracteres). Lo genera el backend, uno por paso.
- `accion`: `IR_A` | `HOME`.
- `parametros.posicion` (sólo `IR_A`): `1` | `2`. Es la posición **lógica**; el firmware la traduce a
  pasos (`POSICION_SECTOR_1 = 21000`, `POSICION_SECTOR_2 = 42000`). La calibración vive en el sketch.
- `HOME` hace homing contra el final de carrera (re-referencia, no "ir al paso 0").

### 1.2 Evento

```json
{"commandId":"5f0c9a7e-2b1d-4c47-9a51-0f3e6c2d8b10","status":"ACEPTADO","posicion":null,"pasos":0}
```
```json
{"commandId":"5f0c9a7e-2b1d-4c47-9a51-0f3e6c2d8b10","status":"LLEGO","posicion":1,"pasos":21000}
```
```json
{"commandId":"5f0c9a7e-2b1d-4c47-9a51-0f3e6c2d8b10","status":"ERROR","posicion":null,"pasos":17345,"codigo":"FIN_DE_CARRERA","detalle":"Se activo el final de carrera opuesto antes de llegar"}
```

| Clave | Tipo | Significado |
|---|---|---|
| `commandId` | string | El del comando que responde |
| `status` | `ACEPTADO` \| `LLEGO` \| `ERROR` | `ACEPTADO` se publica apenas se valida el comando, antes de mover |
| `posicion` | int \| null | Posición lógica tras el evento: `0` home, `1`, `2`; `null` si quedó entre posiciones o sin referencia |
| `pasos` | int | `pasos_actuales` del firmware (diagnóstico) |
| `codigo` | string | Sólo en `ERROR`; ver tabla |
| `detalle` | string | Sólo en `ERROR`; texto libre ASCII para el log |

| `codigo` | Cuándo |
|---|---|
| `COMANDO_INVALIDO` | JSON ilegible, `actuador` ≠ `rail`, `accion` desconocida o `posicion` ∉ {1,2} |
| `HOME_NO_ENCONTRADO` | El homing (de `HOME`, o previo a un `IR_A` sin referencia) superó `MAX_PASOS_HOMING` o tocó el final opuesto |
| `FIN_DE_CARRERA` | Un `IR_A` tocó un final de carrera antes de llegar al objetivo |
| `REEMPLAZADO` | Llegó otro comando (otro `commandId`) mientras éste se movía; éste se abortó |

Al backend `status`/`codigo` desconocidos le dan igual: los trata como `ERROR` con ese código.

### 1.3 Idempotencia y concurrencia (firmware)

1. `commandId` igual al del comando **en ejecución** → se ignora (redelivery QoS 1).
2. `commandId` igual al del **último terminado** → se republica su evento final (`LLEGO`/`ERROR`).
3. `commandId` visto entre los últimos 4 → se ignora.
4. Otro `commandId` mientras se mueve → **el último gana**: se aborta el movimiento, se publica
   `ERROR REEMPLAZADO` del viejo y se ejecuta el nuevo. Es lo que permite cancelar mandando `HOME`.

Backend: sólo considera eventos cuyo `commandId` sea el del paso en curso; el resto se loguea a
DEBUG y se descarta (incluye los de una pasada perdida por reinicio).

### 1.4 Dónde se documenta (los cuatro espejos)

| Espejo | Qué agrega | Pista |
|---|---|---|
| `Desarrollo/embebido/comun/contrato.h` (**fuente de verdad**) | Sección "Riel": tópicos, claves, valores de `accion`/`status`/`codigo` como `static const char*` | C |
| `backend/.../mqtt/ContratoRiel.java` (nuevo) | Las mismas constantes + Javadoc que cita `contrato.h` | A |
| `Desarrollo/simulador/server/contract.ts` | Constantes y tipos TS (sin comportamiento: el simulador no simula el riel) | C |
| `vivero_esp32_red/vivero_esp32_red.ino` | `#define` locales con comentario "espejo de contrato.h" (el sketch no incluye `comun/`) | C |

---

## 2. Backend (pista A)

### 2.1 Piezas

| Archivo | Rol |
|---|---|
| `mqtt/ContratoRiel.java` | Constantes del §1 |
| `mqtt/ComandoRielPublisher.java` | Arma el JSON del §1.1 y lo publica por `MqttCommandGateway` (el mismo gateway que `engine/ComandoActuadorPublisher.java:68`; **no** se toca el de `engine/`). Devuelve `Resultado(publicado, commandId, error)` |
| `mqtt/EventoRiel.java` | `record EventoRiel(String commandId, String status, Integer posicion, Long pasos, String codigo, String detalle)` con `@JsonIgnoreProperties(ignoreUnknown = true)` |
| `mqtt/RielEventoReceiver.java` | Deserializa y llama a `PasadaRielService.registrarEvento(evento)`. JSON roto → WARN y nada más |
| `mqtt/MqttConfig.java` | **Segundo** adaptador de entrada, aparte del de telemetría (`MqttConfig.java:51-67` queda igual) |
| `service/PasadaRielService.java` | Orquestador (§2.2) |
| `config/PasadaProperties.java` | `@ConfigurationProperties("yerbanalytics.pasada")`, registrado en `@EnableConfigurationProperties` igual que `CapturaProperties` |
| `config/SchedulersConfig.java` | Bean `pasadaScheduler` (1 hilo) |
| `controller/PasadaRielController.java`, `dto/Pasada.java`, `dto/PasoPasada.java`, `dto/DiagnosticoPaso.java` | REST §2.6 |
| `repository/DiagnosticoRepository.java` | `Optional<DiagnosticoEntity> findFirstByCapturaIdOrderByCreadoEnDesc(String capturaId)` |
| `model/PreferenciaDashboardEntity.java`, `repository/PreferenciaDashboardRepository.java`, `service/PreferenciaDashboardService.java`, `controller/DemoExpoController.java` | Interruptor §2.7 |

Adaptador nuevo en `MqttConfig`:

```java
@Bean(name = "mqttRielChannel") MessageChannel mqttRielChannel() { return new DirectChannel(); }

@Bean MqttPahoMessageDrivenChannelAdapter rielInboundAdapter(MqttPahoClientFactory factory) {
    var a = new MqttPahoMessageDrivenChannelAdapter(clientId + "-riel", factory, ContratoRiel.TOPIC_EVENTO);
    a.setCompletionTimeout(5000);
    a.setConverter(new DefaultPahoMessageConverter());
    a.setQos(1);
    a.setOutputChannel(mqttRielChannel());
    return a;
}

@Bean IntegrationFlow mqttRielFlow() {
    return IntegrationFlow.from(mqttRielChannel()).handle("rielEventoReceiver", "processMessage").get();
}
```

Un adaptador propio (y no `addTopic` al de telemetría) para que el camino de la telemetría —el que
alimenta al motor— quede byte a byte igual.

Propiedades nuevas (`application.properties`):

```properties
yerbanalytics.pasada.timeout-aceptacion-seg=5
yerbanalytics.pasada.timeout-movimiento-seg=120
yerbanalytics.pasada.timeout-captura-seg=240
yerbanalytics.pasada.tick-ms=1000
```

- 120 s de movimiento: cubre pos2→home (42 s) y un homing completo (80 s) con margen.
- 240 s de captura: la orden puede reintentarse 3 veces × 60 s (`CapturaProperties`:
  `timeoutOrdenSeg=60`, `maxIntentos=3`) más el barrido de 10 s (`CapturaService.java:444`).

### 2.2 Máquina de estados

Una pasada tiene 5 pasos fijos, armados en `iniciar()`:

| n | tipo | posicion | sectorId |
|---|---|---|---|
| 1 | `MOVER` | 1 | `null` |
| 2 | `CAPTURAR` | 1 | sector A |
| 3 | `MOVER` | 2 | `null` |
| 4 | `CAPTURAR` | 2 | sector B |
| 5 | `HOME` | 0 | `null` |

Estados de paso: `PENDIENTE → EN_CURSO → OK | ERROR`, o `OMITIDO`.
Estados de pasada: `EN_CURSO → COMPLETADA | FALLIDA | CANCELADA`.

**Paso MOVER / HOME** (al entrar):
publica el comando (§1.1), guarda `commandId`, `publicadoEn`, `republicado=false`.
- Publicación fallida (gateway lanza) → `ERROR`, detalle "No se pudo publicar el comando al broker: …".
- Evento `ACEPTADO` → marca `aceptado=true` (sigue `EN_CURSO`).
- Evento `LLEGO` → `OK`.
- Evento `ERROR` → `ERROR`, `codigoError = codigo`, detalle legible (tabla §2.3).
- Sin ningún evento a los `timeout-aceptacion-seg` → republica **el mismo JSON** (mismo `commandId`)
  una vez; si a los `2 × timeout-aceptacion-seg` sigue sin evento → `ERROR RIEL_SIN_RESPUESTA`.
- `EN_CURSO` más de `timeout-movimiento-seg` desde `publicadoEn` → `ERROR TIMEOUT_MOVIMIENTO`.

**Paso CAPTURAR** (al entrar): `capturaService.emitirOrden(sectorId, posicion)` y guarda `ordenId`.
En cada tick: `capturaService.consultarOrden(ordenId)`; copia `estado` a `estadoOrden`.
- `RECIBIDA` → `OK` con `capturaId` e `imagenUrl` (`CapturaService.imagenUrl`).
- `ERROR` → `ERROR ORDEN_FALLIDA`, detalle con `motivoFallo`/`detalleFallo` de la orden.
- `emitirOrden` lanza → `ERROR ORDEN_FALLIDA` con el mensaje.
- `EN_CURSO` más de `timeout-captura-seg` → `ERROR TIMEOUT_CAPTURA` (la orden sigue su vida en
  `CapturaService`; si la foto llega después, igual se diagnostica).

**Transiciones de pasada** (se evalúan en el mismo tick, tras actualizar el paso en curso):
- Paso OK → arranca el siguiente. Si era el 5 → pasada `COMPLETADA` si ningún paso quedó en `ERROR`,
  si no `FALLIDA`.
- `CAPTURAR` en ERROR → **se sigue** con el siguiente paso (el riel está bien). *(Decisión abierta 1.)*
- `MOVER` en ERROR → pasos `PENDIENTE` restantes salvo HOME → `OMITIDO`; arranca HOME.
- `HOME` en ERROR → pasada `FALLIDA` (no se reintenta).
- `error` de la pasada = `detalle` del primer paso en ERROR.

**Cancelar** (`EN_CURSO` y no cancelada ya): `cancelacionSolicitada=true`; el paso en curso (si no
es HOME) → `OMITIDO` "Cancelada por el operador"; los `PENDIENTE` → `OMITIDO`; arranca HOME (el
firmware aborta el movimiento en curso por §1.3-4). Cuando HOME termina (OK o ERROR) → `CANCELADA`.
La orden de captura que estuviera emitida no se cancela (no hay API para eso): si el celular la
completa, la foto se archiva y se diagnostica como cualquier otra.

### 2.3 Detalles legibles (los escribe el backend; el front sólo los muestra)

| `codigoError` | `detalle` |
|---|---|
| `RIEL_SIN_RESPUESTA` | "El riel no respondió. ¿El ESP32 está encendido y conectado al broker?" |
| `TIMEOUT_MOVIMIENTO` | "El riel no llegó a la posición N en 120 s." |
| `FIN_DE_CARRERA` | "El riel tocó un final de carrera antes de llegar a la posición N." |
| `HOME_NO_ENCONTRADO` | "El riel no encontró el final de carrera de home." |
| `COMANDO_INVALIDO` | "El ESP32 rechazó el comando: {detalle del evento}" |
| `REEMPLAZADO` | "El movimiento fue interrumpido por otro comando." |
| `PUBLICACION_FALLIDA` | "No se pudo publicar el comando al broker: {mensaje}" |
| `ORDEN_FALLIDA` | "El celular no pudo sacar la foto: {motivoFallo} {detalleFallo}" |
| `TIMEOUT_CAPTURA` | "La foto del sector X no llegó en 240 s." |

Código desconocido → "El riel informó un error: {codigo} {detalle}".

### 2.4 Concurrencia y dónde vive el estado

- **Memoria**, no BD. Justificación: una pasada a la vez, dura ~2 min, nadie más la consulta, y lo
  que importa registrar —órdenes, capturas, diagnósticos— ya lo persisten `CapturaService` y
  `DiagnosticoService`. Una tabla costaría entidad + migración sin ganancia para lo que se necesita.
- Se conserva la **última** pasada (en curso o terminada) para que el front la muestre.
- **Reinicio del backend a mitad**: la pasada se pierde (`GET` → 204). El riel termina su tramo y su
  evento se descarta (§1.3). La orden emitida, si la hay, sigue en BD y se completa sola. Se puede
  iniciar otra pasada enseguida: `IR_A 1` es absoluto, funciona desde cualquier posición.
- Hilos: todo método que muta (`iniciar`, `cancelar`, `registrarEvento`, `tick`) es `synchronized`.
  `registrarEvento` (hilo de Paho) **sólo encola** el evento en una lista; no hace I/O. `tick()`
  (`@Scheduled(fixedDelayString = "${yerbanalytics.pasada.tick-ms:1000}", scheduler = "pasadaScheduler")`)
  consume los eventos, consulta la orden, publica y emite órdenes. Así `emitirOrden` —que escribe en
  el stream SSE y puede bloquear (`CapturaService.java:100-102`)— nunca corre en el hilo de Paho ni
  en un carril ajeno.
- Lectura (`GET`): el servicio guarda tras cada mutación una foto inmutable (`volatile Pasada`) y el
  `GET` la lee **sin lock**; recién ahí completa `diagnostico` de cada paso con `capturaId` vía
  `DiagnosticoRepository.findFirstByCapturaIdOrderByCreadoEnDesc`. Así el diagnóstico aparece aunque
  la pasada ya haya terminado, y un `emitirOrden` lento no congela el polling.
- Hora: `Clock relojVivero` (`RelojConfig`) inyectado; los tests usan `RelojDePrueba`
  (`src/test/.../engine/riego/RelojDePrueba.java`).

### 2.5 Precondiciones de `iniciar()` y mapeo posición → sector

En este orden; cualquiera que falle → `409 {"error": "<mensaje>"}` y no se publica nada:

1. Hay una pasada `EN_CURSO` → "Ya hay una pasada en curso."
2. Topología: la macro-zona de **menor número** (`MZ-1`; orden numérico del sufijo, no lexicográfico)
   y sus sectores ordenados por id (`MZ-1-001`, `MZ-1-002`, … — `TopologiaService.java:242`, con
   ceros a la izquierda). Si tiene menos de 2 → "La pasada necesita al menos 2 sectores en MZ-1 (hay N)."
   Posición 1 → primer sector; posición 2 → segundo. Se resuelve **al iniciar** y queda fijo en la pasada.
3. `!capturaService.hayCanalAbierto()` → "No hay ningún dispositivo de captura conectado."
   *(Decisión abierta 2.)*

### 2.6 REST (interfaz A ↔ B)

| Método y ruta | Respuesta |
|---|---|
| `POST /api/pasadas` (sin body) | `202` + `Pasada` · `409 {"error": "..."}` |
| `GET /api/pasadas/actual` | `200` + `Pasada` (la en curso o la última) · `204` si no hubo ninguna desde el arranque |
| `POST /api/pasadas/actual/cancelar` | `200` + `Pasada` · `409 {"error": "No hay una pasada en curso."}` |
| `GET /api/configuracion/demo-expo` | `200 {"visible": false}` |
| `PUT /api/configuracion/demo-expo` body `{"visible": true}` | `200 {"visible": true}` · `400` si falta `visible` |

`Pasada` (ejemplo a mitad de camino):

```json
{
  "id": "0b6f3c1e-7d3a-4f0e-9a1c-2f4b5e6d7a80",
  "estado": "EN_CURSO",
  "iniciadaEn": 1759514400000,
  "finalizadaEn": null,
  "cancelacionSolicitada": false,
  "error": null,
  "pasos": [
    {"n": 1, "tipo": "MOVER", "posicion": 1, "sectorId": null, "estado": "OK",
     "codigoError": null, "detalle": null, "commandId": "5f0c9a7e-2b1d-4c47-9a51-0f3e6c2d8b10",
     "ordenId": null, "estadoOrden": null, "capturaId": null, "imagenUrl": null, "diagnostico": null,
     "iniciadoEn": 1759514400010, "terminadoEn": 1759514421800},
    {"n": 2, "tipo": "CAPTURAR", "posicion": 1, "sectorId": "MZ-1-001", "estado": "OK",
     "codigoError": null, "detalle": null, "commandId": null,
     "ordenId": "c3d9...", "estadoOrden": "RECIBIDA", "capturaId": "CAP-000042",
     "imagenUrl": "/api/capturas/CAP-000042/imagen",
     "diagnostico": {"estado": "Clorosis", "conf": 87.0, "sev": "Media", "creadoEn": 1759514530000},
     "iniciadoEn": 1759514422000, "terminadoEn": 1759514431000},
    {"n": 3, "tipo": "MOVER", "posicion": 2, "sectorId": null, "estado": "EN_CURSO",
     "codigoError": null, "detalle": null, "commandId": "9e2a...", "ordenId": null, "estadoOrden": null,
     "capturaId": null, "imagenUrl": null, "diagnostico": null,
     "iniciadoEn": 1759514432000, "terminadoEn": null},
    {"n": 4, "tipo": "CAPTURAR", "posicion": 2, "sectorId": "MZ-1-002", "estado": "PENDIENTE",
     "codigoError": null, "detalle": null, "commandId": null, "ordenId": null, "estadoOrden": null,
     "capturaId": null, "imagenUrl": null, "diagnostico": null, "iniciadoEn": null, "terminadoEn": null},
    {"n": 5, "tipo": "HOME", "posicion": 0, "sectorId": null, "estado": "PENDIENTE",
     "codigoError": null, "detalle": null, "commandId": null, "ordenId": null, "estadoOrden": null,
     "capturaId": null, "imagenUrl": null, "diagnostico": null, "iniciadoEn": null, "terminadoEn": null}
  ]
}
```

- `estadoOrden`: el `estado` de `EstadoOrden` tal cual (`PENDIENTE`, `ENTREGADA`, `RECIBIDA`,
  `ERROR`, …; `OrdenCapturaEntity.java:44-49`).
- `diagnostico`: `estado`, `conf` y `sev` tal como están en `DiagnosticoEntity` (mismos valores que ya
  muestra "Diagnósticos de IA"), `null` mientras no exista.
- `imagenUrl` es relativa a la raíz; el `HttpRepository` la absolutiza como ya hace con las demás
  (`httpRepository.ts:40-43`).
- Timestamps en ms epoch. Todas las claves presentes siempre (con `null`).

### 2.7 Interruptor "Demo Expo"

- Entidad propia `PreferenciaDashboardEntity` → tabla `preferencia_dashboard` (fila única `id = 1`,
  `demo_expo_visible BOOLEAN NOT NULL`). Sin fila → `false`. La crea Hibernate (`ddl-auto=update`,
  `spring.sql.init.mode=never`): **no hace falta migración manual**.
- Por qué no una columna en `configuracion_operativa`: esa entidad la lee el motor
  (`RuleContextTestFactory` la construye con `@AllArgsConstructor`), está cacheada en
  `ConfiguracionService` y su `PUT` registra un evento de auditoría HU-15. Una preferencia de
  visualización no tiene nada que ver con eso; ponerla ahí acoplaría el motor a la expo.
- `DemoExpoController` mapea `/api/configuracion/demo-expo` (no choca con `PUT /api/configuracion` de
  `ConfiguracionController.java:37`).
- El interruptor **sólo** oculta la pestaña. Los endpoints de pasada funcionan siempre: son una
  capacidad del sistema, no de la demo.

### 2.8 Garantía: el motor de reglas no cambia

- No se modifica ningún archivo de `engine/`, ni `NurseryService`, ni `ConfiguracionService`, ni el
  adaptador/flujo de telemetría de `MqttConfig`.
- Carril propio `pasadaScheduler`: el tick no compite con `taskScheduler` (motor),
  `despachoScheduler` ni `capturaScheduler` (`SchedulersConfig.java:42-79`).
- Verificación (tarea A.9): `git diff --stat main -- Desarrollo/backend/src/main/java/com/yerbanalytics/backend/engine`
  vacío y `./mvnw test` completo en verde.

---

## 3. Frontend (pista B)

### 3.1 Datos

`types/domain.ts`:

```ts
export type EstadoPasada = 'EN_CURSO' | 'COMPLETADA' | 'FALLIDA' | 'CANCELADA';
export type TipoPaso = 'MOVER' | 'CAPTURAR' | 'HOME';
export type EstadoPaso = 'PENDIENTE' | 'EN_CURSO' | 'OK' | 'ERROR' | 'OMITIDO';
export interface DiagnosticoPaso { estado: string; conf: number; sev: string; creadoEn: number }
export interface PasoPasada {
  n: number; tipo: TipoPaso; posicion: number; sectorId: string | null; estado: EstadoPaso;
  codigoError: string | null; detalle: string | null; commandId: string | null;
  ordenId: string | null; estadoOrden: string | null; capturaId: string | null;
  imagenUrl: string | null; diagnostico: DiagnosticoPaso | null;
  iniciadoEn: number | null; terminadoEn: number | null;
}
export interface Pasada {
  id: string; estado: EstadoPasada; iniciadaEn: number; finalizadaEn: number | null;
  cancelacionSolicitada: boolean; error: string | null; pasos: PasoPasada[];
}
```

`DataRepository` (`data/repository.ts`) suma:

```ts
getDemoExpo(): Promise<boolean>;
setDemoExpo(visible: boolean): Promise<boolean>;
/** Rechaza con PasadaRechazadaError (mensaje del 409) si no se puede iniciar. */
iniciarPasada(): Promise<Pasada>;
/** null si no hubo ninguna desde que arrancó el backend (204). */
getPasadaActual(): Promise<Pasada | null>;
/** Rechaza con PasadaRechazadaError si no hay una en curso. */
cancelarPasada(): Promise<Pasada>;
```

`PasadaRechazadaError` en `data/pasadaError.ts`, exportado desde `data/index.ts` (igual que
`ParametrosInvalidosError`). Actualizar el comentario de `repository.ts:5-8`: las pasadas **sí** son
superficie del dashboard; las órdenes de captura siguen sin serlo.

- **http**: rutas del §2.6; `absolutizarImagen` sobre cada paso.
- **mock**: `data/mock/pasadaMock.ts` con una función pura
  `simularPasada(inicioMs, ahoraMs, canceladaEnMs | null): Pasada` — duraciones MOVER 4 s, CAPTURAR
  3 s, MOVER 4 s, CAPTURAR 3 s, HOME 6 s; `estadoOrden` `ENTREGADA` durante la captura; imágenes de
  `capturasDemo` (`'Clorosis'[0]` y `'Estrés solar'[0]`); diagnóstico 5 s después de terminar.
  `MockRepository` guarda `inicio`/`cancelada` y el flag `demoExpo` en memoria (default `false`).
  `iniciarPasada` con una en curso rechaza con `PasadaRechazadaError("Ya hay una pasada en curso.")`.

### 3.2 Interruptor y Sidebar (sin reiniciar)

- `hooks/DemoExpoContext.tsx`: `DemoExpoProvider` + `useDemoExpo(): { visible: boolean; cargando:
  boolean; cambiar(v: boolean): Promise<void> }`. Lee `getDemoExpo()` al montar; `cambiar` llama a
  `setDemoExpo` y actualiza el estado con lo que devuelve. Error de lectura → `visible=false`.
- `AppLayout.tsx` envuelve con `DemoExpoProvider` (dentro de `PageMetaProvider`).
- `Sidebar.tsx`: si `visible`, agrega a `PRINCIPAL` (`Sidebar.tsx:19-22`), después de "Diagnósticos de
  IA", `{ to: '/demo-expo', icon: 'camera', label: 'Demo Expo' }`.
- `features/configuracion/components/DemoExpoSwitch.tsx`: tarjeta "Demo Expo" con un switch
  (`<input type="checkbox" role="switch">`) y texto "Muestra la pestaña Demo Expo en el menú". **Guarda
  al instante** con `cambiar()`, independiente del borrador y del botón Guardar de la página.
  Deshabilitado mientras guarda; si falla, vuelve al valor previo y muestra el error.
- `router.tsx`: `{ path: 'demo-expo', element: <DemoExpoPage /> }`. La ruta existe siempre; si
  `visible` es `false` la página muestra "La sección Demo Expo está desactivada. Activala en
  Configuración." con un link a `/configuracion`.

### 3.3 Página `features/demo-expo/DemoExpoPage.tsx`

- `usePageTitle('Demo Expo', 'Pasada del riel: del dashboard al ESP32, al celular y a la IA')`.
- Hook `hooks/usePasada.ts`: `{ pasada, cargando, error, iniciando, iniciar(), cancelar() }`.
  - Al montar: un `getPasadaActual()`.
  - Polling: cada **1000 ms** mientras `estado === 'EN_CURSO'`; cada **3000 ms** mientras esté
    terminada, algún paso `CAPTURAR` tenga `capturaId` y no tenga `diagnostico`, y no hayan pasado
    5 min desde `finalizadaEn`; si no, se detiene. Limpia el timer al desmontar.
  - `iniciar()`: `PasadaRechazadaError` → `error` con su mensaje (se muestra tal cual); el resto →
    "No se pudo contactar al backend".
- UI:
  - Botón **Iniciar pasada** (deshabilitado si `EN_CURSO` o `iniciando`); **Cancelar** visible si
    `EN_CURSO && !cancelacionSolicitada`.
  - Badge de la pasada: En curso / Completada / Falló / Cancelada (+ "cancelando…" si
    `cancelacionSolicitada` y sigue en curso). Si `error`, banner con el texto.
  - Lista de los 5 pasos, ícono por estado (pendiente, spinner, check, alerta, tachado):
    - MOVER: "Riel → posición N"; en curso: "Moviendo…".
    - CAPTURAR: "Foto del sector {sectorId}"; según `estadoOrden`: `PENDIENTE` "Esperando al
      celular", `ENTREGADA` "El celular está sacando la foto", `RECIBIDA` miniatura (`imagenUrl`,
      160×107, `object-fit: cover`). Debajo: sin diagnóstico → "Esperando diagnóstico de IA (se
      analiza tras 1 min sin fotos nuevas)"; con diagnóstico → `estado` + `conf` (ya es porcentaje 0–100, `DiagnosticoService`) + link
      "Ver en Diagnósticos de IA" (`/diagnosticos`).
    - HOME: "Riel → home".
    - ERROR: `detalle` en rojo (`var(--crit)`). OMITIDO: `detalle` o "Omitido".
  - Estilos con CSS Modules y tokens (`styles/tokens.css`); dinámicos inline.

---

## 4. Firmware `vivero_esp32_red` (pista C)

### 4.1 Estructura

```
Desarrollo/embebido/prototipo_hardware/vivero_esp32_red/
├── vivero_esp32_red.ino    copia de vivero_esp32.ino + red; UN solo .ino
├── config.example.h        plantilla versionada
└── config.h                NO versionado (agregar a Desarrollo/embebido/.gitignore)
```

Librerías (Library Manager): **PubSubClient** (Nick O'Leary) 2.8.x y **ArduinoJson** (Benoit
Blanchon) **7.x** — usar la API v7 (`JsonDocument doc;`), no `StaticJsonDocument` (v6).

`config.example.h`:

```cpp
#pragma once
#define WIFI_SSID      "mi-red"
#define WIFI_PASS      "mi-clave"
#define MQTT_HOST      "192.168.1.64"   // IP LAN de la PC donde corre el broker (docker compose)
#define MQTT_PORT      1883
#define MQTT_CLIENT_ID_BASE "riel-esp32"
```

En el `.ino`:

```cpp
#if __has_include("config.h")
#include "config.h"
#else
#error "Falta config.h: copia config.example.h como config.h y completa WiFi y MQTT_HOST"
#endif
```

### 4.2 Diseño: movimiento bloqueante **cooperativo** (no se reescribe el motor)

Se conserva el bucle de pasos probado (`nema_mover`, `vivero_esp32.ino:205-239`; `nema_homing`,
`:243-288`) y se le agrega **una llamada a `red_atender()` cada 200 pasos** (≈200 ms). Reescribir
el control del motor como máquina no bloqueante (o con AccelStepper) es más riesgo que beneficio sin
poder compilar acá.

- `red_atender()` hace **sólo** `mqtt.loop()` (mantiene el keepalive de 15 s y recibe) y refresca el
  LED. **Nunca reconecta durante un movimiento**: `connect()` puede bloquear segundos.
- El callback MQTT **sólo copia** el mensaje a un slot `entrante` (id, accion, posicion, valido) y
  levanta un flag. No mueve, no publica (PubSubClient reusa el buffer de recepción para publicar:
  publicar adentro del callback corrompe el payload).
- Si durante el movimiento `entrante` trae un `commandId` **distinto** del actual, `nema_mover` /
  `nema_homing` devuelven `MOV_INTERRUMPIDO`; el comando viejo termina en `ERROR REEMPLAZADO` y el
  `loop()` ejecuta el nuevo (§1.3-4). Si trae el mismo id, se descarta.
- Firmas con `int` y `#define MOV_OK 0 / MOV_FIN_HOME 1 / MOV_FIN_OPUESTO 2 / MOV_INTERRUMPIDO 3 /
  MOV_SIN_HOME 4`. **No usar `enum`/`struct` propios en firmas de funciones del `.ino`**: el
  generador de prototipos de Arduino los declara antes que el tipo y no compila. Los `struct` se usan
  sólo como variables globales.
- `nema_mover` deja de ser `void`: devuelve el código; el comportamiento ante finales de carrera es
  el mismo de hoy.

`loop()`:

```
red_mantener();        // si !conectado: WiFi lo reconecta solo (setAutoReconnect);
                       // MQTT reintenta cada 3 s con setSocketTimeout(2); al conectar se suscribe
                       // a nursery/rail/command QoS 1 y publica el evento final pendiente si lo hay
red_atender();         // mqtt.loop() + LED
procesar_entrante();   // §1.3: dedupe → ACEPTADO → ejecutar (bloqueante cooperativo) → LLEGO/ERROR
serial_atender();      // comandos por serie de siempre + "red"
```

Ejecutar:
- `IR_A n`: si `!referenciado` → `nema_homing()` primero (falla → `ERROR HOME_NO_ENCONTRADO`).
  Luego `nema_ir_a(POSICION_SECTOR_n)`. `MOV_OK` → `LLEGO posicion n`; final de carrera →
  `ERROR FIN_DE_CARRERA`; interrumpido → `ERROR REEMPLAZADO`.
- `HOME`: `nema_homing()`. OK → `LLEGO posicion 0`; falla → `ERROR HOME_NO_ENCONTRADO`.
- `posicion` reportada: `0` si `pasos_actuales == 0` y referenciado; `1`/`2` si coincide con
  `POSICION_SECTOR_1/2`; si no, `null`.
- El evento final se guarda en `ultimoEvento` (JSON ya serializado, `char[256]`) + `pendiente=true`;
  se intenta publicar al instante y, si no hay conexión, al reconectar. `ACEPTADO` no se reintenta.
- Ring de los últimos 4 `commandId` (`char[4][40]`) para §1.3-3.

Arranque (`setup()`): pines y `Serial` como hoy → **homing primero** (`referenciado = nema_homing()`,
igual que hoy; sin red de por medio) → `WiFi.mode(WIFI_STA); WiFi.setAutoReconnect(true);
WiFi.begin(...)` sin esperar → `mqtt.setServer`, `setBufferSize(512)`, `setKeepAlive(15)`,
`setSocketTimeout(2)`, `setCallback`. *(Decisión abierta 4.)* Un comando que llegue antes de
conectar no se pierde por el firmware: nunca llega; el backend lo detecta por falta de `ACEPTADO`.

Seguridad:
- Finales de carrera chequeados en cada paso (sin cambios).
- Topes: homing `MAX_PASOS_HOMING`; `IR_A` sólo a 1|2 (nunca pasos arbitrarios por red).
- `mover N`, `rutina`, etc. por serie siguen funcionando (y también atienden la red mientras mueven).
- El sketch **no** se suscribe a válvula/bomba/mediasombra; esos actuadores sólo por serie.

LED (GPIO 2, libre en este sketch): rápido (100 ms) sin WiFi, lento (500 ms) WiFi sin MQTT, fijo
encendido con MQTT. Log por serie con prefijos `[wifi]`, `[mqtt]`, `[cmd]`, `[riel]`; comando serie
`red` imprime IP, RSSI, estado MQTT y último `commandId`.

### 4.3 Compilar y flashear (Arduino IDE 2.x)

1. Boards Manager: "esp32 by Espressif Systems" **3.x**. Placa: *ESP32 Dev Module*.
2. Library Manager: *PubSubClient* (Nick O'Leary) y *ArduinoJson* (Benoit Blanchon) 7.x.
3. Abrir `vivero_esp32_red/vivero_esp32_red.ino`. Copiar `config.example.h` → `config.h` en la misma
   carpeta y completar `WIFI_SSID`, `WIFI_PASS`, `MQTT_HOST` (IP de la PC del broker; `ip addr` en
   Linux). El ESP32 sólo usa WiFi de 2,4 GHz.
4. Verificar (✓), luego Subir (→). Si queda en "Connecting…", mantener BOOT apretado hasta que empiece.
5. Monitor serie 115200: tiene que verse el homing, `[wifi] IP ...`, `[mqtt] Conectado` y
   `[mqtt] Suscripto a nursery/rail/command`.

---

## 5. Decisiones abiertas (con default)

1. **Foto fallida en una posición**: *default* seguir con la siguiente y terminar `FALLIDA`.
   Alternativa: abortar e ir a home.
2. **Iniciar sin celular conectado**: *default* rechazar con 409. Alternativa: dejar la orden
   `PENDIENTE` hasta que conecte (la pasada esperaría hasta `timeout-captura-seg`).
3. **Comando durante un movimiento**: *default* el último gana (permite cancelar). Alternativa:
   rechazar con `ERROR OCUPADO` (cancelar tendría que esperar a que termine el tramo).
4. **Homing al arrancar**: *default* antes de conectar la red (como hoy). Alternativa: después, para
   que el backend vea el `ACEPTADO` de un homing remoto.

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| El sketch no compila (no hay toolchain acá) | Un solo `.ino`, API ArduinoJson v7 explícita, sin tipos propios en firmas, `#error` si falta `config.h`; la pista C entrega además un checklist de compilación |
| Evento del ESP32 perdido (publica QoS 0) | Republicación del comando a los 5 s + regla §1.3-2 (repite el evento final); en LAN es raro |
| Brownout del ESP32 al transmitir WiFi con el motor andando en la misma fuente | Alimentar el ESP32 aparte del DRV8825 / capacitor en 3V3; si se ve `Brownout detector was triggered` en serie, es esto |
| Jitter de pasos por `mqtt.loop()` | Cada 200 pasos, sin reconexión durante el movimiento; a 1 kHz sin rampa el motor tolera pausas de ms |
| Regenerar la topología borra historial y registro de hardware (`TopologiaService.java:177-180`) | Es la base de desarrollo; se avisa en la pista D |
| Diagnóstico tarda ~1–1,5 min tras la última foto | La UI lo dice explícitamente y sigue consultando 5 min |
| Reinicio del backend a mitad de pasada | Documentado en §2.4; se inicia otra |
