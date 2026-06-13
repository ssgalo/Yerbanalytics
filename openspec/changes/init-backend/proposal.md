# Change: init-backend

## Why
Actualmente el frontend de Yerbanalytics consume un mock determinístico (`MockRepository`) para renderizar el panel de control. Para avanzar hacia un producto real, necesitamos un backend genuino que centralice la lógica de negocio, exponga una API REST para el frontend, y en el futuro, se comunique con la base de datos, el hardware IoT y el modelo de IA.

## What Changes
- **Scaffold del Backend:** Inicialización de un proyecto en Java con Spring Boot en la carpeta `Desarrollo/backend/`.
- **Configuración base:** Puerto `8000` y prefijo de API `/api`, para alinearse con la variable `VITE_API_BASE_URL` esperada por el frontend.
- **CORS:** Configuración para permitir peticiones desde `http://localhost:5173` (Frontend Vite).
- **Endpoint Inicial:** Creación del contrato base `GET /api/nursery` que por ahora devolverá una estructura mínima, sirviendo de base para reemplazar el repositorio mock del frontend.

## Impact
- **Affected specs:** `backend-core`, `api-nursery` (nuevas).
- **Affected code:** `Desarrollo/backend/` (inicialización del proyecto).
- **Frontend:** Una vez que el endpoint `/api/nursery` devuelva la estructura completa, se podrá cambiar `VITE_DATA_SOURCE=http` en el frontend para probar la integración real.

## Non-Goals
- En este cambio no se conectará a ninguna base de datos (PostgreSQL/MongoDB).
- No se implementará la lógica de negocio completa ni la integración con la IA. Es puramente la inicialización de la arquitectura.