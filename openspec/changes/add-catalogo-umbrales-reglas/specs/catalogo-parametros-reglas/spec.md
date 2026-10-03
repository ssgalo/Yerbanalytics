## ADDED Requirements

### Requirement: Definición única de cada parámetro de regla
El backend SHALL mantener un catálogo de parámetros de reglas donde cada parámetro tiene una clave
estable, etiqueta, descripción, familia, tipo (`NUMERO`, `ENTERO`, `HORA`, `VENTANA_HORARIA`),
unidad, valor de fábrica, rango permitido (cuando aplica), cantidad de decimales y referencia a la
especificación agronómica. Cada clave SHALL existir una sola vez. Los valores de fábrica SHALL
vivir en código y NO SHALL depender de un seed de base de datos.

#### Scenario: Clave duplicada
- **WHEN** dos definiciones del catálogo declaran la misma clave
- **THEN** la aplicación no arranca y el error nombra la clave repetida

#### Scenario: Fábrica fuera de su propio rango
- **WHEN** una definición declara un valor de fábrica fuera de su rango permitido
- **THEN** la aplicación no arranca y el error nombra el parámetro

#### Scenario: Base recién creada
- **WHEN** el backend arranca con la tabla de overrides vacía
- **THEN** todos los parámetros toman su valor de fábrica, sin correr ningún script

### Requirement: Valor vigente
El valor vigente de un parámetro SHALL ser el valor persistido para su clave si existe, o el de
fábrica si no. Todas las reglas que evalúan un mismo sector en un mismo ciclo SHALL ver el mismo
conjunto de valores vigentes. Un guardado válido SHALL tener efecto a partir de la siguiente
evaluación, sin reiniciar.

#### Scenario: Override persistido
- **WHEN** `riego.umbral-humedad` tiene fábrica 42 y un override de 40
- **THEN** las reglas leen 40

#### Scenario: Restablecer a fábrica
- **WHEN** se guarda `riego.umbral-humedad` con valor `null`
- **THEN** se borra el override y las reglas vuelven a leer 42

#### Scenario: Guardado durante un ciclo
- **WHEN** se guarda un cambio mientras el motor evalúa un sector
- **THEN** esa evaluación termina con los valores con los que empezó y la siguiente usa los nuevos

### Requirement: Tipos de valor suficientes para riego
El catálogo SHALL poder expresar, con tipo, unidad, fábrica, rango y restricciones cruzadas, todos
los parámetros de riego de `reglas_v2.md` §11: porcentajes, litros, litros por punto con dos
decimales, caudal en L/h, cantidad entera de sectores, ventanas horarias `HH:mm–HH:mm`, horas y
milímetros. Una ventana horaria con inicio posterior al fin SHALL interpretarse como que cruza la
medianoche.

#### Scenario: Ventana de riego normal
- **WHEN** se define `06:00-18:00` y se consulta si las 18:30 están dentro
- **THEN** la respuesta es que no

#### Scenario: Ventana que cruza la medianoche
- **WHEN** se define `18:00-06:00` y se consulta si las 02:00 están dentro
- **THEN** la respuesta es que sí

#### Scenario: Entero con decimales
- **WHEN** se intenta guardar 10,5 en un parámetro `ENTERO` de unidad "sectores"
- **THEN** se rechaza indicando que debe ser entero

### Requirement: Validación al guardar
El backend SHALL rechazar un guardado si algún valor no corresponde a su tipo, está fuera de su
rango, viola una restricción cruzada evaluada sobre el conjunto resultante, o refiere a una clave
inexistente. El guardado SHALL ser todo o nada.

#### Scenario: Fuera de rango
- **WHEN** se envía `riego.umbral-humedad = 70` con rango 35–60
- **THEN** responde 400 con un error asociado a esa clave y no persiste ningún cambio del lote

