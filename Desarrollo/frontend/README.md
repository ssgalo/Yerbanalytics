# Yerbanalytics · Frontend

Dashboard de monitoreo IA del vivero de yerba mate. React + TypeScript + Vite.

Replica el diseño de alta fidelidad `Yerbanalytics.dc.html` (Claude Design) con una
arquitectura mantenible y lista para conectarse al backend.

## Requisitos

- Node.js 18+
- npm 10+

## Puesta en marcha

```bash
cp env.example .env      # configurá las variables (ver abajo)
npm install
npm run dev              # http://localhost:5173
```

> Nota: el harness no permite versionar archivos `.env*`, por eso la plantilla se
> llama `env.example` (sin punto). Copiala a `.env`.

## Scripts

| Script | Qué hace |
|--------|----------|
| `npm run dev` | Dashboard contra el **backend real** (HMR) — http://localhost:5173 |
| `npm run dev:demo` | Dashboard en **modo estático**: demo ilustrativa, sin backend |
| `npm run build` | Type-check + build de producción (`dist/`) |
| `npm run build:demo` | Build de la demo estática |
| `npm run preview` | Sirve el build |
| `npm run lint` | ESLint (0 warnings permitidos) |
| `npm run format` | Prettier |
| `npm test` | Tests (Vitest) |

### Los dos modos

`VITE_DATA_SOURCE` es el modo de operación de la app, y es el **único** punto de decisión del
origen de datos: rige para *todas* las secciones —vivero, mapa, sector, diagnósticos, hardware,
configuración, historial y topología—, así que nunca conviven en pantalla datos mock con datos
del backend.

| | `npm run dev` | `npm run dev:demo` |
|---|---|---|
| `VITE_DATA_SOURCE` | `http` | `mock` (preconfigurado en `.env.demo`) |
| Qué muestra | el vivero real | una demo determinística |
| Para qué | operar y probar el sistema | mostrar cómo se vería, sin levantar nada |
| Necesita backend | sí | no |

Se resuelve **al arrancar**, así que cambiar de modo exige reiniciar el dashboard. A cambio,
ninguna vista consulta el modo al backend: el backend no tiene modos.

> ¿Querés probar el sistema real sin hardware físico? Eso no es el modo estático: es el
> **simulador** (`Desarrollo/simulador/`), una app aparte que publica telemetría al broker
> como si fuera un nodo ESP32. Corré `npm run dev` acá y el simulador en su carpeta.

## Variables de entorno

Solo las variables con prefijo `VITE_` llegan al navegador.

| Variable | Default | Descripción |
|----------|---------|-------------|
| `VITE_DATA_SOURCE` | `mock` | Modo de la app: `http` (backend real) o `mock` (demo estática). Rige para todas las secciones |
| `VITE_API_BASE_URL` | `http://localhost:8000/api` | URL del backend (modo `http`) |
| `VITE_MOCK_SEED` | `20260613` | Semilla del generador determinístico (modo `mock`) |

## Sesión, usuarios y permisos

El dashboard exige iniciar sesión (HU-01) y muestra sólo lo que habilitan los permisos del rol
(HU-20). La autoridad es siempre el backend: el dashboard es presentación y cualquier petición
sin sesión o sin permiso recibe `401`/`403` igual.

### Login

- `/login` y `/cambiar-clave` viven fuera del shell: sin sesión no hay sidebar ni se sondea el
  vivero (`NurseryProvider` y `DemoExpoProvider` se montan recién con sesión).
- Error genérico "Credenciales incorrectas", sin marcar campos y limpiando la clave.
- Al entrar se vuelve a la ruta que se había pedido, si el rol todavía puede verla.
- Con clave temporal (alta o blanqueo) sólo se puede cambiar la contraseña.
- Si la sesión se cerró sola, el login dice por qué: *"Tu sesión se cerró por inactividad"*
  (`SESION_EXPIRADA`) o *"Tu cuenta cambió; volvé a iniciar sesión"* (`SESION_REVOCADA`: cambio
  de rol, suspensión, baja, blanqueo o cambio de la matriz del rol).
- Topbar: nombre, rol e iniciales del usuario, con "Cambiar contraseña" y "Cerrar sesión".

### Inactividad

Los sondeos (GET) **no** cuentan como actividad: si contaran, la sesión no vencería nunca. El
dashboard escucha interacción real (`pointerdown`, `keydown`, `wheel`, `touchstart`, `scroll`) y
manda `POST /api/auth/actividad` como mucho una vez por minuto; las pestañas comparten esa señal
por `BroadcastChannel`. Un temporizador local con el `inactividadMin` del perfil avisa un minuto
antes y muestra el login al vencer, sin esperar al próximo sondeo (`hooks/useInactividad.ts`).

### Usuarios demo (modo mock)

En `npm run dev:demo` el login lista usuarios demo (contraseña `demo` para todos) con la matriz
de permisos por defecto, así que la demo se restringe igual que el sistema real:

| Usuario | Nombre | Rol |
|---|---|---|
| `admin` | Lucía Fernández | Administrador |
| `agronomo` | Ana Benítez | Ingeniero Agrónomo |
| `productor` | Mariano Duarte | Productor Viverista |
| `operario` | Carlos Ramírez | Operario |

Usuarios, matriz, política y auditoría se gestionan en memoria con las mismas validaciones que el
backend; la sesión también vive en memoria, así que **recargar la página cierra la sesión demo**.
Con `VITE_DATA_SOURCE=http` el login no menciona usuarios demo: el primer ingreso es el
Administrador inicial del backend (ver su README).

### Permisos en la UI

