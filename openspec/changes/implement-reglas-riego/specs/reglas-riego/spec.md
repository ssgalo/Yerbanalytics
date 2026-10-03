## ADDED Requirements

Valores de fábrica salvo que el escenario diga otra cosa: umbral 45 %, crítico 35 %, objetivo 65 %,
0,2 L/punto, volumen máx. 6 L, caudal 30 L/h, saturación 75 % (alerta 80 %), ventana 06:00–18:00,
lluvia 70 % / 5 mm / 4 h, pausa 6 h, tope R-02 12 h, 10 sectores simultáneos, ciclo de lectura de
240 min anclado a las 02:00. "Hora" es hora local `America/Argentina/Buenos_Aires`.

### Requirement: Volumen y tiempo de riego
Toda orden de riego SHALL llevar su volumen y su duración calculados antes de abrir:
`V = min((objetivo − humedad) × litrosPorPunto, volumenMax)` para R-01, `V = volumenMax` para R-02, y
`t = ceil(V / caudal × 3600)` segundos, con `V` redondeado a 0,01 L antes de calcular `t` (para que
el error de punto flotante no sume un segundo). Si `t` supera la duración máxima del contrato (1200 s), SHALL
recortarse a 1200 s y la traza SHALL indicarlo.

#### Scenario: Ejemplo de v2
- **WHEN** R-01 decide regar con humedad 45 % − ε (p. ej. 44,9 %)
- **THEN** el volumen es 4,02 L y la duración 483 s

#### Scenario: Tope de volumen
- **WHEN** `riego.litros-por-punto` vale 0,3 y R-01 decide regar con humedad 40 %
- **THEN** la fórmula da 7,5 L, el volumen es 6 L (tope) y la duración 720 s

#### Scenario: Caudal calibrado distinto
- **WHEN** `riego.caudal-emisor` vale 60 L/h y R-02 decide regar
- **THEN** el volumen es 6 L y la duración 360 s

#### Scenario: Configuración que no entra en la válvula
- **WHEN** se intenta guardar `riego.volumen-max-evento` = 10 con `riego.caudal-emisor` = 20
- **THEN** el guardado se rechaza (10 / 20 × 3600 = 1800 s > 1200 s) y no persiste nada

### Requirement: R-01 Riego por déficit hídrico
Con la última lectura de humedad de sustrato `h` tal que `crítico ≤ h < umbral`, dentro de la
ventana de riego y sin R-03, R-04 ni R-06 aplicando al sector, el motor SHALL ordenar el riego del
sector con el volumen de la fórmula. La traza SHALL registrar `h` contra ambos umbrales.

#### Scenario: Justo debajo del umbral
- **WHEN** la humedad es 44 % a las 10:05 sin lluvia prevista ni aplicación reciente
- **THEN** R-01 emite `ACTIVAR_VALVULA` con 4,2 L y 504 s, y la traza muestra "44 % < 45 % ✓"

#### Scenario: En el umbral no riega
- **WHEN** la humedad es 45 %
- **THEN** R-01 emite `NOOP_INFO` y la traza muestra "45 % < 45 % ✗"

#### Scenario: Déficit crítico lo cubre R-02
- **WHEN** la humedad es 34 %
- **THEN** R-01 no emite `ACTIVAR_VALVULA` (lo emitió R-02) y su motivo lo dice

#### Scenario: Umbral modificado
- **WHEN** `riego.umbral-humedad` tiene override 50 y la humedad es 48 %
- **THEN** R-01 riega

#### Scenario: Sin lectura de humedad
- **WHEN** la lectura no trae humedad de sustrato
- **THEN** la comparación queda `SIN_DATO` y no se riega

### Requirement: R-02 Déficit hídrico crítico
Con `h < crítico`, el motor SHALL ordenar el riego del sector con el volumen máximo, a cualquier
hora, aunque el pronóstico anuncie lluvia y aunque el sector esté en pausa por R-06, y SHALL emitir
una alerta `CRITICAL` "Déficit hídrico crítico" por macro-zona. Mientras no exista S-06, R-02 SHALL
respetar un máximo de un riego por sector cada `riego.exceptuado-bloqueo` horas.

