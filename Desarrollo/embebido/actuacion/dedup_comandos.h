// ============================================================================
//  dedup_comandos.h  -  Idempotencia por commandId (defensa en profundidad)
// ----------------------------------------------------------------------------
//  La idempotencia principal la garantiza el backend (in-flight lock +
//  cooldown). Acá el nodo recuerda los últimos commandId recibidos para no
//  repetir una acción física ante una reentrega del broker:
//    - en curso   → la reentrega se ignora (el ack sale cuando termine);
//    - terminado  → se reenvía el ack que produjo.
// ============================================================================
#ifndef DEDUP_COMANDOS_H
#define DEDUP_COMANDOS_H

#include "tipos.h"

namespace dedup_comandos {

// true si el commandId ya se recibió (en curso o terminado): no debe repetirse.
// Comandos sin id ("") nunca se consideran duplicados.
bool yaRecibido(const char* commandId);

// Registra un commandId que empieza a ejecutarse.
void registrarEnCurso(const char* commandId);

// Registra el ack final de un commandId (lo marca terminado, para reenviarlo).
void registrar(const char* commandId, const Ack& ack);

// Recupera el ack de un commandId terminado. false si no está o sigue en curso.
bool ackPrevio(const char* commandId, Ack& out);

}  // namespace dedup_comandos

#endif  // DEDUP_COMANDOS_H
