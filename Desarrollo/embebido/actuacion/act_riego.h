// ============================================================================
//  act_riego.h  -  Riego e insumo por relés (2 electroválvulas + 2 minibombas)
// ----------------------------------------------------------------------------
//  Hardware (módulo de 4 relés):
//    - válvula del sector 1 y del sector 2 (cada una abre el aspersor de su sector)
//    - bomba de riego (agua) y bomba de insumo (fertilizante/pesticida), compartidas
//
//  Cada sector tiene dos temporizadores independientes: riego e insumo. De ahí
//  sale el estado de los relés:
//    - la válvula N está abierta si el sector N tiene riego o insumo en curso;
//    - la bomba de riego prende si algún sector riega y su válvula ya lleva
//      RIEGO_VALVULA_ANTES_DE_BOMBA_MS abierta (ídem la de insumo);
//    - al cambiar, primero se apagan bombas, después se mueven las válvulas y
//      por último se prenden bombas: nunca se bombea contra una válvula cerrada.
//
//  No bloquea: comenzar() arranca y atender() (llamado seguido desde
//  task_actuacion) aplica los relés y cierra lo vencido, devolviendo su ack.
// ============================================================================
#ifndef ACT_RIEGO_H
#define ACT_RIEGO_H

#include "tipos.h"

namespace act_riego {

void iniciar();

// Ejecuta un comando de válvula (riego) o bomba (insumo) del sector cmd.sector.
// ON arranca (y si el sector ya tenía uno en curso, el viejo termina con ack
// ERROR "reemplazado", que sale por atender()); OFF corta.
// Devuelve true si 'ack' ya tiene la respuesta (error de validación u OFF);
// false si quedó en curso y su ack saldrá por atender() al terminar.
bool comenzar(const Comando& cmd, Ack& ack);

// Aplica los relés y cierra lo vencido. Devuelve true si dejó un ack en 'out'
// (llamar en bucle hasta que devuelva false).
bool atender(Ack& out);

}  // namespace act_riego

#endif  // ACT_RIEGO_H
