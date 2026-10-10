## Context

- El backend (Spring Boot 3.2.4, Java 17) no tiene autenticación de personas. La única es la de
  los **dispositivos de captura**: `CamaraAuthFilter`, un `OncePerRequestFilter` artesanal acotado
  a `/api/camara/v1/**` que valida un JWT propio (`TokenService`, `java-jwt`). Su javadoc dice
  explícitamente que se evitó `spring-boot-starter-security` para no cerrar por defecto el resto
  de la API, y que "cuando llegue HU-01 habrá que unificar". Llegó.
- Los clientes de la API de plataforma hoy son: el dashboard (navegador, `:5173`, sondea
  `/api/nursery` cada 5 s, la pasada y la traza también sondean), el **servidor** del simulador
  (Node, server-to-server vía su proxy `/backend/**`, sin `Origin`) y, a futuro, el servicio de
  inferencia. El planificador de pasadas es interno al backend: no usa HTTP.
- El dashboard carga imágenes con `<img src="http://…:8000/api/capturas/{id}/imagen">`, sin
  `fetch`: no puede fijar headers, sólo viajan cookies.
- CORS ya está con `allowCredentials(true)` y patrones de toda la LAN privada.
- `spring.jpa.open-in-view=false`, `ddl-auto=update`, `spring.sql.init.mode=never`: el esquema lo
  crea Hibernate y no hay herramienta de migraciones.
- Decisiones ya tomadas con el equipo: matriz **editable** por el Administrador; un 5º rol
  técnico **Servicio** para integraciones; tiempo de inactividad **editable** por el
  Administrador; modo demo con **usuarios demo por rol**.

## Goals / Non-Goals

**Goals:**
- Cumplir HU-01 CA-01..03 y HU-20 CA-01..03 tal como están en las specs.
- Que la invalidación de sesiones (HU-20 CA-02) y el cierre por inactividad (HU-01 CA-03) sean
  **inmediatos y decididos por el servidor**, no por el reloj del cliente.
- Cerrar la API por omisión: una ruta nueva sin permiso declarado queda denegada.
- No tocar el contrato de cámara v1, ni sus clientes, ni el firmware.
- Mantener los invariantes de §6.2: el backend no conoce al simulador.

**Non-Goals:**
- Roles personalizados (crear/renombrar roles). Los roles son fijos; lo editable es la matriz.
- Permisos por macro-zona o por sector (alcance de datos). Un permiso vale para todo el vivero.
- SSO, OAuth, 2FA, recuperación de contraseña por mail (no hay servidor de mail).
- Bloqueo de cuenta o rate limiting por intentos fallidos. Se deja anotado como riesgo.
- Auditar inicios de sesión, lecturas o acciones agronómicas: eso es HU-11 (historial). La
  auditoría de este cambio cubre los cambios de seguridad que pide HU-20 CA-03.
- HTTPS obligatorio para el dashboard (sigue la decisión vigente de §6.1).

## Decisions

### D1. Sesiones opacas del lado del servidor, no JWT

La sesión es un identificador aleatorio de 256 bits, entregado en una cookie; en la base se
guarda **su SHA-256**, nunca el valor, junto con usuario, creación, última actividad, IP/agente
y, si corresponde, fecha y motivo de revocación.

- *Por qué:* HU-20 CA-02 pide que un cambio de rol, una suspensión o una baja **corten la sesión
  vigente**, y HU-01 CA-03 que la inactividad la cierre. Con una tabla de sesiones ambas cosas son
  un `UPDATE … SET revocada` y una comparación de fechas. Hashear el identificador hace que un
  volcado de la base no sirva para secuestrar sesiones.
- *Alternativa descartada — JWT stateless (como el de la cámara):* para revocar al instante hace
  falta igual una consulta por petición (lista negra o `token_version`), y la inactividad no se
  puede expresar sin estado. Se paga la complejidad de JWT sin su ventaja. El JWT sigue teniendo
  sentido para la cámara (vida corta, contrato propio); no se unifican los formatos, sí la cadena.

### D2. Cookie `HttpOnly; SameSite=Strict`, una sola vía para todos los clientes

`POST /api/auth/login` fija `YERBA_SESION` (`HttpOnly`, `SameSite=Strict`, `Path=/api`; `Secure`
configurable por propiedad, apagado por defecto porque el dashboard corre por HTTP en la LAN). La
respuesta lleva el perfil, **no** el token.

