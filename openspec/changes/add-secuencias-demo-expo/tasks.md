# Tasks: add-secuencias-demo-expo

Tres pistas **paralelas** (A, B, C) que no comparten archivos; la interfaz entre ellas está cerrada
en `design.md` §1 (MQTT) y §2.7 (REST). D (docs) va al final de cada pista o con las tres mergeadas.
E es la puesta en marcha con hardware, **no antes del sábado 10/10**.

| Pista | Toca sólo | Tamaño estimado |
|---|---|---|
| A Backend | `Desarrollo/backend/**` | ~950 líneas prod + ~1000 test |
| B Frontend | `Desarrollo/frontend/**` | ~850 líneas prod + ~600 test |
| C Firmware + contrato | `Desarrollo/embebido/**`, `Desarrollo/simulador/server/contract.ts` | sketch ~400 + contrato ~100 |
| D Documentación | `CLAUDE.md`, READMEs, `docs-motor-reglas-e-integracion/analisis-demo-expo-vs-vivero.md` | ~150 |
| E Puesta en marcha | nada de código | ~1 h con el hardware |

---

## A. Backend (TDD: `cd Desarrollo/backend && ./mvnw test`)

Unidad A1 — entrada y salida MQTT

- [ ] A.1 `ContratoNodo`: `TOPIC_COMANDO_ZONA`, `ACCION_LEER_AHORA`, `TOPIC_ACK`, `STATUS_SUCCESS`/`STATUS_ERROR` (Javadoc → `contrato.h`) + `ComandoZonaPublisherTest` (tópico `nursery/zone/MZ-1/command`, JSON exacto, `commandId` UUID, falla del gateway → `Resultado(false, …)`; espejar `ComandoRielPublisherTest`) → `mqtt/ComandoZonaPublisher`
- [ ] A.2 `AckActuadorReceiverTest` (JSON válido → `registrarAck` con `commandId`, `status`, `detalle` como árbol, zona y sector sacados del tópico; claves desconocidas toleradas; JSON roto → no lanza ni llama) → `AckActuador` + `AckActuadorReceiver`
- [ ] A.3 `LecturaZonaReceiverTest` (zona del tópico; payload → `registrarTelemetria` con `recibidaEn` del reloj; JSON roto → no lanza; **nunca** llama a `NurseryService`) → `LecturaZonaReceiver`
- [ ] A.4 `MqttConfig`: adaptadores `-ack` y `-lectura` con sus canales y flujos (design §2.2). Los de telemetría y riel quedan idénticos (diff de esas líneas vacío)

Unidad A2 — guardia compartido con la pasada

- [ ] A.5 `GuardiaHardwareTest` (libre → ejecuta; otro uso ocupado → lanza el rechazo con su motivo sin ejecutar; el solicitante no se bloquea a sí mismo; dos hilos simultáneos → a lo sumo uno ejecuta) → `UsoDelHardware` + `GuardiaHardware`
- [ ] A.6 Extender `PasadaRielServiceTest`: con una secuencia en curso, `iniciar()` → `PasadaRechazadaException` con el motivo y sin publicar; `ocupadoPor()` sólo con la pasada `EN_CURSO` → `PasadaRielService` implementa `UsoDelHardware` e inicia vía guardia. El resto de sus tests, sin cambios y en verde

Unidad A3 — secuencias

