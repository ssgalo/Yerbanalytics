# Proposal: finish-release-2

## Goal
Implementar los componentes REST API y la integración final del motor necesarios para completar la **Release 2** (Automatización Agronómica Configurable y Alertas Inteligentes), específicamente abordando los faltantes de **HU-10 (Alertas Inteligentes)** y **HU-19 (Bloqueo Manual)**.

## Context
Tras la auditoría de la Release 2, se confirmó que el núcleo duro del motor de reglas, la persistencia de configuración, la integración climática y los tópicos MQTT están completamente implementados. 

Sin embargo, existen dos brechas funcionales que impiden dar por cerrada la release:

1. **Faltante HU-19 (Bloqueo Manual):** El motor (`BloqueoManualRule`) ya es capaz de detener la ejecución autónoma consultando la tabla `bloqueo_manual`. Sin embargo, no existe un `BloqueoController` que permita a los clientes HTTP (el frontend del operario) listar, crear o desactivar estos bloqueos.
2. **Faltante HU-10 (Alertas Inteligentes):** El frontend ya tiene diseñada la UI para las alertas (`AlertsDropdown`, `domain.ts`), pero funciona con datos mockeados. En el backend, falta crear la entidad de persistencia (`AlertaEntity`), el controlador REST (`AlertaController`) y hacer que el `ActionExecutor` genere registros reales de alerta cuando toma decisiones críticas (ej. dosificar insumo, posponer por clima).

## Value
Completar estas historias cierra formalmente el alcance de la Release 2, dotando al sistema de la capacidad real de notificar al usuario sobre eventos autónomos críticos (HU-10) y dándole al operario el control de emergencia para frenar el sistema (HU-19).

## Solution
1. **HU-19:** Crear `BloqueoController` exponiendo `GET /api/bloqueos`, `POST /api/bloqueos` y `DELETE /api/bloqueos/{id}`.
2. **HU-10 Backend:** Crear `AlertaEntity` y `AlertaRepository`. Modificar el `ActionExecutor` para insertar registros en esta tabla según la severidad de la acción evaluada (CRÍTICA, ALTA, MEDIA, INFO). Crear `AlertaController` para servir `GET /api/alertas` y `PATCH /api/alertas/{id}/leida`.
3. **HU-10 Frontend:** Integrar la capa de datos (`HttpRepository`) para que el `AlertsDropdown` consuma las alertas reales del backend cuando `VITE_DATA_SOURCE=http`.
