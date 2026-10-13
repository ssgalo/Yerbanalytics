# Tasks: add-rustificacion-por-macrozona

## 1. Frontend
- [x] 1.1 Tipos `PlanRustificacion`, `EtapaRustificacion`; `Configuracion` sin `rustificacion`; horario por defecto en `ConfigOperativa`
- [x] 1.2 Repositorio: `getPlanRustificacion`, `iniciarPlanRustificacion`, `cancelarPlanRustificacion` (HTTP y mock)
- [x] 1.3 Tarjeta de Rustificación en el detalle de macro-zona: formulario, plan vigente, cancelación
- [x] 1.4 Sección "Mediasombra" en Umbrales; se quita la vista Rustificación de Configuración
- [x] 1.5 Tests

## 2. Backend (pendiente)
- [ ] 2.1 Endpoints `/api/zonas/{zonaId}/rustificacion` y permisos
- [ ] 2.2 Horario por defecto en la configuración; baja de `rustificacion_etapa`
- [ ] 2.3 Motor: ventana del plan de la zona o la por defecto; abrir/cerrar
