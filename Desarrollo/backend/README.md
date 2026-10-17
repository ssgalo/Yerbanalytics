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
| `GET` / `POST`,`PUT /api/topologia/**` | `topologia.ver` / `topologia.gestionar` |
| `GET /api/capturas/**` / `POST /api/capturas/ordenes` | `capturas.ver` / `capturas.ordenar` |
| `/api/camara/vinculacion`, `/api/camara/dispositivos/**` | `camara.gestionar` |
| `GET` / `POST /api/pasadas/**` | `pasadas.ver` / `pasadas.operar` |
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

Fuera del catálogo quedan, a propósito, la fecha de siembra (`yerbanalytics.nursery.sowing-date-iso`),
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
- `GET /api/rules/schema`: grafo de reglas; cada nodo de regla trae sus `parametros`.

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
  la lectura y el estado de los sectores. La mediasombra la mueven las reglas en el pulso; el plan de
  rustificación por horario queda fuera del motor (`add-rustificacion-por-macrozona`).
- **Pulso manual**: `POST /api/pulsos {"ejecutarMotor": bool}` (`true` si se omite) → `202
  {id, origen, ejecutaMotor, pedidoEn}` · `409 {"error"}` si hay uno en curso. Permiso `pasadas.operar`.
  Pide a todas las zonas y sólo se usa por API: Demo Expo usa la secuencia `LECTURA` con `ejecutarMotor`,
  que emite un pulso con motor sólo de su zona (ver "Secuencias"). No corre las franjas. Un programado
  que encuentra uno en curso se omite (queda en el log).
- **Reintentos** (`yerbanalytics.pulso.*`): a una zona pendiente se le reenvía el pedido cada
  `reintento-seg` (60) hasta `intentos` (3); vencido el último plazo queda sin respuesta en ese pulso y
  no se evalúa. El pulso no toma `GuardiaHardware`: si el ESP32 está moviendo el riel, lo cubren los
  reintentos.
- **Escalamiento** (`EscalamientoNodoService`): cada pulso sin respuesta suma uno a
  `zona.pulsos_sin_respuesta` y deja una alerta de la zona (regla `PulsoSensado`): `WARNING`, `WARNING`
  y, al tercero, `CRITICAL`, que pone la zona y sus sectores **fuera de servicio** (en el panel y en
  Hardware: 0 operativo, 1-2 señal intermitente, ≥ 3 fuera de servicio). Desde el cuarto, sin alertas.
  Cualquier telemetría vuelve la cuenta a 0 (alerta `INFO` si estaba fuera de servicio). La cuenta está
  en la base: sobrevive a un reinicio.
- **Umbral de lectura vieja**: `max(piso, intervalo × 60 + reintento-seg × intentos)` (14 580 s con los
  valores de fábrica). Sólo protege al riego (`StaleSensorRule`, revalidación del despacho); ya no decide
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
  12 h por sector.
- Una ronda encolada **se completa** aunque la humedad se recupere: sólo la retira una cancelación de
  seguridad (saturación, bloqueo manual; ventana cerrada o pausa por aplicación, para el riego común) o
  la revalidación del despacho (lluvia pronosticada después de decidir, ya regó en el ciclo). El sensor
  sin datos no la retira: la **pausa**.
- La cola y lo que está regando están en memoria. Un reinicio pierde lo pendiente y reconstruye lo
  abierto del historial; la siguiente telemetría vuelve a decidir.
- El estado de la válvula del dashboard (`Regando` / `En cola` / `Cerrada`) lo deriva el despacho; el
  ESP32 corta solo al cumplir la duración. El ACK no cambia ese estado: `SeguimientoComandosService` lo
  escucha sólo para alertar en el historial si el nodo informa una falla o no contesta a tiempo.

> **No operar con plantines reales sin E-01 y S-06.** Un sensor de humedad trabado en un valor seco
> plausible haría regar en cada ciclo de lectura de la ventana (~20 L por día y por sector con el 40 %
> fijo). Hoy nada lo detecta: la evaluación de efectividad marca `bloqueoRepeticion` pero ninguna regla
> lo lee. Una sonda que deja de reportar sí se detecta (la humedad vieja bloquea el riego). Es seguro
> con el simulador; para campo, antes hay que implementar E-01 + S-06 o bajar
> `riego.volumen-max-evento` y vigilar el historial.

El pronóstico (Open-Meteo) se pide con timeouts HTTP (`yerbanalytics.weather.connect-timeout-ms`,
3 s, y `read-timeout-ms`, 5 s) y se precalienta al arrancar; un refresco colgado se reemplaza a los 60 s.

Otros límites conocidos: R-06 depende de los eventos "Insumo" y la bomba conserva el enganche
`Dosificando`; el cupo se llena por número de sector, no por urgencia. El detalle de qué cubre y qué
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
> `SupplyRule` sigue leyendo `sector.diagnosis_conf` contra `diagnostico.confianza-minima` (85 %), pero
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

## Historial de lecturas

Casi todas las reglas de nutrición y de hongos/plagas/estrés de `reglas_v2` miran el **pasado** de las lecturas
(CE media de 24 h, humedad media de 48 h, DPV en 2 lecturas seguidas, luz máxima entre las 10 y las 16 h…). La zona sólo
guarda la última lectura (`*Raw`), así que cada lectura aceptada se anota además en la tabla `zone_reading`.

- **Qué se guarda.** Una fila por lectura aceptada de una macro-zona: las diez métricas **tal como quedan en la zona
  después de aplicarla** (también las que no vinieron, con su valor vigente; una que nunca se reportó queda nula, no en
  cero), la hora del nodo, la de recepción y el `origen`. `ce` en dS/m, igual que la zona. `dpv` queda nulo hasta
  `add-metrica-dpv`. Una lectura anterior a la última guardada (buffer offline del nodo) no entra.
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

Pedido: `{"tipo": "RIEGO" | "MEDIASOMBRA" | "LECTURA", "parametros": {"duracionSeg": N, "esperaSeg": N}}`.
`duracionSeg` sólo aplica a `RIEGO` (default 10, de 1 a 1200: el tope del contrato);
`esperaSeg` sólo a `MEDIASOMBRA` (default 10, de 0 a 600). La forma de `Secuencia` y sus pasos:
`openspec/changes/add-secuencias-demo-expo/design.md` §2.7 y `dto/Secuencia.java`.

- **Destino.** La zona de menor número (orden numérico) y su primer sector. Sin topología, `409`.
- **MQTT que ahora sí se usa.** Publica `ON`/`OFF` de la válvula y `SET` de la mediasombra en
  `nursery/zone/{zona}/sector/{sector}/command`, y `LEER_AHORA` en `nursery/zone/{zona}/command` (QoS 1,
  sin ACK). **Escucha el ACK de los actuadores** (`nursery/zone/+/sector/+/ack`) con un adaptador propio
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