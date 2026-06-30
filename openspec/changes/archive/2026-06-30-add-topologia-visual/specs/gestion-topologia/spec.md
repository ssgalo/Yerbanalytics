# Spec: gestion-topologia

## ADDED Requirements

### Requirement: Preview interactivo de la topología
El frontend SHALL mostrar en la vista de topología un preview que replica el aspecto del panel
general usando los valores actuales del formulario (cantidad de macro-zonas, sectores por
macro-zona y disposición por fila), para que el Administrador vea cómo quedará la grilla antes
de aplicarla.

#### Scenario: El preview refleja los valores del formulario
- **WHEN** el Administrador cambia la cantidad de macro-zonas, de sectores por macro-zona o la
  disposición por fila
- **THEN** el preview se actualiza mostrando esa misma distribución (macro-zonas y mini-heatmaps)

### Requirement: Edición de la disposición por fila
El frontend SHALL permitir al Administrador definir cuántas macro-zonas se muestran por fila y
cuántos sectores por fila dentro de cada macro-zona, tanto arrastrando un control lateral del
preview (el redimensionado es horizontal) como mediante inputs numéricos, manteniendo ambos
sincronizados. El ajuste por arrastre SHALL modificar únicamente la disposición por fila, sin
alterar las cantidades de macro-zonas ni de sectores.

#### Scenario: Ajustar la disposición arrastrando
- **WHEN** el Administrador arrastra el control lateral del preview
- **THEN** la cantidad de macro-zonas o de sectores por fila cambia según el arrastre horizontal
- **AND** las cantidades totales de macro-zonas y sectores no cambian
- **AND** el input numérico correspondiente refleja el nuevo valor

#### Scenario: Disposición acotada por las cantidades
- **WHEN** el Administrador intenta fijar una disposición por fila mayor a la cantidad
  disponible (más macro-zonas por fila que macro-zonas, o más sectores por fila que sectores
  por macro-zona)
- **THEN** el frontend acota el valor al máximo válido

### Requirement: Guardado unificado de topología y disposición
El frontend SHALL ofrecer una única vista que reúne las cantidades de la grilla (macro-zonas y
sectores por macro-zona) y la disposición por fila, con una sola acción de guardado. La acción
SHALL regenerar la grilla (operación destructiva, con confirmación previa) únicamente cuando
cambian las cantidades; cuando solo cambia la disposición por fila, SHALL guardar la
presentación sin regenerar la grilla ni descartar dispositivos o historial.

#### Scenario: Guardar solo la disposición
- **WHEN** el Administrador modifica únicamente la disposición por fila y guarda
- **THEN** el frontend persiste la disposición mediante el repositorio
- **AND** no muestra la advertencia de regeneración ni reemplaza la grilla existente

#### Scenario: Cambiar las cantidades exige confirmación
- **WHEN** el Administrador modifica la cantidad de macro-zonas o de sectores por macro-zona de
  una topología ya cargada y guarda
- **THEN** el frontend muestra la advertencia de que se reemplaza la grilla y se descartan los
  dispositivos e historial, y pide una confirmación explícita antes de regenerar

## MODIFIED Requirements

### Requirement: Resumen de topología actual
El frontend SHALL mostrar la topología actual (cantidad de macro-zonas y total de sectores) en
el encabezado de la vista, e inicializar los controles de la vista con los valores vigentes de
la grilla y de la disposición por fila (HU-18 CA-01).

#### Scenario: Vista del resumen
- **WHEN** el Administrador abre la vista de topología
- **THEN** el encabezado muestra la cantidad de macro-zonas y el total de sectores de la
  topología actual
- **AND** los controles de cantidades y de disposición por fila reflejan los valores vigentes
