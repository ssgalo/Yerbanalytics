// ============================================================================
//  net_mqtt_usb.cpp  -  net_mqtt.h por el cable USB, para la expo
// ----------------------------------------------------------------------------
//  Implementa la misma interfaz que comun/net_mqtt.cpp, pero en vez de hablar
//  MQTT por Wi-Fi intercambia líneas con el puente de la PC por Serial0 (UART,
//  el puerto del CH343). Los logs siguen saliendo por Serial (USB nativo).
//
//  Protocolo (design D3 de add-transporte-usb-nodo; espejo: puente-usb/protocolo.py):
//    placa → PC   @S <qos> <tópico>              suscribirse
//    placa → PC   @P <qos> <tópico> <payload>    publicar
//    PC → placa   @H <epoch> <sesión>            latido del puente, con la hora
//    PC → placa   @M <tópico> <payload>          mensaje de un tópico suscripto
//    PC → placa   @X                             el puente perdió el broker
//  Lo que no respeta el formato se descarta (la ROM imprime por el mismo UART).
// ============================================================================
#include "net_mqtt.h"
#include "enlace_usb.h"
#include "config.h"
#include <sys/time.h>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>

// Valores por defecto: config.h no necesita cambios para compilar el entorno USB.
#ifndef USB_TRANSPORTE_BAUD
#define USB_TRANSPORTE_BAUD 115200
#endif
// El puente late cada 2 s: tres latidos perdidos y el enlace se da por caído.
#ifndef USB_LATIDO_VENCE_MS
#define USB_LATIDO_VENCE_MS 6000
#endif

// Espejo de LARGO_MAXIMO del puente: MQTT_BUFFER_SIZE más el encabezado de la línea.
static const size_t USB_LINEA_MAX = 1200;
// Una hora anterior a esto es un reloj sin setear (mismo criterio que reloj.cpp).
static const uint32_t EPOCH_MINIMO_VALIDO = 1700000000UL;

static HardwareSerial& enlace = Serial0;

namespace enlace_usb {

static volatile unsigned long ultimoLatidoMs = 0;
static volatile bool huboLatido = false;

bool hayLatido() {
  return huboLatido && (millis() - ultimoLatidoMs < USB_LATIDO_VENCE_MS);
}

}  // namespace enlace_usb

