# Yerbanalytics · App de cámara

Software del **dispositivo de captura** de imágenes cenitales. Corre en Safari sobre un
iPhone montado en el riel: se abre una vez, queda en primer plano y responde las órdenes de
captura que le manda el backend.

No es una vista del dashboard. Es un proyecto propio, hermano de `frontend/` y `backend/`, y
la razón es concreta: **es la implementación de referencia de un contrato, no la
implementación definitiva**. Es probable que el cliente final sea una app Android nativa —
donde un foreground service puede sostener la operación sin depender de que la pantalla esté
encendida. Cuando eso pase, este directorio se borra entero y nada más en el repositorio se
entera.

> **Contrato:** [`Desarrollo/contratos/camara/v1/`](../contratos/camara/v1/README.md).
> Esa es la fuente de verdad. Esta app implementa ese contrato y nada más: si necesitara un
> endpoint fuera de `/api/camara/v1/**`, el problema sería del contrato, no de la app.

---

## Arranque rápido

La app de cámara **no funciona sola**: necesita el backend levantado y un código de
vinculación que se genera desde el simulador. Las tres cosas, cada una en su terminal:

```bash
# 1. Backend — HTTP en 8000 (dashboard) y HTTPS en 8443 (cámara)
cd Desarrollo/backend && ./mvnw spring-boot:run

# 2. Simulador — de acá sale el código de vinculación
cd Desarrollo/frontend && npm run dev:sim          # http://localhost:5180

# 3. App de cámara
cd Desarrollo/camara && npm run dev                # https://<tu-ip>:5190
```

La primera vez, antes del paso 3:

```bash
cd Desarrollo/certs && ./generar-certificados.sh   # detecta tus IPs solo
cd ../camara && npm install
```

**No hay que configurar ninguna IP.** El script de certificados detecta todas las direcciones
de la máquina y las incluye en el certificado; la app deduce la dirección del backend del host
por el que la abriste. Por eso `.env` es opcional (`env.example` explica los casos raros).

Vite imprime la URL de red al arrancar (`Network: https://…`). **Esa** es la que se abre en el
iPhone; `localhost` no sirve, porque desde el teléfono localhost es el teléfono.

Scripts: `dev` · `build` · `preview` · `lint` · `format` · `test`.

> Si Vite falla con *"Port 5190 is already in use"*, hay una instancia anterior colgada. En
> PowerShell:
> ```powershell
> Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
>   Where-Object { $_.CommandLine -like '*Yerbanalytics*' } | Stop-Process -Force
> ```

---

## HTTPS: no es opcional

`getUserMedia` **exige origen seguro**. Sin HTTPS no hay cámara, salvo en `localhost` — que
no sirve, porque el iPhone no es la máquina donde corre Vite.

Y como una página HTTPS no puede llamar a un endpoint HTTP, **el backend también necesita
TLS**: por eso expone un conector adicional en el 8443, dejando el 8000 en HTTP para el
dashboard.

### Paso 1 — generar los certificados (una vez)

```bash
cd Desarrollo/certs
./generar-certificados.sh                       # detecta las IPs de la máquina
./generar-certificados.sh 10.0.0.5 yerba.local  # o pasalas a mano
```

Crea una CA local y un certificado de servidor para todas las direcciones de la máquina —
todas, y no una elegida automáticamente, porque con varias redes activas (WiFi, cable, VPN,
Docker) adivinar "la buena" produce un certificado que el teléfono rechaza sin explicar nada.

No hace falta `mkcert`: el script usa sólo `openssl`. El certificado cumple lo que iOS exige
desde iOS 13 —SAN con la IP, EKU `serverAuth`, SHA-256 y menos de 398 días de validez—, que es
la razón por la que un certificado hecho a mano suele fallar en el iPhone y andar en el resto
de los navegadores.

Genera cuatro archivos (ninguno se versiona):

| Archivo | Para qué |
|---|---|
| `ca.pem` | **Se instala en el iPhone.** Es lo único que hay que llevar al teléfono |
| `servidor.pem` + `servidor-key.pem` | Los toma Vite solo, sin configurar nada |
| `servidor.p12` | Keystore del conector TLS del backend |

### Paso 2 — instalar la CA en el iPhone

El backend la sirve por su puerto **HTTP**, así que el teléfono puede bajarla sin haber
confiado en nada todavía:

1. En Safari, abrí **`http://<tu-ip>:8000/ca.pem`** → *Permitir* → descarga el perfil.
2. **Ajustes → General → VPN y gestión de dispositivos** → instalar el perfil.
3. **Ajustes → General → Información → Ajustes de confianza de certificados** → activar
   *Yerbanalytics CA local*.

**El paso 3 no es opcional y es el que se saltea todo el mundo.** Instalar el perfil no
alcanza: hasta que no se active la confianza, Safari sigue rechazando el certificado y el
síntoma es idéntico a no haber instalado nada.