- [ ] A.7 `SecuenciaProperties` (+ registro en `@EnableConfigurationProperties` y las 4 propiedades en `application.properties`); `SchedulersConfig.secuenciaScheduler`; extender `SchedulersConfigTest`
- [ ] A.8 `SecuenciaServiceTest` — **riego** (Mockito + `RelojDePrueba`; `ComandoActuadorPublisher`, `ComandoZonaPublisher`, `ZonaRepository`, guardia mockeados) → `SecuenciaService` + DTOs `Secuencia`/`PasoSecuencia`/`LecturaSecuencia`:
  - [ ] destino: zona de menor número (orden numérico) y su primer sector; sin topología → 409
  - [ ] `ON` con `durationSec = N + timeout-ack-valvula-seg`; ACK `SUCCESS` → ESPERAR con `esperaHasta`; a los N s `OFF`; ACK → `COMPLETADA`
  - [ ] ACK con `commandId` ajeno → ignorado
  - [ ] ABRIR sin ACK a 10 s → `ACTUADOR_SIN_RESPUESTA`, ESPERAR `OMITIDO`, `OFF` publicado igual, `FALLIDA`
  - [ ] CERRAR sin ACK → `FALLIDA` con el aviso de `durationSec`; publicación fallida → `PUBLICACION_FALLIDA`
  - [ ] cancelar en ESPERAR → `OMITIDO` + `OFF` → `CANCELADA`; cancelar sin secuencia → 409; doble cancelación → 409
  - [ ] 400: `tipo` desconocido, `duracionSeg` < 1 o > tope; 409: secuencia en curso (no publica)
- [ ] A.9 `SecuenciaServiceTest` — **mediasombra**: `SET 0` → ESPERAR → `SET 100`; ACK `ERROR falla_mecanica` en DESPLEGAR → `FALLA_MECANICA`, ENROLLAR igual, `FALLIDA`; timeout 45 s; cancelar en DESPLEGAR → ENROLLAR → `CANCELADA`; `esperaSeg` fuera de `0..600` → 400
- [ ] A.10 `SecuenciaServiceTest` — **lectura**: publica `LEER_AHORA` a la zona; telemetría de otra zona → ignorada; con `recibidaEn < pedidoEn` → ignorada; la primera posterior → `lectura` con `ce` en dS/m, MOSTRAR `OK`, `COMPLETADA`; sin lectura a 20 s → `SIN_LECTURA`, `FALLIDA`; cancelar → `CANCELADA` al instante
- [ ] A.11 `SecuenciaControllerTest` (`@WebMvcTest`): `POST /api/secuencias` 202/400/409 `{"error"}`, `GET /api/secuencias/actual` 200/204, `POST /api/secuencias/actual/cancelar` 200/409; JSON con todas las claves de design §2.7 → `SecuenciaController` + `IniciarSecuencia`
- [ ] A.12 Garantía del motor: `git diff --stat main -- Desarrollo/backend/src/main/java/com/yerbanalytics/backend/engine` vacío, `NurseryService` sin cambios; `./mvnw test` completo en verde

## B. Frontend (TDD: `cd Desarrollo/frontend && npm test`; además `npm run lint` con 0 warnings)

Unidad B1 — capa de datos

- [x] B.1 Tipos de design §3.1 en `types/domain.ts`; `SecuenciaRechazadaError` en `data/secuenciaError.ts` exportado desde `data/index.ts`; 3 métodos nuevos en `DataRepository`
- [x] B.2 `httpRepository.test.ts` (rutas, método y body `{tipo, parametros}`; 204 → `null`; 400 y 409 → `SecuenciaRechazadaError` con el `error` del body) → `HttpRepository`
- [x] B.3 `secuenciaMock.test.ts` (`simularSecuencia` de cada tipo en t=0, a mitad de la espera, terminada; cancelada en la espera → paso seguro → `CANCELADA`; lectura con métricas) → `data/mock/secuenciaMock.ts` + `MockRepository` (secuencia con pasada mock en curso, y al revés → rechazo con el mensaje del backend)

Unidad B2 — hook y vista

