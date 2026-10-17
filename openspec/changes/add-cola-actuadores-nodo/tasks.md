# Tasks: add-cola-actuadores-nodo

Backend y simulador: TDD estricto (test que falla, después el código). Firmware: lógica pura con arnés host
(`g++`), el resto verificado por compilación. **Orden de despliegue: backend antes que firmware** (design D-8).

FQBN de verificación: `esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,CDCOnBoot=cdc,PartitionScheme=app3M_fat9M_16MB`.

## 1. Contrato y espejos
- [x] 1.1 `contrato.h`, sección "Ack": `STATUS_EN_CURSO`, `DETALLE_EN_COLA`, `DETALLE_INICIADO`, `DETALLE_COLA_LLENA`,
      `KEY_POSICION`, `KEY_ESPERA_MAX`, `KEY_PRIORIDAD`, `PRIORIDAD_INMEDIATA`; reescribir el comentario "un único ack
      por comando" con la secuencia `[en_cola] → iniciado → final`, los JSON de design D-4 y la tabla de preempción (D-3).
- [x] 1.2 `ContratoNodo.java`: `ACK_EN_CURSO`, `ACK_TIPO_EN_COLA`, `ACK_TIPO_INICIADO`, `ACK_TIPO_COLA_LLENA`,
      `PARAM_PRIORIDAD`, `PRIORIDAD_INMEDIATA` (test de espejo si existe el patrón; si no, javadoc citando contrato.h).
- [x] 1.3 `simulador/server/contract.ts`: `ACK_STATUSES` suma `EN_CURSO`; `ACK_DETAIL_TYPES` suma `en_cola`,
      `iniciado`, `cola_llena`; `ActuatorAck.detalle` con `posicion?`, `esperaMaxSec?`; constante de prioridad.

## 2. Firmware (`Desarrollo/embebido/`)
- [x] 2.1 Arnés host: `test/host/cola_cargas_test.cpp` (asserts) con los casos del spec: FIFO con límite 1 y 2,
      OFF que descarta encolados y corta el en curso, reemplazo en curso / encolado, prioridad sólo en mediasombra,
      cola llena, `esperaMaxSec` y `posicion`. Debe fallar (no existe el módulo).
- [x] 2.2 `actuacion/cola_cargas.{h,cpp}`: lógica pura (sin `Arduino.h`), `#ifndef MAX_CARGAS_SIMULTANEAS` → 1,
      `static_assert >= 1`, `COLA_CARGAS_MAX` = 8. `g++ -std=c++17 -Iactuacion actuacion/cola_cargas.cpp
      test/host/cola_cargas_test.cpp -o /tmp/cola && /tmp/cola` en verde.
- [x] 2.3 `config.example.h`: `MAX_CARGAS_SIMULTANEAS 1` con su porqué (brownout del 17/10).
- [x] 2.4 `tipos.h`: `EstadoAck::EN_CURSO`; `Ack` con `posicion` y `esperaMaxSec`; `Comando` con `prioridadInmediata`.
      `util_json`: parsear `parametros.prioridad`, serializar los intermedios (`posicion`/`esperaMaxSec` sólo en `en_cola`).
- [x] 2.5 `task_actuacion`: decidir al llegar (inválido / OFF / `sin_cambio` / prioridad / arrancar / encolar), publicar
      `en_cola` e `iniciado`, avisar a la cola por cada final de `atender()` y arrancar la siguiente. Registrar el
      encolado en `dedup_comandos` como en curso.
- [x] 2.6 `nodo_combinado.ino` (y `nodo_actuador.ino`): ACK con peek/retry como los eventos del riel; `sys.ack` a 16 (D-7).
- [x] 2.7 Compilar `nodo_combinado` y `nodo_actuador` con `arduino-cli` (copia plana del README) sin warnings propios,
      con un `config.h` con y sin `MAX_CARGAS_SIMULTANEAS`.

## 3. Backend
- [x] 3.1 Tests `SeguimientoComandosService`: un `EN_CURSO` no resuelve; el final posterior cierra; plazo recalculado
      con `en_cola` y con `iniciado`; status desconocido no resuelve; intermedio de un resuelto se ignora; publica
      `ActuatorProgress`.
- [x] 3.2 `ActuatorProgress` + cambios en el seguimiento (`Pendiente.duracionMs`, `resueltos.add` sólo en finales).
- [x] 3.3 Tests `DespachoRiego`: "En cola" con `QUEUED` (sigue en el cupo), "Regando" y `finMs` desde `STARTED`,
      progreso de otro `commandId` no toca nada, sin intermedios igual que hoy.
- [x] 3.4 `DespachoRiego`: fase en `Curso` y `@EventListener onActuatorProgress`.
- [x] 3.5 Tests `SupplyDoseService`: `estadoBomba` "En cola" con base `Dosificando`; cierre por tiempo desde `STARTED`;
      sin fase, historial; el final limpia la memoria. Código + `NurseryService` usa `estadoBomba`.
- [x] 3.6 Tests `SecuenciaService`: `EN_CURSO` no cierra `ESPERAR` ni `DESPLEGAR`/`ENROLLAR`; plazo recalculado;
      `iniciado` del ON antes de que entre `ESPERAR`; un status desconocido distinto de `EN_CURSO` sigue siendo error.
