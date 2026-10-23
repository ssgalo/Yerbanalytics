# Yerbanalytics · Backend

API REST para el sistema de monitoreo inteligente de plantines de yerba mate. Desarrollado en Java con Spring Boot.

## Requisitos

- Java 17 (LTS)
- Maven 3.8+

## Puesta en marcha

Para levantar el servidor en tu entorno local, ejecutá:

```bash
cd Desarrollo/backend
mvn spring-boot:run
```

La API quedará escuchando en `http://localhost:8000`.

**Primer arranque contra una base vacía:** copiá `env.example` a `.env` (lo hace `start-all` si
falta). El backend lo lee al arrancar y crea el Administrador inicial (`admin` / `admin-yerba-2026`,
con cambio de clave en el primer ingreso) y las cuentas de servicio que esperan el simulador y la
inferencia, con las mismas claves de desarrollo que sus `env.example`. Sin `YERBANALYTICS_ADMIN_CLAVE`,
el backend **no arranca** si tiene que crear el admin: la clave no se genera. Detalle en [Autenticación y permisos](#autenticación-y-permisos-hu-01--hu-20).

## Configuración

La configuración principal se encuentra en `src/main/resources/application.properties`.
- El puerto está fijado en `8000` para coincidir con la URL esperada por el frontend (`VITE_API_BASE_URL`).
- El proyecto cuenta con una configuración global de CORS en la carpeta `config/` para permitir peticiones entrantes desde el entorno de desarrollo del frontend (Vite en `http://localhost:5173`).

## Base de datos

El esquema lo genera Hibernate (`ddl-auto=update`) y se siembra con
`src/main/resources/data.sql` (600 sectores, 6 macro-zonas, umbrales de fábrica).

### Migración manual pendiente · diagnóstico por detecciones

El alta de diagnósticos pasó a llevar nivel de confianza y detecciones (ver
[Diagnósticos](#diagnósticos-contrato-del-alta-y-criterio)). `ddl-auto=update` crea la tabla
`diagnosis_criteria` y agrega las columnas nuevas (`diagnostico.confidence`, `diagnostico.detections`,
`diagnostico.detection_count`, `sector.diagnosis_confidence`), todas nullable: las filas anteriores
quedan sin nivel de confianza (el dashboard las muestra "sin nivel") y con 0 detecciones. Lo que **no**
hace es quitar el `NOT NULL` de `diagnostico.conf`, y un No concluyente ya no lleva score: sin el
script, su alta falla con un `500`.

Corré `src/main/resources/migracion-diagnostico-detecciones.sql` (antes o después de arrancar la
versión nueva; es idempotente). No se pierde nada. Para volver a la versión anterior, primero
`UPDATE diagnostico SET conf = 0 WHERE conf IS NULL`: el backend viejo lee `conf` en un `double`.

### Migración manual pendiente · permisos del bloqueo manual

`bloqueos.ver` y `bloqueos.gestionar` son permisos nuevos y la matriz se siembra **sólo si `rol_permiso` está
vacía**: una base anterior no los tiene y nadie (salvo quien edite la matriz) podría ver ni gestionar bloqueos.
Corré `src/main/resources/migracion-permisos-bloqueo.sql` (idempotente, antes o después de arrancar) para
agregar los de la matriz por defecto. No toca lo que el Administrador editó. Se aplican al reiniciar el backend
(la matriz vive en memoria).

### Migración manual pendiente · permisos de los tanques de insumo

`insumos.ver` e `insumos.gestionar` son permisos nuevos y la matriz se siembra **sólo si `rol_permiso` está vacía**: una base
anterior no los tiene y nadie (salvo quien edite la matriz) podría ver los tanques ni registrar una carga. Corré
`src/main/resources/migracion-permisos-insumos.sql` (idempotente, antes o después de arrancar). El Operario es quien carga; el rol
Servicio los recibe para que el simulador deje stock en sus escenarios. Se aplican al reiniciar el backend.

### Migración manual pendiente · dosis por insumo

La dosis única `insumo.dosis-ml` se reparte en `insumo.dosis-fertilizante` e `insumo.dosis-fitosanitario`. El backend **arranca igual**
sin correr nada (un override de una clave que ya no existe se ignora con un warn), pero si alguien había editado la dosis, se pierde.
`src/main/resources/migracion-dosis-por-insumo.sql` copia el valor editado a la dosis del fertilizante (la dosificación v1 aplicaba un
producto genérico, que es el fertilizante) y borra el viejo. Idempotente.

### Migración manual pendiente · permisos de reglas y pasadas para el rol Servicio

El rol Servicio (cuentas de integración) recibe `reglas.ver`, `pasadas.ver` y `pasadas.operar` en la matriz por defecto: lee el
catálogo y la traza del motor y pide pulsos, secuencias y pasadas. En una base ya sembrada faltan (403): corré
`src/main/resources/migracion-permisos-servicio-simulador.sql` (idempotente) y reiniciá el backend.

### Migración manual pendiente · permiso de lectura del hardware para el rol Servicio

El simulador (cuenta de rol Servicio) consulta qué sectores ya no tienen electroválvula registrada para armar su escenario
«Hardware incompleto» (S-04), así que el rol Servicio recibe `hardware.ver` en la matriz por defecto (sólo lectura:
`hardware.gestionar` no se le da). La matriz se siembra **sólo si `rol_permiso` está vacía**: una base anterior no lo tiene.
Corré `src/main/resources/migracion-permisos-hardware-servicio.sql` (idempotente, antes o después de arrancar). Se aplica
al reiniciar el backend (la matriz vive en memoria). Las tablas nuevas de este cambio (`invalid_reading`) las crea Hibernate.

### Migración manual pendiente · permiso para registrar reparaciones de hardware

`hardware.reparar` (S-05) es un permiso nuevo. Lo reciben el Ingeniero Agrónomo, el **Operario** (que además pasa a ver el panel
de Hardware: `hardware.ver`, porque la reparación se registra desde ahí) y el rol Servicio (el simulador registra la reparación de
su escenario); `hardware.gestionar` (alta y recambio) sigue siendo de quien administra. Una base sembrada no lo tiene: corré
`src/main/resources/migracion-permisos-hardware-reparar.sql` (idempotente, antes o después de arrancar). La columna nueva
`dispositivo.falla_desde` la agrega Hibernate. Se aplica al reiniciar el backend (la matriz vive en memoria).

### Migración manual pendiente · confianza mínima 70 %

`diagnostico.confianza-minima` pasó de 85 a **70 %** de fábrica y su rango de 50–100 a **50–95 %**
(`reglas_v2` §1.3 y §11). Una base que estaba en la fábrica vieja no tiene fila en `parametro_regla` y
toma el 70 sola; un valor que alguien editó (p. ej. 80) se respeta. Lo único que queda mal son los
valores de 96 a 100, que el rango nuevo no admite: el catálogo los ignora y cae a la fábrica.

Corré `src/main/resources/migracion-confianza-70.sql` (idempotente, antes o después de arrancar) para
borrarlos. No se pierde nada más.

### Migración manual pendiente · mediasombra binaria

La mediasombra es binaria (el firmware enrolla con un objetivo >= 50 y desenrolla por debajo), así que
`ShadingRule` sólo pide 100 (abierta) o 0 (cerrada) y reenvía el cierre mientras dure el pico de UV
(el valor guardado es lo último que mandó el backend, no la posición real; el firmware responde
`sin_cambio` si ya está ahí). Salen `mediasombra.apertura-proteccion-uv` y `mediasombra.apertura-maxima`:
corré `src/main/resources/migracion-mediasombra-binaria.sql` (idempotente) para borrar sus overrides; el
catálogo los ignora con un warn si no lo hacés. El tope de las etapas de rustificación pasa a 100 % fijo.

### Migración manual pendiente · mediasombra por macro-zona

La mediasombra pasó a ser **de la macro-zona** (una por zona, como el nodo testigo) y la rustificación global por
porcentaje se reemplazó por un plan por zona (ver "Mediasombra por macro-zona y plan de rustificación"). `ddl-auto=update`
crea `zona.mediasombra_abierta`, las columnas del horario por defecto en `configuracion_operativa` y las tablas
`plan_rustificacion` / `etapa_plan_rustificacion`, pero no mueve datos ni borra lo viejo. Corré
`src/main/resources/migracion-mediasombra-por-zona.sql` (idempotente) **después de arrancar el backend nuevo una vez**:

- pasa una mediasombra de cada zona de su sector a la zona (conserva serial y falla) y borra las demás;
- copia la posición a `zona.mediasombra_abierta` y borra `sector.actuador_shade`;
- borra `rustificacion_etapa`.

> **Hasta correrla, no se pueden crear sectores** (regenerar la topología, los tests con base real como
> `SectorPumpStateTest`): `sector.actuador_shade` sigue `NOT NULL` y la entidad ya no la escribe.

### Migración manual pendiente · pulso de sensado y pasada diaria

El pedido de lectura cada X segundos y el barrido periódico del motor se reemplazaron por el **pulso
de sensado** (ver "Pulso de sensado" más abajo). `ddl-auto=update` agrega solo las columnas nuevas
(`configuracion_operativa.pasada_diaria_activa` y `pasada_diaria_hora`, `sector.diagnosis_ts` y
`diagnosis_usado_ts`, `zona.pulsos_sin_respuesta`), todas nullable: los nulos se leen como valores de
fábrica (pasada desactivada a las 11:00) o 0. Lo que no hace es borrar las dos columnas que quedaron sin
uso, `sensing_pull_interval_seconds` e `intervalo_evaluacion_minutos`.

Corré `src/main/resources/migracion-pulso-sensado.sql` (detener la app, ejecutar, volver a arrancar).
Se pierden dos valores de configuración que ya nadie lee; ningún dato del vivero. Backend y dashboard
se despliegan juntos: los campos retirados y los nuevos viajan en `GET/PUT /api/configuracion` (un `PUT`
que todavía mande los viejos los ignora).

### Migración manual pendiente · sensado por macro-zona

Las lecturas sensadas se mudaron del sector a la macro-zona (un solo nodo testigo por MZ).
**`ddl-auto=update` no cubre ese cambio**: agrega las columnas nuevas en `zona`, pero no
copia los datos, no borra las columnas viejas de `sector` y no corrige umbrales ya
sembrados (`data.sql` usa `ON CONFLICT DO NOTHING`).

Si tu base es descartable, borrala y dejá que `data.sql` la siembre de cero. Si querés
conservar el estado, corré `src/main/resources/migracion-manual.sql` en este orden:

1. Arrancar la app una vez → crea las columnas nuevas en `zona`.
2. Detener la app.
3. Ejecutar `migracion-manual.sql`.
4. Volver a arrancar.

El script traslada las lecturas, recupera el estado de los nodos desde el registro de
hardware, convierte `ce` a dS/m y re-escala los umbrales de `uv`. Los `DROP COLUMN` quedan
comentados a propósito: descomentalos recién después de verificar el resultado.

### Migración manual pendiente · catálogo de parámetros de reglas

El tiempo máximo de apertura de riego y la apertura máxima de la mediasombra se mudaron de
`configuracion_operativa` al **catálogo de parámetros de reglas** (`riego.tiempo-max-apertura` y
`mediasombra.apertura-maxima`), junto con el resto de los umbrales que comparan las reglas.
`ddl-auto=update` **nunca baja columnas**: las dos quedan en la tabla como `NOT NULL` sin default y
el primer `INSERT` de una fila operativa nueva falla. El backend arranca igual (la fila existente
sólo se lee y los valores de fábrica son los mismos del seed).

Corré `src/main/resources/migracion-catalogo-parametros.sql`:

1. Arrancar la app una vez con el código nuevo → crea la tabla `parametro_regla`.
2. Detener la app.
3. Ejecutar el script.
4. Volver a arrancar.

Copia cada valor a `parametro_regla` **sólo si difiere de fábrica** (120 s y 100 %) y baja las
columnas. También copia el `ideal_min` de `humSus` como override de `riego.umbral-humedad` si
difiere y no hay uno: antes el riego usaba ese umbral y ahora usa el del catálogo. Es idempotente y
trae, comentado, el bloque inverso (`ADD COLUMN … DEFAULT …`) para un rollback del código.

> **Ojo:** el script compara ese `ideal_min` contra la fábrica **actual** de `riego.umbral-humedad`,
> **45** (era 42; ver más abajo). Una base con `ideal_min = 42` (el default del seed) SÍ recibe el
> override 42, así que su umbral de riego se conserva; sólo una base con `ideal_min = 45` queda sin
> override (coincide con la fábrica). Una base ya migrada con la comparación vieja (contra 42) no
> recibió override y pasó de 42 a 45: cargá 42 por `PUT /api/rules/parametros` si querés conservarlo.
>
> `riego.tiempo-max-apertura` (uno de los dos parámetros que mueve este script) ya no existe en el
> catálogo: lo limpia el script siguiente.

### Migración manual pendiente · reglas de riego R-01…R-06

El riego pasó a las reglas v2 (`reglas_v2` §5) con despacho por tandas. `ddl-auto=update` agrega las
columnas nuevas (`historial_evento.regla`, `alerta`, `volumen_l`, `duracion_seg` y `zona.hum_sus_ts`,
todas nulas) y los índices de `historial_evento`, pero **no limpia filas viejas ni baja columnas**.
El backend arranca igual sin correr nada: un override de una clave que ya no existe se ignora con un
warn. Correr `src/main/resources/migracion-reglas-riego.sql` deja la base sin esas filas huérfanas:

1. Arrancar la app una vez con el código nuevo → crea las columnas.
2. Ejecutar el script (la app puede estar levantada: sólo borra filas muertas e índices).

El script **no se aplica solo**. Hace cuatro cosas:

- Borra los overrides de `riego.tiempo-max-apertura`, `riego.max-riegos-24h` y
  `riego.max-riegos-24h-sector`, claves que ya no existen.
- Crea (si no existen) los índices `historial_evento(zona_id, tipo, ts)` y `(tipo, ts)`. Hibernate los
  crea sola al arrancar; el script permite hacerlo antes o por separado.
- **Baja `configuracion_operativa.riego_vol_max_diario_ml`** (`NOT NULL`): el código nuevo no la
  completa, así que mientras exista el primer guardado de Configuración sobre una base *sin fila*
  falla.
- Trae, comentadas, una consulta que detecta overrides que violan las restricciones nuevas (el
  backend no falla al arrancar con ellos, pero la pantalla de Reglas los rechaza al editar) y el
  bloque de rollback.

`riego.umbral-humedad` (42 → 45 %) y `riego.lluvia-probabilidad` (60 → 70 %) cambiaron **de fábrica**:
sin override, el valor vigente cambia solo al desplegar; con override, se respeta. La columna
`sector.actuador_valve` quedó como legado: nadie la lee para decidir (el estado de la válvula lo
deriva el despacho).

### Migración manual pendiente · baja del estado del simulador

El simulador se extrajo a `Desarrollo/simulador/`, un proyecto independiente, y con él salieron
del backend el **modo de operación** y los **sensores simulados**. `ddl-auto=update` crea y
modifica tablas pero **nunca las elimina**, así que `modo_operacion` y `sensor_simulado` quedan
huérfanas en la base.

Corré `src/main/resources/migracion-quitar-simulador.sql`:

1. Detener la app.
2. Ejecutar el script.
3. Volver a arrancar.

Se pierden los sensores de prueba y un modo que ya no existe. **Ningún dato del vivero**: zonas,
sectores, lecturas, hardware, historial, capturas y diagnósticos quedan intactos. Los sensores
de prueba se vuelven a dar de alta en el simulador, que ahora los guarda en su propia carpeta.

Qué cambió, en concreto:

- **El modo estático/simulación ya no es estado del backend.** Pasó a ser `VITE_DATA_SOURCE`
  del dashboard, que se resuelve al arrancar. El backend se comporta siempre como en
  producción.
- **El envío manual de telemetría ya no pasa por acá.** `POST /api/simulacion/telemetria`
  desapareció junto con todo `/api/simulacion/**`. El simulador publica **directo al broker**,
  en el mismo topic y con el mismo payload que el firmware, así que la lectura entra por la
  ingesta de siempre.
- **El backend dejó de publicar telemetría.** Consume la del nodo y publica **sólo comandos a los
  actuadores** (`nursery/zone/{zona}/sector/{sector}/command`, `ComandoActuadorPublisher`). El
  publicador de telemetría existía únicamente para que el backend se publicara lecturas a sí
  mismo.

## Autenticación y permisos (HU-01 / HU-20)

Toda la API exige **sesión de usuario y permiso**, salvo tres excepciones: `POST /api/auth/login`,
`/ca.pem` y el contrato del dispositivo de captura (`/api/camara/v1/**`, que tiene su propio
token). Es *deny-by-default*: una ruta que no tiene permiso declarado queda cerrada para todos.

### Dos cadenas de Spring Security (`seguridad/SeguridadConfig`)

| Cadena | Rutas | Credencial | Filtro |
|---|---|---|---|
| `@Order(1)` | `/api/camara/v1/**` | Token JWT del dispositivo | `config/CamaraAuthFilter` |
| `@Order(2)` | todo lo demás | Cookie de sesión de usuario | `seguridad/SesionFilter` |

Las credenciales no se cruzan: un token de cámara contra `/api/nursery` recibe `401`, y una
sesión de usuario contra `/api/camara/v1/config` también.

Se apagan a propósito, documentado en el código: CSRF (la cookie es `SameSite=Strict`),
`formLogin`, `httpBasic`, `logout` de Spring, la sesión HTTP del contenedor y el
`Cache-Control: no-store` que Spring agrega por defecto (rompería el `ETag` de las imágenes).

### Sesiones

- `POST /api/auth/login` (`{"username","clave"}`) → `200` con el perfil (nombre, rol, permisos,
  `inactividadMin`, `debeCambiarClave`) y la cookie `YERBA_SESION` (`HttpOnly; SameSite=Strict;
  Path=/api`). La respuesta nunca trae el identificador de sesión.
- Ante **cualquier** falla responde lo mismo: `401 {"error":"Credenciales incorrectas"}`. Usuario
  inexistente, clave errónea, suspendido o dado de baja son indistinguibles, también en el tiempo
  de respuesta (hash de relleno; el estado se evalúa después de la clave).
- La sesión es opaca y vive en la tabla `sesion` (se guarda su SHA-256, nunca el valor). Por eso
  revocar es inmediato: un cambio de rol, una suspensión, una baja, un restablecimiento o un cambio de la
  matriz del rol cortan las sesiones en el acto (`401 SESION_REVOCADA` en la próxima petición).
- **Inactividad:** el backend decide. Si `ahora − ultima_actividad` supera el tiempo máximo
  (60 min por defecto, editable 5–480 desde `PUT /api/seguridad/politica`) responde
  `401 SESION_EXPIRADA`. **Sólo cuenta como actividad lo explícito**: el login, toda petición que
  no sea `GET` y `POST /api/auth/actividad`, que el dashboard manda cuando detecta interacción. Los
  sondeos (`GET`) no mantienen viva la sesión.
- `GET /api/auth/perfil`, `POST /api/auth/logout`, `PUT /api/auth/clave` (`{"actual","nueva"}`,
  cierra las demás sesiones del usuario).
- Contraseña temporal (alta, restablecimiento, admin inicial): mientras no se cambie, todo lo demás
  responde `403 {"motivo":"CAMBIO_CLAVE_REQUERIDO"}`.
- Un barrido diario (`yerbanalytics.auth.barrido-cron`) borra las sesiones cerradas o inactivas
  hace más de 7 días.

Cuerpos de error: `401 {"error","motivo": SIN_SESION|SESION_EXPIRADA|SESION_REVOCADA}` y
`403 {"error","permiso":"reglas.editar"}`.

### Clientes que no son navegador

El simulador, el servicio de inferencia y la suite de conformidad usan una cuenta de rol
**Servicio** que da de alta el Administrador: hacen login, guardan el `Set-Cookie` y lo reenvían
en `Cookie:`; ante un `401` vuelven a iniciar sesión y reintentan una vez. El backend no tiene
ninguna rama para ellos: son usuarios como cualquier otro.

### Permisos: catálogo, matriz y mapa de rutas

- **Roles** (`seguridad/Rol`): Administrador, Ingeniero Agrónomo, Productor Viverista, Operario y
  Servicio. Fijos.
- **Permisos** (`seguridad/Permiso`): catálogo fijo en código, con su grupo y su par de lectura.
  Un permiso de edición no implica el de lectura; la matriz se rechaza si falta el par.
- **Matriz** (`rol_permiso`): la edita el Administrador (`GET /api/roles`,
  `PUT /api/roles/{rol}/permisos`). Se siembra con la matriz por defecto **sólo si la tabla está
  vacía**. Salvaguardas: el Administrador nunca pierde `usuarios.gestionar` ni `auditoria.ver`
  (`409`), y nunca queda el sistema sin un Administrador activo (`409`).
- **Mapa ruta → permiso** (`seguridad/MapaPermisos`): **el único lugar** donde se declara qué
  permiso exige cada ruta. Al agregar un endpoint, se le agrega una línea ahí;
  `MapaPermisosCoberturaTest` falla si alguno quedó sin declarar.

| Rutas | Permiso |
|---|---|
| `GET /api/nursery`, `GET /api/configuracion/demo-expo` | `vivero.ver` |
| `PUT /api/configuracion/demo-expo` | `demo-expo.configurar` |
| `GET` / `PUT /api/configuracion/diagnostico` | `diagnosticos.ver` / `configuracion.editar` |
| `GET` / `PUT /api/configuracion` | `configuracion.ver` / `configuracion.editar` |
| `GET /api/diagnosticos/**` / `POST /api/diagnosticos` | `diagnosticos.ver` / `diagnosticos.registrar` |
| `GET /api/historial` | `historial.ver` |
| `GET /api/rules/**` / `PUT /api/rules/parametros` | `reglas.ver` / `reglas.editar` |
| `GET` / `POST`,`PUT /api/hardware/**` | `hardware.ver` / `hardware.gestionar` |
| `POST /api/hardware/{id}/reparacion` | `hardware.reparar` |
| `GET` / `POST`,`PUT /api/topologia/**` | `topologia.ver` / `topologia.gestionar` |
| `GET /api/capturas/**` / `POST /api/capturas/ordenes` | `capturas.ver` / `capturas.ordenar` |
| `/api/camara/vinculacion`, `/api/camara/dispositivos/**` | `camara.gestionar` |
| `GET` / `POST /api/pasadas/**` | `pasadas.ver` / `pasadas.operar` |
| `GET` / `POST`,`DELETE /api/bloqueos/**` | `bloqueos.ver` / `bloqueos.gestionar` |
| `/api/usuarios/**`, `/api/roles/**`, `/api/seguridad/politica` | `usuarios.gestionar` |
| `GET /api/auditoria/**` | `auditoria.ver` |
| `/api/auth/perfil`, `/actividad`, `/logout`, `/clave` | sólo sesión |

Los permisos se evalúan en cada petición contra la matriz vigente (caché en memoria que se
invalida al guardar): un cambio rige en la petición siguiente.

### Gestión de usuarios (`/api/usuarios`)

Alta (`username` de 3–40 caracteres `[a-z0-9._-]`, único sin distinguir mayúsculas e incluso
frente a bajas; contraseña temporal ≥ 8 y distinta del username), edición de nombre y rol,
`/suspender`, `/reactivar`, `/baja` (lógica y definitiva) y `PUT /{id}/clave` (restablecimiento). Nadie
puede suspenderse ni darse de baja a sí mismo.

### Auditoría de seguridad (`auditoria_seguridad`)

Cada cambio sobre usuarios, matriz o política queda registrado **en la misma transacción**:
si la auditoría falla, el cambio se revierte. Append-only en tres capas: entidad `@Immutable`
con repositorio sólo de alta; **triggers** que rechazan `UPDATE`, `DELETE` y `TRUNCATE`; y una
**cadena de hashes** SHA-256 que `GET /api/auditoria/verificacion` recorre para detectar una
fila alterada por fuera. Consulta: `GET /api/auditoria?autor=&objetivo=&tipo=&desde=&hasta=&pagina=&tamanio=`.

Los triggers los instala la app al arrancar (`SeguridadInicializador`, script idempotente
`src/main/resources/auditoria-triggers.sql`): **no es una migración manual más**. Si el usuario de
la base no puede crear funciones, el arranque falla con un mensaje que lo dice; en ese caso corré
el script a mano con un usuario que pueda:

```bash
psql -U <usuario-con-permisos> -d yerbanalytics -f src/main/resources/auditoria-triggers.sql
```

La cadena es evidencia de alteración, no prueba criptográfica: quien tenga acceso total a la
base podría recalcularla entera.

### Administrador inicial y configuración

| Propiedad | Default | Para qué |
|---|---|---|
| `yerbanalytics.auth.admin-inicial.usuario` | `${YERBANALYTICS_ADMIN_USUARIO:admin}` | Nombre del Administrador que se crea si no hay ninguno activo |
| `yerbanalytics.auth.admin-inicial.clave` | `${YERBANALYTICS_ADMIN_CLAVE:}` | Su contraseña temporal. Obligatoria si hay que crearlo: vacía → el arranque se frena |
| `yerbanalytics.auth.usuarios-iniciales` | `${YERBANALYTICS_USUARIOS_INICIALES:}` | Cuentas `usuario:ROL:clave,…` que se crean si su usuario no existe |
| `yerbanalytics.auth.cookie-secure` | `false` | Atributo `Secure` de la cookie. Encendelo si todo va por HTTPS |
| `yerbanalytics.auth.barrido-cron` | `0 30 3 * * *` | Barrido de sesiones viejas |

Las variables se leen de `Desarrollo/backend/.env` (plantilla: `env.example`, no se versiona) vía
`spring.config.import`; una variable de entorno del sistema con el mismo nombre le gana al archivo.
Los **usuarios iniciales** se crean sólo si el nombre no existe (bajas incluidas) y **nunca se
modifican después**: cambiar la clave en `.env` no toca una cuenta ya creada (eso se hace desde la
vista Usuarios). Las de rol `SERVICIO` nacen con la clave definitiva, porque no hay persona que pueda
cambiarla; las de personas, con clave temporal. Cada alta queda en la auditoría como `USUARIO_ALTA`
del sistema. Una entrada mal formada o una clave que no cumple las reglas frena el arranque.

> **Dashboard y API tienen que compartir esquema y host.** `localhost:5173` → `localhost:8000`
> (o `IP:5173` → `IP:8000`) es *same-site* y la cookie viaja. Si el dashboard va por `http` y
> `VITE_API_BASE_URL` apunta a `https://…:8443`, el navegador los trata como sitios distintos y no
> manda la cookie: el login "funciona" pero la sesión no se establece.

Ningún dato existente se migra (no había usuarios). Las tablas nuevas las crea Hibernate.

**Riesgo conocido:** no hay bloqueo por intentos fallidos (para no abrir un vector de denegación
contra el Administrador). BCrypt costo 10 vuelve lenta la fuerza bruta.

## Arquitectura

El proyecto sigue el patrón multicapa clásico de Spring Boot:

- `controller/`: Controladores REST. Definen los endpoints, rutas y manejan las peticiones HTTP.
- `config/`: Clases de configuración global (CORS, propiedades, beans).
- `seguridad/`: Autenticación, roles, permisos, gestión de usuarios y auditoría (HU-01 / HU-20).
- `dto/`: Objetos de Transferencia de Datos. Mantienen paridad con `domain.ts` del frontend.
- `service/`: Lógica de negocio. `NurseryService` genera el snapshot del vivero.
- `engine/zone/`: reglas que deciden sobre una macro-zona entera y su motor (`ZoneRuleEngine`); `nutrition/`: el plan de nutrición (N-01, N-02).
- `service/mock/`: Generador determinístico portado del mock del frontend.

## Endpoints

- `GET /api/nursery`: Devuelve el snapshot completo del vivero (`NurseryData`). Hoy usa un generador determinístico con semilla configurable (`yerbanalytics.mock.seed`, default `20260613`).

### Motor de reglas: catálogo de parámetros y traza de evaluación

Los umbrales que comparan las reglas (antigüedad de la lectura, umbrales y volúmenes de riego,
lluvia, saturación, ventana horaria, UV, confianza mínima del diagnóstico, dosis en 24 h, aperturas de
mediasombra…) viven en **un catálogo único** (`engine/parametros/`, 21 parámetros), **no** en
`application.properties` ni en `configuracion_operativa`. Definiciones y valores de fábrica están en
código; en la base (`parametro_regla`) sólo hay los overrides. Cada regla declara los parámetros que
usa (`Rule.parametros()`) y sólo puede leer esos. Sumar un umbral nuevo es agregarlo al catálogo, no
una constante en la regla.

Fuera del catálogo quedan, a propósito, el horario por defecto de la mediasombra (configuración operativa),
el intervalo de despacho de riego, los parámetros del cliente de pronóstico (`yerbanalytics.weather.*`)
y el intervalo del pulso de sensado y evaluación (configuración operativa).

- `GET /api/rules/parametros`: catálogo normalizado (`reglas` y `parametros`, cada parámetro una
  vez con su `usadoPor`).
- `PUT /api/rules/parametros`: edición en lote, todo o nada (`{"cambios":[{"clave","valor"}]}`;
  `valor: null` restablece la fábrica). Valida tipo, rango y restricciones cruzadas (crítico < umbral
  de riego < humedad objetivo; bloqueo ≤ alerta de saturación; el volumen máximo debe caber en los
  1200 s de la válvula); si algo falla responde `400 {"errores":[{"clave","mensaje"}]}` y no persiste
  nada. El historial lo firma el usuario de la sesión.
- `GET /api/rules/evaluaciones/{sectorId}?origen=TELEMETRIA`: última traza de evaluación
  del sector (qué recibió cada regla contra qué umbral). Vive en memoria: `204` si todavía no se
  evaluó desde el arranque, `404` si el sector no existe. Una regla de diagnóstico sin diagnóstico
  nuevo figura `OMITIDA_SIN_DIAGNOSTICO_NUEVO`.
- `GET /api/rules/schema`: grafo de reglas; cada nodo de regla trae sus `parametros` y `activa` (`false` en las apagadas, como las retiradas: el historial no las dibuja).

El dashboard lo muestra en la sección **Motor de reglas** (`/reglas`): pestaña *Parámetros* (edición
del catálogo) e *Inspector* (el grafo coloreado con la última evaluación de un sector).

### Pulso de sensado: el único momento en que decide el motor

Cada `intervaloSensadoMinutos` (60-360 min, 240 por defecto), en franjas ancladas a las 02:00 (con 240:
02, 06, 10, 14, 18 y 22 h), `SensingScheduler` emite un **pulso**: `PulsoSensadoService` publica
`LEER_AHORA` a todas las macro-zonas y espera la **primera telemetría de cada una recibida desde el
pedido** (la telemetría no trae `commandId`: la correlación es por ventana, como la secuencia
`LECTURA`). Esa primera respuesta persiste la lectura y evalúa el motor para los 100 sectores de la
zona. El intervalo se relee en cada tick, así que un cambio rige sin reiniciar; un pulso perdido con el
backend caído no se recupera.

- **No hay barrido.** `NurseryWatchdog` y `intervaloEvaluacionMinutos` se eliminaron. Toda otra
  telemetría (arranque del nodo, secuencia `LECTURA`, segunda respuesta, pulso sin motor) sólo actualiza
  la lectura y el estado de los sectores. Lo reactivo de la mediasombra (pico de UV) lo deciden las reglas de zona en
  el pulso; el plan de rustificación y el horario por defecto quedan fuera del motor (`HorarioMediasombraService`).
- **Pulso manual**: `POST /api/pulsos {"ejecutarMotor": bool}` (`true` si se omite) → `202
  {id, origen, ejecutaMotor, pedidoEn}` · `409 {"error"}` si hay uno en curso. Permiso `pasadas.operar`.
  Pide a todas las zonas y sólo se usa por API: Demo Expo usa la secuencia `LECTURA` con `ejecutarMotor`,
  que emite un pulso con motor sólo de su zona (ver "Secuencias"). No corre las franjas. Un programado
  que encuentra uno en curso se omite (queda en el log).
- **Reintentos** (parámetros `sensado.*` del catálogo, editables en Motor de reglas → Parámetros): a una
  zona pendiente se le reenvía el pedido tras `sensado.espera-respuesta` (60 s) y después cada
  `sensado.espera-reintento` (1 min), hasta `sensado.reintentos` (2) reintentos; vencido el último plazo
  queda sin respuesta en ese pulso y no se evalúa. Un cambio guardado rige en el tick siguiente. Se
  quitaron `yerbanalytics.pulso.reintento-seg` e `intentos` de `application.properties` (queda sólo
  `tick-ms`): si alguien los fijaba por variable de entorno, ahora se ignoran. El pulso no toma `GuardiaHardware`: si el ESP32 está moviendo el riel, lo cubren los
  reintentos.
- **Escalamiento** (`EscalamientoNodoService`): cada pulso sin respuesta suma uno a
  `zona.pulsos_sin_respuesta` y deja una alerta de la zona (regla `PulsoSensado`): `WARNING`, `WARNING`
  y, al tercero, `CRITICAL`, que pone la zona y sus sectores **fuera de servicio** (en el panel y en
  Hardware: 0 operativo, 1-2 señal intermitente, ≥ 3 fuera de servicio). Desde el cuarto, sin alertas.
  Cualquier telemetría vuelve la cuenta a 0 (alerta `INFO` si estaba fuera de servicio). La cuenta está
  en la base: sobrevive a un reinicio.
- **Estado seguro** (S-02): al cruzar el tercero se ordena **una sola vez** `valve OFF`, `pump OFF` y
  `shade SET {targetPct: 0}` a los equipos **registrados** de la zona (válvula y bomba de cada sector, la mediasombra de
  la zona por el comando de zona; `ZoneSafeState`, por el
  mismo `ComandoActuadorPublisher`, así que `SeguimientoComandosService` vigila cada ack). La alerta
  `CRITICAL` dice cuántas órdenes salieron. No se revierte cuando el nodo vuelve: decide el motor con la
  lectura nueva (que llega en el siguiente pulso). Una zona sin equipos registrados no recibe órdenes.
- **Reintento con el nodo caído**: mientras `pulsos_sin_respuesta ≥ 3`, cada tick del planificador pide
  `LEER_AHORA` a la zona si pasaron `sensado.reintento-fuera-servicio` minutos (15) del último pedido
  (`EscalamientoNodoService.retryOfflineNodes`). Si responde, la telemetría limpia la cuenta y deja el `INFO`.
- **Umbral de lectura vieja**: `max(piso, intervalo × 60 + espera-respuesta + reintentos × espera-reintento × 60)`
  (14 580 s con los valores de fábrica). Sólo protege al riego (`StaleSensorRule`, revalidación del despacho); ya no decide
  si un nodo está fuera de servicio.
- **Diagnóstico nuevo.** Una regla que declara `usaDiagnostico()` (hoy `SupplyRule`) sólo corre si el
  sector tiene un diagnóstico que no usó: `sector.diagnosis_ts` (lo escribe el alta con el `creadoEn`)
  posterior a `diagnosis_usado_ts`. Al evaluarse, `NurseryService` copia uno en otro en la misma
  transacción: un rollback, un bloqueo manual o una rama cortada lo dejan nuevo para el pulso siguiente.
  La ingesta **no toca más el diagnóstico** del sector (antes lo inventaba a partir del estado).
- **Riesgo aceptado**: el riego reacciona como mucho una vez por pulso (hasta 4 h para el déficit
  crítico, como plantea `reglas_v2` §1.2). Para decidir en el momento, un pulso por API o, en Demo Expo, "Leer
  sensores y ejecutar motor ahora".

### Riego: reglas R-01…R-06 y despacho por tandas

El riego sigue `docs-motor-reglas-e-integracion/reglas_v2.md` §5. Las reglas **deciden y encolan**; no
abren la válvula. Un servicio de despacho (`engine/riego/DespachoRiego`) corre cada
`yerbanalytics.riego.despacho-intervalo-ms` (10 000 ms), abre hasta `riego.sectores-simultaneos` (10)
válvulas por macro-zona en orden de numeración de sector y publica `valve ON` con
`durationSec = ceil(volumen / caudal × 3600)`, con tope de 1200 s. Antes de abrir cada válvula
**revalida con los datos de ese momento**, con los mismos parámetros que las reglas: sin bloqueo
manual, humedad bajo el bloqueo por saturación y, para el riego común (R-01), hora dentro de la
ventana, sin aplicación de insumo reciente (R-06), sin lluvia prevista (R-03, pronóstico cacheado) y
sin riego en este ciclo; para R-02, el tope de horas. Lo que no pasa se descarta de la cola y deja una
alerta WARNING. Si la **lectura o la humedad no están vigentes** el despacho **pausa** (no abre nada y
conserva la ronda) en vez de descartarla, y la retoma cuando vuelve el nodo; para que no queden
solicitudes eternas, cada una **vence** al empezar el tercer ciclo de lectura contando el de su pedido.

- Un sector recibe a lo sumo **un riego común por ciclo de lectura** (franjas de
  `intervaloSensadoMinutos`, acotado a 60-360 min, ancladas a las 02:00).
- El déficit crítico (R-02) riega a cualquier hora con el volumen máximo, con un tope de 1 riego cada
  12 h por sector. **Desviación deliberada de `reglas_v2`:** la v2 aplica ese tope sólo cuando S-06 bloqueó
  la macro-zona; como S-06 todavía no existe, se aplica siempre para que un sensor roto no dispare R-02 en
  cada ciclo. Se reevalúa en `add-efectividad-y-s06`.
- Una ronda encolada **se completa** aunque la humedad se recupere: sólo la retira una cancelación de
  seguridad (saturación, bloqueo manual; ventana cerrada o pausa por aplicación, para el riego común) o
  la revalidación del despacho (lluvia pronosticada después de decidir, ya regó en el ciclo). El sensor
  sin datos no la retira: la **pausa**.
- La cola y lo que está regando están en memoria. Un reinicio pierde lo pendiente y reconstruye lo
  abierto del historial; la siguiente telemetría vuelve a decidir.
- El estado de la válvula del dashboard (`Regando` / `En cola` / `Cerrada`) lo deriva el despacho; el
  ESP32 corta solo al cumplir la duración. El ACK `SUCCESS` no cambia ese estado: `SeguimientoComandosService` lo
  escucha para alertar en el historial si el nodo informa una falla o no contesta a tiempo.
- **Riego cortado desde afuera.** Un ACK `ERROR cancelado` de la válvula (un `valve OFF` manual, la protección del
  nodo) cierra el riego en curso de ese sector: el seguimiento publica un `ActuatorStopped` y `DespachoRiego` lo saca
  de lo que está regando (el cupo se libera y el sector puede volver a regarse) y deja una alerta `INFO` con regla
  `RiegoCortado` y el sector real en el historial. Esa fila es la que la reconstrucción tras un reinicio respeta: un
  riego anterior al último corte de su sector no se da por abierto. `reemplazado` no cierra nada (otro comando más
  nuevo lo tomó). Si el sector no figuraba regando (un OFF de una secuencia de Demo Expo o del estado seguro) no
  se registra nada. Un `cancelado` sin comando pendiente (se perdió en un reinicio) no se puede atribuir a la
  válvula y no cierra nada: el riego vence solo por tiempo.
- **Cola de cargas del nodo y ACK intermedios** (`add-cola-actuadores-nodo`). El nodo ejecuta a lo sumo
  `MAX_CARGAS_SIMULTANEAS` cargas a la vez (1 por defecto: la fuente del prototipo no banca el pico de riego + insumo +
  mediasombra juntos) y encola el resto, así que el backend sigue mandando todo en un pulso. Para saber cuándo una
  carga espera y cuándo arranca, el nodo publica ACK intermedios `EN_CURSO` (`detalle.tipo` `en_cola` con `posicion` y
  `esperaMaxSec`, o `iniciado`). `SeguimientoComandosService` los usa así: **sólo un final (`SUCCESS`/`ERROR`)
  resuelve el comando**; un `EN_CURSO` lo mantiene pendiente y recalcula su plazo desde la hora de recepción
  (`en_cola`: espera máxima + duración + `ack-margen-ms`; `iniciado`: duración + margen), y publica un
  `ActuatorProgress` (`QUEUED`/`STARTED`). De ahí: `DespachoRiego` muestra la válvula **«En cola»** mientras el nodo la
  tiene encolada (sigue ocupando el cupo de la zona: el comando ya salió) y «Regando» desde `iniciado`, contando la
  duración desde el arranque; `SupplyDoseService` hace lo mismo con la bomba («En cola» en el snapshot mientras la base
  diga `Dosificando`, que es lo que impide ordenar otra dosis; la fase vive en memoria) y `SecuenciaService` no confunde
  un intermedio con el fin de un paso (recalcula su plazo). Sin intermedios (firmware viejo, sketch de la expo) todo
  funciona como antes. Los intermedios de un comando ya resuelto o desconocido se ignoran. `ActuatorShutdown` (estado
  seguro S-02, bloqueo manual) publica los `OFF` primero y la mediasombra al final con `prioridad: "INMEDIATA"`: es la
  única orden que el nodo no hace esperar. Tras un reinicio del backend lo que estaba en la cola del nodo se reconstruye
  del historial (puede cerrarse antes de tiempo; su ACK final llega como desconocido).
- **Orden de despliegue: backend antes que firmware.** Un backend viejo con el firmware nuevo descarta el ACK final
  (tomaba el `EN_CURSO` por un status desconocido y daba el comando por resuelto) y las secuencias de Demo Expo lo
  tratan como error; un backend nuevo con el firmware viejo funciona igual que siempre.

> **No operar con plantines reales sin E-01 y S-06.** Un sensor de humedad trabado en un valor seco
> plausible haría regar en cada ciclo de lectura de la ventana (~20 L por día y por sector con el 40 %
> fijo). Hoy nada lo detecta: la evaluación de efectividad marca `bloqueoRepeticion` pero ninguna regla
> lo lee. Una sonda que deja de reportar sí se detecta (la humedad vieja bloquea el riego). Es seguro
> con el simulador; para campo, antes hay que implementar E-01 + S-06 o bajar
> `riego.volumen-max-evento` y vigilar el historial.

El pronóstico (Open-Meteo) se pide con timeouts HTTP (`yerbanalytics.weather.connect-timeout-ms`,
3 s, y `read-timeout-ms`, 5 s) y se precalienta al arrancar; un refresco colgado se reemplaza a los 60 s.

**Dosificación (bomba de insumo).** Ya no la ordena `SupplyRule` directo: toda aplicación pasa por el `ApplicationService`
(sección «Aplicación de insumos»), que publica `pump ON {"ml": N}`; el firmware calcula el tiempo (1 s por ml). Mientras el sector
figura `Dosificando` no se ordena otra dosis. Ese estado lo cierra `SupplyDoseService`: al ACK final de la bomba (`ok`, `cancelado` o
un error; un `reemplazado` no) y, de respaldo, por tiempo: pasada la duración de la dosis (`duracion_seg` de su fila "Insumo") más
`yerbanalytics.actuadores.ack-margen-ms`, así un nodo que se reinicia a mitad de la dosis no deja el sector colgado (también limpia los
que quedaron así antes de un reinicio del backend).

Otros límites conocidos: R-06 depende de los eventos "Insumo"; el cupo se llena por número de sector, no por urgencia. El detalle de qué cubre y qué
no cada regla frente a la spec está en
[`diferencias-motor-reglas-vs-reglas-v2.md`](../../docs-motor-reglas-e-integracion/diferencias-motor-reglas-vs-reglas-v2.md).

### Historial: volumen del Registro de Inacción y tope de la consulta

Cuando el motor decide no actuar, `ActionExecutor` deja filas `Info` en `historial_evento`: una por
regla, para todo el sector. Un sector tiene unas 13 reglas y hay 600 sectores, así que **cada
pulso con motor deja ~7.800 filas** (~47.000 por día con 6 pulsos). Se escribe el **conjunto completo
en cada pulso**, haya cambiado o no la decisión: cada pulso queda como un ciclo completo en la vista y
el DAG de cada sector tiene siempre todas sus reglas. No hay deduplicación ni tope temporal (se retiró
`yerbanalytics.historial.inaccion-intervalo-min`): lo que acota el volumen es la cadencia del pulso.
Antes el motor corría con cada lectura (una por minuto) y se llegó a ~130.000 filas por hora; si el
volumen molesta, volver a "sólo ante un cambio" es local al `ActionExecutor`.

`GET /api/historial` **nunca devuelve la tabla entera**. Devuelve los eventos más recientes que
cumplen los filtros (`sector`, `zona`, `tipo`, `desde`, `hasta`) hasta `limit`: 1000 si no se indica,
5000 como máximo. Una consulta sin tope llegó a cargar cientos de miles de filas en memoria y colgaba
el backend y la pestaña.

## Captura de imágenes cenitales (HU-04 CA-01)

La API se divide en **dos superficies que conviene no confundir**.

### Contrato del dispositivo — `/api/camara/v1/**`

Es lo que implementa un dispositivo de captura (hoy la PWA sobre el iPhone; mañana,
probablemente, una app Android). **La fuente de verdad es
[`Desarrollo/contratos/camara/v1/openapi.yaml`](../contratos/camara/v1/openapi.yaml)**: el
backend se implementa contra el contrato, no al revés. Si el backend difiere de lo que el
contrato declara, el defecto es del backend.

| Método | Ruta | Para qué |
|---|---|---|
| `POST` | `/api/camara/v1/enrolar` | Consume el código de vinculación y devuelve la credencial |
| `POST` | `/api/camara/v1/token` | Canjea la credencial por un token de vida corta |
| `GET` | `/api/camara/v1/config` | Resolución, calidad JPEG y parámetros de operación |
| `POST` | `/api/camara/v1/heartbeat` | Señal de vida del dispositivo |
| `GET` | `/api/camara/v1/ordenes/stream` | Canal de órdenes (SSE) |
| `POST` | `/api/camara/v1/ordenes/{id}/imagen` | Entrega del JPEG (multipart) |
| `POST` | `/api/camara/v1/ordenes/{id}/fallo` | Acuse de captura fallida |

Agregar rutas acá **cambia el contrato** y exige actualizar el OpenAPI.

Verificable con la suite de conformidad:

```bash
cd Desarrollo/contratos/camara/v1/conformidad && npm test
```

### API de plataforma — fuera del contrato

La consumen el dashboard, el planificador de pasadas del riel (`POST /api/pasadas`, más abajo) y
el servicio de inferencia. Un dispositivo de captura no debe usarlas.

| Método | Ruta | Quién la usa |
|---|---|---|
| `POST` | `/api/camara/vinculacion` | Backoffice: emite el código de un solo uso |
| `GET` | `/api/camara/dispositivos` | Estado técnico de la flota de cámaras |
| `POST` | `/api/capturas/ordenes` | Emisor de órdenes (hoy, el simulador y el planificador de pasadas) |
| `GET` | `/api/capturas/ordenes/{id}` | Seguimiento de una orden |
| `GET` | `/api/capturas/{capturaId}/imagen?v={hash}` | El dashboard, para mostrar la foto. Se sirve inmutable por un año; por eso la URL que publica el backend lleva `?v=` con el comienzo del sha256: los ids de captura salen de un contador y se repiten si se purgan capturas, y sin la versión el navegador mostraría la foto vieja que tenía en caché |
| `POST` | `/api/diagnosticos` | **Alta de diagnóstico** (servicio de inferencia y carga manual del simulador) |
| `GET` | `/api/diagnosticos` | Listado de diagnósticos persistidos |
| `GET` | `/api/diagnosticos/{id}/detecciones` | Las cajas de un diagnóstico (el modal de la imagen) |
| `GET` / `PUT` | `/api/configuracion/diagnostico` | Criterio agronómico del diagnóstico (lo lee el servicio de inferencia en cada ciclo) |

> **`POST /api/diagnosticos` es un camino único, sin variantes.** Es el mismo endpoint que va
> a usar el servicio de inferencia y el mismo que usa hoy una carga manual. No hay endpoint
> alternativo, no hay columna que marque el origen y no hay ningún estado global del backend
> que condicione el alta — el modelo tampoco va a depender de uno.
>
> El modelo es Keras/Python: aunque corra en la misma máquina, no vive dentro del JVM. El
> diagnóstico entra por HTTP en cualquier caso.
>
> Consecuencia asumida: no se pueden purgar selectivamente los diagnósticos de prueba. La
> purga posible es por fecha o por sector, que alcanza porque todo diagnóstico está anclado a
> una captura fechada.

### Diagnósticos: contrato del alta y criterio

Cambio `openspec/changes/add-diagnostico-por-detecciones/` (design D5, D7-D9 y D12, que es el
contrato compartido con el frontend, el servicio de inferencia y el simulador). El diagnóstico lo
elige el **módulo de diagnóstico** del servicio de inferencia a partir de todas las detecciones del
modelo; el backend lo valida, lo guarda y lo sirve, pero no lo recalcula.

**Alta — `POST /api/diagnosticos`** (BREAKING respecto del contrato anterior):

```json
{
  "capturaId": "CAP-…",
  "estado": "Clorosis",
  "sev": "Baja",
  "confidence": "Media",
  "conf": 62.0,
  "detections": [{"label": "Clorosis", "score": 0.62, "box": [0.12, 0.30, 0.18, 0.37]}]
}
```

| estado | `sev` | `confidence` | `conf` |
|---|---|---|---|
| anómalo (Clorosis, Estrés solar, Plaga foliar, Daño fúngico, Daño biótico, Ácaro) | obligatoria | obligatoria | obligatorio |
| Sano | ausente | obligatoria | obligatorio |
| No concluyente | ausente | ausente | ausente |

- `sev` y `confidence` son `Baja`, `Media` o `Alta`. En `sev`, ausente / `null` / `"—"` significan
  "sin severidad", y se persiste `—` (convención de `diagnosis_sev`).
- `conf` es el **score promedio × 100** del estado diagnosticado (0-100), ya no "la confianza".
- `detections` es opcional (la carga manual no trae): como máximo 2000; cada una con `label` de la
  taxonomía, `score` en `[0, 1]` y `box` = `[x1, y1, x2, y2]` normalizada en `[0, 1]` con `x1 < x2` e
  `y1 < y2`. Una sola detección inválida rechaza el alta completa.
- Todo error es `400 {"error": "<mensaje apto para mostrar>"}`, que nombra el campo.
- El alta actualiza el diagnóstico vigente del sector, incluido el nivel (`sector.diagnosis_confidence`).

**Concluyente** = estado distinto de No concluyente (D9). Ya no depende de
`diagnostico.confianza-minima`, que sigue siendo un parámetro del motor (lo usa `SupplyRule`).

**Lectura.** `Diagnostico` (listado), `DiagnosisCard` (snapshot) y `Diagnosis` (sector) traen
`conf` (nulo en No concluyente), `confidence` y —las dos primeras— `detectionCount`; **no** traen las
cajas, que serían decenas de KB por tarjeta. Se piden aparte:
`GET /api/diagnosticos/{id}/detecciones` → `{"detections": [...]}`, o `404 {"error": …}` si el id no
existe (incluye los `DG-` que el snapshot deriva del sector). Las detecciones se guardan como JSON en
`diagnostico.detections` (texto): sólo se leen juntas para dibujarlas.

**Criterio de diagnóstico — `GET/PUT /api/configuracion/diagnostico`** (tabla `diagnosis_criteria`):
bandas de severidad por estado anómalo, en cantidad de hojas, y orden de criticidad.

```json
{
  "severityBands": [
    {"state": "Clorosis",     "minimum": 5, "mediumFrom": 10, "highFrom": 20},
    {"state": "Estrés solar", "minimum": 5, "mediumFrom": 10, "highFrom": 20},
    {"state": "Plaga foliar", "minimum": 2, "mediumFrom": 5,  "highFrom": 10},
    {"state": "Daño fúngico", "minimum": 2, "mediumFrom": 4,  "highFrom": 8}
  ],
  "criticality": ["Daño fúngico", "Plaga foliar", "Clorosis", "Estrés solar"]
}
```

- Se siembra con esos valores si la tabla está vacía; nunca pisa uno editado.
- `severityBands` sale siempre en ese orden; `criticality[0]` es el más crítico.
- El `PUT` reemplaza el criterio completo y devuelve el guardado. Rechaza con `400 {"error"}`
  bandas que no sean enteros con `1 ≤ minimum < mediumFrom < highFrom`, un estado que falte, sobre o
  se repita, y una criticidad que no sea una permutación de los cuatro estados. Cada cambio queda en
  el historial como recalibración, firmado por el usuario de la sesión.
- Es un recurso aparte de `/api/configuracion` a propósito: la lectura pide `diagnosticos.ver`,
  que tiene la cuenta de rol Servicio del servicio de inferencia (no tiene `configuracion.ver`). No
  vive en el catálogo de parámetros del motor: describe el diagnóstico, no una acción.
- Los umbrales de **confianza** por clase no están acá: son del equipo y se configuran por variables
  de entorno del servicio de inferencia.

> **`SupplyRule` compara ahora el score promedio × 100.** El motor no se tocó en este cambio:
> `SupplyRule` sigue leyendo `sector.diagnosis_conf` contra `diagnostico.confianza-minima` (70 %), pero
> ese número dejó de ser "la confianza del diagnóstico" y pasó a ser el score promedio de las
> detecciones del estado elegido, que con bandas de confianza por clase no es comparable entre
> clases. Un No concluyente llega con `conf` nulo y la regla lo trata como "sin dato". La regla
> queda **por rediseñar** sobre los niveles de severidad y confianza en un cambio posterior (ver
> `docs-motor-reglas-e-integracion/diferencias-motor-reglas-vs-reglas-v2.md`, §4.5).

### Autenticación

Las rutas del contrato exigen el token del dispositivo, y las atiende su propia cadena de Spring
Security (`CamaraAuthFilter`, ver [Autenticación y permisos](#autenticación-y-permisos-hu-01--hu-20)).
La API de plataforma de la tabla anterior exige, en cambio, **sesión de usuario** con el permiso
que corresponda (`camara.gestionar`, `capturas.ordenar`, `capturas.ver`, `diagnosticos.registrar`).
Las credenciales no se cruzan: el token del dispositivo no abre la plataforma ni una sesión abre
el contrato.

El flujo es: la plataforma emite un **código de un solo uso** → el dispositivo se enrola y
recibe una credencial de renovación → la canjea por **tokens de ~15 minutos**. Nada de larga
duración vive en el código del cliente, porque el bundle de una PWA es público.

`Authorization: Bearer` es la vía canónica en todos los endpoints. El token por query string
se admite **sólo** en el stream, porque `EventSource` no permite fijar headers en el
navegador; un cliente nativo usa el header también ahí.

### Almacenamiento de las imágenes

El JPEG va al filesystem (`yerbanalytics.capturas.dir`, particionado `AAAA/MM/DD`) y en
Postgres queda la metadata con la ruta, el hash y la correlación. Como `bytea` serían varios
GB por semana en el `pg_dump` y en la replicación. Se escribe **primero el archivo y después
la fila**: un archivo huérfano es basura recolectable, pero una fila apuntando a un archivo
inexistente sería un `404` a la vista del usuario.

Si el directorio no existe y no puede crearse, o no es escribible, **la aplicación falla al
arrancar** — mejor eso que descubrirlo en la primera subida, con una pasada del riel perdida.

### HTTPS para la app de cámara

El backend expone un **conector TLS adicional en el 8443**; el 8000 sigue siendo HTTP, así que
el dashboard no se entera. Es necesario porque la PWA de cámara se sirve por
HTTPS (`getUserMedia` exige origen seguro) y una página HTTPS no puede llamar a un endpoint
HTTP.

```bash
cd Desarrollo/certs && ./generar-certificados.sh <ip-de-esta-maquina>
```

El backend toma `../certs/servidor.p12` y además publica la CA en **`/ca.pem` por el puerto
HTTP**, para que el iPhone pueda instalarla sin haber confiado en nada todavía (problema de
arranque en frío). Detalle del trámite en el iPhone: [README de la app de cámara](../camara/README.md).

Se desactiva con `yerbanalytics.https.enabled=false`.

### Configuración

```properties
yerbanalytics.https.enabled=true                 # conector TLS adicional
yerbanalytics.https.port=8443
yerbanalytics.https.keystore=../certs/servidor.p12
yerbanalytics.capturas.dir=./capturas
yerbanalytics.capturas.timeout-orden-seg=60      # plazo antes de vencer una orden
yerbanalytics.capturas.max-intentos=3            # antes de mandarla a ERROR
yerbanalytics.capturas.jwt-secret=...            # SOBRESCRIBIR EN PRODUCCIÓN
yerbanalytics.capturas.ancho-max=1920            # la resolución la fija el backend,
yerbanalytics.capturas.calidad-jpeg=0.85         # no el cliente
yerbanalytics.cors.origins=...                   # agregar el origen de la app de cámara
```

La confianza mínima de un diagnóstico concluyente (HU-04 CA-03) ya no es una propiedad: es el
parámetro `diagnostico.confianza-minima` del catálogo de reglas, compartido con `SupplyRule`.

`yerbanalytics.cors.origins` importa: el iPhone carga la app desde la IP de la máquina en la
LAN y por HTTPS, no desde `localhost`. Acepta patrones (`https://192.168.0.*:5190`).

### Dos decisiones que conviene no revertir sin leer el porqué

- **`spring.jpa.open-in-view=false`.** Con el stream SSE es fatal, no sólo una mala práctica:
  mantiene el `EntityManager` vivo mientras dure el request y, fuera de una transacción,
  Hibernate no suelta la conexión. Cada dispositivo conectado retendría una conexión JDBC de
  forma permanente; con el pool por defecto, diez cámaras dejarían la plataforma sin
  conexiones.
- **El despacho de órdenes no corre dentro de una transacción.** Escribir en el stream puede
  bloquear si el dispositivo dejó de leer, y hacerlo con una conexión JDBC tomada tiene el
  mismo efecto que lo anterior. Ver las notas en `CapturaService`.

### Migración

Las cuatro tablas (`orden_captura`, `captura`, `dispositivo_camara`, `diagnostico`) son
nuevas y no tocan ninguna existente: `ddl-auto=update` las crea sola. El DDL queda
documentado en [`migracion-captura-imagenes.sql`](src/main/resources/migracion-captura-imagenes.sql)
para entornos sin permisos de DDL.

## Planificador de pasadas del riel

Una **pasada** mueve el riel de la cámara, pide la foto de dos sectores y vuelve a home:
`IR_A 1` → foto del 1.er sector de la macro-zona de menor número → `IR_A 2` → foto del 2.º → `HOME`.
Una sola a la vez. Es capacidad del sistema (el backend no tiene modos): funciona siempre.

| Método y ruta | Respuesta |
|---|---|
| `POST /api/pasadas` | `202` + `Pasada` · `409 {"error"}` (ya hay una en curso, menos de 2 sectores, ningún dispositivo de captura conectado) |
| `GET /api/pasadas/actual` | `200` + `Pasada` (la en curso o la última) · `204` si no hubo ninguna desde el arranque |
| `POST /api/pasadas/actual/cancelar` | `200` + `Pasada` · `409` si no hay una en curso |

La forma de `Pasada` y sus pasos está en `openspec/changes/add-pasada-riel/design.md` §2.6.

- **MQTT.** Publica el comando en `nursery/rail/command` (QoS 1) y escucha `nursery/rail/event` con
  un adaptador propio, aparte del de telemetría (que queda idéntico). Contrato: `mqtt/ContratoRiel.java`,
  espejo de la sección "Riel" de `Desarrollo/embebido/comun/contrato.h`. Cada paso lleva un
  `commandId` y sólo cuentan los eventos que lo citan.
- **Tiempos** (`yerbanalytics.pasada.*`): sin ningún evento a los `timeout-aceptacion-seg` (5) se
  republica el *mismo* comando; al doble, `RIEL_SIN_RESPUESTA`. Un movimiento tiene
  `timeout-movimiento-seg` (120) y una foto `timeout-captura-seg` (240). `tick-ms` (1000) es la
  cadencia del orquestador, que corre en su propio carril (`pasadaScheduler`).
- **Fallas.** Una foto fallida no corta la pasada (termina `FALLIDA`); un movimiento fallido omite
  lo pendiente y manda a home; si falla HOME no se reintenta. Cancelar manda `HOME` (el firmware
  aborta el movimiento en curso).
- **El estado vive en memoria.** Un reinicio a mitad pierde la pasada (`GET` → `204`); las órdenes,
  capturas y diagnósticos, que son el registro real, ya se persisten. Se puede iniciar otra enseguida.
- **Limitación.** Un `LLEGO` del firmware no prueba que el carro se movió: éste no puede saber si el
  motor tiene alimentación. Y con el final de carrera de home en falso, `HOME` termina al instante
  (`pasos: 0`). Detalle en el README del sketch `vivero_esp32_red`.
- **Diagnóstico.** `GET /api/pasadas/actual` lo completa por captura aunque la pasada ya haya
  terminado: llega ~1 min después de la última foto, cuando el servicio de inferencia barre.

### Pasada diaria

Si `pasadaDiariaActiva` (configuración operativa, `PUT /api/configuracion`; desactivada de fábrica para
que en desarrollo el riel no se mueva sin que nadie lo pida), `PasadaProgramadaService` inicia **la misma
pasada** que `POST /api/pasadas` en el minuto de `pasadaDiariaHora` (`HH:mm`, hora del vivero). Revisa
cada `yerbanalytics.pasada.programada-tick-ms` (20 s) en el carril `pasadaScheduler` y dispara a lo sumo
una vez por minuto. No guarda "ya corrió hoy": un cambio de hora rige desde el minuto siguiente, y un
reinicio posterior a la hora no la recupera. Si se rechaza (pasada o secuencia en curso, sin topología,
sin cámara conectada) deja una alerta `WARNING` de la macro-zona del recorrido (regla
`PasadaProgramada`) con el motivo, y no se reintenta. La pasada manual no cambia.

### Interruptor "Demo Expo"

`GET`/`PUT /api/configuracion/demo-expo` (`{"visible": true|false}`; `400` si falta `visible`) decide si
el dashboard muestra la pestaña. Se guarda en `preferencia_dashboard` (fila única), que crea
Hibernate: no hay migración manual. Sólo oculta la pestaña; los endpoints de pasada no dependen de él.

## Bloqueo manual (`/api/bloqueos`, HU-19 · reglas_v2 S-01)

Suspende la actuación autónoma sobre un **sector** o una **macro-zona**. Se crea y se reanuda; no se edita.

| Ruta | Qué hace |
|---|---|
| `GET /api/bloqueos?zona=&sector=` | Vigentes, del más nuevo al más viejo. `zona` incluye los de sus sectores |
| `POST /api/bloqueos {zonaId, sectorId?, motivo}` | Bloquea. `201`; `400` sin zona o motivo, o alcance inexistente; `409` si ya está bloqueado |
| `DELETE /api/bloqueos/{id}` | Reanuda (desactiva, no borra: se conserva quién y cuándo). `204`; `404` si no existe o ya se reanudó |

- **Quién bloquea sale de la sesión**, no del cuerpo.
- **Un bloqueo es de un sector o de una zona, nunca de los dos.** `NurseryService` y `DespachoRiego` tratan cualquier
  bloqueo con `zonaId` como bloqueo de toda la zona, así que uno de sector se guarda con `zonaId = null` (la respuesta
  sí informa su zona). No guardar los dos: bloquearía toda la zona.
- **Al activar** (`ManualLockService`): se cancela el riego encolado del alcance y se ordena cerrar las válvulas y
  detener las bombas registradas (`ActuatorShutdown`, el mismo mecanismo del estado seguro de la zona). La
  **mediasombra no se mueve**: queda como está. Si el broker o el historial fallan, el bloqueo ya está guardado.
- **Al reanudar** no se repone nada: decide la próxima lectura.
- Cada activación y reanudación deja una **alerta `INFO`** en el historial (usuario, alcance y motivo), atribuida a
  `ManualLockRule`.
- `ManualLockRule` (prioridad 0, `ABORT_ALL`) corta la cadena del sector. La mediasombra es de la zona: un bloqueo **de la
  zona** corta su rama en las reglas de zona (`ShadeFaultRule`) y frena también al horario de la mediasombra; un bloqueo de
  un solo sector no la toca.
- Desde el simulador: apagar la regla, el bypass `lock` y el escenario «Bloqueo manual» (README del simulador).

## Lectura inválida (S-03) y hardware incompleto (S-04)

Dos guardas de seguridad de `reglas_v2` §4 que frenan lo autónomo antes de que decida el riego, el insumo o la malla.

### S-03 · una lectura fuera del rango físico no es una lectura

`NurseryService.updateTelemetry` pasa cada lectura por `ReadingValidationGuard` **antes** de escribir en la zona. La lectura
se arma como `ReadingMetrics` (unidades canónicas: la CE ya en dS/m) y `ReadingValidator` (puro) compara cada métrica con su
rango del catálogo (`seguridad.rango-hum|temp|ph|ce|luz-min|max`; bordes incluidos; N, P y K no tienen rango). Lo que no cumple:

1. **Relectura.** Se pide `LEER_AHORA` enseguida, hasta `seguridad.relecturas-invalidas` veces (2 de fábrica), **una sola vez
   por lectura** aunque haya varias métricas inválidas. La métrica no entra a la zona: queda el último valor bueno.
   Una lectura que espera su relectura **no consume el pulso** (no es la respuesta definitiva): el motor decide cuando
   llega la que corrige o la que ya agotó las relecturas.
2. **Corrige.** Si una relectura llega válida se usa y no hay alerta; el contador de esa métrica se reinicia.
3. **Descarte.** Si la lectura y todas sus relecturas son inválidas, esa métrica se descarta en el ciclo: el `RuleContext` la
   marca (`isMetricValid`) y las reglas de riego la leen como **sin dato** por `RiegoRuleSupport.humedad` (traza
   `SIN_DATO`, no actúan); las demás métricas siguen. El dashboard conserva el último valor bueno.
4. **Alerta** (`InvalidReadingEscalation`, tabla `invalid_reading`): el primer ciclo con la métrica descartada es un
   `WARNING` «Lectura inválida en [métrica]»; si vuelve a fallar en el ciclo de lectura siguiente, `CRITICAL` «Sensor de
   [métrica] con falla: revisar», y sigue siéndolo mientras dure la racha. Dos descartes en el mismo ciclo cuentan como uno.
   Una lectura válida reinicia la cuenta.

- El contador de relecturas es por (zona, métrica, ciclo) y vive en memoria; la racha vive en la base y sobrevive a un reinicio.
- Desde el simulador: apagar la pieza `ReadingValidationGuard` (acepta cualquier valor), el bypass `retries` (no pide
  relecturas) y el escenario «Lectura inválida».
- El guard es consumidor de parámetros y depende con `@Lazy` del catálogo **y** del servicio de configuración (éste depende
  del catálogo): sin eso el contexto no arranca (`ReadingValidationGuardWiringTest`).

### S-04 · sin el actuador mapeado no se actúa

`HardwareService.coverageOf(zona, sectores)` arma, con dos consultas por zona, qué tiene registrado cada sector
(`HardwareCoverage`: electroválvula, bomba y nodo testigo de la zona) y
`NurseryService` la pone en `RuleContext.hardware`. `IncompleteHardwareRule` (prioridad **1**, entre el bloqueo manual y la
frescura) corta sólo lo que falta, sin alertas:

| Falta | Se aborta |
|---|---|
| electroválvula del sector, o nodo testigo de la zona | `ABORT_RIEGO` |
| bomba peristáltica del sector | `ABORT_INSUMO` |

La mediasombra es de la zona: si falta, la rama de la malla la corta la regla de zona `ShadeFaultRule` (S-04 por zona,
`HardwareService.mallaDe`). Nunca `ABORT_ALL`: un sector sin bomba sí se riega. «Mapeado» no es «sano»: un dispositivo con falla cuenta (eso es de S-05).
Una regla de la cadena no puede tener prioridad fraccionaria, así que las demás subieron una (`ReglasArquitecturaTest` exige
prioridades únicas). S-05 volvió a correrlas para sumar `HydraulicFaultRule` (ver «Fallas del nodo»).

> **Efecto operativo.** Un sector sin su electroválvula registrada **deja de regarse de forma autónoma** (HU-18 CA-04). El
> `data.sql` de desarrollo sólo equipa unos pocos sectores y regenerar la topología borra todos los dispositivos: para ver
> actuar al motor, registrá el hardware o usá el bypass `hardware` del simulador (Telemetría → Control de reglas).

## Fallas del nodo (S-05) y registro de reparaciones

La v2 reparte la detección: **el ESP32 detecta la falla, corta el actuador y la publica; el motor reacciona** sacando al sector (o a
la malla) de lo autónomo hasta que alguien registre la reparación. Contrato: `nursery/zone/{zona}/errores`, payload
`{nodo, sector, tipo, valor, timestamp}` (fuente de verdad `contrato.h`; espejos `ContratoNodo.TOPIC_ERRORS*`, `NodeErrorType`
y `simulador/server/contract.ts`).

| Pieza | Qué hace |
|---|---|
| `NodeErrorReceiver` (`mqtt/`) | Parsea el payload, saca la zona del tópico y entrega a `HardwareFaultService`. Un `tipo` que el contrato no define, un JSON roto o un tópico inesperado se loguean y se descartan |
| `HardwareFaultService` (`service/`) | **Único lugar que escribe `dispositivo.falla`** (+ `falla_desde`). `SIN_CAUDAL`/`CAUDAL_EXCESIVO` marcan la electroválvula del sector; `MEDIASOMBRA_FIN_DE_CARRERA` marca la mediasombra **de la zona** (se registra con la zona, una por zona). Deja UNA alerta `CRITICAL` con los textos de la v2 |
| `HydraulicFaultRule` (prioridad **2**, `GLOBAL`) | Sector con la válvula averiada: `ABORT_RIEGO` (con `CancelaRiego.TODAS`: retira también el riego ya encolado) y `ABORT_INSUMO`. **R-02 no se exceptúa**: la v2 no lo dice, por eso la alerta pide riego manual. Nunca `ABORT_ALL` |
| `ShadeFaultRule` (regla **de zona**, prioridad **13**, `MEDIASOMBRA`) | Malla averiada, sin registrar o zona bloqueada: `ABORT_MEDIASOMBRA`; `ShadingRule` no mueve la malla |

- **Una sola fuente:** no hay tabla de fallas. `HardwareService.faultsOf(sectores)` (una consulta por zona) arma `HardwareFaults` y
  `NurseryService` lo pone en `RuleContext.faults`, como `coverageOf` hace con S-04. `HardwareService` ya derivaba el estado
  operativo de `falla`, así que el panel de Hardware lo ve sin cambios.
- **Textos de la alerta (D-4):** falla hidráulica con la humedad de la macro-zona bajo el umbral de riego (`riego.umbral-humedad`,
  45 %, del catálogo) pide **riego manual** del sector; con el sustrato húmedo, no. Falla de la malla con el final de «cerrada»
  sin activar (quedó abierta) pide **cubrir a mano**.
- **Un mismo hecho por dos caminos (D-6):** la falla de la malla llega por el tópico y por el ack `falla_mecanica`
  (`SeguimientoComandosService` la entrega al mismo servicio). Mientras la marca siga con el mismo tipo, el segundo aviso es
  `DUPLICATE` y no alerta. Con el receptor apagado desde el simulador, el ack alerta como antes.
- **Reparación (D-5):** `POST /api/hardware/{id}/reparacion {detalle}` (permiso `hardware.reparar`; el detalle es obligatorio; `400`
  sin detalle o dispositivo inexistente, `409` si no tiene falla) limpia la marca, deja el evento
  **«Reparación»** en el historial con usuario y detalle y una alerta `INFO`. En el siguiente pulso el sector vuelve a evaluarse.
  S-06 reutilizará este camino.
- **Prioridades:** `HydraulicFaultRule` entra en la 2 y las reglas de riego subieron una (`ShadeFaultRule` en la 13 y
  `ShadingRule` en la 14), como en S-04. `ReglasArquitecturaTest` exige que la falla hidráulica corra antes que R-02.
- **Para probarlo sin hardware:** simulador → Automatización de reglas → «Sin caudal», «Caudal excesivo», «Falla de mediasombra»
  y «Reparación». Controles (apagar `HardwareFaultService`, bypass `fault` de las dos reglas): Telemetría → Control de reglas.

> **Efecto operativo.** Un sector con falla hidráulica **no se riega solo ni se dosifica** hasta que se registre la reparación, y con
> la mediasombra averiada la malla no recibe comandos de la regla de mediasombra. El estado seguro del nodo caído (S-02) no pasa por
> las reglas: sigue ordenando cerrar la malla.

## Mediasombra por macro-zona y plan de rustificación (`add-rustificacion-por-macrozona`)

**La mediasombra es de la macro-zona**: hay una por zona y se registra como el nodo testigo (con `zonaId`, sin sector). Su
última posición ordenada vive en `zona.mediasombra_abierta` (`GET /api/nursery` → `zonas[].mediasombra`: `abierta` |
`cerrada` | `null`); los actuadores del sector ya no la traen. Todo `shade SET` sale por el **comando de zona**
(`nursery/zone/{zona}/command`, con `actuador: "shade"`) y su ACK vuelve por `nursery/zone/{zona}/ack`
(`ContratoNodo.TOPIC_ACK_ZONA`; espejo en `contrato.h` y el simulador).

Dos cosas mueven la malla, y no se pisan:

| Quién | Cuándo | Qué |
|---|---|---|
| `HorarioMediasombraService` (`mediasombra/`, **fuera del motor**) | Cada minuto revisa; **actúa sólo en los bordes** | Abre al entrar en la ventana de hoy y cierra al salir. La ventana es la de la etapa del **plan de rustificación** de la zona o, sin plan en curso, el **horario por defecto** (`mediasombraAbiertaDesde/Hasta` en la configuración operativa, 08:00–17:00 de fábrica). Iniciar o eliminar un plan, cambiar el horario o pasar de etapa también es un borde |
| Reglas de zona (`ShadeFaultRule` 13, `ShadingRule` 14), en el pulso con motor | En cualquier momento | Hoy, cerrar ante un pico de UV. **El horario no lo corrige**: queda cerrada hasta el próximo borde de apertura (que puede ser al día siguiente) |

- Ni el horario ni el motor mandan nada si la zona tiene **bloqueo manual**, **no tiene mediasombra registrada** o la tiene
  **averiada**; el horario tampoco con la zona en **estado seguro**. Lo deja en el historial; pasado el impedimento, espera al
  próximo borde. Al arrancar el backend, la primera revisión pone cada zona en su posición si la última orden no coincide.
- Todo movimiento queda en el historial (tipo «Mediasombra»), con quién lo pidió (`HorarioMediasombraService`, la regla o el
  estado seguro). El único que publica es `MediasombraService`; el estado seguro y las secuencias de Demo Expo publican por su
  cuenta (prioridad propia, commandId propio) y registran la posición.
- **Plan de rustificación** (`PlanRustificacionService`): fecha de inicio y etapas de N días (1–60) con una ventana
  `HH:MM`–`HH:MM` (cierre después de la apertura). A lo sumo uno vigente por zona. El estado se calcula: `programado` antes del
  inicio (rige el horario por defecto), `en_curso` desde ese día y **también pasada la última etapa** (la última se sostiene),
  `cancelado` cuando alguien lo elimina; entonces vuelve el horario por defecto.
  - `GET /api/zonas/{zona}/rustificacion` → plan vigente o `204` (permiso `vivero.ver`).
  - `POST /api/zonas/{zona}/rustificacion {fechaInicio, etapas: [{dias, abiertaDesde, abiertaHasta}]}` → `400`/`409 {error}`.
  - `POST /api/zonas/{zona}/rustificacion/cancelar` (eliminar) → `409` si no hay plan. Los dos con `configuracion.editar`.
- **Simulador:** `HorarioMediasombraService` es `DemoControllable` (se apaga); `ShadeFaultRule` admite los bypass `fault` y
  `hardware`. Las secuencias de Demo Expo (abrir / cerrar / desplegar-esperar-enrollar) siguen igual, ahora por el comando de zona.

## Aplicación de insumos (`add-dosificacion-base` · reglas_v2 §8.1–8.3)

Mismo reparto que el riego: **las reglas deciden, un servicio ejecuta**. Una regla que resuelve que a un sector le corresponde
fertilizante o fitosanitario emite `ACTIVAR_BOMBA` con un `DetalleInsumo(supply, fromDiagnosis)`; el `ActionExecutor` se lo pide a
`ApplicationService.request(...)`, que devuelve `APPLIED | DEFERRED(motivo) | REJECTED(motivo)`. Código en `supply/`.

| Pieza | Qué hace |
|---|---|
| `Supply` | `FERTILIZER` / `FUNGICIDE`: dosis, intervalo y clave de cooldown de cada uno |
| `ApplicationPreconditions` | P-D1…P-D5 como función pura (`check(facts, vigentes, overrides)`); es `DemoControllable` |
| `ValidDiagnosis` | P-D1: el último diagnóstico del sector, a lo sumo `diagnostico.vigencia-horas` (48), confianza ≥ `diagnostico.confianza-minima`, no «No concluyente» |
| `ApplicationService` | verifica, publica la orden, registra (`application` + fila «Insumo» del historial que lee R-06), marca el reingreso, descuenta el tanque, y administra la cola de diferidas |
| `PendingApplicationScheduler` | cada `yerbanalytics.insumo.pendientes-revision-ms` (5 min) reintenta las diferidas |
| `TankService` | stock por insumo y por macro-zona: carga, descuento, avisos `WARNING` (bajo `insumo.stock-aviso`) y `CRITICAL` (vacío o aplicación sin stock) |

- **Resultado de las precondiciones.** P-D1 (diagnóstico) **rechaza**: un diagnóstico viejo no se arregla esperando. El resto
  (intervalo, una por día, ventana, temperatura, lluvia, humedad) **difiere**: queda en `pending_application` y se reintenta con los
  datos de ese momento hasta que la regla de origen la cancele (`cancelPending`), deje de ser válida o venza (`insumo.pendiente-vence-dias`,
  7). Lo que no se aplicó queda en el historial del sector como Registro de Inacción, una sola vez mientras el motivo no cambie.
- **El dato que falta no es un dato bueno.** Sin humedad de sustrato no se aplica; sin pronóstico, la lluvia no bloquea (se asume que no
  llueve) y la temperatura sale de la lectura de la zona (o del pronóstico). Un tanque sin registrar es un stock **desconocido**, no un
  tanque lleno: se rechaza hasta que alguien lo cargue.
- **Fitosanitario antes que fertilizante (P-D2).** Si el mismo día corresponden ambos, va el fitosanitario y el fertilizante espera al día
  siguiente. Un fitosanitario pendiente sólo frena al fertilizante si, con los datos de ahora, se aplicaría: si está trabado por su propio
  intervalo (7 días), esperarlo dejaría al fertilizante sin salir durante días.
- **Cada tick revalida.** El reintento no tiene hora fija: corre seguido y deja que las precondiciones decidan, porque las ventanas son
  parámetros del catálogo (`insumo.ventana-manana`, `insumo.ventana-tarde`; 07–10 y 17–19 de fábrica) y atar el tick a dos horas las
  desfasaría en cuanto el agrónomo las cambie.
- **Reingreso.** Un fitosanitario deja `sector.reingreso_hasta` (`insumo.reingreso-horas`, 24 h); el snapshot lo expone como
  `reentryUntil` y el dashboard lo muestra. Sólo informa: no bloquea ningún actuador.
- **Dosis y bomba.** El backend manda **mililitros** y el firmware los convierte a tiempo y corta solo. La dosis tiene como tope el que
  acepta el firmware por comando (`ContratoNodo.BOMBA_ML_MAX` = 50 ml, `LIMITE_BOMBA_ML_MAX`): una dosis mayor se rechaza al guardar, no se
  recorta en silencio. De fábrica 10 ml (la spec decía 100 ml; no entra en el firmware actual). Hay **una bomba por sector** y el comando
  no lleva el insumo: el backend sólo sabe qué insumo pidió la regla, y descuenta ese tanque; que la bomba esté conectada al tanque correcto
  es del operario. Si hace falta que el nodo elija la bomba, hay que sumar el insumo al contrato (`contrato.h`, `ContratoNodo`, el simulador y el
  firmware) y registrar dos tipos de dispositivo por sector.
- **API de tanques.** `GET /api/tanques` (`insumos.ver`) y `POST /api/tanques/{zona}/{insumo}/recarga` (`insumos.gestionar`) con
  `{liters, capacityLiters?}`: la primera carga declara la capacidad; una carga que desborda el tanque se rechaza (`400`). La carga queda en
  el historial de la macro-zona con el usuario.
- **Control desde el simulador** (`/api/demo/rules/ApplicationPreconditions`): apagada, no se aplica nada y queda constancia; bypass
  `diagnosis`, `daily`, `window`, `temperature`, `rain`, `moisture` y `stock`; cooldowns `interval-fertilizer` e `interval-fungicide`.

> **La dosificación v1 queda retirada pero no borrada.** `SupplyRule` (estado crítico + confianza) y `DailyDoseLimitRule` (1 dosis cada 24 h)
> arrancan **apagadas** (`yerbanalytics.reglas.retiradas`, ver `RuleOverridesStore`; en la traza figuran «deshabilitadas»). Hoy el único camino
> productivo que pide aplicaciones es el **plan de nutrición** (N-01, ver abajo), que no decide con el diagnóstico de IA: lo que reemplaza a
> `SupplyRule` son las reglas por diagnóstico F-* (todavía no están), así que borrarla dejaría al vivero sin dosificación por diagnóstico. Para
> probarla de punta a punta: `YERBANALYTICS_REGLAS_RETIRADAS=ninguna` (cualquier valor que no sea el nombre de una regla; un valor
> vacío puede leerse como «sin definir» y dejar la lista de fábrica) enciende `SupplyRule` de arranque, o con el modo demo se la enciende desde el simulador. `DailyDoseLimitRule` queda redundante: P-D2 ya limita una aplicación por día y por intervalo. Se borran
> junto con `insumo.max-dosis-24h` cuando entren las reglas F-* que las reemplazan.

## Plan de nutrición (`add-plan-nutricion` · reglas_v2 §8.4)

Fertilizante foliar **en todos los sectores de la macro-zona cada N días**, sin mirar lo que se mide (N-01), y adelantado si la CE baja
(N-02). Las dos son **reglas de macro-zona**: no se evalúan sector por sector sino una vez por zona, y cada aplicación pasa por el
`ApplicationService` (único camino para aplicar un insumo, ver arriba).

| Pieza | Qué hace |
|---|---|
| `engine/zone/ZoneRule` | Interfaz de una regla de macro-zona (`ConsumidorParametros` + `DemoControllable`). Decide y devuelve acciones; no ejecuta |
| `engine/zone/ZoneRuleEngine` | Corre las `ZoneRule` por prioridad, **una vez por zona**, en `NurseryService.updateTelemetry` después del bucle de sectores y sólo con el motor encendido (pulso con motor). Cada regla ejecuta sus acciones antes de que corra la siguiente |
| `engine/zone/ZoneActionExecutor` | Ejecuta las acciones de zona: la transición del plan (`UPDATE_NUTRITION_PLAN`) y las alertas (`ALERTA`, una por evento) |
| `engine/zone/ZoneTraceStore` | Última traza de las reglas de zona, por zona, en memoria. `GET /api/rules/evaluaciones/{sectorId}` la **suma** a la del sector, así el Inspector las dibuja en el mismo grafo (rama `INSUMO`) |
| `nutrition/NutritionPlanRule` (N-01) | Cuando pasaron los días del intervalo desde la última aplicación del plan (o la zona nunca recibió el plan, o N-02 lo adelantó) pide la **tanda**: fertilizante en cada sector de la zona. `INFO` al iniciar |
| `nutrition/LowEcRule` (N-02) | Con la CE media de las últimas `nutricion.ce-lecturas` (6) lecturas de pulso < `nutricion.ce-baja` (0,5 dS/m) **y** sin fertilizar en la zona hace `nutricion.sin-fertilizar-dias` (5), adelanta N-01 y deja el `INFO` «CE baja: la fertilización de base del sustrato podría estar agotada» |
| `nutrition/NutritionPlanService` | El estado del plan de cada zona y sus transiciones (`START`, `ADVANCE`, `COMPLETE`); el intervalo de la zona (`setInterval`) |
| `nutrition/NutritionSchedule` | Cuándo vence el plan (función pura): la usan N-01 para decidir y el snapshot para mostrar la próxima aplicación |

- **La tanda.** Iniciar el plan pide fertilizante en cada sector de la zona y abre una tanda. Mientras algún sector espera su ventana (queda
  en `pending_application`, lo reintenta `PendingApplicationScheduler` cada 5 min) la tanda está abierta y N-01 no vuelve a pedir. Se cierra
  cuando ninguno espera —todos se aplicaron o se descartaron con motivo— y **recién ahí corre el intervalo**: `zona.last_plan_application_ts`
  es la hora en que se aplicó el último sector, no la de la decisión. Los sectores que no cumplen las precondiciones P-D2…P-D5 no cambian nada:
  quedan pendientes y se reintentan en la ventana siguiente, como cualquier aplicación.
- **Estado en la zona** (columnas nuevas, se agregan solas con `ddl-auto=update`; sin migración manual): `nutrition_plan_interval_days`
  (`NULL` = rige el del catálogo), `last_plan_application_ts`, `nutrition_plan_due_ts` (el adelantamiento de N-02) y `nutrition_batch_start_ts`
  (la tanda en curso). Sobrevive a un reinicio. Una zona sin ninguna aplicación del plan se considera **vencida** (propuesta de D-5: aplica en
  la próxima ventana apta); por eso, con N-01 encendida, el primer pulso con motor de cada zona pide la tanda.
- **Quién queda fuera de la tanda.** Un sector con bloqueo manual propio (S-01), sin bomba registrada (S-04) o con falla hidráulica sin reparar
  (S-05) no recibe el pedido y queda en su Registro de Inacción con el motivo. Con la **zona entera bloqueada** el plan queda vencido y espera a que
  se reanude (no se descarta la tanda por una causa que el operario puede levantar). Un tanque sin registrar rechaza cada aplicación (`CRITICAL`
  del tanque) y la tanda se cierra descartada: **cargá los tanques antes del primer pulso**.
- **N-02.** Sin las lecturas necesarias dice «sin dato» y no actúa; «sin fertilizar» cuenta **cualquier** aplicación de fertilizante en la zona,
  del plan o correctiva. Mientras el plan está adelantado o en curso no vuelve a avisar (una alerta por evento).
- **Intervalo por macro-zona.** `PUT /api/zonas/{zonaId}/plan-nutricion {"intervalDays": n}` (permiso `configuracion.editar`) fija los días de la
  zona; el rango es el de `nutricion.intervalo-plan` (3–30, fábrica 7) y uno fuera de rango se rechaza (`400`, no se recorta). Una zona sin
  intervalo propio usa el del catálogo. Con un intervalo menor a `insumo.intervalo-fertilizante` (5 d) el límite real es el de P-D2 por sector.
  El plan se lee de `GET /api/nursery` → `zonas[].nutritionPlan` (`intervalDays`, `customInterval`, `lastApplicationTs`, `nextDueTs`,
  `batchOpen`, `advanced`).
- **Dosis.** Las aplica el `ApplicationService` con `insumo.dosis-fertilizante` (10 ml de fábrica, 1–50): la v2 dice 100 ml por sector, pero el
  firmware sólo acepta 50 ml por comando (ver «Aplicación de insumos»). El plan no tiene una dosis propia.
- **Parámetros** (familia `NUTRICION`, editables en Motor de reglas → Parámetros): `nutricion.intervalo-plan`, `nutricion.ce-baja`,
  `nutricion.ce-lecturas`, `nutricion.sin-fertilizar-dias`.
- **Si una regla de zona lanza**, la excepción se propaga como en las de sector (la ingesta revierte y el pulso siguiente lo reintenta) y antes
  se guarda la traza parcial con esa regla en `ERROR`.
- **Control desde el simulador** (`/api/demo/rules`): `NutritionPlanRule` (cooldown `interval`, minutos entre aplicaciones del plan; 0 lo
  vence ya) y `LowEcRule` (cooldown `no-fertilized`; bypass `samples`: la última lectura de CE cuenta como la media). Las dos se apagan.
  Escenarios `NUTRITION_PLAN` y `LOW_EC` en el simulador.

## Reglas bióticas (`add-reglas-biotico` · reglas_v2 §8.5)

Cinco decisiones sobre hongos y plagas, todas con fitosanitario y todas por el `ApplicationService` (nunca publican la bomba). Dos son **de
sector** (corren en la cadena del `RuleOrchestrator`, rama `INSUMO`, sólo con un **diagnóstico nuevo**) y dos **de macro-zona** (las corre el
`ZoneRuleEngine`, antes que el plan de nutrición: sanidad antes que rutina). El tope es una precondición más de la aplicación.

| Regla | Prioridad | Qué decide |
|---|---|---|
| `FungalDamageRule` (F-F1) | 15 · sector | Diagnóstico *Daño fúngico* vigente (P-D1) **y** (severidad Media/Alta **o** sustrato > `biotico.humedad-hongo` (80 %) **o** DPV < `biotico.dpv-saturado` (0,3 kPa) en las **2 últimas lecturas de pulso, de las últimas 24 h** **o** el mismo diagnóstico en `biotico.capturas-seguidas` (3) capturas de días seguidos): pide fitosanitario y deja un `WARNING` «Alta probabilidad de hongo» (`CRITICAL` con severidad Alta). Si no, espera a las próximas capturas |
| `FoliarPestRule` (F-P1) | 16 · sector | Lo mismo para *Plaga foliar* y *Ácaro* (que es una plaga foliar), con DPV ≥ `biotico.dpv-calor-seco` (2 kPa) en alguna lectura de las últimas `biotico.horas-dpv-acaros` (72) horas como razón ambiental. Si no se cumple ninguna deja un `INFO` y espera |
| `FungalConditionRule` (F-F2) | 17 · zona | DPV < `biotico.dpv-saturado` en 2 lecturas de pulso seguidas, temperatura entre `biotico.temp-predisponente-min` y `-max` (18–28 °C) y sustrato ≥ `biotico.humedad-predisponente` (75 %): `WARNING` «Condiciones favorables a enfermedades fúngicas». **No aplica nada** y no mira el diagnóstico |
| `OutbreakRule` (F-04) | 18 · zona | `biotico.sectores-foco` (3) o más sectores de la zona con el mismo tipo de daño biótico vigente dentro de `biotico.horas-foco` (72 h): pide fitosanitario para los **lindantes** de cada afectado (aunque estén sanos) y deja un `CRITICAL` «Foco de [diagnóstico] en MZ-N» |
| Tope (F-06) | precondición | Un sector que ya recibió `insumo.tope-fitosanitario` (3) fitosanitarios en `insumo.tope-fitosanitario-dias` (30) días **no recibe otro** hasta que alguien registre la revisión: `ApplicationPreconditions` rechaza (motivo «F-06 … tope») y deja un `WARNING` por sector, una vez |

- **Qué decide la regla y qué el servicio.** La regla decide *si* corresponde tratar; *cuándo* se aplica lo resuelve el `ApplicationService` con
  P-D1…P-D5 (ventana, temperatura, lluvia, humedad, intervalo de 7 días y una por día). Lo diferido queda en `pending_application` y se reintenta.
  La dosis es la del catálogo (`insumo.dosis-fitosanitario`, 10 ml de fábrica, máx. 50): la v2 dice 100 ml, el firmware acepta 50 por comando.
- **Mapa de estados (D-1).** *Daño fúngico* → F-F1; *Plaga foliar* y *Ácaro* → F-P1 (y cuentan juntos en el foco); el *Daño biótico* genérico
  **no es de ninguno** (no se sabe qué condición lo justifica): sólo queda en el Registro de Inacción. Es la decisión por defecto del diseño,
  **a validar con el vivero**. Severidad: Baja = leve, Media = moderada, Alta = alta (`biotic/Severity`).
- **Capturas seguidas (D-3).** `biotic/ConsecutiveCaptures`: de cada día vale la última captura, y la racha corre hacia atrás desde la más nueva
  sin saltear ningún día (una pasada que faltó la corta). Cuentan sólo las del tipo de daño y con la confianza mínima del catálogo.
- **Un diagnóstico, una aplicación (D-4).** Una regla que pide una aplicación por el diagnóstico **no lo gasta**: lo gasta el `ApplicationService`
  cuando la aplicación se ejecuta (o se rechaza, porque no se reintenta). La fila diferida guarda `diagnosis_ts` (columna nueva de
  `pending_application`, sin migración manual) y al ejecutarse se marca ESE diagnóstico, no el que tenga entonces el sector: uno más nuevo sigue
  siendo nuevo. Mientras espera, la regla se reevalúa en cada pulso y vuelve a pedir lo mismo (la fila es única por sector e insumo).
  `ResultadoEvaluacion.usoDiagnostico` es `false` si alguna regla pidió una aplicación por el diagnóstico.
- **El foco (D-6).** Cuenta, por tipo de daño, los sectores **de la zona** con diagnóstico vigente (concluyente y con la confianza mínima) **dentro
  de la ventana del foco**, que puede ser más larga que la vigencia de P-D1 (48 h). Los lindantes son los hasta 8 de la grilla de la zona
  calculada con `sectoresPorFila` de la topología (`biotic/GridNeighbors`, `biotic/SectorGrid`); los del borde sólo tienen los que existen, y los
  de otra macro-zona no se tratan. **Supuesto a validar con el vivero:** esa disposición es visual, no un mapa de adyacencia física. Un foco, un
  tratamiento: no se le pide a un lindante que ya recibió fitosanitario dentro de la ventana ni al que ya lo tiene en cola, y si no queda ninguno
  por tratar no se repiten ni las órdenes ni la alerta. Un lindante bloqueado (S-01), sin bomba (S-04) o con falla hidráulica (S-05) queda fuera
  de la tanda pero el foco se avisa igual.
- **Una alerta por evento (F-F2).** La regla no guarda estado: avisa cuando la condición se cumple con las últimas lecturas y **no** se cumplía
  con las mismas lecturas corridas una hacia atrás, es decir, cuando el evento empieza.
- **Revisión del sector (F-06).** `POST /api/sectores/{sectorId}/revision-fitosanitario {"detail": "..."}` (permiso `insumos.gestionar`,
  que ya tienen el Ingeniero, el Operario y el Servicio; sin migración) registra quién revisó y qué constató (evento «Revisión» en el historial
  del sector) y fija `sector.fungicide_review_ts`: desde ahí el conteo del tope vuelve a cero. El detalle es obligatorio (`400`); un sector
  inexistente da `404`. Es el único camino que levanta el bloqueo. El tope cuenta las aplicaciones de la tabla `application`, sea cual sea la
  regla que las pidió.
- **Parámetros** (editables en Motor de reglas → Parámetros): familia `BIOTICO` (`biotico.dpv-saturado`, `dpv-calor-seco`, `humedad-hongo`,
  `temp-predisponente-min`/`-max`, `humedad-predisponente`, `capturas-seguidas`, `horas-dpv-acaros`, `sectores-foco`, `horas-foco`) e `INSUMO`
  (`insumo.tope-fitosanitario`, `insumo.tope-fitosanitario-dias`). Los rangos de las temperaturas predisponentes y de las ventanas los propone el
  equipo: la v2 fija el valor de fábrica pero no cuánto se puede mover.
- **Control desde el simulador** (`/api/demo/rules`): `FungalDamageRule` y `FoliarPestRule` (bypass `persistence`: el diagnóstico cuenta como
  visto en las capturas seguidas), `FungalConditionRule` (bypass `readings`: basta una lectura de DPV bajo el umbral), `OutbreakRule` (cooldown
  `window`, en minutos: la ventana del foco) y `ApplicationPreconditions` (bypass `cap`: el tope de fitosanitarios no se aplica). Las cuatro reglas
  se apagan. Escenarios `FUNGAL_DAMAGE`, `FOLIAR_PEST`, `FUNGAL_CONDITION`, `OUTBREAK` y `FUNGICIDE_CAP` en el simulador.
- **Efecto a conocer.** `SupplyRule` y `DailyDoseLimitRule` siguen retiradas: estas reglas **no las reemplazan** (las F-C*/F-S* de
  `add-reglas-clorosis-estres-solar` son las que faltan para borrarlas). Con el motor encendido, un sector con daño fúngico o plaga que no tiene
  el tanque de fitosanitario registrado rechaza la aplicación (y gasta el diagnóstico): cargá los tanques antes de la primera pasada.

## Historial de lecturas

Casi todas las reglas de nutrición y de hongos/plagas/estrés de `reglas_v2` miran el **pasado** de las lecturas
(CE media de 24 h, humedad media de 48 h, DPV en 2 lecturas seguidas, luz máxima entre las 10 y las 16 h…). La zona sólo
guarda la última lectura (`*Raw`), así que cada lectura aceptada se anota además en la tabla `zone_reading`.

- **Qué se guarda.** Una fila por lectura aceptada de una macro-zona: las diez métricas **tal como quedan en la zona
  después de aplicarla** (también las que no vinieron, con su valor vigente; una que nunca se reportó queda nula, no en
  cero), la hora del nodo, la de recepción y el `origen`. `ce` en dS/m, igual que la zona. `dpv` es el déficit de
  presión de vapor (ver "DPV" abajo). Una lectura anterior a la última guardada (buffer offline del nodo) no entra.
- **Origen.** `PULSE` = respondió a un pulso de sensado, con o sin motor; `OTHER` = envío manual, verificación de
  efectividad, secuencia LECTURA, arranque del nodo. Se decide con `PulsoSensadoService.recordResponse`, que a
  diferencia de `respondePulso` distingue "respondió a un pulso sin motor" de "no respondió a ninguno".
- **Misma transacción.** `ReadingRecorder` corre dentro de `NurseryService.updateTelemetry`: si falla el guardado, la
  lectura de la zona tampoco se aplica (mejor eso que una serie con huecos que rompa en silencio "2 lecturas seguidas").
- **Consultas** (`readings/ReadingHistory`, la única puerta para todas las reglas): `latest(zone, n, onlyPulse)` y
  `max`/`min`/`average`/`count(zone, ReadingMetric, ReadingWindow)`. La `ReadingWindow` lleva el rango de
  tiempo, una franja horaria local opcional (la lectura de la hora de fin todavía entra, como en la ventana de riego) y
  si cuentan sólo las lecturas de pulso. Las medias de "N lecturas" usan `onlyPulse()`; los máximos y medias de una
  ventana temporal (48 h, 72 h) cuentan todas. **Sin dato no es cero**: sin lecturas los agregados devuelven vacío y el
  conteo 0, y es la regla la que registra `SIN_DATO` (con menos lecturas de las que exige, igual).
- **Retención.** Parámetro `readings.retention-days` del catálogo (familia `READINGS`, fábrica 14, rango 3–90, editable
  por `PUT /api/rules/parametros`). `ReadingPurge` borra lo anterior todos los días a las 03:30 hora del vivero.
- **Migración.** Ninguna: la tabla la crea Hibernate. Un clon existente arranca con la serie vacía y las reglas dan
  `SIN_DATO` hasta juntar lecturas; no se reconstruye el pasado.
- La telemetría que publica el simulador alimenta la serie igual que la de un nodo real (hay un test con el JSON del
  contrato). Esto no es una regla: no tiene control de demo.

### DPV (déficit de presión de vapor)

Métrica **derivada** (reglas_v2 §1.4): no la manda el nodo ni está en el contrato MQTT; la ingesta la calcula.

- **Cálculo.** `readings/VaporPressureDeficit.calculate(T, HR)` (pura): `es = 0,6108·e^(17,27·T/(T+237,3))`,
  `DPV = es·(1 − HR/100)`, en kPa. Devuelve `null` si falta una entrada, no es finita o HR cae fuera de 0–100; no inventa un valor.
  Se redondea a 0,01 kPa sólo al persistir (`round`).
- **Cuándo.** En `NurseryService.updateTelemetry`, con la temperatura y la humedad **aceptadas en esa misma lectura**
  (una descartada por S-03 cuenta como ausente). Si falta alguna, el DPV conserva su valor y su `dpv_ts` anteriores: las reglas
  comparan frescura por métrica. Se escribe antes de `ReadingRecorder`, así la fila del historial ya lo lleva.
- **Dónde.** Columnas `zona.dpv` y `zona.dpv_ts` (se agregan solas con `ddl-auto=update`; sin migración) y `zone_reading.dpv`. No
  pasa por `ZonaEntity.raw/setRaw` (esas son las diez sensadas). Las reglas lo piden por `ReadingHistory` con
  `ReadingMetric.DPV` (`latest`, `max`…); sin lecturas es `SIN_DATO`, no cero. Un clon existente arranca sin DPV hasta la primera lectura.
- **Tramos** (`VaporPressureBand`): `< 0,3` aire saturado · `0,3–0,5` transición · `0,5–1,2` confortable · `1,2–2,0` demanda
  alta · `≥ 2,0` calor seco; el límite inferior pertenece al tramo. Los umbrales de las reglas (0,3 y 2,0) los declara cada regla
  en su catálogo, no este enum.
- **API.** `GET /api/nursery` → `zonas[].lectura.dpv` = `{value, band, label}` (`dto/VaporDeficit`), `null` sin dato o con el
  nodo fuera de servicio. Es informativo: no entra en `lectura.metrics`, no tiene `MetricSpec` ni umbrales editables y no
  cambia el estado de los sectores.
- No es una regla: no tiene control de demo. El simulador lo alimenta solo con la temperatura y la humedad que ya publica
  (38 °C y 25 % ⇒ 4,97 kPa).

## Control de reglas desde el simulador (`/api/demo/**`)

Sólo existen con `yerbanalytics.demo.enabled=true`; en producción dan `404` y no hay ningún rastro de
simulación. Los usa el simulador (`Desarrollo/simulador/`), nunca el dashboard.

- **`DemoControllable`** (`engine/overrides/`) es todo lo que `GET /api/demo/rules` necesita saber de una pieza
  de decisión: `name`, `label`, `branch`, `priority`, `cooldownKeys()` y `bypassKeys()`. `Rule` lo extiende, y
  también lo implementan las reglas por macro-zona y los servicios que deciden fuera de la cadena por sector.
  `DemoControllableRegistry` las reúne a todas; nombres duplicados hacen fallar el arranque.
- `PUT /api/demo/rules/{nombre}` apaga o enciende una pieza y fija sus cooldowns (minutos; `0` los elimina) y
  bypass. `404` si el nombre no existe; `400` si una clave de cooldown o bypass no la declara la pieza.
  `PUT /api/demo/rules/cooldowns/zero` alcanza a todas, no sólo a las de la cadena por sector. Los overrides
  viven en memoria: un reinicio los borra.
- Quien decide consulta `RuleOverrides` **antes de actuar**: apagada no actúa ni corta nada y lo deja en su
  traza (`OMITIDA_DESHABILITADA` en las reglas por sector). Toda espera o intervalo se declara como
  `cooldownKey` y toda guarda de estado o franja horaria como `bypassKey`.
- **Clima simulado**: `POST /api/demo/weather` arma 24 marcas horarias (probabilidad, mm por hora y
  temperatura) más el UV máximo del día. Con sólo los cinco campos básicos las marcas salen planas con esos
  valores (así R-03 se puede accionar); `uvMaxDia` y `horas` (`{hora, probLluviaPct?, precipMm?, tempC?}`, hora
  0–23 del vivero) afinan una marca. `PUT /api/demo/weather/offline {"on": true}` hace que el servicio
  responda "sin pronóstico" como si Open-Meteo estuviera caída; `DELETE /api/demo/weather` limpia ambos.

### Fuente de telemetría (`/api/demo/telemetry-source`)

Con el modo demo, el simulador publica su telemetría en un tópico **aparte**, `nursery/demo/zone/{zona}/telemetry`
(el comodín productivo `nursery/zone/+/telemetry` nunca lo alcanza), así que no compite con el ESP32 real.
`GET`/`PUT /api/demo/telemetry-source` (`{"source":"REAL_NODE"|"SIMULATOR"}`, por defecto `REAL_NODE`, en memoria:
un reinicio vuelve al nodo real) elige cuál procesa el backend: con `SIMULATOR` sólo la del tópico de demo, con
`REAL_NODE` sólo la real; la descartada no se persiste ni cuenta como respuesta del pulso ni de la lectura de Demo
Expo. Lo demás no cambia: `LEER_AHORA` y los comandos a los actuadores siguen en los tópicos del ESP32 (los
actuadores reales se mueven) y el ACK es el de siempre. Todo vive en el paquete `demo/` con
`@ConditionalOnProperty(yerbanalytics.demo.enabled=true)`: con el modo demo apagado no hay suscripción, endpoint (404)
ni bean. El código productivo sólo conoce la interfaz neutra `mqtt/TelemetryAdmission` (`ALL` sin demo), igual que
`RuleOverrides`. Spec: `openspec/changes/add-demo-telemetry-source/`.

## Secuencias de la Demo Expo

Una **secuencia** es un guion corto contra el actuador de un sector, con cada paso visible: **riego**
(abre la válvula, espera el ACK del final del riego, cierra), **mediasombra** (despliega, espera, enrolla) y **lectura** (pide una
lectura al nodo testigo y la muestra). No pasan por el motor de reglas: son una prueba directa del
hardware. La excepción es la lectura con `"parametros":{"ejecutarMotor":true}` ("Leer sensores y ejecutar
motor ahora"): su pedido sale como un pulso de sensado con motor sólo de su zona, así que la telemetría que
la responde evalúa las reglas de esos sectores en la ingesta, como cualquier pulso (`409` si ya hay un pulso
en curso). Una sola a la vez, y ninguna mientras corre una pasada del riel (ni al revés).

| Método y ruta | Respuesta |
|---|---|
| `POST /api/secuencias` | `202` + `Secuencia` · `400 {"error"}` (tipo desconocido, `duracionSeg` o `esperaSeg` fuera de rango) · `409 {"error"}` (hay una secuencia o una pasada en curso; sin topología) |
| `GET /api/secuencias/actual` | `200` + `Secuencia` (la en curso o la última) · `204` si no hubo ninguna desde el arranque |
| `POST /api/secuencias/actual/cancelar` | `200` + `Secuencia` · `409` si no hay una en curso o ya se está cancelando |

Además hay dos acciones sueltas de **un solo paso**: `MEDIASOMBRA_ABRIR` (enrolla: `shade SET {targetPct: 100}`)
y `MEDIASOMBRA_CERRAR` (despliega: `targetPct: 0`), al mismo destino que la secuencia de mediasombra. Esperan el
ACK final (los `EN_CURSO` `en_cola`/`iniciado` sólo estiran el plazo; `sin_cambio` es éxito, "ya estaba así"), no
llevan parámetros y se rigen por la misma guardia (`409` con otra secuencia o una pasada). Se pasan por acá y no por
un publish directo al broker para que el seguimiento de comandos y el estado del backend las vean.

Pedido: `{"tipo": "RIEGO" | "MEDIASOMBRA" | "LECTURA" | "MEDIASOMBRA_ABRIR" | "MEDIASOMBRA_CERRAR", "parametros": {"duracionSeg": N, "esperaSeg": N}}`.
`duracionSeg` sólo aplica a `RIEGO` (default 10, de 1 a 1200: el tope del contrato);
`esperaSeg` sólo a `MEDIASOMBRA` (default 10, de 0 a 600). La forma de `Secuencia` y sus pasos:
`openspec/changes/add-secuencias-demo-expo/design.md` §2.7 y `dto/Secuencia.java`.

- **Destino.** La zona de menor número (orden numérico) y su primer sector. Sin topología, `409`.
- **MQTT que ahora sí se usa.** Publica `ON`/`OFF` de la válvula en `nursery/zone/{zona}/sector/{sector}/command`, y el
  `SET` de la mediasombra (que es de la zona) y `LEER_AHORA` en `nursery/zone/{zona}/command` (QoS 1). **Escucha el ACK de
  los actuadores** (`nursery/zone/+/sector/+/ack` y, el de la malla, `nursery/zone/+/ack`) con un adaptador propio
  (`-ack`), que `AckActuadorReceiver` entrega a `SeguimientoComandosService` *y* a `SecuenciaService`
  (los comandos de las secuencias también los vigila el seguimiento, a propósito: una falla mecánica es una
  alerta real sin importar quién mandó el comando), y la telemetría con otro (`-lectura`); el de telemetría que alimenta al motor queda idéntico, a
  costa de que el broker entregue cada lectura dos veces. Sólo cuentan los ACK que citan el `commandId`
  del paso en curso. Contrato: `mqtt/ContratoNodo.java`, espejo de `Desarrollo/embebido/comun/contrato.h`.
- **Tiempos** (`yerbanalytics.secuencia.*`): ACK del OFF de la válvula `timeout-ack-valvula-seg` (10), ACK del
  ON del riego `margen-ack-riego-seg` (15: el firmware lo manda al *terminar* el riego, así que se espera
  hasta N + margen), ACK de la
  mediasombra `timeout-ack-mediasombra-seg` (45; el firmware se rinde a los 30 s), lectura
  `timeout-lectura-seg` (20), cadencia del orquestador `tick-ms` (1000, carril propio `secuenciaScheduler`).
- **Guardia compartido.** Hay un único ESP32: `GuardiaHardware` arbitra entre secuencia y pasada
  (`UsoDelHardware`), y quien pierde recibe el `409` con el motivo del otro.
- **Riego contra el firmware ESP32-S3.** `ABRIR` publica `valve ON durationSec=N` y queda OK al publicar
  (el ON no se confirma al abrir); `ESPERAR` espera el ACK de ese ON, que llega al final del riego
  (`SUCCESS`, con cualquier `tipo`: `ok` o `limite_excedido`); un `ERROR` inmediato (`duracion_invalida`,
  `sector_invalido`…) falla el paso con el tipo como código. `CERRAR` corre siempre: `valve OFF` y su ACK
  inmediato. El `ERROR cancelado` que recibe el ON cuando el OFF lo corta se ignora (cita otro `commandId`).
- **Paso seguro.** El `ON` de la válvula lleva `durationSec = N`, así que el
  nodo la apaga solo aunque el backend desaparezca. Ante cualquier falla o cancelación se publica el `OFF`
  (riego) o el `SET 100` (mediasombra) igual, se marcan `OMITIDO` los pasos de espera y la secuencia
  termina `FALLIDA` o `CANCELADA`. Si falla el propio CERRAR, el error avisa que el nodo apaga la bomba
  solo a los N s.
- **Fallas con código propio.** `ACTUADOR_SIN_RESPUESTA` (sin ACK a tiempo), `FALLA_MECANICA` (ACK `ERROR`
  de la mediasombra), `PUBLICACION_FALLIDA`, `SIN_LECTURA` (no llegó telemetría de la zona a tiempo).
- **Lectura.** Cuenta la primera telemetría de la zona recibida *después* del pedido; `ce` se muestra en
  dS/m. Sin sensores cableados en el nodo, termina `FALLIDA` con `SIN_LECTURA`: es lo esperado hoy.
- **El estado vive en memoria.** Un reinicio a mitad pierde la secuencia (`GET` → `204`). No hay tablas
  nuevas ni migración manual.
- **Garantía.** `engine/` y `NurseryService` no cambiaron: las secuencias no tocan el motor.

## Integración con el frontend

| `VITE_DATA_SOURCE` | Origen de datos |
|---|---|
| `mock` (default) | `MockRepository` en el frontend |
| `http` | `GET /api/nursery` en este backend |

Para probar la integración:

```bash
# Terminal 1 — backend
cd Desarrollo/backend
mvn spring-boot:run

# Terminal 2 — frontend
cd Desarrollo/frontend
# En .env: VITE_DATA_SOURCE=http y VITE_API_BASE_URL=http://localhost:8000/api
npm run dev
```

El dashboard pide login: entrá con `admin` y la contraseña temporal del primer arranque.