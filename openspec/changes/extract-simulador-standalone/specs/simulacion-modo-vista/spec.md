## REMOVED Requirements

### Requirement: La vista del vivero sigue el modo persistido para elegir su fuente de datos
**Reason**: La capacidad entera desaparece. El modo dejó de ser estado del backend, y la
elección de fuente vuelve a ser lo que siempre debió ser: la selección por entorno que
`data-layer` ya especifica. Además el requisito sólo gobernaba el snapshot del vivero,
mientras el resto de las secciones seguía `VITE_DATA_SOURCE`, con lo que era posible tener en
pantalla un mapa mock junto a hardware real.
**Migration**: Ver `data-layer`, requisito «Selección de origen por entorno», que ahora exige
un único punto de decisión válido para **todas** las secciones.

### Requirement: El modo se respeta al refrescar la página
**Reason**: Sin lectura de modo al montar, no hay nada que pueda revertirse al refrescar: el
origen queda fijado por la variable de entorno con la que se levantó el dashboard.
**Migration**: Ver `data-layer`, requisito «Selección de origen por entorno», escenario «El
origen no se consulta al backend».

### Requirement: Degradación a estático si no se puede leer el modo
**Reason**: La degradación existía para tolerar que la consulta del modo fallara. Ya no hay
consulta que pueda fallar, y degradar en silencio sería peor: en modo real un backend caído
debe reportarse como error, no disfrazarse de demo.
**Migration**: Ver `data-layer`, requisito «Selección de origen por entorno», escenario «La
demo carga sin backend»: la demo se levanta eligiéndola, no por caída del backend.
