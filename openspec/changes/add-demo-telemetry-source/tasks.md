# Tasks: add-demo-telemetry-source

TDD estricto. Suites: `./mvnw test -Dtest=...` y, en `Desarrollo/simulador`, `npm test && npm run typecheck`.

## 1. Backend
- [x] 1.1 Tests de la compuerta (store) y de los receivers (ambos sentidos, zona de ambas formas de tópico).
- [x] 1.2 `TelemetryAdmission`, receivers con `ObjectProvider`.
- [x] 1.3 `be/demo/`: enum, store, controlador, config de adaptadores.
- [x] 1.4 Tests: demo apagado ⇒ 404 y sin beans; demo encendido ⇒ adaptadores con su clientId.

## 2. Simulador
- [x] 2.1 Tests: tópico de demo según disponibilidad; cliente del endpoint.
- [x] 2.2 `contract.ts`, `mqtt.ts`/`emission.ts`, interruptor en la pestaña "Telemetría y reglas".

## 3. Docs
- [x] 3.1 README del backend y del simulador; CLAUDE.md §6/§6.2.