- [x] 3.7 `SecuenciaService`: `venceAckEn` para los pasos de mediasombra y último progreso de `comandoOnId`.
- [x] 3.8 Tests y código de `ActuatorShutdown`: OFF primero, malla al final, `prioridad: "INMEDIATA"` en sus órdenes
      (`ZoneSafeStateTest`, test del bloqueo manual).
- [x] 3.9 `AckActuadorReceiverTest`: un `EN_CURSO` llega a los dos consumidores.
- [x] 3.10 `./mvnw test` sin fallas nuevas (1476 tests, 20 fallas, todas las preexistentes de DiagnosticoControllerTest / NurseryControllerTest / ReglasParametrosCatalogoRealTest; `MapaPermisosCoberturaTest` en verde, no hay rutas nuevas).

## 4. Simulador
- [x] 4.1 Tests de `server/nodeQueue.ts` (reloj y timers falsos): mismos casos que 2.1 más OFF que corta un ON en curso
      con `cancelado`, cola por zona, `SIM_NODE_MAX_LOADS`.
- [x] 4.2 `nodeQueue.ts`; `mqtt.ts` lo usa en lugar de `scheduleAck`/`simulatedAck` (la validación de `simulatedAck`
      se reutiliza). `env.example` con `SIM_NODE_MAX_LOADS=1`.
- [x] 4.3 `npm run typecheck` y `npm test` en verde.

## 5. Happy path de vuelta a un pulso
- [x] 5.1 Tests de `happyPath.ts`: UN `triggerPulse`, ningún `updateRule` de `enabled`, diagnóstico antes del pulso,
      lectura de la traza por regla, avance por acción leído de `getActuators` (en cola / en curso / hecho). Borrar
      los tests de pasos, pausa y espera de fin.
- [x] 5.2 `happyPath.ts`: quitar `STEP_ORDER`, `STEP_RULES`, `enableOnly`/`previousEnabled`, `waitUntilFinished`,
      `expectedSeconds`, `STEP_WAIT_MS`, `FINISH_TIMEOUT_EXTRA_MS`; volver a `HappyPathOutcome` con un `readout`.
- [x] 5.3 `HappyPathPanel.tsx`: tarjeta por regla con el avance de cada acción; sin textos de pausa ni "esperando que termine".
- [x] 5.4 Actualizar `openspec/changes/add-demo-expo-happy-path/` (design y tasks): el desvío a tres pulsos queda
      reemplazado por este cambio.

## 6. Docs
- [x] 6.1 `Desarrollo/embebido/README.md`: la cola de cargas, `MAX_CARGAS_SIMULTANEAS`, los ACK intermedios, el arnés
      host y cómo correrlo.
- [x] 6.2 README del backend: seguimiento de comandos, "Regando"/"Dosificando" desde el arranque, orden de despliegue.
- [x] 6.3 README del simulador: la cola simulada y `SIM_NODE_MAX_LOADS`; el happy path en un pulso.
- [x] 6.4 CLAUDE.md §6.2: los tres espejos del contrato MQTT incluyen ahora los ACK intermedios y `prioridad`.
- [x] 6.5 Verificar en el frontend que una bomba "En cola" se ve bien (el estado de válvula "En cola" ya existe).

## 7. Verificación
- [x] 7.1 Arnés host, `./mvnw test`, `npm test` del simulador y compilación con `arduino-cli` en verde.
- [ ] 7.2 Demo sin hardware: happy path en modo simulador; en el historial no queda ningún "no confirmó a tiempo".
- [ ] 7.3 En vivo con el ESP32-S3: flashear `nodo_combinado`, happy path en modo ESP32 real (riego en los dos sectores,
      insumo y mediasombra en un pulso). El nodo no se reinicia (mismo client id en todo el monitor serie), las
      cargas arrancan de a una, llegan `en_cola`/`iniciado`/final de cada comando y el dashboard muestra
      "En cola" → "Regando" → "Cerrada". Probar además un `valve OFF` de un sector encolado y el estado seguro con
      la malla en la cola.

## Notas de implementación
- El riel queda fuera del límite de cargas (documentado en el README del firmware); el simulador encola por macro-zona con
  `SIM_NODE_MAX_LOADS=1`; la mediasombra figura «ordenada» en la tarjeta del happy path; el frontend productivo no
  hardcodea estados de bomba (sólo un comentario de tipo suma «En cola»); `vivero_esp32_red` no se tocó.
- La cola decide con `liberarTerminadas(sigueActiva)` en lugar de un aviso por cada ack final: si un ack se pierde
  (`MAX_PENDIENTES` del driver) la cola no se traba.
- `KEY_POSICION` ya existía en `contrato.h` (riel): se reutiliza para el `posicion` del ack `en_cola`.
- `ActuatorShutdown` agrega `prioridad` sólo al `shade SET` (los `OFF` no llevan parámetros, ahí sería inocuo).
- En `SecuenciaService` el ABRIR del riego encadena a ESPERAR en el mismo instante, así que el intermedio del ON siempre
  encuentra a ESPERAR en curso: no hizo falta recordar el último progreso de `comandoOnId` (D-11).
