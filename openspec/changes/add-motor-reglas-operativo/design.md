# Design: add-motor-reglas-operativo

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`; `fe/` = `Desarrollo/frontend/src/`;
`sim/` = `Desarrollo/simulador/`. Código nuevo en inglés; textos, comentarios y docs en español (CLAUDE.md §7).

## Estado verificado (develop, 969e61b)

| Pieza | Hoy |
|---|---|
| Overrides | `be/engine/overrides/RuleOverridesStore` (`@Component`, en memoria) **implementa** `RuleOverrides` y es el único bean de ese tipo. Siembra `yerbanalytics.reglas.retiradas` (`SupplyRule,DailyDoseLimitRule`, `application.properties:140`) como apagadas; `clear()` las vuelve a sembrar |
| Escritura | Sólo `DemoRuleControlService` → `DemoRulesController` (`/api/demo/rules/**`, `@ConditionalOnProperty yerbanalytics.demo.enabled`) |
| Quién lee `isEnabled` | `RuleOrchestrator:108` (`OMITIDA_DESHABILITADA`), `ZoneRuleEngine:105`, `DespachoRiego:468,604,617,641`, `HorarioMediasombraService:146`, `HardwareFaultService:132`, `ReadingValidationGuard:134`, `EscalamientoNodoService:133,170`, `ApplicationService:220` (por `ApplicationPreconditions.NAME`), `RuleEngineSchemaController:107,125` (`RuleNodeDto.activa`) |
| Registro | `DemoControllableRegistry` (`@Component` sin condición) junta todos los `DemoControllable` y valida nombres únicos |
| Catálogo | `GET/PUT /api/rules/parametros` (`ReglasParametrosController`) → `CatalogoReglasDto(reglas, parametros)`; `reglas` lista sólo `Rule` y `ZoneRule` (`CatalogoParametrosService.catalogo()`), no los servicios |
| Etiquetas | Las 23 `label()` de `DemoControllable` del backend empiezan con un emoji y varias terminan con el código (`"💦 Riego por déficit hídrico (R-01)"`). Lo replica `fe/data/mock/catalogoReglas.fixture.json` y lo afirman tests (`RuleEngineSchemaControllerTest`, `ReglaNode.test`, `NodoPanel.test`, `ReglasPage.test`, `ReglaCard.test`, `ParametrosTab.test`, `historyGraphModel.test`) |
| Parche | `fe/components/DAGViewer/historyGraphModel.ts:53` `plainLabel` borra el emoji; `:56` `isActive` excluye `activa === false` del grafo de **cualquier** evento, también de los pasados |
| Símbolos sueltos | `fe/components/DAGViewer/ReglaNode.tsx:78` (🔒), `fe/features/reglas/inspector/NodoPanel.tsx:96` (`'⚠ ALERTA'`), `fe/features/reglas/inspector/lecturaTraza.ts:33` (`✓ ✗ ?`), `fe/features/historial/components/ReasoningModal.module.css:9` (`rgba(21, 35, 27, 0.42)`) |
| Permisos | `be/seguridad/Permiso.java:32` `reglas.editar` = "Editar el catálogo de parámetros"; `MapaPermisos:105-106` (`GET /api/rules/**` → `reglas.ver`, `PUT /api/rules/parametros` → `reglas.editar`). Lo tienen ADMINISTRADOR e INGENIERO_AGRONOMO; SERVICIO tiene `reglas.ver`, no `reglas.editar` (`Permiso.java:115`) |
| Umbrales | `ConfiguracionOperativaEntity`: `insumo_dosis_max_24h_ml` (sólo se lee/escribe en `ConfiguracionService`; nadie decide con él), `seguimiento_latencia_min` / `seguimiento_delta_min` (los lee `HistorialService.withSeguimiento` vía `ConfiguracionService.getLatencyMs/getUmbralRecuperacion`), `mediasombra_abierta_desde/hasta` (los lee `HorarioMediasombraService`) |
| Vista Rustificación | **No existe** en develop: `add-rustificacion-por-macrozona` (tarea 1.4) la quitó y el plan vive en el detalle de la macro-zona (`fe/features/map/components/HardeningCard.tsx`). CLAUDE.md §4 todavía la nombra en el grupo Configuración (desactualizado) |
| Despacho de riego | Revalida con overrides R-05, R-06, guarda de ciclo y tope de R-02, pero **no** S-01 (`DespachoRiego:478,512`), R-03 (`:590`) ni R-04 (`:713`): esas se aplican aunque la regla esté apagada |
| Simulador | `sim/src/happyPath.ts:350-355` **prende `SupplyRule`** por `/api/demo/rules` porque arranca retirada |

## Decisiones

### D-1 — Estado operativo persistido en `regla_estado`

Entidad `RuleStateEntity` (tabla `regla_estado`): `nombre` (PK, el `name()` del `DemoControllable`), `activa`
(boolean), `motivo` (TEXT, nullable), `updated_by`, `updated_ts`. Repositorio `RuleStateRepository` y servicio
`OperationalRuleStates`:

- **Sin fila, rige la siembra**: `activa = !retiradas.contains(nombre)`. `yerbanalytics.reglas.retiradas` deja de ser
  una lista de overrides de demo y pasa a ser **el valor por defecto del estado operativo** (`activaPorDefecto`). No se
  insertan filas al arrancar: la fila aparece recién cuando alguien cambia el interruptor, así "de fábrica" y
  "decidido por una persona" se distinguen.
- **Cache en memoria** cargada al arrancar y reemplazada después del commit de cada cambio
  (`TransactionSynchronization.afterCommit`, el mismo patrón de `CatalogoParametrosService.invalidarAlTerminar`). El
  motor consulta `isEnabled` por regla y por sector en cada pulso: no puede ir a la base cada vez.
- **Filas huérfanas** (nombre que ya no está en el registro, p. ej. `FollowUpRule` cuando entre #67): se ignoran y se
  loguea un `WARN` al arrancar. No se borran solas: si la regla vuelve, vuelve con lo que se decidió.
- `ddl-auto=update` crea la tabla; no hace falta migración para ella.

### D-2 — Un solo estado efectivo: el override explícito de demo gana; si no, el operativo

`EffectiveRuleOverrides implements RuleOverrides` (en `engine/overrides/`) pasa a ser **el único bean de
`RuleOverrides`**:

```
isEnabled(n)          = demo.explicitEnabled(n).orElse(operational.isActive(n))
cooldownMinutes(n, k) = demo (sin cambios)
isBypassed(n, k)      = demo (sin cambios)
```

- `RuleOverridesStore` **deja de implementar `RuleOverrides`** y de sembrar las retiradas; queda como la capa de demo
  (en memoria, la escribe sólo `DemoRuleControlService`). `clear()` borra los overrides explícitos: vuelve a regir el estado operativo. La capa distingue "nunca tocado" de "prendido a mano" (`explicitEnabled` es un `Optional`).
- Todos los consumidores siguen inyectando `RuleOverrides`: no cambia ninguno (`RuleOrchestrator`, `ZoneRuleEngine`,
  `DespachoRiego`, los servicios). `RuleNodeDto.activa` de `GET /api/rules/schema` sale de ese mismo bean, así que
  ya es el efectivo sin tocar `RuleEngineSchemaController`.
- **En modo demo, el override explícito del simulador gana, para prender o apagar.** Sólo existe con
  `yerbanalytics.demo.enabled=true`: en producción la capa está vacía y el efectivo es el operativo. Se eligió así para no
  romper el happy path de Demo Expo, que prende `SupplyRule` (retirada) para el tramo de insumo; el simulador es un
  entorno de prueba, no el vivero.

### D-3 — Lo que cada pieza declara (en `DemoControllable`)

Se suman cuatro métodos a `DemoControllable` (y por herencia a `Rule` y `ZoneRule`):

| Método | Tipo | Default | Para qué |
|---|---|---|---|
| `code()` | `String` | `""` | Código de `reglas_v2` (`"R-01"`, `"S-05"`); sale del `label()` |
| `objective()` | `RuleObjective` | derivado de `branch()`: GLOBAL→`PROTECTION`, RIEGO→`IRRIGATION`, INSUMO→`NUTRITION`, MEDIASOMBRA→`SHADE`, SEGUIMIENTO→`FOLLOW_UP` | Grupo en la pantalla. `SANITY` lo declaran las F-* |
| `effect()` | `RuleEffect` (`ACTS`, `WARNS`, `PROTECTS`) | `ACTS` | Chip Actúa / Avisa / Protege |
| `protection()` | `Optional<String>` | `Optional.empty()` | Si es protección, **el texto de la consecuencia de apagarla**, en lenguaje del agrónomo |

- `ReglasArquitecturaTest` suma: `effect() == PROTECTS` ⇔ `protection().isPresent()`; toda `label()` sin emoji
  (`\p{Extended_Pictographic}`) y sin el código entre paréntesis; `code()` vacío o con formato `^[A-Z]-[A-Z0-9]{1,3}$`
  (o lista separada por coma, para `ShadeFaultRule`).
- **No se renombra `DemoControllable`** aunque el nombre ya no cuente toda la historia: hay cuatro PRs abiertos que lo
  implementan. Se actualiza su javadoc ("pieza que decide; la conmuta el vivero y, en demo, el simulador") y el renombre
  queda para después de que entren.
- La agrupación vive en el backend (y no en un mapa del frontend) para que una regla nueva traiga su grupo, igual que el
  grafo ya se arma solo desde `/api/rules/schema`.

### D-4 — Qué significa "apagar" cada pieza

Verificado en el código. La columna "Al apagarla" es la base del texto de `protection()` y del Callout de la tarjeta.

| Pieza (`name()`) | Código | Objetivo | Efecto | Al apagarla |
|---|---|---|---|---|
| `ManualLockRule` | S-01 | Protecciones | Protege | Un sector o macro-zona bloqueado a mano vuelve a regarse y dosificarse solo (ver D-10) |
| `EscalamientoNodo` | S-02 | Protecciones | Protege | Un nodo que no responde nunca pasa a fuera de servicio y su macro-zona no va a estado seguro |
| `StaleSensorRule` | S-02 | Protecciones | Protege | El motor decide con lecturas viejas o con la humedad congelada |
| `ReadingValidationGuard` | S-03 | Protecciones | Protege | Se aceptan lecturas fuera del rango físico y el motor decide con ellas |
| `IncompleteHardwareRule` | S-04 | Protecciones | Protege | Se ordena a actuadores que no están registrados |
| `HydraulicFaultRule` | S-05 | Protecciones | Protege | Un sector con falla hidráulica marcada se riega y dosifica solo |
| `HardwareFaultService` | S-05 | Protecciones | Protege | Los errores que publica el nodo se ignoran: ninguna falla se marca |
| `SustratoSaturadoRule` | R-04 | Riego | Protege | Se riega con el sustrato saturado |
| `CicloLecturaRiegoRule` | — | Riego | Protege | Un sector puede regarse más de una vez por ciclo de lectura |
| `ShadeFaultRule` | S-01, S-04, S-05 | Mediasombra | Protege | Se mueve la malla de una zona bloqueada, sin malla registrada o con la malla averiada |
| `RiegoPorDeficitRule` | R-01 | Riego | Actúa | No se riega por déficit (R-02 sigue regando el déficit crítico) |
| `DeficitCriticoRule` | R-02 | Riego | Actúa | No se riega por déficit crítico |
| `PosponerPorLluviaRule` | R-03 | Riego | Actúa | No se pospone el riego por lluvia prevista |
| `FueraDeVentanaRiegoRule` | R-05 | Riego | Actúa | R-01 riega a cualquier hora |
| `PausaTrasAplicacionRule` | R-06 | Riego | Actúa | Se riega aunque se haya aplicado un insumo recién |
| `NutritionPlanRule` | N-01 | Nutrición | Actúa | No se aplica el plan de nutrición |
| `LowEcRule` | N-02 | Nutrición | Actúa | No se corrige la CE baja |
| `ApplicationPreconditions` | P-D1…P-D5 | Nutrición | Actúa | **Ninguna aplicación de insumo se ejecuta** (`ApplicationService:220` rechaza todo). No es "aplicar sin precondiciones": es el corte general de aplicaciones |
| `SupplyRule` | — | Nutrición | Actúa | (retirada) |
| `DailyDoseLimitRule` | — | Nutrición | Protege | (retirada) Sin tope diario de dosis de la v1 |
| `ShadingRule` | — | Mediasombra | Actúa | No se cierra la malla por pico de UV |
| `HorarioMediasombraService` | — | Mediasombra | Actúa | La malla no sigue el plan ni el horario por defecto |
| `FollowUpRule` | — | Seguimiento | Avisa | No se evalúa la efectividad en cada pulso (el `@Scheduled` de `HistorialService` sigue) |

- `ApplicationService:221` dice hoy "las aplicaciones están apagadas desde el control de demo": pasa a "las
  aplicaciones están apagadas en el motor de reglas", porque ahora lo puede apagar el vivero.
- La lista de protecciones dada se respeta; `StaleSensorRule` y `DailyDoseLimitRule` como protecciones y `ApplicationPreconditions` como *Actúa* son propuestas de este diseño (pregunta abierta 2).

### D-5 — API: estado en el catálogo, cambio por un endpoint propio

**Lectura**: `GET /api/rules/parametros` (el que ya carga la pantalla) amplía `ReglaCatalogoDto` y **lista todos los
`DemoControllable`** (del `DemoControllableRegistry`), no sólo `Rule` y `ZoneRule`:

```
ReglaCatalogoDto(
  id, label, codigo, rama, objetivo, efecto, prioridad, parametros,
  activa,            // estado operativo
  activaPorDefecto,  // false si está en yerbanalytics.reglas.retiradas
  apagadaPorDemo,    // la capa de demo la apaga (siempre false sin modo demo)
  proteccion,        // texto de la consecuencia, o null
  estado             // { motivo, updatedBy, updatedTs } o null si nunca se cambió
)
```

`prioridad` se conserva (la usa el Inspector y el orden dentro de cada grupo) pero la vista no la muestra.
`RuleNodeDto` (schema) suma `activaPorDefecto` (D-11) y `codigo`.

**Escritura**: `PUT /api/rules/{name}/estado` con `{ activa, motivo, confirmaProteccion }` → `200` con la
`ReglaCatalogoDto` actualizada.

| Caso | Respuesta |
|---|---|
| Nombre desconocido | `404 { error }` |
| Apagar una protección sin `motivo` (blank) | `400 { error: "MOTIVO_REQUERIDO" }` |
| Apagar una protección sin `confirmaProteccion: true` | `400 { error: "CONFIRMACION_REQUERIDA", consecuencia }` |
| `motivo` > 500 caracteres | `400 { error: "MOTIVO_DEMASIADO_LARGO" }` |
| Mismo estado que el vigente | `200` sin escribir ni registrar nada |

Prender nunca pide motivo ni confirmación (vuelve a lo seguro o a lo de fábrica); si viene, se guarda.

**Por qué un endpoint aparte y no el `PUT /api/rules/parametros`**: el guardado de parámetros es un lote todo-o-nada
con restricciones cruzadas. Si el interruptor viajara en ese lote, un umbral inválido impediría apagar una regla que se
está portando mal, y "Guardar" mezclaría dos decisiones con auditoría distinta (una pide motivo). Además el interruptor es
por regla y tiene efecto inmediato en el próximo pulso: es una acción, no un borrador.

### D-6 — Protecciones: confirmación en la UI y en el backend

- El backend es la autoridad: valida `motivo` y `confirmaProteccion` (D-5) aunque la UI ya lo haga, porque la API la puede
  llamar cualquiera con `reglas.editar`.
- La UI, al apagar una protección, abre un diálogo con el **texto de `proteccion`** (la consecuencia), un campo `motivo`
  obligatorio y el botón "Apagar protección" en tono crítico. Para una regla que no es protección pide confirmación simple
  y `motivo` opcional.
- La tarjeta de una protección apagada lleva un `Callout tone="crit"` con la consecuencia, quién y cuándo la apagó y el
  motivo; el encabezado del grupo Protecciones cuenta las apagadas.

### D-7 — Permiso: `reglas.editar` cubre umbrales e interruptores

- `Permiso.REGLAS_EDITAR` cambia su descripción a "Editar los parámetros del motor y prender o apagar reglas";
  `fe/lib/catalogoSeguridad.ts` igual.
- `MapaPermisos`: `permiso(r, put, "/api/rules/*/estado", Permiso.REGLAS_EDITAR)` junto al de parámetros.
  `MapaPermisosCoberturaTest` la cubre.
