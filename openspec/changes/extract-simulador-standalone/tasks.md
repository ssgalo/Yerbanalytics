> Orden deliberado: el simulador nuevo se construye y se verifica **antes** de podar nada.
> Entre el grupo 1 y el 5 conviven el simulador viejo y el nuevo, y eso está bien: permite
> comparar comportamiento contra el sistema actual sin perder el camino de vuelta.

## 1. Andamiaje del proyecto simulador

- [x] 1.1 Crear `Desarrollo/simulador/` con `package.json` propio (React, Vite, Express,
      cliente MQTT de Node, TypeScript), sin workspaces ni referencias al frontend
- [x] 1.2 Configurar TypeScript y Vite del simulador, con alias propios y sin `paths` que
      apunten fuera del directorio
- [x] 1.3 Montar el servidor Express con Vite en modo middleware (desarrollo) y servido
      estático del build (producción): un proceso, un puerto `5180`
- [x] 1.4 Crear `env.example` propio (URL del backend, host del broker, puerto, intervalo de
      emisión automática) y `.gitignore` que excluya `node_modules/`, `datos/` y `.env`
- [x] 1.5 Implementar el almacenamiento en JSON dentro de `datos/`, con lectura tolerante a
      archivo inexistente o corrupto
- [x] 1.6 Implementar el proxy `/backend/**` → backend, para que la UI hable siempre con su
      propio origen y el backend no necesite declarar CORS del simulador
- [x] 1.7 Implementar el estado de disponibilidad del backend, para que el simulador arranque
      y sea usable con el backend apagado

## 2. Contrato y publicación MQTT

- [x] 2.1 Escribir el espejo del contrato del nodo en el simulador (claves, unidades y
      conversión de `ce`), con el puntero explícito a `Desarrollo/embebido/comun/contrato.h`
      como fuente de verdad
- [x] 2.2 Implementar el publicador MQTT del simulador: conexión al broker, publicación en
      `nursery/zone/{zonaId}/telemetry` con QoS 1 y propagación del error al llamador
- [x] 2.3 Implementar la construcción del payload a partir de una lectura parcial: sólo las
      métricas cargadas, con `timestamp` explícito o sellado con la hora actual
- [x] 2.4 Rechazar el envío sin ninguna métrica, antes de tocar el broker
- [x] 2.5 Informar en la UI el fallo de publicación sin dar la lectura por enviada

## 3. Sensores simulados

- [x] 3.1 Implementar alta, baja y listado de sensores simulados sobre el almacenamiento
      propio, conservando el orden de alta
- [x] 3.2 Rechazar el alta con serial/MAC duplicado, comparando sin distinción de mayúsculas
- [x] 3.3 Poblar el desplegable de macro-zonas desde la topología vigente del backend, y
      reflejar los cambios cuando la topología se regenera
- [x] 3.4 Construir la tarjeta de sensor: envío por métrica individual y envío conjunto de las
      diez, cada uno con su fecha/hora opcional
- [x] 3.5 Verificar que dar de alta un sensor simulado no crea ni modifica ningún dispositivo
      del registro de hardware

## 4. Emisión automática

- [x] 4.1 Implementar el temporizador de emisión automática con los mismos rangos de valores
      que el simulador actual del backend, incluida la macro-zona con batería baja
- [x] 4.2 Dejarla apagada por defecto y conmutable desde la UI, sin publicar nada mientras
      esté apagada
- [x] 4.3 Detener el temporizador al apagar el proceso del simulador

## 5. Topología y panel de cámara en el simulador

- [x] 5.1 Escribir el cliente HTTP propio del simulador contra la API pública del backend
      (topología, dispositivos de cámara, vinculación, órdenes de captura, diagnósticos),
      declarando sólo los campos que usa
- [x] 5.2 Portar el control de regeneración de topología, con confirmación cuando ya hay una
      grilla cargada
- [x] 5.3 Portar el panel de cámara: estado del dispositivo, generación del código de
      vinculación y caso «sin dispositivo enrolado»
- [x] 5.4 Portar la emisión de la orden de captura con sector, macro-zona y posición de riel,
      y el seguimiento de estados hasta la imagen, incluidos los avisos de orden vencida y de
      reintentos agotados
- [x] 5.5 Resolver la `imagenUrl` de la captura contra el proxy: el backend la devuelve
      relativa (`/api/capturas/{id}/imagen`) porque no conoce su URL pública, y sin
      absolutizarla el navegador la busca en el origen de la página y la foto no aparece
- [x] 5.6 Portar la carga manual del diagnóstico, con sector y macro-zona precargados desde la
      orden y editables, y el bloqueo cuando no hay captura recibida
