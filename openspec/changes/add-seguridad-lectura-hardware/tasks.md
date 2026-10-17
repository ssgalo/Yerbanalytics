# Tasks: add-seguridad-lectura-hardware

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-seguridad-lectura-hardware`.

## 0. Previo
- [x] 0.1 Leer `NurseryService.updateTelemetry` y `LecturaZonaReceiver` (no verificados en detalle) y anotar el punto de enganche. *Hecho: `LecturaZonaReceiver` sólo alimenta las secuencias; el punto de enganche es `updateTelemetry`, tras descartar la lectura vieja y antes de escribir en la zona (ver design, "Decisiones de la implementación").*
- [x] 0.2 Resolver con el equipo la pregunta de D-5 (¿qué bombas son requeridas?). *Resuelta por el modelo vigente: el hardware admite UNA bomba peristáltica por sector (se rechaza una segunda), así que "requerida" es "tiene su bomba". Cuando `add-dosificacion-base` registre una bomba por insumo, la regla pasa a exigir todas.*

## 1. Rangos en el catálogo
- [x] 1.1 Test: claves, fábrica, restricción min < max.
- [x] 1.2 `ParametrosSeguridad` (rangos y `relecturas-invalidas`); frontend los muestra en Parámetros. *(Backend hecho; el frontend se sincroniza en el bloque 4.)*

## 2. S-03
- [x] 2.1 Test (tabla): `LecturaValidator` por métrica, bordes incluidos, CE ya convertida.
- [x] 2.2 Implementar `LecturaValidator` puro.
- [x] 2.3 Test: ingesta con valor inválido pide `LEER_AHORA` y no actualiza la zona.
- [x] 2.4 Contador de relecturas por (zona, métrica); test de 2 reintentos y relectura que corrige.
- [x] 2.5 `RuleContext.metricaValida` y `SIN_DATO` en las reglas que usan humedad de sustrato (R-01…R-06).
- [x] 2.6 Tabla `lectura_invalida` y escalamiento `WARNING`→`CRITICAL`; test de dos ciclos y de recuperación.
- [x] 2.7 Migración: entidad nueva ⇒ `ddl-auto=update` alcanza; sin script manual.

## 3. S-04
- [ ] 3.1 Test: `HardwareIncompletoRule` por rama (RIEGO, INSUMO, MEDIASOMBRA) con y sin actuador.
- [ ] 3.2 Cargar el hardware en el `RuleContext` sin consultar la base por regla.
- [ ] 3.3 Implementar la regla (`@Component`, prioridad 0.5 ⇒ renumerar si `priority()` es entero), flag de activación.
- [ ] 3.4 Inspector (`/reglas`): la regla aparece en el grafo (`GET /api/rules/schema`).

## 4. Cierre
- [ ] 4.1 README del backend, índice, `./mvnw test`, `npm test && npm run lint`.

## 5. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 5.1 `ReadingValidationGuard` y `HardwareIncompletoRule` como `DemoControllable` (apagado + bypass `retries` / `hardware`); tests.
- [ ] 5.2 Comprobar que la emisión del simulador permite publicar valores fuera de rango (no se verificó si el formulario recorta); ajustar si lo hace.
- [ ] 5.3 Escenarios `INVALID_READING` y `INCOMPLETE_HARDWARE` con sus tests.
