# Design: add-dosificacion-base

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| `SupplyRule` | prio 11, rama INSUMO, `usaDiagnostico()=true`; estado `critical` + confianza ⇒ `ACTIVAR_BOMBA` sin dosis ni insumo |
| `DailyDoseLimitRule` | prio 5, rama INSUMO; cuenta eventos "Insumo" del historial en 24 h contra `insumo.max-dosis-24h` (fábrica 1; rango 1–10) |
| Parámetros de insumo | sólo `ParametrosInsumo.MAX_DOSIS_24H` |
| Diagnóstico | `DiagnosticoEntity` (`estado`, `conf`, `sev`, `confidence`, `detections`, `capturaId`, `creadoEn`); "diagnóstico nuevo" = marcado como usado en el sector (CLAUDE.md §2) |
| Aplicación previa | `ContextoRiego.ultimaAplicacionMs` ("fertilizante o fitosanitario") — lo consume R-06 |
| Bombas | `HardwareService.BOMBA = "bomba_peristaltica"`. Verificado: se registra **una por sector** (se rechaza una segunda) y `DispositivoEntity` **no tiene campo de insumo** |
| Contrato de la bomba | `contrato.h`: `pump ON {}` o `pump ON {ml}`; con `ml` el **firmware** calcula el tiempo con `BOMBA_INSUMO_MS_POR_ML` y recorta a `LIMITE_BOMBA_ML_MAX` / `LIMITE_INSUMO_SEG_MAX`; sin `ml` usa `INSUMO_SEG_POR_DEFECTO`. **No hay campo de insumo en el comando.** Hoy `ActionExecutor` publica `pump ON {}` (sin `ml`) |
| Stock | No existe |
| Ack/seguimiento | `SeguimientoComandosService` vigila cada comando de actuador |

## Decisiones

### D-1 — Un solo camino para aplicar
Toda regla pide una aplicación así: `ApplicationService.request(sectorId, Insumo, reglaOrigen)`. La regla **decide**,
el servicio **ejecuta**. Mismo patrón que el riego (reglas deciden, `DespachoRiego` abre por tandas) y que "el
diagnóstico describe; el motor decide". Devuelve `APLICADA | DIFERIDA(motivo) | RECHAZADA(motivo)`.

### D-2 — Insumo ↔ bomba (CAMBIO DE CONTRATO — decisión a confirmar)
La v2: "cada insumo tiene su tanque y su bomba dosificadora". Verificado: el sistema registra **una** bomba por sector, sin insumo,
y el comando MQTT no dice qué insumo. Hay dos salidas:
- (a) **Dos tipos de dispositivo** (`bomba_fertilizante`, `bomba_fitosanitario`), cada uno una por sector, y el comando
  `pump ON {ml}` lleva un campo `insumo` (`FERTILIZANTE`|`FITOSANITARIO`) para que el nodo elija la bomba. Toca `contrato.h`,
  `ContratoNodo.java`, `simulador/server/contract.ts` y el firmware (`act_riego.cpp`). **Recomendada** (es lo que describe la v2).
- (b) Una bomba por sector que dosifica el insumo del tanque que el operario conectó; el backend sólo sabe `insumo` por tanque. No
  toca el contrato, pero un error de conexión aplica el producto equivocado y S-04/stock no distinguen.
Este cambio implementa (a) salvo que el equipo elija (b). Es un cambio de alcance real (hardware y firmware), con migración del registro
de dispositivos (`bomba_peristaltica` existente ⇒ `bomba_fertilizante`, script manual `migracion-bombas-por-insumo.sql`).

### D-3 — Precondiciones como funciones puras
`PrecondicionesAplicacion` (patrón de `PrecondicionesRiego`): `intervaloCumplido`, `sinOtraAplicacionHoy`,
`enVentana`, `sinLluvia`, `humedadMinima`. Cada una devuelve un resultado con **motivo** para la traza. El servicio las
revalida **al despachar** (igual que el riego revalida al abrir).

