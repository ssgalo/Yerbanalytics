## 1. Backend — dominio y persistencia

- [ ] 1.1 Agregar `spring-boot-starter-security` al `pom.xml` y verificar que el proyecto compila y los tests existentes corren (todavía sin cadenas propias, con la seguridad por defecto desactivada en un `SecurityFilterChain` provisorio `permitAll`)
- [ ] 1.2 Crear los `enum` `Rol` (5 roles) y `Permiso` (catálogo con código, grupo, descripción y par de lectura) en un paquete `seguridad/`
- [ ] 1.3 Entidades y repositorios `Usuario` (con `@Version`), `Sesion`, `RolPermiso`, `PoliticaSesion` según design D6
- [ ] 1.4 Entidad `AuditoriaSeguridad` `@Immutable` con columnas `updatable=false` y repositorio sólo de alta y consulta (D7)
- [ ] 1.5 `ApplicationRunner` que siembra la matriz por defecto si `rol_permiso` está vacía y la política de sesión si no existe (audita `MATRIZ_SEMBRADA` con autor sistema)
- [ ] 1.6 `ApplicationRunner` que instala, de forma idempotente, los triggers que rechazan `UPDATE`/`DELETE`/`TRUNCATE` sobre `auditoria_seguridad`; dejar el mismo SQL en `resources/auditoria-triggers.sql` para correrlo a mano si el usuario de la base no puede crear funciones
- [ ] 1.7 Administrador inicial (D9): propiedades `yerbanalytics.auth.admin-inicial.*`, creación si no hay Administrador activo, clave aleatoria logueada una vez si falta, auditado `ADMIN_INICIAL_CREADO`

## 2. Backend — sesiones y autenticación (HU-01)

- [ ] 2.1 `SesionService`: crear sesión (id aleatorio de 256 bits, guardar SHA-256), resolver por cookie, verificar inactividad contra la política, registrar actividad con umbral de 15 s, cerrar (`LOGOUT`), revocar por usuario o por rol con exclusión de la sesión del autor
- [ ] 2.2 `AuthService.login`: comparación BCrypt, hash de relleno para usuario inexistente, estado evaluado después de la clave, respuesta única `401` (D8); actualiza `ultimo_ingreso`
- [ ] 2.3 `AuthController`: `POST /api/auth/login` (cookie `YERBA_SESION` `HttpOnly; SameSite=Strict; Path=/api`, `Secure` por propiedad), `GET /api/auth/perfil`, `POST /api/auth/actividad`, `POST /api/auth/logout`, `PUT /api/auth/clave`
- [ ] 2.4 Validación de contraseñas (≥ 8, distinta del username) y cambio propio que cierra las demás sesiones del usuario
- [ ] 2.5 Barrido diario que borra sesiones cerradas o vencidas hace más de 7 días
- [ ] 2.6 Tests: login válido, inválido, inexistente, suspendido y baja con respuesta idéntica; sesión que vence por inactividad; `GET` que no refresca actividad y `POST /actividad` que sí; logout invalida la cookie

## 3. Backend — cadenas de seguridad y permisos

- [ ] 3.1 Cadena `@Order(1)` para `/api/camara/v1/**` con la lógica de `CamaraAuthFilter` convertida en filtro de la cadena (sin cambio de comportamiento); eliminar el `FilterRegistrationBean` anterior
- [ ] 3.2 Filtro de sesión de la cadena `@Order(2)`: arma el `Authentication` con los permisos efectivos (caché de la matriz invalidada al guardar), responde `401` con `motivo` y `403` con `permiso` o `CAMBIO_CLAVE_REQUERIDO` en JSON
- [ ] 3.3 Reglas ruta→permiso de la tabla de design D5 en un único lugar, con `permitAll` para login y `/ca.pem` y `denyAll()` final; desactivar CSRF, `formLogin`, `httpBasic`, `logout` y `cacheControl` por defecto (documentado en el código por qué)
- [ ] 3.4 Test que recorre todos los `@RequestMapping` de la app y falla si alguno no tiene permiso declarado (cae en `denyAll`)
- [ ] 3.5 Tests de autorización: Operario `403` en `PUT /api/rules/parametros`, Ingeniero Agrónomo `200`; sin sesión `401`; token de cámara contra `/api/nursery` `401`; sesión de usuario contra `/api/camara/v1/config` `401`
- [ ] 3.6 Verificar que no hubo regresiones: imagen de captura con `ETag` y `304`, stream SSE de órdenes y la suite de conformidad de `Desarrollo/contratos/camara/v1/conformidad` contra el backend