- El código del permiso no cambia, así que **no hace falta migración de la matriz**: quien ya tenía `reglas.editar` lo
  sigue teniendo (ADMINISTRADOR, INGENIERO_AGRONOMO).

### D-8 — Registro: fila con quién/cuándo/por qué + evento en el historial; no en la auditoría de seguridad

- `regla_estado` guarda el último cambio (quién, cuándo, motivo). Cada cambio deja además un evento en el historial
  (`HistorialService.registrarEstadoRegla`, tipo `Configuración`, `regla = name`): "Regla «Riego por déficit hídrico»
  (R-01) apagada por <usuario>. Motivo: …", y para una protección, la consecuencia.
- **No va a la auditoría de seguridad** (`GET /api/auditoria`). Esa auditoría cubre HU-20 CA-03 —quién puede hacer qué:
  usuarios, matriz de permisos, política de sesión (`TipoAuditoria`, `ObjetivoAuditoria `)— y no decisiones agronómicas.
  Apagar una regla es operar el vivero, y donde se busca "¿por qué no se regó?" es el historial, que ya trae los cambios
  del catálogo de parámetros. Duplicarlo partiría la fuente de verdad. Si el cliente pide evidencia a prueba de
  manipulación para las protecciones, se suma un `TipoAuditoria.REGLA_PROTECCION_APAGADA` (pregunta abierta).