- **Tabla vista → permiso única** (`src/lib/vistas.ts`), compartida por el router
  (`<RequirePermiso>`) y el sidebar. Una vista sin permiso no aparece en el menú y, por URL,
  muestra "No tenés permiso para ver esta sección" **sin pedir datos**. Un rol sin ninguna vista
  ve "Tu rol no tiene secciones habilitadas".
- **Acciones**: `useAuth().puede('<permiso>')` oculta o deshabilita la edición en Configuración
  (`configuracion.editar`; el interruptor de Demo Expo, `demo-expo.configurar`), Motor de reglas
  (`reglas.editar`: Parámetros en sólo lectura, sin guardar), Hardware (`hardware.gestionar`),
  Topología (`topologia.gestionar`) y Demo Expo (`pasadas.operar`: iniciar/cancelar).
- **403 uniforme**: el cliente HTTP convierte un `403` en `PermisoDenegadoError` con el mensaje
  "No tenés permiso para esta acción." y recarga el perfil (la matriz pudo cambiar mientras se
  navegaba).
- **Usuarios** (`/usuarios`, `usuarios.gestionar`): pestañas Usuarios, Roles y permisos,
  Auditoría (además `auditoria.ver`) y Seguridad (tiempo máximo de inactividad, 5–480 min).

Para una vista nueva: agregarla a `VISTAS` con su permiso de lectura y envolver la ruta con
`con(VISTAS.x, <Pagina />)` en `router.tsx`; el sidebar la toma de la misma tabla.

### Capa de datos de la sesión

`SeguridadRepository` (`HttpSeguridadRepository` / `MockSeguridadRepository`) se elige con el
**mismo** `VITE_DATA_SOURCE` que el `DataRepository`: nunca hay una sesión simulada sobre datos
del backend. Todas las llamadas HTTP pasan por `src/data/http/apiFetch.ts`, que manda la cookie
(`credentials: 'include'`) y decodifica `401` (motivo) y `403` (permiso o
`CAMBIO_CLAVE_REQUERIDO`) en errores tipados, avisando al `AuthProvider` por
`src/data/sesionEventos.ts`.

### Mismo esquema y host entre dashboard y API

La sesión es una cookie `HttpOnly; SameSite=Strict` del backend. `localhost:5173` →
`localhost:8000` (o `IP:5173` → `IP:8000`) es *same-site* y la cookie viaja. Pero si el dashboard
se sirve por `http` y `VITE_API_BASE_URL` apunta a `https://…:8443` (o a otro host), el navegador
lo considera otro sitio y **no manda la cookie**: el login responde bien pero la sesión no queda
establecida. El dashboard lo detecta y lo dice ("No se pudo establecer la sesión…"). Dashboard y
API tienen que compartir esquema y host.

## Arquitectura

Feature-based + atomic design. La regla de oro: **la UI nunca sabe de dónde vienen
los datos**, solo consume la capa de datos.

```
src/
├── styles/        tokens.css (design tokens), global.css (reset, fuentes, animaciones)
├── types/         domain.ts — contratos del dominio (Sector, Diagnosis, Metric, ...); seguridad.ts — sesión, usuarios, roles, auditoría
├── lib/           rng.ts — RNG determinístico (mulberry32)
├── data/          ← CAPA DE DATOS (patrón Repository)
│   ├── repository.ts      interface DataRepository
│   ├── seguridadRepository.ts  interface SeguridadRepository (sesión y gestión de usuarios)
│   ├── mock/              MockRepository + generadores (portados del diseño)
│   ├── http/              HttpRepository, HttpSeguridadRepository y apiFetch (cookie + 401/403)
│   ├── selectors.ts       derivaciones puras (detalle de sector)
│   └── index.ts           getRepository() — factory por entorno
├── hooks/         AuthContext (useAuth), useInactividad, NurseryContext (provider + useNurseryData), PageMeta, DemoExpoContext, usePasada, useSectorDetail
├── components/
│   ├── ui/        átomos: Card, Badge, StatusDot, ProgressBar, Sparkline, Icon
│   └── layout/    AppLayout, Sidebar, Topbar, UserMenu, AlertsDropdown, VigilanteInactividad
├── features/      ← una carpeta por vista del producto
│   ├── dashboard/      Panel general
│   ├── map/            Mapa de producción
│   ├── sector/         Detalle de sector
│   ├── diagnostics/    Diagnósticos de IA
│   ├── auth/           Login, cambio de contraseña y guardas de ruta
│   ├── usuarios/       Usuarios, roles y permisos, auditoría y política de sesión
│   ├── demo-expo/      Demo Expo: pasada del riel en vivo (pestaña oculta; se enciende en Configuración → "Mostrar Demo Expo")
│   └── placeholder/    Módulos no incluidos en la demo
├── router.tsx     rutas (react-router)
├── App.tsx        AuthProvider + RouterProvider (el vivero se monta dentro del shell, con sesión)
└── main.tsx       entrypoint
```

### Del mock al backend real

El diseño genera todos los datos en el cliente con un RNG sembrado. Acá eso vive
detrás de `MockRepository`. Cuando el backend exista:

1. Implementás los endpoints (empezando por `GET /nursery`).
2. En `.env`: `VITE_DATA_SOURCE=http` y `VITE_API_BASE_URL=<tu-backend>`.

**No se toca ni un componente.** Esa es la ventaja del patrón Repository.

## Sistema de diseño

Los tokens del diseño viven en `src/styles/tokens.css` como CSS custom properties.
Los estilos estáticos van en CSS Modules; los valores dinámicos (color de un sector,
ancho de una barra) se aplican inline porque dependen de datos en runtime.

## Documentación de cambios

Este frontend se planificó con **OpenSpec** (spec-driven development). Ver el cambio
`openspec/changes/add-monitoring-frontend/` en la raíz del repo: proposal, design,
tasks y specs por capacidad.
