# Design: add-rustificacion-por-macrozona

El frontend se hizo primero (tareas 1.x). Este documento fija cómo se adaptó el resto del sistema, y con él lo
que estaba mal modelado: **la mediasombra es de la macro-zona, no del sector**. El código de este cambio va en
español (decisión del equipo para este cambio).

## Contexto: qué había

- La mediasombra se registraba como un dispositivo **de sector** (`DispositivoEntity.sectorId`), su posición vivía en
  `SectorEntity.actuadorShade` y el `shade SET` salía por el tópico del sector. `ShadeFaultRule` ya razonaba por zona,
  pero corría en la cadena de cada sector.
- `ShadingRule` (prioridad 14, rama MEDIASOMBRA) cerraba por pico de UV y aplicaba una rustificación global por fecha de
  siembra (`RustificacionEtapaEntity`, porcentaje de apertura).
- `ActuatorShutdown` (estado seguro) y `SecuenciaService` (Demo Expo, lo que usa el simulador para abrir y cerrar)
  publicaban la malla a un sector.

## Decisiones

### D1. La mediasombra es un equipo de la macro-zona

Se registra como el nodo testigo: con `zonaId`, **sin** `sectorId`, a lo sumo una por zona (`HardwareService.TIPOS_DE_ZONA`).

- Deja de ser actuador requerido del sector (`sectoresIncompletos`, `HardwareCoverage`); `HardwareFaults` ya no trae la
  falla de la malla. La vista de la malla es `MallaZona(registrada, falla)` (`HardwareService.mallaDe(zona)`).
- La posición pasa de `SectorEntity.actuadorShade` a **`ZonaEntity.mediasombraAbierta`** (`Boolean`; `null` = nunca se le
  ordenó). El snapshot la expone como `zonas[].mediasombra` (`abierta` | `cerrada` | `null`); `Actuadores` del sector la pierde.
- La falla `MEDIASOMBRA_FIN_DE_CARRERA` marca el dispositivo de la zona; repararlo lo devuelve a lo autónomo.
- Migración manual `migracion-mediasombra-por-zona.sql`: mueve una mediasombra por zona del sector a la zona, borra las demás,
  copia la posición, borra `sector.actuador_shade` y `rustificacion_etapa`.

### D2. Contrato MQTT: el comando de la malla va por el tópico de zona

El `shade SET` se publica en el **comando de zona** (`nursery/zone/{z}/command`, el del `LEER_AHORA`) con
`actuador: "shade"`; el ACK vuelve por **`nursery/zone/{z}/ack`**, mismo payload que el del sector.

- Backend: `ComandoActuadorPublisher.publicarZona`, `ContratoNodo.TOPIC_ACK_ZONA*`; el adaptador de ACK escucha los dos tópicos
  y `AckActuadorReceiver` entrega el de zona con `sectorId = null`. El seguimiento de comandos habla de "Macro-zona" en ese caso.
- Firmware: `contratoTopicAckZona`, `CONTRATO_SECTOR_ZONA` (0); `parsearComandoZona` distingue `ACTUADOR`; `nodo_combinado` y
  `nodo_actuador` (que ahora se suscribe al comando de zona) encolan el `shade SET` con sector 0 y publican su ACK por la zona.
  Un `shade SET` por el tópico de un sector se sigue aceptando.
- Simulador: `zoneAckTopic`, `isZoneShadeCommand`; el nodo simulado atiende el `shade SET` del comando de zona.
- Orden de despliegue: backend antes que firmware (el backend escucha los dos ACK).

### D3. La rustificación NO es parte del motor de reglas

El plan (o el horario por defecto) abre y cierra la malla con un servicio propio, **`HorarioMediasombraService`**
(`mediasombra/`), no con una regla: no depende del pulso de sensado ni de lo que se mide, sólo del reloj del vivero.

- Cada minuto calcula, por zona, la **posición programada** (abierta dentro de la ventana de hoy, cerrada fuera) y **ordena
  sólo cuando cambia** respecto de la revisión anterior: borde de apertura, borde de cierre, plan iniciado o eliminado,
  horario por defecto cambiado, cambio de etapa. Al arrancar, la primera revisión pone cada zona en su posición si la última
  orden no coincide.
- El motor puede mover la malla en cualquier momento (hoy, cerrarla por pico de UV). **El horario no la corrige**: queda
  como la dejó la regla hasta el próximo borde de apertura (puede ser al día siguiente). Si en el futuro hace falta reabrir
  antes, será una regla que lo haga en un pulso siguiente.