- [x] 5.7 Propagar los mensajes de error del backend a la UI del simulador (dato inválido,
      confianza fuera de rango, estado fuera de taxonomía)
- [x] 5.8 Escribir los estilos mínimos propios del simulador, sin importar tokens ni átomos
      del dashboard
- [x] 5.9 Verificar que el simulador no toca `/api/camara/v1/**`: ese contrato lo implementa
      únicamente el dispositivo de captura, y el simulador sólo emite y consulta órdenes

## 6. Verificación del simulador nuevo (con el viejo todavía en pie)

> Verificado contra el sistema real (Postgres y Mosquitto por `docker-compose.yml`, backend
> en el 8000, simulador en el 5180). El iPhone se reemplazó por un cliente que habla el mismo
> contrato `/api/camara/v1/**`, incluida la suscripción SSE por la que el dispositivo real
> recibe las órdenes: lo único no ejercitado es el hardware físico.
>
> Dos hallazgos durante la verificación, ambos de las pruebas y no del código:
> - En esta base el serial de `DEV-001` fue cambiado a mano a `"1"`, así que no coincide con
>   ningún MAC del seed. El heartbeat se verificó con `DEV-002`.
> - Una orden emitida **sin dispositivo conectado no vence**: queda `PENDIENTE` a propósito y
>   se despacha cuando alguien conecta (`CapturaService`). El vencimiento aplica a las
>   `ENTREGADA`, y ése sí se ejercitó hasta agotar los tres intentos.

- [x] 6.1 Enviar una métrica desde el simulador nuevo y verificar que el sector de la
      macro-zona la refleja conservando las demás
- [x] 6.2 Enviar una conductividad y verificar que se persiste en dS/m, es decir que la
      conversión de unidades del contrato es correcta
- [x] 6.3 Verificar el heartbeat: enviar con un serial/MAC registrado y con uno no registrado,
      y comprobar los dos comportamientos
- [x] 6.4 Recorrer el ciclo de cámara completo con el iPhone conectado: generar el código de
      vinculación desde el simulador, enrolar el dispositivo, emitir la orden, verificar que
      llega por SSE a la app de cámara, subir la foto y ver la imagen en el panel
- [x] 6.5 Cargar el diagnóstico sobre esa captura y comprobar que aparece en Diagnósticos de
      IA del dashboard con su imagen real
- [x] 6.6 Ejercitar el camino de falla: emitir una orden sin dispositivo conectado y verificar
      que el panel informa el vencimiento en vez de quedar esperando

## 7. Migración del dashboard

- [x] 7.1 Simplificar `NurseryContext` para que use `getRepository()` como el resto de los
      hooks, eliminando la consulta del modo y la elección de repositorio
- [x] 7.2 Quitar de `DataRepository` los seis métodos de simulación y los cinco de cámara, y
      podar `HttpRepository` y `MockRepository` en consecuencia
- [x] 7.3 Quitar de `types/domain.ts` los tipos que quedan sin uso (`ModoSimulacion`,
      `SimulacionEstado`, `SensorSimulado`, `EnvioTelemetria` y los de cámara que sólo usaba
      el panel)
- [x] 7.4 Eliminar `src/simulador/`, `src/features/simulacion/`, `hooks/useSimulacion.ts`,
      `hooks/useCamaraSim.ts` y `data/mock/simulacion.test.ts`
- [x] 7.5 Eliminar `simulador.html`, `vite.simulador.config.ts` y los scripts `dev:sim`,
      `build:sim` y `preview:sim`
- [x] 7.6 Agregar el script `dev:demo` que levanta el dashboard con `VITE_DATA_SOURCE=mock`, y
      documentar los dos modos en `env.example`
- [x] 7.7 Verificar que `npm run lint` pasa con 0 warnings y que `npm run test` pasa
- [x] 7.8 Verificar a mano los dos modos: en `mock` todas las secciones muestran la demo y
      ninguna consulta el backend; en `http` todas consultan el backend

## 8. Poda del backend

- [x] 8.1 Eliminar `controller/SimulacionController.java` y `service/SimulacionService.java`
- [x] 8.2 Eliminar `mqtt/MqttTelemetrySimulator.java` y `mqtt/MqttTelemetryPublisher.java`
- [x] 8.3 Eliminar `model/ModoOperacionEntity.java`, `model/SensorSimuladoEntity.java` y sus
      repositorios
- [x] 8.4 Eliminar los DTOs `SimulacionEstado`, `SensorSimulado` y `EnvioTelemetria`
- [x] 8.5 Quitar de `application.properties` las propiedades `yerbanalytics.mqtt.simulator.*`
      y el origen `:5180` de `yerbanalytics.cors.origins`
- [x] 8.6 Quitar de `data.sql` la semilla de `modo_operacion`
- [x] 8.7 Simplificar `DiagnosticoControllerTest` para que no dependa de un modo de operación,
      conservando la cobertura del alta
