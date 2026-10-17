// ============================================================================
//  Arnés host de cola_cargas (lógica pura, sin Arduino ni FreeRTOS).
//    g++ -std=c++17 -I actuacion actuacion/cola_cargas.cpp test/host/cola_cargas_test.cpp -o /tmp/cola && /tmp/cola
//  (corrido desde Desarrollo/embebido/). Asserts simples: sale con código != 0 al primer fallo.
// ============================================================================
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "cola_cargas.h"

using namespace cola_cargas;

static int total = 0;

#define CHECK(cond)                                                              \
  do {                                                                           \
    total++;                                                                     \
    if (!(cond)) {                                                               \
      std::printf("FALLA %s:%d  %s\n", __FILE__, __LINE__, #cond);               \
      std::exit(1);                                                              \
    }                                                                            \
  } while (0)

static Carga carga(const char* id, int sector, int tipo, uint32_t duracionMs) {
  Carga c = {};
  std::strncpy(c.commandId, id, sizeof(c.commandId) - 1);
  c.sector = sector;
  c.tipo = tipo;
  c.duracionMs = duracionMs;
  return c;
}

// Predicado de "sigue activa": el arnés simula al driver.
static bool activa[3][3];   // [sector][tipo]; la mediasombra usa sector 0
static bool sigueActiva(int sector, int tipo) { return activa[tipo == TIPO_MEDIASOMBRA ? 0 : sector][tipo]; }
static void setActiva(const Carga& c, bool v) { activa[c.tipo == TIPO_MEDIASOMBRA ? 0 : c.sector][c.tipo] = v; }

// Llega una carga y, si arranca, el "driver" la activa.
static Llegada llega(const Carga& c, bool inmediata, uint32_t ahora) {
  Llegada l = llegar(c, inmediata, ahora);
  if (l.resultado == Resultado::ARRANCA) setActiva(c, true);
  return l;
}

static void reset(int limite = 1) {
  reiniciar();
  establecerLimite(limite);
  std::memset(activa, 0, sizeof(activa));
}

static void caso_fifo_limite_1() {
  reset(1);
  Llegada a = llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  CHECK(a.resultado == Resultado::ARRANCA);
  Llegada b = llega(carga("B", 2, TIPO_INSUMO, 10000), false, 0);
  CHECK(b.resultado == Resultado::EN_COLA);
  CHECK(b.posicion == 1);
  CHECK(b.esperaMaxMs == 20000);
  Llegada c = llega(carga("C", 0, TIPO_MEDIASOMBRA, 30000), false, 0);
  CHECK(c.resultado == Resultado::EN_COLA);
  CHECK(c.posicion == 2);
  CHECK(c.esperaMaxMs == 20000 + 10000);   // lo que queda del riego + lo que tiene adelante
  CHECK(enCurso() == 1 && enCola() == 2);

  Carga sig;
  CHECK(!proximo(1000, sig));   // el riego sigue

  // Termina A (el driver lo suelta).
  activa[1][TIPO_RIEGO] = false;
  CHECK(liberarTerminadas(sigueActiva, 20000) == 1);
  CHECK(proximo(20000, sig));
  CHECK(std::strcmp(sig.commandId, "B") == 0);
  setActiva(sig, true);
  CHECK(!proximo(20000, sig));   // límite 1: C espera
  CHECK(enCurso() == 1 && enCola() == 1);

  activa[2][TIPO_INSUMO] = false;
  liberarTerminadas(sigueActiva, 30000);
  CHECK(proximo(30000, sig));
  CHECK(std::strcmp(sig.commandId, "C") == 0);
  CHECK(sig.tipo == TIPO_MEDIASOMBRA);
}

static void caso_fifo_limite_2() {
  reset(2);
  CHECK(llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0).resultado == Resultado::ARRANCA);
  CHECK(llega(carga("B", 2, TIPO_RIEGO, 20000), false, 0).resultado == Resultado::ARRANCA);
  Llegada c = llega(carga("C", 1, TIPO_INSUMO, 5000), false, 0);
  CHECK(c.resultado == Resultado::EN_COLA);
  CHECK(c.posicion == 1);
  CHECK(c.esperaMaxMs == 20000);
  CHECK(enCurso() == 2);

  activa[1][TIPO_RIEGO] = false;
  liberarTerminadas(sigueActiva, 20000);
  Carga sig;
  CHECK(proximo(20000, sig));
  CHECK(std::strcmp(sig.commandId, "C") == 0);
}

