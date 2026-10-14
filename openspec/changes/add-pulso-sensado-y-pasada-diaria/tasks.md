## 1. Configuración (backend)

- [x] 1.1 `ConfiguracionOperativaEntity` + DTO: sumar `pasadaDiariaActiva` (false) y `pasadaDiariaHora` ("11:00"); quitar `sensingPullIntervalSeconds` e `intervaloEvaluacionMinutos`
- [x] 1.2 `ConfiguracionService`: validar `pasadaDiariaHora` (`HH:mm`, 00:00–23:59) y `intervaloSensadoMinutos` (60–360) con `400` que nombra el parámetro; nulos leídos como valores de fábrica
- [x] 1.3 `SensingCadence`: umbral `max(piso, intervalo × 60 + reintento-seg × intentos)` y acotado del intervalo a 60–360; propiedades `yerbanalytics.pulso.reintento-seg` (60) e `intentos` (3)
- [x] 1.4 `effectiveStaleThresholdSeconds`, `StaleSensorRule`, `FrescuraLectura` y el despacho de riego usan el umbral nuevo
- [x] 1.5 `resources/migracion-pulso-sensado.sql`: borrar `sensing_pull_interval_seconds` e `intervalo_evaluacion_minutos`
- [x] 1.6 Tests: `SensingCadenceTest`, `ConfiguracionServiceIntervaloTest`, contrato del controlador de configuración (campos nuevos, campos retirados ignorados, hora inválida)

## 2. Pulso de sensado (backend)

- [x] 2.1 `PulsoSensadoService`: pulso en memoria (origen, `ejecutaMotor`, zonas pendientes, intentos), `iniciar(origen, ejecutaMotor)` con rechazo si hay uno en curso
- [x] 2.2 Planificador en el carril `sensingScheduler`: próximo disparo con `CicloLectura`, releyendo el intervalo; omitir si hay uno en curso; reemplaza a `SensingScheduler.pullAll`
- [x] 2.3 Tick de reintentos: reenviar `LEER_AHORA` a las zonas pendientes, cerrar la zona sin respuesta al vencer el tercer pedido, cerrar el pulso sin zonas pendientes; aislar fallas por zona
- [x] 2.4 `respondePulso(zonaId, recepcionMs)`: atómico; marca la zona como respondida y devuelve si corresponde ejecutar el motor
- [x] 2.5 `NurseryService.updateTelemetry`: ejecutar el motor sólo si `respondePulso` lo indica; si no, persistir lectura y estado de sectores sin motor
- [x] 2.6 `PulsoController`: `POST /api/pulsos {ejecutarMotor}` (`202`, `409`); declarar en `MapaPermisos` con `pasadas.operar`
- [x] 2.7 Tests: franjas y cambio en caliente, pulso con y sin motor, `409`, reintentos, telemetría anterior al pedido, segunda respuesta sin reevaluar, telemetría fuera de pulso sin motor, `MapaPermisosCoberturaTest`

## 3. Escalamiento y fuera de servicio (backend)

- [x] 3.1 `ZonaEntity.pulsosSinRespuesta`; al cerrar una zona sin respuesta, incrementar y registrar alerta (`WARNING`, `WARNING`, `CRITICAL`; nada desde el cuarto)
- [x] 3.2 Cualquier telemetría de la zona vuelve la cuenta a 0; si venía de ≥ 3, alerta `INFO` de recuperación
- [x] 3.3 Snapshot (`NurseryService`): "Fuera de servicio" por `pulsosSinRespuesta >= 3` o zona sin lecturas, no por el umbral de lectura vieja
- [x] 3.4 `HardwareService`: estado del nodo testigo por la cuenta (0 Operativo, 1–2 Señal intermitente, ≥ 3 Fuera de servicio); quitar los umbrales de heartbeat para el nodo testigo
- [x] 3.5 Tests: secuencia de alertas en tres pulsos, sin alertas repetidas, recuperación, racha cortada, cuenta persistida tras reinicio, snapshot y hardware

## 4. Motor: sin barrido y diagnóstico nuevo (backend)

