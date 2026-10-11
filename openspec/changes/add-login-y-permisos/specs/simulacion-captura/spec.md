## MODIFIED Requirements

### Requirement: El simulador no tiene superficie de API propia
El panel de cámara SHALL operar exclusivamente con endpoints que la plataforma expone para sus
propios emisores: el de emisión de órdenes, el de consulta de órdenes, el de servido de
imágenes y el de alta de diagnósticos. NO SHALL existir ningún endpoint, parámetro ni rama de
código en el backend que exista para servir al simulador.

El simulador SHALL autenticarse ante el backend como cualquier otro cliente: su servidor inicia
sesión con una cuenta de rol Servicio cuyas credenciales viven en el entorno del propio
simulador, adjunta esa sesión a lo que reenvía su proxy `/backend/**` y vuelve a iniciar sesión
cuando el backend responde `401`. El backend NO SHALL tener usuarios, roles ni permisos creados
para el simulador: la cuenta la da de alta el Administrador como una cuenta de servicio más.

#### Scenario: Superficie exclusiva vacía
- **WHEN** se audita la API que consume el panel de cámara
- **THEN** ningún endpoint es exclusivo del simulador

#### Scenario: El backend no conoce al simulador
- **WHEN** se revisan los servicios de captura y de diagnóstico del backend
- **THEN** ninguno contiene lógica condicionada a que el llamante sea el simulador

#### Scenario: El backend no depende del simulador
- **WHEN** la app de simulación no se ejecuta
- **THEN** las órdenes de captura, la recepción de imágenes y el dashboard funcionan igual

#### Scenario: Cuenta de servicio
- **WHEN** el simulador arranca con usuario y contraseña de una cuenta de rol Servicio en su `.env`
- **THEN** emite órdenes y carga diagnósticos a través del proxy con esa sesión

#### Scenario: Sesión vencida del simulador
- **WHEN** el backend responde `401` a una petición reenviada por el proxy
- **THEN** el servidor del simulador vuelve a iniciar sesión y reintenta una única vez

#### Scenario: Sin credenciales configuradas
- **WHEN** el simulador arranca sin credenciales de servicio
- **THEN** su UI informa que el backend rechaza las peticiones por falta de credenciales, y la publicación de telemetría por MQTT sigue funcionando
