## REMOVED Requirements

### Requirement: App de simulación standalone en puerto propio
**Reason**: La app de simulación dejó de ser una segunda entrada del proyecto frontend —con
el que compartía dependencias, tipos, estilos y capa de datos— para ser un proyecto
independiente. Correr en un puerto propio ya no alcanza como criterio de aislamiento.
**Migration**: Ver `simulador-standalone`, requisitos «El simulador es un proyecto
independiente y borrable», «Arranque separado del sistema» y «El simulador no aparece en el
dashboard».

### Requirement: Switch de modo que refleja el modo persistido
**Reason**: Ya no hay modo persistido en el backend que reflejar. El modo es una decisión de
arranque del dashboard, no un control del simulador.
**Migration**: Ver `data-layer`, requisito «Selección de origen por entorno». El dashboard se
levanta en modo real o en modo demo con comandos distintos.

### Requirement: Crear y asignar sensores simulados
**Reason**: Se traslada al simulador, con su propio almacenamiento.
**Migration**: Ver `simulador-telemetria`, requisito «Gestión de sensores simulados propios»,
que conserva la separación del registro de hardware y la validación de serial/MAC duplicado.

### Requirement: Envío de lecturas por sensor con fecha/hora opcional
**Reason**: Se traslada al simulador, que ahora publica directo al broker en vez de pedirle al
backend que publique.
**Migration**: Ver `simulador-telemetria`, requisito «Envío por métrica o completo, con
fecha/hora opcional».

### Requirement: Regenerar la topología desde el flujo de simulación
**Reason**: Se traslada al simulador. La condición «no disponible en modo estático» pierde
sentido: el simulador no tiene modo estático, y el dashboard en modo demo no habla con el
backend.
**Migration**: Ver `simulador-telemetria`, requisito «Regeneración de la topología desde el
simulador».
