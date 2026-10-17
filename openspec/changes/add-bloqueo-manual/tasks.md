# Tasks: add-bloqueo-manual

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-bloqueo-manual`.

## 1. Backend: API
- [ ] 1.1 Test (MockMvc): `POST /api/bloqueos` 201 con id; sin `zonaId` 400; `GET` con filtros; `DELETE` desactiva.
- [ ] 1.2 `BloqueoController` + `BloqueoService` (entidad `ManualLockEntity` existente).
- [ ] 1.3 Permisos `bloqueos.ver`/`bloqueos.gestionar` en `Permiso` y `MapaPermisos`; matriz sembrada. `MapaPermisosCoberturaTest` verde.
- [ ] 1.4 Test: 403 sin permiso.

## 2. Backend: efecto
- [ ] 2.1 Test: activar cierra válvulas y bombas del alcance y vacía la cola de riego (sector y MZ).
- [ ] 2.2 Implementar con `ComandoActuadorPublisher` y la cola de `engine/riego/`.
- [ ] 2.3 Test: reanudar no encola nada.
- [ ] 2.4 Alertas INFO en el historial (activar y reanudar).
- [ ] 2.5 Test de `ManualLockRule` con bloqueo de MZ sobre un sector de la zona (hoy sin test de alcance).

## 3. Frontend
- [ ] 3.1 Tipos y repositorio (`http` y `mock`) de bloqueos.
- [ ] 3.2 Botón y diálogo con motivo obligatorio en detalle de sector y de macro-zona; estado "Bloqueado".
- [ ] 3.3 Tests y `npm run lint` en cero warnings; colores sólo por token.

## 4. Cierre
- [ ] 4.1 README del backend (sección Autenticación y permisos) y marcar el cambio en el índice.
- [ ] 4.2 Anotar en `add-mediasombra-v2` que M-01/M-02 deben respetar el bloqueo de MZ (D-2).

## 5. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 5.1 Test: `ManualLockRule` sigue en `/api/demo/rules` con bypass `lock`; apagada no corta.
- [ ] 5.2 Escenario `LOCK` del simulador (crea y levanta bloqueos por `/backend/api/bloqueos`) con su test.
- [ ] 5.3 Decidir y sembrar el permiso `bloqueos.gestionar` para la cuenta Servicio del simulador en `YERBANALYTICS_USUARIOS_INICIALES`/matriz de desarrollo, sin código del backend específico del simulador (CLAUDE.md §6.2).
