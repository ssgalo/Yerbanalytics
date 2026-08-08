## REMOVED Requirements

### Requirement: Modo de operación en runtime, persistido
**Reason**: El modo de operación era el interruptor del simulador y dejó de ser estado del
sistema. El backend ya no tiene modos: se comporta siempre como en producción.
**Migration**: El modo pasa a ser `VITE_DATA_SOURCE` del dashboard (`http` = sistema real,
`mock` = demo ilustrativa), especificado en `data-layer`. Las tablas `modo_operacion` y su
semilla se dan de baja con el script de migración manual del cambio.

### Requirement: Envío manual de telemetría por MQTT
**Reason**: El backend dejó de publicar telemetría; ahora sólo la consume. El envío manual lo
hace el simulador publicando directo al broker, como cualquier nodo.
**Migration**: Ver `simulador-telemetria`, requisitos «El simulador publica al broker como un
nodo más» y «Envío por métrica o completo, con fecha/hora opcional». El endpoint
`POST /api/simulacion/telemetria` desaparece.

### Requirement: Fecha y hora opcional del envío
**Reason**: Se mueve al simulador junto con el envío.
**Migration**: Ver `simulador-telemetria`, requisito «Envío por métrica o completo, con
fecha/hora opcional».

### Requirement: Gating del simulador automático
**Reason**: El simulador automático dejó de vivir en el backend, así que no hay nada que
condicionar. Apagar el simulador equivale hoy a apagar su proceso.
**Migration**: Ver `simulador-telemetria`, requisito «Emisión automática periódica». Las
propiedades `yerbanalytics.mqtt.simulator.*` se eliminan.

### Requirement: Gestión de sensores simulados persistidos
**Reason**: Los sensores simulados son estado del simulador, no del vivero, y ocupaban una
tabla de la base de producción.
**Migration**: Ver `simulador-telemetria`, requisito «Gestión de sensores simulados propios».
La tabla `sensor_simulado` se da de baja con el script de migración manual del cambio; los
sensores de prueba existentes se vuelven a dar de alta en el simulador.

### Requirement: Heartbeat del nodo por coincidencia de serial/MAC
**Reason**: No es un requisito del simulador sino de la ingesta del sistema: rige igual para
la telemetría de un ESP32 físico.
**Migration**: Se traslada sin cambios de comportamiento a `sensado-persistencia`.

### Requirement: Regeneración de topología robusta bajo concurrencia
**Reason**: No es un requisito del simulador sino de la gestión de topología: el borrado en
bloque protege a cualquier regeneración, venga de donde venga.
**Migration**: Se traslada sin cambios de comportamiento a `topologia-persistencia`.
