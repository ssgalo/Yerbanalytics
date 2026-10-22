// ============================================================================
//  cola_cargas.h  -  Cola de cargas del nodo (add-cola-actuadores-nodo)
// ----------------------------------------------------------------------------
//  Lógica PURA: sin Arduino, sin FreeRTOS, sin relés. Sólo decide qué carga corre
//  y cuál espera, para que el nodo no prenda varias a la vez (la fuente del
//  prototipo no banca el pico). El reloj entra como argumento, así se prueba en
//  el host: ver test/host/cola_cargas_test.cpp.
//
//  Carga = una acción que mueve potencia y dura: un tramo de riego de un sector,
//  un tramo de insumo de un sector o un movimiento de la mediasombra (una sola
//  por nodo: su clave ignora el sector).
//
//  Quién la usa: task_actuacion decide al llegar cada comando (llegar / cancelar),
//  arranca los drivers cuando le toca (proximo) y suelta los lugares de las cargas
//  que los drivers ya terminaron (liberarTerminadas).
// ============================================================================
#ifndef COLA_CARGAS_H
#define COLA_CARGAS_H

#include <stdint.h>

#if defined(__has_include)
#if __has_include("config.h")
#include "config.h"
#endif
#endif

// Cuántas cargas corren a la vez. Un config.h anterior no lo define: 1.
#ifndef MAX_CARGAS_SIMULTANEAS
#define MAX_CARGAS_SIMULTANEAS 1
#endif
static_assert(MAX_CARGAS_SIMULTANEAS >= 1, "MAX_CARGAS_SIMULTANEAS (config.h) debe ser >= 1");

namespace cola_cargas {

// Cuántos comandos pueden esperar: dos sectores x riego/insumo + mediasombra, con holgura.
static const int COLA_CARGAS_MAX = 8;
// Cargas en curso simultáneas que la estructura soporta (el límite + una mediasombra inmediata).
static const int EN_CURSO_MAX = 8;
static const int LARGO_COMMAND_ID = 40;

// Tipo de carga. RIEGO e INSUMO coinciden con el índice de tramo de act_riego.
static const int TIPO_RIEGO = 0;
static const int TIPO_INSUMO = 1;
static const int TIPO_MEDIASOMBRA = 2;

struct Carga {
  char     commandId[LARGO_COMMAND_ID];
  int      sector;        // 1..N; en la mediasombra no es parte de la clave
  int      tipo;          // TIPO_*
  uint32_t duracionMs;    // estimada: cuenta para la espera de lo que viene detrás
  int      slot;          // casillero de la cola (el llamador guarda ahí su Comando); sólo si salió de la cola
  uint32_t inicioMs;      // cuándo arrancó (sólo las que están en curso)
};

// Un comando que salió de la cola sin correr.
struct Descartado {
  char        commandId[LARGO_COMMAND_ID];
  int         sector;
  int         tipo;
  const char* motivo;     // "cancelado" | "reemplazado" (detalle.tipo del ACK final)
};

enum class Resultado { ARRANCA, EN_COLA, COLA_LLENA };

struct Llegada {
  Resultado  resultado;
  bool       reemplazaEnCurso;   // ARRANCA: tomó el lugar de una carga en curso de la misma clave (el driver la reemplaza)
  int        slot;               // EN_COLA: casillero donde el llamador guarda el Comando
  int        posicion;           // EN_COLA: 1 = la próxima
  uint32_t   esperaMaxMs;        // EN_COLA: cota superior de la espera
  Descartado descartados[COLA_CARGAS_MAX];   // encolados que esta llegada desplazó
  int        cantDescartados;
};

struct Cancelacion {
  bool       corteEnCurso;       // había una carga en curso de esa clave (el driver emite su 'cancelado')
  Descartado descartados[COLA_CARGAS_MAX];
  int        cantDescartados;
};

// Vacía la cola y las cargas en curso; el límite vuelve a MAX_CARGAS_SIMULTANEAS.
void reiniciar();

// Sólo para pruebas: cambia el límite de simultáneas (< 1 se trata como 1).
void establecerLimite(int limite);

// Llega una carga válida (la validación ya ocurrió).
//  - Misma clave en curso: la reemplaza y arranca ya (ARRANCA, reemplazaEnCurso). Mismo lugar, no suma consumo.
//  - Misma clave encolada: ocupa su lugar en la fila (EN_COLA, desplaza al viejo con "reemplazado").
//  - 'inmediata' (sólo se honra en la mediasombra): arranca ya aunque el límite esté lleno y descarta la
//    mediasombra encolada ("reemplazado").
//  - Si hay lugar y nadie espera: ARRANCA. Si no: EN_COLA, o COLA_LLENA si no entra.
// Cuando ARRANCA, la cola ya la cuenta como en curso (el llamador arranca el driver).
Llegada llegar(const Carga& carga, bool inmediata, uint32_t ahoraMs);

// Llega un OFF de (sector, tipo): descarta lo encolado de esa clave y suelta el lugar de lo que corría.
Cancelacion cancelar(int sector, int tipo);

// ¿Hay lugar y alguien esperando? Entonces saca la cabeza de la fila, la cuenta como en curso y la devuelve.
bool proximo(uint32_t ahoraMs, Carga& salida);

// Suelta las cargas en curso que el driver ya no tiene activas (terminaron, o su ACK se perdió: la cola no se
// puede trabar). Devuelve cuántas soltó.
typedef bool (*SigueActiva)(int sector, int tipo);
int liberarTerminadas(SigueActiva sigueActiva, uint32_t ahoraMs);

int enCurso();
int enCola();

}  // namespace cola_cargas

#endif  // COLA_CARGAS_H
