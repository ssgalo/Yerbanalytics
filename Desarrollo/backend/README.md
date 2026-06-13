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
- `config/`: Clases de configuración global (CORS, beans, etc.).
- `dto/`: Objetos de Transferencia de Datos (próximamente). Mantendrán la paridad con la interfaz `domain.ts` del frontend.
- `service/`: Lógica de negocio principal (próximamente).

## Endpoints Iniciales

- `GET /api/nursery`: Endpoint base. Actualmente devuelve un JSON de validación de estado. A futuro, este será el endpoint que devuelva el "snapshot" completo del vivero para poblar el dashboard del frontend.