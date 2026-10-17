## ADDED Requirements

### Requirement: Estado del pronóstico
El sistema SHALL distinguir tres estados del pronóstico — disponible, vencido y ausente — y NO SHALL entregar a las
reglas un pronóstico más viejo que la vigencia utilizable del servicio (hoy 4 veces el TTL de 15 min, o sea 60 min).

#### Scenario: Pronóstico fresco
- **WHEN** el último pronóstico se obtuvo hace 20 min
- **THEN** el estado es disponible y las reglas lo reciben

#### Scenario: Pronóstico vencido
- **WHEN** el último pronóstico se obtuvo hace 2 h
- **THEN** el estado es vencido y las reglas actúan como si no hubiera pronóstico

#### Scenario: Nunca hubo
- **WHEN** el sistema arrancó sin conexión
- **THEN** el estado es ausente

### Requirement: O-01 Riego sin pronóstico
Sin pronóstico, R-03 NO SHALL posponer y R-01 SHALL regar; la revalidación del despacho NO SHALL posponer.

#### Scenario: Sin pronóstico y sustrato seco
- **WHEN** la humedad es 43 % a las 10:00 y no hay pronóstico
- **THEN** R-01 riega y la traza dice "sin pronóstico: se asume que no llueve"

### Requirement: O-02 Mediasombra sin pronóstico
Sin pronóstico, M-02 SHALL decidir sólo con la última lectura (temperatura y luz) y M-01 SHALL seguir por horario.

#### Scenario: Calor leído
- **WHEN** la última lectura da 36 °C a las 13:00 y no hay pronóstico
- **THEN** M-02 cierra la malla

#### Scenario: Sin lectura que alcance
- **WHEN** la lectura da 30 °C con luz 90 % y el pronóstico (ausente) habría dado 34 °C
- **THEN** M-02 no actúa

### Requirement: O-03 Aplicaciones sin pronóstico
Sin pronóstico, P-D4 SHALL no bloquear y P-D3 SHALL usar sólo la temperatura de la última lectura.

#### Scenario: Lluvia desconocida
- **WHEN** todas las demás precondiciones se cumplen y no hay pronóstico
- **THEN** la aplicación procede

### Requirement: O-04 Plan de rustificación sin conexión
El plan de rustificación SHALL avanzar con el reloj, sin depender de la conexión.

#### Scenario: Sin red varios días
- **WHEN** el sistema no tiene internet durante 3 días de un plan en curso
- **THEN** el día del plan avanza 3 y los horarios rigen normalmente

### Requirement: Aviso del modo sin pronóstico
Al entrar y al salir del modo sin pronóstico el sistema SHALL emitir una alerta `INFO`, una por evento.

#### Scenario: Se cae y vuelve
- **WHEN** el pronóstico pasa a vencido y 6 h después vuelve
- **THEN** quedan exactamente dos alertas `INFO`

### Requirement: O-05 Sincronización
El sistema SHALL dejar documentado si la sincronización de eventos con destino remoto aplica a la arquitectura
actual y, en caso afirmativo, SHALL subir los eventos con su marca de tiempo original y sin duplicados.

#### Scenario: Arquitectura sin destino remoto
- **WHEN** no existe un destino remoto de eventos
- **THEN** O-05 queda registrada como no aplicable, con el criterio para reabrirla
