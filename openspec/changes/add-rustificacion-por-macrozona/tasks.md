# Tasks: add-rustificacion-por-macrozona

## 1. Frontend
- [x] 1.1 Tipos `PlanRustificacion`, `EtapaRustificacion`; `Configuracion` sin `rustificacion`; horario por defecto en `ConfigOperativa`
- [x] 1.2 Repositorio: `getPlanRustificacion`, `iniciarPlanRustificacion`, `cancelarPlanRustificacion` (HTTP y mock)
- [x] 1.3 Tarjeta de Rustificación en el detalle de macro-zona: formulario, plan vigente, cancelación
- [x] 1.4 Sección "Mediasombra" en Umbrales; se quita la vista Rustificación de Configuración
- [x] 1.5 Tests
- [x] 1.6 Sin `finalizado`: la última etapa se sostiene; botón «Eliminar plan»; horario de fábrica 08:00–17:00 (D8)
- [x] 1.7 Alta de hardware: mediasombra por macro-zona; detalle de sector con la mediasombra de la zona (D8)

## 2. Mediasombra de macro-zona (D1, D2, D4)
- [x] 2.1 Registro: la mediasombra es equipo de zona (una por MZ); `HardwareService`/`HardwareCoverage`/`MallaZona`
- [x] 2.2 `ZonaEntity.mediasombraAbierta`; baja de `SectorEntity.actuadorShade`; snapshot `zonas[].mediasombra`
- [x] 2.3 Contrato MQTT: `shade SET` por `nursery/zone/{z}/command`, ACK en `nursery/zone/{z}/ack` (backend, simulador, firmware)
- [x] 2.4 `ShadingRule` y `ShadeFaultRule` pasan a `ZoneRule`; corte de rama en `ZoneRuleEngine`
- [x] 2.5 `ActuatorShutdown`, `SecuenciaService`, `SeguimientoComandosService` y `HardwareFaultService` con la malla de zona
- [x] 2.6 Migración manual `migracion-mediasombra-por-zona.sql`; `data.sql` con una mediasombra por zona
- [ ] 2.7 Compilar el firmware (`nodo_actuador`, `nodo_combinado`, `nodo_sensor`) y probarlo con el hardware

## 3. Plan de rustificación (D3, D5, D6)
- [x] 3.1 `PlanRustificacionEntity`, `EtapaPlanRustificacion`, repositorio; estado calculado
- [x] 3.2 `PlanRustificacionService` + controller `/api/zonas/{zonaId}/rustificacion`; `MapaPermisos`; historial
- [x] 3.3 `HorarioMediasombraService`: bordes de ventana, guardas (bloqueo, falla, sin malla, estado seguro), `DemoControllable`
- [x] 3.4 Simulador: el nodo simulado atiende la malla por el tópico de zona; happy path y fallas con la malla de zona

## 4. Horario por defecto (D7)
- [x] 4.1 `mediasombraAbiertaDesde/Hasta` en `ConfigOperativa` con validación
- [x] 4.2 Baja de la rustificación global (dto, entidad, repo, data.sql, `sowing-date-iso`)

## 5. Cierre
- [x] 5.1 Tests (horario, plan, API de ACK, reglas de zona, hardware, estado seguro, secuencias; frontend y simulador)
- [x] 5.2 README del backend, simulador, firmware y CLAUDE.md
- [x] 5.3 Correr la migración manual en la base de desarrollo
