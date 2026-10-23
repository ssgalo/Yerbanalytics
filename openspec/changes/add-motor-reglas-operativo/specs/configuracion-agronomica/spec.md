## MODIFIED Requirements

### Requirement: Vista de configuración por bloques
La vista Umbrales (`/configuracion`) SHALL presentar sólo las bandas de alerta de las 10 métricas sensadas
(`umbral_metrica`: ideal, warn, crit y puntos de alerta). Los parámetros que consume el motor SHALL editarse en Motor de
reglas, y el horario por defecto de la mediasombra en la vista Rustificación. La vista SHALL NOT enlazar ni describir el
motor de reglas.

#### Scenario: Render de la configuración vigente
- **WHEN** el Ingeniero Agrónomo abre `/configuracion`
- **THEN** ve las bandas vigentes de las 10 métricas, y ningún bloque de seguimiento, límites de insumos ni mediasombra

## REMOVED Requirements

### Requirement: Edición de límites operativos
**Reason**: Lo que quedaba de este bloque era `insumoDosisMax24hMl`, que ninguna regla compara y duplica
`insumo.max-dosis-24h` del catálogo. Los demás límites ya se habían mudado al catálogo del motor o dado de baja.
**Migration**: `migracion-umbrales-al-motor.sql` borra `insumo_dosis_max_24h_ml`. El tope de dosis se edita en Motor de
reglas (`insumo.max-dosis-24h`).

### Requirement: Edición de parámetros de seguimiento
**Reason**: La latencia y el delta del seguimiento post-acción son parámetros que lee el motor; el catálogo es la única
fuente de umbrales que deciden (CLAUDE.md §6).
**Migration**: Pasan a `seguimiento.latencia-min` y `seguimiento.delta-min` del catálogo (requisito "Parámetros de
seguimiento en el catálogo" de `motor-reglas-operativo`). `migracion-umbrales-al-motor.sql` copia los valores editados a
`parametro_regla` y borra las columnas.
