## ADDED Requirements

### Requirement: Plan de rustificación por macro-zona
El detalle de una macro-zona SHALL mostrar su plan de rustificación vigente o, si no tiene, el
horario por defecto de la mediasombra y la acción "Iniciar plan". El plan SHALL definirse con una
fecha de inicio y etapas de N días con una ventana diaria de apertura (`HH:MM`–`HH:MM`), sin
porcentaje de apertura. Una zona SHALL tener a lo sumo un plan vigente, y SHALL poder eliminarse con
confirmación. Iniciar y eliminar SHALL requerir `configuracion.editar`. Pasada la última etapa, el plan
SHALL seguir vigente con la ventana de la última etapa hasta que alguien lo elimine.

#### Scenario: Iniciar un plan
- **WHEN** el agrónomo toca "Iniciar plan", elige la fecha y las etapas y confirma
- **THEN** la tarjeta muestra el plan programado o en curso, con la etapa y la ventana de hoy

#### Scenario: Cancelar el armado
- **WHEN** el agrónomo toca "Cancelar" en el formulario
- **THEN** no se crea ningún plan y la tarjeta vuelve al horario por defecto

#### Scenario: Eliminar un plan vigente
- **WHEN** el agrónomo confirma "Eliminar plan"
- **THEN** el plan deja de regir y la zona vuelve al horario por defecto

#### Scenario: Terminó la última etapa
- **WHEN** pasaron todos los días de las etapas
- **THEN** el plan sigue en curso y la mediasombra sigue la ventana de la última etapa hasta que se elimine el plan

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

### Requirement: El horario de la mediasombra no es parte del motor
La mediasombra de cada macro-zona SHALL abrirse y cerrarse según la ventana de hoy (la de la etapa del plan en curso
o, sin plan, la del horario por defecto) por un servicio fuera del motor de reglas, que SHALL ordenar sólo cuando la
posición programada cambia (bordes de la ventana, alta o baja de un plan, cambio del horario o de etapa). Las reglas del
motor SHALL poder mover la malla en cualquier momento y el horario SHALL NOT corregirla hasta el próximo borde. No SHALL
mandar órdenes a una zona con bloqueo manual, sin mediasombra registrada, con la malla averiada o en estado seguro.

#### Scenario: Apertura en el borde
- **WHEN** la hora del vivero entra en la ventana de hoy de la zona
- **THEN** se ordena abrir su mediasombra y queda en el historial

#### Scenario: El motor la cierra dentro de la ventana
- **WHEN** a las 12 un pulso detecta un pico de UV y la regla cierra la malla de una zona con ventana 10–15
- **THEN** la malla queda cerrada; el horario no la reabre y a las 15 ordena cerrarla (ya lo está)

#### Scenario: Zona bloqueada en el borde
- **WHEN** la ventana abre en una zona con bloqueo manual
- **THEN** no se manda ninguna orden y el historial lo registra como omitido

### Requirement: La mediasombra es de la macro-zona
Cada macro-zona SHALL tener a lo sumo una mediasombra, registrada con la zona y sin sector, como el nodo testigo. Su
posición SHALL guardarse en la zona, su `shade SET` SHALL publicarse por el comando de zona y su ACK SHALL recibirse por el
tópico de ACK de la zona. El simulador SHALL poder seguir abriéndola y cerrándola con las secuencias de Demo Expo.

#### Scenario: Registrar una segunda mediasombra en la zona
- **WHEN** se registra una mediasombra en una macro-zona que ya tiene una
- **THEN** el registro se rechaza (409)

#### Scenario: Abrir desde el simulador
- **WHEN** el simulador inicia la secuencia "Abrir mediasombra"
- **THEN** el backend publica `shade SET {targetPct: 100}` en `nursery/zone/{zona}/command` y espera el ACK en `nursery/zone/{zona}/ack`

