// ============================================================================
//  buffer_offline.h  -  Cola FIFO de telemetría en flash (NVS)
// ----------------------------------------------------------------------------
//  Cuando no hay red al momento de reportar, la lectura se persiste con su
//  timestamp original. Al reconectar, task_red drena el buffer en orden
//  cronológico y borra cada registro tras publicarlo (sin duplicados).
//  Al llenarse, descarta el más antiguo (FIFO) dejando traza por serial.
// ============================================================================
#ifndef BUFFER_OFFLINE_H
#define BUFFER_OFFLINE_H

#include "tipos.h"

namespace buffer_offline {

void iniciar();

// Persiste un paquete. Si el buffer está lleno, descarta el más antiguo.
bool encolar(const PaqueteTelemetria& p);

// Copia (sin remover) el paquete más antiguo en 'out'. false si está vacío.
bool verMasAntiguo(PaqueteTelemetria& out);

// Elimina el más antiguo (llamar tras publicarlo con éxito).
void removerMasAntiguo();

bool vacio();
uint16_t cantidad();

}  // namespace buffer_offline

#endif  // BUFFER_OFFLINE_H
