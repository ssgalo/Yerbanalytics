# Yerbanalytics · Backend

API REST para el sistema de monitoreo inteligente de plantines de yerba mate. Desarrollado en Java con Spring Boot.

## Requisitos

- Java 17 (LTS)
- Maven 3.8+

## Puesta en marcha

Para levantar el servidor en tu entorno local, ejecutá:

```bash
cd Desarrollo/backend
mvn spring-boot:run
```

La API quedará escuchando en `http://localhost:8000`.

## Configuración

La configuración principal se encuentra en `src/main/resources/application.properties`.
- El puerto está fijado en `8000` para coincidir con la URL esperada por el frontend (`VITE_API_BASE_URL`).
- El proyecto cuenta con una configuración global de CORS en la carpeta `config/` para permitir peticiones entrantes desde el entorno de desarrollo del frontend (Vite en `http://localhost:5173`).

## Arquitectura

El proyecto sigue el patrón multicapa clásico de Spring Boot:

- `controller/`: Controladores REST. Definen los endpoints, rutas y manejan las peticiones HTTP.
- `config/`: Clases de configuración global (CORS, propiedades, beans).
- `dto/`: Objetos de Transferencia de Datos. Mantienen paridad con `domain.ts` del frontend.
- `service/`: Lógica de negocio. `NurseryService` genera el snapshot del vivero.
- `service/mock/`: Generador determinístico portado del mock del frontend.

## Endpoints

- `GET /api/nursery`: Devuelve el snapshot completo del vivero (`NurseryData`). Hoy usa un generador determinístico con semilla configurable (`yerbanalytics.mock.seed`, default `20260613`).

## Integración con el frontend

| `VITE_DATA_SOURCE` | Origen de datos |
|---|---|
| `mock` (default) | `MockRepository` en el frontend |
| `http` | `GET /api/nursery` en este backend |

Para probar la integración:

```bash
# Terminal 1 — backend
cd Desarrollo/backend
mvn spring-boot:run

# Terminal 2 — frontend
cd Desarrollo/frontend
# En .env: VITE_DATA_SOURCE=http y VITE_API_BASE_URL=http://localhost:8000/api
npm run dev
```