## MODIFIED Requirements

### Requirement: Edición de umbrales de métricas
La vista SHALL permitir editar, por cada métrica, el ideal (desde/hasta), el crítico (debajo
de/arriba de) y un punto de alerta en cada franja amarilla, sobre una barra que muestra las zonas
roja, amarilla y verde a escala del rango fisiológico, con manijas arrastrables y operables por
teclado y con los seis valores como campos numéricos. El rango fisiológico SHALL NOT editarse desde
esta vista.

#### Scenario: Editar una banda
- **WHEN** el usuario modifica un valor de banda de una métrica (campo o manija)
- **THEN** la barra y el campo reflejan el nuevo valor y se incluye al guardar

#### Scenario: Arrastrar no rompe el orden
- **WHEN** el usuario arrastra una manija más allá de su vecina
- **THEN** la manija se detiene en el valor de la vecina

#### Scenario: Punto de alerta fuera de su franja
- **WHEN** el usuario tipea un punto de alerta fuera de `[crítico, ideal]` de su lado
- **THEN** la vista marca la métrica con error de coherencia y bloquea el guardado
