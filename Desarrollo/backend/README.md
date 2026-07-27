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

## Base de datos

El esquema lo genera Hibernate (`ddl-auto=update`) y se siembra con
`src/main/resources/data.sql` (600 sectores, 6 macro-zonas, umbrales de fábrica).

### Migración manual pendiente · sensado por macro-zona

Las lecturas sensadas se mudaron del sector a la macro-zona (un solo nodo testigo por MZ).
**`ddl-auto=update` no cubre ese cambio**: agrega las columnas nuevas en `zona`, pero no
copia los datos, no borra las columnas viejas de `sector` y no corrige umbrales ya
sembrados (`data.sql` usa `ON CONFLICT DO NOTHING`).

Si tu base es descartable, borrala y dejá que `data.sql` la siembre de cero. Si querés
conservar el estado, corré `src/main/resources/migracion-manual.sql` en este orden:

1. Arrancar la app una vez → crea las columnas nuevas en `zona`.
2. Detener la app.
3. Ejecutar `migracion-manual.sql`.
4. Volver a arrancar.

El script traslada las lecturas, recupera el estado de los nodos desde el registro de
hardware, convierte `ce` a dS/m y re-escala los umbrales de `uv`. Los `DROP COLUMN` quedan
comentados a propósito: descomentalos recién después de verificar el resultado.

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