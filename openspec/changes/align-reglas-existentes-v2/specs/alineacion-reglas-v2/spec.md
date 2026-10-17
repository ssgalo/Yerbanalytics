## ADDED Requirements

### Requirement: Confianza mínima del diagnóstico
El catálogo SHALL traer `diagnostico.confianza-minima` con fábrica 70 % y rango permitido 50–95 %, y las
reglas que usan el diagnóstico SHALL comparar contra ese parámetro.

#### Scenario: Instalación nueva
- **WHEN** se siembra el catálogo en una base vacía
- **THEN** `diagnostico.confianza-minima` vale 70

#### Scenario: Valor editado por el agrónomo
- **WHEN** el agrónomo había guardado 80 y se actualiza el sistema
- **THEN** el valor sigue siendo 80

#### Scenario: Valor fuera del rango nuevo
- **WHEN** se intenta guardar 97
- **THEN** el guardado se rechaza por superar el máximo de 95

### Requirement: Estado seguro al declarar el nodo fuera de servicio
Cuando el nodo testigo de una macro-zona llega a la cuenta de fuera de servicio, el sistema SHALL ordenar
**una sola vez** el estado seguro de esa MZ: válvulas cerradas, bombas detenidas y mediasombra cerrada, y
SHALL registrar la orden en el historial.

#### Scenario: Tercer fallo
- **WHEN** el tercer pulso consecutivo de MZ-2 cierra sin respuesta
- **THEN** salen los comandos de cierre de válvulas, apagado de bombas y mediasombra cerrada de MZ-2, y
  queda una alerta `CRITICAL`

#### Scenario: Cuarto fallo y siguientes
- **WHEN** MZ-2 acumula un cuarto pulso sin respuesta
- **THEN** no se publica ningún comando de estado seguro

#### Scenario: El nodo vuelve
- **WHEN** MZ-2 responde otra vez
- **THEN** la cuenta vuelve a 0, queda una alerta `INFO`, y no se envía ninguna orden retroactiva

### Requirement: Reintento con el nodo fuera de servicio
Mientras una MZ esté fuera de servicio, el sistema SHALL pedir lectura al nodo cada
`sensado.reintento-fuera-servicio` minutos (fábrica 15).

#### Scenario: Pasan 15 minutos
- **WHEN** MZ-2 está fuera de servicio y pasaron 15 min desde el último pedido
- **THEN** se publica `LEER_AHORA` a MZ-2

#### Scenario: Zona sana
- **WHEN** una MZ tiene la cuenta en 0
- **THEN** el reintento periódico no la toca

### Requirement: Parámetros de sensado en el catálogo
Los plazos de espera y reintentos de la lectura SHALL ser parámetros del catálogo, con los rangos de
`reglas_v2` §11, y SHALL regir el pulso de sensado.

#### Scenario: Cambio de reintentos
- **WHEN** el agrónomo guarda `sensado.reintentos` = 3
- **THEN** el siguiente pulso pide hasta 4 lecturas a una zona que no responde

#### Scenario: Valor inválido
- **WHEN** se guarda `sensado.espera-respuesta` = 5 s
- **THEN** se rechaza por estar bajo el mínimo de 10 s

### Requirement: La seguridad por frescura no corta la mediasombra
La regla de lectura vencida SHALL cortar sólo el riego; la mediasombra por horario y por pronóstico SHALL
seguir evaluándose.

#### Scenario: Lectura vieja y franja nueva
- **WHEN** la última lectura de MZ-1 está vencida y empieza una franja de mediasombra
- **THEN** el riego queda abortado y la mediasombra se evalúa igual
