## ADDED Requirements

### Requirement: Interruptor Demo Expo

El backend SHALL persistir la visibilidad de la pestaña "Demo Expo" (`GET/PUT
/api/configuracion/demo-expo`, `{"visible": boolean}`, `false` por defecto) en una tabla propia, sin
tocar la configuración operativa. La página de Configuración SHALL ofrecer un switch que la guarda al
instante, y el Sidebar SHALL mostrar u ocultar la entrada sin recargar la página. El interruptor SHALL
NOT habilitar ni deshabilitar los endpoints de pasada.

#### Scenario: Activar
- **WHEN** el operador activa el switch en Configuración
- **THEN** el backend guarda `visible: true` y la entrada "Demo Expo" aparece en el Sidebar sin recargar

#### Scenario: Persistencia
- **WHEN** se recarga el dashboard o se reinicia el backend
- **THEN** la pestaña conserva la visibilidad guardada

#### Scenario: Ruta con el interruptor apagado
- **WHEN** se entra a `/demo-expo` con `visible: false`
- **THEN** la página informa que está desactivada y enlaza a Configuración

### Requirement: Vista Demo Expo

La vista SHALL consumir sólo `DataRepository`: iniciar y cancelar la pasada, mostrar el progreso de los
cinco pasos consultando cada 1 s mientras corre, la miniatura de cada foto recibida, el estado del
diagnóstico de cada captura (consultando cada 3 s hasta 5 min tras terminar) con enlace a "Diagnósticos
de IA", y los errores con el texto del backend. En modo `mock` SHALL simular la pasada sin backend.

#### Scenario: Progreso en vivo
- **WHEN** el operador inicia una pasada
- **THEN** cada paso pasa de pendiente a en curso a terminado sin recargar, y al recibir una foto aparece su miniatura

#### Scenario: Rechazo
- **WHEN** el backend responde 409 al iniciar
- **THEN** la vista muestra el mensaje del backend y no cambia la pasada mostrada

#### Scenario: Esperando la IA
- **WHEN** la pasada terminó y una captura todavía no tiene diagnóstico
- **THEN** la vista indica que espera el diagnóstico y lo muestra cuando aparece

#### Scenario: Demo sin backend
- **WHEN** el dashboard corre con `VITE_DATA_SOURCE=mock`
- **THEN** la pasada avanza sola por los cinco pasos con fotos y diagnósticos de demostración
