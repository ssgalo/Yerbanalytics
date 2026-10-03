# Tasks: add-pasada-riel

Tres pistas **paralelas** (A, B, C) que no comparten archivos; la interfaz entre ellas está cerrada
en `design.md` §1 (MQTT) y §2.6 (REST). D va al final, con las tres mergeadas.

| Pista | Toca sólo | Tamaño estimado |
|---|---|---|
| A Backend | `Desarrollo/backend/**` | ~500 líneas prod + ~600 test |
| B Frontend | `Desarrollo/frontend/**` | ~500 líneas prod + ~400 test |
| C Firmware + contrato | `Desarrollo/embebido/**`, `Desarrollo/simulador/server/contract.ts`, `CLAUDE.md` | sketch ~550 (≈400 copiadas) + ~80 docs |
| D Puesta en marcha | nada de código | ~1 h con el hardware |

---

## A. Backend (TDD: `cd Desarrollo/backend && ./mvnw test`)

- [ ] A.1 `ContratoRiel` (constantes §1, Javadoc → `contrato.h`) + `ComandoRielPublisherTest` (tópico `nursery/rail/command`, JSON exacto de `IR_A` y `HOME`, `commandId` UUID, falla del gateway → `Resultado(false, …)`; espejar `ComandoActuadorPublisherTest`) → `ComandoRielPublisher`
- [ ] A.2 `RielEventoReceiverTest` (JSON válido → `registrarEvento` con todos los campos; claves desconocidas toleradas; JSON roto → no lanza ni llama) → `EventoRiel` + `RielEventoReceiver`
- [ ] A.3 `MqttConfig`: canal `mqttRielChannel`, adaptador `rielInboundAdapter` (`clientId + "-riel"`, QoS 1) y flujo `mqttRielFlow` (design §2.1). El adaptador y flujo de telemetría quedan idénticos
- [ ] A.4 `PasadaProperties` (+ registro en `@EnableConfigurationProperties` y las 4 propiedades en `application.properties`); `SchedulersConfig.pasadaScheduler` (1 hilo, prefijo `pasada-sched-`); extender `SchedulersConfigTest`: el tick pide `pasadaScheduler`
- [ ] A.5 `DiagnosticoRepository.findFirstByCapturaIdOrderByCreadoEnDesc`
- [ ] A.6 `PasadaRielServiceTest` (Mockito + `RelojDePrueba`; `CapturaService`, `ComandoRielPublisher`, repos mockeados) → `PasadaRielService` + DTOs `Pasada`/`PasoPasada`/`DiagnosticoPaso`. Un test por escenario:
  - [ ] iniciar: arma 5 pasos con `MZ-1-001`/`MZ-1-002` (zona de menor número, orden numérico) y publica `IR_A 1`
  - [ ] 409: pasada en curso · zona con < 2 sectores · `hayCanalAbierto() == false` (no publica)
  - [ ] `LLEGO` → en el tick siguiente `emitirOrden("MZ-1-001", 1)`; orden `RECIBIDA` → `IR_A 2`; … `HOME` `LLEGO` → `COMPLETADA`
  - [ ] evento con `commandId` ajeno → ignorado
  - [ ] sin evento a 5 s → republica el mismo JSON; a 10 s → `RIEL_SIN_RESPUESTA`, capturas `OMITIDO`, `HOME`, `FALLIDA`
  - [ ] `ERROR FIN_DE_CARRERA` en MOVER → mismo camino; `ERROR` en HOME → `FALLIDA` sin reintento
  - [ ] `TIMEOUT_MOVIMIENTO` (120 s) y `TIMEOUT_CAPTURA` (240 s)
  - [ ] orden `ERROR` → paso `ORDEN_FALLIDA`, sigue a posición 2, termina `FALLIDA`
  - [ ] cancelar: en curso → `OMITIDO` + `HOME` → `CANCELADA`; sin pasada → excepción (409)
  - [ ] `estado()` completa `diagnostico` desde el repo aun con la pasada terminada; `null` si no hay pasada
