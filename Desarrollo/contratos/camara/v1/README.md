# Contrato de dispositivo de captura — v1

Referencia del contrato entre un **dispositivo de captura de imágenes cenitales** y la
plataforma Yerbanalytics.

La especificación formal está en [`openapi.yaml`](./openapi.yaml). Este documento explica lo
que el OpenAPI no puede expresar: el flujo de credenciales, la máquina de estados de una
orden, las obligaciones de comportamiento del cliente y las reglas de evolución.

> **Fuente de verdad.** El backend se implementa contra el contrato. Si el comportamiento del
> backend difiere de lo declarado acá, el defecto es del backend. Mismo criterio que
> `Desarrollo/embebido/comun/contrato.h` para el contrato MQTT del ESP32.

---

## 1. Por qué existe este contrato

La primera implementación fue una PWA que corre en Safari sobre un iPhone montado en el riel.
**No era la definitiva.** Hoy existe además una app Android nativa
(`Desarrollo/camara-android/`), donde un foreground service sostiene la operación sin depender de
que la pantalla esté encendida y la app en primer plano.

El criterio de aceptación del contrato era literal:

> **Escribir la app Android no debe requerir tocar una línea del backend.**

**Quedó ejercido.** La app nativa entró sin modificar el backend, sin agregar endpoints y sin
subir la versión del contrato. Los dos clientes conviven contra la misma superficie, y el único
punto donde la app nativa se aparta del contrato está declarado: opera sin TLS (§8), porque la CA
local y el conector 8443 existen por una restricción del navegador que un cliente nativo no
tiene. Admite igual una URL cifrada sin recompilar.

De ahí se derivan tres propiedades que el contrato mantiene deliberadamente:

1. **Superficie acotada y enumerable.** Siete endpoints bajo `/api/camara/v1`. Nada más.
2. **Neutralidad de plataforma.** Ninguna obligación depende de una API de navegador.
3. **Verificable.** Existe una suite de conformidad ejecutable; "cumple el contrato" es algo
   que se corre, no algo que se afirma.

---

## 2. Qué es y qué no es parte del contrato

### Superficie del contrato — lo que un cliente implementa

| Método | Ruta | Para qué |
|---|---|---|
| `POST` | `/api/camara/v1/enrolar` | Vinculación inicial con un código de un solo uso |
| `POST` | `/api/camara/v1/token` | Renovación del token de acceso |
| `GET` | `/api/camara/v1/config` | Configuración de captura vigente |
| `POST` | `/api/camara/v1/heartbeat` | Señal de vida |
| `GET` | `/api/camara/v1/ordenes/stream` | Canal de órdenes (SSE) |
| `POST` | `/api/camara/v1/ordenes/{ordenId}/imagen` | Entrega de la captura |
| `POST` | `/api/camara/v1/ordenes/{ordenId}/fallo` | Acuse de captura fallida |

### API de plataforma — fuera del contrato

Estos endpoints existen, pero los consume la plataforma, no el dispositivo. **Un cliente no
debe usarlos.**

| Método | Ruta | Quién la usa |
|---|---|---|
| `POST` | `/api/camara/vinculacion` | Backoffice / simulador, para emitir el código |
| `POST` | `/api/capturas/ordenes` | El emisor de órdenes (hoy el simulador; mañana el planificador) |
| `GET` | `/api/capturas/ordenes/{id}` | Seguimiento del estado de una orden |
| `GET` | `/api/capturas/{capturaId}/imagen` | El dashboard, para mostrar la foto |
| `POST` | `/api/diagnosticos` | El servicio de inferencia (hoy la carga manual del simulador) |

---

## 3. Credenciales

El código de cualquier cliente distribuido es inspeccionable, así que **nada de larga duración
se escribe en el fuente**. El dispositivo obtiene sus credenciales en tiempo de ejecución.

