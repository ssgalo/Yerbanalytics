## 1. Spike de viabilidad (antes de construir nada encima)

- [x] 1.1 Crear el esqueleto mínimo del proyecto Gradle en `Desarrollo/camara-android/` (Kotlin,
      Compose, minSdk 26, target 35) y verificar que `./gradlew assembleDebug` produce una APK
- [ ] 1.2 Instalar la APK en el teléfono del piloto y verificar que un foreground service de tipo
      `camera|specialUse`, arrancado con la app visible, **sigue pudiendo abrir la cámara con la
      pantalla apagada y el teléfono bloqueado** (riesgo principal del diseño, D2/D3)
- [ ] 1.3 Verificar contra el backend levantado que `http://<ip>:8000/api/camara/v1/config` responde
      a un cliente nativo sin TLS y sin CORS (D4)
- [ ] 1.4 Registrar el resultado del spike en `design.md` → *Open Questions*; si 1.2 falla, ajustar
      el diseño a "pantalla encendida" antes de seguir

## 2. Proyecto y andamiaje

- [x] 2.1 Estructura definitiva del proyecto Gradle: `settings.gradle.kts`, `build.gradle.kts`,
      wrapper, `app/build.gradle.kts` con Compose, CameraX, OkHttp + `okhttp-sse`,
      kotlinx-serialization y DataStore
- [x] 2.2 `.gitignore` propio del proyecto (`build/`, `.gradle/`, `local.properties`, `*.apk`) y
      confirmar que ninguna APK ni credencial queda versionada
- [x] 2.3 `AndroidManifest.xml`: permisos `INTERNET`, `CAMERA`, `FOREGROUND_SERVICE`,
      `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`,
      `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`
- [x] 2.4 `network_security_config.xml` con tráfico en claro permitido y anclas de confianza
      `system` + `user`, referenciado desde el manifiesto (D4)

## 3. Cliente del contrato

- [x] 3.1 Modelos serializables del contrato (`Orden`, `ConfigCaptura`, `MetadataImagen`,
      `AcuseFallo`, `Heartbeat`, `Error`) tolerantes a campos desconocidos
- [x] 3.2 `ContratoClient`: los siete endpoints del contrato, con la credencial en el header en
      todos —incluido el stream— y ningún endpoint fuera del namespace (D5)
- [x] 3.3 Gestión de credenciales: enrolamiento, renovación proactiva a ~2/3 de la vida del token,
      reintento único ante rechazo y descarte de la credencial cuando la renovación falla
- [x] 3.4 Persistencia en DataStore de `refreshToken`, `dispositivoId` y URL base; el token de
      acceso sólo en memoria (D8)
- [x] 3.5 Canal SSE con `okhttp-sse`: `readTimeout = 0`, reconexión con espera creciente y jitter
      acotada, y watchdog por ausencia de eventos (D6)
- [x] 3.6 Tests JVM del backoff, del watchdog y del parseo de eventos, incluidos eventos y campos
      desconocidos

## 4. Colas

- [x] 4.1 Cola de órdenes FIFO **persistida en disco**, acotada por `maxColaOrdenes`, con acuse
      `COLA_LLENA` al descartar (D7)
- [x] 4.2 Recuperación de la cola de órdenes al arrancar el servicio: retomar las no vencidas y
      descartar las vencidas dejando constancia
- [x] 4.3 Cola de envío en disco: JPEG en `filesDir/pendientes/` + sidecar de metadata, con índice
      recuperable al arrancar
- [x] 4.4 Tope de la cola de envío (50 imágenes / 200 MB) descartando lo más antiguo, con registro
      en el log
- [x] 4.5 Reintento con espera creciente, drenaje al recuperar conectividad y **`409` tratado como
      éxito** (D7)
- [x] 4.6 Tests JVM de ambas colas: orden de ejecución, desborde con acuse, tope por cantidad y por
      tamaño, drenaje, y recuperación tras muerte del proceso

## 5. Captura

- [x] 5.1 `CapturaController` sobre CameraX: `bindToLifecycle` con `ImageCapture` ante la primera
      orden, **reutilización de la sesión** en las órdenes siguientes y `unbindAll` al vencer la
      ventana de inactividad configurable (D3)
- [x] 5.2 Espera por **convergencia de 3A** antes del disparo, con `warmupMs` como plazo máximo y
      no como espera fija; sin volver a esperarla dentro de la misma ráfaga (D3)
- [x] 5.3 `ResolutionSelector` a partir de `anchoMax`/`altoMax` con degradación a la resolución más
      cercana, y `setJpegQuality` desde `calidadJpeg` (D12)
- [x] 5.4 Cambio de configuración sobre sesión viva: calidad aplicada en caliente, resolución
      marcando la sesión obsoleta para que la próxima orden la vuelva a atar (D12)
- [x] 5.5 Metadata veraz: ancho y alto reales del JPEG, `sha256` de los bytes enviados,
      `capturadaEn`, y `constraints` con los ajustes de cámara efectivamente aplicados
- [x] 5.6 Mapeo de fallos al conjunto cerrado de motivos: cámara no disponible → `CAMARA_NO_LISTA`,
      exportación fallida → `EXPORTACION_FALLIDA`, plazo local excedido → `TIMEOUT_LOCAL`,
      reintentos agotados → `ENVIO_AGOTADO`
