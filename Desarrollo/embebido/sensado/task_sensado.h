// ============================================================================
//  task_sensado.h  -  Tarea FreeRTOS de muestreo periódico
// ----------------------------------------------------------------------------
//  Lee los sensores (luz, ambiente, suelo y nivel de los tanques), arma el paquete de
//  telemetría (con battery/signal/timestamp) y lo entrega a cola_telemetria.
//  Backend-driven sensing (pure pull): reads once at boot and afterwards ONLY on a
//  LEER_AHORA (the backend publishes it every X s). There is no own timer: between reads the
//  task waits with no timeout (ulTaskNotifyTake with portMAX_DELAY).
// ============================================================================
#ifndef TASK_SENSADO_H
#define TASK_SENSADO_H

#include "tipos.h"

namespace task_sensado {

// Inicializa los drivers de sensores. Llamar en setup() antes de crear la tarea.
void iniciarDrivers();

// Pide una lectura inmediata (comando de zona LEER_AHORA). Seguro de llamar desde el callback MQTT:
// sólo notifica a la tarea de sensado, que lee y entrega por la cola de siempre. Sin efecto si la
// tarea todavía no arrancó. Varios pedidos seguidos se funden en una lectura. There is no own
// period to re-arm: the task goes back to waiting for the next request.
void pedirLecturaAhora();

// Punto de entrada de la tarea FreeRTOS. pvParameters = SistemaColas*.
void tarea(void* pvParameters);

}  // namespace task_sensado

#endif  // TASK_SENSADO_H
