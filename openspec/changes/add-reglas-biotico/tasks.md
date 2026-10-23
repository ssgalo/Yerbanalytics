# Tasks: add-reglas-biotico

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-reglas-biotico`. Requiere `add-metrica-dpv` y `add-dosificacion-base`.

## 0. Previo
- [x] 0.1 D-1 (¿`Daño biótico` genérico?) y D-6 (vecinos de otra MZ): se implementó el **default del diseño** (ninguno de los dos casos dispara; los vecinos de otra MZ no se tratan). Sigue pendiente validarlo con el vivero.
- [x] 0.2 Verificado: `n` va de 1 a N dentro de la MZ y `sectoresPorFila` es un layout visual; los vecinos se calculan con esa convención (supuesto a validar con el vivero, ver D-6).
- [x] 0.3 `add-historial-lecturas`, `add-metrica-dpv` y `add-dosificacion-base` están integrados (ESTADO.md).

## 1. Utilidades puras
- [x] 1.1 Test: `Severity` (la `SeveridadV2` del diseño): Baja/Media/Alta ⇒ leve/moderada/alta.
- [x] 1.2 Test (tabla): `GridNeighbors.of(n, sectoresPorFila, total)` (la `Vecinos.de` del diseño) con esquinas y bordes.
- [x] 1.3 Test: `ConsecutiveCaptures.count(...)` (la `CapturasSeguidas.de` del diseño) — 3 de días distintos; una pasada faltante corta la racha. Más `BioticKind` (mapa de estados, D-1).

## 2. Parámetros
- [x] 2.1 Test y catálogo (D-8): `ParametrosBiotico` (familia `BIOTICO`) y `insumo.tope-fitosanitario[-dias]`; el frontend los muestra en Parámetros (fixture del catálogo). La temperatura predisponente son dos parámetros (`-min` y `-max`).

## 3. Reglas
- [x] 3.1 F-F1: test por cada condición y por el caso "espera"; `FungalDamageRule` (la `DanoFungicoRule` del diseño).
- [x] 3.2 F-P1: ídem, `FoliarPestRule` (estados `Plaga foliar` y `Ácaro`). F-F1 y F-P1 comparten el esqueleto `BioticDiagnosisBase`.
- [x] 3.3 F-F2: `FungalConditionRule` (la `CondicionFungicaRule` del diseño), de macro-zona; alerta única por evento sin estado propio.
- [x] 3.4 F-04: `OutbreakRule` (la `FocoRule` del diseño), de macro-zona; vecinos sin P-D1; test del borde.
- [x] 3.5 Todas con flag ON/OFF y patrón de CLAUDE.md §6 (`BioticWiringTest`).

## 4. Contrato con el servicio de aplicación
- [x] 4.1 Test: la marca "diagnóstico usado" se pone al ejecutar la aplicación (D-4); la diferida lleva `diagnosis_ts` (el instante del diagnóstico, que es lo que se compara con el último usado, en vez del `diagnostico_id`).
- [x] 4.2 Ajustar `ApplicationService` y el uso de `usaDiagnostico()`: `ResultadoEvaluacion.usoDiagnostico` es `false` si una regla pidió una aplicación por el diagnóstico.

## 5. F-06
- [x] 5.1 Test: tope de 3 en 30 días; cuarta se rechaza; revisión lo levanta.
- [x] 5.2 Endpoint de revisión + permiso en `MapaPermisos`: `POST /api/sectores/{id}/revision-fitosanitario` con `insumos.gestionar` (no hay permiso nuevo ni migración). Quedó sin botón en el dashboard: lo agrega `add-efectividad-y-s06` junto a la revisión de la efectividad.

## 6. Cierre
- [x] 6.1 Inspector; README; índice; suites verdes.

## 7. Control desde el simulador (requiere `add-control-demo-reglas`)
- [x] 7.1 `FungalDamageRule`, `FoliarPestRule`, `FungalConditionRule`, `OutbreakRule` como `DemoControllable` (apagado; `persistence` en F-F1/F-P1; `readings` en F-F2; cooldown `window` en F-04; `cap` para F-06); tests de cada clave.
- [x] 7.2 Escenarios `FUNGAL_DAMAGE`, `FOLIAR_PEST`, `FUNGAL_CONDITION`, `OUTBREAK` y `FUNGICIDE_CAP`; `OUTBREAK` registra el diagnóstico en tres sectores por la API de diagnósticos. Tests.
