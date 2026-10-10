# Simulador de hardware

Ocupa el lugar del hardware físico del vivero: publica telemetría en el broker MQTT como si
fuera un nodo ESP32, y ejercita el ciclo de captura del riel.

> **Es un proyecto aparte, y eso es el punto.** Borrá esta carpeta y el sistema sigue
> funcionando igual: el backend no tiene una sola línea —código, tabla, propiedad o
> configuración— que dependa de que el simulador exista. Si no está, el vivero simplemente
> espera telemetría de hardware real, que es su comportamiento de producción.

---

## Arranque rápido

```bash
cd Desarrollo/simulador
cp env.example .env      # y completá BACKEND_USUARIO/BACKEND_CLAVE (ver "Cuenta de servicio")
npm install
npm run dev              # http://localhost:5180
```

Se levanta con su propio comando: no hace falta reiniciar ni el backend ni el dashboard para
usarlo.

El `start-all` de la raíz también lo arranca, como comodidad para el trabajo diario. Lo trata
como **opcional**: si esta carpeta no existe, omite ese paso y levanta el resto del sistema
igual. El simulador es una conveniencia del launcher, nunca una dependencia.

Para que la telemetría llegue a algún lado necesitás además:

| | Para qué | Sin eso |
|---|---|---|
| **Mosquitto** en `:1883` | publicar lecturas | el envío falla con un mensaje claro |
| **Backend** en `:8000` | topología, cámara y diagnósticos | el simulador arranca igual y lo avisa |
| **Cuenta de servicio** en el `.env` | que el backend acepte esas peticiones | la telemetría sale igual; el resto lo rechaza el backend |

La barra de estado de arriba muestra los dos, así que si algo no anda se ve de entrada cuál
de los dos falta. Del backend distingue además si está levantado pero rechaza la cuenta del
simulador (punto ámbar, con el motivo).

---

## Qué hace

- **Sensores simulados** — alta por serial/MAC y macro-zona. Es una lista **del simulador**,
  separada del registro de hardware del sistema: crear uno acá no da de alta ningún
  dispositivo. Para que un nodo además actualice su estado técnico (batería, señal, último
  contacto), registralo en la sección Hardware del dashboard con el **mismo serial/MAC**.
- **Envío de lecturas** — cada métrica por separado, con su propia fecha/hora opcional, o las
  diez juntas con batería y señal. Fecha vacía = ahora. Las métricas que no mandás conservan
  su último valor en el vivero.
- **Emisión automática** — ruido periódico a todas las macro-zonas, para darle actividad de
  fondo al vivero. Arranca siempre apagada, porque encendida pisa lo que cargaste a mano.
- **Topología** — regenera la grilla (N macro-zonas × M sectores) para que coincida con el
  hardware a simular.
- **Cámara del riel** — vincular el teléfono, pedir una captura, ver la foto llegar y cargar a
  mano el diagnóstico que devolvería el modelo.

---

## Por qué publica directo al broker

```
simulador ──MQTT──► mosquitto:1883 ──► backend (ingesta normal)
```

El simulador ocupa **exactamente** el lugar del ESP32: mismo topic
(`nursery/zone/{zonaId}/telemetry`), mismo payload, mismas unidades. El backend lo ingiere por
su pipeline de siempre y no tiene forma de saber que del otro lado no había un nodo.

Antes esto pasaba por un endpoint del backend (`POST /api/simulacion/telemetria`), que
publicaba al broker para que el mensaje atravesara el pipeline real. Es decir: el backend se
publicaba telemetría a sí mismo, y cargaba con un controlador, un servicio, un publicador MQTT
y dos tablas que no servían a ningún caso de uso del vivero.

Como el navegador no habla MQTT sobre TCP, el simulador necesita su propio proceso. De ahí que
sea una app Node y no una SPA suelta.

---

## Por qué el backend se consume a través de `/backend/**`

La UI **nunca** llama al backend directo: pega contra `/backend/**` de su propio servidor, que
reenvía.

```
navegador :5180 ──► server :5180 ──► backend :8000
        mismo origen,        servidor a servidor,
        sin CORS             sin CORS
```

Si llamara directo, el backend tendría que listar `http://localhost:5180` entre sus orígenes
permitidos — y **esa línea sería un rastro del simulador en el sistema**, justo lo que se
quiere evitar. Así la dirección del backend es configuración del simulador (`.env`), no del
sistema: si el backend se muda de puerto, tocás esto y nada más.

Un efecto secundario que conviene conocer: el backend devuelve la imagen de una captura como
ruta relativa (`/api/capturas/{id}/imagen`), porque no conoce su propia URL pública. El cliente
le antepone `/backend`; sin eso el navegador la buscaría en el origen de la página y la foto no
aparecería.

---

## Cuenta de servicio

El backend exige sesión de usuario en toda `/api/**`, y el simulador entra como cualquier otro
cliente: con una cuenta de rol **Servicio** cuyas credenciales viven en **su** `.env`.

```
BACKEND_USUARIO=simulador
BACKEND_CLAVE=...
```

- **La cuenta la da de alta el Administrador** desde el dashboard (vista Usuarios), como una
  cuenta de servicio más. El backend no tiene usuario, rol ni permiso creado para el simulador:
  borrar esta carpeta deja, como mucho, una cuenta sin uso que se da de baja desde la misma vista.
