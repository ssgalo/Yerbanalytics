## Why

La PWA de `Desarrollo/camara/` cumple el contrato de dispositivo, pero lo hace peleando contra
Safari: iOS **suspende la cámara** al perder el primer plano, no hay ejecución en segundo plano,
el Wake Lock se pierde al minimizar y `getUserMedia` exige origen seguro — de ahí la CA local, el
conector TLS del 8443 y el trámite de instalar un perfil en el teléfono. El resultado es un
dispositivo que sólo captura mientras alguien deja la pantalla encendida y la app en primer plano.

El contrato `camara/v1` se escribió previendo exactamente esto, y su criterio de aceptación es
literal: *escribir la app Android no debe requerir tocar una línea del backend*. Este cambio lo
ejerce. Una app nativa con **foreground service** sostiene el canal de órdenes con la pantalla
apagada y el teléfono bloqueado, y **CameraX** permite abrir la cámara sólo para el disparo y
cerrarla enseguida, en lugar de sostener un stream de video prendido todo el día.

## What Changes

- **Nuevo proyecto `Desarrollo/camara-android/`**: app Android nativa (Kotlin + Jetpack Compose),
  segundo cliente del contrato `camara/v1`. Proyecto Gradle independiente, hermano de `camara/`,
  `frontend/` y `backend/`.
- **Operación en standby con la cámara apagada**: en reposo la app no sostiene ninguna sesión de
  cámara. Ante una orden la abre, espera a que converjan los ajustes automáticos, dispara, y la
  sostiene sólo mientras siga llegando trabajo: al pasar una ventana de inactividad la cierra y
  vuelve a standby. Una pasada de riel son 600 órdenes seguidas, y reabrir el hardware en cada una
  agregaría latencia y reiniciaría la exposición en cada foto.
- **Foreground service persistente**: sostiene el canal SSE, la cola de órdenes, la cola de envío y
  el heartbeat con la pantalla apagada y el teléfono bloqueado. Sobrevive a que la app salga de
  primer plano; se relanza al reiniciar el teléfono.
- **UI mínima**: log de eventos desplazable + visor de cámara visible **sólo** durante una captura.
  Una pantalla de vinculación que se ve una única vez en la vida del dispositivo. El log además se
  persiste en archivo rotativo y se puede exportar, porque el equipo está montado en un riel.
- **Las dos colas en disco**: tanto las órdenes recibidas y todavía sin ejecutar como las imágenes
  que no se pudieron entregar sobreviven al cierre de la app y al reinicio del teléfono, acotadas
  en cantidad y en tamaño.
- **Sin certificados ni CA**: la app habla HTTP contra el `:8000`, que ya sirve todo el contrato.
  La CA local existía por una restricción del navegador (`getUserMedia` exige origen seguro), no
  del sistema; una app nativa no la tiene. La app admite igualmente una URL `https://…:8443`
  confiando en el almacén de CAs de usuario de Android, sin recompilar. Desvío deliberado del §8
  del contrato, justificado en `design.md`.
- **README con el trámite completo de la APK**: cómo generarla desde Android Studio y por
  `gradlew`, cómo instalarla, y qué opciones de Android hay que tocar para una app de desarrollo
  (orígenes desconocidos, optimización de batería, permisos, opciones de desarrollador).
- **La PWA no se toca.** Conviven a propósito: dos clientes independientes contra el mismo
  contrato es la evidencia de que el contrato sirve. Dar de baja la PWA es una decisión posterior,
  fuera del alcance de este cambio.
- **El backend no se modifica.** Ni un endpoint, ni una propiedad, ni un origen CORS (una app
  nativa no manda `Origin`). Si algo pareciera exigirlo, el defecto sería del contrato.

## Capabilities

### New Capabilities

- `camara-android`: cliente nativo Android del contrato de captura — operación en standby con
  cámara bajo demanda, ejecución sostenida en segundo plano vía foreground service, colas
  persistentes, UI de log + visor efímero, y empaquetado/distribución como APK.

### Modified Capabilities

Ninguna. El contrato (`camara-contrato`), la persistencia de capturas (`captura-persistencia`), el
registro de dispositivos (`camara-dispositivos`) y la PWA de referencia (`camara-pwa`) quedan como
están: este cambio es la prueba de que un cliente nuevo entra sin mover nada de eso.

## Impact

- **Código nuevo**: `Desarrollo/camara-android/` (proyecto Gradle: `app/`, `gradle/wrapper/`,
  `build.gradle.kts`, `settings.gradle.kts`, `README.md`).
- **Código modificado**: `CLAUDE.md` (§1 mapa del repositorio y §6.1, para nombrar al segundo
  cliente) y `Desarrollo/contratos/camara/v1/README.md` §1 (la app Android deja de ser hipotética).
  Nada más.
- **Backend**: sin cambios. Es el criterio de aceptación del cambio, no un efecto colateral.
- **Dependencias nuevas** (sólo dentro del proyecto Android): Kotlin, Jetpack Compose, CameraX,
  OkHttp + `okhttp-sse`, kotlinx-serialization, DataStore. Ninguna toca el resto del repo.
- **Toolchain**: JDK 17 y Android SDK (API 35). El repo suma un `.gitignore` propio para
  `build/`, `.gradle/` y `local.properties`; los APK generados no se versionan.
- **Verificación**: la suite de conformidad existente (`contratos/camara/v1/conformidad/`) es el
  criterio de aceptación del backend; la app se valida recorriendo el ciclo completo contra un
  backend levantado, sin variantes hechas para ella.
