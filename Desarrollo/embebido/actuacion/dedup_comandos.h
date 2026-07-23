// ============================================================================
//  dedup_comandos.h  -  Idempotencia por commandId (defensa en profundidad)
// ----------------------------------------------------------------------------
//  La idempotencia principal la garantiza el backend (in-flight lock +
//  cooldown). Acá el nodo recuerda los últimos commandId ejecutados para no
//  repetir una acción física ante una reentrega del broker.
// ============================================================================
#ifndef DEDUP_COMANDOS_H
#define DEDUP_COMANDOS_H

#include "tipos.h"

namespace dedup_comandos {

// true si el commandId ya fue ejecutado (y por lo tanto no debe repetirse).
// Comandos sin id ("") nunca se consideran duplicados.
bool yaEjecutado(const char* commandId);

// Registra un commandId como ejecutado, junto al ack producido (para reenviarlo).
void registrar(const char* commandId, const Ack& ack);

// Recupera el ack previo de un commandId ya ejecutado. false si no está.
bool ackPrevio(const char* commandId, Ack& out);

}  // namespace dedup_comandos

#endif  // DEDUP_COMANDOS_H
