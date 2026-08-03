// ============================================================================
//  act_bomba.h  -  Bomba peristáltica (dosificación de insumos)
// ============================================================================
#ifndef ACT_BOMBA_H
#define ACT_BOMBA_H

#include "tipos.h"

namespace act_bomba {

void iniciar();

// Inyecta el volumen (ml) del comando. Aplica el límite de dosis máxima local.
void ejecutar(const Comando& cmd, Ack& ack);

}  // namespace act_bomba

#endif  // ACT_BOMBA_H
