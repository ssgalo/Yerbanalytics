# Tasks: finish-release-2

- [ ] **HU-19: Backend REST**
  - [ ] Implementar `BloqueoService` que encapsule la lógica de alta/baja de bloqueos y su registro en `HistorialRepository`.
  - [ ] Implementar `BloqueoController` con `GET /api/bloqueos`, `POST /api/bloqueos` y `DELETE /api/bloqueos/{id}`.

- [ ] **HU-10: Backend Persistencia y API**
  - [ ] Crear entidad `AlertaEntity` y repositorio `AlertaRepository`.
  - [ ] Implementar `AlertaController` con endpoints de consulta y marcado como leída.
  - [ ] Integrar `AlertaRepository` en `ActionExecutor`.
  - [ ] Añadir lógica en `ActionExecutor` para emitir alertas con la severidad correspondiente según el tipo de acción / decisión tomada por el motor.

- [ ] **HU-10: Frontend Integración**
  - [ ] Actualizar la interfaz `DataRepository` (si no lo tiene) para incluir `getAlertas()` y `markAlertRead(id)`.
  - [ ] Implementar los métodos en `HttpRepository.ts` realizando los fetch correspondientes a `/api/alertas`.
  - [ ] Actualizar la vista `AlertsDropdown` y el `NurseryContext` para que disparen la mutación de "marcar como leída" hacia el repositorio.