- [x] B.4 `useSecuencia.test.tsx` con fake timers (carga inicial; polling 1 s sólo en curso; se detiene al terminar; limpia al desmontar; rechazo → mensaje del backend; otro error → "No se pudo contactar al backend"). Mockear con `vi.spyOn(data, 'getRepository')` → `hooks/useSecuencia.ts`
- [x] B.5 `secuenciaPresentacion.test.ts` (texto por tipo de paso; etiquetas y unidades de métricas: `uv` "Luz (%)", `ce` dS/m; cuenta regresiva desde `esperaHasta`) → `features/demo-expo/secuenciaPresentacion.ts`
- [ ] B.6 `SecuenciasPanel.test.tsx` (tres tarjetas; envía `duracionSeg`/`esperaSeg`; botones deshabilitados con secuencia o pasada en curso; Cancelar sólo en curso y no cancelando; `detalle` de error visible; lectura → tabla + link a `/reglas`) → `components/SecuenciasPanel.tsx` + `components/SecuenciaProgreso.tsx` + CSS Modules, reusando `PasoItem`
- [ ] B.7 Extender `DemoExpoPage.test.tsx` (la sección "Secuencias" aparece debajo de la pasada; la pasada sigue igual) → `DemoExpoPage.tsx` y subtítulo de `usePageTitle`
- [ ] B.8 Verificación manual en `npm run dev:demo`: las tres secuencias, cancelar cada una y el rechazo cruzado con la pasada. **Dejar constancia** acá (en `add-pasada-riel` faltó)

## C. Firmware + contrato

> **El firmware no se puede probar con hardware hasta el sábado 10/10.** Hasta entonces: compilar
> con `arduino-cli` (core `esp32:esp32` 3.3.x, ArduinoJson 7, PubSubClient 2.8, como el 03/10) y el
> checklist C.9. El contrato (C.1, C.2) no depende del hardware.

Unidad C1 — contrato

- [ ] C.1 `contrato.h`: sección "Comando de zona" (`contratoTopicComandoZona`, `ACCION_LEER_AHORA`, QoS, respuesta = telemetría sin `commandId`, regla de correlación) y en "Ack" los valores de `detalle.tipo` con `reemplazado`; lista de espejos
- [ ] C.2 `simulador/server/contract.ts`: `zoneCommandTopic`, `READ_NOW_ACTION`, tipos `ZoneCommand` y `ActuatorAck`, en inglés como el resto del archivo. **Sin comportamiento**: nada más del simulador cambia

Unidad C2 — sketch `vivero_esp32_red`

- [ ] C.3 `config.example.h`: `NODO_ZONA_ID`, `NODO_SECTOR_ID`, `BOMBA_CAUDAL_PWM`, `LECTURA_SENSORES_HABILITADA`; `#error` si falta `NODO_ZONA_ID` en `config.h`
- [ ] C.4 Red: tópicos armados en `red_iniciar()`, dos suscripciones nuevas QoS 1 al conectar, callback que despacha por tópico a `act_entrante` / `leer_hay` (el slot del riel, sin cambios)
- [ ] C.5 Válvula sobre el driver de bomba: `ON` con validación y plazo, `OFF`, `valvula_vigilar()` que apaga al vencer; `pump` → `actuador_desconocido`
- [ ] C.6 Mediasombra: `mediasombra_enrollar/desenrollar` devuelven `int` (`MS_OK`, `MS_TIMEOUT`, `MS_INTERRUMPIDO`), cortan si `act_entrante` trae otro `commandId`; `targetPct` sólo 0 o 100; `sin_cambio` si ya está en el final pedido. Los comandos por serie siguen funcionando
- [ ] C.7 ACK: armado en `char[256]` fuera del callback, pendiente al reconectar, ring de 4 `commandId` propio de actuadores (repetido del último → republica su ACK)
- [ ] C.8 "Leer ahora" con el **hueco marcado** (`leer_sensores()` vacía + comentario de design §4.5): con `LECTURA_SENSORES_HABILITADA 0` sólo loguea; con 1 publica la telemetría del contrato (`signal` = RSSI, `timestamp` en segundos)
- [ ] C.9 Checklist antes de entregar: compila con `arduino-cli`; firmas sólo con tipos primitivos; ningún `publish` dentro del callback; ningún `connect()` alcanzable desde `red_atender()`; `commandId` con `strlcpy`; ArduinoJson sin `StaticJsonDocument`; el flujo del riel sin cambios de comportamiento

## D. Documentación

