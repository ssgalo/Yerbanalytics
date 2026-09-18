# Design: finish-release-2

## Context
Este documento detalla el diseño técnico para completar las funcionalidades REST pendientes de HU-10 (Alertas) y HU-19 (Bloqueo manual).

## 1. HU-19: REST API de Bloqueo Manual
Actualmente `BloqueoManualEntity`, `BloqueoManualRepository` y `BloqueoManualRule` existen y el motor los respeta. Falta la capa de exposición.

### Endpoints (`BloqueoController`)
*   **`GET /api/bloqueos`**
    *   Query params: `sector` (opcional), `zona` (opcional).
    *   Response: Lista de bloqueos activos. Llama a `findByActivoTrue()` (o derivados) del repositorio.
*   **`POST /api/bloqueos`**
    *   Body: `{ zonaId: string, sectorId?: string, activadoPor: string, motivo: string }`
    *   Action: Crea un `BloqueoManualEntity` con `activo = true`, `activadoTs = ahora()`.
    *   Side-effect: Registra un evento en `HistorialRepository` de tipo "BloqueoManual" para trazabilidad.
*   **`DELETE /api/bloqueos/{id}`**
    *   Action: Busca el bloqueo, lo marca `activo = false`.
    *   Side-effect: Registra en el historial la desactivación.

## 2. HU-10: Alertas Inteligentes (Backend)
Las alertas informan al productor sobre eventos críticos sin tener que buscar en el historial de cada sector.

### Modelo de Datos (`AlertaEntity`)
*   `id` (Long, PK)
*   `severidad` (String): CRITICA, ALTA, MEDIA, INFO.
*   `sectorId` (String), `zonaId` (String)
*   `mensaje` (String)
*   `timestamp` (Long)
*   `leida` (Boolean, default: false)

### Integración en `ActionExecutor`
El `ActionExecutor` ya persiste en `historial_evento`. Ahora también debe generar y persistir en `AlertaRepository`:
*   Si ejecuta `ACTIVAR_BOMBA` (Insumo) → Genera alerta `CRITICA`.
*   Si emite `ABORT_RIEGO` (Nodo caído) → Genera alerta `CRITICA`.
*   Si ejecuta `ACTIVAR_VALVULA` (Riego) → Genera alerta `ALTA`.
*   Si ejecuta `MOVER_MEDIASOMBRA` (Por UV) → Genera alerta `ALTA`.
*   Si emite `POSTPONE_RIEGO` (Lluvia) → Genera alerta `MEDIA`.
*   Si emite `ABORT_ALL` (Bloqueo manual) → Genera alerta `INFO`.

### Endpoints (`AlertaController`)
*   **`GET /api/alertas`**
    *   Query params: `leida` (boolean opcional, por defecto false para traer las no leídas).
    *   Response: Lista ordenada por timestamp descendente.
*   **`PATCH /api/alertas/{id}/leida`**
    *   Action: Marca `leida = true`.

## 3. HU-10: Alertas Inteligentes (Frontend)
El componente visual (`AlertsDropdown`) y el tipo (`Alert`) ya están listos y consumen datos del `mock`.

### Modificación en capa de datos
*   En `HttpRepository.ts`, el método `getAlertas()` (actualmente probablemente devolviendo un arreglo vacío o mock) debe hacer un fetch a `GET /api/alertas?leida=false`.
*   Mapear `AlertaEntity` a la interfaz `Alert` del frontend (transformando el timestamp a string, asignando colores según severidad).
*   Agregar método en `DataRepository` para marcar alerta como leída (`patchAlertaLeida(id)`), que en modo HTTP llame a `PATCH /api/alertas/{id}/leida`.