#### Scenario: Restricción cruzada
- **WHEN** una familia declara `crítico < umbral` y se envía un umbral menor que el crítico vigente
- **THEN** responde 400 con el mensaje de la restricción y no persiste nada

#### Scenario: Ventana mal formada
- **WHEN** se envía `25:00-18:00` en un parámetro `VENTANA_HORARIA`
- **THEN** responde 400 indicando el formato esperado `HH:mm-HH:mm`

### Requirement: La regla declara los parámetros que usa
Cada regla SHALL declarar la lista de parámetros del catálogo que usa. Una regla SHALL poder leer
sólo los parámetros que declaró. Un parámetro usado por varias reglas SHALL ser la misma
definición y el mismo valor para todas.

#### Scenario: Regla que declara una clave inexistente
- **WHEN** una regla declara un parámetro que no está en el catálogo
- **THEN** la aplicación no arranca y el error nombra la regla y la clave

#### Scenario: Regla que lee un parámetro no declarado
- **WHEN** una regla intenta leer un parámetro que no figura en su declaración
- **THEN** la evaluación falla con un error que nombra la regla y el parámetro

#### Scenario: Parámetro compartido
- **WHEN** dos reglas declaran el mismo parámetro y se le guarda un valor nuevo
- **THEN** las dos reglas leen el valor nuevo en su siguiente evaluación

### Requirement: Ningún umbral de regla fuera del catálogo
Toda magnitud contra la que una regla compara para decidir SHALL ser un parámetro del catálogo o
SHALL quedar registrada en la traza como condición no configurable. Las reglas NO SHALL leer
umbrales de propiedades de la aplicación, de `configuracion_operativa` ni de las bandas de
`umbral_metrica`. Se exceptúa la fecha de siembra de `ShadingRule`, que no es un umbral.

#### Scenario: Umbral de riego independiente de la banda de estado
- **WHEN** se cambia la banda ideal mínima de humedad de sustrato en la configuración de métricas
- **THEN** el umbral con el que `IrrigationRule` decide regar no cambia

#### Scenario: Confianza mínima única
- **WHEN** se cambia `diagnostico.confianza-minima`
- **THEN** cambian a la vez el umbral de `SupplyRule` y la marca de diagnóstico concluyente

#### Scenario: Comportamiento preservado
- **WHEN** se evalúa cualquiera de las reglas existentes con el catálogo en valores de fábrica
- **THEN** produce las mismas acciones que antes de este cambio para las mismas entradas

### Requirement: API del catálogo
El backend SHALL exponer `GET /api/rules/parametros` con las reglas (id, etiqueta, rama, prioridad y
claves de sus parámetros) y los parámetros (definición, valor vigente, fábrica, si está
modificado, auditoría y lista de reglas que lo usan), cada parámetro una sola vez. SHALL exponer
`PUT /api/rules/parametros` con un lote de cambios `{clave, valor}` donde `valor = null` restablece
fábrica, y SHALL devolver el catálogo actualizado.

#### Scenario: Catálogo normalizado
- **WHEN** se consulta `GET /api/rules/parametros`
- **THEN** cada clave aparece una sola vez en `parametros` y su `usadoPor` lista todas las reglas
  que la declaran

#### Scenario: Esquema del DAG con parámetros
- **WHEN** se consulta `GET /api/rules/schema`
- **THEN** cada nodo de regla incluye las claves de los parámetros que declara, y los nodos
  especiales una lista vacía

### Requirement: Auditoría del cambio de parámetros
Cada guardado válido SHALL registrar usuario y timestamp por parámetro modificado y SHALL asentar un
evento "Configuración" en el historial con las claves cambiadas.

#### Scenario: Guardado auditado
- **WHEN** se guarda un lote válido con el encabezado `X-Usuario: Ana`
- **THEN** cada parámetro cambiado muestra `updatedBy = Ana` y su fecha, y el historial tiene un
  evento "Configuración" que nombra esas claves
