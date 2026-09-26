## Why

El dispositivo de captura hoy habla con el backend por WiFi. En un vivero eso es una dependencia
frágil —humedad, estructura metálica, distancia al router— y en una demo es directamente un riesgo:
`Documentacion/2_ModeloDeNegocio.md:46` dice con todas las letras que *"Demostración con prototipo:
es nuestro canal decisivo para cerrar la venta"*, y
`Documentacion/arquitectura y hw/informe-ia-plan-entrenamiento.md:111` agrega que el test set del
modelo sale de los tubetes del prototipo, *"mismas condiciones que el día de la defensa"*. Una demo
que depende del WiFi del lugar es una demo que puede fallar delante del cliente.

Y hay un argumento más simple que todos los anteriores: **el teléfono necesita alimentación igual**.
Montado en el riel, capturando todo el día, se queda sin batería. El cable ya va a estar. La
pregunta no es *"¿pongo un cable?"* sino *"ya que hay uno, ¿le pido también que lleve los datos?"*.

El contrato `camara/v1` no presupone ningún transporte —su segundo principio es la *neutralidad de
plataforma* (`Desarrollo/contratos/camara/v1/README.md:36`)— así que llevar los bytes por un cable
en vez de por el aire no lo toca. Un cable es una red como cualquier otra.

Este cambio existe porque el procedimiento **no está escrito, no está verificado contra hardware
real y tiene una trampa que sólo aparece leyendo el código de la app**: la URL del backend queda
atada al enrolamiento del dispositivo (ver `design.md` D4). Sin eso resuelto, el prototipo se rompe
solo la primera vez que la PC cambia de IP.

## What Changes

- **El transporte pasa a ser una decisión documentada, no una suposición.** Se define el camino
  principal (USB tethering **con IP fija del lado de la PC**), la alternativa de URL estable
  (`adb reverse`) y el criterio para elegir entre las dos. El tethering "pelado" con DHCP queda
  descartado como opción por defecto, por el hallazgo de la URL.
- **Paridad Linux / Windows.** El testing inmediato es en Linux, pero el procedimiento queda
  soportado y escrito para los dos sistemas. No hay un "sistema de primera" y otro de segunda.
- **Cero cambios en el backend, y por una razón verificada, no por disciplina.**
  `Desarrollo/backend/src/main/resources/application.properties:2` fija `server.port=8000` y **no
  existe ninguna propiedad `server.address`**: Spring Boot bindea a todas las interfaces, así que el
  teléfono alcanza el backend por la interfaz USB sin tocar una línea. El resto del backend ya es
  portable a Windows (ver `design.md` D3 y D6).
- **Se declaró el hallazgo de la URL, y su disparador se cumplió.** La app persistía la URL del
  backend junto a la credencial sin ninguna vía para editarla después de vincular: cambiarla exigía
  un enrolamiento completo nuevo. El plan original era no tocar la app y elegir transportes con URL
  estable, dejando escrito el disparador para una pantalla de ajustes (ver `design.md` D4). La
  verificación contra hardware real (`tasks.md` §6.3) cumplió ese disparador, así que **este cambio
  terminó agregando esa pantalla**: `PantallaAjustes` permite editar sólo la URL, sin re-enrolar y
  conservando la credencial. Ver la actualización del 2026-09-26 en `design.md` D4.
- **Dos scripts auxiliares** (`herramientas/url-backend.sh` y `url-backend.ps1`) que detectan la
  interfaz USB, imprimen la URL a tipear en el teléfono y verifican el backend **por esa IP**, no
  por `localhost`. Ya están escritos; lo que falta es probarlos contra hardware real.
- **La documentación del transporte ya existe** en `Desarrollo/camara-android/transporte-usb.md`,
  escrita antes que este cambio. Acá se la respalda con decisiones, requisitos y una checklist de
  verificación; no se la duplica. Las correcciones que surgen del hallazgo de la URL se aplican
  sobre ese archivo.
