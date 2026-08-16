# Yerbanalytics · App de cámara (Android nativa)

Software del **dispositivo de captura** de imágenes cenitales, para un teléfono Android montado
en el riel. Se instala una vez, se vincula una vez, y a partir de ahí responde las órdenes de
captura que le manda el backend **con la pantalla apagada y el teléfono bloqueado**.

Es un proyecto propio, hermano de `frontend/`, `backend/` y `camara/`. Borrar este directorio no
obliga a tocar nada más del repositorio.

> **Contrato:** [`Desarrollo/contratos/camara/v1/`](../contratos/camara/v1/README.md).
> Esa es la fuente de verdad. Esta app implementa ese contrato y nada más: si necesitara un
> endpoint fuera de `/api/camara/v1/**`, el problema sería del contrato, no de la app.

---

## Por qué existe, si ya está la PWA

La PWA de [`Desarrollo/camara/`](../camara/README.md) fue la primera implementación y sigue
siendo válida: se hizo para un iPhone, donde no había herramientas para compilar nativo. Pero
pelea contra el navegador todo el tiempo.

| Restricción de iOS/Safari | Qué hace esta app |
|---|---|
| No hay ejecución en segundo plano | Un foreground service sostiene canal, colas y señal de vida |
| iOS suspende la cámara al perder el primer plano | El servicio conserva el acceso mientras vive |
| `getUserMedia` exige origen seguro → CA local + TLS | Un cliente nativo no tiene esa restricción (ver [TLS](#sobre-tls-por-qué-acá-no-hace-falta-la-ca)) |
| No existe `takePhoto()`: hay que dibujar en un `<canvas>` | CameraX usa el pipeline de fotografía fija |
| No se sabe si la exposición convergió: se descartan fotogramas por tiempo | Se espera la convergencia real de 3A |
| Sin filesystem: lo que no entra en memoria se pierde | Colas y log persistidos en disco |

Las dos apps **conviven a propósito**: dos clientes independientes contra el mismo contrato, sin
que el backend se entere de la diferencia, es la evidencia de que el contrato sirve.

---

## Generar la APK

### Lo que hace falta

| Herramienta | Versión | Cómo verificar |
|---|---|---|
| JDK | 17 o superior | `java -version` |
| Android SDK | plataforma API 36 + build-tools | lo instala Android Studio |
| Android Studio | opcional pero recomendado | — |

Gradle **no hace falta instalarlo**: el proyecto trae su wrapper.

### Opción A — Android Studio

1. **File → Open** y elegí la carpeta `Desarrollo/camara-android` (esta, no la raíz del repo).
2. Esperá a que sincronice. La primera vez baja las dependencias: son unos minutos.
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Cuando termina, el aviso de abajo a la derecha tiene un link **locate**.

Para instalar directo en un teléfono conectado por USB, alcanza con el botón ▶ **Run**.

### Opción B — línea de comandos

```bash
cd Desarrollo/camara-android
./gradlew assembleDebug          # en PowerShell: .\gradlew.bat assembleDebug
```

La APK queda en:

```
Desarrollo/camara-android/app/build/outputs/apk/debug/app-debug.apk
```

Otros comandos útiles:

```bash
./gradlew test                   # tests JVM (contrato, backoff, watchdog, colas)
./gradlew installDebug           # compila e instala en el teléfono conectado
./gradlew clean                  # si algo quedó raro
```

> **`local.properties`.** Guarda la ruta del SDK de tu máquina y **no se versiona**. Android
> Studio lo escribe solo al abrir el proyecto. Si compilás sólo por consola en un clon nuevo,
> crealo a mano:
> ```properties
> sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk
> ```
> o exportá `ANDROID_HOME` apuntando al SDK.

### Sobre la firma

La APK es de **debug**, firmada con la clave de depuración que Android Studio genera sola. Es lo
que corresponde para un piloto: no hay tienda, no hay distribución y no hay actualización
automática. No hace falta generar ningún keystore.

---

## Instalar en el teléfono

### Por USB (lo más cómodo si tenés el cable)

1. En el teléfono: **Ajustes → Acerca del teléfono** → tocá **Número de compilación** siete veces
   hasta que diga *Ya sos desarrollador*.
2. **Ajustes → Sistema → Opciones de desarrollador** → activá **Depuración por USB**.
3. Conectá el cable y aceptá el diálogo *¿Permitir depuración USB?* en el teléfono.
4. En la PC:

```bash
adb devices                      # tiene que listar el teléfono como "device", no "unauthorized"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb` está en `<SDK>/platform-tools/`.

### Por copia del archivo (sin cable)

1. Pasá `app-debug.apk` al teléfono (Drive, correo, WhatsApp Web, cable de datos).
2. Abrilo desde la app de Archivos.
3. Android va a decir que **la instalación desde esta fuente está bloqueada**. Tocá
   **Configuración** en ese mismo aviso y activá **Permitir desde esta fuente** para la app desde
   la que estás abriendo el archivo (Archivos, Chrome, Drive…).
   - En algunos equipos el camino es **Ajustes → Apps → Acceso especial → Instalar apps
     desconocidas**.
4. Volvé atrás y confirmá la instalación.

---

## Ajustes de Android que hay que tocar (y por qué)

Estos **no son opcionales**. Sin ellos la app se instala y arranca, pero el dispositivo deja de
responder solo al rato y el síntoma no dice nada útil.

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Permiso de cámara** | Se pide al abrir la app | Sin él el servicio arranca **degradado**: sostiene el canal e informa al backend que no puede capturar, en vez de mentirle |
| **Permiso de notificaciones** | Se pide al abrir la app (Android 13+) | La notificación permanente es lo que mantiene vivo al servicio. Sin ella el sistema lo mata |
| **Sin optimización de batería** | La app lo ofrece sola al arrancar. A mano: **Ajustes → Apps → Yerbanalytics Cámara → Batería → Sin restricciones** | Un servicio en primer plano no lo mata el sistema, pero **Doze le corta la red a un teléfono quieto** — y uno montado en un riel está siempre quieto |
| **Instalación de orígenes desconocidos** | Ver arriba | Sólo para instalar; no afecta la operación |
| **Depuración USB** | Opciones de desarrollador | Sólo si instalás por `adb` o querés leer `logcat` |

### Fabricantes que matan servicios igual

Xiaomi, Huawei, Oppo, Vivo y Samsung agregan su propia capa de ahorro de energía **por encima**
de la de Android, y no alcanza con la exención estándar:

| Marca | Qué buscar |
|---|---|
| Xiaomi / Redmi | Seguridad → Batería → Ahorro de energía de apps → *Sin restricciones*; y **Autostart** activado |
| Huawei / Honor | Batería → Inicio de apps → pasar la app a **gestión manual** con las tres opciones activadas |
| Samsung | Batería → Límites de uso en segundo plano → sacar la app de *Apps en suspensión* |
| Oppo / Realme / Vivo | Gestor de batería → Alto consumo en segundo plano → permitir |

Si el servicio igual muere, no se pierde el sistema: el backend vence la orden y la reintenta. Se
pierde una pasada, no la plataforma.

---

## Puesta en marcha

```bash
# 1. Backend (HTTP en 8000 — es todo lo que esta app necesita)
cd Desarrollo/backend && ./mvnw spring-boot:run

# 2. Simulador — de acá sale el código de vinculación y las órdenes de prueba
cd Desarrollo/simulador && npm run dev        # http://localhost:5180
```

Después, en el teléfono (que tiene que estar en **la misma WiFi** que la PC):

1. Averiguá la IP de la PC: `ipconfig` en Windows, `ip addr` en Linux. Algo como `192.168.1.100`.
2. En el **panel de simulación** (`localhost:5180` → sección *Cámara del riel*) tocá **Generar
   código de vinculación**.
3. Abrí la app y completá:
   - **Backend**: `http://192.168.1.100:8000` ← la IP de tu PC, no `localhost`
   - **Código**: el que generó el simulador
   - **Nombre**: cómo querés verlo en el panel
4. Aceptá los permisos de cámara y notificaciones, y la exención de batería cuando la ofrezca.
5. Listo. **Se hace una sola vez en la vida del dispositivo**: la credencial queda guardada.

Desde ahí, **Pedir captura** en el simulador emite una orden real y la foto vuelve por el mismo
camino que va a usar el planificador de pasadas del riel.

> `localhost` **no sirve** como dirección del backend: desde el teléfono, localhost es el
> teléfono.

---

## Cómo opera

```
        reposo (cámara APAGADA, canal abierto, heartbeat cada 15s)
           │
           │ llega una orden por el canal
           ▼
        abre la cámara ─→ espera convergencia de 3A (techo: warmupMs)
           │                ↑
           │                └── en la 2ª orden de la ráfaga ya está convergido
           ▼
        dispara ─→ guarda el JPEG en disco ─→ lo sube ─→ borra el pendiente
           │
           │ ¿llegó otra orden? ── sí ─→ reutiliza la sesión, vuelve a disparar
           │        │
           │        no
           ▼        ▼
        12s sin trabajo ─→ cierra la cámara ─→ reposo
```

**Por qué no abre y cierra la cámara en cada foto.** Una pasada de riel son 600 órdenes seguidas.
Abrir y cerrar 600 veces cuesta latencia y, sobre todo, reinicia la exposición y el balance de
blancos en cada disparo — que es lo que más ensucia la consistencia entre imágenes de la misma
pasada.

**Por qué no la deja prendida.** Entre pasadas, que es donde el teléfono pasa la mayor parte del
día, tener la cámara abierta consume batería y deja el indicador encendido sin motivo.

### Las dos colas, las dos en disco

- **Órdenes.** Si llega una orden con una captura en curso, se encola. Al llegar a
  `maxColaOrdenes` se descarta y **se la acusa** con motivo `COLA_LLENA`: un descarte silencioso
  haría que la orden espere su vencimiento en vez de reintentarse enseguida.
- **Envío.** El JPEG va a disco *antes* de intentar subirlo. Reintentos con espera creciente;
  agotados, queda guardado y se drena al volver la red. Acotada a 50 imágenes / 200 MB.

Ambas sobreviven al cierre de la app y al reinicio del teléfono. Igual son una **optimización, no
la garantía**: aunque el teléfono pierda todo, el backend vence la orden y la reintenta.

### Modo degradado

Si el teléfono se reinicia, el servicio vuelve solo. Pero Android puede negarle el acceso a
cámara a un servicio que arrancó sin interfaz visible. En ese caso la app **no miente**: sostiene
el canal, informa `capturaListo: false` en su señal de vida, acusa `CAMARA_NO_LISTA` a las
órdenes que lleguen y avisa en su notificación que hace falta abrirla una vez. Al abrirla,
recupera el acceso y sigue operando sin volver a vincularse.

---

## Sobre TLS: por qué acá no hace falta la CA

La PWA necesita la CA local de `Desarrollo/certs/` y el conector TLS del backend en el 8443
porque **el navegador** lo exige: `getUserMedia` sólo funciona en origen seguro, y una página
HTTPS no puede llamar a un endpoint HTTP.

Una app nativa no tiene ninguna de las dos restricciones. Habla HTTP contra el `:8000`, que ya
sirve el contrato completo. CORS tampoco entra en juego: un cliente nativo no manda `Origin`.

**Lo que se asume.** El §8 del contrato pide HTTPS igual, y el motivo es real: sin TLS el token
de acceso viaja en claro por la WiFi. Se asume a conciencia —red privada del vivero, token de 15
minutos, alcance acotado— y queda escrito acá en vez de escondido.

**Si algún día querés cifrado**, no hay que recompilar nada:

1. Generá los certificados: `cd Desarrollo/certs && ./generar-certificados.sh`
2. Pasá `ca.pem` al teléfono y andá a **Ajustes → Seguridad → Cifrado y credenciales → Instalar
   un certificado → Certificado de CA**.
3. Al vincular, tipeá `https://<ip>:8443` en vez de `http://<ip>:8000`.

Funciona porque la app confía en el almacén de CAs de usuario además del del sistema.

---

## Diagnóstico de fallas

| Síntoma | Causa casi segura |
|---|---|
| Deja de responder al rato, con el teléfono quieto | Optimización de batería. Ponela en *Sin restricciones* y revisá los ajustes del fabricante |
| «No se pudo contactar al backend» al vincular | La dirección apunta a `localhost`, o el teléfono está en otra WiFi que la PC |
| «El código no existe, ya se usó o venció» | Los códigos duran 10 minutos y son de un solo uso. Generá otro desde el simulador |
| La app dice **DEGRADADO** | El servicio no tiene acceso a cámara. Abrí la app y dejala un momento en pantalla |
| Vuelve a pedir el código de vinculación sola | El backend rechazó la credencial: dieron de baja el dispositivo desde el panel |
| El canal dice RECONECTANDO todo el tiempo | El backend se cayó, o cambió la IP de la PC. La IP se cambia volviendo a vincular |
| No llegan órdenes pero el canal dice CONECTADO | Mirá el log: el watchdog reconecta solo si dejan de llegar `ping` |
| La cámara no abre | Otra app la tiene tomada, o falta el permiso. El log dice cuál de las dos |

El **log en pantalla** es la primera herramienta de diagnóstico, y el botón *Exportar log*
comparte el historial completo en archivo — que es más de lo que se ve en pantalla. Para el resto:

```bash
adb logcat | grep -i yerbanalytics
```

---

## Cómo está armado

```
app/src/main/java/com/yerbanalytics/camara/
├── contrato/     Cliente del contrato v1. TODO el tráfico pasa por acá.
│   ├── Modelos.kt            Tipos del contrato, tolerantes a campos desconocidos
│   ├── ContratoClient.kt     Los siete endpoints. Credencial siempre por header
│   ├── CanalOrdenes.kt       SSE con reconexión y watchdog de silencio
│   ├── SesionToken.kt        Renovación proactiva a los 2/3 de la vida del token
│   ├── AlmacenCredenciales.kt  DataStore: URL, dispositivoId y refreshToken
│   ├── Backoff.kt            Espera creciente con jitter
│   └── Watchdog.kt           Cuándo dar el canal por muerto
├── colas/        ColaOrdenes y ColaEnvio, las dos en disco
├── captura/      CapturaController (CameraX, ráfaga y 3A) y Sha256
├── servicio/     DispositivoService (ES el dispositivo), notificación y BootReceiver
├── registro/     Log en memoria + archivo rotativo
└── ui/           MainActivity y las dos pantallas
```

### Por qué el servicio es el dispositivo y la Activity es sólo una ventana

Todo el estado operativo vive en el foreground service. La pantalla se ata a él para mostrar el
log y el visor, y **puede morir sin que la captura se entere**. Es la inversión exacta respecto
de la PWA, donde la pestaña visible *era* el dispositivo.

El tipo `camera` del servicio se declara al arrancarlo **con la app visible**: ese es el momento
en que el sistema concede el acceso a cámara, y una vez concedido el servicio lo conserva
mientras viva, aunque la pantalla se apague. Declarar el tipo **no** abre la cámara.

---

## Verificar que cumple el contrato

```bash
cd ../contratos/camara/v1/conformidad
npm test
```

La suite corre contra un backend levantado y verifica códigos de respuesta y transiciones de
estado. Es el mismo criterio de aceptación que pasó la PWA.

Y el criterio de aceptación de esta app, que es más fuerte:

```bash
git status --porcelain Desarrollo/backend    # tiene que estar vacío
```

Escribir este cliente no requirió tocar una línea del backend.