#### Scenario: Justo debajo del crítico, de noche y con lluvia
- **WHEN** la humedad es 34 % a las 23:30, el pronóstico da 90 % y 12 mm en 4 h, y al sector se le
  aplicó fitosanitario hace 1 h
- **THEN** R-02 emite `ACTIVAR_VALVULA` con 6 L y 720 s; R-05, R-06 y R-03 registran "no aplica"

#### Scenario: En el crítico
- **WHEN** la humedad es 35 %
- **THEN** R-02 emite `NOOP_INFO` y R-01 decide (si está en ventana)

#### Scenario: Tope de 12 h
- **WHEN** la humedad es 30 % y R-02 regó el sector hace 11 h 59 min
- **THEN** R-02 emite `ABORT_RIEGO` citando el tope, y no se riega

#### Scenario: Tope vencido
- **WHEN** la humedad es 30 % y R-02 regó el sector hace 12 h
- **THEN** R-02 riega

#### Scenario: Una alerta por macro-zona
- **WHEN** los 100 sectores de MZ-2 disparan R-02 en el mismo ciclo de lectura
- **THEN** el historial tiene un solo evento "Alerta" `CRITICAL` de MZ-2 para ese ciclo

### Requirement: R-03 Posponer por lluvia
Cuando aplica R-01 (`crítico ≤ h < umbral`) y el pronóstico da, en las próximas
`riego.lluvia-ventana` horas, probabilidad máxima horaria ≥ `riego.lluvia-probabilidad` **y** lluvia
acumulada ≥ `riego.lluvia-mm`, el motor SHALL emitir `POSTPONE_RIEGO` para el sector y una alerta
`INFO` "Riego pospuesto por pronóstico de lluvia" por macro-zona. No SHALL hacer nada más.

#### Scenario: Ambos umbrales en el borde
- **WHEN** la humedad es 40 % y en las 4 h siguientes la probabilidad máxima es 70 % y la suma 5 mm
- **THEN** R-03 emite `POSTPONE_RIEGO` y R-01 no se evalúa

#### Scenario: Probabilidad alta, pocos milímetros
- **WHEN** la probabilidad máxima es 95 % y la suma 4,9 mm
- **THEN** R-03 emite `NOOP_INFO` y R-01 riega

#### Scenario: Muchos milímetros, probabilidad baja
- **WHEN** la probabilidad máxima es 69 % y la suma 20 mm
- **THEN** R-03 emite `NOOP_INFO`

#### Scenario: Lluvia fuera de la ventana
- **WHEN** la lluvia prevista cae entre la hora +5 y +6
- **THEN** no cuenta para R-03 con ventana de 4 h

#### Scenario: Sin pronóstico
- **WHEN** el pronóstico no está disponible
- **THEN** las comparaciones de R-03 quedan `SIN_DATO`, no pospone y R-01 decide

#### Scenario: Sin déficit no hay nada que posponer
- **WHEN** la humedad es 60 % y el pronóstico da 90 % y 15 mm
- **THEN** R-03 emite `NOOP_INFO` "no aplica" y no se registra ninguna alerta

### Requirement: R-04 Sustrato saturado
R-04 SHALL evaluarse ANTES de la guarda de ciclo (prioridad 2 contra 3): un sector recién regado ya tiene riego
"en el ciclo" y si la guarda cortara primero la saturación quedaría sin evaluar. Con `h ≥ riego.saturacion-bloqueo`, el motor SHALL bloquear todo riego autónomo del sector
(`ABORT_RIEGO`). Con `h ≥ riego.saturacion-alerta`, además SHALL emitir una alerta `WARNING`
"Sustrato saturado, riesgo de asfixia radicular y hongos" por macro-zona y ciclo.

#### Scenario: Bajo el bloqueo
- **WHEN** la humedad es 74 %
- **THEN** R-04 emite `NOOP_INFO`

