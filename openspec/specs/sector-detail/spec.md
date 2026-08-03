# sector-detail

## Purpose

Detalle de un sector: diagnostico de IA del plantin, actuadores, seguimiento e historial.
## Requirements
### Requirement: Diagnóstico de IA del sector
El detalle de sector SHALL mostrar el diagnóstico (estado, severidad, imagen
cenital, confianza con barra) y un aviso cuando la confianza supera el umbral (85%).

#### Scenario: Diagnóstico concluyente
- **WHEN** la confianza del modelo es ≥ 85%
- **THEN** se muestra el aviso de que el motor de reglas puede actuar

### Requirement: Estado de actuadores
El detalle SHALL mostrar el estado de electroválvula (riego), bomba peristáltica
(insumo) y mediasombra, con indicador de actividad.

#### Scenario: Actuador activo
- **WHEN** un actuador está operando (p. ej. electroválvula regando)
- **THEN** su fila muestra el estado y un indicador de actividad encendido

### Requirement: Seguimiento post-acción e historial
El detalle SHALL mostrar el seguimiento antes/ahora cuando hubo acciones, y el
historial inalterable de acciones del sector (lectura → decisión → acción).

#### Scenario: Sector sano sin seguimiento
- **WHEN** el sector está sano (sin acciones recientes)
- **THEN** se muestra el mensaje de que opera dentro de parámetros

### Requirement: Navegación de retorno
El detalle SHALL ofrecer un botón "Volver" que regresa a la vista previa.

#### Scenario: Volver a la vista anterior
- **WHEN** el usuario hace clic en "Volver"
- **THEN** regresa a la vista desde la que abrió el sector

### Requirement: El detalle de sector muestra sólo lo que es propio del sector

El detalle SHALL limitarse a la información que efectivamente pertenece al sector:
diagnóstico de IA del plantín, estado de sus actuadores, seguimiento post-acción e
historial de acciones. NO SHALL mostrar valores sensados, que son de alcance macro-zona.

#### Scenario: Composición del detalle

- **WHEN** el usuario abre el detalle de un sector
- **THEN** ve diagnóstico, actuadores, seguimiento post-acción e historial
- **AND** no ve tiles de métricas ni gráfico de series sensadas

#### Scenario: Un sector crítico sigue siendo explicable

- **WHEN** el usuario abre un sector en estado crítico
- **THEN** el detalle muestra el motivo de ese estado y permite volver a su macro-zona,
  donde están las condiciones ambientales que lo explican

### Requirement: Captura cenital a tamaño completo

La miniatura de la captura del diagnóstico SHALL abrirse a tamaño completo al hacer clic,
para que el usuario pueda revisar el plantín que el modelo diagnosticó. La vista ampliada
SHALL cerrarse con la tecla Escape, con su botón de cierre y haciendo clic fuera de la
imagen.

#### Scenario: Ampliar la captura

- **WHEN** el usuario hace clic en la miniatura del diagnóstico
- **THEN** la captura se muestra a tamaño completo, identificada con el sector, el estado
  diagnosticado y la antigüedad de la lectura

#### Scenario: Cerrar la vista ampliada

- **WHEN** el usuario presiona Escape, usa el botón de cierre o hace clic fuera de la imagen
- **THEN** vuelve al detalle del sector

