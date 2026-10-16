## ADDED Requirements

### Requirement: Criterio agronómico del diagnóstico
El backend SHALL persistir el criterio agronómico con que se elige el diagnóstico: para cada estado
anómalo que emite el modelo (Clorosis, Estrés solar, Plaga foliar, Daño fúngico), sus bandas de
severidad en cantidad de hojas por sector (mínimo, media desde, alta desde), y un orden de
criticidad total entre esos estados. Si no hay criterio guardado, SHALL sembrarse con los valores
por defecto: Daño fúngico 2/4/8, Plaga foliar 2/5/10, Clorosis 5/10/20, Estrés solar 5/10/20, y
criticidad Daño fúngico > Plaga foliar > Clorosis > Estrés solar.

#### Scenario: Base sin criterio
- **WHEN** el backend arranca sin criterio de diagnóstico guardado
- **THEN** lo siembra con los valores por defecto

#### Scenario: Criterio ya editado
- **WHEN** el backend arranca con un criterio editado por el agrónomo
- **THEN** conserva el criterio editado

### Requirement: Consulta y edición del criterio
El backend SHALL exponer `GET /api/configuracion/diagnostico`, con el permiso `diagnosticos.ver`, y
`PUT /api/configuracion/diagnostico`, con el permiso `configuracion.editar`. El `PUT` SHALL
reemplazar el criterio completo.

#### Scenario: La cuenta de servicio lee el criterio
- **WHEN** el servicio de inferencia, con su cuenta de rol Servicio, consulta el criterio
- **THEN** recibe las bandas de severidad y el orden de criticidad vigentes

#### Scenario: Edición sin permiso
- **WHEN** un usuario sin `configuracion.editar` intenta guardar el criterio
- **THEN** el backend responde `403` y el criterio no cambia

### Requirement: Validación del criterio
El backend SHALL rechazar con `400`, y un mensaje apto para mostrar, un criterio en el que las bandas
no sean enteros con `1 ≤ mínimo < media < alta`, en el que falte o sobre algún estado anómalo, o en
el que el orden de criticidad no sea una permutación de los cuatro estados anómalos.

#### Scenario: Bandas no crecientes
- **WHEN** se guarda Clorosis con mínimo 10 y media 5
- **THEN** el backend responde `400` y el criterio no cambia

#### Scenario: Criticidad incompleta
- **WHEN** se guarda un orden de criticidad que repite un estado u omite otro
- **THEN** el backend responde `400` y el criterio no cambia

### Requirement: Cambio de criterio en el historial
Cada cambio guardado del criterio de diagnóstico SHALL registrarse en el historial como una
recalibración, con el usuario que la hizo.

#### Scenario: Recalibración registrada
- **WHEN** el agrónomo guarda un criterio nuevo
- **THEN** el historial registra la recalibración del criterio de diagnóstico con su usuario y hora

### Requirement: Vista Configuración → Criterio de diagnóstico
El dashboard SHALL ofrecer, dentro del grupo Configuración, la vista *Criterio de diagnóstico* para
consultar y editar el criterio: una tabla de bandas de severidad por estado anómalo y el orden de
criticidad, reordenable con controles accesibles por teclado. La vista SHALL validar en cliente las
mismas reglas que el backend, mostrar la barra de guardado con la cantidad de cambios y permitir
descartar o restablecer los valores por defecto. La vista SHALL aclarar que los umbrales de confianza
por clase son del equipo y no se editan desde el dashboard. La vista SHALL requerir
`configuracion.ver` y `diagnosticos.ver`; sin `configuracion.editar`, SHALL mostrarse de sólo lectura.

#### Scenario: Editar una banda
- **WHEN** el agrónomo cambia la severidad media de Daño fúngico de 4 a 3 y guarda
- **THEN** el criterio queda guardado y la barra de guardado deja de contar cambios

#### Scenario: Reordenar la criticidad
- **WHEN** el agrónomo sube Clorosis por encima de Plaga foliar y guarda
- **THEN** el orden guardado es Daño fúngico > Clorosis > Plaga foliar > Estrés solar

#### Scenario: Valor inválido
- **WHEN** el agrónomo deja un mínimo mayor o igual que su "media desde"
- **THEN** la vista marca el campo, explica el problema y no permite guardar

#### Scenario: Usuario de sólo lectura
- **WHEN** un usuario con `configuracion.ver` y `diagnosticos.ver` pero sin `configuracion.editar`
  abre la vista
- **THEN** ve el criterio vigente sin poder editarlo

#### Scenario: Modo mock
- **WHEN** `VITE_DATA_SOURCE=mock`
- **THEN** la vista muestra el criterio por defecto del mock determinístico
