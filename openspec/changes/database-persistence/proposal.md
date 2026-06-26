# Change: database-persistence

## Why
El sistema actualmente depende de un generador determinístico en memoria (`NurseryGenerator`) para simular la estructura física del vivero (las 6 macrozonas y 600 sectores) y su estado inicial. Esto presenta las siguientes limitaciones:
1. El estado no es persistente; reiniciar el servidor borra cualquier cambio en tiempo real y reinicia todo a partir de la semilla.
2. No podemos registrar un historial persistente de alertas o telemetría.
3. Queremos desacoplar la lógica de telemetría y actuadores del generador de mocks.

Para solucionar esto, migraremos el backend a una base de datos relacional PostgreSQL real persistente que servirá como el único origen de verdad del vivero.

## What Changes
- **Docker Compose**: Se añade el servicio de base de datos PostgreSQL (`postgres:17-alpine`) escuchando en el puerto local `5433` para evitar colisiones con otros puertos locales.
- **Backend Dependencies**: Se añaden `spring-boot-starter-data-jpa` y `postgresql` en `pom.xml`.
- **Configuración**: Parámetros de conexión a la base de datos en `application.properties` con generación automática de esquema DDL (`update`).
- **Modelo**: Entidades JPA (`ZonaEntity` y `SectorEntity`) y repositorios JPA correspondientes para mapear las macrozonas y los sectores.
- **Seeding**: Un componente `DatabaseSeeder` que inicializa los 600 sectores con valores iniciales estables y balanceados cuando la base de datos está vacía.
- **Servicios**: Refactorización de `NurseryService` para que consulte la base de datos al solicitar el snapshot (`getSnapshot()`) y actualice las entidades de forma persistente y transaccional cuando llegue telemetría por MQTT.

## Impact
- **Affected specs:** `backend-core`, `database-schema`.
- **Affected code:** `pom.xml`, `application.properties`, `NurseryService.java`, `MqttTelemetryReceiver.java`. Se elimina la dependencia en vivo de `NurseryGenerator` (se relega únicamente a la inicialización).
- **Frontend:** Ninguno (el frontend sigue consumiendo `GET /api/nursery` de forma transparente).

## Non-Goals
- No se implementa historial histórico de telemetría en esta fase (solo se guarda la última lectura de cada sensor en el sector correspondiente).
- No se migra el control de actuadores del vivero (permanecerán como campos de estado en el sector).