- **Nace con clave temporal.** Hasta que se cambia, el backend responde `403` a todo. Entrá una
  vez al dashboard con esa cuenta, cambiá la clave y poné la nueva en `BACKEND_CLAVE`. La barra
  de estado lo avisa ("clave temporal").
- **Cómo la usa.** El servidor inicia sesión la primera vez que necesita el backend, guarda la
  cookie `YERBA_SESION` en memoria y la adjunta a todo lo que reenvía `/backend/**` y al sondeo
  de estado (`server/backend-session.ts`). La cookie que traiga el navegador no se reenvía nunca:
  el backend ve la cuenta del simulador y nada más.
- **Sesión vencida.** Los sondeos son `GET` y no cuentan como actividad, así que la sesión vence
  por inactividad como cualquier otra. Ante un `401` reinicia sesión y reintenta **una sola
  vez**; si varias peticiones lo reciben a la vez, abren una única sesión nueva entre todas.
- **Sin credenciales** no intenta iniciar sesión: el backend rechaza las peticiones, la barra
  dice "sin credenciales" y la publicación de telemetría por MQTT sigue funcionando, porque no
  pasa por el backend. Si el backend rechaza las credenciales, dice "credenciales rechazadas" y
  no vuelve a probarlas hasta 30 s después.
- **`/api/camara/v1/**` no lleva la sesión.** Es el contrato del dispositivo, con sus propios
  tokens; un `401` ahí es del dispositivo y reiniciar la sesión no lo arreglaría.

**Lo que el rol Servicio no trae por defecto.** Regenerar la topología (`POST /api/topologia`)
pide `topologia.gestionar`, y el rol no lo tiene: el panel muestra el `403` del backend. Hacelo
desde el dashboard, o que el Administrador le agregue ese permiso al rol en la matriz — sabiendo
que vale para todas las cuentas de servicio, no sólo para la del simulador. La emisión
automática tampoco puede leer el intervalo de sensado (`configuracion.ver`) y usa
`AUTO_EMISSION_INTERVAL_MS`.

---

## Qué endpoints del sistema usa

Ninguno es del simulador. Todos son superficie **pública** de la plataforma:

| Endpoint | Quién lo va a usar en producción |
|---|---|
| `GET /api/topologia`, `GET /api/nursery` | el dashboard |
| `POST /api/topologia` | el panel de topología del dashboard |
| `POST /api/capturas/ordenes` | el planificador de pasadas del riel |
| `GET /api/capturas/ordenes/{id}` | idem |
| `GET /api/camara/dispositivos`, `POST /api/camara/vinculacion` | el backoffice |
| `POST /api/diagnosticos` | el servicio de inferencia |

Por eso el recorrido que se ensaya acá es literalmente el que va a correr solo cuando exista el
modelo. **El simulador no toca `/api/camara/v1/**`**: ese es el contrato del dispositivo de
captura, y lo implementa la app de cámara.

Tampoco le habla nunca a la app de cámara. Emite la orden al backend, el backend la empuja al
teléfono por SSE, el teléfono sube la imagen, y el panel consulta el avance de la orden.

---

## Estructura

```
simulador/
├── server/          Express + Vite en modo middleware: un proceso, un puerto
│   ├── index.ts       arranque y cableado
│   ├── config.ts      configuración desde el entorno
│   ├── contract.ts    espejo de contrato.h (claves y unidades del nodo)
│   ├── mqtt.ts        publicador al broker
│   ├── store.ts       sensores simulados, persistidos en data/
│   ├── emission.ts    emisión automática
│   ├── api.ts         API interna (/api/**)
│   ├── proxy.ts       proxy al backend (/backend/**)
│   └── backend-session.ts  sesión de la cuenta de servicio ante el backend
├── src/             UI React, con estilos propios
└── data/            estado local (no se versiona)
```

**Idioma del código**: inglés, salvo lo que ve el operario —textos de la interfaz, mensajes de
error que llegan a pantalla y valores de dominio como `Clorosis`—, que va en español. Es la
convención del repo (`CLAUDE.md` §7).

---

## Detalles que sorprenden

- **La emisión automática no se persiste.** Siempre arranca apagada: levantar el simulador
  nunca debería empezar a pisar valores por su cuenta.
- **El contrato MQTT está espejado en tres lugares** — el firmware, `ContratoNodo.java` del
  backend y `server/contract.ts` acá. La fuente de verdad es
  `Desarrollo/embebido/comun/contrato.h`; si cambia una clave o una unidad, hay que actualizar
  los tres. Se acepta la duplicación porque compartirlo con el backend haría que esta carpeta
  dejara de ser borrable.
- **`ce` viaja convertida.** Acá se trabaja en dS/m (la unidad de la plataforma) y se publica en
  µS/cm (la unidad de la sonda). Si mandás `1.4` y el vivero muestra `1400`, la conversión se
  rompió.
- **`uv` no es radiación UV**: es % de luz de un LDR. La clave se conserva por compatibilidad
  con el firmware ya escrito.
- **Los sensores viven en `data/`**, no en la base del vivero. Borrar la carpeta borra los
  sensores de prueba y nada más.

---

## Modo estático del dashboard

Si lo que querés es *mostrar* cómo se ve el sistema, no hace falta levantar nada de esto: el
dashboard tiene una demo ilustrativa completa, sin backend ni broker.

```bash
cd Desarrollo/frontend && npm run dev:demo
```

El simulador es para lo otro: probar el sistema **real** en funcionamiento, inyectándole
hardware simulado.
