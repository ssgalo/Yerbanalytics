# Design: init-backend

## Context
El frontend espera interactuar con un backend a través de una API REST. Según la documentación actual, el endpoint principal para poblar el dashboard es `GET {VITE_API_BASE_URL}/nursery`.

## Decisions

### 1. Stack Tecnológico
- **Lenguaje:** Java 17 (versión LTS estable y ampliamente adoptada).
- **Framework:** Spring Boot 3.x. Proporciona autoconfiguración, servidor embebido (Tomcat) y un ecosistema robusto para APIs REST.
- **Gestor de dependencias:** Maven.
- **Librerías iniciales:** `spring-boot-starter-web` (para REST y Tomcat) y `lombok` (para reducir boilerplate de getters/setters/constructores).

### 2. Arquitectura Base
Patrón multicapa clásico de Spring:
- `controller/`: Controladores REST, manejan HTTP, rutas y validaciones de entrada.
- `service/`: Lógica de negocio (a implementar a futuro).
- `dto/`: Objetos de Transferencia de Datos para mantener el contrato estricto con el frontend.

### 3. Configuración del Servidor
- `server.port=8000`: Para coincidir con la URL por defecto del frontend (`http://localhost:8000/api`).
- Se aplicará un filtro o configuración `@CrossOrigin` global para permitir peticiones desde Vite (`http://localhost:5173`).

### 4. Contrato Inicial
El controlador `NurseryController` expondrá `GET /api/nursery`. Inicialmente, para validar la conexión de red, devolverá un JSON básico que deberá evolucionar hasta igualar la interfaz `NurseryData` (`frontend/src/types/domain.ts`).

## Risks / Trade-offs
- **Riesgo:** Desincronización de contratos entre Frontend (`domain.ts`) y Backend (`DTOs`).
- **Mitigación:** Documentar estrictamente la forma del JSON esperado. A futuro, se podría evaluar usar herramientas como OpenAPI/Swagger.