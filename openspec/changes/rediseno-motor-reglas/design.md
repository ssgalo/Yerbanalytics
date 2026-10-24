# Design: rediseno-motor-reglas

## Contexto

Hoy `CatalogoParametrosService.vigentes()` devuelve un `ParametrosVigentes` (clave → valor) único; las reglas lo leen
por `Evaluacion` (que exige que la clave esté declarada en `parametros()`) y los servicios por `DeclaredParameters` o
directo. La tabla `parametro_regla` guarda overrides por `clave`. El estado operativo de cada pieza vive en
`regla_estado` (una fila por pieza cambiada).

## D-1 · El valor es de `(pieza, clave)`; la definición sigue siendo de la clave

- Valor vigente de `(p, k)` = override de `(p, k)` si existe; si no, fábrica de `k`. **No hay herencia** de un valor
  "global": una copia sin override está en fábrica.
- Tabla nueva `regla_parametro (consumidor, clave, valor, updated_by, updated_ts)`, PK compuesta. `parametro_regla` se
  migra a mano y se borra (D-7).
- `vigentes()` devuelve `ParametrosPorPieza`: snapshot inmutable de todo, con `de(ConsumidorParametros)` →
  `ParametrosVigentes` (la misma API de hoy: `numero`, `hora`, `ventana`). `Evaluacion` y `DeclaredParameters`
  reciben la vista de su dueño, así que las reglas no cambian. Los lectores directos (`HardwareFaultService`,
  `ReadingPurge`, `PulsoSensadoService`, `EscalamientoNodoService`, `ApplicationService`, `ZoneRuleEngine`,
  `ReadingValidationGuard`, `ConfiguracionService`) pasan a leer su vista.
- La huella de la traza se calcula sobre la vista de la pieza: dos trazas con la misma huella vieron los mismos valores.
- El umbral efectivo de `seguridad.antiguedad-max-lectura` (piso vs. cadencia de sensado) se superpone en cada vista que
  lo declara, con los `sensado.*` de `PulsoSensado`, que es su dueño.

## D-2 · Quien revalida una decisión lee la copia de quien decidió

Un servicio que ejecuta o revalida lo que una regla decidió no debe poder discrepar con ella. `ConsumidorParametros`
suma `default Map<DefinicionParametro, String> parametrosDe()` (clave → nombre de la pieza dueña) y
`DeclaredParameters` resuelve esas claves en la vista de la dueña:

| Servicio | Clave | Lee la copia de |
|---|---|---|
| `DespachoRiego` | `riego.lluvia-*` | `PosponerPorLluviaRule` (R-03) |
| `DespachoRiego` | `riego.saturacion-bloqueo` | `SustratoSaturadoRule` (R-04) |
| `DespachoRiego` | `riego.ventana-normal` | `FueraDeVentanaRiegoRule` (R-05) |
| `DespachoRiego` | `riego.pausa-tras-aplicacion` | `PausaTrasAplicacionRule` (R-06) |
| `DespachoRiego` | `riego.exceptuado-bloqueo` | `DeficitCriticoRule` (R-02) |
| `DespachoRiego` | `seguridad.antiguedad-max-lectura` | `StaleSensorRule` (S-02) |
| `NutritionPlanService` | `nutricion.plan-intervalo` | `NutritionPlanRule` (N-01) |

Esas claves no figuran como propias del servicio: en la pantalla se editan sólo en la regla. Lo que el servicio lee y
nadie más decide (p. ej. `riego.sectores-simultaneos`, dosis, ventanas de aplicación, `sensado.*`) es su copia propia y
se muestra en su fila de ejecución. El catálogo valida al arrancar que la dueña exista y declare la clave.

## D-3 · `usadoPor` deja de existir en la API

Con una copia por pieza, "compartido" no tiene sentido. `ParametroDto` pasa a describir una copia: suma `regla`
(la pieza dueña de la copia) y quita `usadoPor`. El catálogo lista una entrada por `(pieza, clave)`.
`ReglaCatalogoDto.parametros` sigue siendo la lista de claves propias (sin las de D-2).

## D-4 · Restricciones cruzadas

