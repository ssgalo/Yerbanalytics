## ADDED Requirements

### Requirement: Interruptor operativo por regla
Toda pieza que decide una acción (`DemoControllable`: reglas por sector, reglas de macro-zona y los servicios que
deciden por su cuenta) SHALL poder prenderse o apagarse en producción, con `yerbanalytics.demo.enabled=false`. El estado
SHALL persistirse en `regla_estado` y SHALL sobrevivir un reinicio del backend. Una pieza sin fila SHALL tomar su estado
por defecto: apagada si figura en `yerbanalytics.reglas.retiradas`, prendida si no.

#### Scenario: Apagar una regla en modo REAL
- **WHEN** un usuario con `reglas.editar` apaga `RiegoPorDeficitRule` con el backend en modo `REAL`
- **THEN** en el pulso siguiente la regla figura en la traza como `OMITIDA_DESHABILITADA` y no se encola ningún riego por R-01

#### Scenario: El estado sobrevive un reinicio
- **WHEN** se apaga una regla y se reinicia el backend
- **THEN** la regla sigue apagada y conserva quién, cuándo y por qué

#### Scenario: Regla retirada sin decisión de nadie
- **WHEN** `SupplyRule` figura en `yerbanalytics.reglas.retiradas` y no tiene fila en `regla_estado`
- **THEN** el catálogo la informa con `activa: false` y `activaPorDefecto: false`

#### Scenario: Regla retirada prendida a mano
- **WHEN** alguien prende `SupplyRule` y después se la quita de `yerbanalytics.reglas.retiradas`
- **THEN** sigue prendida, porque tiene fila

### Requirement: Estado efectivo único
El estado efectivo de una pieza SHALL ser su estado operativo: Motor de reglas es la única fuente de verdad del encendido y la capa de demo (sólo con `yerbanalytics.demo.enabled=true`) SHALL guardar únicamente cooldowns y bypass. Todo lo que consulta si una pieza corre
(orquestador por sector, motor de macro-zona, despacho de riego, servicios, `RuleNodeDto.activa` de
`GET /api/rules/schema`) SHALL usar ese mismo estado. `PUT /api/demo/rules/{name}` SHALL rechazar con `400` todo cuerpo que traiga `enabled`.

#### Scenario: La demo no puede prender ni apagar
- **WHEN** el simulador pide `PUT /api/demo/rules/{name}` con `enabled` sobre una regla, prendida o apagada en el vivero
- **THEN** responde `400` ("El encendido se cambia en Motor de reglas.") y el estado de la regla no cambia

#### Scenario: Cooldowns y bypass no cambian el estado
- **WHEN** el simulador cambia cooldowns o bypass de una regla apagada en el vivero, o limpia la capa de demo
- **THEN** la regla corre si y sólo si está prendida en el vivero, y `GET /api/demo/rules` informa su `enabled` (sólo lectura) igual al operativo

#### Scenario: Sin modo demo
- **WHEN** `yerbanalytics.demo.enabled=false`
- **THEN** el estado efectivo de cada pieza es su estado operativo

### Requirement: Apagar una regla apaga su revalidación
Una guarda que el despacho de riego revalida en nombre de una regla (bloqueo manual, lluvia, sustrato saturado, ventana,
pausa tras aplicación, ciclo de lectura, tope de R-02) SHALL NOT aplicarse cuando esa regla está apagada en su estado
efectivo.

#### Scenario: R-03 apagada
- **WHEN** `PosponerPorLluviaRule` está apagada y hay lluvia prevista sobre los umbrales
- **THEN** el despacho no descarta el riego encolado por lluvia

#### Scenario: R-04 prendida
- **WHEN** `SustratoSaturadoRule` está prendida y la humedad actual supera `riego.saturacion-bloqueo`
- **THEN** el despacho retira el riego encolado, como hoy