- [ ] D.1 README del backend: sección "Secuencias" (endpoints, tópicos, timeouts, guardia con la pasada, paso seguro, estado en memoria) y el ACK que ahora sí se escucha
- [ ] D.2 README del frontend: una línea en la tabla de vistas (Demo Expo suma secuencias)
- [ ] D.3 `prototipo_hardware/README.md` y el de `vivero_esp32_red`: pines de bomba y mediasombra, `config.h` nuevo, el hueco de sensores, prueba con `mosquitto_pub/sub` (E.3)
- [ ] D.4 `CLAUDE.md`: §6 endpoints de secuencias y corregir "no escucha el ACK de los actuadores"; §6.1/§6.2 el comando de zona en la nota de espejos; referencia rápida al cambio
- [ ] D.5 `analisis-demo-expo-vs-vivero.md` §9: enlazar este cambio; tildar lo que quede respondido de §9.4

## E. Puesta en marcha (con A, B y C mergeadas; **desde el 10/10**)

- [ ] E.1 Topología 1×2 (`MZ-1-001`, `MZ-1-002`) y `config.h` del ESP32 con `NODO_ZONA_ID "MZ-1"`, `NODO_SECTOR_ID "MZ-1-001"`
- [ ] E.2 Flashear `vivero_esp32_red`; en el monitor serie, las tres suscripciones
- [ ] E.3 Sin backend: `mosquitto_sub -t 'nursery/#' -v` y `mosquitto_pub` de `valve ON {durationSec:5}` (la bomba arranca, ACK `SUCCESS`, se apaga sola a los 5 s), `shade SET 0` y `100` (ACK al tocar cada final), `LEER_AHORA` (log de "sin sensores")
- [ ] E.4 Desde Demo Expo: riego 10 s y mediasombra completos; cancelar cada uno a mitad → bomba apagada y mediasombra enrollada
- [ ] E.5 Fallas: ESP32 desenchufado → `ACTUADOR_SIN_RESPUESTA` y la secuencia termina; iniciar una secuencia durante una pasada → 409 legible (y al revés)
- [ ] E.6 Lectura: sin sensores → `SIN_LECTURA` legible; si ya hay sensores cableados, completar C.8 y ver los valores y la traza en `/reglas`

---

## Review Workload Forecast

| Unidad | Contenido | Líneas estimadas (prod + test) |
|---|---|---|
| PR 1 · C1 contrato | `contrato.h`, `contract.ts` (+ A.1 `ContratoNodo` si se quiere el espejo junto) | ~150 |
| PR 2 · A1 MQTT | A.1–A.4 | ~400 |
| PR 3 · A2 guardia | A.5–A.6 | ~220 |
| PR 4 · A3 riego | A.7–A.8 (núcleo de `SecuenciaService` + DTOs) | ~650 |
| PR 5 · A3 mediasombra + lectura + REST | A.9–A.12 + D.1 | ~650 |
| PR 6 · B1 datos | B.1–B.3 | ~550 |
| PR 7 · B2 vista | B.4–B.8 + D.2 | ~850 |
| PR 8 · C2 firmware | C.3–C.9 + D.3 | ~450 |
| PR 9 · docs | D.4–D.5 | ~60 |

- Total estimado: **~4000 líneas** cambiadas (≈ 45 % tests).
- Decision needed before apply: Yes
- Chained PRs recommended: Yes
- 400-line budget risk: High

Lectura: aun troceado, PR 4, 5, 6, 7 y 8 superan las 400 líneas porque cada uno lleva sus tests.
Las opciones reales son (a) cadena de 9 PRs aceptando `size:exception` en esos cinco, o (b) un PR por
pista (A, B, C) con `size:exception`, como se hizo en `add-pasada-riel`. Con la expo encima, (b) es lo
más rápido; (a) es lo más revisable. Lo decide el equipo antes de aplicar.

Total de tareas: 40 (A 12, B 8, C 9, D 5, E 6), más 7 subtareas de test en A.8.