#### Scenario: En el bloqueo, sin alerta
- **WHEN** la humedad es 75 %
- **THEN** R-04 emite `ABORT_RIEGO` y no hay evento "Alerta"

#### Scenario: En la alerta
- **WHEN** la humedad es 80 %
- **THEN** R-04 emite `ABORT_RIEGO` y una alerta `WARNING` de la macro-zona

#### Scenario: Recién regado
- **WHEN** el sector regó en este ciclo y la lectura siguiente es 82 %
- **THEN** R-04 emite `ABORT_RIEGO` y la alerta `WARNING`, y la guarda de ciclo queda omitida

#### Scenario: Cancela lo pendiente
- **WHEN** MZ-2 tiene sectores en cola y llega una lectura con 76 %
- **THEN** esos sectores salen de la cola y los que ya están regando siguen hasta su duración

### Requirement: R-05 Riego fuera de ventana
Fuera de `riego.ventana-normal`, cuando aplica R-01, el motor SHALL emitir `ABORT_RIEGO`. La ventana
SHALL incluir el minuto de su hora de fin (la lectura de las 18:00 entra) y su hora de inicio.

#### Scenario: Bordes de la ventana
- **WHEN** la humedad es 40 % y la hora es 05:59:59 / 06:00:00 / 17:59:59 / 18:00:59 / 18:01:00
- **THEN** R-05 corta / no corta / no corta / no corta / corta, respectivamente

#### Scenario: Ventana modificada
- **WHEN** `riego.ventana-normal` vale 07:00-17:00 y la hora es 06:30 con humedad 40 %
- **THEN** R-05 emite `ABORT_RIEGO` y la traza muestra "06:30 ∈ 07:00–17:00 ✗"

### Requirement: R-06 Pausa después de una aplicación
Cuando aplica R-01 y al sector se le aplicó fertilizante o fitosanitario hace menos de
`riego.pausa-tras-aplicacion` horas, el motor SHALL emitir `ABORT_RIEGO` para ese sector. Los demás
sectores de la macro-zona SHALL seguir su curso.

#### Scenario: Dentro de la pausa
- **WHEN** la humedad es 40 % a las 11:00 y al sector se le aplicó insumo a las 05:00:01
- **THEN** R-06 corta la rama del sector

#### Scenario: Pausa cumplida
- **WHEN** la aplicación fue a las 05:00:00
- **THEN** R-06 emite `NOOP_INFO` y R-01 riega

#### Scenario: Sólo el sector aplicado
- **WHEN** a MZ-1-003 se le aplicó insumo hace 2 h y la humedad de MZ-1 es 40 %
- **THEN** MZ-1-003 no se riega y los otros 99 sectores sí

### Requirement: Ciclo de lectura
Un sector SHALL recibir como máximo un riego autónomo **de R-01** por ciclo de lectura, y ningún riego
(de R-01 ni de R-02) mientras tiene uno en curso. R-02 SHALL respetar únicamente su tope de
`riego.exceptuado-bloqueo` horas. El ciclo SHALL ser la franja de `intervaloSensadoMinutos` (acotado a 60–360)
anclada a las 02:00 locales que contiene la hora de evaluación.

#### Scenario: Segunda lectura del mismo ciclo
- **WHEN** MZ-1-001 se regó a las 10:12 y llega una lectura a las 13:59 con 40 %
- **THEN** la regla de ciclo emite `ABORT_RIEGO` y no se encola nada

#### Scenario: Ciclo siguiente
- **WHEN** la misma lectura llega a las 14:00:10
- **THEN** el sector vuelve a ser elegible

#### Scenario: R-02 no depende del ciclo
- **WHEN** R-01 regó MZ-1-001 a las 10:12 y a las 13:30 la humedad es 30 %
- **THEN** R-02 emite `ACTIVAR_VALVULA` (el sector no tiene riego crítico en las últimas 12 h)

#### Scenario: Riego en curso frena a todos
- **WHEN** MZ-1-001 tiene un riego abierto hasta las 10:10 y a las 10:05 la humedad es 20 %
- **THEN** nadie riega el sector

