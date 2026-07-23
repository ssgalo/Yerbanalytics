// ============================================================================
//  act_mediasombra.h  -  Motor de la mediasombra (rustificación)
// ----------------------------------------------------------------------------
//  Mueve la lona hacia una posición objetivo (% de apertura). Corta la energía
//  ante sobrecorriente sostenida (atasco) o si no llega el fin de carrera en el
//  tiempo estipulado.
// ============================================================================
#ifndef ACT_MEDIASOMBRA_H
#define ACT_MEDIASOMBRA_H

#include "tipos.h"

namespace act_mediasombra {

void iniciar();
void ejecutar(const Comando& cmd, Ack& ack);

}  // namespace act_mediasombra

#endif  // ACT_MEDIASOMBRA_H
