## ADDED Requirements

### Requirement: Horario por defecto en la vista Rustificación
La vista Rustificación (`/rustificacion`, grupo Configuración) SHALL permitir editar `mediasombraAbiertaDesde` y
`mediasombraAbiertaHasta`, que rigen en toda macro-zona sin plan vigente, y SHALL listar las macro-zonas con su plan vigente
o "sin plan", cada una enlazada a su detalle. Guarda el mismo recurso de configuración que Umbrales y Preferencias. Este
requisito reemplaza el "Horario por defecto de la mediasombra" de `add-rustificacion-por-macrozona`, que lo ubicaba en
Umbrales.

#### Scenario: Editar el horario por defecto
- **WHEN** el agrónomo cambia el horario a 09:00–16:00 en Rustificación y guarda
- **THEN** las macro-zonas sin plan siguen esa ventana desde la próxima revisión del horario

#### Scenario: Horario incoherente
- **WHEN** el cierre es anterior o igual a la apertura
- **THEN** la vista marca el error y bloquea el guardado

#### Scenario: Zona con plan
- **WHEN** MZ-2 tiene un plan en curso
- **THEN** la lista la muestra con su etapa y un enlace a su detalle