### Sobre las advertencias que muestra iOS

Son correctas y conviene entenderlas: una CA raíz de confianza puede avalar **cualquier**
dominio. Quien tuviera la clave privada de esta CA (`certs/ca-key.pem`) **y** además pudiera
interceptar el tráfico del teléfono, podría hacerse pasar por cualquier sitio sin que Safari
protestara. Por eso iOS te hace confirmar dos veces y en dos lugares distintos.

En este caso el riesgo está acotado por tres cosas:

1. **La CA lleva `nameConstraints`**: sólo puede avalar la subred del vivero, `localhost` y
   los hostnames que le pasaste al script. Un certificado falso para cualquier otro dominio
   es rechazado por el validador **aunque esté firmado con esta misma clave**.
2. **`pathlen:0`**: no puede emitir CAs intermedias, sólo certificados finales.
3. **La clave privada nunca sale de tu máquina**: está en `Desarrollo/certs/`, ignorada por
   git (verificable con `git check-ignore -v Desarrollo/certs/ca-key.pem`).

Lo que **sí** conviene tener presente:

- La clave **no tiene passphrase**. Quien pueda leer el archivo, puede usarla. Protegela como
  a cualquier credencial: no la subas a Drive, no la mandes por chat, no la copies al
  teléfono.
- La CA vale 10 años. Si el proyecto termina, desinstalá el perfil (abajo).
- Instalá la CA **sólo en dispositivos tuyos**. No se la pases a nadie más "para que pruebe".

### Desinstalar la CA del iPhone

Cuando termines de usar el sistema, o si perdés el control de la máquina donde vive la clave:

**Ajustes → General → VPN y gestión de dispositivos** → *Yerbanalytics CA local* → **Eliminar
perfil**.

Con eso el teléfono deja de confiar en la CA de inmediato. No hace falta tocar nada más.

---

## Poner el dispositivo en marcha

```bash
# 1. Backend (HTTP en 8000 para el dashboard, HTTPS en 8443 para la cámara)
cd Desarrollo/backend && ./mvnw spring-boot:run

# 2. Simulador
cd Desarrollo/frontend && npm run dev:sim

# 3. App de cámara
cd Desarrollo/camara && npm run dev
```

Después, desde el teléfono:

1. En el **panel de simulación** (`localhost:5180` → sección *Cámara del riel*), tocá
   **Generar código de vinculación**.
2. Abrí **`https://<tu-ip>:5190`** en el iPhone y tipeá el código. Se hace **una sola vez
   en la vida del dispositivo**: la credencial queda guardada en IndexedDB.
3. Tocá **Iniciar cámara** y aceptá el permiso. iOS exige un gesto: no se puede pedir solo.
4. Agregá la app a la pantalla de inicio para que corra en modo autónomo, sin la barra de
   Safari.
5. Dejala en primer plano.

Desde ahí, **Pedir captura** en el simulador emite una orden real y la foto vuelve por el
mismo camino que va a usar el planificador de pasadas del riel.

### Si la IP de la máquina cambia

Cambia con la red. Sólo hay que regenerar los certificados:

```bash
cd Desarrollo/certs && rm -f ca.pem ca-key.pem && ./generar-certificados.sh
```

No hay ningún archivo con la IP que haya que editar: la app deduce la del backend del host por
el que la abriste.

Al regenerar la CA hay que volver a instalarla en el iPhone. Si sólo querés reemitir el
certificado de servidor conservando la CA (y evitando el trámite en el teléfono), **no borres
`ca.pem` ni `ca-key.pem`**: el script reutiliza la CA existente.

---

## Diagnóstico rápido

| Síntoma | Causa casi segura |
|---|---|
| `blocked by CORS policy` | El origen de la app no está en `yerbanalytics.cors.origins` del backend |
| `No se pudo contactar al backend` | `VITE_API_BASE_URL` apunta a `localhost` — desde el iPhone, localhost es el iPhone |
| Safari rechaza el certificado | Falta el paso 3: activar la confianza de la CA, no sólo instalar el perfil |
| El botón *Iniciar cámara* falla | La app se está sirviendo por HTTP; `getUserMedia` exige origen seguro |
| La cámara abre pero no llegan órdenes | Revisá el log en pantalla: el indicador de conexión dice si el canal está abierto |

---

## Resolución de las capturas

La fija **el backend**, no el cliente:

```properties
yerbanalytics.capturas.ancho-max=1920
yerbanalytics.capturas.alto-max=1080
yerbanalytics.capturas.calidad-jpeg=0.85
```

La app pide esa resolución al abrir la cámara (como valor *preferido*, así el navegador
degrada en lugar de fallar) y el log deja constancia de lo pedido y lo obtenido:

```
Resolución: pedida 1920x1080 · obtenida 1920x1080 (2.1 MP) · frameRate 30
```

Si el número obtenido es mucho menor que el pedido, la app lo advierte: ahí el límite es del
equipo y subir la configuración no cambia nada.