- No manda nada si la zona tiene bloqueo manual (S-01), no tiene malla registrada (S-04), la tiene averiada (S-05) o está en
  estado seguro (nodo testigo fuera de servicio). Lo deja en el historial como «Omitida».
- Es `DemoControllable` (se apaga desde el simulador).
- Alta y baja de un plan piden una revisión al confirmar la transacción, para no esperar al minuto.

### D4. Las reglas de mediasombra son de zona

`ShadeFaultRule` (13) y `ShadingRule` (14) pasan a `ZoneRule` (`mediasombra/`). La rama MEDIASOMBRA sale de la cadena por
sector (`IncompleteHardwareRule` ya no la corta).

- `ShadeFaultRule` es la guarda de la rama: `ABORT_MEDIASOMBRA` con bloqueo de la zona, sin malla registrada o con la malla
  averiada (bypass `fault` y `hardware`). `ZoneRuleEngine` corta las reglas que siguen de esa rama
  (`OMITIDA_RAMA_BLOQUEADA`), igual que el orquestador por sector.
- `ShadingRule` queda sólo con lo reactivo (pico de UV → cerrar), con `DetalleMediasombra(abrir)` en vez de `[apertura=N]`
  en el motivo. `ZoneRuleContext` suma el pronóstico del pulso y la `MallaZona`.
- `ZoneActionExecutor` ejecuta `MOVER_MEDIASOMBRA` por `MediasombraService`, el único que publica la malla, guarda la posición
  y deja el historial («Mediasombra»). El estado seguro y las secuencias publican por su cuenta (prioridad y commandId
  propios) y registran la posición.
- Se borran el `@Value` de la fecha de siembra y su propiedad.

### D5. Modelo del plan

- `PlanRustificacionEntity` (`plan_rustificacion`): `id`, `zonaId`, `fechaInicio` (`LocalDate`, hora del vivero),
  `creadoPor`, `creadoEn`, `canceladoEn` (null si rige); etapas como `@ElementCollection` de `EtapaPlanRustificacion`
  (`etapa_plan_rustificacion`: `orden`, `dias`, `abiertaDesde`, `abiertaHasta`).
- **El estado se calcula** (`PlanRustificacionService.estado`): `cancelado` si se eliminó; `programado` antes del inicio;
  si no, `en_curso`, **también pasada la última etapa**: sigue rigiendo la última hasta que alguien elimina el plan. No
  hay `finalizado`.
- Validación (400): fecha `YYYY-MM-DD` no anterior a hoy, 1–20 etapas, `dias` 1–60, horas `HH:MM` con cierre posterior a la
  apertura. Un plan vigente por zona (409).

### D6. API

| Método | Ruta | Permiso | Respuesta |
|---|---|---|---|
| GET | `/api/zonas/{zonaId}/rustificacion` | `vivero.ver` | plan vigente, o `204` |
| POST | `/api/zonas/{zonaId}/rustificacion` | `configuracion.editar` | plan creado; `400`/`409` con `{ error }` |
| POST | `/api/zonas/{zonaId}/rustificacion/cancelar` | `configuracion.editar` | plan eliminado; `409` si no hay |

El JSON es el que ya usaba el frontend (`fechaInicio`, `etapas`, `abiertaDesde`…). La lectura va con `vivero.ver` porque la
tarjeta está en el detalle de la zona, que ve cualquiera que vea el vivero. Iniciar y eliminar quedan en el historial.

### D7. Horario por defecto

`ConfiguracionOperativa` suma `mediasombraAbiertaDesde` / `mediasombraAbiertaHasta` (`HH:mm`, **08:00–17:00 de fábrica**,
editables en Configuración → Umbrales; `null` en un PUT conserva el guardado). Validación: las dos horas válidas y el cierre
después de la apertura. Se borran `Configuracion.rustificacion`, `RustificacionEtapa*` y su siembra.

### D8. Frontend

- Alta de hardware: la mediasombra se asocia a una macro-zona, como el nodo testigo (también en el mock).
- La tarjeta del plan: sin `finalizado`; pasada la última etapa dice que se sostiene; el botón es «Eliminar plan».
- El detalle de sector muestra la mediasombra **de la macro-zona** (abierta / cerrada / sin órdenes) y la tarjeta de
  rustificación, la última orden.

## Riesgos y pendientes

- **Firmware sin compilar ni probar en hardware** (no hay PlatformIO en el entorno de este cambio).
- **Migración manual obligatoria**: hasta correrla, `sector.actuador_shade NOT NULL` impide crear sectores.
- El horario corre cada minuto: un borde se aplica con hasta un minuto de demora.
