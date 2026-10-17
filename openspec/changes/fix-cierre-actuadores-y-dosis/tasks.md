# Tasks: fix-cierre-actuadores-y-dosis

TDD estricto: test que falla, después el código.

## 1. Riego cortado por ack `cancelado`
- [x] 1.1 Tests de `SeguimientoComandosService`: publica `ActuatorStopped` en `cancelado` de la válvula; no en `reemplazado` ni `ok`.
- [x] 1.2 `ActuatorStopped` + publicación en el seguimiento.
- [x] 1.3 Tests de `DespachoRiego`: cierra y deja huella, libera cupo, ignora sectores sin riego / bomba / reemplazado.
- [x] 1.4 `DespachoRiego.onActuatorStopped` + `HistorialService.registrarCorteRiego`.
- [x] 1.5 Tests y código de la reconstrucción tras reinicio (riego anterior al corte no cuenta; posterior sí).

## 2. Bomba atascada en "Dosificando"
- [x] 2.1 Tests de `SupplyDoseService` (ack final, plazo sin ack, tope sin duración, estado huérfano).
- [x] 2.2 `SupplyDoseService`, `SectorRepository.cambiarBomba` y `HistorialRepository.findFirstBySectorIdAndTipoOrderByTsDesc`.
- [x] 2.3 Plazo de seguimiento del comando de la bomba cuenta los ml.

## 3. Dosis decidida por el backend
- [x] 3.1 Tests: `insumo.dosis-ml` (fábrica 10, 1–50), `SupplyRule` con `DetalleInsumo`, `ActionExecutor` publica `{"ml"}`.
- [x] 3.2 `ParametrosInsumo.DOSIS_ML`, `DetalleInsumo`, `SupplyRule`, `ActionExecutor`, `registrarInsumo(sector, ml)`.
- [x] 3.3 Simulador: test de `pump ON {ml}`; fixture y tests del frontend.

## 4. Revisión (bajo riesgo)
- [x] 4.0a Test (`SectorPumpStateTest`) y `@DynamicUpdate` en `SectorEntity`: un cierre de dosis confirmado en el medio no se pisa.
- [x] 4.0b Test y `commandId` en `ActuatorStopped` / `Curso`: un cancelado tardío de un comando viejo no cierra el riego nuevo.

## 5. Docs
- [x] 5.1 README del backend (despacho de riego y dosificación). CLAUDE.md no cambia: no cambia ningún invariante.
- [ ] 5.2 Verificar en vivo con el ESP32-S3: `valve OFF` manual a mitad de un riego y una dosis completa + otra.
