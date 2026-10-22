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

// ¿El motor está en marcha? Lo usa la cola de cargas para soltar el lugar de la malla cuando termina.
bool moviendoseAhora();

// ¿La malla ya está en la posición que pide 'cmd' (y no se está moviendo)? Entonces el comando es un sin_cambio:
// no ocupa lugar en la cola de cargas.
bool yaEnPosicion(const Comando& cmd);

// Devuelve true si 'ack' ya tiene la respuesta (ya estaba en esa posición o
// el comando es inválido); false si el motor arrancó y el ack saldrá por atender().
// Un comando nuevo durante un movimiento lo reemplaza (el viejo: ERROR "reemplazado").
bool comenzar(const Comando& cmd, Ack& ack);

// Para el motor si llegó o venció el tiempo. Devuelve true si dejó un ack en
// 'out' (llamar en bucle hasta que devuelva false).
bool atender(Ack& out);

// Estado real de los dos finales de carrera de la malla (S-05). Con ambos activos a la vez, o ninguno, el cableado o
// los sensores están mal; el firmware lo informa en el log del error.
struct EstadoFinesDeCarrera {
  bool cerrada;   // la malla está desenrollada: cubre
  bool abierta;   // la malla está enrollada: deja pasar el sol
};
EstadoFinesDeCarrera estadoFines();

// Si hay una falla del final de carrera que todavía no se informó por el tópico de errores, devuelve true y deja en
// 'valor' (>= 8 bytes) cuál falló: VALOR_FIN_CERRADA (la malla no llegó a cerrar) o VALOR_FIN_ABIERTA (no llegó a abrir).
// Se informa UNA vez por evento: no se repite hasta que un movimiento termina bien.
bool tomarFallaPendiente(char* valor, size_t n);

}  // namespace act_mediasombra

#endif  // ACT_MEDIASOMBRA_H
