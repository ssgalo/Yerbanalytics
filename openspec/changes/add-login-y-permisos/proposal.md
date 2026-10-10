# Inicio de sesión por rol y gestión de usuarios y permisos

## Why

Hoy la plataforma no sabe quién la está usando. Toda `/api/**` (salvo el contrato de cámara)
es abierta: cualquiera que llegue al puerto 8000 en la LAN del vivero puede editar el catálogo
de umbrales del motor, regenerar la topología, disparar una pasada del riel o revocar la cámara.
La topbar muestra un usuario clavado ("Mariano Duarte · Productor Viverista") que no existe.
Mientras el sistema actúa sobre plantas vivas —riego, insumos, mediasombra— eso es un riesgo
agronómico real, no sólo de seguridad informática.

HU-01 (iniciar sesión bajo un rol) y HU-20 (gestionar roles, permisos y su auditoría) son las
dos historias de R4 que faltan para cerrar el "esquema de acceso seguro" del release. Se hacen
juntas porque ninguna tiene sentido sin la otra: un login sin roles no restringe nada, y una
matriz de permisos sin login no tiene a quién aplicarse.

## What Changes

- **Login con usuario y contraseña** (HU-01 CA-01/CA-02). Pantalla de inicio de sesión fuera del
  shell; al autenticar, redirige al Panel general con sólo las vistas y acciones de su rol. Ante
  cualquier error responde un único mensaje genérico ("Credenciales incorrectas"), sin distinguir
  usuario inexistente, contraseña errónea, cuenta suspendida o dada de baja.
- **Sesiones del lado del servidor con cierre por inactividad** (HU-01 CA-03). El tiempo máximo
  de inactividad lo edita el Administrador (60 min por defecto). El sondeo automático del
  dashboard **no** cuenta como actividad: sólo la interacción real del usuario.
- **Cinco roles fijos**: Administrador, Ingeniero Agrónomo, Productor Viverista, Operario y
  **Servicio** (rol técnico para integraciones —simulador, futuro servicio de inferencia—, no
  pensado para personas).
- **Matriz de permisos editable por el Administrador** (HU-20 CA-01). Los permisos son un catálogo
  fijo en código (p. ej. `reglas.editar`, `topologia.gestionar`); qué permisos tiene cada rol se
  persiste y se edita desde la UI. Arranca con una matriz por defecto derivada de quién es dueño
  de cada HU. Salvaguardas contra quedarse afuera: el Administrador no puede perder la gestión
  de usuarios, ni puede quedar el sistema sin un Administrador activo.
- **Gestión de usuarios** (HU-20 CA-01/CA-02): alta, edición, cambio de rol, suspensión
  (reversible), baja (definitiva, lógica) y blanqueo de contraseña. Cualquier cambio de rol,
  suspensión o baja **invalida en el acto** las sesiones vigentes del usuario afectado; editar la
  matriz de un rol invalida las de quienes tienen ese rol.
- **Registro de auditoría inalterable** (HU-20 CA-03): toda alta, baja o modificación sobre
  usuarios, roles, permisos o el tiempo de inactividad queda asentada con quién, sobre quién, qué
  cambió y el timestamp. Append-only a nivel aplicación y base, con encadenamiento por hash para
  que una alteración por fuera del sistema sea detectable.
- **Cambio de contraseña propio** y contraseña temporal obligatoria de cambiar en el primer
  ingreso (las asigna el Administrador al dar de alta o blanquear).
- **Administrador inicial**: si no hay ningún Administrador activo, el backend crea uno al
  arrancar con credenciales tomadas de la configuración.
- **BREAKING (API)**: toda `/api/**` que no sea el contrato de cámara (`/api/camara/v1/**`),
  el login y `/ca.pem` pasa a exigir sesión de usuario y el permiso correspondiente. Respuestas
  nuevas: `401` (sin sesión, vencida o revocada, con motivo) y `403` (sin permiso).
- **BREAKING (simulador)**: el simulador deja de llamar al backend de forma anónima; su servidor
  inicia sesión con una cuenta de rol Servicio configurada en su propio `.env`. El backend sigue
  sin conocerlo: para él es un usuario más.
