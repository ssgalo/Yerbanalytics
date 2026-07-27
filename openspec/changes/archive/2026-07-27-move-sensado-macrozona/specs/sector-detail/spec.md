# Spec: sector-detail (delta)

## REMOVED Requirements

### Requirement: Métricas y tendencias

**Reason**: Las métricas provienen de un único nodo sensor testigo por macro-zona, por lo
que son idénticas para los 100 sectores de esa zona. Mostrarlas en el detalle de sector
sugiere falsamente que cada sector tiene instrumentación propia — y cuando se conecte el
sensado real, los 100 sectores mostrarían exactamente el mismo número.

**Migration**: El sensado pasa a la vista de macro-zona con alcance ampliado: diez métricas
en lugar de cinco, e histórico navegable para cualquiera de ellas en lugar de sólo humedad
de sustrato. Ver la capacidad `sensado-macrozona`. El detalle de sector conserva una
referencia a su macro-zona con acceso directo a ese panel.

## ADDED Requirements

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