```
  1. La plataforma emite un código de un solo uso (10 min de vida)
        POST /api/camara/vinculacion  →  { codigo: "7F3K-2M9Q", expiraEn }

  2. El operario lo tipea en el dispositivo, una única vez en su vida
        POST /api/camara/v1/enrolar   { codigo, nombre, plataforma }
                                      →  { dispositivoId, refreshToken }
        └── el refreshToken se persiste localmente en el dispositivo

  3. En cada arranque y antes de cada expiración
        POST /api/camara/v1/token     { refreshToken }
                                      →  { accessToken, expiraEnSeg }   (~15 min)
```

- El backend guarda sólo la **huella** del `refreshToken`; no puede devolverlo después.
- Dar de baja el dispositivo lo revoca: la siguiente renovación falla con `401`.
- Ante un `401` en cualquier endpoint, el cliente renueva y reintenta **una vez**. Si la
  renovación también falla, descarta la credencial y vuelve a pedir el código de vinculación.

### Sobre el token en la query string

`Authorization: Bearer` es la vía canónica **en todos los endpoints, incluido el stream**.

`GET /ordenes/stream` acepta además `?token=`. Esto es una concesión a `EventSource`, que en
el navegador no permite fijar headers. **No es la forma preferida y no está disponible en
ningún otro endpoint.** Un cliente Android usa el header en todas las rutas y puede ignorar
que esta alternativa existe.

---

## 4. Ciclo de vida de una orden

La orden es la entidad de primera clase, no la imagen. Es lo que permite saber en qué posición
del riel se tomó una foto.

```
                 ┌──────────── reintento (intentos < max) ─────────────┐
                 ↓                                                     │
  [PENDIENTE] ──despacho por SSE──> [ENTREGADA] ──llega imagen──> [RECIBIDA]  (terminal)
                                          │
                                          ├──acuse de fallo del device──> [FALLIDA] ──┤
                                          └──vence el plazo (watchdog)──> [VENCIDA] ──┘
                                                                                      │
                                                          intentos agotados ──> [ERROR] (terminal)
```

Lo que el cliente necesita saber de esta máquina:

- **Una orden emitida sin el dispositivo conectado no se pierde.** Queda `PENDIENTE`, y al
  abrirse el stream el backend le drena las pendientes en orden de creación.
- **El backend vence las órdenes por su cuenta**, con un barrido periódico. No depende de que
  el cliente avise. Un cliente que se cae sin decir nada no deja órdenes colgadas para siempre.
- **Reentrega:** una orden reintentada llega con el mismo `ordenId` y el campo `intento`
  incrementado. No es una orden nueva.

---

## 5. Obligaciones de un cliente conforme

Estas obligaciones están redactadas **sin nombrar tecnologías** a propósito. Un cliente las
cumple con los medios de su plataforma; el contrato no prescribe cuáles.

### 5.1 Canal de órdenes

- **Sostener el canal abierto** mientras el cliente esté operativo.
- **Reconectar automáticamente** ante cortes, sin intervención humana.
- No asumir que el canal sigue vivo por el hecho de no haber recibido un error: la ausencia
  prolongada de `ping` es señal de canal muerto.

### 5.2 Cola de órdenes

- Una orden que llega **mientras hay una captura en curso se encola**, no se descarta.
- Las órdenes encoladas se ejecutan en el orden en que llegaron.
- Al alcanzar `maxColaOrdenes`, el cliente descarta una orden y **la acusa como fallo** con
  motivo `COLA_LLENA`. Nunca la descarta en silencio: si el backend no se entera, la orden
  queda esperando su vencimiento en vez de reintentarse enseguida.

### 5.3 Envío de la imagen

- **Reintentar con espera creciente** ante fallo de red.
- **Conservar en almacenamiento persistente y acotado** las imágenes que no pudo entregar,
  y reintentarlas al recuperar conectividad. Acotado en cantidad y en tamaño: una cola sin
  techo termina rompiendo el cliente.
