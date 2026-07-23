// ============================================================================
//  task_actuacion.h  -  Tarea FreeRTOS ejecutora de comandos
// ----------------------------------------------------------------------------
//  Consume cola_comandos, deduplica por commandId, despacha al actuador
//  correspondiente y empuja el Ack (SUCCESS/ERROR) a cola_ack.
// ============================================================================
#ifndef TASK_ACTUACION_H
#define TASK_ACTUACION_H

#include "tipos.h"

namespace task_actuacion {

// Inicializa los drivers de actuadores. Llamar en setup() antes de crear la tarea.
void iniciarDrivers();

// Punto de entrada de la tarea FreeRTOS. pvParameters = SistemaColas*.
void tarea(void* pvParameters);

}  // namespace task_actuacion

#endif  // TASK_ACTUACION_H