### El techo, y por qué no son 12 MP

`getUserMedia` da acceso al **pipeline de video**, no al de fotografía fija. Los 12 MP de la
cámara del iPhone salen de `ImageCapture.takePhoto()`, que **Safari no implementa** — es la
misma restricción que obliga a capturar dibujando en un `<canvas>`. En la práctica el máximo
alcanzable ronda los 1920×1080, y hasta 3840×2160 en equipos que expongan modo 4K.

### Subir a 4K

```properties
yerbanalytics.capturas.ancho-max=3840
yerbanalytics.capturas.alto-max=2160
```

El cambio baja por el canal y la app **reabre el stream sola** para aplicarlo; no hace falta
tocar el teléfono. Pesá el costo antes: a 0,85 de calidad, una captura 4K ronda 1,5–3 MB
contra ~400 kB en 1080p. Sobre 600 sectores por ciclo son varios GB por pasada, y esas
imágenes van al filesystem del backend.

Para entrenar el modelo tampoco hace falta tanto: MobileNetV3 trabaja con entradas de 224×224,
así que 1080p ya deja margen de sobra para recortar el plantín y para revisión humana.

## Lo que iOS impone

Estas no son decisiones de diseño: son restricciones de la plataforma que definen cómo está
escrita la app.

| Restricción | Consecuencia en el código |
|---|---|
| No existe `ImageCapture` / `takePhoto()` en Safari | La foto sale de `drawImage` sobre un `<canvas>` + `toBlob` (`lib/captura.ts`) |
| El permiso de cámara exige un gesto | Botón **Iniciar**; nunca se pide el stream al cargar |
| El `<video>` sin `playsinline` se abre a pantalla completa | `playsinline`, `muted` y `autoplay` en el preview |
| iOS **suspende** la cámara al perder el primer plano | En `visibilitychange` se verifica `readyState` y se reinicializa si murió |
| No hay ejecución en segundo plano | Aviso imposible de ignorar cuando la app deja de estar operativa |
| Wake Lock existe desde iOS 16.4 y se pierde al ir atrás | Se pide con detección de soporte y se re-pide al volver |
| `localStorage` no sirve para blobs | La cola de envío usa IndexedDB (`lib/almacen.ts`) |

Y una que no es de iOS pero se le parece: **el stream se abre una sola vez y se sostiene**.
Reabrirlo en cada disparo agrega segundos de latencia por captura y es la causa más común de
que estos sistemas terminen sin usarse.

---

## Cómo está armado

```
src/
├── lib/
│   ├── contrato.ts   Cliente del contrato v1. Todo el tráfico pasa por acá.
│   ├── almacen.ts    IndexedDB: credencial + cola de imágenes sin entregar.
│   ├── cola.ts       Cola FIFO de órdenes y backoff exponencial con jitter.
│   └── captura.ts    Canvas → JPEG, sha256 y ajustes de cámara.
├── hooks/
│   ├── useCamara.ts       Stream, warm-up, wake lock, recuperación tras background.
│   ├── useDispositivo.ts  Credenciales, canal SSE, colas, envío, heartbeat.
│   └── useLog.ts          Log de eventos en pantalla.
└── components/
    ├── Vinculacion.tsx    Se ve una sola vez.
    └── Panel.tsx          Estado, preview, contadores y log.
```

### Por qué hay un log en pantalla

Porque depurar con las devtools enchufadas a un iPhone montado en un riel no es practicable.
Todo lo que pasa —cámara, canal, captura, envío— tiene que poder leerse desde el propio
teléfono.

Ahí aparece también **qué ajustes de exposición y balance de blancos se pudieron aplicar
realmente**. Safari los soporta de forma muy parcial, y esa es la evidencia para decidir con
datos si la consistencia entre imágenes alcanza para entrenar el modelo. Si no alcanza, la
salida es iluminación controlada en el riel — que es hardware y otro problema.

### Las dos colas

- **Órdenes (memoria).** Si llega una orden con otra captura en curso, se encola. Al llegar
  al tope se descarta la más vieja y **se la acusa al backend** con motivo `COLA_LLENA`: un
  descarte silencioso haría que la orden espere su vencimiento en vez de reintentarse.
- **Envío (IndexedDB).** Reintentos con espera creciente; agotados, la imagen se guarda y se
  drena al volver la red. Acotada a ~50 imágenes / 200 MB, porque Safari desaloja el storage
  sin avisar y una cola sin techo rompe la app.

La cola local es una **optimización, no la garantía**: aunque el teléfono pierda todo lo
pendiente, el backend vence la orden y la reintenta.

---

## Verificar que cumple el contrato

```bash
cd ../contratos/camara/v1/conformidad
npm test
```

La suite corre contra un backend levantado y verifica códigos de respuesta y transiciones de
estado. Es el mismo criterio de aceptación que tendría que pasar un cliente Android.
