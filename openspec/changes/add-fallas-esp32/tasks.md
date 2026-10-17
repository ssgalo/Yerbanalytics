# Tasks: add-fallas-esp32

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-fallas-esp32`. Depende de `add-seguridad-lectura-hardware`
(si no se integró, ver D-3).

## 0. Previo
- [x] 0.1 Verificado: nadie escribe `dispositivo.falla` (sólo se limpia al recambiar) y el firmware lee un final de carrera por sentido (ver `design.md`). Este cambio crea el primer camino que lo escribe.
- [x] 0.2 Responder las preguntas abiertas del diseño (ver «Preguntas resueltas» en `design.md`).

## 1. Contrato
- [x] 1.1 `contrato.h`: tópico, payload, tipos y constantes; helper `contratoTopicErrores()`.
- [x] 1.2 Espejos: `ContratoNodo.java` y `simulador/server/contract.ts`. Test de paridad de constantes si existe el patrón.

## 2. Backend
- [x] 2.1 Test: el receptor registra la falla (`falla`, `falla_desde`) y descarta tipos desconocidos.
- [x] 2.2 `ErrorNodoReceiver` (MQTT) + servicio; deduplicación con el ack `falla_mecanica` (D-6).
- [x] 2.3 Test: `FallaHidraulicaRule` aborta RIEGO e INSUMO del sector, incluso R-02; otros sectores siguen.
- [x] 2.4 Test: `FallaMediasombraRule` impide comandos de malla de la MZ (rama MEDIASOMBRA).
- [x] 2.5 Alertas con los textos de la v2 (riego manual si humedad MZ < 45 %; cubrir a mano).
- [x] 2.6 Reparación: endpoint, permiso `hardware.reparar` en `MapaPermisos`, evento de historial.

## 3. Frontend
- [x] 3.1 Vista de Hardware: estado "Falla hidráulica"/"Falla mecánica" y botón Registrar reparación con detalle obligatorio.
- [x] 3.2 Tests y lint.

## 4. Firmware y simulador
- [x] 4.1 Firmware: publicar `MEDIASOMBRA_FIN_DE_CARRERA` y exponer ambos finales de carrera. **Sin compilar**: esta máquina no tiene `arduino-cli` ni PlatformIO; compilar antes de flashear (`nodo_actuador`, `nodo_combinado`, `nodo_sensor`).
- [x] 4.2 Firmware: publicar caudal sólo detrás de un flag de compilación (`ENVIAR_ERRORES_CAUDAL`, de fábrica 0; deja `informarErrorCaudal()` como punto de integración, sin hardware instalado).
- [x] 4.3 Simulador: botón por tipo de error (`Desarrollo/simulador/`); el backend no se toca.

## 5. Cierre
- [x] 5.1 README del backend, README del embebido y del simulador, `CLAUDE.md`, índice (`ESTADO.md`), suites verdes (backend: las que no necesitan PostgreSQL; frontend y simulador completas).

## 6. Control desde el simulador (requiere `add-control-demo-reglas`)
- [x] 6.1 Las dos reglas y el receptor como `DemoControllable` (apagado + bypass `fault`); tests.
- [x] 6.2 Escenarios `FAULT_NO_FLOW`, `FAULT_OVERFLOW`, `FAULT_SHADE_LIMIT` (publican en el tópico de errores) y `REPAIR` (registra la reparación) con sus tests. Ya estaba prevista la inyección de errores en 4.3: este bloque la completa con el catálogo de escenarios.