- [x] 5.7 `Preview` atado **sólo** cuando la Activity está visible, de modo que el visor aparezca
      durante la captura y no exista en reposo (D3/D11)

## 6. Servicio en primer plano

- [x] 6.1 `DispositivoService` (`LifecycleService`) que aloja credenciales, canal, colas, heartbeat
      y captura; la Activity se ata a él y puede morir sin afectarlo (D2)
- [x] 6.2 Notificación persistente con el estado del dispositivo (conectado / reconectando /
      degradado / sin cámara) y acceso a la pantalla de la app
- [x] 6.3 Heartbeat con la cadencia de `heartbeatSeg`, informando `capturaListo` real y los
      contadores de exitosas, fallidas y pendientes de envío
- [x] 6.4 `WakeLock` parcial acotado a la ventana de captura y envío (D10)
- [x] 6.5 Receptor de `BOOT_COMPLETED` con **modo degradado** cuando el sistema niegue el tipo
      `camera`, y promoción a modo normal al abrirse la Activity (D9)
- [x] 6.6 Solicitud de exención de optimización de batería desde la pantalla de puesta en marcha
      (D10)

## 7. Interfaz

- [x] 7.1 Pantalla de vinculación: URL del backend + código, con validación y mensajes de error
      legibles; se muestra una única vez en la vida del dispositivo
- [x] 7.2 Pantalla de operación: estado de conexión, contadores, visor efímero y log desplazable
      (D11)
- [x] 7.3 Log con marca temporal y niveles: ventana acotada en pantalla + **archivo rotativo**
      (~5 × 1 MB) en el almacenamiento de la app, con acción de exportar/compartir (D11)
- [x] 7.4 Flujo de permisos en primer arranque: cámara, notificaciones y batería, explicando para
      qué sirve cada uno
- [x] 7.5 Disparo local de prueba accesible desde la pantalla, para verificar la cámara sin backend

## 8. Verificación punta a punta

- [ ] 8.1 Ciclo completo contra un backend levantado: vinculación desde el simulador, enrolamiento,
      canal abierto, **Pedir captura** e imagen visible en el dashboard
- [ ] 8.2 Captura con la pantalla apagada y el teléfono bloqueado
- [ ] 8.3 Corte de red durante una subida: la imagen queda en disco y se drena al volver la red
- [ ] 8.4 Corte de red durante el canal: reconexión sola y drenaje de las órdenes acumuladas
- [ ] 8.5 Reinicio del teléfono: el servicio vuelve y el dispositivo opera (o degrada según D9)
- [ ] 8.6 Cambio de resolución desde el backend: la captura siguiente lo aplica sin tocar el
      teléfono
- [ ] 8.7 Ráfaga de órdenes seguidas: la cámara se abre una vez, se reutiliza en todas y se cierra
      al vencer la inactividad; comparar tiempos de la primera captura contra las siguientes
- [ ] 8.8 Matar el proceso con órdenes encoladas: al volver el servicio las retoma sin esperar el
      vencimiento del backend
- [ ] 8.9 Baja del dispositivo desde el panel: la app detecta el rechazo y vuelve a vinculación
- [ ] 8.10 Auditar el tráfico emitido y confirmar que **toda** petición cae dentro del namespace del
      contrato
- [x] 8.11 Confirmar con `git status` que el backend no tiene ni una línea modificada — criterio de
      aceptación del cambio
- [ ] 8.12 Correr la suite de conformidad del contrato y verificar que sigue en verde

## 9. Documentación

- [x] 9.1 `Desarrollo/camara-android/README.md`: qué es, por qué es un proyecto aparte y su relación
      con el contrato y con la PWA
- [x] 9.2 README — **generar la APK**: requisitos (JDK 17, Android Studio, SDK 35), build desde
      Android Studio y por `./gradlew assembleDebug`, y dónde queda el archivo
- [x] 9.3 README — **instalar en el teléfono**: por `adb install` y por copia del archivo, con la
      habilitación de instalación desde orígenes desconocidos
- [x] 9.4 README — **ajustes de Android obligatorios**: permisos de cámara y notificaciones,
      exención de optimización de batería, opciones de desarrollador y depuración USB, y ajustes
      por fabricante que matan servicios; con el porqué de cada uno
- [x] 9.5 README — **puesta en marcha**: backend, simulador, código de vinculación, URL del backend
      y primera captura
- [x] 9.6 README — **diagnóstico de fallas**: tabla síntoma → causa (deja de responder con el
      teléfono quieto, no llegan órdenes, la cámara no abre, credencial rechazada, backend
      inalcanzable por IP cambiada)
- [x] 9.7 README — **el desvío de TLS**: por qué la app no necesita la CA local, qué se asume al
      operar sin cifrado y cómo apuntarla a `https://…:8443` sin recompilar (D4)
- [x] 9.8 Actualizar `CLAUDE.md` §1 (mapa del repositorio) y §6.1 (segundo cliente del contrato)
- [x] 9.9 Actualizar `Desarrollo/contratos/camara/v1/README.md` §1: la app Android existe, y con
      ella el criterio de aceptación del contrato queda ejercido
- [x] 9.10 Nota en `Desarrollo/camara/README.md` señalando que ahora hay un cliente nativo y en qué
      se diferencia
