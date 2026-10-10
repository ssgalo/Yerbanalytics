## MODIFIED Requirements

### Requirement: Autenticación de todas las rutas de cámara y captura
El backend SHALL exigir un token de acceso válido de dispositivo en todas las rutas del contrato
de cámara (`/api/camara/v1/**`), excepto en las de vinculación y enrolamiento. Las rutas de
plataforma de cámara y captura (generar código de vinculación, listar y revocar dispositivos,
emitir y consultar órdenes, servir imágenes, listar capturas pendientes) SHALL exigir, en cambio,
una sesión de usuario con el permiso que corresponda (`camara.gestionar`, `capturas.ordenar`,
`capturas.ver`), igual que el resto de la API de plataforma. El token de dispositivo NO SHALL dar
acceso a rutas de plataforma, y una sesión de usuario NO SHALL dar acceso al contrato.

#### Scenario: Petición sin token
- **WHEN** se llama a una ruta del contrato de cámara sin token
- **THEN** el backend responde `401`

#### Scenario: Petición con token vencido
- **WHEN** se llama a una ruta del contrato de cámara con un token expirado
- **THEN** el backend responde `401` y el cliente renueva el token antes de reintentar

#### Scenario: La API de plataforma exige sesión de usuario
- **WHEN** se llama a `POST /api/camara/vinculacion` o a `GET /api/capturas/{id}/imagen` sin sesión de usuario
- **THEN** el backend responde `401`

#### Scenario: Permiso de plataforma
- **WHEN** un usuario sin `camara.gestionar` llama a `DELETE /api/camara/dispositivos/{id}`
- **THEN** el backend responde `403` y el dispositivo sigue habilitado

#### Scenario: Las credenciales no se cruzan
- **WHEN** un dispositivo usa su token de cámara contra `GET /api/nursery`
- **THEN** el backend responde `401`

#### Scenario: El contrato no cambia para los clientes
- **WHEN** la PWA o la app Android operan con el contrato v1
- **THEN** funcionan sin modificaciones y la suite de conformidad pasa sin cambios