namespace net_mqtt {

static const int MAX_SUSCRIPCIONES = 4;
struct Suscripcion { const char* topic; uint8_t qos; };
static Suscripcion suscripciones[MAX_SUSCRIPCIONES];
static int cantSuscripciones = 0;

static MQTT_CALLBACK_SIGNATURE;            // el del sketch: onMensajeMqtt
static SemaphoreHandle_t candadoEscritura = nullptr;

static char linea[USB_LINEA_MAX + 1];
static size_t largoLinea = 0;
static bool descartandoLinea = false;      // la línea actual pasó el máximo: se tira hasta el '\n'

static char sesionActual[16] = "";
static bool sesionNueva = false;           // el latido trajo otra sesión: hay que reenviar las suscripciones
static bool estabaConectado = false;

static void escribirLinea(const char* encabezado, const char* resto) {
  xSemaphoreTake(candadoEscritura, portMAX_DELAY);
  // El '\n' previo cierra cualquier texto ajeno a medio imprimir en UART0 (logs del sistema), así la línea del
  // protocolo empieza limpia. El puente ignora las líneas vacías.
  enlace.print('\n');
  enlace.print(encabezado);
  enlace.print(resto);
  enlace.print('\n');
  xSemaphoreGive(candadoEscritura);
}

// PubSubClient sólo se suscribe con QoS 0 o 1; se respeta el mismo tope para no cambiar el comportamiento.
static void enviarSuscripcion(const Suscripcion& s) {
  char encabezado[8];
  snprintf(encabezado, sizeof(encabezado), "@S %u ", s.qos > 1 ? 1 : s.qos);
  escribirLinea(encabezado, s.topic);
  Serial.printf("[mqtt-usb] Suscripción enviada al puente: %s\n", s.topic);
}

void iniciar(MQTT_CALLBACK_SIGNATURE) {
  net_mqtt::callback = callback;
  candadoEscritura = xSemaphoreCreateMutex();
  enlace.setRxBufferSize(2 * USB_LINEA_MAX);
  enlace.setTxBufferSize(2 * USB_LINEA_MAX);
  enlace.begin(USB_TRANSPORTE_BAUD);
  Serial.printf("[mqtt-usb] Transporte USB por Serial0 a %d baudios; esperando al puente.\n", USB_TRANSPORTE_BAUD);
}

void suscribir(const char* topic, uint8_t qos) {
  if (cantSuscripciones >= MAX_SUSCRIPCIONES) {
    Serial.printf("[mqtt-usb] ERROR: no entra la suscripción a %s (máximo %d)\n", topic, MAX_SUSCRIPCIONES);
    return;
  }
  suscripciones[cantSuscripciones] = { topic, qos };
  if (enlace_usb::hayLatido()) {
    enviarSuscripcion(suscripciones[cantSuscripciones]);
  }
  cantSuscripciones++;
}

bool conectado() {
  return enlace_usb::hayLatido();
}

// Igual que con PubSubClient: al (re)conectar se re-suscribe todo. Acá "reconectar" es volver a recibir latidos
// o recibir una sesión nueva (el puente se reinició sin que se cortaran los latidos, design D4).
bool reconectarSiNecesario() {
  bool ok = enlace_usb::hayLatido();
  if (ok && (!estabaConectado || sesionNueva)) {
    Serial.printf("[mqtt-usb] Puente conectado (sesión %s): se envían las suscripciones.\n", sesionActual);
    for (int i = 0; i < cantSuscripciones; i++) {
      enviarSuscripcion(suscripciones[i]);
    }
    sesionNueva = false;
  } else if (!ok && estabaConectado) {
    Serial.println("[mqtt-usb] Sin latido del puente: enlace caído.");
  }
  estabaConectado = ok;
  return ok;
}

bool publicar(const char* topic, const char* payload, uint8_t qos) {
  if (!enlace_usb::hayLatido()) {
    return false;  // el sketch guarda la telemetría en el buffer offline y reintenta los acks
  }
  char encabezado[96];
  int n = snprintf(encabezado, sizeof(encabezado), "@P %u %s ", qos, topic);
  if (n < 0 || (size_t) n >= sizeof(encabezado) || (size_t) n + strlen(payload) > USB_LINEA_MAX) {
    Serial.printf("[mqtt-usb] ERROR: el mensaje de %s no entra en una línea\n", topic);
    return false;
  }
  if (strchr(payload, '\n') != nullptr) {
    Serial.printf("[mqtt-usb] ERROR: el payload de %s tiene un salto de línea\n", topic);
    return false;
  }
  escribirLinea(encabezado, payload);
  return true;
}

// "@H <epoch> <sesión>": marca el enlace vivo, ajusta la hora y detecta una sesión nueva.
static void procesarLatido(char* resto) {
  char* epochTxt = strtok(resto, " ");
  char* sesion = strtok(nullptr, " ");
  if (epochTxt == nullptr || sesion == nullptr) {
    return;
  }
  uint32_t epoch = strtoul(epochTxt, nullptr, 10);
  time_t ahora = time(nullptr);
  // Sólo si difiere más de 2 s: ajustarla en cada latido le borraría la fracción de segundo cada vez.
  if (epoch >= EPOCH_MINIMO_VALIDO && (ahora < (time_t) epoch - 2 || ahora > (time_t) epoch + 2)) {
    // reloj::sincronizarSiHayRed() la toma de acá en la próxima vuelta de task_red (design D5).
    struct timeval tv = { (time_t) epoch, 0 };
    settimeofday(&tv, nullptr);
  }
  // Se compara lo mismo que se guarda (a lo sumo 15 caracteres): una sesión más larga no debe parecer nueva siempre.
  if (strncmp(sesion, sesionActual, sizeof(sesionActual) - 1) != 0) {
    strncpy(sesionActual, sesion, sizeof(sesionActual) - 1);
    sesionActual[sizeof(sesionActual) - 1] = '\0';
    sesionNueva = true;
  }
  enlace_usb::ultimoLatidoMs = millis();
  enlace_usb::huboLatido = true;
}

// "@M <tópico> <payload>": se entrega al callback del sketch, como lo haría PubSubClient.
static void procesarMensaje(char* resto) {
  char* espacio = strchr(resto, ' ');
  if (espacio == nullptr || espacio == resto || *(espacio + 1) == '\0') {
    return;
  }
  *espacio = '\0';
  char* payload = espacio + 1;
  if (callback) {
    callback(resto, (uint8_t*) payload, (unsigned int) strlen(payload));
  }
}

static void procesarLinea() {
  if (largoLinea > 0 && linea[largoLinea - 1] == '\r') {
    linea[--largoLinea] = '\0';
  }
  if (strcmp(linea, "@X") == 0) {
    // El puente perdió el broker: dejar de publicar ya, para que lo nuevo vaya al buffer offline y a los reintentos.
    enlace_usb::huboLatido = false;
    return;
  }
  if (largoLinea < 3 || linea[0] != '@' || linea[2] != ' ') {
    return;  // texto de la ROM, logs ajenos o basura
  }
  if (linea[1] == 'H') {
    procesarLatido(linea + 3);
  } else if (linea[1] == 'M') {
    procesarMensaje(linea + 3);
  }
}

void loop() {
  while (enlace.available() > 0) {
    char c = (char) enlace.read();
    if (c == '\n') {
      if (!descartandoLinea) {
        linea[largoLinea] = '\0';
        procesarLinea();
      }
      largoLinea = 0;
      descartandoLinea = false;
    } else if (!descartandoLinea) {
      if (largoLinea < USB_LINEA_MAX) {
        linea[largoLinea++] = c;
      } else {
        descartandoLinea = true;  // demasiado larga: se descarta entera, nunca un pedazo
      }
    }
  }
}

}  // namespace net_mqtt
