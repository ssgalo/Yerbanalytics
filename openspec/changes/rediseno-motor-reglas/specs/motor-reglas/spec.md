## ADDED Requirements

### Requirement: Parámetros propios por pieza
Cada pieza del motor que lee parámetros SHALL tener su propia copia de cada parámetro que declara. Editar la copia de
una pieza MUST NOT cambiar el valor que lee otra pieza. Una copia sin editar SHALL valer el valor de fábrica.

#### Scenario: Editar el umbral de una regla
- **WHEN** el agrónomo cambia `riego.umbral-humedad` de R-01 a 50 %
- **THEN** R-01 evalúa con 50 % y R-03, R-05 y R-06 siguen evaluando con su propio valor

#### Scenario: Guardado en lote por copia
- **WHEN** se envía `PUT /api/rules/parametros` con `[{regla: "RiegoPorDeficitRule", clave: "riego.umbral-humedad", valor: "50"}]`
- **THEN** sólo la copia de esa pieza cambia y queda en el historial

#### Scenario: Pieza o clave no declarada
- **WHEN** el cambio nombra una clave que la pieza no declara
- **THEN** la respuesta es `400` y no se guarda nada del lote

### Requirement: Revalidación con la copia de quien decidió
Un servicio que ejecuta o revalida la decisión de una regla SHALL leer la copia de esa regla para los parámetros de
esa decisión.

#### Scenario: Despacho y lluvia
- **WHEN** R-03 tiene probabilidad de lluvia 60 % y el despacho revalida un riego encolado
- **THEN** el despacho pospone con el mismo 60 %

### Requirement: Nombre propio de una pieza
Una pieza con interruptor SHALL poder tener un nombre propio sin perder el de fábrica, y SHALL poder restablecerlo.

#### Scenario: Renombrar
- **WHEN** se envía `PUT /api/rules/RiegoPorDeficitRule/etiqueta {"etiqueta": "Riego normal"}`
- **THEN** el catálogo devuelve `label: "Riego normal"` y `etiquetaFabrica: "Riego por déficit hídrico"`

#### Scenario: Restablecer
- **WHEN** se envía la etiqueta vacía
- **THEN** `label` vuelve al nombre de fábrica

### Requirement: Eliminar y restaurar una pieza
Eliminar una pieza SHALL apagarla y marcarla eliminada; una pieza eliminada MUST NOT correr ni poder prenderse hasta
restaurarla. Restaurar SHALL dejarla apagada. Una protección MUST NOT poder eliminarse.

#### Scenario: Eliminar
- **WHEN** se envía `DELETE /api/rules/FueraDeVentanaRiegoRule`
- **THEN** la pieza queda `activa: false`, `eliminada: true` y el motor la omite

#### Scenario: Prender una eliminada
- **WHEN** se intenta prender una pieza eliminada
- **THEN** la respuesta es `409`

#### Scenario: Eliminar una protección
- **WHEN** se envía `DELETE /api/rules/ManualLockRule`
- **THEN** la respuesta es `409` y nada cambia

#### Scenario: Restaurar
- **WHEN** se envía `POST /api/rules/FueraDeVentanaRiegoRule/restaurar`
- **THEN** la pieza vuelve a la vista, apagada