#### Scenario: Tope de R-02 con riego previo en el ciclo
- **WHEN** la humedad es 30 %, R-01 regó a las 10:12 y R-02 regó hace 3 h 18 min
- **THEN** R-02 emite `ABORT_RIEGO` citando el tope

#### Scenario: Intervalo fuera de rango
- **WHEN** `intervaloSensadoMinutos` guardado vale 5
- **THEN** el ciclo usa 60 min y se loguea una advertencia

### Requirement: Tandas por macro-zona
Las órdenes de riego SHALL encolarse por macro-zona y despacharse en orden de numeración de sector,
con a lo sumo `riego.sectores-simultaneos` válvulas abiertas por macro-zona. Al despachar, el
backend SHALL publicar el comando `valve ON` con `durationSec`, y registrar el evento "Riego" con
sector, volumen, duración, humedad y regla. Una solicitud cuyo sector o macro-zona tiene un bloqueo
manual activo al momento de despachar SHALL descartarse.

#### Scenario: Primera tanda
- **WHEN** una lectura de MZ-2 con 40 % hace que los 100 sectores pidan riego
- **THEN** en el siguiente despacho se publican 10 comandos, para MZ-2-001 … MZ-2-010, y los 90
  restantes quedan "En cola"

#### Scenario: Se libera un lugar
- **WHEN** vence la duración (más 5 s) de MZ-2-003
- **THEN** el siguiente despacho abre MZ-2-011 y nunca hay más de 10 abiertas en MZ-2

#### Scenario: Zonas independientes
- **WHEN** MZ-1 y MZ-2 tienen 10 sectores regando cada una
- **THEN** ninguna espera por la otra

#### Scenario: Bloqueo manual antes de despachar
- **WHEN** MZ-2-050 está en cola y un operario bloquea MZ-2
- **THEN** no se publica comando para MZ-2-050 ni para ningún otro pendiente de MZ-2

#### Scenario: Broker caído
- **WHEN** la publicación del comando falla
- **THEN** no se registra el riego y la solicitud sigue en cola

### Requirement: La ronda se completa
Una solicitud en cola SHALL pertenecer a la ronda decidida y completarse: que R-01 o R-02 dejen de
pedir riego porque la humedad se recuperó NO SHALL retirarla (el nodo testigo mide un solo sector y su
humedad sube cuando el despacho lo riega). Sólo SHALL retirarla una cancelación explícita de seguridad:
sustrato saturado (R-04), bloqueo manual y, sólo para las solicitudes de R-01, ventana horaria cerrada (R-05) o
pausa por aplicación de ESE sector (R-06). El sensor sin datos o con la humedad congelada (S-02) NO SHALL retirarla:
SHALL pausarla (ver "Revalidación al despachar"). Una nueva
decisión de riego para un sector ya encolado SHALL actualizar su solicitud sin duplicarla y SHALL NOT
degradar una de R-02 a R-01.

#### Scenario: La humedad se recupera durante la ronda
- **WHEN** MZ-2 con 100 solicitudes abre la primera tanda y la lectura siguiente sube de 30 % a 65 %
- **THEN** los 90 sectores restantes siguen en la cola y se riegan en las tandas siguientes

#### Scenario: Saturación a mitad de ronda
- **WHEN** la lectura siguiente a la primera tanda es 76 %
- **THEN** los 90 pendientes se cancelan y los 10 abiertos siguen hasta su duración

#### Scenario: Ventana cerrada sólo cancela a R-01
- **WHEN** son las 18:01 con humedad 40 % y MZ-2-007 (R-01) y MZ-2-008 (R-02) están en cola
- **THEN** MZ-2-007 sale de la cola y MZ-2-008 sigue