### D-9 — El simulador no escribe el estado operativo

- La cuenta Servicio sigue **sin** `reglas.editar`: el simulador no puede llamar a `PUT /api/rules/{name}/estado`. Su
  interruptor sigue siendo el de demo (capa en memoria), y en modo demo gana sobre el operativo (D-2).
- `DemoRuleDto` suma `operationalActive` (sólo lectura). `enabled` sigue significando la capa de demo. El simulador
  muestra la leyenda "Apagada en el vivero" junto al interruptor cuando `operationalActive` es `false`, para que se sepa
  que el override de demo la está forzando.
- **`happyPath.ts` sigue prendiendo `SupplyRule`** por `/api/demo/rules`, sin cambios de comportamiento.

### D-10 — El despacho de riego respeta el interruptor de cada guarda que revalida

Hoy el despacho revalida con overrides R-05, R-06, el ciclo y el tope de R-02, pero aplica S-01, R-03 y R-04 aunque la
regla esté apagada. Con un interruptor operativo eso deja de ser un detalle de demo: el agrónomo apaga R-03 y el riego
igual se descarta por lluvia en el despacho.

Decisión: **apagar una regla apaga también su revalidación**. `DespachoRiego` consulta `overrides.isEnabled` (o
`skipsGuard`) antes del descarte por bloqueo manual (`ManualLockRule.NAME`), por lluvia (`PosponerPorLluviaRule.NAME`) y
por saturación (`SustratoSaturadoRule.NAME`). Lo que hace el bloqueo al activarse (cerrar válvulas y bombas, cancelar lo
encolado, `BloqueoService`) es la acción del bloqueo, no de la regla, y no cambia.

