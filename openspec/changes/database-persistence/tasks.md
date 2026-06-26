# Tasks: database-persistence

## Checklist de Implementación

- [ ] Configurar el servicio `yerbanalytics-db` (puerto 5432) en `docker-compose.yml`.
- [ ] Agregar las dependencias de JPA y PostgreSQL en `pom.xml`.
- [ ] Configurar los parámetros del data source y de Hibernate en `application.properties`.
- [ ] Crear las entidades JPA `ZonaEntity` y `SectorEntity` en un nuevo paquete `com.yerbanalytics.backend.model`.
- [ ] Crear los repositorios JPA `ZonaRepository` y `SectorRepository` en `com.yerbanalytics.backend.repository`.
- [ ] Implementar la inicialización en base de datos (`DatabaseSeeder`) usando la lógica de `NurseryGenerator` si las tablas están vacías.
- [ ] Refactorizar el receptor MQTT (`MqttTelemetryReceiver`) para delegar la actualización a un método persistente.
- [ ] Modificar `NurseryService` para escribir la telemetría en la base de datos de forma transaccional y recuperar el snapshot desde PostgreSQL.
- [ ] Realizar pruebas locales de extremo a extremo, levantando Docker y validando la persistencia de los cambios de telemetría.