## 4. Backend — gestión de usuarios, matriz y política (HU-20 CA-01/CA-02)

- [ ] 4.1 `UsuarioService`: alta (username único sin distinguir mayúsculas, incluso bajas; formato), edición de nombre, cambio de rol, suspensión, reactivación, baja y blanqueo; cada operación revoca las sesiones que correspondan
- [ ] 4.2 Salvaguardas: no autosuspensión ni autobaja; nunca menos de un Administrador activo (bloqueo `FOR UPDATE` sobre administradores activos); `409` con mensaje explicativo
- [ ] 4.3 `RolPermisoService`: guardar la matriz de un rol validando par de lectura y permisos intocables del Administrador; revoca sesiones del rol salvo la del autor; invalida la caché
- [ ] 4.4 `PoliticaSesionService`: leer y cambiar `inactividad_min` (rango 5–480)
- [ ] 4.5 Controllers `/api/usuarios/**`, `/api/roles/**` (catálogo de permisos + matriz) y `/api/seguridad/politica`; DTOs sin hash de contraseña
- [ ] 4.6 Tests: cambio de rol y suspensión con sesión abierta → `401 SESION_REVOCADA`; último Administrador → `409`; username repetido → `409`; matriz inválida → `400`; matriz del Administrador sin `usuarios.gestionar` → `409`

## 5. Backend — auditoría (HU-20 CA-03)

- [ ] 5.1 `AuditoriaService.registrar` dentro de la transacción del cambio, con `pg_advisory_xact_lock`, contenido canónico y hash encadenado SHA-256; detalle JSON anterior/nuevo sin contraseñas
- [ ] 5.2 Registrar desde 4.1, 4.3, 4.4, 1.5 y 1.7 todos los tipos del design D7
- [ ] 5.3 `GET /api/auditoria` paginado y filtrable (autor, objetivo, tipo, rango de fechas) y `GET /api/auditoria/verificacion`
- [ ] 5.4 Tests: cambio fallido no audita; falla al auditar revierte el cambio; `UPDATE`/`DELETE` directos son rechazados por la base; verificación detecta un registro alterado (deshabilitando el trigger en el test)

## 6. Frontend — capa de datos y sesión

- [ ] 6.1 Tipos de dominio de seguridad en `src/types/` (Rol, Permiso, PerfilSesion, Usuario, MatrizPermisos, RegistroAuditoria, PoliticaSesion) y constante espejo de la matriz por defecto y del catálogo
- [ ] 6.2 `fetch` envuelto en `src/data/http/` con `credentials: 'include'`, decodificación de `401`/`403` y callback de cierre de sesión; migrar todas las llamadas de `httpRepository.ts` a usarlo
- [ ] 6.3 Interface `SeguridadRepository` + `HttpSeguridadRepository` + `MockSeguridadRepository` (usuarios demo, matriz, auditoría e inactividad en memoria), elegidos en `src/data/index.ts` por `VITE_DATA_SOURCE`
- [ ] 6.4 `AuthProvider` / `useAuth()` (perfil, `puede`, login, logout, motivo de cierre); montar `NurseryProvider` y `DemoExpoProvider` sólo con sesión
- [ ] 6.5 `useInactividad()`: listeners de interacción, ping a `/auth/actividad` como mucho una vez por minuto, `BroadcastChannel` entre pestañas, temporizador local, aviso un minuto antes y cierre al vencer
- [ ] 6.6 Tests: 401 con cada motivo lleva al login con el aviso correcto; el sondeo no dispara pings de actividad; dos pestañas comparten actividad; mock con Operario restringe permisos

