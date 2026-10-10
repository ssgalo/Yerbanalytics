// ============================================================================
//  act_mediasombra.h  -  Motor de la mediasombra (rustificación)
// ----------------------------------------------------------------------------
//  Motor DC por L298N (canal A) con dos finales de carrera: enrollada y
//  desenrollada. Dos estados, sin posiciones intermedias.
//
//  El backend manda "shade SET {targetPct}" con el % de APERTURA:
//  >= MEDIASOMBRA_UMBRAL_APERTURA_PCT → enrollar (deja pasar el sol);
//  menos → desenrollar (cubre). Hay una sola mediasombra para los dos sectores:
//  manda el último comando.
//
//  No bloquea: comenzar() arranca el motor y atender() lo para al llegar al
//  final de carrera o al vencer TIMEOUT_MEDIASOMBRA_MS (falla_mecanica).
// ============================================================================
#ifndef ACT_MEDIASOMBRA_H
#define ACT_MEDIASOMBRA_H

#include "tipos.h"

namespace act_mediasombra {

void iniciar();

// Devuelve true si 'ack' ya tiene la respuesta (ya estaba en esa posición o
// el comando es inválido); false si el motor arrancó y el ack saldrá por atender().
// Un comando nuevo durante un movimiento lo reemplaza (el viejo: ERROR "reemplazado").
bool comenzar(const Comando& cmd, Ack& ack);

// Para el motor si llegó o venció el tiempo. Devuelve true si dejó un ack en
// 'out' (llamar en bucle hasta que devuelva false).
bool atender(Ack& out);

}  // namespace act_mediasombra

#endif  // ACT_MEDIASOMBRA_H
