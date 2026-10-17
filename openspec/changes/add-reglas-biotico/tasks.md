# Tasks: add-reglas-biotico

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-biotico`. Requiere `add-metrica-dpv` y `add-dosificacion-base`.

## 0. Previo
- [ ] 0.1 Resolver D-1 (¿`Daño biótico` genérico?) y D-6 (vecinos de otra MZ) con el equipo.
- [x] 0.2 Verificado: `n` va de 1 a N dentro de la MZ y `sectoresPorFila` es un layout visual; los vecinos se calculan con esa convención (supuesto a validar con el vivero, ver D-6).
- [ ] 0.3 Confirmar que `add-historial-lecturas` y `add-dosificacion-base` están integrados.

## 1. Utilidades puras
- [ ] 1.1 Test: `SeveridadV2` (Baja/Media/Alta ⇒ leve/moderada/alta).
- [ ] 1.2 Test (tabla): `Vecinos.de(n, sectoresPorFila, filas)` con esquinas y bordes.
- [ ] 1.3 Test: `CapturasSeguidas.de(diagnosticos)` — 3 de días distintos; una pasada faltante corta la racha.

## 2. Parámetros
- [ ] 2.1 Test y catálogo (D-8), frontend en Parámetros.

## 3. Reglas
- [ ] 3.1 F-F1: test por cada condición y por el caso "espera"; implementar `DanoFungicoRule`.
- [ ] 3.2 F-P1: ídem, `PlagaFoliarRule` (estados `Plaga foliar` y `Ácaro`).
- [ ] 3.3 F-F2: `CondicionFungicaRule` por MZ; alerta única por evento.
- [ ] 3.4 F-04: `FocoRule`; vecinos sin P-D1; test del borde.
- [ ] 3.5 Todas con flag ON/OFF y patrón de CLAUDE.md §6.

## 4. Contrato con el servicio de aplicación
- [ ] 4.1 Test: la marca "diagnóstico usado" se pone al ejecutar la aplicación (D-4); la diferida lleva `diagnostico_id`.
- [ ] 4.2 Ajustar `ApplicationService` y el uso de `usaDiagnostico()`.

## 5. F-06
- [ ] 5.1 Test: tope de 3 en 30 días; cuarta se rechaza; revisión lo levanta.
- [ ] 5.2 Endpoint de revisión + permiso en `MapaPermisos` (o reusar el de `add-efectividad-y-s06` si ya está).

## 6. Cierre
- [ ] 6.1 Inspector; README; índice; suites verdes.

## 7. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 7.1 `DanoFungicoRule`, `PlagaFoliarRule`, `CondicionFungicaRule`, `FocoRule` como `DemoControllable` (apagado; `persistence` en F-F1/F-P1; `readings` en F-F2; cooldown `window` en F-04; `cap` para F-06); tests de cada clave.
- [ ] 7.2 Escenarios `FUNGAL_DAMAGE`, `FOLIAR_PEST`, `FUNGAL_CONDITION`, `OUTBREAK` y `FUNGICIDE_CAP`; `OUTBREAK` registra el diagnóstico en tres sectores por la API de diagnósticos. Tests.
