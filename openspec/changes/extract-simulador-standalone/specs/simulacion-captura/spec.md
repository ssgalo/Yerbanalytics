## REMOVED Requirements

### Requirement: Panel de cámara dentro de la app de simulación
**Reason**: La capacidad se renombra a `simulador-camara`, porque el panel deja de vivir en el
proyecto frontend y pasa al simulador independiente, con su propio cliente HTTP.
**Migration**: Ver `simulador-camara`, requisito «Panel de cámara dentro del simulador».

### Requirement: El simulador no tiene superficie de API propia
**Reason**: Renombrado de capacidad. El requisito se conserva sin cambios de fondo, y el
invariante se refuerza además en `simulador-standalone`.
**Migration**: Ver `simulador-camara`, requisito «El panel de cámara no tiene superficie de
API propia», y `simulador-standalone`, requisito «El backend no conoce al simulador».

### Requirement: Visibilidad del dispositivo de cámara
**Reason**: Renombrado de capacidad, sin cambios de comportamiento.
**Migration**: Ver `simulador-camara`, requisito homónimo.

### Requirement: Solicitud manual de una captura de prueba
**Reason**: Renombrado de capacidad, sin cambios de comportamiento.
**Migration**: Ver `simulador-camara`, requisito homónimo.

### Requirement: Carga manual del diagnóstico sobre la captura recibida
**Reason**: Renombrado de capacidad, sin cambios de comportamiento.
**Migration**: Ver `simulador-camara`, requisito homónimo.

### Requirement: El diagnóstico cargado aparece en el dashboard
**Reason**: Renombrado de capacidad. Se precisa que la verificación es contra el dashboard en
modo real, ya que en modo demo el dashboard no consulta el backend.
**Migration**: Ver `simulador-camara`, requisito homónimo.
