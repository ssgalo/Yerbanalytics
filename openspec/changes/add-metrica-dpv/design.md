# Design: add-metrica-dpv

## Estado verificado
- Backend: sin ninguna referencia a DPV (búsqueda `dpv|vapor`, sin resultados).
- Las métricas de la zona viven en `ZonaEntity` (`zona.raw(key)` es lo que usa `HistorialService.currentMetric`).
- Verificado: **no hay historial de lecturas**; `updateTelemetry` pisa las columnas `*Raw` de la zona. El historial se crea en
  `add-historial-lecturas`, que es **prerrequisito** de este cambio. Acá sólo se calcula el DPV y se escribe en la zona y en esa serie.

## Decisiones

### D-1 — Cálculo puro
`VaporPressureDeficit.calculate(tempC, humPct)` estático. Devuelve `null` si falta alguna entrada o si la
humedad está fuera de 0–100 (no inventa un valor). Redondeo a 0,01 kPa al persistir; el cálculo interno no redondea.

### D-2 — Dónde se persiste
Columna `dpv` (+ `dpv_ts`) en la zona, junto a las otras 10 métricas, y la columna `dpv` de `zone_reading` (`add-historial-lecturas`).
Se calcula al ingerir cada lectura (no al pedirla), porque las reglas lo leen del `RuleContext`.
Si la lectura no trae temperatura **o** humedad ambiental, el DPV queda con su valor anterior y su
`dpv_ts` viejo: las reglas ya comparan frescura por métrica (D-3 de `add-seguridad-lectura-hardware`).

### D-3 — Tramos y estado
Para §1.4: `< 0,3` "aire saturado"; `0,5–1,2` confortable; `1,2–2,0` demanda alta; `≥ 2,0` calor seco. Los
huecos (0,3–0,5) quedan sin etiqueta propia en la v2: se muestran como "transición" **sin** estado de alerta.
Es informativo (`afectaEstado: false`), igual que las 5 de la sonda de suelo: no cambia la salud del sector.
Los umbrales de las reglas (0,3 y 2,0) se declaran en el catálogo de cada regla que los use, no acá.

### D-4 — Consulta para reglas
Se usa `ReadingHistory` (`latest(zone, n, onlyPulse)` y `max(zone, ReadingMetric.DPV, window)`) de `add-historial-lecturas`; no se crea un
`DpvHistorial` propio. Sin lecturas suficientes ⇒ lista corta y la regla registra `SIN_DATO`.

### D-5 — Frontend
Tipo `Zona.lectura.dpv` y tarjeta en el detalle de zona con el color de su tramo (token existente
`--ok/--warn/--crit` según corresponda; nunca hex). En modo `mock`, un valor determinístico derivado de T y HR del mock.

### D-6 — Cómo quedó implementado (nombres en inglés, CLAUDE.md §7)
- `readings/VaporPressureDeficit` (cálculo puro y redondeo), `readings/VaporPressureBand` (tramos y etiquetas) y `dto/VaporDeficit`
  (`value`, `band`, `label`). `ZonaEntity.dpvRaw`/`dpvTs`; **no** pasa por `raw()/setRaw()`, que son las diez sensadas.
- El DTO va en `LecturaZona.dpv`, **fuera** de `metrics`: así no necesita `MetricSpec`, ni umbrales editables (`umbral_metrica`), ni lo toca
  la validación de rango de S-03, ni entra en las alertas por métrica. Con el nodo fuera de servicio viaja `null`.
- Se calcula con T y HR **aceptadas en esa lectura**: una descartada por S-03 cuenta como ausente y el DPV conserva el valor anterior.
- La consulta para las reglas es `ReadingHistory` tal cual (D-4): `ReadingMetric.DPV` ya existía desde `add-historial-lecturas`.
- Frontend: fila `VaporDeficitRow` en `SensingCard` (no abre histórico: no hay serie del DPV en el dashboard). Tono por tramo en
  `features/map/vaporBand.ts`: saturado y demanda alta = atención, calor seco = crítico visual, confortable y transición = ok.
  Mock: `data/mock/vaporDeficit.ts`, espejo de la fórmula y los tramos del backend.
