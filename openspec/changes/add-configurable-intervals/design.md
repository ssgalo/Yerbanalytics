## Context

El sistema tiene dos procesos asíncronos clave que actualmente funcionan con intervalos fijos o hardcodeados:
1. **Sensado (Telemetría)**: El simulador integrado reporta datos cada 5 segundos (o según constante interna), lo que puede saturar la BD o no reflejar la realidad operativa (donde el sensado ocurriría cada X minutos u horas).
2. **Inferencia (IA)**: El servicio Python consulta la BD buscando nuevas capturas en base a un `POLLING_INTERVAL_SECONDS` inyectado por el entorno (usualmente 4 horas).

El usuario necesita modificar ambos parámetros desde la pantalla de "Configuración", con la flexibilidad de elegir la unidad (Horas o Minutos) en la interfaz gráfica. Dado que el hardware físico final no usará *Deep Sleep*, el simulador será el consumidor principal del parámetro de sensado.

## Goals / Non-Goals

**Goals:**
- Extender `ConfiguracionOperativaEntity` y su DTO para almacenar `intervaloSensadoMinutos` e `intervaloInferenciaMinutos`.
- Construir un control en la UI que agrupe un input numérico y un selector de unidad (Minutos/Horas) y convierta este par al valor real en minutos antes de enviar el POST al backend (y viceversa al cargar).
- Adaptar el servicio de inferencia (`servicio-inferencia`) para consumir `/api/configuracion` y dormir dinámicamente.
- Adaptar el simulador para que consuma esta configuración del backend y modifique su `setInterval`.

**Non-Goals:**
- Implementar envío de comandos MQTT (Downlink) al hardware físico para alterar su Deep Sleep, ya que se descartó el uso de Deep Sleep en el hardware.
- Implementar notificaciones Push o WebSockets cuando cambia la configuración; el simulador y el modelo de IA utilizarán técnicas de polling (o el simulador consultará periódicamente la topología/configuración).

## Decisions

1. **Almacenamiento en Minutos:**
   La BD y la API almacenarán y transferirán el tiempo estrictamente en *minutos* (Entero). Esto mantiene el backend agnóstico a la unidad de visualización y evita manejar fracciones. La UI se encargará de dividir/multiplicar por 60 según lo que seleccione el usuario.

2. **UI: Selector Mixto:**
   Se usará un input `number` combinado con un `<select>` nativo para "Minutos" / "Horas". Al guardar, el formulario calculará `valor * (unidad === 'horas' ? 60 : 1)`.

3. **Inferencia Python (Fallback):**
   Si la consulta a `/api/configuracion` falla (por ejemplo, el backend no está disponible temporalmente), el script de Python atrapará el error y utilizará su variable de entorno `POLLING_INTERVAL_SECONDS` como tiempo de espera seguro (fallback), evitando que el bucle falle o se ejecute sin pausas.

4. **Simulador (Actualización Reactiva):**
   El simulador ya consulta `/api/topologia` frecuentemente. Se puede modificar esa llamada o agregar una consulta a `/api/configuracion` en su ciclo para verificar si el `intervaloSensadoMinutos` cambió, y en ese caso, hacer un `clearInterval` y recrear el `setInterval` con el nuevo tiempo.

## Risks / Trade-offs

- **Risk**: El simulador podría sobrecargar el backend si consulta la configuración demasiado rápido cuando el intervalo configurado es muy pequeño.
  - **Mitigation**: Mantener un caché en el proxy del simulador o limitar la frecuencia mínima permitida en la UI (ej. mínimo 1 minuto).
- **Risk**: Si el usuario configura la inferencia a "0 minutos", el bucle infinito podría causar 100% uso de CPU.
  - **Mitigation**: El backend y Python deben imponer un límite inferior estricto (ej. mínimo 1 minuto) para la inferencia y el sensado.