- **Tratar el `409` como éxito.** Significa que una subida anterior sí llegó y se perdió la
  respuesta. El cliente quita la imagen de su cola y no la cuenta como fallo. Sin esta regla,
  un corte de red en el momento justo produce duplicados o falsos errores.

> El almacén local del cliente es una **optimización, no la garantía**. Aunque el cliente
> pierda todo lo pendiente, el backend vence la orden y la reintenta. Un cliente puede ser
> conforme con una cola modesta.

### 5.4 Reporte

- **Acusar el resultado de cada captura**, incluidos los fallos, con un motivo del conjunto
  cerrado.
- **Emitir la señal de vida** con la cadencia de `heartbeatSeg`.

### 5.5 Configuración

- **Tomar del backend** la resolución, la calidad JPEG y los parámetros de operación. No
  fijarlos en el código del cliente.
- **Aplicar el evento `config`** en la siguiente captura, sin reiniciar ni requerir
  intervención física: el dispositivo está montado en un riel.

### 5.6 Tolerancia a la evolución

- **Ignorar sin fallar los campos desconocidos.** Es lo que permite agregar campos opcionales
  sin romper clientes viejos.

---

## 6. Compatibilidad y versionado

La versión vive en el path (`/api/camara/v1`).

### Cambios compatibles — no suben la versión

- Agregar un campo **opcional** a una respuesta o a un payload.
- Agregar un **evento nuevo** al stream. Un cliente que no lo conoce lo ignora.
- Agregar un **motivo de fallo** nuevo al conjunto.
- Agregar un endpoint nuevo.

### Cambios incompatibles — exigen `v2`

- Quitar o renombrar un campo.
- Cambiar el tipo o la semántica de un campo existente.
- Volver obligatorio algo que era opcional.
- Cambiar un código de respuesta o una transición de estado.

Cuando se publique `v2`, `v1` **sigue atendiendo** hasta que no queden clientes usándola.

---

## 7. Conformidad

La suite de conformidad vive en `Desarrollo/contratos/camara/v1/conformidad/` y se ejecuta
contra un backend levantado. Recorre el ciclo completo —vinculación, enrolamiento, token,
apertura del canal, recepción de una orden, subida de imagen, acuse de fallo, heartbeat— y
verifica códigos de respuesta y transiciones de estado.

```bash
cd Desarrollo/contratos/camara/v1/conformidad
npm install
npm test                     # contra http://localhost:8000 por defecto
BASE_URL=https://mi-host npm test
```

Sirve para dos cosas:

1. **Detectar regresiones del backend.** Si un cambio altera un código o una transición que el
   contrato define, la suite falla.
2. **Validar un cliente nuevo.** El día que exista la app Android, la suite dice si el backend
   le responde según el contrato — sin haberlo modificado para acomodarla.

---

## 8. Requisitos del entorno

- **HTTPS obligatorio.** No es un capricho del contrato: el acceso a la cámara desde un
  navegador exige origen seguro (salvo en `localhost`). Un cliente nativo no tiene esa
  restricción, pero el token de acceso viaja en claro sin TLS, así que HTTPS se exige igual.
- El dispositivo y el backend tienen que verse en la misma red.

---

## 9. Lo que el contrato deliberadamente no define

- **Cómo se toma la foto.** Resolución y calidad las fija el backend; el resto —enfoque,
  exposición, estabilización— es problema del cliente y de su plataforma.
- **Cómo el cliente persiste lo que no pudo enviar.** Sólo que debe hacerlo y que debe estar
  acotado.
- **Cómo se mueve el riel.** El dispositivo no lo controla. La `posicionRiel` viaja en la
  orden como dato, para que la captura quede trazable.
- **Qué se hace con la imagen después.** El diagnóstico entra a la plataforma por otro camino
  (`POST /api/diagnosticos`), que no es parte de este contrato.