### Requirement: Revalidación al despachar
Antes de abrir CADA válvula el despacho SHALL revalidar con datos actuales y con los MISMOS parámetros del
catálogo y las mismas condiciones que las reglas:
- **Pausa** (no abre ninguna válvula de la zona y CONSERVA la cola): lectura de la zona o humedad de sustrato no
  vigentes (misma definición y mismo `seguridad.antiguedad-max-lectura` que `StaleSensorRule`) o sin humedad. Se
  retoma sola cuando hay lectura vigente. No deja alerta ni retira solicitudes.
- **Retira** (se descarta de la cola, se loguea y deja una alerta `WARNING` por zona y motivo): bloqueo manual;
  humedad actual `>= riego.saturacion-bloqueo` (R-04, vale para R-01 y R-02); y, sólo para las de R-01, hora fuera
  de `riego.ventana-normal` (cerrada al minuto, R-05), última aplicación de insumo hace menos de
  `riego.pausa-tras-aplicacion` (R-06), lluvia prevista que alcanza `riego.lluvia-probabilidad` Y `riego.lluvia-mm`
  en `riego.lluvia-ventana` h con el pronóstico cacheado y sin esperar (R-03; sin dato de pronóstico no pospone) y
  "ya regó en este ciclo de lectura". Para las de R-02: un riego crítico hace menos de `riego.exceptuado-bloqueo` h
  (tope). R-02 SHALL NOT depender de la ventana, la pausa de aplicación, la lluvia ni la guarda de ciclo.
- Si no se puede leer la zona no SHALL abrirse ninguna válvula y la solicitud SHALL seguir en cola. Si no se puede
  leer el historial, las de R-01 SHALL esperar y las de R-02 SHALL seguir con el último riego que el despacho recuerda.
El último riego, el último riego crítico y la última aplicación SHALL ser el más reciente entre el historial y la
memoria del despacho (el ciclo y el tope no deben depender de que el registro haya sido exitoso).

#### Scenario: El nodo muere con la ronda a medias
- **WHEN** hay 90 sectores en cola, la última lectura tiene 91 s y hay cupo libre
- **THEN** no se publica ningún comando y los 90 siguen en la cola, sin alerta

#### Scenario: Hueco de lectura de 3 minutos
- **WHEN** hay 90 sectores en cola, la humedad ya subió a 65 %, el nodo calla 3 minutos y vuelve
- **THEN** durante el hueco no se abre ninguna válvula y al volver el nodo se riegan los 90

#### Scenario: Solicitud de R-01 de las 17:58
- **WHEN** a las 18:10 se despacha una solicitud de R-01 encolada a las 17:58
- **THEN** se descarta; una de R-02 en la misma situación se abre

#### Scenario: Borde de la ventana
- **WHEN** se despacha una solicitud de R-01 a las 18:00:59 / 18:01:00
- **THEN** se abre / se descarta

#### Scenario: Dosificación mientras espera
- **WHEN** MZ-2-015 espera su tanda con R-01 y recibe una aplicación de insumo hace menos de 6 h
- **THEN** su solicitud se descarta y no se riega (una de R-02 del mismo sector se abriría)

#### Scenario: Pronóstico que llega tarde
- **WHEN** R-01 encoló la ronda sin pronóstico (primer mensaje tras arrancar) y luego llega lluvia que supera los dos umbrales
- **THEN** el despacho descarta las solicitudes de R-01 sin abrir válvulas; las de R-02 no se tocan

#### Scenario: Duración corta
- **WHEN** un sector regó 3 s y 10 s después se vuelve a encolar en el mismo ciclo
- **THEN** no se abre otra vez (R-01 por la guarda de ciclo, R-02 por su tope de horas)

### Requirement: Vencimiento de las solicitudes
Una solicitud de riego SHALL ser válida durante el ciclo de lectura en que se pidió y durante el siguiente, y SHALL
vencer al empezar el tercero (es decir, vence si `solicitadaEn` es anterior al inicio del ciclo anterior al de ahora,
con el intervalo de `CicloLectura`). El despacho SHALL descartar lo vencido aunque la zona esté en pausa y dejar una
alerta `WARNING` por zona.

