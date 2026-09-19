## MODIFIED Requirements

### Requirement: Seguimiento post-acción e historial
El detalle SHALL mostrar el seguimiento antes/ahora cuando hubo acciones, y el
historial inalterable de acciones del sector (lectura → decisión → acción).
El historial SHALL obtenerse a través del backend real (`useHistory` filtrado por el sector) 
en lugar de utilizar mocks locales, mostrando las últimas 5 acciones. Si el sector no cuenta 
con historial de acciones recientes, SHALL mostrar un empty state.

#### Scenario: Sector sano sin seguimiento
- **WHEN** el sector está sano (sin acciones recientes)
- **THEN** se muestra el mensaje de que opera dentro de parámetros

#### Scenario: Sector sin acciones registradas
- **WHEN** el sector no tiene historial de acciones en la base de datos
- **THEN** el componente SectorHistory muestra el mensaje "No hay acciones registradas en el historial reciente para este sector."

#### Scenario: Sector con múltiples acciones
- **WHEN** el sector tiene más de 5 acciones en su historial
- **THEN** el componente SectorHistory muestra solo las 5 acciones más recientes, ordenadas de más a menos reciente.
