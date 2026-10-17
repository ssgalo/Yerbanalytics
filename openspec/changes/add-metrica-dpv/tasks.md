# Tasks: add-metrica-dpv

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-metrica-dpv`.

## 1. Cálculo
- [ ] 1.1 Test (tabla): 25 °C/60 % ⇒ 1,27; HR 100 ⇒ 0; HR 130 o entradas nulas ⇒ `null`.
- [ ] 1.2 `VaporPressureDeficit.calculate` puro.

## 2. Persistencia
- [x] 2.1 Verificado: no hay historial de lecturas; este cambio **requiere** `add-historial-lecturas` integrado.
- [ ] 2.2 Test: la ingesta de una lectura con T y HR guarda `dpv`; sin una de las dos conserva el viejo.
- [ ] 2.3 Columnas `dpv`, `dpv_ts` en la zona (se agregan solas con `ddl-auto=update`) y `dpv` en `zone_reading`.
- [ ] 2.4 Test de integración: `ReadingHistory.max(zone, ReadingMetric.DPV, window)` devuelve el DPV calculado.

## 3. API y frontend
- [ ] 3.1 `Zona.lectura.dpv` en el DTO y en `src/types/domain.ts`.
- [ ] 3.2 Tarjeta en el detalle de zona con el tramo; informativo; colores por token. Mock determinístico.
- [ ] 3.3 Tests, `npm run lint` en cero warnings.

## 4. Control desde el simulador
- [ ] 4.1 El DPV es una métrica derivada, no una regla: nada que apagar. Test de integración: los escenarios de `add-reglas-biotico` producen el DPV esperado con la telemetría que publica el simulador (p. ej. 38 °C y 25 % de humedad ambiental ⇒ DPV ≈ 5 kPa).

## 5. Cierre
- [ ] 5.1 Aclarar en CLAUDE.md §2 que el DPV es derivada (no son 10 métricas sensadas, sino 10 + 1 derivada), índice, `./mvnw test`.