- **Nada del contrato cambia.** `Desarrollo/contratos/camara/v1/openapi.yaml` no se toca, la versión
  del contrato no sube y la suite de conformidad no se modifica. Si algo de esto pareciera
  necesario, el defecto estaría en el contrato.

## Capabilities

### New Capabilities

- `transporte-usb`: procedimiento soportado para conectar el dispositivo de captura al backend por
  el cable USB —en Linux y en Windows—, con URL estable durante la vida del enrolamiento,
  diagnóstico de fallas y límites declarados.

### Modified Capabilities

Ninguna. El contrato (`camara-contrato`), la app nativa (`camara-android`), la PWA (`camara-pwa`) y
la persistencia de capturas (`captura-persistencia`) quedan como están. Que un transporte nuevo
entre sin modificar ninguna de ellas es, otra vez, la prueba de que el contrato sirve.

## Impact

- **Documentación**: `Desarrollo/camara-android/transporte-usb.md` (ya escrito, con el hallazgo de
  la URL y el procedimiento de IP fija incorporados) y punteros desde
  `Desarrollo/camara-android/README.md` (ya agregados).
- **Código nuevo**: `Desarrollo/camara-android/herramientas/url-backend.sh` y `url-backend.ps1`. Son
  de sólo lectura: miran interfaces y hacen una consulta HTTP; no levantan nada, no cambian rutas y
  no tocan el firewall.
- **Backend**: sin cambios. Verificado, no asumido.
- **Contrato**: sin cambios. La versión no sube.
- **App Android**: el hallazgo de la URL disparó una pantalla de Ajustes durante la verificación de
  este mismo cambio (`tasks.md` §6.3, `design.md` D4) — no quedó para un cambio posterior.
- **Dependencias nuevas**: ninguna. `adb` ya está instalado en la máquina del piloto
  (`/usr/bin/adb`, 34.0.4) y viene con el Android SDK que la app ya exige.
- **Verificación**: es lo único que este cambio no puede dar por hecho. **Nada de esto está probado
  contra hardware real todavía** — al momento de escribirlo el teléfono no estaba conectado. La §6
  de `tasks.md` es el trabajo real.

## Fuera de alcance

- **Usar el teléfono como webcam USB** (UVC, DroidCam, Iriun, `scrcpy` + `v4l2loopback`). Suena
  parecido y es lo contrario: devuelve a la PC un stream de video del que hay que extraer
  fotogramas, que es exactamente el problema del que la app nativa vino a escapar. El razonamiento
  completo está en `design.md` D1.
- ~~Agregar una pantalla de ajustes a la app para editar la URL del backend sin re-enrolar.~~
  **Resuelto en este cambio (2026-09-26)**: el disparador de `design.md` D4 se cumplió durante la
  verificación de hardware (`tasks.md` §6.3.1) y la pantalla se implementó acá, no en un cambio
  aparte.
- **Dar de baja el WiFi.** El transporte por cable se suma; no reemplaza nada. Volver al aire tiene
  que seguir siendo posible.
- **Cablear el vivero real.** Seiscientos sectores no se conectan uno por uno a una PC. Esto es para
  la maqueta, el prototipo y la defensa. Para producción se vuelve a red, y que no sorprenda.
- **Alimentación del teléfono.** Que el mismo cable cargue es una consecuencia feliz, no un
  requisito de este cambio. Si algún día el tramo supera lo que aguanta el USB, energía y datos se
  separan y el problema pasa a ser de calibre, no de red.
- **Poner el backend en `docker-compose`.** Hoy corre en el host y el servicio de inferencia le pega
  por `host.docker.internal` (`docker-compose.yml:35,49-50`), que existe de fábrica en Docker Desktop
  para Windows. No hay nada que arreglar acá.
- **Limpieza oportunista detectada, no parte del alcance**: en `docker-compose.yml:59` el volumen
  `capturas-data` está declarado y **ningún servicio lo usa** (el compartido real es el bind-mount
  de `Desarrollo/backend/capturas`). Es una declaración muerta. Se anota para que alguien la barra
  cuando toque ese archivo por otro motivo.
