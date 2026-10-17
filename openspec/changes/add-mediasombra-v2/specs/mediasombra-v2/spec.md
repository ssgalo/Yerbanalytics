## ADDED Requirements

Valores de fábrica salvo que el escenario diga otra cosa. "Hora" es hora local `America/Argentina/Buenos_Aires`.
La malla tiene dos estados: **cerrada** (sombra) y **abierta** (sol pleno), y se comanda por macro-zona.

### Requirement: M-01 Horario base
Al empezar una franja nueva, el sistema SHALL llevar la malla de cada MZ al estado que indica su horario:
el **perfil de crecimiento** si no tiene plan, o la **etapa del día** si tiene un plan activo o finalizado.
De noche (19:00–07:00 con plan; fuera de 08:00–10:00 sin plan) SHALL estar cerrada.

#### Scenario: Perfil de crecimiento, mañana
- **WHEN** MZ-1 no tiene plan y son las 08:00
- **THEN** se ordena abrir la malla de MZ-1 y el factor registrado es "horario"

#### Scenario: Perfil de crecimiento, fin de la ventana
- **WHEN** MZ-1 no tiene plan y son las 10:00
- **THEN** se ordena cerrar la malla

#### Scenario: Plan, etapa 2
- **WHEN** MZ-1 está en el día 14 de su plan y son las 16:00
- **THEN** la malla se abre (ventana 16–19 de la etapa 2) y el factor registrado es "plan"

#### Scenario: Sin cambio de estado
- **WHEN** la malla ya está en el estado que indica el horario
- **THEN** no se publica ningún comando

### Requirement: M-02 Protección por calor y sol extremo
Entre las 11:00 y las 16:00, si se cumple alguna de las condiciones, el sistema SHALL cerrar la malla de la MZ
hasta las 16:00 en cualquier perfil o etapa, y NO SHALL reabrirla antes aunque la condición desaparezca.
Condiciones: temperatura ≥ 35 °C; o luz ≥ 85 % con temperatura ≥ 32 °C; o UV pronosticado del día ≥ 11 con
temperatura ≥ 32 °C. La temperatura es la mayor entre la última lectura y el pronóstico de esa hora.

#### Scenario: Calor extremo
- **WHEN** a las 13:00 la lectura da 36 °C
- **THEN** la malla se cierra hasta las 16:00 y se emite `WARNING`

#### Scenario: Sol directo con calor
- **WHEN** a las 12:00 la luz es 90 % y la temperatura 33 °C
- **THEN** la malla se cierra hasta las 16:00 y se emite `INFO`

#### Scenario: Sol sin calor
- **WHEN** a las 12:00 la luz es 90 % y la temperatura 28 °C
- **THEN** M-02 no actúa

#### Scenario: Pronóstico más alto que la lectura
- **WHEN** a las 12:00 la lectura da 31 °C y el pronóstico para la hora da 34 °C con luz 88 %
- **THEN** la temperatura considerada es 34 °C y la malla se cierra

#### Scenario: No oscila
- **WHEN** a las 12:30 la malla se cerró por M-02 y a las 14:00 la temperatura baja a 29 °C
- **THEN** la malla sigue cerrada hasta las 16:00

#### Scenario: Calor crítico
- **WHEN** la temperatura llega a 39 °C
- **THEN** la alerta es `CRITICAL` "Calor extremo"

#### Scenario: Fuera de la ventana
- **WHEN** son las 16:30 y la temperatura es 36 °C
- **THEN** M-02 no actúa

### Requirement: En la mediasombra gana cerrar
Si dos reglas piden estados distintos para la misma MZ, el sistema SHALL ordenar cerrada.

#### Scenario: Horario abre, protección cierra
- **WHEN** el plan indica abierta a las 12:00 y M-02 pide cerrada
- **THEN** la malla queda cerrada

### Requirement: Ciclo de vida del plan de rustificación
El backend SHALL permitir iniciar, cancelar y resetear el plan de rustificación de una MZ, sólo al rol Ingeniero
Agrónomo, con un único plan vivo por MZ. El día de inicio es el día 1. Pasada la última etapa el plan SHALL quedar
`finalizado`, manteniendo el horario de la última etapa hasta que se resetee.

#### Scenario: Iniciar
- **WHEN** el Ingeniero Agrónomo inicia un plan en MZ-3 con fecha de hoy
- **THEN** hoy es el día 1 y el horario de la etapa 1 rige desde la próxima franja

#### Scenario: Cancelar
- **WHEN** se cancela un plan en curso a las 11:30
- **THEN** la MZ vuelve en ese momento al perfil de crecimiento y queda un `INFO`

#### Scenario: Fin automático
- **WHEN** pasa el día 45 de un plan de 45 días
- **THEN** el plan queda `finalizado`, la MZ sigue con el horario de la etapa 4 y queda un `INFO`
  "Plan finalizado: plantines listos para trasplante"

#### Scenario: Dos planes a la vez
- **WHEN** se intenta iniciar un plan en una MZ que ya tiene uno vivo
- **THEN** se rechaza con 409

#### Scenario: Rol incorrecto
- **WHEN** un Operario intenta iniciar un plan
- **THEN** la respuesta es 403

### Requirement: Etapas con más de una ventana
Una etapa SHALL admitir una o más ventanas diarias abiertas.

#### Scenario: Etapa 3
- **WHEN** la etapa 3 define las ventanas 07–12 y 15–19
- **THEN** la malla está abierta a las 11:59, cerrada de 12:00 a 14:59 y abierta de nuevo desde las 15:00

### Requirement: Respeto de bloqueo y de falla
El sistema NO SHALL publicar comandos de malla para una MZ con bloqueo manual activo ni con falla mecánica
registrada, y SHALL dejar la decisión en la traza.

#### Scenario: MZ bloqueada
- **WHEN** MZ-1 está bloqueada y empieza la franja de las 08:00
- **THEN** no se publica ningún comando y la traza lo indica