static void caso_off_descarta_encolado() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  llega(carga("B", 2, TIPO_RIEGO, 20000), false, 0);
  Cancelacion x = cancelar(2, TIPO_RIEGO);
  CHECK(x.cantDescartados == 1);
  CHECK(std::strcmp(x.descartados[0].commandId, "B") == 0);
  CHECK(std::strcmp(x.descartados[0].motivo, "cancelado") == 0);
  CHECK(!x.corteEnCurso);
  CHECK(enCola() == 0);
  CHECK(enCurso() == 1);   // S1 sigue regando
}

static void caso_off_corta_en_curso_y_libera() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  llega(carga("B", 2, TIPO_RIEGO, 20000), false, 0);
  Cancelacion x = cancelar(1, TIPO_RIEGO);
  CHECK(x.corteEnCurso);
  CHECK(x.cantDescartados == 0);
  CHECK(enCurso() == 0);
  activa[1][TIPO_RIEGO] = false;   // el driver lo cortó
  Carga sig;
  CHECK(proximo(500, sig));        // el lugar liberado lo toma B
  CHECK(std::strcmp(sig.commandId, "B") == 0);
}

static void caso_off_no_toca_otro_actuador_ni_sector() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  llega(carga("B", 1, TIPO_INSUMO, 10000), false, 0);
  llega(carga("C", 2, TIPO_RIEGO, 20000), false, 0);
  Cancelacion x = cancelar(1, TIPO_INSUMO);   // pump OFF S1
  CHECK(x.cantDescartados == 1);
  CHECK(std::strcmp(x.descartados[0].commandId, "B") == 0);
  CHECK(enCola() == 1);                       // C sigue
}

static void caso_reemplazo_en_curso() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  Llegada b = llega(carga("B", 1, TIPO_RIEGO, 30000), false, 5000);
  CHECK(b.resultado == Resultado::ARRANCA);   // usa el mismo lugar
  CHECK(b.reemplazaEnCurso);
  CHECK(enCurso() == 1 && enCola() == 0);
}

static void caso_reemplazo_encolado_mantiene_turno() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  llega(carga("X", 2, TIPO_INSUMO, 5000), false, 0);
  Llegada a = llega(carga("B", 2, TIPO_RIEGO, 20000), false, 0);
  CHECK(a.posicion == 2);
  Llegada b = llega(carga("B2", 2, TIPO_RIEGO, 20000), false, 0);
  CHECK(b.resultado == Resultado::EN_COLA);
  CHECK(b.posicion == 2);                    // conserva el lugar
  CHECK(b.slot == a.slot);                   // y el casillero
  CHECK(b.cantDescartados == 1);
  CHECK(std::strcmp(b.descartados[0].commandId, "B") == 0);
  CHECK(std::strcmp(b.descartados[0].motivo, "reemplazado") == 0);
  CHECK(enCola() == 2);
}

static void caso_prioridad_solo_mediasombra() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  // Un ON con prioridad se encola igual.
  Llegada on = llega(carga("B", 2, TIPO_RIEGO, 20000), true, 0);
  CHECK(on.resultado == Resultado::EN_COLA);
  Llegada ins = llega(carga("B2", 1, TIPO_INSUMO, 5000), true, 0);
  CHECK(ins.resultado == Resultado::EN_COLA);

  // La mediasombra normal se encola; la inmediata arranca ya y descarta la encolada.
  Llegada s1 = llega(carga("S1", 0, TIPO_MEDIASOMBRA, 30000), false, 0);
  CHECK(s1.resultado == Resultado::EN_COLA);
  Llegada s2 = llega(carga("S2", 0, TIPO_MEDIASOMBRA, 30000), true, 0);
  CHECK(s2.resultado == Resultado::ARRANCA);
  CHECK(!s2.reemplazaEnCurso);
  CHECK(s2.cantDescartados == 1);
  CHECK(std::strcmp(s2.descartados[0].commandId, "S1") == 0);
  CHECK(std::strcmp(s2.descartados[0].motivo, "reemplazado") == 0);
  CHECK(enCurso() == 2);   // supera el límite a propósito
  Carga sig;
  CHECK(!proximo(0, sig)); // mientras haya 2 en curso, nada más arranca
}