- *Por qué cookie:* las `<img>` de capturas no pueden mandar headers; con `Authorization: Bearer`
  habría que pasar a `fetch`+blob o meter el token en la URL. `HttpOnly` lo deja fuera del alcance
  de un XSS. `localhost:5173` → `localhost:8000` (y `IP:5173` → `IP:8000`) son *same-site*, así que
  `SameSite=Strict` no estorba al dashboard y bloquea CSRF desde cualquier otro sitio.
- *Clientes no navegador:* el simulador (y la inferencia) leen el `Set-Cookie` del login y lo
  reenvían en `Cookie:`. Es trivial en Node y evita una segunda vía de autenticación.
- *Alternativa descartada — token en `localStorage` + header:* expuesto a XSS y no resuelve las
  imágenes. *Token de CSRF de Spring Security:* innecesario con `SameSite=Strict` y un CORS ya
  acotado; se desactiva el CSRF de Spring documentando por qué.

### D3. Spring Security con dos cadenas; `CamaraAuthFilter` se integra, no se reescribe

Se agrega `spring-boot-starter-security` con dos `SecurityFilterChain`:

1. `@Order(1)`, `securityMatcher("/api/camara/v1/**")`: la lógica actual de `CamaraAuthFilter`
   (token de dispositivo; `enrolar` y `token` libres; query string sólo en el stream SSE) pasa a ser
   un filtro de esta cadena. Comportamiento idéntico; la suite de conformidad es la prueba.
2. `@Order(2)`, `/api/**`: filtro de sesión (D1) que arma un `Authentication` cuyas *authorities*
   son los permisos efectivos del rol. `POST /api/auth/login` y `/ca.pem` son `permitAll`. Todo lo
   demás: `authenticated()` + autorización por permiso (D5). `anyRequest().denyAll()` al final de
   las reglas para que lo no declarado quede cerrado.

Respuestas: `401` con `{"error": …, "motivo": "SIN_SESION|SESION_EXPIRADA|SESION_REVOCADA"}`;
`403` con `{"error": …, "permiso": "<código>"}` o `{"motivo": "CAMBIO_CLAVE_REQUERIDO"}`. Sin
redirecciones ni formulario de login de Spring (`formLogin`, `httpBasic`, `logout` desactivados).
El `BCryptPasswordEncoder` sale del mismo starter.

- *Por qué:* el filtro artesanal fue correcto mientras había un solo caso; con dos esquemas,
  autorización por permiso y deny-by-default, reescribir eso a mano es reinventar Spring Security.
- *Cuidado:* Spring Security agrega por defecto `Cache-Control: no-store` a todas las respuestas,
  lo que rompe el `ETag` de las imágenes. Se desactiva `headers().cacheControl()` en la cadena 2 y
  los controllers siguen fijando sus propios headers de caché.

### D4. Actividad: sólo cuenta la interacción real

El problema: el dashboard sondea cada 5 s; si cualquier petición contara como actividad, la
sesión no vencería nunca y HU-01 CA-03 sería letra muerta.

- Cuentan como actividad (actualizan `ultima_actividad`): el login, toda petición no `GET`, y
  `POST /api/auth/actividad`.
- El dashboard detecta interacción (`pointerdown`, `keydown`, `wheel`, `touchstart`, `scroll`) y
  manda `POST /api/auth/actividad` como mucho una vez por minuto mientras la haya. La señal se
  comparte entre pestañas con `BroadcastChannel` para que la de atrás no se cierre si hay uso en
  la de adelante.
- El backend, en cada petición, rechaza con `SESION_EXPIRADA` si
  `ahora − ultima_actividad > inactividad`. No hace falta un job: la comprobación es perezosa. Un
  barrido diario borra sesiones cerradas o vencidas hace más de 7 días.
- El dashboard además lleva un temporizador local (con el valor que vino en el perfil) para
  mostrar el login justo al vencer, sin esperar al próximo sondeo, y un aviso un minuto antes.
- Para no escribir en cada petición, `ultima_actividad` se actualiza sólo si cambió más de 15 s.

*Alternativa descartada — header `X-Background` en los sondeos:* invierte la carga (hay que
acordarse de marcar cada sondeo nuevo) y un olvido deja la sesión eterna. Con "sólo cuenta lo
explícito", un olvido produce el error seguro: se cierra antes de tiempo.

*Cuentas de servicio:* el mismo régimen. El simulador hace sobre todo `GET`; cuando la sesión
vence, recibe `401`, reinicia sesión y reintenta una vez. No hay excepción por rol.

### D5. Permisos: catálogo en código, matriz en tabla, chequeo por ruta