## 7. Frontend — login, guardas y shell

- [ ] 7.1 Página `/login` (diseño acorde a los tokens; mensaje genérico; sin marcar campos; limpia la clave; lista de usuarios demo sólo en modo mock) y `/cambiar-clave` (obligatoria y voluntaria)
- [ ] 7.2 Tabla vista→permiso compartida; `<RequireAuth>` (con retorno a la ruta pedida) y `<RequirePermiso>` con aviso "No tenés permiso para ver esta sección"; pantalla "Tu rol no tiene secciones habilitadas"
- [ ] 7.3 Sidebar filtrado por permisos (oculta grupos vacíos) con el ítem "Usuarios"; actualizar `Sidebar.test.tsx`
- [ ] 7.4 Topbar con el usuario real (nombre, rol, iniciales) y menú "Cambiar contraseña" / "Cerrar sesión"
- [ ] 7.5 Acciones de edición condicionadas por permiso en Configuración (incl. interruptor Demo Expo), Motor de reglas (Parámetros), Hardware, Topología y Demo Expo (iniciar/cancelar); manejo uniforme de `403` en esas vistas
- [ ] 7.6 Actualizar los tests de vistas existentes que ahora necesitan un `AuthProvider` con permisos

## 8. Frontend — vista Usuarios

- [ ] 8.1 Ruta `/usuarios` con pestañas Usuarios · Roles y permisos · Auditoría · Seguridad
- [ ] 8.2 Pestaña Usuarios: tabla (personas y cuentas de servicio separadas, bajas a pedido), alta, edición, cambio de rol (con advertencia para Servicio), suspender/reactivar, baja con confirmación, blanqueo de contraseña
- [ ] 8.3 Pestaña Roles y permisos: matriz rol × permiso agrupada, casillas bloqueadas para las salvaguardas, regla de par de lectura en la UI, aviso de que guardar cierra las sesiones del rol
- [ ] 8.4 Pestaña Auditoría: tabla paginada con filtros y estado de integridad de la cadena
- [ ] 8.5 Pestaña Seguridad: tiempo máximo de inactividad con validación de rango
- [ ] 8.6 Tests de la vista con el repositorio mock (alta, suspensión, matriz, filtros)

## 9. Simulador

- [ ] 9.1 `server/backend-session.ts`: login con `BACKEND_USUARIO`/`BACKEND_CLAVE`, cookie en memoria, reautenticación y reintento único ante `401`
- [ ] 9.2 Adjuntar la sesión en `proxyBackend` y en el sondeo de estado del backend; estado "sin credenciales" en la UI
- [ ] 9.3 Agregar las variables a `simulador/env.example` y verificar de punta a punta: telemetría por MQTT sin credenciales, órdenes y diagnósticos con una cuenta de rol Servicio
- [ ] 9.4 Verificar que no se tocó nada del backend para el simulador (grep de "simulador" en el backend sin resultados nuevos)

## 10. Documentación y cierre

- [ ] 10.1 README del backend: autenticación, cadenas, mapa ruta→permiso, admin inicial, triggers de auditoría, `Secure` de la cookie y requisito de mismo esquema/host entre dashboard y API
- [ ] 10.2 README del frontend (login, usuarios demo, permisos en la UI) y del simulador (cuenta de servicio)
- [ ] 10.3 `CLAUDE.md`: §6 convenciones (la API exige sesión y permiso; deny-by-default; dónde se declara el permiso de una ruta nueva), §6.1 (la API de plataforma de cámara exige sesión) y §6.2 (el simulador entra con cuenta de servicio); glosario de roles y permisos en §2
- [ ] 10.4 Prueba manual de punta a punta: login con cada rol, mensaje genérico, cierre por inactividad con el valor en 5 min, cambio de rol y suspensión con la otra sesión abierta, edición de la matriz, auditoría y verificación de la cadena
