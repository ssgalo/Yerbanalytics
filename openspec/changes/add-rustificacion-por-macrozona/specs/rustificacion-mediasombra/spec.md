## ADDED Requirements

### Requirement: Plan de rustificación por macro-zona
El detalle de una macro-zona SHALL mostrar su plan de rustificación vigente o, si no tiene, el
horario por defecto de la mediasombra y la acción "Iniciar plan". El plan SHALL definirse con una
fecha de inicio y etapas de N días con una ventana diaria de apertura (`HH:MM`–`HH:MM`), sin
porcentaje de apertura. Una zona SHALL tener a lo sumo un plan vigente, y SHALL poder cancelarse con
confirmación. Iniciar y cancelar SHALL requerir `configuracion.editar`.

#### Scenario: Iniciar un plan
- **WHEN** el agrónomo toca "Iniciar plan", elige la fecha y las etapas y confirma
- **THEN** la tarjeta muestra el plan programado o en curso, con la etapa y la ventana de hoy

#### Scenario: Cancelar el armado
- **WHEN** el agrónomo toca "Cancelar" en el formulario
- **THEN** no se crea ningún plan y la tarjeta vuelve al horario por defecto

#### Scenario: Cancelar un plan vigente
- **WHEN** el agrónomo confirma "Cancelar plan"
- **THEN** el plan deja de regir y la zona vuelve al horario por defecto

#### Scenario: Ventana inválida
- **WHEN** una etapa tiene la hora de cierre anterior o igual a la de apertura
- **THEN** el formulario lo marca y no deja iniciar el plan

### Requirement: Horario por defecto de la mediasombra
La vista Umbrales SHALL permitir configurar `mediasombraAbiertaDesde` y `mediasombraAbiertaHasta`,
que rigen en toda macro-zona sin plan vigente. Dentro de la ventana, otras reglas pueden cerrar la
mediasombra; fuera de ella, permanece cerrada.

#### Scenario: Horario incoherente
- **WHEN** el cierre es anterior o igual a la apertura
- **THEN** la vista marca el error y bloquea el guardado