- `Permiso` es un `enum` Java (código, grupo, descripción, par de lectura si es de edición). `Rol`
  es otro `enum` con los cinco roles. La matriz es la tabla `rol_permiso(rol, permiso)`.
- La siembra de la matriz por defecto la hace un `ApplicationRunner` **sólo si la tabla está
  vacía** (el mismo criterio que evita pisar ediciones en el catálogo de parámetros).
- La correspondencia ruta→permiso vive **en un único lugar**, la configuración de la cadena 2
  (`requestMatchers(GET, "/api/rules/**").hasAuthority("reglas.ver")`, etc.), en vez de
  `@PreAuthorize` desperdigado. Así se audita en una pantalla y el `denyAll()` final cubre lo no
  declarado. Un test recorre todos los `@RequestMapping` y falla si alguno cae en `denyAll`.
- Los permisos efectivos se leen de una caché en memoria de la matriz (5 roles × ~20 permisos),
  invalidada al guardar. El rol del usuario se lee con la sesión en cada petición (es la misma
  consulta), así que un cambio rige en la próxima petición.
- Salvaguardas en el servicio (spec `control-acceso-roles`): Administrador siempre con
  `usuarios.gestionar` y `auditoria.ver`; nunca menos de un Administrador activo (se verifica con
  `SELECT … FOR UPDATE` sobre los administradores activos para que dos bajas concurrentes no
  dejen cero).
- `GET /api/configuracion/demo-expo` queda en `vivero.ver` (el sidebar lo necesita para saber si
  mostrar la pestaña); `PUT` en `demo-expo.configurar`.

Mapa ruta → permiso (resumen; el detalle va en la configuración de la cadena):

| Rutas | Permiso |
|---|---|
| `GET /api/nursery`, `GET /api/configuracion/demo-expo` | `vivero.ver` |
| `GET /api/diagnosticos` / `POST /api/diagnosticos` | `diagnosticos.ver` / `diagnosticos.registrar` |
| `GET /api/historial` | `historial.ver` |
| `GET` / `PUT /api/configuracion` | `configuracion.ver` / `configuracion.editar` |
| `GET /api/rules/**` / `PUT /api/rules/parametros` | `reglas.ver` / `reglas.editar` |
| `GET` / `POST`, `PUT /api/hardware/**` | `hardware.ver` / `hardware.gestionar` |
| `GET` / `POST`, `PUT /api/topologia/**` | `topologia.ver` / `topologia.gestionar` |
| `GET /api/capturas/**` / `POST /api/capturas/ordenes` | `capturas.ver` / `capturas.ordenar` |
| `/api/camara/vinculacion`, `/api/camara/dispositivos/**` | `camara.gestionar` |
| `GET /api/pasadas/**` / `POST /api/pasadas/**` | `pasadas.ver` / `pasadas.operar` |
| `PUT /api/configuracion/demo-expo` | `demo-expo.configurar` |
| `/api/usuarios/**`, `/api/roles/**`, `/api/seguridad/politica` | `usuarios.gestionar` |
| `GET /api/auditoria/**` | `auditoria.ver` |
| `/api/auth/perfil`, `/actividad`, `/logout`, `/clave` | sólo sesión |

### D6. Modelo de datos

- `usuario`: `id`, `username` (único, minúsculas), `nombre`, `rol` (enum como texto),
  `estado` (`ACTIVO|SUSPENDIDO|BAJA`), `clave_hash`, `debe_cambiar_clave`, `creado_en`,
  `ultimo_ingreso`, `version` (`@Version`, bloqueo optimista para dos admins editando a la vez).
- `sesion`: `id_hash` (PK), `usuario_id`, `creada_en`, `ultima_actividad`, `cerrada_en`,
  `motivo_cierre` (`LOGOUT|EXPIRADA|REVOCADA`), `ip`, `agente`.
- `rol_permiso`: `(rol, permiso)` PK compuesta.
- `politica_sesion`: fila única con `inactividad_min` (default 60, rango 5–480). Se descartó
  meterlo en `configuracion_operativa`: ésa es agronómica y la edita otro rol.
- `auditoria_seguridad`: ver D7.

Ninguna de estas tablas existe hoy, así que `ddl-auto=update` las crea sin migración manual.

### D7. Auditoría append-only en tres capas

1. **Aplicación:** entidad `@Immutable`, columnas `updatable=false`; el repositorio sólo expone
   `save` de altas y consultas; no hay endpoint de escritura. El registro se escribe en la misma
   transacción del cambio (si falla, el cambio se revierte).
