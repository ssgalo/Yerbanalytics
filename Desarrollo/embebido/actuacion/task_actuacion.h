// ============================================================================
//  task_actuacion.h  -  Tarea FreeRTOS ejecutora de comandos
// ----------------------------------------------------------------------------
//  Consume cola_comandos, deduplica por commandId y despacha al actuador
//  (riego/insumo o mediasombra). Los actuadores no bloquean: la tarea los
//  atiende cada CICLO_MS y empuja a cola_ack el ack de lo que termina.
//  El riel tiene su propia tarea (act_riel), que se crea aparte.
//  Además informa por sys->error los errores del nodo (S-05): hoy, la falla del final de carrera de la mediasombra.
// ============================================================================
#ifndef TASK_ACTUACION_H
#define TASK_ACTUACION_H

#include "config.h"
#include "tipos.h"

namespace task_actuacion {

// Inicializa los drivers de actuadores (incluido el riel). Llamar en setup()
// antes de crear las tareas.
void iniciarDrivers();

// Punto de entrada de la tarea FreeRTOS. pvParameters = SistemaColas*.
void tarea(void* pvParameters);

#if ENVIAR_ERRORES_CAUDAL
// Punto de integración del caudalímetro (S-05): encola el error SIN_CAUDAL o CAUDAL_EXCESIVO del sector (1 o 2) con el caudal
// medido en L/h. El hardware NO está instalado en el vivero: hasta la versión final nadie lo llama (ver config.h).
// El nodo debe haber cortado el actuador antes de informar.
void informarErrorCaudal(SistemaColas* sys, int sector, bool excesivo, float caudalLph);
#endif

}  // namespace task_actuacion

#endif  // TASK_ACTUACION_H