- [ ] A.7 `PasadaRielControllerTest` (`@WebMvcTest`): `POST /api/pasadas` 202/409 `{"error"}`, `GET /api/pasadas/actual` 200/204, `POST /api/pasadas/actual/cancelar` 200/409; JSON con todas las claves de design §2.6 → `PasadaRielController`
- [ ] A.8 `DemoExpoControllerTest` + `PreferenciaDashboardServiceTest` (sin fila → `false`; `PUT` persiste y devuelve; body sin `visible` → 400) → `PreferenciaDashboardEntity` (`preferencia_dashboard`) + repo + service + `DemoExpoController`
- [ ] A.9 Garantía del motor: `git diff --stat main -- Desarrollo/backend/src/main/java/com/yerbanalytics/backend/engine` vacío; `./mvnw test` completo en verde
- [ ] A.10 README del backend: sección "Planificador de pasadas" (endpoints, tópicos, timeouts, estado en memoria) y el interruptor

## B. Frontend (TDD: `cd Desarrollo/frontend && npm test`; además `npm run lint` con 0 warnings)

- [ ] B.1 Tipos de design §3.1 en `types/domain.ts`; `PasadaRechazadaError` en `data/pasadaError.ts` exportado desde `data/index.ts`; 5 métodos nuevos en `DataRepository` y comentario de `repository.ts:5-8` actualizado
- [ ] B.2 `httpRepository.test.ts` (rutas y métodos; 204 → `null`; 409 → `PasadaRechazadaError` con el `error` del body; `imagenUrl` absolutizada en cada paso) → `HttpRepository`
- [ ] B.3 `pasadaMock.test.ts` (`simularPasada` en t=0, 5 s, 9 s, 20 s, +5 s tras terminar; cancelada) → `data/mock/pasadaMock.ts` + `MockRepository` (flag `demoExpo` en memoria, default `false`; segunda `iniciarPasada` en curso → `PasadaRechazadaError`)
- [ ] B.4 `DemoExpoContext.test.tsx` (lee al montar; `cambiar` actualiza; error → `false`) → `hooks/DemoExpoContext.tsx`; montar `DemoExpoProvider` en `AppLayout`
- [ ] B.5 `Sidebar.test.tsx` (con `visible` muestra "Demo Expo" después de "Diagnósticos de IA"; sin él, no) → `Sidebar.tsx`
- [ ] B.6 `DemoExpoSwitch.test.tsx` (guarda al instante; deshabilitado mientras guarda; revierte y muestra error si falla; no ensucia el borrador de la página) → componente + render en `ConfiguracionPage`
- [ ] B.7 `usePasada.test.tsx` con fake timers (carga inicial; polling 1 s en curso, 3 s esperando diagnóstico, se detiene a los 5 min o con todo diagnosticado; limpia al desmontar; 409 → mensaje del backend) → `hooks/usePasada.ts`. Mockear con `vi.spyOn(data, 'getRepository')` como `useTrazaEvaluacion.test.tsx`
- [ ] B.8 `DemoExpoPage.test.tsx` (desactivada → aviso con link; textos por paso y `estadoOrden`; miniatura con `imagenUrl`; "Esperando diagnóstico de IA…" y luego diagnóstico + link a `/diagnosticos`; `detalle` de error visible; Cancelar sólo en curso) → `features/demo-expo/DemoExpoPage.tsx` + CSS Module; ruta `demo-expo` en `router.tsx`
- [ ] B.9 Verificación manual en `npm run dev:demo`: activar el switch, ver la pestaña aparecer, correr la pasada simulada
- [ ] B.10 README del frontend: la sección y el interruptor (una línea en la tabla de vistas)

## C. Firmware + contrato (sin tests; compilación verificada en la PC del compañero)