2. **Base:** un trigger `BEFORE UPDATE OR DELETE` que lanza excepción. Como `ddl-auto` no crea
   triggers y `sql.init` está apagado, un `ApplicationRunner` ejecuta al arrancar un script
   idempotente (`CREATE OR REPLACE FUNCTION` + `DROP TRIGGER IF EXISTS` / `CREATE TRIGGER`)
   después de que Hibernate creó la tabla. No es una migración manual más: se aplica sola.
   `TRUNCATE` también se bloquea con un trigger `BEFORE TRUNCATE`.
3. **Evidencia:** cada registro guarda `hash = SHA-256(hash_anterior ‖ contenido canónico)`.
   `GET /api/auditoria/verificacion` recorre la cadena y reporta íntegra o el primer id roto. Para
   que dos escrituras concurrentes no lean el mismo "anterior", la inserción toma un
   `pg_advisory_xact_lock` fijo (las escrituras de auditoría son pocas; no hay contención real).

Columnas: `id` (secuencia), `ocurrido_en` (UTC, ms), `autor_id` + `autor_username` (nulo =
sistema), `objetivo_tipo` (`USUARIO|ROL|POLITICA`), `objetivo_ref`, `tipo` (enum: `USUARIO_ALTA`,
`USUARIO_EDITADO`, `USUARIO_ROL_CAMBIADO`, `USUARIO_SUSPENDIDO`, `USUARIO_REACTIVADO`,
`USUARIO_BAJA`, `USUARIO_CLAVE_BLANQUEADA`, `ROL_PERMISOS_CAMBIADOS`, `POLITICA_SESION_CAMBIADA`,
`ADMIN_INICIAL_CREADO`, `MATRIZ_SEMBRADA`), `detalle` (JSON con anterior/nuevo), `hash_anterior`,
`hash`. Se copian los usernames (no sólo ids) para que el registro se lea solo aunque cambie el
usuario.

El cambio de contraseña **propio** no se audita (no es una modificación hecha por el
Administrador sobre otro usuario, que es lo que pide CA-03); el blanqueo sí.

### D8. Login resistente a enumeración

Un único `401` idéntico para usuario inexistente, contraseña mala, suspendido o baja. Si el
usuario no existe se compara contra un hash BCrypt de relleno calculado al arrancar, para que el
tiempo de respuesta no delate la existencia. El estado (suspendido/baja) se evalúa **después** de
verificar la contraseña, por la misma razón.

### D9. Administrador inicial por propiedad

`yerbanalytics.auth.admin-inicial.usuario` (default `admin`) y `…clave` (sin default; se espera por
variable de entorno `YERBANALYTICS_ADMIN_CLAVE`). Si falta, se genera una aleatoria y se escribe
**una vez** en el log de arranque con un aviso visible. Queda con `debe_cambiar_clave=true`.
Nunca se crea un segundo si ya hay un Administrador activo.

### D10. Frontend

- `SeguridadRepository` (interface) + `HttpSeguridadRepository` + `MockSeguridadRepository`,
  elegidos en `src/data/index.ts` por el mismo `VITE_DATA_SOURCE`. Se separa del `DataRepository`
  porque éste ya es grande y porque la sesión tiene que existir **antes** que los datos del vivero.
- `AuthProvider` / `useAuth()`: perfil, `puede(permiso)`, `login`, `logout`, motivo del último
  cierre. Envuelve al `NurseryProvider`, que sólo se monta con sesión (así no se sondea sin sesión).
- Un único `fetch` envuelto en `src/data/http/` (`credentials: 'include'`, decodifica 401/403 y
  avisa al `AuthProvider` por un callback registrado). Todas las llamadas de `httpRepository.ts`
  pasan a usarlo.
- Rutas: `/login` y `/cambiar-clave` fuera de `AppLayout`; dentro, un `<RequireAuth>` y, por vista,
  `<RequirePermiso permiso="reglas.ver">`. La tabla vista→permiso es una constante compartida por
  router y sidebar, para que no diverjan.
- Acciones de edición: `useAuth().puede('reglas.editar')` decide ocultar/deshabilitar en
  Configuración, Motor de reglas (pestaña Parámetros), Hardware, Topología, Demo Expo (iniciar/
  cancelar pasada) e interruptor de Demo Expo en Configuración.
- `useInactividad()`: listeners de interacción, ping throttled, `BroadcastChannel`, temporizador
  local y aviso previo.
- Vista `features/usuarios/` con pestañas: **Usuarios** (tabla + alta/edición/suspensión/baja/
  blanqueo), **Roles y permisos** (matriz rol×permiso con casillas; deshabilita las salvaguardas),
  **Auditoría** (tabla filtrable + estado de la cadena) y **Seguridad** (tiempo de inactividad).