Una restricción (p. ej. `umbral-critico < umbral-humedad`) se evalúa en cada vista de pieza que declara **todas** sus
claves. Si ninguna pieza las declara todas pero cada clave tiene una sola pieza que la declara (estrés solar: luz baja en
F-S3, luz alta en F-S1), se evalúa con el valor de cada dueña. Si no, no se evalúa: se pierde, a propósito, el control
entre el umbral crítico de R-02 y el umbral de riego de R-01 (son copias de reglas distintas que el agrónomo eligió
separar). El error se informa sobre la copia `(pieza, clave)` editada.

## D-5 · Nombre propio y eliminación en `regla_estado`

- Columnas nuevas, nulables (ddl-auto las agrega sin romper filas existentes): `etiqueta TEXT` y `eliminada BOOLEAN`.
  `null` = nombre de fábrica / no eliminada.
- `ReglaCatalogoDto` suma `etiquetaFabrica` y `eliminada`; `label` pasa a ser el nombre vigente.
- `PUT /api/rules/{name}/etiqueta {etiqueta}`: 1–80 caracteres tras recortar; vacío o `null` restablece el de fábrica.
- `DELETE /api/rules/{name}`: apaga y marca eliminada en una transacción. Una protección (`protection()` presente) da
  `409`. `POST /api/rules/{name}/restaurar`: quita la marca y la deja **apagada** (prenderla es otra decisión).
- Una pieza eliminada no se puede prender (`PUT …/estado` con `activa: true` da `409`) y no corre: para el
  orquestador es una pieza apagada más (`OperationalRuleStates` la informa inactiva).
- Las piezas que no tienen interruptor (servicios de ejecución que no son `DemoControllable`) no se renombran ni se
  eliminan: no tienen fila en `regla_estado`.
- Cada cambio queda en el historial como hoy el cambio de estado (quién, cuándo, nombre anterior/nuevo).
- Permisos en `MapaPermisos`: los tres endpoints con `reglas.editar`.

## D-6 · Pantalla

- Grupos por objetivo (orden y nombres de hoy). Cabecera: nombre, "n de m" activas y un `Switch` del grupo
  (`aria-checked="mixed"` si están mezcladas; el `Switch` del sistema suma ese estado). Prenderlo prende todas las no
  eliminadas; apagarlo las apaga. Si el lote incluye protecciones, se abre la confirmación existente (motivo
  obligatorio, consecuencias listadas) antes de mandar nada.
- Fila: nombre vigente + `(código)` en gris semicondensado; menú ⋮ (Editar nombre en la fila, Eliminar con
  confirmación en la fila) e interruptor. Apagar una regla común no pide confirmación; apagar una protección sí (la de
  hoy). Sin parámetros: sin flecha y sin acción al hacer clic.
- Parámetros: campos con unidad, rango debajo, borde de acento si están editados. El borrador y la `SaveBar` se
  conservan (guardado en lote, todo o nada); el punto naranja marca la fila con cambios.
- Al pie, si hay eliminadas: "n reglas eliminadas · Ver" despliega su lista con "Restaurar".
- Filas de ejecución (consumidores sin interruptor) en su grupo, sin `Switch` ni menú.
- `?regla=` despliega y lleva a esa fila (lo usa el Inspector).
- Inspector: ruta `/reglas/inspector` (misma pantalla de hoy) y entrada en Configuración; `/reglas?tab=inspector…`
  redirige conservando `sector`.
- El modo `mock` refleja el nuevo DTO (fixture regenerado).

## D-7 · Migración

`resources/migracion-parametros-por-regla.sql`: por cada fila de `parametro_regla`, inserta en `regla_parametro` una
copia para cada pieza que declara esa clave (la lista se escribe en el script, generada desde el catálogo al momento
del cambio) y después `DROP TABLE parametro_regla`. Sin correr el script, las copias quedan en fábrica y el arranque
registra un `WARN` si encuentra la tabla vieja con filas.

## Riesgos

- Divergencia agronómica: R-01 puede regar con 45 % mientras R-03 considera "necesita riego" con 50 %. Es lo pedido;
  la fila muestra el valor de cada regla, así que la diferencia es visible.
- El simulador arma telemetría con umbrales: debe leer la copia de la regla que ejercita su escenario.