- [x] 8.8 Barrer los comentarios y Javadoc que mencionan al simulador
      (`CamaraAuthFilter`, `HttpsConnectorConfig`, `CapturaController`,
      `DiagnosticoController`, `DispositivoCamaraController`, `DispositivoCamara`,
      `ContratoNodo`, `DiagnosticoService`, `data.sql`)
- [x] 8.9 Verificar que `@EnableScheduling` se conserva, porque lo siguen usando
      `CapturaService`, `HistorialService` y `NurseryWatchdog`
- [x] 8.10 Compilar y correr la suite del backend

## 9. Migración de base de datos

- [x] 9.1 Escribir `resources/migracion-quitar-simulador.sql` con la baja de `modo_operacion` y
      `sensor_simulado`, siguiendo el patrón de los scripts de migración ya existentes
- [x] 9.2 Documentar en el script y en el README del backend que la baja es manual, porque
      `ddl-auto=update` no elimina tablas obsoletas
- [x] 9.3 Correr la migración sobre la base local y verificar que el backend arranca

## 10. Verificación de independencia

- [x] 10.1 Renombrar temporalmente `Desarrollo/simulador/` y verificar que el backend arranca,
      el dashboard carga en sus dos modos y la app de cámara opera
- [x] 10.2 Auditar por búsqueda que ni backend, ni dashboard, ni app de cámara mencionan al
      simulador en código o configuración
- [x] 10.3 Verificar que un clon limpio levanta el sistema sin instalar el simulador
- [x] 10.4 Restaurar el directorio y verificar que el simulador vuelve a funcionar con sus
      datos intactos

## 11. Documentación

- [x] 11.1 Escribir `Desarrollo/simulador/README.md`: qué es, cómo se levanta, cómo se
      configura, cómo se borra y por qué publica directo al broker
- [x] 11.2 Actualizar `CLAUDE.md`: mapa del repo (§1), sección del backend (§6) y una sección
      nueva del simulador con sus invariantes
- [x] 11.3 Actualizar el README del backend: baja del modo de operación, del envío manual y de
      las dos tablas, más el apunte de la migración manual
- [x] 11.4 Actualizar el README del frontend: los dos modos de arranque y la baja del panel de
      simulación

## 12. Script de arranque conjunto

> El simulador dejó de ser parte del sistema, pero sigue siendo parte del **trabajo diario**.
> `start-all` lo levanta como comodidad, tratándolo como opcional: si la carpeta no está, se
> omite el paso y el sistema arranca igual. Eso preserva lo que importa —que no sea una
> dependencia— sin obligar a abrir dos terminales todos los días.

- [x] 12.1 Apuntar `start-all.ps1` y `start-all.bat` al simulador nuevo
      (`Desarrollo/simulador`, `npm run dev`): quedaron rotos al eliminar `dev:sim` del frontend
- [x] 12.2 Instalar las dependencias del simulador aparte, porque ya no comparte
      `node_modules` con el dashboard
- [x] 12.3 Omitir el paso, sin error, cuando el directorio del simulador no existe
- [x] 12.4 Crear el `.env` del frontend desde `env.example` si falta, para que el dashboard no
      arranque en modo demo contra un backend que sí se está levantando
- [x] 12.5 Corregir el chequeo de `npm install` en el `.bat`: dentro de un bloque, `%ERRORLEVEL%`
      se expande al parsear y nunca detectaba la falla
- [x] 12.6 Abrir las dos pestanas del navegador —dashboard y simulador—, esperando a que cada
      servidor escuche para no mostrar un "no se puede conectar"
- [x] 12.7 Quitar `open: true` del `vite.config.ts` del frontend: con el launcher abriendo
      pestanas, se abria una de mas. El navegador ahora tiene un solo dueno
- [x] 12.8 Sondear las dos direcciones de loopback (`127.0.0.1` y `::1`) en vez de `localhost`:
      Vite escucha en IPv6 y Windows PowerShell 5.1 resuelve `localhost` solo a IPv4, con lo
      cual daba a Vite por caido siempre y nunca abria su pestana
- [x] 12.9 Sacar de Git `vite.config.js` y `vite.config.d.ts` del frontend, y redirigir la
      salida de `tsc -b` a `node_modules/.tmp`. Eran compilados de junio commiteados por
      error, y **Vite resuelve `vite.config.js` ANTES que el `.ts`**: venía leyendo el
      compilado, con lo cual la tarea 12.7 no surtía efecto y el dashboard abría dos veces
      (una Vite por el `open: true` viejo, otra el launcher). Cualquier edición del `.ts` se
      ignoraba en silencio hasta el siguiente build
