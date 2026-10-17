# reglas-v2-overview

Índice de los cambios OpenSpec que llevan el motor de reglas a `docs-motor-reglas-e-integracion/reglas_v2.md`
(**v2**, 28/09/2026). Esta rama (`feature/doc-openSpec-rules`) es la **rama padre**: acá vive sólo la
documentación. Cada cambio se implementa en **su propia rama**, que sale de esta, y vuelve acá por PR.

> Origen: auditoría del código del backend contra la v2 (16/10/2026) y una segunda lectura que cerró los puntos "no verificado".
> "Implementada" = existe la clase o el servicio y hace lo que dice la v2; "parcial" = existe pero difiere. Lo verificado y lo que
> sigue abierto de cada cambio está en su `design.md`, sección *Estado verificado*, y en el bloque `0. Previo` de su `tasks.md`.

## Convenciones comunes a todos los cambios

- **Rama de implementación:** `feat/reglas-v2-<slug>` (el `<slug>` es el nombre del cambio sin `add-`/`align-`),
  creada desde `feature/doc-openSpec-rules`. Al cerrar, PR a esta rama.
- **TDD estricto** (`openspec/config.yaml`): test en rojo → mínimo código → refactor. Un commit por bloque de
  `tasks.md`, conventional commits, sin atribución de IA. Suite en verde antes de cerrar un bloque:
  `cd Desarrollo/backend && ./mvnw test` (más `npm test && npm run lint` si toca el frontend, y
  `cd Desarrollo/simulador && npm test && npm run typecheck` si toca el simulador).
- **Código en inglés, texto en español** (CLAUDE.md §7).
- **Toda regla nueva** sigue el patrón de CLAUDE.md §6: implementa `Rule` (o `ZoneRule`), `@Component`, `priority()`,
  `name()`, `label()`, `branch()`, umbrales declarados en `parametros()` (catálogo, nunca `@Value`), y la lista de verificación de abajo.
- **Parámetros nuevos** entran al catálogo (`engine/parametros/`) con fábrica y rango de `reglas_v2` §11.
- **Ruta nueva de la API** ⇒ permiso declarado en `seguridad/MapaPermisos`
  (`MapaPermisosCoberturaTest` falla si falta).
- **Contrato MQTT:** si cambia, se toca `Desarrollo/embebido/comun/contrato.h` y sus dos espejos
  (`ContratoNodo.java`, `simulador/server/contract.ts`).

## Lista de verificación: control desde el simulador (obligatoria para toda regla nueva)

Requisito del equipo: *cada regla nueva se puede apagar y accionar desde el simulador, como las demás.* Qué significa, verificado en el código:

1. **Es un `DemoControllable`** (`add-control-demo-reglas`): aparece en `GET /api/demo/rules` y se apaga con `PUT /api/demo/rules/{nombre}`
   `{enabled:false}`. Apagada no actúa ni corta nada y lo deja en la traza. Vale también para reglas por macro-zona y para servicios que no son `Rule`.
2. **Declara sus cooldowns y bypass:** toda espera/intervalo/latencia como `cooldownKeys` (minutos; 0 la elimina) y toda guarda de estado o franja
   horaria como `bypassKeys`, y los lee del contexto (`cooldownOverrideMinutes` / `isBypassed`); si un servicio revalida en su nombre usa `skipsGuard`.
3. **Tiene un escenario en el simulador** (`Desarrollo/simulador/src/…/scenarios/`): al seleccionarla y pulsar Disparar arma la telemetría, el clima
   y el diagnóstico previo que la hacen cumplir y pide un pulso con motor; detecta conflictos con otras reglas; tiene su test.
4. **En producción no existe nada de eso:** `/api/demo/**` da 404 y el dashboard productivo no tiene controles de demo (CLAUDE.md §4/§6.2).
5. **El backend no conoce al simulador:** ni endpoint, ni entidad, ni rama de código propia.

Cada cambio tiene su bloque *Control desde el simulador* en `tasks.md` y su `specs/…-control-demo/spec.md` con las claves exactas.
**Esta lista se exige en la revisión de cada PR**: sin las cinco condiciones, la regla no se integra. Cómo se implementa cada punto: `Desarrollo/simulador/README.md` ("Cómo agregar el escenario de una regla") y `DemoControllable` en el backend.

## Los cambios (14)

| Id | Cambio (carpeta) | Reglas v2 que cubre | Depende de |
|---|---|---|---|
| **C** | `add-control-demo-reglas` | Infraestructura para apagar/accionar desde el simulador; arregla R-03 simulado y el clima simulado | — |
| **H** | `add-historial-lecturas` | Serie de lecturas por zona (la v2 la da por hecha y no existe) | — |
| 0 | `align-reglas-existentes-v2` | Lo que hoy **difiere** (S-02, confianza 70 %, R-02, guarda de ciclo, simulador con 85 %) | C |
| 1 | `add-bloqueo-manual` | S-01 (API + UI + efecto de bloquear/reanudar) | C |
| 2 | `add-seguridad-lectura-hardware` | S-03 lectura inválida, S-04 hardware incompleto | C |
| 3 | `add-fallas-esp32` | S-05 (tópico de errores, caudal, mediasombra) + reparaciones | C, 2 |
| 4 | `add-metrica-dpv` | §1.4 DPV (cálculo, persistencia, UI) | C, H |
| 5 | `add-mediasombra-v2` | M-01, M-02, §7 plan de rustificación por MZ, principio 4, `ZoneRuleEngine` | C |
| 6 | `add-dosificacion-base` | §8.1–8.3: insumos, tanques/stock, P-D1…P-D5, reingreso, **bomba por insumo en contrato y firmware** | C, 2 |
| 7 | `add-plan-nutricion` | N-01, N-02 | C, H, 6, `ZoneRuleEngine` (5) |
| 8 | `add-reglas-biotico` | F-F1, F-F2, F-P1, F-04, F-06 | C, H, 4, 6 |
| 9 | `add-reglas-clorosis-estres-solar` | F-C1, F-C2, F-S1, F-S2, F-S3 | C, H, 5, 6 |
| 10 | `add-efectividad-y-s06` | E-01 (realineado), E-02, E-03, S-06, F-05 + revisión humana + sector del nodo testigo | C, 6, 8, 9 |
| 11 | `add-operacion-sin-internet` | O-01…O-05 (HU-13) | C, 5, 6 |

