## ADDED Requirements

### Requirement: Registro de cambios de seguridad
El sistema SHALL asentar en el registro de auditoría, en la misma transacción que el cambio,
toda alta, edición, cambio de rol, suspensión, reactivación, baja y blanqueo de contraseña de un
usuario, todo cambio en la matriz de permisos y todo cambio del tiempo máximo de inactividad.
Cada registro SHALL indicar quién lo hizo (usuario autor, o "sistema" para el Administrador
inicial y la siembra), sobre qué usuario o rol, el tipo de cambio, el detalle (valores anterior y
nuevo; nunca contraseñas ni hashes) y el timestamp exacto en UTC con precisión de milisegundos.
(HU-20 CA-03)

#### Scenario: Cambio de rol auditado
- **WHEN** el Administrador "admin" cambia el rol de "jperez" de Operario a Productor Viverista
- **THEN** existe un registro con autor "admin", objetivo "jperez", tipo `USUARIO_ROL_CAMBIADO`, rol anterior y nuevo, y el timestamp

#### Scenario: Cambio rechazado no se audita
- **WHEN** un cambio sobre un usuario falla por validación
- **THEN** no se agrega ningún registro

#### Scenario: Falla de auditoría revierte el cambio
- **WHEN** no se puede escribir el registro de auditoría
- **THEN** el cambio sobre el usuario tampoco se aplica

### Requirement: Inalterabilidad del registro
El registro de auditoría SHALL ser append-only. La API NO SHALL exponer ninguna operación para
modificarlo o borrarlo. La base SHALL rechazar `UPDATE` y `DELETE` sobre su tabla. Cada registro
SHALL guardar el hash SHA-256 de su contenido encadenado con el del registro anterior, para que
cualquier alteración hecha por fuera del sistema sea detectable.

#### Scenario: Intento de modificación en la base
- **WHEN** se ejecuta un `UPDATE` o un `DELETE` sobre la tabla de auditoría
- **THEN** la base lo rechaza con error

#### Scenario: Verificación de la cadena
- **WHEN** el Administrador consulta la integridad del registro y nadie lo alteró
- **THEN** el sistema informa la cadena íntegra y la cantidad de registros verificados

#### Scenario: Alteración detectada
- **WHEN** alguien con acceso directo a la base desactivó la protección y cambió un registro
- **THEN** la verificación informa la cadena rota y el primer registro que no coincide

### Requirement: Consulta de la auditoría
Quien tenga `auditoria.ver` SHALL poder consultar el registro, del más reciente al más antiguo,
paginado y filtrable por autor, usuario objetivo, tipo de cambio y rango de fechas.

#### Scenario: Filtro por usuario
- **WHEN** el Administrador filtra la auditoría por el usuario "jperez"
- **THEN** ve sólo los registros cuyo objetivo es "jperez", del más reciente al más antiguo

#### Scenario: Sin permiso
- **WHEN** un Operario llama a `GET /api/auditoria`
- **THEN** el backend responde `403`
