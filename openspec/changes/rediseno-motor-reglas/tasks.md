# Tasks: rediseno-motor-reglas

## 1. Backend — parámetros por pieza
- [x] 1.1 Entidad `ReglaParametroEntity` (`regla_parametro`, PK `consumidor`+`clave`) y repositorio; quitar `ParametroReglaEntity`.
- [x] 1.2 `ParametrosPorPieza` (snapshot con `de(consumidor)`); `CatalogoParametrosService.vigentes()` lo devuelve; overlay de antigüedad por vista (D-1).
- [x] 1.3 `ConsumidorParametros.parametrosDe()` + validación al arrancar; `DeclaredParameters` resuelve en la vista de la dueña (D-2); `DespachoRiego` y `NutritionPlanService` lo declaran.
- [x] 1.4 `Evaluacion`, `RuleOrchestrator`, `ZoneRuleEngine` y los lectores directos usan su vista.
- [x] 1.5 Restricciones cruzadas por vista (D-4); errores por `(regla, clave)`.
- [x] 1.6 `CambioParametro(regla, clave, valor)`, `ParametroDto` con `regla` y sin `usadoPor` (D-3); historial por copia.
- [x] 1.7 Tests: editar una copia no cambia otra; despacho lee la copia de R-03; restricciones; catálogo por copia.

## 2. Backend — nombre y eliminación
- [x] 2.1 `regla_estado.etiqueta` y `eliminada`; `ReglaCatalogoDto.etiquetaFabrica`, `eliminada`, `label` vigente.
- [x] 2.2 `PUT /api/rules/{name}/etiqueta`, `DELETE /api/rules/{name}`, `POST /api/rules/{name}/restaurar` (D-5), en `MapaPermisos` con `reglas.editar`.
- [x] 2.3 Eliminada ⇒ inactiva para el orquestador; prender una eliminada `409`; eliminar una protección `409`.
- [x] 2.4 Tests de los tres endpoints y de cobertura de permisos.

## 3. Migración
- [x] 3.1 `resources/migracion-parametros-por-regla.sql` (D-7) y `WARN` al arrancar si queda `parametro_regla` con filas.
- [x] 3.2 CLAUDE.md (lista de migraciones) y README del backend. (README hecho; falta CLAUDE.md)

## 4. Frontend
- [x] 4.1 Tipos (`domain.ts`), repositorio http y mock (fixture) con el DTO nuevo y los endpoints de etiqueta/eliminar/restaurar.
- [x] 4.2 `Switch` con estado `mixed` (sistema de diseño + catálogo `/sistema-diseno`).
- [x] 4.3 Pestaña Reglas reescrita según D-6: grupos, interruptor del grupo, filas desplegables, menú ⋮, renombrar en fila, eliminar con confirmación, pie de eliminadas, filas de ejecución.
- [x] 4.4 Borrador por `(regla, clave)`; `SaveBar`; quitar filtros, orden, compartidos y `EjecucionCard`.
- [x] 4.5 Inspector en `/reglas/inspector` + Sidebar + redirección de `?tab=inspector`; "Editar parámetro" del Inspector lleva a `?regla=`.
- [x] 4.6 Tests de la vista (grupo mixto, fila sin parámetros, renombrar, eliminar/restaurar, guardado por copia); `npm run lint`, `test`, `build`.

## 5. Simulador
- [x] 5.1 Leer el valor de la copia de la regla que ejercita cada escenario (`ParametroRegla.regla`).

## 6. Cierre
- [x] 6.1 CLAUDE.md §4/§6 al día (parámetros por pieza, Inspector, endpoints).