### Orden sugerido

```
 Ola 0 (en paralelo):   C control-demo · H historial-lecturas · 0 align (después de C)
 Ola 1 (en paralelo):   1 bloqueo · 2 seguridad · 5 mediasombra · 4 DPV (necesita H)
 Ola 2:                 3 fallas (tras 2) · 6 dosificación-base (tras 2)
 Ola 3:                 7 nutrición · 8 biótico · 9 clorosis/estrés   (tras 6; 9 y 7 usan el ZoneRuleEngine de 5)
 Ola 4:                 10 efectividad + S-06
 Ola 5:                 11 sin internet
```

### Reglas heredadas que quedan sin dueño hasta que su reemplazo exista

| Regla v1 (clase) | Se retira en | Por qué no antes |
|---|---|---|
| `ShadingRule` | `add-mediasombra-v2` | Es lo único que mueve la malla hoy |
| `SupplyRule` | `add-dosificacion-base` | Es lo único que dosifica hoy |
| `DailyDoseLimitRule` | `add-dosificacion-base` | La v2 la reemplaza por intervalos por insumo (P-D2) |
| `FollowUpRule` (delegador global) | `add-efectividad-y-s06` | Es lo que dispara hoy la evaluación de efectividad |

`add-dosificacion-base` no debe integrarse a la rama padre sin al menos una regla que pida aplicaciones (7, 8 o 9); si hace falta, se deja
`SupplyRule` apagada por flag en vez de borrarla.

### Cobertura: cada regla de la v2 tiene exactamente un dueño

S-01→1 · S-02→0 · S-03,S-04→2 · S-05→3 · S-06→10 · R-01…R-06→ya implementadas (parámetros verificados contra §11; sólo el tope de R-02
está en 0) · M-01,M-02,§7→5 · §1.4→4 · §8.1–8.3→6 · N-01,N-02→7 · F-F1,F-F2,F-P1,F-04,F-06→8 · F-C1,F-C2,F-S1,F-S2,F-S3→9 ·
F-05,E-01…E-03→10 · O-01…O-05→11.

## Hallazgos que cambian el alcance, y dónde se hacen

| Hallazgo (verificado en el código) | Se hace en | Estado en la documentación |
|---|---|---|
| S-01 no se puede activar: no hay endpoints de bloqueo ni UI | 1 | Tareas 1–3 |
| Nadie lee `bloqueoRepeticion` (S-06 inexistente) | 10 | Bloque 4 |
| No existe historial de lecturas; N-02, F-C1, F-F*, F-P1, F-S* lo necesitan | H | Cambio propio, prerrequisito de 4, 7, 8, 9 |
| El motor sólo evalúa por sector; mediasombra, N-01/N-02 y F-S2 son por MZ | 5 (`ZoneRuleEngine`) | Tarea 3.0 |
| La mediasombra y las bombas se registran por sector; la v2 habla de la MZ | 5 y 6 | Un comando por sector de la MZ con mediasombra registrada |
| El pronóstico no trae temperatura por hora ni UV máximo | C §2 (lo consume 5) | Tarea 2.3 de C |
| El simulador no puede accionar R-03 (el clima simulado ignora `precipMm` y no genera marcas horarias) | C §2 | Tareas 2.1–2.2 de C |
| Reglas por zona/servicios no aparecerían en `/api/demo/rules` | C §1 | `DemoControllable` |
| Nadie escribe `dispositivo.falla` | 3 | El primer camino que lo escribe |
| Una bomba por sector y el comando no lleva insumo (la v2 pide una por insumo) | 6 (D-2) | **Requiere confirmar**: contrato + firmware + migración |
| El nodo testigo no tiene sector (E-01) | 10 (2.3) | `sectorId` opcional del nodo |
| Las etapas 2 y 3 del plan tienen dos ventanas; el front modela una | 5 (D-5) | **Requiere confirmar**: cambia el modelo del front ya hecho |
| Vecinos de F-04 sin mapa físico (sólo el layout visual `sectoresPorFila`) | 8 (D-6) | Supuesto a validar con el vivero |
| `DEFAULT_THRESHOLDS` del simulador con 85 % y `CRITICAL_AIR_TEMP` de `SupplyRule` | 0 (7.3) y 6 (9.3) | Tareas |

Los dos marcados **Requiere confirmar** son los únicos que dependen de una decisión del equipo antes de implementarse; el resto está en
`tasks.md` y se hace.
