# Change: add-cola-actuadores-nodo

> Rama: `feat/esp32-cola-actuadores` (sobre `feat/demo-expo-happy-path`). Falla vista en vivo con el ESP32-S3 del
> prototipo el 17/10/2026.

## Why

En un mismo pulso el motor ordena riego (`valve ON` a los dos sectores), insumo (`pump ON {ml}`) y mediasombra
(`shade SET`). El nodo arranca todo a la vez: dos electroválvulas, dos minibombas y el motor de la malla. La fuente
del prototipo no banca el pico, cae la tensión y el ESP32 se reinicia ~7 s después (vuelve con otro client id de
MQTT): las acciones quedan cortadas y los ACK finales no llegan nunca. El historial se llena de "no confirmó a
tiempo" y la demo no muestra nada de lo que el motor decidió.

El parche que se probó (el happy path del simulador en tres pulsos, uno por regla, apagando las otras y esperando que
cada acción termine) esquiva el problema sólo para ese botón: un pulso normal del motor lo sigue provocando.

## What Changes

- **El nodo serializa las cargas.** `nodo_combinado` (y `nodo_actuador`, que comparte `actuacion/`) recibe los
  comandos como hoy y los pone en una cola en RAM: corre a lo sumo `MAX_CARGAS_SIMULTANEAS` (config.h, por defecto 1)
  acciones de carga a la vez, en orden de llegada. Backend, motor y happy path siguen mandando todo en un pulso.
- **Lo de seguridad no hace cola.** `valve OFF` y `pump OFF` actúan al instante y además descartan lo encolado del
  mismo sector y actuador (cada descartado recibe su ACK final `ERROR cancelado`). Un `shade SET` con
  `parametros.prioridad = "INMEDIATA"` (lo manda sólo `ActuatorShutdown`: estado seguro S-02) tampoco espera.
- **ACK intermedio.** El nodo avisa cuándo una acción queda en cola y cuándo empieza:
  `status: "EN_CURSO"` con `detalle.tipo` `en_cola` o `iniciado`, por el mismo tópico de ACK. El ACK final
  (`SUCCESS`/`ERROR`) no cambia.
- **El backend usa el arranque.** `SeguimientoComandosService` mantiene pendiente el comando y recalcula su plazo
  desde el arranque; `DespachoRiego` deriva "Regando" desde `iniciado` (y muestra "En cola" mientras el nodo lo
  tiene encolado); `SupplyDoseService` hace lo mismo con "Dosificando"; `SecuenciaService` no confunde un intermedio
  con el fin de un paso. Sin intermedios (firmware viejo, sketch de la expo) todo sigue como hoy.
- **Espejos y simulador.** `contrato.h` (fuente de verdad), `ContratoNodo.java` y `simulador/server/contract.ts`. El
  simulador serializa y emite los intermedios igual que el firmware, así una demo sin hardware se comporta igual.
- **Happy path de vuelta a un pulso.** Con el nodo serializando, el botón vuelve a lo original (un pulso con las tres
  reglas): se quitan el encendido/apagado de reglas por paso y la espera de fin. Queda la tarjeta por regla, con el
  avance de cada acción (en cola / en curso / hecho) según los estados que deriva el backend.

## Out of scope

- El rendimiento del vivero real (100 sectores por nodo, tandas de 10 válvulas): esto es para el prototipo y la demo.
  El límite configurable alcanza; no se diseña planificación de potencia.
- Persistir la cola: vive en RAM y se pierde en un reinicio; lo encolado termina como la alerta "no confirmó a tiempo"
  que ya existe. Un evento de arranque del nodo queda como trabajo futuro.
- El sketch `prototipo_hardware/vivero_esp32_red`: sigue con un solo ACK final por comando, que el backend sigue
  aceptando (los intermedios son opcionales).
- El riel (`nursery/rail/*`): tiene su propia cola de un lugar y su propio ciclo; no entra en el límite de cargas.
- Frontend: no cambia. "En cola" ya es un estado de válvula que muestra; el de la bomba se muestra tal cual llega.
