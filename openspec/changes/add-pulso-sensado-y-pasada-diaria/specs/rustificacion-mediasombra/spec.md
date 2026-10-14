## REMOVED Requirements

### Requirement: Trigger vía Watchdog (sin telemetría)
**Reason**: Se elimina el barrido periódico del motor. El plan de rustificación define horarios fijos de apertura y
cierre que no dependen del motor de reglas; su ejecución por horario es parte de
`add-rustificacion-por-macrozona`, no de este cambio.
**Migration**: La regla de mediasombra se evalúa en cada pulso, como el resto de las reglas. La ejecución del plan
por horario queda a cargo del cambio de rustificación por macro-zona.
