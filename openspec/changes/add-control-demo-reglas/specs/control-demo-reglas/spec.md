## ADDED Requirements

Todo lo de este cambio existe **sólo** con `yerbanalytics.demo.enabled=true`; en producción las rutas `/api/demo/**`
responden 404 y el dashboard productivo no tiene ningún control de demo.

### Requirement: Toda pieza de decisión es controlable desde el simulador
Cada regla o componente que decide una acción del sistema SHALL ser un `DemoControllable` y SHALL aparecer en
`GET /api/demo/rules` con su nombre, etiqueta, rama, prioridad, claves de cooldown, claves de bypass y su estado actual,
tanto si corre en la cadena por sector como si corre por macro-zona o como servicio.

#### Scenario: Regla por macro-zona
- **WHEN** existe una regla de mediasombra por macro-zona
- **THEN** `GET /api/demo/rules` la lista junto a las reglas por sector

#### Scenario: Componente que no es regla
- **WHEN** existen las precondiciones de aplicación de insumos
- **THEN** figuran en el listado con sus claves de bypass

#### Scenario: Producción
- **WHEN** el backend corre sin modo demo
- **THEN** `GET /api/demo/rules` responde 404

### Requirement: Apagar y encender una regla
`PUT /api/demo/rules/{nombre}` con `enabled=false` SHALL hacer que la pieza no actúe ni corte nada, dejando constancia
(`OMITIDA_DESHABILITADA` para las reglas por sector, y el mismo motivo en la traza o el registro propio de las demás).
Reencenderla SHALL restablecer su comportamiento sin reiniciar.

#### Scenario: Apagar una regla por zona
- **WHEN** se apaga la regla de protección por calor y la temperatura es 37 °C a las 13:00
- **THEN** la malla no se cierra por esa regla y la traza dice que está deshabilitada

#### Scenario: Reencender
- **WHEN** se vuelve a encender
- **THEN** en el siguiente pulso decide con normalidad

#### Scenario: Nombre desconocido
- **WHEN** se hace `PUT /api/demo/rules/NoExiste`
- **THEN** la respuesta es 404

### Requirement: Cooldowns y guardas con claves declaradas
Toda espera, intervalo, latencia o ventana de tiempo de una regla SHALL declararse como `cooldownKey` (valor en minutos, 0 la
elimina) y toda guarda de estado o franja horaria como `bypassKey`, y la regla SHALL leerlos del contexto en vez de usar
constantes. Los componentes que revalidan en nombre de una regla SHALL consultar `skipsGuard`.

#### Scenario: Cooldown en cero
- **WHEN** se fija en 0 el intervalo de una regla y se dispara el pulso
- **THEN** la regla decide sin esperar el intervalo

#### Scenario: Bypass de ventana
- **WHEN** se activa el bypass de la ventana horaria de aplicaciones a las 12:00
- **THEN** la aplicación procede aunque esté fuera de 07–10 y 17–19

#### Scenario: Clave no declarada
- **WHEN** se intenta fijar un cooldown con una clave que la regla no declara
- **THEN** se rechaza con 400

#### Scenario: Cooldowns a cero
- **WHEN** se invoca `PUT /api/demo/rules/cooldowns/zero`
- **THEN** quedan en 0 los cooldowns de todos los `DemoControllable`, no sólo los de la cadena por sector

### Requirement: Clima simulado completo
`POST /api/demo/weather` SHALL producir un pronóstico con 24 marcas horarias (probabilidad de lluvia, milímetros y
temperatura) y el UV máximo del día; si sólo llegan los campos antiguos SHALL derivar las marcas de ellos. `PUT
/api/demo/weather/offline` SHALL simular la ausencia de pronóstico.

#### Scenario: R-03 se puede accionar
- **WHEN** se inyecta probabilidad 80 % y 8 mm y la humedad de la zona es 40 %
- **THEN** `lluviaProxima(ahora, 4)` devuelve 80 % y al menos 5 mm, y R-03 pospone el riego

#### Scenario: Temperatura por hora
- **WHEN** se inyecta una temperatura de 36 °C para las 13:00
- **THEN** el pronóstico de esa hora devuelve 36 °C

#### Scenario: Sin pronóstico
- **WHEN** se activa el modo offline
- **THEN** el servicio de pronóstico devuelve "sin pronóstico" hasta que se desactive

### Requirement: Escenario del simulador por cada regla
Toda regla nueva SHALL tener en el simulador un escenario propio que, al seleccionarla, arme la telemetría, el clima y el
diagnóstico previo que la hacen disparar en el siguiente pulso, avise de los conflictos con otras reglas seleccionadas y
fije los cooldowns y bypass necesarios, con su test.

#### Scenario: Selección de una regla nueva
- **WHEN** el operador del simulador marca una regla nueva y pulsa "Disparar"
- **THEN** se publican las lecturas que la hacen cumplir su condición y se pide un pulso con motor

#### Scenario: Reglas excluyentes
- **WHEN** se seleccionan dos reglas cuyas condiciones son incompatibles
- **THEN** el simulador lo informa y no dispara

#### Scenario: Regla sin escenario
- **WHEN** existe un `DemoControllable` sin escenario en el simulador
- **THEN** el listado de reglas del simulador lo muestra en gris con "sin escenario" y falla la lista de verificación del cambio que lo agregó

### Requirement: Valores de la v2 en el simulador
El simulador SHALL usar como valores por defecto los de la v2 y SHALL NOT forzar condiciones propias de reglas retiradas.

#### Scenario: Confianza mínima
- **WHEN** el catálogo del backend no responde
- **THEN** el umbral de confianza por defecto es 70

#### Scenario: Reglas retiradas
- **WHEN** se retira la dosificación por estado crítico
- **THEN** el simulador no fuerza la temperatura crítica que sólo ella necesitaba