### D-11 — El grafo del historial dibuja lo que corrió entonces, no lo que está prendido hoy

`historyGraphModel` infiere qué reglas corrieron con el schema **actual** y excluye `activa === false`. Apagar hoy una regla
la borra del grafo de todas las ejecuciones pasadas y, si fue la que decidió, `decidingRule` devuelve `null` y el evento se
queda sin grafo.

- `HistorialEventoEntity` suma `reglasInactivas` (TEXT, nombres separados por coma, nullable): las piezas con estado
  efectivo apagado **en el momento** en que se registró una decisión del motor. Lo completa `HistorialService` al registrar
  un evento con `regla`, a partir de `RuleOverrides` y el registro.
- `ActionRecord` suma `reglasInactivas?: string[]`. `historyGraphModel` usa todas las reglas del schema menos
  `reglasInactivas`; para un evento anterior a este cambio (`null`) usa las de `activaPorDefecto !== false`.
  `decidingRule` busca entre **todas** las reglas del schema: la que decidió siempre se dibuja.
- Se borra `plainLabel` (D-12).
- Límite conocido: una regla que se borra del código (p. ej. `FollowUpRule` en #67) no está en el schema y no se puede
  dibujar en eventos viejos; ya pasaba.

### D-12 — Sin emojis en la interfaz, desde el origen

- Las 23 `label()` del backend quedan como nombre legible, sin emoji y sin el código (que pasa a `code()`): `"Riego por
  déficit hídrico"` + `code() = "R-01"`. La UI compone "Riego por déficit hídrico · R-01".
- `fe/data/mock/catalogoReglas.fixture.json` y los tests que afirman etiquetas se actualizan.
- Reemplazos en el frontend, todos con componentes del sistema:
  - `ReglaNode.tsx` 🔒 → `<Icon name="lock" />` con `aria-label="Condición fija: no se edita"`.
  - `NodoPanel.tsx` `'⚠ ALERTA'` → `<Chip tone="warning">Alerta</Chip>`.
  - `lecturaTraza.ts` deja de devolver un carácter: devuelve el resultado y `NodoPanel`/`ReglaNode` lo dibujan con
    `<Icon name="check" />` (cumple), `<Icon name="close" />` (no cumple) y `<StateGlyph status="offline" />` (sin dato),
    con texto para lector de pantalla.
  - `ReasoningModal.module.css:9` → token nuevo `--scrim` en `tokens.css` (y `theme.ts`).
- Guarda automática: un test del frontend (junto a `styles/tokens.test.ts`) recorre `src/**/*.{ts,tsx,json}` y falla con
  cualquier `\p{Extended_Pictographic}`; en el backend lo cubre `ReglasArquitecturaTest` (D-3).
- La regla se escribe en CLAUDE.md §4 ("Convenciones de estilo") y en el spec `sistema-diseno`.

### D-13 — Pantalla Motor de reglas

Pestañas: **Reglas** (reemplaza a *Parámetros*) e **Inspector**. `?tab=` se conserva (`?tab=parametros` redirige a Reglas).

```
┌─ Motor de reglas ─────────────────────────────────────────────────────────────┐
│ [ Reglas | Inspector ]                                                        │
│ Buscar regla o parámetro [______________]                                     │
│ [Apagadas 2] [Protecciones 9] [Con cambios 3]                    (FilterChip) │
│                                                                               │
│ RIEGO · 7 reglas · 1 apagada                                                  │
│ ┌ Compartidos del grupo ───────────────────────────────────────────────────┐  │
│ │ Umbral de humedad de sustrato  [ 45 ] %   35–60 · fábrica 45             │  │
│ │ Compartido con 5 reglas: R-01, R-03, R-05, R-06, Alerta de falla hidr.   │  │
│ └──────────────────────────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────────────────────────┐  │
│ │ Riego por déficit hídrico · R-01          [Actúa]            (●──) Activa │  │
│ │ Riega el sector cuando la humedad cae bajo el umbral.                    │  │
│ │  Volumen por riego     [  5 ] L    1–20 · fábrica 5                      │  │
│ │  Umbral de humedad     45 %  · Compartido con 5 reglas  → editar arriba  │  │
│ └──────────────────────────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────────────────────────┐  │
│ │ Sustrato saturado · R-04                  [Protege]          (──○) Apag. │  │
│ │ ▌Apagada por Ana López el 22/10 14:05. Motivo: sonda en recalibración.   │  │
│ │ ▌Consecuencia: se riega con el sustrato saturado.        (Callout crit)  │  │
│ │  Saturación de bloqueo [ 85 ] %   70–95 · fábrica 85                     │  │
│ └──────────────────────────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────────────────────────┐  │
│ │ Ejecución del riego                       (sin interruptor: ejecuta)     │  │
│ │  Sectores simultáneos  [ 10 ]             1–20 · fábrica 10              │  │
│ └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
│ NUTRICIÓN · SANIDAD · MEDIASOMBRA · SEGUIMIENTO · PROTECCIONES …              │
│                                                                               │
│ ┌ SaveBar ─────────────────────────────────────────────────────────────────┐  │
│ │ 3 parámetros sin guardar                       [Descartar] [Guardar]     │  │
│ └──────────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────────┘
```

- **Grupos por objetivo** (`objetivo` del backend), en este orden: Riego, Nutrición, Sanidad, Mediasombra, Seguimiento,
  Protecciones. Un grupo sin reglas no se dibuja (Sanidad hasta que entren las F-*). Dentro del grupo, por `prioridad`,
  sin mostrarla.
- Las protecciones que pertenecen a un objetivo (R-04 y la guarda de ciclo en Riego, `ShadeFaultRule` en Mediasombra) van
  en su grupo con el chip *Protege*; *Protecciones* junta las transversales (S-01…S-05).
- **Tarjeta** (`ReglaCard`, se reescribe): nombre + código (`--font-display`), chip de efecto (`Chip`: Actúa → `info`,
  Avisa → `line`, Protege → `dark`; el efecto no usa colores de estado), `Switch` con `aria-label` "Prender/Apagar <nombre>", chip *Retirada* si
  `activaPorDefecto` es false, `Callout` si está apagada (crit para protecciones, neutral para el resto) con quién, cuándo
  y motivo, y otro si `apagadaPorDemo` ("El simulador la apagó en modo demo").
- **Umbrales en línea**: cada parámetro propio con `Field` + `InputUnit` (unidad), rango y valor de fábrica como `hint`,
  marca de modificado. Reutiliza `ParametroField`.
- **Compartidos**: un parámetro con `usadoPor.length > 1` se edita **una sola vez**, en el bloque "Compartidos del grupo"
  del primer grupo (en el orden de arriba) donde aparece uno de sus consumidores. En cada tarjeta que lo usa figura en
  sólo lectura con "Compartido con N reglas" y un enlace al bloque. `riego.umbral-humedad` (R-01, R-03, R-05, R-06,
  `HardwareFaultService`) vive en Riego; los `diagnostico.*` de las F-* vivirán en Sanidad.
- **Consumidores sin interruptor** (`DespachoRiego`, `ReadingPurge`, `PulsoSensado`, `ApplicationService`, `TankService`,
  `HistorialService` desde este cambio): tarjeta sin `Switch`, rotulada "Ejecución". El mapa `CONSUMIDORES` de
  `catalogoView.ts` suma `objetivo` y pierde `EscalamientoNodo`, `ReadingValidationGuard`, `HardwareFaultService` y
  `ApplicationPreconditions`, que ahora vienen como reglas.
- **Parámetros en lote, interruptores al instante.** Los parámetros se guardan con `SaveBar` y el `PUT` todo-o-nada que ya
  existe (`borrador.ts`). El `Switch` dispara su confirmación y su `PUT` en el momento, no entra al borrador ni lo toca, y
  muestra el resultado con `Toast`. Por qué: D-5.
- **Sin `reglas.editar`**: todo en sólo lectura (`Switch disabled`, campos `readOnly`, sin `SaveBar`) y un `Callout` "Para
  cambiar umbrales o prender y apagar reglas hace falta el permiso «Editar el motor de reglas»".
- **Inspector**: queda como está, con D-12 aplicado (sin emojis, glifos del sistema). El enlace `?regla=` del Inspector
  lleva a la tarjeta en la pestaña Reglas.
- Accesibilidad: el diálogo de confirmación atrapa el foco y devuelve el foco al `Switch`; los chips de efecto llevan texto.

### D-14 — Configuración → Umbrales queda con las 10 bandas de alerta

| Hoy en Umbrales | Destino |
|---|---|
| Bandas `umbral_metrica` (ideal/warn/crit + puntos de alerta) | Se queda: es lo único de la vista |
| Seguimiento post-acción (`seguimiento_latencia_min`, `seguimiento_delta_min`) | Catálogo del motor: `seguimiento.latencia-min` (ENTERO, min, fábrica 2) y `seguimiento.delta-min` (NUMERO, fábrica 5), familia nueva `SEGUIMIENTO`. Los declara **`HistorialService`**, que es quien los lee (pasa a `ConsumidorParametros`, como `DespachoRiego`); aparecen en Seguimiento como tarjeta de ejecución |
| Dosis máx. de insumo (`insumo_dosis_max_24h_ml`) | Se borra: nadie decide con él y duplica `insumo.max-dosis-24h` del catálogo |
| Horario por defecto de la mediasombra (`mediasombraAbiertaDesde/Hasta`) | Vista **Rustificación** (ver abajo). Mismo recurso de configuración: sólo se muda la UI |

- `ConfiguracionOperativa` (DTO), `ConfiguracionOperativaEntity`, `ConfiguracionService` (validación, getters
  `getLatencyMs/getLatencyLabel/getUmbralRecuperacion`, fábrica) y `data.sql` pierden los tres campos. `HistorialService`
  lee los dos parámetros del catálogo; las propiedades `yerbanalytics.historial.latency-ms/label` dejan de ser fallback
  (el catálogo siempre tiene fábrica).
- Frontend: se borran `SeguimientoForm` y `LimitesActuadoresForm` (con su `<Link>` a Motor de reglas), los campos de
  `ConfigOperativa`, su validación (`lib/configValidation.ts`) y el reset de `ConfiguracionPage`. Los textos de
  `ConfiguracionPage` y `PreferencesPage` dejan de hablar del motor (la sección "Frecuencia operativa" dice "Pulso de
  sensado"; el motor es otra vista).
- **Vista Rustificación**: como no existe en develop, se crea `/rustificacion` en el grupo Configuración con
  `ShadeScheduleForm` (horario por defecto, sobre `useConfigDraft`) y la lista de macro-zonas con su plan (o "sin plan:
  rige el horario por defecto"), cada una enlazada a su detalle. El alta y baja del plan siguen en el detalle de la zona.
  Ver pregunta abierta 1.
- **Migración manual** `migracion-umbrales-al-motor.sql` (idempotente, encabezado como `migracion-catalogo-parametros.sql`):
  copia `seguimiento_latencia_min` / `seguimiento_delta_min` a `parametro_regla` sólo si difieren de la fábrica, están en
  rango y no hay ya una fila (`ON CONFLICT DO NOTHING`, `RAISE NOTICE` si un valor queda afuera); después `DROP COLUMN IF
  EXISTS` de las tres columnas. Hasta correrla, los `NOT NULL` sin default de esas columnas rompen el `INSERT` de una fila
  operativa nueva (el backend arranca igual: la fila existente sólo se actualiza).

### D-15 — El invariante nuevo de CLAUDE.md

Texto propuesto, que reemplaza lo que hoy dice que los interruptores de reglas son sólo de demo:

> **Interruptor operativo vs. controles de demo.** Prender o apagar una regla es una **operación del vivero**: vive en
> Motor de reglas (`PUT /api/rules/{name}/estado`, permiso `reglas.editar`), se persiste en `regla_estado`, vale en modo
> `REAL` y sobrevive reinicios. Apagar una protección exige motivo y confirmación. Los **controles de demo** —apagar una
> regla para un escenario, cooldowns en cero, bypass de guardas, clima simulado, "Leer ahora"— siguen viviendo sólo en el
> simulador, en memoria, existen sólo en modo demo y, mientras están puestos, ganan sobre el estado operativo. El
> simulador no escribe el estado operativo.

Se edita en §4 ("Sin controles de demo": el interruptor operativo no es un control de demo), §6 ("Modos de operación y
Feature Flags" y "Patrón para agregar una nueva regla": el flag `enabled` lo gestiona `OperationalRuleStates`, y toda regla
declara `code()`, `objective()`, `effect()` y, si corresponde, `protection()`) y §6.2 (el interruptor del simulador es la
capa de demo).

## Adaptación de los PRs de reglas v2

**Hecho (backend, rama `integ/motor-reglas-v2`).** La pila #64–#68 se mergeó sobre este cambio con `git merge --no-ff`.
Lo resuelto:

- **Conflictos.** `FollowUpRule` queda borrada (#67) y `HistorialService` toma la estructura de #67 (`FollowUpPolicy`,
  `EffectivenessJob`, `withIrrigationFollowUp` / `withTreatmentFollowUp`) y conserva lo de este cambio:
  `guardarDelMotor` (guarda `reglasInactivas` en todo evento con regla: riego, alerta, insumo —también el que abre un
  seguimiento de efectividad—, mediasombra), el evento `Configuración` de `registrarEstadoRegla` y el constructor con
  `RuleOverrides`. Se quitó `SectorRepository` y el catálogo del constructor (ya no hay evaluación propia del seguimiento).
- **`seguimiento.*` obsoleto.** Se borraron `ParametrosSeguimiento`, la familia `SEGUIMIENTO` de `FamiliaParametro`, la
  declaración de parámetros de `HistorialService` y sus tests; la efectividad usa `efectividad.*`. En
  `migracion-umbrales-al-motor.sql` ya no se copian los valores de seguimiento a `parametro_regla`: se borran las columnas
  de `configuracion_operativa` (y `insumo_dosis_max_24h_ml`) y se limpian filas `seguimiento.*` si existieran. La fila
  huérfana de `regla_estado` de `FollowUpRule` se ignora y se loguea (D-1).
- **Declaración de las piezas del stack** (fijada por `ReglasArquitecturaTest`), sin emoji ni código en el label:

| Pieza | `code()` | `objective()` | `effect()` | `protection()` |
|---|---|---|---|---|
| `FungalDamageRule` | F-F1 | SANITY | ACTS | no |
| `FoliarPestRule` | F-P1 | SANITY | ACTS | no |
| `FungalConditionRule` | F-F2 | SANITY | WARNS | no |
| `OutbreakRule` | F-04 | SANITY | ACTS | no |
| `ChlorosisNonNutritionalRule` | F-C1 | NUTRITION | ACTS (corta la rama de insumos y cancela el fertilizante pendiente de F-C2: decide, aunque no mande a un actuador) | no |
| `ChlorosisNutritionalRule` | F-C2 | NUTRITION | ACTS | no |
| `SolarStressRule` | F-S1 | IRRIGATION | ACTS | no |
| `SolarStressUnexplainedRule` | F-S3 | SANITY | WARNS | no |
| `IneffectiveActionRule` | S-06 | PROTECTION | PROTECTS | sí: el motor repite el riego o tratamiento que ya no funcionó |
| `EffectivenessService` | E-01, E-02, E-03, F-05 | FOLLOW_UP | WARNS | no |

- **Despacho (D-10).** Sigue valiendo con el bypass de ventana de F-S1 en `DespachoRiego`: ambos usan
  `overrides.skipsGuard(...)`; los tests de #65 se pasaron a `TestRuleOverrides`.
- **Prioridades.** Siguen únicas (`ReglasArquitecturaTest`).

**Pendiente para la integración del frontend:** el dashboard todavía conoce la familia `SEGUIMIENTO` y las claves
`seguimiento.*`; los fixtures y tests de etiquetas con emoji de #64/#65/#67 se rehacen sin emoji; la pantalla Motor de
reglas muestra las diez piezas nuevas en su grupo sin tocar mapas.

## Riesgos

- **Precedencia demo vs. operativo.** En modo demo, el simulador puede prender una regla que el vivero apagó (D-2). Es
  aceptable porque la capa de demo no existe en producción; el dashboard muestra siempre el estado efectivo y el simulador
  avisa cuando su override contradice al vivero.
- **Huecos al apagar reglas de acción.** Apagar R-01 con R-02 encendida deja regar sólo el déficit crítico; apagar R-05
  deja a R-01 regar de noche; apagar `ApplicationPreconditions` corta todas las aplicaciones (no las libera). La tarjeta lo
  dice (D-4) y el historial lo registra, pero el sistema no impide combinaciones. No se modelan dependencias entre reglas
  en este cambio.
- **Despacho y revalidación.** Con D-10, apagar S-01 hace que el despacho riegue un sector bloqueado. Es la semántica
  pedida ("todo se puede apagar"), con confirmación y motivo, pero es el caso más delicado: el texto de la consecuencia
  tiene que decirlo literal.
- **Parámetros compartidos.** Editar `riego.umbral-humedad` cambia cinco piezas a la vez; editarlo una sola vez y mostrar
  "Compartido con N" lo hace visible, no lo evita.
- **Reinicios.** El estado operativo sobrevive; la capa de demo no (como hoy). Tras un reinicio con modo demo, lo que el
  simulador había apagado vuelve a correr si el vivero lo tiene prendido.
- **Cambiar `yerbanalytics.reglas.retiradas`** sólo afecta a las piezas sin fila. Una retirada que alguien prendió a mano
  sigue prendida aunque la lista cambie: es lo esperado (decidió una persona), pero hay que saberlo.
- **Migración manual** obligatoria para borrar las columnas (D-14).
- **PRs abiertos**: el merge de la pila ya está resuelto del lado backend (sección anterior); falta el frontend.

## Preguntas resueltas

Se resolvieron al cerrar el design (23/10/2026), sin bloquear la implementación:

1. **Vista Rustificación**: se crea `/rustificacion` en Configuración (D-14), con el horario por defecto de la mediasombra y
   un resumen del plan de cada macro-zona que linkea a su detalle. El horario por defecto es el par del plan: "con plan rige
   el plan; sin plan, este horario".
2. **Protecciones**: `StaleSensorRule` y `DailyDoseLimitRule` son protecciones. R-03, R-05 y R-06 **no**: son frenos
   agronómicos de R-01, no protecciones físicas ni de integridad de datos; pedir confirmación para todo le quita valor a la
   confirmación. `ApplicationPreconditions` queda como *Actúa*.
3. **Rangos de seguimiento**: se aceptan los amplios (1–1440 min y 0,5–50); #67 los reemplaza por `efectividad.*`.
4. **Auditoría de seguridad**: no (D-8). La fila de `regla_estado` y el evento en el historial dejan quién, cuándo y por
   qué; la auditoría encadenada queda para usuarios, matriz y política de sesión.
5. **Códigos**: no se inventan. Las piezas sin código en `reglas_v2` se muestran sin código.
6. **Conteo de migraciones** de CLAUDE.md §6: la tarea de docs reconcilia la lista con los `migracion-*.sql` reales y suma
   la nueva.
7. **Precedencia con la demo** (agregada al cerrar): el override explícito del simulador gana en modo demo (D-2), para no
   romper el happy path de Demo Expo.
