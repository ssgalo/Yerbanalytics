## ADDED Requirements

### Requirement: Reglas agrupadas en filas
La vista Motor de reglas SHALL mostrar las piezas agrupadas por objetivo, una fila por pieza con su nombre vigente, su
código (o nombre interno) entre paréntesis, un menú de acciones y su interruptor. No SHALL tener selector de pestañas.

#### Scenario: Fila de una regla
- **WHEN** se abre `/reglas`
- **THEN** la fila de R-01 dice "Riego por déficit hídrico (R-01)" y tiene menú ⋮ e interruptor

#### Scenario: Fila de ejecución
- **WHEN** el grupo Riego incluye el despacho de riego
- **THEN** su fila no tiene interruptor ni menú

### Requirement: Interruptor del grupo
Cada grupo SHALL tener un interruptor que prende o apaga todas sus piezas no eliminadas, y que se muestra a medio
camino cuando hay piezas prendidas y apagadas.

#### Scenario: Apagar un grupo sin protecciones
- **WHEN** se apaga el interruptor de Riego
- **THEN** todas sus reglas quedan apagadas sin pedir confirmación

#### Scenario: Grupo con protecciones
- **WHEN** se apaga un grupo que incluye protecciones
- **THEN** se pide confirmar con motivo antes de apagar nada

### Requirement: Fila desplegable con parámetros propios
Hacer clic en una fila con parámetros SHALL desplegar sus campos; una fila sin parámetros MUST NOT tener flecha ni
reaccionar al clic. Las ediciones se guardan en lote con la barra de guardado.

#### Scenario: Desplegar
- **WHEN** se hace clic en "Riego por déficit hídrico"
- **THEN** se ven sus parámetros con unidad y rango

#### Scenario: Sin parámetros
- **WHEN** se hace clic en una regla sin parámetros
- **THEN** no pasa nada

### Requirement: Menú de la regla
El menú ⋮ SHALL ofrecer Editar nombre (en la misma fila) y Eliminar (con confirmación en la fila). Una protección
SHALL ofrecer sólo Editar nombre. Las eliminadas SHALL poder restaurarse desde el pie de la vista.

#### Scenario: Eliminar
- **WHEN** se elige Eliminar y se confirma
- **THEN** la fila desaparece y el pie dice "1 regla eliminada"

### Requirement: Inspector en su propia ruta
El Inspector SHALL vivir en `/reglas/inspector`, con entrada en Configuración; `/reglas?tab=inspector` SHALL redirigir.

#### Scenario: Enlace viejo
- **WHEN** se abre `/reglas?tab=inspector&sector=S-001`
- **THEN** se muestra el Inspector de S-001