- **Frontend**: guardas de ruta por permiso, sidebar filtrado, acciones de edición ocultas o
  deshabilitadas sin permiso, topbar con el usuario real y "Cerrar sesión", nueva vista
  **Usuarios** (usuarios · roles y permisos · auditoría · seguridad).
- **Modo demo** (`VITE_DATA_SOURCE=mock`): login simulado con un usuario demo por rol, para
  mostrar cómo cambia la UI según el perfil; la gestión funciona en memoria.

## Capabilities

### New Capabilities

- `autenticacion-usuarios`: inicio y cierre de sesión, mensaje de error genérico, sesión del
  lado del servidor, cierre por inactividad configurable, cambio de contraseña (incluido el
  obligatorio del primer ingreso) y creación del Administrador inicial.
- `control-acceso-roles`: roles fijos, catálogo de permisos, matriz rol→permisos editable con sus
  salvaguardas, y su aplicación en el backend (por endpoint) y en el dashboard (rutas, menú y
  acciones).
- `gestion-usuarios`: ABM de usuarios por el Administrador, estados (activo, suspendido, baja),
  blanqueo de contraseña e invalidación de sesiones ante cambios de rol, permisos o estado.
- `auditoria-seguridad`: registro append-only y verificable de los cambios sobre usuarios, roles,
  permisos y política de sesión, y su consulta por el Administrador.

### Modified Capabilities

- `app-shell`: el sidebar muestra sólo las vistas permitidas e incorpora "Usuarios"; la topbar
  muestra el usuario autenticado y su rol, con cierre de sesión y cambio de contraseña.
- `data-layer`: la sesión y la gestión de usuarios también se acceden por repositorio y quedan
  bajo el mismo `VITE_DATA_SOURCE`; el repositorio HTTP envía la sesión y trata `401`/`403` de
  forma uniforme; el mock incluye usuarios demo por rol.
- `camara-dispositivos`: el escenario "el resto de la API sigue abierta" deja de valer; la API de
  plataforma de cámara y captura (vinculación, dispositivos, órdenes, imágenes) exige sesión de
  usuario con permiso. El contrato `/api/camara/v1/**` y su token de dispositivo no cambian.
- `simulacion-captura`: el simulador se autentica ante el backend con una cuenta de servicio
  configurada en su propio entorno, sin que el backend tenga rama alguna para él.

## Impact

- **Backend**: nueva dependencia `spring-boot-starter-security` (reemplaza el filtro artesanal
  `CamaraAuthFilter`, que ya anticipaba "cuando llegue HU-01 habrá que unificar"). Entidades
  nuevas: `usuario`, `sesion`, `rol_permiso`, `auditoria_seguridad`, y un parámetro de política
  de sesión. Controllers nuevos `/api/auth/**`, `/api/usuarios/**`, `/api/roles/**`,
  `/api/auditoria`. Todos los controllers existentes quedan detrás de un permiso. CORS sigue con
  credenciales (ya `allowCredentials(true)`). Trigger de PostgreSQL contra `UPDATE`/`DELETE` en
  la auditoría, instalado al arrancar.
- **Frontend**: `AuthContext`, página `/login`, guardas de ruta, detector de inactividad,
  `SeguridadRepository` (mock + http), vista `features/usuarios/`, cambios en `Sidebar`,
  `Topbar`, `router.tsx`, `httpRepository.ts` (`credentials: 'include'` y manejo de 401) y en
  las vistas con acciones de edición (Configuración, Motor de reglas, Hardware, Topología,
  Demo Expo).
- **Simulador**: su servidor inicia sesión y adjunta la cookie de sesión en el proxy
  `/backend/**`; nuevas variables en `env.example`.
- **Sin cambios**: el contrato de cámara (`/api/camara/v1/**`, OpenAPI v1, suite de
  conformidad), la PWA y la app Android, el firmware y el contrato MQTT. El ESP32 no habla HTTP.
- **Operación**: en un clon nuevo hay que fijar la contraseña del Administrador inicial; datos
  existentes no se migran (no hay usuarios previos).
- **Docs**: `CLAUDE.md` (§6 convenciones, §6.1, §6.2), README del backend, del frontend y del
  simulador.