- [ ] C.1 `contrato.h`: sección "Riel" con tópicos, claves (`posicion`, `pasos`, `codigo` además de las existentes), valores de `accion`/`status`/`codigo`, QoS e idempotencia (§1.3), y los espejos listados
- [ ] C.2 `simulador/server/contract.ts`: `RAIL_COMMAND_TOPIC`, `RAIL_EVENT_TOPIC`, tipos `RailCommand`/`RailEvent` (sin comportamiento)
- [ ] C.3 Copiar `vivero_esp32/vivero_esp32.ino` → `vivero_esp32_red/vivero_esp32_red.ino` **sin cambios** (primer commit aparte, así el diff de la red se revisa limpio)
- [ ] C.4 `config.example.h` (design §4.1) y `prototipo_hardware/vivero_esp32_red/config.h` en `Desarrollo/embebido/.gitignore`
- [ ] C.5 Red: includes, `__has_include`, `#define` del contrato (espejo), WiFi no bloqueante, `red_mantener()` (reconexión cada 3 s sólo en reposo, `setBufferSize(512)`, `setKeepAlive(15)`, `setSocketTimeout(2)`, suscripción QoS 1, publica evento pendiente), `red_atender()`, LED en GPIO 2, comando serie `red`
- [ ] C.6 Callback que sólo copia a `entrante`; `procesar_entrante()` con dedupe (§1.3, ring de 4), validación (`COMANDO_INVALIDO`), `ACEPTADO`, ejecución y evento final con reintento al reconectar; JSON con ArduinoJson 7 (`JsonDocument`)
- [ ] C.7 Motor: `nema_mover` y `nema_homing` devuelven `int` (`MOV_*`), llaman a `red_atender()` cada 200 pasos y abortan con `MOV_INTERRUMPIDO` si `entrante` trae otro `commandId`; `referenciado` global; `IR_A` sin referencia hace homing primero. Sin tipos propios en firmas
- [ ] C.8 Checklist de revisión antes de entregar (sin toolchain acá): firmas sólo con tipos primitivos; ningún `publish` dentro del callback; ningún `connect()` alcanzable desde `red_atender()`; `commandId` copiado con `strlcpy` a `char[40]`; ArduinoJson sin `StaticJsonDocument`/`DynamicJsonDocument`
- [ ] C.9 `prototipo_hardware/README.md`: fila de `vivero_esp32_red`, pasos de compilación/flasheo (design §4.3), prueba con `mosquitto_pub`/`mosquitto_sub` (D.3)
- [ ] C.10 `CLAUDE.md`: §6 endpoint de pasadas; §6.1 el planificador ya existe; contrato del riel en la nota de espejos (§2 / §6.2); referencia rápida al sketch nuevo

## D. Puesta en marcha (con A, B y C mergeadas)

- [ ] D.1 Topología 1×2 (⚠ borra historial y registro de hardware de la base de dev):
  `curl -X POST http://localhost:8000/api/topologia -H 'Content-Type: application/json' -d '{"macroZonas":1,"sectoresPorMacroZona":2,"regenerar":true,"macroZonasPorFila":1,"sectoresPorFila":2}'`
  (o desde la vista Topología). Verificar `MZ-1-001` y `MZ-1-002` en `GET /api/topologia`
- [ ] D.2 Backend y broker arriba (`docker compose up -d mosquitto yerbanalytics-db servicio-inferencia`, `./mvnw spring-boot:run`); dashboard con `npm run dev`; Configuración → activar "Demo Expo"; verificar que aparece la pestaña sin recargar
- [ ] D.3 Flashear `vivero_esp32_red` (design §4.3). Probar sin backend: `docker compose exec mosquitto mosquitto_sub -t 'nursery/rail/#' -v` en una terminal y en otra `docker compose exec mosquitto mosquitto_pub -t nursery/rail/command -m '{"commandId":"t-1","actuador":"rail","accion":"IR_A","parametros":{"posicion":1}}'` → `ACEPTADO` y `LLEGO`; repetir el mismo id → republica `LLEGO` sin moverse; `HOME` → `LLEGO posicion 0`
- [ ] D.4 Celular: el mismo `adb reverse` que ya usan (README de `camara-android`), app abierta con `CAM-827` y el canal conectado
- [ ] D.5 Pasada completa desde "Demo Expo": 5 pasos OK, dos miniaturas, y ~1–1,5 min después ambos diagnósticos en la pestaña y en "Diagnósticos de IA"
- [ ] D.6 Fallas: desenchufar el ESP32 e iniciar → `RIEL_SIN_RESPUESTA` en ~10 s; cancelar a mitad de un tramo → el riel vuelve a home y la pasada queda `CANCELADA`; cerrar la app de cámara e iniciar → 409 legible
