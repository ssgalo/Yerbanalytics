// ============================================================================
//  act_riel.h  -  Riel de la cámara (NEMA 17 + DRV8825) por MQTT
// ----------------------------------------------------------------------------
//  Implementa el contrato nursery/rail/* (contrato.h, sección "Riel"): IR_A a la
//  posición 1 o 2, HOME contra el final de carrera, eventos ACEPTADO / LLEGO /
//  ERROR. Portado del sketch del prototipo vivero_esp32_red (retirado; queda
//  en el historial de git): misma lógica de movimiento, homing e idempotencia.
//
//  Corre en su propia tarea (TASK_PRIO_RIEL, la más baja): los pasos se generan
//  con espera activa y el resto de las tareas la interrumpe cuando la necesita
//  (un paso más largo no hace perder pasos). Cada RIEL_PASOS_ENTRE_CHEQUEOS cede
//  el núcleo un tick y mira si llegó otro comando: el último gana.
//
//  Entrada: sys->comandoRiel (largo 1, lo llena el callback MQTT con xQueueOverwrite).
//  Salida:  sys->eventoRiel (lo publica la tarea de red).
// ============================================================================
#ifndef ACT_RIEL_H
#define ACT_RIEL_H

#include "tipos.h"

namespace act_riel {

// Pines del driver y de los finales de carrera; deja el driver dormido.
void iniciar();

// Punto de entrada de la tarea FreeRTOS. pvParameters = SistemaColas*.
// Hace el homing de arranque y después atiende los comandos.
void tarea(void* pvParameters);

}  // namespace act_riel

#endif  // ACT_RIEL_H