### D-4 — Orden fitosanitario → fertilizante (P-D2)
Si el mismo día corresponden ambos en un sector, se aplica el fitosanitario y el fertilizante queda `DIFERIDA`
para el día siguiente. Se resuelve en el servicio, no en las reglas.

### D-5 — Diferidas
Tabla `aplicacion_pendiente` (sector, insumo, regla, creada, motivo del último diferimiento). La procesa el tick del
scheduler cuando se abre una **ventana de aplicación** (07:00 y 17:00). Una pendiente vence si deja de ser vigente su
causa (diagnóstico viejo, plan sin aplicación): la regla de origen la cancela. *Sin vencimiento propio en la v2;
propuesta: vence a los 7 días* (parámetro).

### D-6 — Tiempo y stock
El contrato **ya soporta** `ml`: el backend manda `pump ON {ml, insumo}` y el **firmware** convierte a tiempo con su calibración
(`BOMBA_INSUMO_MS_POR_ML`) y corta solo (principio 6). Por eso el backend NO calcula la duración de la bomba: la v2 §1.2.1
(`mL = caudal × tiempo`) queda del lado del firmware, y la recalibración mensual se hace en `config.h`. La orden "lleva su duración"
en el sentido de que el nodo corta por tiempo; el ack puede informar `limite_excedido`. Dosis 100 mL, rango 20–500 (debe ser
≤ `LIMITE_BOMBA_ML_MAX`; restricción cruzada con el contrato). El tanque descuenta `dosis`; bajo 20 % ⇒ `WARNING` "Reponer [insumo]"; en 0 ⇒
`CRITICAL` y no se aplica. Stock **por insumo y por MZ** *(pregunta abierta: ¿un tanque por MZ o uno central?)*. Se elimina de este
cambio el parámetro `caudal-bomba-*` (ver D-10).

### D-7 — Reingreso
Fitosanitario ⇒ `reingreso_hasta` en el sector (24 h por defecto). Visible en el dashboard; no bloquea actuadores
(sólo informa y activa R-06, que ya existe).

### D-8 — Diagnóstico vigente
`DiagnosticoVigente.of(sector, ahora)`: último diagnóstico del sector, `creadoEn` ≤ 48 h, `conf` ≥ umbral (70 % tras
`align-reglas-existentes-v2`), distinto de "No concluyente". Se reutiliza en F-*.

### D-9 — Retiro de las reglas v1
`SupplyRule` y `DailyDoseLimitRule` se borran en el último bloque, cuando N/F aún no existen **no hay quien dosifique**.
Mitigación: este cambio se integra a la rama padre sólo junto con `add-plan-nutricion` o con al menos una regla F;
si no, se deja `SupplyRule` apagada por flag en vez de borrarla (decisión abierta con el equipo).

### D-10 — Migración del parámetro
`insumo.max-dosis-24h` desaparece del catálogo (`resources/migracion-quitar-max-dosis.sql`). Parámetros nuevos:
| Clave | Fábrica | Rango |
|---|---|---|
| `insumo.dosis-fertilizante` | 100 mL | 20–500 |
| `insumo.dosis-fitosanitario` | 100 mL | 20–500 |
| `insumo.intervalo-fertilizante` | 5 d | 3–14 |
| `insumo.intervalo-fitosanitario` | 7 d | 5–21 |
| `insumo.ventanas-aplicacion` | 07:00–10:00 y 17:00–19:00 | — |
| `insumo.temp-max-aplicar` | 30 °C | 25–32 |
| `insumo.humedad-min-aplicar` | 45 % | 35–60 |
| `insumo.lluvia-impide-mm` / `…-horas` | 5 mm / 6 h | 2–20 / 2–12 |
| `insumo.reingreso-horas` | 24 | — |
| `insumo.stock-aviso` | 20 % | 10–50 |
El catálogo hoy sólo tiene tipos NUMERO/ENTERO/VENTANA_HORARIA: **dos ventanas** (07–10 y 17–19) exigen
`VENTANA_HORARIA` múltiple o dos parámetros (`ventana-manana`, `ventana-tarde`). Default: dos parámetros.