- [x] 4.1 Eliminar `NurseryWatchdog`, `OrigenEvaluacion.BARRIDO` y lo que dependa de ellos (traza, `ActionExecutor`, tests)
- [x] 4.2 `Rule.usaDiagnostico()` por defecto `false`; `true` en `SupplyRule`
- [x] 4.3 `EstadoRegla.OMITIDA_SIN_DIAGNOSTICO_NUEVO`; el Inspector/DAG del frontend lo muestra
- [x] 4.4 `RuleOrchestrator`: saltear las reglas de diagnóstico sin diagnóstico nuevo; `ResultadoEvaluacion` informa si se evaluó alguna
- [x] 4.5 `SectorEntity`: `diagnosisTs` y `diagnosisUsadoTs`; `RuleContext.diagnosticoNuevo()`
- [x] 4.6 `DiagnosticoService.alta`: guardar `diagnosisTs` con el `creadoEn` del diagnóstico
- [x] 4.7 `NurseryService`: marcar el diagnóstico como usado en la misma transacción cuando se evaluó una regla de diagnóstico
- [x] 4.8 Borrar de `updateTelemetry` la asignación del diagnóstico derivado del estado (y `PATHOS` si queda sin uso)
- [x] 4.9 Tests: primer pulso usa el diagnóstico y el siguiente no, bloqueo manual lo deja nuevo, rollback lo deja nuevo, lectura no pisa el diagnóstico; ajustar `NurseryServiceRiegoTest`, `RiegoIntegracionTest` y los tests de reglas afectados

## 5. Historial (backend)

- [x] 5.1 `ActionExecutor`: revertir el tope `inaccion-intervalo-min` (commit `d9cc7d5`) y quitar la deduplicación por firma; registrar la inacción completa en cada evaluación
- [x] 5.2 Tests: `ActionExecutorInaccionTest` (dos pulsos con la misma decisión escriben dos conjuntos)

## 6. Pasada diaria (backend)

- [x] 6.1 `PasadaProgramadaService`: tick por minuto en `pasadaScheduler`; dispara `PasadaRielService.iniciar()` en el minuto configurado si está activa, una vez por minuto
- [x] 6.2 Rechazo a la hora programada → alerta `WARNING` con el motivo, zona del recorrido, sin reintento
- [x] 6.3 Tests con reloj inyectado: dispara a la hora, desactivada no dispara, cambio de hora en caliente, no repite en el mismo minuto, alerta ante rechazo, la manual no cambia

## 7. Frontend

- [x] 7.1 `types/domain.ts`, mock y `httpRepository`: quitar `sensingPullIntervalSeconds` e `intervaloEvaluacionMinutos`; sumar `pasadaDiariaActiva` y `pasadaDiariaHora`; `changes.ts` y `configValidation.ts`
- [x] 7.2 `IntervalosForm`: un solo campo "Intervalo de sensado y evaluación" con el umbral del backend
- [x] 7.3 Bloque "Pasada diaria del riel" (interruptor + hora) en la vista de configuración que corresponda, con el sistema de diseño
- [x] 7.4 Capa de datos: `ParametrosSecuencia.ejecutarMotor` (el pedido de pulso de todas las zonas no se usa desde la vista)
- [x] 7.5 Demo Expo: "Leer sensores y ejecutar motor ahora" junto a "Leer sensores ahora" en la tarjeta Lectura; sin botones de pulso de todas las zonas (D11)
- [x] 7.7 Backend: secuencia `LECTURA` con `ejecutarMotor` emite un pulso con motor de su zona; `409` con pulso en curso; tests en `SecuenciaServiceTest`
- [x] 7.6 Tests (vitest) de formularios, capa de datos y la sección de Demo Expo; `npm run lint` y `npm test` en verde

## 8. Documentación y cierre

- [x] 8.1 README del backend: pulso, escalamiento, sin barrido, diagnóstico nuevo, pasada diaria, migración manual; actualizar la sección del historial
- [x] 8.2 CLAUDE.md: endpoint de pulso, quinto script de migración pendiente, glosario (pulso de sensado), quitar el barrido
- [ ] 8.3 Verificación manual con el simulador: "Leer sensores ahora" muestra la lectura sin motor; "Leer sensores y ejecutar motor ahora" deja las decisiones de la zona en el historial; tres pulsos (`POST /api/pulsos`) con la emisión apagada escalan hasta fuera de servicio y vuelven al prenderla; pasada manual → diagnóstico → pulso con motor consume el diagnóstico; pasada programada a un minuto próximo
- [x] 8.4 Suite completa del backend sin fallos nuevos respecto de los 8 preexistentes
