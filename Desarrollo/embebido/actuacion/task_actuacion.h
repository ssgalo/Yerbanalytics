// ============================================================================
//  task_actuacion.h  -  Tarea FreeRTOS ejecutora de comandos
// ----------------------------------------------------------------------------
//  Consume cola_comandos, deduplica por commandId y despacha al actuador
//  (riego/insumo o mediasombra). Los actuadores no bloquean: la tarea los
//  atiende cada CICLO_MS y empuja a cola_ack el ack de lo que termina.
//  El riel tiene su propia tarea (act_riel), que se crea aparte.
// ============================================================================
#ifndef TASK_ACTUACION_H
#define TASK_ACTUACION_H

#include "tipos.h"

namespace task_actuacion {

// Inicializa los drivers de actuadores (incluido el riel). Llamar en setup()
// antes de crear las tareas.
void iniciarDrivers();

// Punto de entrada de la tarea FreeRTOS. pvParameters = SistemaColas*.
void tarea(void* pvParameters);

}  // namespace task_actuacion

#endif  // TASK_ACTUACION_H
