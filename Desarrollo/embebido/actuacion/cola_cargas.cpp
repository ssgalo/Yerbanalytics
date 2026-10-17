#include "cola_cargas.h"

#include <string.h>

namespace cola_cargas {

// Un límite fuera de rango falla al compilar (la estructura guarda EN_CURSO_MAX cargas: el límite + una malla inmediata).
static_assert(MAX_CARGAS_SIMULTANEAS >= 1 && MAX_CARGAS_SIMULTANEAS < EN_CURSO_MAX,
              "MAX_CARGAS_SIMULTANEAS (config.h) debe estar entre 1 y EN_CURSO_MAX - 1");

static Carga enCursoLista[EN_CURSO_MAX];
static int   cantEnCurso = 0;

static Carga colaLista[COLA_CARGAS_MAX];   // orden de llegada (índice 0 = la próxima)
static int   cantEnCola = 0;
static bool  slotUsado[COLA_CARGAS_MAX];

static int limite = MAX_CARGAS_SIMULTANEAS;

// La mediasombra es una sola: su clave ignora el sector.
static bool mismaClave(const Carga& c, int sector, int tipo) {
  if (c.tipo != tipo) {
    return false;
  }
  return tipo == TIPO_MEDIASOMBRA || c.sector == sector;
}

static int buscarEnCurso(int sector, int tipo) {
  for (int i = 0; i < cantEnCurso; i++) {
    if (mismaClave(enCursoLista[i], sector, tipo)) return i;
  }
  return -1;
}

static int buscarEnCola(int sector, int tipo) {
  for (int i = 0; i < cantEnCola; i++) {
    if (mismaClave(colaLista[i], sector, tipo)) return i;
  }
  return -1;
}

static uint32_t restanteMs(const Carga& c, uint32_t ahoraMs) {
  int32_t falta = (int32_t) (c.inicioMs + c.duracionMs - ahoraMs);
  return falta > 0 ? (uint32_t) falta : 0;
}

// Cota superior de la espera de la posición 'indice' (0 = primera): lo que más tarda en liberarse lo que corre
// + la duración de lo que tiene adelante.
static uint32_t esperaHasta(int indice, uint32_t ahoraMs) {
  uint32_t espera = 0;
  for (int i = 0; i < cantEnCurso; i++) {
    uint32_t r = restanteMs(enCursoLista[i], ahoraMs);
    if (r > espera) espera = r;
  }
  for (int i = 0; i < indice; i++) {
    espera += colaLista[i].duracionMs;
  }
  return espera;
}

static void agregarDescartado(Descartado* lista, int& cant, const Carga& c, const char* motivo) {
  if (cant >= COLA_CARGAS_MAX) return;
  Descartado& d = lista[cant++];
  strncpy(d.commandId, c.commandId, sizeof(d.commandId) - 1);
  d.commandId[sizeof(d.commandId) - 1] = '\0';
  d.sector = c.sector;
  d.tipo = c.tipo;
  d.motivo = motivo;
}

static void quitarDeCola(int indice) {
  slotUsado[colaLista[indice].slot] = false;
  for (int i = indice + 1; i < cantEnCola; i++) {
    colaLista[i - 1] = colaLista[i];
  }
  cantEnCola--;
}

static void quitarEnCurso(int indice) {
  for (int i = indice + 1; i < cantEnCurso; i++) {
    enCursoLista[i - 1] = enCursoLista[i];
  }
  cantEnCurso--;
}

static void ponerEnCurso(const Carga& c, uint32_t ahoraMs) {
  if (cantEnCurso >= EN_CURSO_MAX) {
    return;   // no puede pasar con límite <= EN_CURSO_MAX - 1
  }
  Carga& e = enCursoLista[cantEnCurso++];
  e = c;
  e.inicioMs = ahoraMs;
}

static int casilleroLibre() {
  for (int i = 0; i < COLA_CARGAS_MAX; i++) {
    if (!slotUsado[i]) return i;
  }
  return -1;
}

void reiniciar() {
  cantEnCurso = 0;
  cantEnCola = 0;
  memset(slotUsado, 0, sizeof(slotUsado));
  limite = MAX_CARGAS_SIMULTANEAS;
}

void establecerLimite(int nuevo) {
  limite = nuevo < 1 ? 1 : (nuevo > EN_CURSO_MAX - 1 ? EN_CURSO_MAX - 1 : nuevo);
}

Llegada llegar(const Carga& carga, bool inmediata, uint32_t ahoraMs) {
  Llegada l = {};
  // Sólo la mediasombra puede saltear la cola.
  bool salta = inmediata && carga.tipo == TIPO_MEDIASOMBRA;

  // 1. La misma clave en curso: la reemplaza y arranca ya, en el mismo lugar.
  int cur = buscarEnCurso(carga.sector, carga.tipo);
  if (cur >= 0) {
    quitarEnCurso(cur);
    ponerEnCurso(carga, ahoraMs);
    // Lo que hubiera encolado de la misma clave ya no tiene sentido (no debería haber).
    int q = buscarEnCola(carga.sector, carga.tipo);
    if (q >= 0) {
      agregarDescartado(l.descartados, l.cantDescartados, colaLista[q], "reemplazado");
      quitarDeCola(q);
    }
    l.resultado = Resultado::ARRANCA;
    l.reemplazaEnCurso = true;
    return l;
  }

  // 2. La misma clave encolada.
  int q = buscarEnCola(carga.sector, carga.tipo);
  if (q >= 0 && !salta) {
    agregarDescartado(l.descartados, l.cantDescartados, colaLista[q], "reemplazado");
    int slot = colaLista[q].slot;
    colaLista[q] = carga;
    colaLista[q].slot = slot;
    l.resultado = Resultado::EN_COLA;
    l.slot = slot;
    l.posicion = q + 1;
    l.esperaMaxMs = esperaHasta(q, ahoraMs);
    return l;
  }

  // 3. Mediasombra de seguridad: arranca ya y desplaza a la encolada.
  if (salta) {
    if (q >= 0) {
      agregarDescartado(l.descartados, l.cantDescartados, colaLista[q], "reemplazado");
      quitarDeCola(q);
    }
    ponerEnCurso(carga, ahoraMs);
    l.resultado = Resultado::ARRANCA;
    return l;
  }

  // 4. Lugar libre y nadie esperando.
  if (cantEnCurso < limite && cantEnCola == 0) {
    ponerEnCurso(carga, ahoraMs);
    l.resultado = Resultado::ARRANCA;
    return l;
  }

  // 5. A la cola.
  int slot = casilleroLibre();
  if (cantEnCola >= COLA_CARGAS_MAX || slot < 0) {
    l.resultado = Resultado::COLA_LLENA;
    return l;
  }
  colaLista[cantEnCola] = carga;
  colaLista[cantEnCola].slot = slot;
  slotUsado[slot] = true;
  l.resultado = Resultado::EN_COLA;
  l.slot = slot;
  l.posicion = cantEnCola + 1;
  l.esperaMaxMs = esperaHasta(cantEnCola, ahoraMs);
  cantEnCola++;
  return l;
}

Cancelacion cancelar(int sector, int tipo) {
  Cancelacion c = {};
  int q = buscarEnCola(sector, tipo);
  while (q >= 0) {
    agregarDescartado(c.descartados, c.cantDescartados, colaLista[q], "cancelado");
    quitarDeCola(q);
    q = buscarEnCola(sector, tipo);
  }
  int cur = buscarEnCurso(sector, tipo);
  if (cur >= 0) {
    quitarEnCurso(cur);
    c.corteEnCurso = true;
  }
  return c;
}

bool proximo(uint32_t ahoraMs, Carga& salida) {
  if (cantEnCola == 0 || cantEnCurso >= limite) {
    return false;
  }
  salida = colaLista[0];
  quitarDeCola(0);
  ponerEnCurso(salida, ahoraMs);
  return true;
}

int liberarTerminadas(SigueActiva sigueActiva, uint32_t ahoraMs) {
  (void) ahoraMs;
  int soltadas = 0;
  for (int i = cantEnCurso - 1; i >= 0; i--) {
    if (!sigueActiva(enCursoLista[i].sector, enCursoLista[i].tipo)) {
      quitarEnCurso(i);
      soltadas++;
    }
  }
  return soltadas;
}

int enCurso() { return cantEnCurso; }
int enCola() { return cantEnCola; }

}  // namespace cola_cargas
