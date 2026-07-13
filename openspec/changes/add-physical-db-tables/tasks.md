---
id: add-physical-db-tables
title: "Persistencia física y generación de DER"
status: done
---

# Tareas Realizadas

- `[x]` Desvincular entidades de simulación (`SensorSimuladoEntity`, `ModoOperacionEntity`) de JPA/Hibernate, manejándolas puramente en memoria en el backend para que no generen tablas productivas innecesarias.
- `[x]` Eliminar inserciones de simulación (`modo_operacion`) del script base `data.sql`.
- `[x]` Crear script `schema.sql` explícito con el modelo de datos productivo completo (`zona`, `sector`, `dispositivo`, `historial_evento`, `configuracion_operativa`, `umbral_metrica`, `rustificacion_etapa`, `topologia_layout`).
- `[x]` Generar diagrama Entidad-Relación (DER) de la base de datos en `context.md` para visualizar el modelo final.