- Mock: usuarios demo `admin`, `agronomo`, `productor`, `operario` (contraseña `demo`), matriz por
  defecto importada de una constante espejo del `enum` del backend; inactividad simulada igual.

### D11. Simulador

`server/backend-session.ts`: inicia sesión con `BACKEND_USUARIO`/`BACKEND_CLAVE` de su `.env`,
guarda la cookie en memoria, la adjunta en `proxyBackend` y en el sondeo de estado
(`GET /api/topologia`), y ante `401` reautentica y reintenta una vez. Si faltan las credenciales,
el estado del backend en su UI pasa a "sin credenciales". El backend no cambia por esto: la cuenta
la da de alta el Administrador con rol Servicio.

*Agregado al implementar:* `Desarrollo/servicio-inferencia/` ya existía y llamaba al backend sin
sesión. Recibe el mismo trato (`BACKEND_USUARIO`/`BACKEND_CLAVE`, reintento único ante `401`). Lo
mismo la suite de conformidad del contrato de cámara: sus aserciones no cambian, pero la
preparación de los casos (códigos de vinculación, órdenes) es API de plataforma y ahora entra con
una cuenta (`PLATAFORMA_USUARIO`/`PLATAFORMA_CLAVE`).

## Risks / Trade-offs

- **[Sin bloqueo por intentos fallidos]** → fuerza bruta posible desde la LAN. Mitigación: BCrypt
  (costo 10) la vuelve lenta; se deja anotado como mejora siguiente. No se implementa ahora para no
  abrir un vector de denegación (bloquear la cuenta del Administrador desde afuera).
- **[`Secure` apagado por defecto]** → la cookie viaja en claro por HTTP en la LAN. Es coherente
  con que hoy todo el dashboard viaja en claro; con HTTPS se enciende por propiedad.
- **[Esquema mixto http/https]** → si `VITE_API_BASE_URL` apunta a `https://…:8443` y el
  dashboard se sirve por `http`, el navegador considera los sitios distintos (schemeful same-site)
  y no manda la cookie. Mitigación: documentar que dashboard y API deben compartir esquema y host;
  el login falla de forma visible ("no se pudo establecer la sesión") en vez de silenciosa.
- **[BREAKING para clientes anónimos]** → cualquier script o herramienta que llame a la API sin
  sesión deja de funcionar. Mitigación: el simulador se adapta en este mismo cambio; el resto se
  documenta en el README del backend.
- **[Trigger instalado por la app]** → si el usuario de la base no puede crear funciones, el
  arranque falla. Mitigación: el runner lo detecta y falla con un mensaje explícito; el SQL queda
  también como script para correr a mano con un usuario con permisos.
- **[La cadena de hash no protege contra quien reescribe todo]** → alguien con acceso total a la
  base podría recalcular la cadena entera. Es evidencia de alteración, no prueba criptográfica de
  integridad; para eso haría falta anclar el último hash fuera de la base. Fuera de alcance.
- **[Spring Security cambia comportamientos por defecto]** (`Cache-Control`, `X-Frame-Options`,
  manejo de errores en SSE) → mitigación: tests de las imágenes con `ETag`, del stream SSE de la
  cámara y suite de conformidad del contrato antes de dar por terminado.
- **[Doble barrera en el frontend puede desincronizarse]** → la tabla vista→permiso es compartida y
  ante un `403` inesperado se recarga el perfil.

## Migration Plan

1. Desplegar el backend: crea las tablas, siembra la matriz, instala los triggers y crea el
   Administrador inicial (fijar `YERBANALYTICS_ADMIN_CLAVE` antes, o tomar la clave del log).
2. Ingresar como `admin`, cambiar la contraseña, dar de alta a las personas y una cuenta de rol
   Servicio para el simulador.
3. Cargar `BACKEND_USUARIO`/`BACKEND_CLAVE` en el `.env` del simulador y reiniciarlo.
4. Frontend: sin variables nuevas; reiniciar.

Rollback: volver al commit anterior. Las tablas nuevas quedan huérfanas sin efecto; el trigger de
auditoría no afecta a ninguna otra tabla.

## Open Questions

Ninguna. Resueltas con el equipo:

- El Operario **no** dispara pasadas del riel: no tiene `pasadas.operar` en la matriz por defecto.
  Si el vivero lo pide más adelante, se le agrega desde la matriz sin cambiar código.
- El tiempo de inactividad por defecto es **60 min** (no 30), pensando en el uso en campo.
