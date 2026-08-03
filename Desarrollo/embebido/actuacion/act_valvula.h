// ============================================================================
//  act_valvula.h  -  Electroválvula de riego (con feedback de caudalímetro)
// ============================================================================
#ifndef ACT_VALVULA_H
#define ACT_VALVULA_H

#include "tipos.h"

namespace act_valvula {

void iniciar();

// Ejecuta un comando de riego. Completa 'ack' con SUCCESS/ERROR y detalle.
// Aplica el límite de seguridad local de apertura máxima.
void ejecutar(const Comando& cmd, Ack& ack);

}  // namespace act_valvula

#endif  // ACT_VALVULA_H
