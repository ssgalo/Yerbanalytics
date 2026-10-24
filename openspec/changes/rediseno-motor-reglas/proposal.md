# Change: rediseno-motor-reglas

> Mockup aprobado: https://claude.ai/artifact/HKAbBZwCFta11EH9jT5Xe7 (versión 2).
> Toca el catálogo de parámetros del motor (`engine/parametros/`), el estado operativo de las piezas
> (`engine/overrides/`), la vista Motor de reglas del dashboard y la lectura de parámetros del simulador.

## Why

La pestaña Reglas de Motor de reglas creció a tarjetas con chips, filtros, orden, tarjetas de "Ejecución" y una
sección de parámetros compartidos: para prender una regla o cambiar un umbral hay que leer demasiado. Además, un
parámetro del catálogo es **uno solo** aunque lo lean varias reglas (`riego.umbral-humedad` lo leen seis piezas): el
agrónomo cambia el umbral pensando en R-01 y mueve sin querer R-03, R-05 y R-06. Quiere que cada regla tenga sus
valores, poder renombrarlas a su manera y quitar de la vista las que no usa.

## What Changes

- **Parámetros propios por pieza.** El valor de un parámetro pasa a ser de `(pieza, clave)`: cada regla (y cada
  servicio que lee parámetros) tiene su copia; editarla en una no cambia las demás. La definición (tipo, rango,
  unidad, fábrica) sigue siendo una por clave, en código. **BREAKING** en `PUT /api/rules/parametros`: cada cambio
  nombra la pieza (`{regla, clave, valor}`).
- **Quien revalida una decisión lee la copia de la regla que decidió.** El despacho de riego revalida lo que
  decidieron R-02…R-06 y S-02, así que lee sus copias; el servicio del plan de nutrición lee la de N-01.
- **Nombre editable.** Cada pieza puede tener un nombre propio (`PUT /api/rules/{name}/etiqueta`); el nombre de
  fábrica no se pierde y se puede restablecer. La UI muestra al lado, entre paréntesis, el código de la regla en
  `reglas_v2` (o el nombre interno si no tiene).
- **Eliminar una regla.** `DELETE /api/rules/{name}` la apaga y la saca de la vista; `POST /api/rules/{name}/restaurar`
  la devuelve (apagada). Las reglas son código: eliminar no borra lógica, es una decisión operativa persistida. Las
  **protecciones no se eliminan** (sólo se apagan, con motivo, como hoy).
- **Pantalla nueva.** Grupos por objetivo en tarjetas con interruptor del grupo (prende/apaga todas; medio camino si
  están mezcladas). Cada regla es una fila: nombre, código, menú ⋮ (Editar nombre, Eliminar) e interruptor; si tiene
  parámetros, la fila se despliega y los muestra. Los servicios que ejecutan (despacho de riego, aplicación de
  insumos, tanques, pulso de sensado, historial de lecturas) son filas sin interruptor ni menú. Se quitan filtros,
  orden, chips de efecto y la sección de compartidos.
- **Inspector fuera de las pestañas.** La página deja de tener el selector Reglas / Inspector: el Inspector pasa a su
  propia ruta (`/reglas/inspector`) y entrada en Configuración. Los enlaces existentes (`?tab=inspector&sector=…`)
  redirigen.
- **Migración manual** `migracion-parametros-por-regla.sql`: copia cada override vigente a todas las piezas que leen
  esa clave (nadie pierde un valor editado) y borra la tabla vieja.

## Out of scope

- Crear reglas nuevas o duplicar (se descartó en la revisión del mockup).
- Cambiar qué parámetros lee cada regla o sus rangos.
- Restricciones cruzadas entre parámetros de piezas distintas que ya no comparten valor (ver design D-4).

## Impact

- Backend: `engine/parametros/` (snapshot por pieza, entidad y repositorio nuevos), `engine/overrides/`
  (`regla_estado` con `etiqueta` y `eliminada`), controladores de reglas, `MapaPermisos`, todos los lectores de
  `vigentes()`.
- Frontend: `features/reglas/` (pestaña Reglas reescrita), router y Sidebar (Inspector), `types/domain.ts`,
  repositorios http y mock.
- Simulador: lee el valor de la pieza que su escenario ejercita.