#### Scenario: Ciclo siguiente
- **WHEN** una solicitud de las 10:05 (ciclo 10:00-14:00) se mira a las 14:05
- **THEN** sigue en la cola

#### Scenario: Tercer ciclo
- **WHEN** la misma solicitud se mira a las 18:00 y el nodo no volvió
- **THEN** se descarta con una alerta `WARNING` y la cola de la zona queda vacía sin haber regado

### Requirement: Último riego en memoria
El ciclo de lectura y el tope de R-02 SHALL ver el último riego despachado aunque su registro en el historial
haya fallado, mientras el proceso viva.

#### Scenario: Falla el historial
- **WHEN** el despacho abre MZ-2-001 y el registro del riego falla, y 606 s después llega una lectura del mismo ciclo con 40 %
- **THEN** MZ-2-001 no vuelve a la cola

### Requirement: Cierre del ciclo de la válvula
El estado "regando" de un sector SHALL derivarse del último evento "Riego" (`ts + duracionSeg + 5 s`),
sin depender de un ACK ni de un campo que haya que volver a cerrar. El sector SHALL poder volver a
regar en el ciclo siguiente.

#### Scenario: Vuelve a regar
- **WHEN** un sector regó a las 10:12 por 480 s y a las 14:05 la humedad es 42 %
- **THEN** se despacha un nuevo riego (hoy nunca ocurre por el enganche "Regando")

#### Scenario: Estado visible
- **WHEN** se pide el snapshot del vivero a las 10:15 para ese sector
- **THEN** la válvula figura "Regando"; a las 10:21 figura "Cerrada"; un sector pendiente figura "En cola"

#### Scenario: Reinicio del backend a mitad de riego
- **WHEN** el backend reinicia a las 10:14 con 10 sectores regando
- **THEN** el primer despacho cuenta esos 10 como en curso y no abre otros hasta que venzan

### Requirement: Frescura de la humedad de sustrato
El riego autónomo SHALL bloquearse si la última humedad de sustrato recibida es más vieja que
`seguridad.antiguedad-max-lectura`, aunque otras métricas de la zona sean frescas.

#### Scenario: Sonda caída
- **WHEN** el nodo publica temperatura y luz cada 30 s pero no `humSus` desde hace 5 min
- **THEN** `StaleSensorRule` emite `ABORT_RIEGO` con la comparación "Antigüedad de la humedad de
  sustrato 300 s > 90 s"

### Requirement: Zona horaria fija
Todas las ventanas y ciclos de riego SHALL evaluarse en `America/Argentina/Buenos_Aires`,
independientemente de la zona del JVM, con un reloj inyectable.

#### Scenario: JVM en UTC
- **WHEN** el JVM corre en UTC y son las 20:30 UTC (17:30 locales) con humedad 40 %
- **THEN** R-05 no corta y R-01 riega

### Requirement: Parámetros de riego en el catálogo
Los parámetros de riego de `reglas_v2` §11 SHALL existir una sola vez en el catálogo, y cada regla
SHALL declarar todos los que lee. `riego.max-riegos-24h`, `riego.max-riegos-24h-sector` y
`riego.tiempo-max-apertura` SHALL dejar de existir.

#### Scenario: Umbral compartido
- **WHEN** se pide `GET /api/rules/parametros`
- **THEN** `riego.umbral-humedad` aparece una vez con `usadoPor` = `[FueraDeVentanaRiegoRule,
  PausaTrasAplicacionRule, PosponerPorLluviaRule, RiegoPorDeficitRule]`

#### Scenario: Fábrica nueva sin override
- **WHEN** la base no tiene override de `riego.umbral-humedad`
- **THEN** su valor vigente es 45

#### Scenario: Override existente
- **WHEN** la base tiene override 42 de `riego.umbral-humedad`
- **THEN** su valor vigente sigue siendo 42

#### Scenario: Restricción cruzada
- **WHEN** se intenta guardar `riego.umbral-critico` = 40 con `riego.umbral-humedad` = 40
- **THEN** el guardado se rechaza con "El umbral crítico debe ser menor que el umbral de riego."
