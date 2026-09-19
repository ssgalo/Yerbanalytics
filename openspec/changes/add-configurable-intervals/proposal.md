## Why

Actualmente, los intervalos de sensado (reporte de los nodos IoT o el simulador) y de ejecución del modelo de inferencia de IA están fijos en el código (vía variables de entorno o constantes). El objetivo de este cambio es añadir estos intervalos a la configuración central del sistema, permitiendo que el usuario pueda ajustarlos dinámicamente desde la interfaz web de "Configuración". Esto soluciona la rigidez del sistema y permite adaptar la frecuencia operativa sin reiniciar servicios ni modificar código fuente.

## What Changes

- **Backend (Configuración)**: Se agregan `intervaloSensadoMinutos` e `intervaloInferenciaMinutos` a `ConfiguracionOperativaEntity`.
- **Frontend (UI)**: El formulario de configuración incluye controles para estos intervalos, permitiendo al usuario ingresar el valor y elegir la unidad (Horas o Minutos) dinámicamente.
- **Backend (Simulador)**: El `simulador/server/emission.ts` se actualiza para leer dinámicamente la configuración y ajustar su `setInterval` de sensado. Al no usarse deep sleep en el hardware final, el simulador es el principal consumidor de este parámetro en el prototipo.
- **Backend (Inferencia)**: El servicio en Python (`servicio-inferencia/src/main.py`) deja de depender exclusivamente de `POLLING_INTERVAL_SECONDS` y comienza a consultar la API de configuración al finalizar cada ciclo para determinar cuánto tiempo debe pausar.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `configuracion-agronomica`: El módulo de configuración ahora soporta y persiste parámetros de tiempo para la telemetría y la inferencia.
- `simulacion-ingesta`: El simulador ajusta su emisión periódica basado en la configuración viva del sistema en lugar de usar una constante interna.
- `ai-diagnostics`: El orquestador de inferencia ajusta su polling consultando la API de configuración.

## Impact

- **API/BD**: Migración de la tabla `configuracion_operativa` agregando dos columnas enteras.
- **Python**: Llamada HTTP adicional en cada ciclo.
- **Frontend**: Conversión de unidades (minutos ↔ horas) manejada en el componente de UI.
