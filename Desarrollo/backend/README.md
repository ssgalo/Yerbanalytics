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

## Arquitectura

El proyecto sigue el patrón multicapa clásico de Spring Boot:

- `controller/`: Controladores REST. Definen los endpoints, rutas y manejan las peticiones HTTP.
- `config/`: Clases de configuración global (CORS, propiedades, beans).
- `dto/`: Objetos de Transferencia de Datos. Mantienen paridad con `domain.ts` del frontend.
- `service/`: Lógica de negocio. `NurseryService` genera el snapshot del vivero.
- `service/mock/`: Generador determinístico portado del mock del frontend.

## Endpoints

- `GET /api/nursery`: Devuelve el snapshot completo del vivero (`NurseryData`). Hoy usa un generador determinístico con semilla configurable (`yerbanalytics.mock.seed`, default `20260613`).

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

La consumen el dashboard, el simulador y —en el futuro— el planificador de pasadas del riel
y el servicio de inferencia. Un dispositivo de captura no debe usarlas.

| Método | Ruta | Quién la usa |
|---|---|---|
| `POST` | `/api/camara/vinculacion` | Backoffice/simulador: emite el código de un solo uso |
| `GET` | `/api/camara/dispositivos` | Estado técnico de la flota de cámaras |
| `POST` | `/api/capturas/ordenes` | Emisor de órdenes (hoy el simulador) |
| `GET` | `/api/capturas/ordenes/{id}` | Seguimiento de una orden |
| `GET` | `/api/capturas/{capturaId}/imagen` | El dashboard, para mostrar la foto |
| `POST` | `/api/diagnosticos` | **Alta de diagnóstico** |
| `GET` | `/api/diagnosticos` | Listado de diagnósticos persistidos |

> **`POST /api/diagnosticos` es un camino único, sin variantes.** Es el mismo endpoint que va
> a usar el servicio de inferencia y el mismo que usa hoy el panel de simulación para cargar
> un diagnóstico a mano. No hay endpoint de simulación, no hay columna que marque el origen y
> el alta **no** depende del modo estático/simulación — el modelo tampoco va a depender de él.
>
> El modelo es Keras/Python: aunque corra en la misma máquina, no vive dentro del JVM. El
> diagnóstico entra por HTTP con o sin simulador.
>
> Consecuencia asumida: no se pueden purgar selectivamente los diagnósticos de prueba. La
> purga posible es por fecha o por sector, que alcanza porque todo diagnóstico está anclado a
> una captura fechada.

### Autenticación

Sólo las rutas del contrato exigen token, y el filtro está **acotado por path**
(`CamaraAuthFilter`): el resto de la API queda exactamente como estaba. Deliberadamente no se
usa `spring-boot-starter-security` — traer la cadena de filtros completa protegería por
defecto endpoints que hoy son abiertos. Cuando llegue HU-01 (login) habrá que unificar.

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
el dashboard y el simulador no se enteran. Es necesario porque la PWA de cámara se sirve por
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
yerbanalytics.capturas.confianza-minima=85       # umbral de concluyente (HU-04 CA-03)
yerbanalytics.capturas.jwt-secret=...            # SOBRESCRIBIR EN PRODUCCIÓN
yerbanalytics.capturas.ancho-max=1920            # la resolución la fija el backend,
yerbanalytics.capturas.calidad-jpeg=0.85         # no el cliente
yerbanalytics.cors.origins=...                   # agregar el origen de la app de cámara
```

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