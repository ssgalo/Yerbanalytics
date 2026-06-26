# Guía de Limpieza: Componentes Temporales y Mocks de Desarrollo

Este archivo contiene la lista de dependencias, archivos, configuraciones y lógicas simuladas que han sido creados temporalmente para facilitar el desarrollo y pruebas locales. Deberán ser eliminados o reemplazados antes del despliegue en producción.

---

## 1. Dependencias y Herramientas de Desarrollo

### 🛠️ Spring Boot DevTools (`pom.xml`)
* **Ubicación:** `Desarrollo/backend/pom.xml`
* **Propósito:** Permite el reinicio automático del servidor al modificar configuraciones (como `application.properties`) o código Java.
* **Acción para producción:** Aunque está configurado con `<optional>true</optional>` y `<scope>runtime</scope>` (lo cual evita que se empaquete en el JAR de producción), se recomienda removerlo del archivo POM si se desea un build 100% limpio.

---

## 2. Simulación de Telemetría (MQTT ESP32)

### 📡 Simulador de Nodos (`MqttTelemetrySimulator.java`)
* **Ubicación:** `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/mqtt/MqttTelemetrySimulator.java`
* **Propósito:** Simula de forma periódica el envío de mensajes MQTT con mediciones de sensores ficticias para las 6 macrozonas.
* **Acción para producción:** **Eliminar por completo**. En producción, los datos vendrán exclusivamente de los sensores reales ESP32 distribuidos en el vivero físico.
* **Configuraciones asociadas:** Eliminar en `application.properties` las siguientes líneas:
  ```properties
  yerbanalytics.mqtt.simulator.enabled=true
  yerbanalytics.mqtt.simulator.client-id=yerbanalytics-esp32-simulator
  yerbanalytics.mqtt.simulator.interval-ms=10000
  ```

---

## 3. Datos de Prueba e Inicialización (PostgreSQL)

### 🌱 Inicialización Semilla (`data.sql`)
* **Ubicación:** `Desarrollo/backend/src/main/resources/data.sql`
* **Propósito:** Popula la base de datos vacía en el primer arranque con los 600 sectores en estado `offline`.
* **Acción para producción:** **Eliminar o migrar**. En producción, el esquema y los datos estructurales deben gestionarse mediante herramientas de migración de base de datos como **Flyway** o **Liquibase**, y la inicialización de los sectores reales debe ser configurada por el administrador según el layout físico real del vivero.
* **Configuraciones asociadas:** Desactivar o quitar en `application.properties`:
  ```properties
  spring.sql.init.mode=always
  spring.jpa.defer-datasource-initialization=true
  ```

---

## 4. Contenido Estático Simulado (Backend)

### 📋 Historial de Acciones y Alertas de Prueba
* **Ubicación:** `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/constant/NurseryConstants.java`
* **Propósito:** Las variables `ACT_TPL` (Templates de Acciones) y la lista de alertas fijas en `NurseryService.java` devuelven un historial simulado de riego, insumos y apertura de mediasombra.
* **Acción para producción:** Reemplazar por consultas a tablas físicas en PostgreSQL (por ejemplo, tablas `historial_acciones` y `alertas_sistema`) para registrar y leer eventos reales en lugar de maquetas fijas en memoria.

---

## 5. Entorno Local de Contenedores

### 🐳 Broker y DB locales (`docker-compose.yml` y scripts)
* **Ubicación:** `docker-compose.yml`, `start-all.bat` y `start-all.ps1` en la raíz.
* **Propósito:** Levanta localmente Mosquitto (puerto 1883) y PostgreSQL (puerto 5432).
* **Acción para producción:** En producción, se debe apuntar el backend a servicios cloud persistentes (ej. AWS RDS o Azure Database para PostgreSQL) y a un broker MQTT industrial (ej. AWS IoT Core, EMQX Cloud o HiveMQ), en lugar de correrlos localmente en contenedores efímeros de Docker Compose.

---

## 6. Mocks en el Frontend

### 💻 Modo de Datos Mock (`VITE_DATA_SOURCE`)
* **Ubicación:** `Desarrollo/frontend/.env` y `Desarrollo/frontend/src/data/mock/`
* **Propósito:** Permite que el frontend funcione de forma aislada sin levantar el backend de Java.
* **Acción para producción:** Configurar `VITE_DATA_SOURCE=http` de forma definitiva y remover la carpeta `src/data/mock/` para reducir el peso del bundle final de la UI.
