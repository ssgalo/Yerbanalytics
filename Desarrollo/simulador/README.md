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
cp env.example .env      # opcional: los defaults sirven para el setup normal
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

La barra de estado de arriba muestra los dos, así que si algo no anda se ve de entrada cuál
de los dos falta.

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

## Qué endpoints del sistema usa

Ninguno es del simulador. Todos son superficie **pública** de la plataforma:

| Endpoint | Quién lo va a usar en producción |
|---|---|
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
│   └── proxy.ts       proxy al backend (/backend/**)
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
