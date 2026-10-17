## ADDED Requirements

### Requirement: N-01 Aplicación del plan de nutrición
Cuando en una MZ hayan pasado los días del intervalo del plan desde la última aplicación del plan, el motor SHALL
pedir 100 mL de fertilizante en cada sector de la MZ, en la próxima ventana apta, sin depender de los valores
medidos. Un sector que no cumple las precondiciones SHALL reintentarse en la ventana siguiente.

#### Scenario: Se cumple el intervalo
- **WHEN** MZ-2 tiene intervalo 7 días y su última aplicación del plan fue hace 7 días, y son las 07:00
- **THEN** se piden 100 mL de fertilizante para cada sector de MZ-2 y queda un `INFO`

#### Scenario: Todavía no
- **WHEN** pasaron 5 días
- **THEN** N-01 no actúa

#### Scenario: Un sector no cumple
- **WHEN** S-045 tiene humedad 40 % a las 07:00
- **THEN** su aplicación queda diferida y se reintenta a las 17:00; los demás sectores se aplican

#### Scenario: Zona sin historial
- **WHEN** MZ-4 nunca recibió una aplicación del plan
- **THEN** N-01 se considera vencido y aplica en la próxima ventana apta

#### Scenario: Idempotencia
- **WHEN** N-01 ya generó las aplicaciones de este ciclo y se vuelve a evaluar en la misma ventana
- **THEN** no genera aplicaciones duplicadas

### Requirement: N-02 CE baja
El motor SHALL adelantar N-01 a la próxima ventana apta y emitir `INFO` "CE baja: la fertilización de base del
sustrato podría estar agotada" cuando la CE media de las últimas 6 lecturas de la MZ sea menor a 0,5 dS/m y no haya
habido fertilización en la MZ en los últimos 5 días.

#### Scenario: CE baja sin fertilizar
- **WHEN** la media de CE es 0,4 dS/m, la última fertilización fue hace 6 días y el intervalo del plan es 10 días
- **THEN** N-01 se dispara en la próxima ventana apta y queda el `INFO`

#### Scenario: Fertilizó hace poco
- **WHEN** la media es 0,4 dS/m pero hubo fertilización hace 3 días
- **THEN** N-02 no actúa

#### Scenario: Datos insuficientes
- **WHEN** hay sólo 4 lecturas de CE
- **THEN** la traza marca "sin dato" y N-02 no actúa

#### Scenario: Una sola alerta
- **WHEN** N-02 ya adelantó el plan y todavía no se ejecutó
- **THEN** no se emite otra alerta en la lectura siguiente

### Requirement: Intervalo del plan por macro-zona
El intervalo del plan de nutrición SHALL configurarse por MZ (fábrica 7 días, rango 3–30) y SHALL poder cambiarse
desde el detalle de la macro-zona.

#### Scenario: Alargar durante la rustificación
- **WHEN** el agrónomo fija 14 días en MZ-3
- **THEN** N-01 espera 14 días desde la última aplicación del plan

#### Scenario: Fuera de rango
- **WHEN** se intenta guardar 2 días
- **THEN** se rechaza
