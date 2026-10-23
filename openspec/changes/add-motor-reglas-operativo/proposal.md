# Change: add-motor-reglas-operativo

## Why

Hoy el motor de reglas no se puede operar desde el sistema real. Prender o apagar una regla vive sólo
en memoria (`engine/overrides/RuleOverridesStore`) y sólo se escribe por `/api/demo/rules/**`, que no
existe con `yerbanalytics.demo.enabled=false`. En producción no hay forma de decir "esta regla no la
quiero mientras validamos su rango con el vivero" sin redeployar con otra propiedad
(`yerbanalytics.reglas.retiradas`), y un reinicio borra cualquier cambio.

Además, la pantalla Motor de reglas está ordenada por cómo corre el motor (rama técnica, prioridad),
no por cómo piensa el agrónomo (qué quiero lograr: regar, nutrir, sanar, sombrear). Los umbrales de una
regla están lejos de la regla, los compartidos no se distinguen, y la interfaz arrastra emojis desde el
backend (`Rule.label()`), con un parche en el historial (`plainLabel`) para esconderlos.

Por último, Configuración → Umbrales mezcla las bandas de alerta de las métricas con parámetros que
consume el motor (seguimiento post-acción), un campo que no usa nadie (`insumoDosisMax24hMl`) y el horario
por defecto de la mediasombra, que es parte de la rustificación.

## What

- **Interruptor operativo por regla**, en producción. Toda pieza que decide (`DemoControllable`: reglas por
  sector, reglas de macro-zona y los servicios `ApplicationPreconditions`, `EscalamientoNodoService`,
  `ReadingValidationGuard`, `HardwareFaultService`, `HorarioMediasombraService`) se prende o apaga desde Motor
  de reglas. Se persiste en una tabla nueva (`regla_estado`), sobrevive reinicios y vale en modo `REAL`.
- **Estado efectivo = override explícito de demo, si existe; si no, el operativo.** El override de demo sigue en memoria,
  sólo existe en modo demo y sólo lo escribe el simulador (así el happy path de Demo Expo puede prender `SupplyRule`). `yerbanalytics.reglas.retiradas` pasa a ser la
  siembra del estado operativo cuando no hay fila.
- **Todas las reglas son conmutables, incluidas las protecciones** (S-01…S-05, R-04, guarda de ciclo,
  disponibilidad de la mediasombra). Apagar una protección exige confirmación en la UI con su consecuencia y,
  en el backend, `motivo` no vacío y confirmación explícita. Cada cambio queda con quién, cuándo y por qué,
  y como evento en el historial.
- **Permiso**: `reglas.editar` cubre umbrales e interruptores.
- **Sin emojis en la interfaz.** Se limpian en el origen (`label()` del backend y el mock), se borra
  `plainLabel`, y los símbolos sueltos del Inspector pasan a componentes del sistema de diseño. La regla entra
  en CLAUDE.md §4 y en el spec del sistema de diseño.
- **Rediseño de Motor de reglas**: reglas agrupadas por objetivo agronómico, una tarjeta por regla con nombre,
  código, efecto, interruptor y sus umbrales en línea; parámetros compartidos marcados y editados una vez.
  El Inspector se queda, limpio.
- **Historial**: una regla apagada hoy sigue apareciendo en el grafo de las ejecuciones pasadas en que corrió.
- **Configuración → Umbrales queda sólo con las 10 bandas de alerta.** Seguimiento post-acción pasa al
  catálogo del motor; `insumoDosisMax24hMl` se borra; el horario por defecto de la mediasombra se muda a la
  vista de Rustificación. Migración manual nueva.
- **Invariante nuevo en CLAUDE.md**: existe un interruptor operativo en producción; los controles de demo
  (cooldowns, bypass, apagado de demo) siguen viviendo sólo en el simulador.

## Fuera de alcance

- Interruptores por macro-zona o por sector (el interruptor es de la regla, para todo el vivero).
- Programar un apagado con vencimiento ("apagar hasta el lunes").
- Que el simulador escriba el estado operativo (ver design D-9).
- Los símbolos ✓/⚠ de los logs del simulador: no es la interfaz del producto.
- Las reglas nuevas de los PRs de reglas v2 (#64, #65, #67, #68): este cambio entra primero y fija lo que
  cada uno adapta (design, "Adaptación de los PRs de reglas v2").