static void caso_set_0_normal_se_encola() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  Llegada s = llega(carga("S", 0, TIPO_MEDIASOMBRA, 30000), false, 0);
  CHECK(s.resultado == Resultado::EN_COLA);
}

static void caso_mediasombra_en_curso_se_reemplaza() {
  reset(1);
  llega(carga("S1", 0, TIPO_MEDIASOMBRA, 30000), false, 0);
  Llegada s2 = llega(carga("S2", 0, TIPO_MEDIASOMBRA, 30000), false, 1000);
  CHECK(s2.resultado == Resultado::ARRANCA);
  CHECK(s2.reemplazaEnCurso);
  CHECK(enCurso() == 1);
}

static void caso_cola_llena() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 1000), false, 0);
  char id[8];
  // Llenar con claves distintas no alcanza (sólo hay 5 combinaciones): se usan sectores ficticios 3..10.
  for (int i = 0; i < COLA_CARGAS_MAX; i++) {
    std::snprintf(id, sizeof(id), "Q%d", i);
    Llegada l = llega(carga(id, 10 + i, TIPO_INSUMO, 1000), false, 0);
    CHECK(l.resultado == Resultado::EN_COLA);
    CHECK(l.posicion == i + 1);
  }
  Llegada l = llega(carga("EXTRA", 50, TIPO_INSUMO, 1000), false, 0);
  CHECK(l.resultado == Resultado::COLA_LLENA);
  CHECK(enCola() == COLA_CARGAS_MAX);
  // Un reemplazo no necesita lugar nuevo.
  Llegada r = llega(carga("Q0b", 10, TIPO_INSUMO, 1000), false, 0);
  CHECK(r.resultado == Resultado::EN_COLA);
  CHECK(r.posicion == 1);
}

static void caso_espera_descuenta_lo_transcurrido() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  Llegada b = llega(carga("B", 2, TIPO_RIEGO, 20000), false, 15000);
  CHECK(b.esperaMaxMs == 5000);
  // Una carga vencida no resta de menos cero.
  Llegada c = llega(carga("C", 2, TIPO_INSUMO, 10000), false, 90000);
  CHECK(c.esperaMaxMs == 0 + 20000);
}

static void caso_liberar_terminadas_no_pierde_lugar() {
  reset(1);
  llega(carga("A", 1, TIPO_RIEGO, 20000), false, 0);
  // El driver todavía la tiene: no se libera.
  CHECK(liberarTerminadas(sigueActiva, 10) == 0);
  CHECK(enCurso() == 1);
  // El driver la perdió (ack perdido): igual se libera, no se cuelga la cola.
  activa[1][TIPO_RIEGO] = false;
  CHECK(liberarTerminadas(sigueActiva, 20) == 1);
  CHECK(enCurso() == 0);
}

static void caso_limite_invalido_se_corrige() {
  reset(1);
  establecerLimite(0);   // < 1 se trata como 1
  llega(carga("A", 1, TIPO_RIEGO, 1000), false, 0);
  CHECK(llega(carga("B", 2, TIPO_RIEGO, 1000), false, 0).resultado == Resultado::EN_COLA);
}

int main() {
  caso_fifo_limite_1();
  caso_fifo_limite_2();
  caso_off_descarta_encolado();
  caso_off_corta_en_curso_y_libera();
  caso_off_no_toca_otro_actuador_ni_sector();
  caso_reemplazo_en_curso();
  caso_reemplazo_encolado_mantiene_turno();
  caso_prioridad_solo_mediasombra();
  caso_set_0_normal_se_encola();
  caso_mediasombra_en_curso_se_reemplaza();
  caso_cola_llena();
  caso_espera_descuenta_lo_transcurrido();
  caso_liberar_terminadas_no_pierde_lugar();
  caso_limite_invalido_se_corrige();
  std::printf("cola_cargas_test: OK (%d verificaciones)\n", total);
  return 0;
}