### Requirement: Protecciones con motivo y confirmación
Una pieza SHALL poder declararse protección junto con el texto de la consecuencia de apagarla. Apagar una protección SHALL
requerir un `motivo` no vacío (hasta 500 caracteres) y `confirmaProteccion: true`; el backend SHALL rechazar el pedido con
`400` si falta alguno, aunque la interfaz ya lo valide. Prender una pieza SHALL NOT requerir motivo ni confirmación.

#### Scenario: Apagar una protección sin motivo
- **WHEN** se pide `PUT /api/rules/ManualLockRule/estado` con `{ activa: false, confirmaProteccion: true }` y sin motivo
- **THEN** responde `400` con `error: "MOTIVO_REQUERIDO"` y la regla sigue prendida

#### Scenario: Apagar una protección sin confirmación
- **WHEN** se pide apagar `SustratoSaturadoRule` con motivo pero sin `confirmaProteccion`
- **THEN** responde `400` con `error: "CONFIRMACION_REQUERIDA"` y el texto de la consecuencia

#### Scenario: Apagar una regla que no es protección
- **WHEN** se pide apagar `ShadingRule` sin motivo
- **THEN** responde `200` y la regla queda apagada

#### Scenario: Declaración coherente
- **WHEN** se corre `ReglasArquitecturaTest`
- **THEN** toda pieza con efecto `PROTECTS` declara su consecuencia y ninguna otra la declara

### Requirement: Cambio de estado por API con permiso
El estado operativo SHALL cambiarse sólo por `PUT /api/rules/{name}/estado`, con el permiso `reglas.editar`, declarado en
`MapaPermisos`. `GET /api/rules/parametros` SHALL listar todas las piezas conmutables con su código, objetivo, efecto,
estado operativo, estado por defecto, consecuencia (si es protección) y último cambio.

#### Scenario: Usuario sin permiso
- **WHEN** un Operario pide `PUT /api/rules/RiegoPorDeficitRule/estado`
- **THEN** responde `403`

#### Scenario: Regla desconocida
- **WHEN** se pide `PUT /api/rules/NoExiste/estado`
- **THEN** responde `404`

#### Scenario: Mismo estado
- **WHEN** se pide prender una regla que ya está prendida
- **THEN** responde `200` y no se escribe fila ni evento de historial

#### Scenario: El simulador no escribe el estado operativo
- **WHEN** la cuenta de rol Servicio pide `PUT /api/rules/{name}/estado`
- **THEN** responde `403`

### Requirement: Registro de cada cambio
Cada cambio efectivo de estado operativo SHALL guardar quién, cuándo y el motivo en `regla_estado`, y SHALL dejar un evento
en el historial (tipo `Configuración`, con la regla) que diga la regla, su código, el nuevo estado, el usuario, el motivo y,
para una protección apagada, la consecuencia.

#### Scenario: Apagado registrado
- **WHEN** el agrónomo apaga R-04 con motivo "sonda en recalibración"
- **THEN** el historial muestra el evento con la regla, el usuario, el motivo y la consecuencia

### Requirement: Parámetros de seguimiento en el catálogo
La latencia y el delta mínimo del seguimiento post-acción SHALL ser parámetros del catálogo del motor
(`seguimiento.latencia-min`, `seguimiento.delta-min`), declarados por quien los lee, y SHALL editarse en Motor de reglas.
`HistorialService` SHALL leerlos del catálogo.

#### Scenario: Editar la latencia
- **WHEN** el agrónomo cambia `seguimiento.latencia-min` a 10 y guarda
- **THEN** el seguimiento de las acciones registradas desde entonces espera 10 minutos

### Requirement: Etiquetas sin emojis y con código aparte
La etiqueta de toda pieza conmutable SHALL ser un nombre legible sin emojis y sin el código entre paréntesis; el código de
`reglas_v2` SHALL viajar aparte (`codigo`).

#### Scenario: Etiqueta de R-01
- **WHEN** se pide `GET /api/rules/parametros`
- **THEN** `RiegoPorDeficitRule` viene con `label: "Riego por déficit hídrico"` y `codigo: "R-01"`
