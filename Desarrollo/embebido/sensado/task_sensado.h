// ============================================================================
//  task_sensado.h  -  Tarea FreeRTOS de muestreo periódico
// ----------------------------------------------------------------------------
//  Cada SENSOR_POLLING_INTERVAL_MS lee los sensores (luz, ambiente, suelo y
//  nivel de los tanques), arma el paquete de
//  telemetría (con battery/signal/timestamp) y lo entrega a cola_telemetria.
//  Entre ciclos cede el núcleo con vTaskDelay (ahorro de energía sin deep sleep).
// ============================================================================
#ifndef TASK_SENSADO_H
#define TASK_SENSADO_H

#include "tipos.h"

namespace task_sensado {

// Inicializa los drivers de sensores. Llamar en setup() antes de crear la tarea.
void iniciarDrivers();

// Pide una lectura inmediata (comando de zona LEER_AHORA). Seguro de llamar desde el callback MQTT:
// sólo notifica a la tarea de sensado, que lee y entrega por la cola de siempre. Sin efecto si la
// tarea todavía no arrancó. Varios pedidos seguidos se funden en una lectura. Después de la lectura
// a pedido el período normal arranca de nuevo.
void pedirLecturaAhora();

// Punto de entrada de la tarea FreeRTOS. pvParameters = SistemaColas*.
void tarea(void* pvParameters);

}  // namespace task_sensado

#endif  // TASK_SENSADO_H
