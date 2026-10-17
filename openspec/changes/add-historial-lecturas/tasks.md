# Tasks: add-historial-lecturas

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-historial-lecturas`. Va **antes** de DPV, nutrición,
biótico y clorosis/estrés.

## 1. Modelo
- [ ] 1.1 Test (JPA): se guarda y se lee una `LecturaZonaEntity` con las 10 métricas, `dpv` nulo, origen.
- [ ] 1.2 Entidad `lectura_zona` + `LecturaZonaRepository` con índice (`zona_id`, `ts`).

## 2. Escritura
- [ ] 2.1 Test: `updateTelemetry` guarda una fila con los valores vigentes (también los que no vinieron).
- [ ] 2.2 Test: lectura anterior a `lastReadingTime` no se guarda; CE queda en dS/m.
- [ ] 2.3 `PulsoSensadoService.respondeAlgunPulso` (con o sin motor) y origen `PULSO`/`OTRA`.
- [ ] 2.4 Test: si falla el guardado, la zona no cambia (misma transacción).

## 3. Consultas
- [ ] 3.1 Test (tabla): `ultimas`, `maximo/minimo/media` con ventana, franja horaria local, `soloPulso`, sin dato.
- [ ] 3.2 Implementar `HistorialLecturas` (consultas JPQL/derivadas; sin cargar todo en memoria).

## 4. Retención
- [ ] 4.1 Parámetro `lecturas.retencion-dias` (3–90, fábrica 14) en la configuración; test de rango.
- [ ] 4.2 Test: la purga borra lo viejo y respeta lo nuevo (reloj inyectado). Tarea a las 03:30.

## 5. Cierre
- [ ] 5.1 Servicio documentado en el README del backend; actualizar en el índice las dependencias; suite verde.
- [ ] 5.2 Control desde el simulador: nada que agregar (no es regla); verificar que la telemetría del simulador alimenta la serie (test de integración con el payload del contrato).
