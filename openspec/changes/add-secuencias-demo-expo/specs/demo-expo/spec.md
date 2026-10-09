## ADDED Requirements

> `demo-expo` nace en `add-pasada-riel`, que todavía no se archivó. Este cambio le suma un requisito;
> los de esa propuesta no cambian.

### Requirement: Secuencias en la vista Demo Expo

La vista Demo Expo SHALL ofrecer, junto a la pasada del riel, las tres secuencias (riego con su
duración, mediasombra con su espera, lectura de sensores) consumiendo sólo `DataRepository`. SHALL
mostrar el progreso de la secuencia consultando cada 1 s mientras corre, la cuenta regresiva de las
esperas, los valores de la lectura con un enlace al Inspector de `/reglas`, y los errores con el texto
del backend. SHALL deshabilitar los botones mientras haya una secuencia o una pasada en curso, sin
reemplazar la validación del backend. En modo `mock` SHALL simular las tres secuencias, con la misma
exclusión entre pasada y secuencia.

#### Scenario: Riego en vivo
- **WHEN** el operador pide regar 10 s
- **THEN** ve abrir la válvula, una cuenta regresiva de 10 s y el cierre, sin recargar

#### Scenario: Rechazo
- **WHEN** el backend responde 409 o 400 al iniciar una secuencia
- **THEN** la vista muestra el mensaje del backend y no cambia la secuencia mostrada

#### Scenario: Lectura mostrada
- **WHEN** termina una secuencia de lectura
- **THEN** la vista muestra las métricas recibidas con su unidad y un enlace a `/reglas`

#### Scenario: Demo sin backend
- **WHEN** el dashboard corre con `VITE_DATA_SOURCE=mock`
- **THEN** cada secuencia avanza sola por sus tres pasos, y no se puede iniciar mientras la pasada simulada corre
