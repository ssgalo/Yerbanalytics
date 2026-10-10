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

**Primer arranque contra una base vacía:** se crea el Administrador inicial (`admin`). Definí antes
su contraseña temporal con `YERBANALYTICS_ADMIN_CLAVE`; si no, se genera una y aparece **una sola
vez** en el log de arranque. Detalle en [Autenticación y permisos](#autenticación-y-permisos-hu-01--hu-20).

## Configuración

La configuración principal se encuentra en `src/main/resources/application.properties`.
- El puerto está fijado en `8000` para coincidir con la URL esperada por el frontend (`VITE_API_BASE_URL`).
- El proyecto cuenta con una configuración global de CORS en la carpeta `config/` para permitir peticiones entrantes desde el entorno de desarrollo del frontend (Vite en `http://localhost:5173`).

## Base de datos

El esquema lo genera Hibernate (`ddl-auto=update`) y se siembra con
`src/main/resources/data.sql` (600 sectores, 6 macro-zonas, umbrales de fábrica).

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
  revocar es inmediato: un cambio de rol, una suspensión, una baja, un blanqueo o un cambio de la
  matriz del rol cortan las sesiones en el acto (`401 SESION_REVOCADA` en la próxima petición).
- **Inactividad:** el backend decide. Si `ahora − ultima_actividad` supera el tiempo máximo
  (60 min por defecto, editable 5–480 desde `PUT /api/seguridad/politica`) responde
  `401 SESION_EXPIRADA`. **Sólo cuenta como actividad lo explícito**: el login, toda petición que
  no sea `GET` y `POST /api/auth/actividad`, que el dashboard manda cuando detecta interacción. Los
  sondeos (`GET`) no mantienen viva la sesión.
- `GET /api/auth/perfil`, `POST /api/auth/logout`, `PUT /api/auth/clave` (`{"actual","nueva"}`,
  cierra las demás sesiones del usuario).
- Contraseña temporal (alta, blanqueo, admin inicial): mientras no se cambie, todo lo demás
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
| `GET` / `PUT /api/configuracion` | `configuracion.ver` / `configuracion.editar` |
| `GET` / `POST /api/diagnosticos` | `diagnosticos.ver` / `diagnosticos.registrar` |
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
`/suspender`, `/reactivar`, `/baja` (lógica y definitiva) y `PUT /{id}/clave` (blanqueo). Nadie
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
| `yerbanalytics.auth.admin-inicial.usuario` | `admin` | Nombre del Administrador que se crea si no hay ninguno activo |
| `yerbanalytics.auth.admin-inicial.clave` | `${YERBANALYTICS_ADMIN_CLAVE:}` | Su contraseña temporal. Vacía → se genera y se loguea una vez |
| `yerbanalytics.auth.cookie-secure` | `false` | Atributo `Secure` de la cookie. Encendelo si todo va por HTTPS |
| `yerbanalytics.auth.barrido-cron` | `0 30 3 * * *` | Barrido de sesiones viejas |

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
y los intervalos de sensado y evaluación (configuración operativa).

- `GET /api/rules/parametros`: catálogo normalizado (`reglas` y `parametros`, cada parámetro una
  vez con su `usadoPor`).
- `PUT /api/rules/parametros`: edición en lote, todo o nada (`{"cambios":[{"clave","valor"}]}`;
  `valor: null` restablece la fábrica). Valida tipo, rango y restricciones cruzadas (crítico < umbral
  de riego < humedad objetivo; bloqueo ≤ alerta de saturación; el volumen máximo debe caber en los
  1200 s de la válvula); si algo falla responde `400 {"errores":[{"clave","mensaje"}]}` y no persiste
  nada. El historial lo firma el usuario de la sesión.
- `GET /api/rules/evaluaciones/{sectorId}?origen=TELEMETRIA|BARRIDO`: última traza de evaluación
  del sector (qué recibió cada regla contra qué umbral). Vive en memoria: `204` si todavía no se
  evaluó desde el arranque, `404` si el sector no existe.
- `GET /api/rules/schema`: grafo de reglas; cada nodo de regla trae sus `parametros`.

El dashboard lo muestra en la sección **Motor de reglas** (`/reglas`): pestaña *Parámetros* (edición
del catálogo) e *Inspector* (el grafo coloreado con la última evaluación de un sector).

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
  ESP32 corta solo al cumplir la duración y el backend no escucha el ACK.

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
| `GET` | `/api/capturas/{capturaId}/imagen` | El dashboard, para mostrar la foto |
| `POST` | `/api/diagnosticos` | **Alta de diagnóstico** |
| `GET` | `/api/diagnosticos` | Listado de diagnósticos persistidos |

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

### Interruptor "Demo Expo"

`GET`/`PUT /api/configuracion/demo-expo` (`{"visible": true|false}`; `400` si falta `visible`) decide si
el dashboard muestra la pestaña. Se guarda en `preferencia_dashboard` (fila única), que crea
Hibernate: no hay migración manual. Sólo oculta la pestaña; los endpoints de pasada no dependen de él.

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