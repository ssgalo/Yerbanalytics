# Tasks: add-historial-lecturas

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-historial-lecturas`. Va **antes** de DPV, nutrición,
biótico y clorosis/estrés.

## 1. Modelo
- [x] 1.1 Test (JPA): se guarda y se lee una `LecturaZonaEntity` con las 10 métricas, `dpv` nulo, origen.
- [x] 1.2 Entidad `lectura_zona` + `LecturaZonaRepository` con índice (`zona_id`, `ts`).

## 2. Escritura
- [x] 2.1 Test: `updateTelemetry` guarda una fila con los valores vigentes (también los que no vinieron).
- [x] 2.2 Test: lectura anterior a `lastReadingTime` no se guarda; CE queda en dS/m.
- [x] 2.3 `PulsoSensadoService.registrarRespuesta` (con o sin motor, en una sola operación) y origen `PULSO`/`OTRA`.
- [x] 2.4 Test: si falla el guardado, la zona no cambia (misma transacción).

## 3. Consultas
- [x] 3.1 Test (tabla): `ultimas`, `maximo/minimo/media` con ventana, franja horaria local, `soloPulso`, sin dato.
- [x] 3.2 Implementar `HistorialLecturas` (consultas JPQL/derivadas; sin cargar todo en memoria).

## 4. Retención
- [x] 4.1 Parámetro `lecturas.retencion-dias` (3–90, fábrica 14) en el catálogo de parámetros (familia `LECTURAS`); test de rango. El frontend (fixture del modo estático y tests anti-drift) lo conoce.
- [x] 4.2 Test: la purga borra lo viejo y respeta lo nuevo (reloj inyectado). Tarea a las 03:30.

## 5. Cierre
- [x] 5.1 Servicio documentado en el README del backend; actualizar en el índice las dependencias; suite verde. Backend: 1109 tests, los nuevos en verde; fallan los mismos 18 que ya fallaban en `develop` (`DiagnosticoControllerTest` ×17, `NurseryControllerTest`). Frontend: 408 tests y lint en verde.
- [x] 5.2 Control desde el simulador: nada que agregar (no es regla); verificar que la telemetría del simulador alimenta la serie (test de integración con el payload del contrato).
