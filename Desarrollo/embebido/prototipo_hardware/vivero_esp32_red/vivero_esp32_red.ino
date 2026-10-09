// ============================================================
//  VIVERO ESP32 RED — vivero_esp32 + WiFi + MQTT (riel de la cámara)
//  Componentes:
//    - NEMA 17 + DRV8825 (riel cámara)
//    - JGA25-370 + L298N canal A (mediasombra)
//    - Bomba DC + L298N canal B (riego)
//    - 2x Endstop (mediasombra)
//    - 2x Endstop (riel NEMA 17: home y fin de carrera)
//
//  Es una copia de vivero_esp32.ino (que no se toca) con red:
//    - Todos los comandos por monitor serie de siempre, más "red".
//    - Por MQTT maneja el riel: se suscribe a nursery/rail/command
//      y publica en nursery/rail/event.
//    - Además (add-secuencias-demo-expo) atiende por MQTT la válvula (sobre el driver
//      de la bomba) y la mediasombra del sector NODO_SECTOR_ID, con un ACK por comando,
//      y el comando de zona "leer ahora" (la lectura de sensores es un HUECO marcado).
//    - Contrato: openspec/changes/add-pasada-riel/design.md §1,
//      openspec/changes/add-secuencias-demo-expo/design.md §1 y §4, y
//      Desarrollo/embebido/comun/contrato.h (secciones "Riel", "Ack" y "Comando de zona").
//
//  Librerías (Library Manager de Arduino IDE):
//    - PubSubClient (Nick O'Leary) 2.8.x
//    - ArduinoJson  (Benoit Blanchon) 7.x
//  Placa: "esp32 by Espressif Systems" 3.x → ESP32 Dev Module.
// ============================================================

#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// ── Configuración local (WiFi y broker) ──────────────────────
// config.h NO se versiona: se copia de config.example.h y se completa.
#if __has_include("config.h")
  #include "config.h"
#else
  #error "Falta config.h: copia config.example.h como config.h (en esta misma carpeta) y completa WIFI_SSID, WIFI_PASSWORD y MQTT_HOST"
#endif

// El design lo llamaba WIFI_PASS; se aceptan los dos nombres.
#if !defined(WIFI_PASSWORD) && defined(WIFI_PASS)
  #define WIFI_PASSWORD WIFI_PASS
#endif
#if __has_include("config.h") && (!defined(WIFI_SSID) || !defined(WIFI_PASSWORD) || !defined(MQTT_HOST))
  #error "config.h incompleto: tiene que definir WIFI_SSID, WIFI_PASSWORD y MQTT_HOST (ver config.example.h)"
#endif
// Un config.h anterior a add-secuencias-demo-expo no compila a propósito: sin zona y sector
// el nodo no sabría a qué tópicos suscribirse.
#if __has_include("config.h") && (!defined(NODO_ZONA_ID) || !defined(NODO_SECTOR_ID))
  #error "config.h desactualizado: falta NODO_ZONA_ID y/o NODO_SECTOR_ID (ver config.example.h)"
#endif
#ifndef BOMBA_CAUDAL_PWM
  #define BOMBA_CAUDAL_PWM 200
#endif
#ifndef LECTURA_SENSORES_HABILITADA
  #define LECTURA_SENSORES_HABILITADA 0
#endif
#ifndef MQTT_PORT
  #define MQTT_PORT 1883
#endif
#ifndef MQTT_CLIENT_ID_BASE
  #define MQTT_CLIENT_ID_BASE "riel-esp32"
#endif

#if !defined(ARDUINOJSON_VERSION_MAJOR) || ARDUINOJSON_VERSION_MAJOR < 7
  #error "Hace falta ArduinoJson 7.x (Library Manager: ArduinoJson de Benoit Blanchon)"
#endif

// ── NEMA 17 / DRV8825 ────────────────────────────────────────
#define STEP_PIN     19
#define DIR_PIN      21
#define SLEEP_PIN  15
// Nivel de DIR_PIN que mueve el carro hacia HOME. Depende de cómo estén
// cableadas las bobinas al driver: si el motor va al revés, cambiar LOW↔HIGH.
#define DIR_HACIA_HOME HIGH
#define DIR_HACIA_FIN  (!DIR_HACIA_HOME)
// GPIO34 y GPIO35 son "solo entrada": no tienen pull-up/pull-down interno.
// Hace falta una resistencia pull-up externa (~10k a 3.3V) en cada pin,
// con el final de carrera entre el pin y GND.
#define ENDSTOP_RIEL_HOME 34   // final de carrera extremo home (retroceso)
#define ENDSTOP_RIEL_FIN  35   // final de carrera extremo opuesto (avance máximo)

// Tipo de contacto de los finales de carrera del riel:
// false = NO (normalmente abierto) → activado = LOW
// true  = NC (normalmente cerrado) → activado = HIGH
#define ENDSTOP_RIEL_NC true

#if ENDSTOP_RIEL_NC
  #define RIEL_ACTIVADO(pin) (digitalRead(pin) == HIGH)
#else
  #define RIEL_ACTIVADO(pin) (digitalRead(pin) == LOW)
#endif

#define POSICION_SECTOR_1 21000
#define POSICION_SECTOR_2 42000
// Máximo de pasos que puede dar el homing sin encontrar el endstop.
// Tiene que ser mayor que el largo total del riel en pasos (con margen).
#define MAX_PASOS_HOMING 80000

// ── L298N Canal A — Motor DC mediasombra ─────────────────────
#define MOTOR_IN1    22
#define MOTOR_IN2    23
#define MOTOR_ENA    25  // PWM velocidad

// ── L298N Canal B — Bomba ────────────────────────────────────
#define BOMBA_IN3    26
#define BOMBA_IN4    27
#define BOMBA_ENB    14  // PWM caudal

// ── Endstops mediasombra ─────────────────────────────────────
#define ENDSTOP_ENROLLADA    32
#define ENDSTOP_DESENROLLADA 33

// Tipo de contacto de los finales de carrera de la mediasombra (con INPUT_PULLUP,
// contacto entre el pin y GND):
// false = NO (normalmente abierto) → activado = LOW
// true  = NC (normalmente cerrado) → activado = HIGH
#define ENDSTOP_MEDIASOMBRA_NC true

#if ENDSTOP_MEDIASOMBRA_NC
  #define MEDIASOMBRA_ACTIVADO(pin) (digitalRead(pin) == HIGH)
#else
  #define MEDIASOMBRA_ACTIVADO(pin) (digitalRead(pin) == LOW)
#endif

// ── Configuración LEDC (PWM en ESP32) ────────────────────────
// Compatible con ESP32 Arduino 3.x (nueva API)
#define PWM_FREQ     1000
#define PWM_BITS     8    // resolución 8 bits → valores 0-255

// ── Variables NEMA 17 ─────────────────────────────────────────
long pasos_actuales = 0;  // posición actual en pasos desde home

// ── Velocidad NEMA (microsegundos entre pasos) ────────────────
// Más alto = más lento y más torque
#define VELOCIDAD_NEMA 500

// ── LED de estado de la red (GPIO 2, el LED azul de la placa) ─
// Parpadeo rápido: sin WiFi · lento: WiFi sin broker · fijo: conectado al broker
#define LED_ESTADO 2

// =============================================================
//  CONTRATO MQTT DEL RIEL
//  Espejo de Desarrollo/embebido/comun/contrato.h (sección "Riel"),
//  que es la fuente de verdad. Si cambia allá, cambia acá.
// =============================================================
#define RIEL_TOPIC_COMANDO   "nursery/rail/command"   // backend → ESP32
#define RIEL_TOPIC_EVENTO    "nursery/rail/event"     // ESP32 → backend
#define RIEL_QOS_SUSCRIPCION 1   // PubSubClient: 1 es lo máximo al suscribir; publica siempre QoS 0

#define RIEL_KEY_COMMAND_ID  "commandId"
#define RIEL_KEY_ACTUADOR    "actuador"
#define RIEL_KEY_ACCION      "accion"
#define RIEL_KEY_PARAMETROS  "parametros"
#define RIEL_KEY_POSICION    "posicion"
#define RIEL_KEY_STATUS      "status"
#define RIEL_KEY_PASOS       "pasos"
#define RIEL_KEY_CODIGO      "codigo"
#define RIEL_KEY_DETALLE     "detalle"

#define RIEL_ACTUADOR        "rail"
#define RIEL_ACCION_IR_A     "IR_A"
#define RIEL_ACCION_HOME     "HOME"

#define RIEL_STATUS_ACEPTADO "ACEPTADO"
#define RIEL_STATUS_LLEGO    "LLEGO"
#define RIEL_STATUS_ERROR    "ERROR"

#define RIEL_COD_COMANDO_INVALIDO   "COMANDO_INVALIDO"
#define RIEL_COD_HOME_NO_ENCONTRADO "HOME_NO_ENCONTRADO"
#define RIEL_COD_FIN_DE_CARRERA     "FIN_DE_CARRERA"
#define RIEL_COD_REEMPLAZADO        "REEMPLAZADO"

// ── Resultado de un movimiento (int, no enum: ver nota de prototipos) ──
#define MOV_OK           0   // llegó
#define MOV_FIN_HOME     1   // tocó el final de carrera home antes de llegar
#define MOV_FIN_OPUESTO  2   // tocó el final de carrera opuesto antes de llegar
#define MOV_INTERRUMPIDO 3   // llegó otro comando por MQTT (el último gana)
#define MOV_SIN_HOME     4   // el homing no encontró home (timeout o final opuesto)

// ── Acción del comando recibido ───────────────────────────────
#define ACCION_NINGUNA 0
#define ACCION_IR_A    1
#define ACCION_HOME    2

// ── Parámetros de red ─────────────────────────────────────────
// (Nombres con prefijo RED_ para no chocar con los MQTT_* de PubSubClient.)
#define PASOS_ENTRE_RED          200    // cada cuántos pasos se atiende MQTT al moverse (~200 ms)
#define RED_MQTT_BUFFER          512    // bytes; sobra para los JSON del riel
#define RED_MQTT_KEEPALIVE_S     15
#define RED_MQTT_SOCKET_TIMEOUT_S 2
#define RED_MQTT_REINTENTO_MS    3000   // reintento de conexión al broker (sólo en reposo)
#define RED_WIFI_REINTENTO_MS    15000  // si el WiFi no vuelve solo, se reinicia el intento
#define LARGO_ID                 40     // commandId: UUID de 36 caracteres + '\0'
#define LARGO_DETALLE            96
#define LARGO_EVENTO             320
#define CANT_RECIENTES           4

// =============================================================
//  CONTRATO MQTT DE ACTUADORES Y DE ZONA (add-secuencias-demo-expo)
//  Espejo de Desarrollo/embebido/comun/contrato.h (secciones "Ack" y
//  "Comando de zona"), que es la fuente de verdad. Si cambia allá, cambia acá.
//  Los tópicos se arman en red_iniciar() con NODO_ZONA_ID y NODO_SECTOR_ID.
// =============================================================
#define ACK_STATUS_SUCCESS   "SUCCESS"
#define ACK_STATUS_ERROR     "ERROR"
#define ACK_KEY_TIPO         "tipo"
#define ACK_KEY_DURATION     "durationSec"
#define ACK_KEY_TARGET_PCT   "targetPct"

#define ACT_NOMBRE_VALVE     "valve"
#define ACT_NOMBRE_PUMP      "pump"
#define ACT_NOMBRE_SHADE     "shade"
#define ACT_ACCION_ON        "ON"
#define ACT_ACCION_OFF       "OFF"
#define ACT_ACCION_SET       "SET"

// detalle.tipo del ACK
#define ACK_TIPO_OK                   "ok"
#define ACK_TIPO_SIN_CAMBIO           "sin_cambio"
#define ACK_TIPO_COMANDO_INVALIDO     "comando_invalido"
#define ACK_TIPO_DURACION_INVALIDA    "duracion_invalida"
#define ACK_TIPO_ACTUADOR_DESCONOCIDO "actuador_desconocido"
#define ACK_TIPO_FALLA_MECANICA       "falla_mecanica"
#define ACK_TIPO_REEMPLAZADO          "reemplazado"

// Duración máxima de apertura de la válvula (s). Espejo de CONTRATO_VALVULA_DURACION_MAX_SEG.
#define VALVULA_DURACION_MAX_SEG 1200

// Comando de zona "leer ahora"
#define ZONA_ACCION_LEER_AHORA "LEER_AHORA"
#define ZONA_KEY_MAC           "mac"
#define ZONA_KEY_SIGNAL        "signal"
#define ZONA_KEY_TIMESTAMP     "timestamp"
#define ZONA_KEY_METRICS       "metrics"

// ── Código interno del comando de actuador (int, no enum: ver nota de prototipos) ──
#define ACTR_NINGUNO     0
#define ACTR_VALVULA     1
#define ACTR_MEDIASOMBRA 2
#define ACTR_ON          1
#define ACTR_OFF         2
#define ACTR_SET         3

// ── Resultado de mover la mediasombra ─────────────────────────
#define MS_OK            0   // tocó el final de carrera pedido
#define MS_TIMEOUT       1   // 30 s sin tocarlo
#define MS_INTERRUMPIDO  2   // llegó otro comando por MQTT (el último gana)
#define MS_TIMEOUT_MS    30000

#define LARGO_ACK        256
#define LARGO_TOPIC      96
#define LARGO_TELEMETRIA 384

// =============================================================
//  PROTOTIPOS
//  Declarados a mano para no depender del generador de Arduino IDE.
//  Sólo tipos primitivos en las firmas (nada de enum/struct propios).
// =============================================================
void nema_despertar();
void nema_dormir();
int  nema_mover(long pasos);
int  nema_homing();
int  nema_ir_a(long posicion_objetivo);
void nema_rutina_captura();
void motorDC_parar();
void motorDC_adelante(int velocidad);
void motorDC_atras(int velocidad);
int  mediasombra_enrollar();
int  mediasombra_desenrollar();
void bomba_encender(int caudal);
void bomba_apagar();
void serial_atender();
void red_iniciar();
void red_mantener();
void red_atender();
bool red_conectar_mqtt();
bool red_publicar(const char* payload);
void red_publicar_pendiente();
bool red_hay_reemplazo();
void red_imprimir_estado();
const char* mqtt_estado_texto(int rc);
void led_actualizar();
void mqtt_callback(char* topic, byte* payload, unsigned int length);
void procesar_entrante();
int  riel_ejecutar(int accion, int posicion);
int  riel_posicion_logica();
void evento_armar(char* buf, size_t n, const char* commandId, const char* status,
                  int posicion, const char* codigo, const char* detalle);
bool id_es_reciente(const char* id);
void id_recordar(const char* id);
// Actuadores y "leer ahora" (add-secuencias-demo-expo)
void procesar_actuador();
void procesar_lectura();
void valvula_vigilar();
bool act_hay_reemplazo();
bool act_id_es_reciente(const char* id);
void act_id_recordar(const char* id);
void act_terminar(const char* id, const char* status, const char* tipo, int durationSec);
void ack_armar(char* buf, size_t n, const char* commandId, const char* status,
               const char* tipo, int durationSec);
bool act_publicar(const char* payload);
void act_publicar_pendiente();
void mqtt_callback_actuador(const char* payload, unsigned int length);
void mqtt_callback_zona(const char* payload, unsigned int length);
bool leer_sensores(JsonObject metricas);

// =============================================================
//  ESTADO DE RED Y DEL RIEL
// =============================================================
WiFiClient   wifi_cliente;
PubSubClient mqtt(wifi_cliente);

bool referenciado = false;   // true si el último homing salió bien (pasos_actuales es confiable)

bool red_iniciada = false;   // false hasta red_iniciar() (durante el homing del arranque)
bool wifi_estaba_conectado = false;
bool mqtt_estaba_conectado = false;
unsigned long wifi_ultimo_intento = 0;
unsigned long mqtt_ultimo_intento = 0;
unsigned long pendiente_ultimo_intento = 0;
char mqtt_client_id[48] = "";

// Slot "entrante": lo llena el callback MQTT y lo consume loop() (o el bucle
// de pasos, para ver si hay que abortar). El callback NO mueve ni publica.
bool entrante_hay = false;
bool entrante_valido = false;
int  entrante_accion = ACCION_NINGUNA;
int  entrante_posicion = 0;
char entrante_id[LARGO_ID] = "";
char entrante_detalle[LARGO_DETALLE] = "";

// Comando en ejecución ("" si está quieto o si el movimiento es por serie).
char actual_id[LARGO_ID] = "";

// Último comando terminado y su evento final (LLEGO / ERROR) ya serializado.
char ultimo_id[LARGO_ID] = "";
char ultimo_evento[LARGO_EVENTO] = "";
bool ultimo_pendiente = false;   // true si el evento final todavía no se pudo publicar

// Ring con los últimos commandId procesados (§1.3-3).
char recientes[CANT_RECIENTES][LARGO_ID];
int  recientes_proximo = 0;

unsigned long led_ultimo = 0;
bool led_encendido = false;

// ── Actuadores por MQTT (válvula y mediasombra) ───────────────
// Tópicos armados una vez en red_iniciar().
char topic_act_comando[LARGO_TOPIC] = "";
char topic_act_ack[LARGO_TOPIC] = "";
char topic_zona_comando[LARGO_TOPIC] = "";
char topic_zona_telemetria[LARGO_TOPIC] = "";

// Slot "act_entrante": lo llena el callback y lo consume loop() (o el bucle de la
// mediasombra, para ver si hay que abortar). El callback NO mueve ni publica.
bool act_hay = false;
bool act_valido = false;
int  act_actuador = ACTR_NINGUNO;
int  act_accion = ACTR_ON;
int  act_duracion = 0;       // durationSec de valve ON
int  act_target_pct = -1;    // targetPct de shade SET
char act_id[LARGO_ID] = "";
const char* act_tipo_error = ACK_TIPO_COMANDO_INVALIDO;   // sólo si !act_valido (apunta a un literal)

// Comando de actuador en ejecución ("" si está quieto o si el movimiento es por serie).
char act_actual_id[LARGO_ID] = "";

// Último comando de actuador terminado y su ACK ya serializado.
char act_ultimo_id[LARGO_ID] = "";
char act_ultimo_ack[LARGO_ACK] = "";
bool act_ack_pendiente = false;   // true si el ACK todavía no se pudo publicar
unsigned long act_pendiente_ultimo_intento = 0;

// Ring propio de commandId de actuadores (aparte del del riel).
char act_recientes[CANT_RECIENTES][LARGO_ID];
int  act_recientes_proximo = 0;

// Válvula (sobre el driver de la bomba): se cierra sola al vencer el plazo.
bool valvula_abierta = false;
unsigned long valvula_cierra_en = 0;

// ── "Leer ahora" ──────────────────────────────────────────────
bool leer_hay = false;
char leer_id[LARGO_ID] = "";

// =============================================================
//  SETUP
// =============================================================
void setup() {

  // NEMA 17
  pinMode(STEP_PIN, OUTPUT);
  pinMode(DIR_PIN, OUTPUT);
  pinMode(ENDSTOP_RIEL_HOME, INPUT);
  pinMode(ENDSTOP_RIEL_FIN,  INPUT);
  pinMode(SLEEP_PIN, OUTPUT);
  digitalWrite(SLEEP_PIN, LOW);

  // Motor DC mediasombra
  pinMode(MOTOR_IN1, OUTPUT);
  pinMode(MOTOR_IN2, OUTPUT);

  // Bomba
  pinMode(BOMBA_IN3, OUTPUT);
  pinMode(BOMBA_IN4, OUTPUT);

  // Endstops mediasombra (INPUT_PULLUP; lógica según ENDSTOP_MEDIASOMBRA_NC)
  pinMode(ENDSTOP_ENROLLADA,    INPUT_PULLUP);
  pinMode(ENDSTOP_DESENROLLADA, INPUT_PULLUP);

  // LED de estado de la red
  pinMode(LED_ESTADO, OUTPUT);
  digitalWrite(LED_ESTADO, LOW);

  // PWM con LEDC del ESP32 — API nueva (Arduino 3.x)
  // ledcAttach(pin, frecuencia, bits_resolución)
  ledcAttach(MOTOR_ENA, PWM_FREQ, PWM_BITS);
  ledcAttach(BOMBA_ENB, PWM_FREQ, PWM_BITS);

  // Asegurarse de que todo empieza apagado
  motorDC_parar();
  bomba_apagar();

  Serial.begin(115200);
  Serial.println("Iniciando sistema vivero (con red)...");

  // Homing del NEMA 17 al arrancar, antes de la red (como en vivero_esp32)
  Serial.println("Buscando posición home...");
  nema_homing();

  // Red: arranca sin esperar; loop() la completa y reconecta sola
  red_iniciar();
  Serial.println("Sistema listo. Escribí 'red' para ver el estado de la conexión.");
}

// =============================================================
//  LOOP
// =============================================================
void loop() {
  red_mantener();       // reconexión WiFi/MQTT (sólo acá, nunca a mitad de un movimiento)
  red_atender();        // mqtt.loop() + LED
  procesar_entrante();  // comando del riel recibido por MQTT, si hay
  procesar_actuador();  // comando de válvula/mediasombra recibido por MQTT, si hay
  procesar_lectura();   // "leer ahora" recibido por MQTT, si hay
  valvula_vigilar();    // red de seguridad: cierra la válvula al vencer durationSec
  serial_atender();     // comandos por monitor serie de siempre
}

// =============================================================
//  MONITOR SERIAL — comandos para pruebas (los mismos de vivero_esp32 + "red")
// =============================================================
void serial_atender() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();

    // ── NEMA 17 ──────────────────────────────────────────────
    if (cmd == "home") {
      nema_homing();

    } else if (cmd == "rutina") {
      nema_rutina_captura();

    } else if (cmd.startsWith("mover ")) {
      long pasos = cmd.substring(6).toInt();
      nema_mover(pasos);
      Serial.print("Posición actual: ");
      Serial.println(pasos_actuales);

    // ── Mediasombra ───────────────────────────────────────────
    } else if (cmd == "enrollar") {
      mediasombra_enrollar();

    } else if (cmd == "desenrollar") {
      mediasombra_desenrollar();

    } else if (cmd == "parar motor") {
      motorDC_parar();

    // ── Bomba ─────────────────────────────────────────────────
    } else if (cmd == "bomba on") {
      bomba_encender(200); // 200/255 = ~78% caudal

    } else if (cmd == "bomba off") {
      bomba_apagar();

    // ── Estado sensores ───────────────────────────────────────
    } else if (cmd == "estado") {
      Serial.print("Endstop enrollada:    ");
      Serial.println(MEDIASOMBRA_ACTIVADO(ENDSTOP_ENROLLADA) ? "ACTIVADO" : "libre");
      Serial.print("Endstop desenrollada: ");
      Serial.println(MEDIASOMBRA_ACTIVADO(ENDSTOP_DESENROLLADA) ? "ACTIVADO" : "libre");
      Serial.print("Endstop riel home:    ");
      Serial.println(RIEL_ACTIVADO(ENDSTOP_RIEL_HOME) ? "ACTIVADO" : "libre");
      Serial.print("Endstop riel fin:     ");
      Serial.println(RIEL_ACTIVADO(ENDSTOP_RIEL_FIN) ? "ACTIVADO" : "libre");
      Serial.print("Pasos actuales:       ");
      Serial.println(pasos_actuales);

    // ── Red ───────────────────────────────────────────────────
    } else if (cmd == "red") {
      red_imprimir_estado();

    } else {
      Serial.println("Comandos disponibles:");
      Serial.println("  home          → busca posición home NEMA");
      Serial.println("  mover N       → mueve N pasos (negativo = reversa)");
      Serial.println("  rutina        → home → sector 1 → espera 3s → sector 2");
      Serial.println("  enrollar      → enrolla mediasombra");
      Serial.println("  desenrollar   → desenrolla mediasombra");
      Serial.println("  parar motor   → para motor DC");
      Serial.println("  bomba on      → enciende bomba");
      Serial.println("  bomba off     → apaga bomba");
      Serial.println("  estado        → lee todos los sensores");
      Serial.println("  red           → estado de WiFi, MQTT y último comando del riel");
    }
  }
}

// =============================================================
//  FUNCIONES NEMA 17
// =============================================================

// Antes de mover → despertar
void nema_despertar() {
  digitalWrite(SLEEP_PIN, HIGH);
  delay(10);  // espera que estabilice
}

// Después de mover → dormir
void nema_dormir() {
  digitalWrite(SLEEP_PIN, LOW);
}

// Mueve N pasos. Positivo = adelante, negativo = atrás.
// Devuelve MOV_OK, MOV_FIN_HOME, MOV_FIN_OPUESTO o MOV_INTERRUMPIDO.
int nema_mover(long pasos) {
  if (pasos == 0) return MOV_OK;

  nema_despertar();

  bool direccion = (pasos > 0); // true = avance (hacia FIN), false = retroceso (hacia HOME)
  digitalWrite(DIR_PIN, direccion ? DIR_HACIA_FIN : DIR_HACIA_HOME);
  delay(1); // pequeña pausa para que el driver procese la dirección

  long abs_pasos = labs(pasos);
  for (long i = 0; i < abs_pasos; i++) {
    // Verifica finales de carrera en cada paso (seguridad)
    if (!direccion && RIEL_ACTIVADO(ENDSTOP_RIEL_HOME)) {
      Serial.println("Final de carrera home alcanzado, deteniendo.");
      pasos_actuales = 0;
      nema_dormir();
      return MOV_FIN_HOME;
    }
    if (direccion && RIEL_ACTIVADO(ENDSTOP_RIEL_FIN)) {
      pasos_actuales += i;  // solo los pasos que llegó a dar
      Serial.print("Final de carrera opuesto alcanzado, deteniendo tras ");
      Serial.print(i);
      Serial.print(" pasos. Posición: ");
      Serial.println(pasos_actuales);
      nema_dormir();
      return MOV_FIN_OPUESTO;
    }
    // Cada PASOS_ENTRE_RED pasos atiende MQTT (sin reconectar) y se fija
    // si llegó otro comando del riel: el último gana.
    if (i > 0 && (i % PASOS_ENTRE_RED) == 0) {
      red_atender();
      if (red_hay_reemplazo()) {
        pasos_actuales += direccion ? i : -i;  // solo los pasos que llegó a dar
        Serial.print("[riel] Movimiento interrumpido por otro comando. Posición: ");
        Serial.println(pasos_actuales);
        nema_dormir();
        return MOV_INTERRUMPIDO;
      }
    }
    digitalWrite(STEP_PIN, HIGH);
    delayMicroseconds(VELOCIDAD_NEMA);
    digitalWrite(STEP_PIN, LOW);
    delayMicroseconds(VELOCIDAD_NEMA);
  }
  nema_dormir();  // ← apaga el driver al terminar
  pasos_actuales += pasos;
  return MOV_OK;
}

// Busca el final de carrera home y resetea el contador.
// Devuelve MOV_OK si encontró home, MOV_SIN_HOME si no (timeout o final
// opuesto) y MOV_INTERRUMPIDO si llegó otro comando por MQTT.
// Actualiza "referenciado".
int nema_homing() {
  // Si ya está en home, listo
  if (RIEL_ACTIVADO(ENDSTOP_RIEL_HOME)) {
    pasos_actuales = 0;
    referenciado = true;
    Serial.println("Ya está en home.");
    return MOV_OK;
  }

  // Mientras busca home la posición no es confiable
  referenciado = false;

  // Mueve hacia atrás (retroceso) hasta encontrar el final de carrera home
  nema_despertar();
  digitalWrite(DIR_PIN, DIR_HACIA_HOME);
  delay(1);

  long timeout = MAX_PASOS_HOMING; // límite de seguridad antes de abortar
  long i = 0;
  // Si arranca apoyado en el endstop fin, es normal que siga activado
  // los primeros pasos mientras se aleja: solo cuenta si se libera y vuelve a activarse.
  bool fin_liberado = !RIEL_ACTIVADO(ENDSTOP_RIEL_FIN);
  while (!RIEL_ACTIVADO(ENDSTOP_RIEL_HOME) && i < timeout) {
    bool fin_activo = RIEL_ACTIVADO(ENDSTOP_RIEL_FIN);
    if (!fin_activo) {
      fin_liberado = true;
    } else if (fin_liberado) {
      // Llegó al extremo opuesto buscando home: dirección o cableado invertidos
      Serial.println("ERROR: Se activó el final de carrera opuesto durante el homing.");
      nema_dormir();
      return MOV_SIN_HOME;
    }
    // Cada PASOS_ENTRE_RED pasos atiende MQTT (sin reconectar)
    if (i > 0 && (i % PASOS_ENTRE_RED) == 0) {
      red_atender();
      if (red_hay_reemplazo()) {
        Serial.println("[riel] Homing interrumpido por otro comando.");
        nema_dormir();
        return MOV_INTERRUMPIDO;
      }
    }
    digitalWrite(STEP_PIN, HIGH);
    delayMicroseconds(VELOCIDAD_NEMA);
    digitalWrite(STEP_PIN, LOW);
    delayMicroseconds(VELOCIDAD_NEMA);
    i++;
  }

  nema_dormir();

  if (i >= timeout) {
    Serial.println("ERROR: Home no encontrado. Revisá el final de carrera.");
    return MOV_SIN_HOME;
  }

  pasos_actuales = 0;
  referenciado = true;
  Serial.println("Home encontrado. Posición reseteada a 0.");
  return MOV_OK;
}

// Mueve a una posición absoluta (en pasos desde home)
int nema_ir_a(long posicion_objetivo) {
  long pasos = posicion_objetivo - pasos_actuales;
  return nema_mover(pasos);
}

// Rutina: home → sector 1 → espera 3s → sector 2
void nema_rutina_captura() {
  Serial.println("Rutina: buscando home...");
  if (nema_homing() != MOV_OK) {
    Serial.println("Rutina abortada: no se pudo hacer home.");
    return;
  }

  Serial.println("Rutina: moviendo a sector 1...");
  int r = nema_ir_a(POSICION_SECTOR_1);
  Serial.print("Posición actual: ");
  Serial.println(pasos_actuales);
  if (r == MOV_INTERRUMPIDO) {
    Serial.println("Rutina abortada: llegó un comando del riel por MQTT.");
    return;
  }

  Serial.println("Rutina: esperando 3s...");
  delay(3000);

  Serial.println("Rutina: moviendo a sector 2...");
  r = nema_ir_a(POSICION_SECTOR_2);
  Serial.print("Posición actual: ");
  Serial.println(pasos_actuales);
  if (r == MOV_INTERRUMPIDO) {
    Serial.println("Rutina abortada: llegó un comando del riel por MQTT.");
    return;
  }
  delay(3000);

  Serial.println("Rutina finalizada.");
}

// =============================================================
//  FUNCIONES MOTOR DC (mediasombra)
// =============================================================

void motorDC_parar() {
  digitalWrite(MOTOR_IN1, LOW);
  digitalWrite(MOTOR_IN2, LOW);
  ledcWrite(MOTOR_ENA, 0);
}

void motorDC_adelante(int velocidad) {
  // velocidad: 0-255
  digitalWrite(MOTOR_IN1, HIGH);
  digitalWrite(MOTOR_IN2, LOW);
  ledcWrite(MOTOR_ENA, velocidad);
}

void motorDC_atras(int velocidad) {
  digitalWrite(MOTOR_IN1, LOW);
  digitalWrite(MOTOR_IN2, HIGH);
  ledcWrite(MOTOR_ENA, velocidad);
}

// Devuelven MS_OK (tocó el final de carrera, o ya estaba ahí), MS_TIMEOUT o
// MS_INTERRUMPIDO (llegó por MQTT otro comando de actuador: el último gana).
// Los comandos por serie las llaman igual e ignoran el resultado.
int mediasombra_enrollar() {
  if (MEDIASOMBRA_ACTIVADO(ENDSTOP_ENROLLADA)) {
    Serial.println("Mediasombra ya enrollada.");
    return MS_OK;
  }
  Serial.println("Enrollando mediasombra...");
  motorDC_atras(200); // 78% velocidad

  // Espera hasta que el endstop se active o timeout
  unsigned long inicio = millis();
  while (!MEDIASOMBRA_ACTIVADO(ENDSTOP_ENROLLADA)) {
    if (millis() - inicio > MS_TIMEOUT_MS) { // 30 segundos máximo
      Serial.println("TIMEOUT: Revisá el endstop de enrollado.");
      motorDC_parar();
      return MS_TIMEOUT;
    }
    red_atender();  // mantiene viva la conexión MQTT (30 s > keepalive)
    if (act_hay_reemplazo()) {
      motorDC_parar();
      Serial.println("[act] Enrollado interrumpido por otro comando.");
      return MS_INTERRUMPIDO;
    }
    delay(10);
  }
  motorDC_parar();
  Serial.println("Mediasombra enrollada.");
  return MS_OK;
}

int mediasombra_desenrollar() {
  if (MEDIASOMBRA_ACTIVADO(ENDSTOP_DESENROLLADA)) {
    Serial.println("Mediasombra ya desenrollada.");
    return MS_OK;
  }
  Serial.println("Desenrollando mediasombra...");
  motorDC_adelante(200); // 78% velocidad

  unsigned long inicio = millis();
  while (!MEDIASOMBRA_ACTIVADO(ENDSTOP_DESENROLLADA)) {
    if (millis() - inicio > MS_TIMEOUT_MS) {
      Serial.println("TIMEOUT: Revisá el endstop de desenrollado.");
      motorDC_parar();
      return MS_TIMEOUT;
    }
    red_atender();  // mantiene viva la conexión MQTT (30 s > keepalive)
    if (act_hay_reemplazo()) {
      motorDC_parar();
      Serial.println("[act] Desenrollado interrumpido por otro comando.");
      return MS_INTERRUMPIDO;
    }
    delay(10);
  }
  motorDC_parar();
  Serial.println("Mediasombra desenrollada.");
  return MS_OK;
}

// =============================================================
//  FUNCIONES BOMBA
// =============================================================

void bomba_encender(int caudal) {
  // caudal: 0-255
  digitalWrite(BOMBA_IN3, HIGH);
  digitalWrite(BOMBA_IN4, LOW);
  ledcWrite(BOMBA_ENB, caudal);
  Serial.print("Bomba encendida al ");
  Serial.print(map(caudal, 0, 255, 0, 100));
  Serial.println("%");
}

void bomba_apagar() {
  digitalWrite(BOMBA_IN3, LOW);
  digitalWrite(BOMBA_IN4, LOW);
  ledcWrite(BOMBA_ENB, 0);
  valvula_abierta = false;
  Serial.println("Bomba apagada.");
}

// =============================================================
//  RED — WiFi y MQTT
// =============================================================

// Configura WiFi y MQTT sin esperar a que conecten.
void red_iniciar() {
  // clientId único: base + MAC de la placa (estable entre reinicios)
  uint64_t chip = ESP.getEfuseMac();
  snprintf(mqtt_client_id, sizeof(mqtt_client_id), "%s-%04X%08lX",
           MQTT_CLIENT_ID_BASE,
           (unsigned int) ((chip >> 32) & 0xFFFF),
           (unsigned long) (chip & 0xFFFFFFFFUL));

  // Tópicos del sector y de la zona (espejo de contrato.h)
  snprintf(topic_act_comando, sizeof(topic_act_comando),
           "nursery/zone/%s/sector/%s/command", NODO_ZONA_ID, NODO_SECTOR_ID);
  snprintf(topic_act_ack, sizeof(topic_act_ack),
           "nursery/zone/%s/sector/%s/ack", NODO_ZONA_ID, NODO_SECTOR_ID);
  snprintf(topic_zona_comando, sizeof(topic_zona_comando),
           "nursery/zone/%s/command", NODO_ZONA_ID);
  snprintf(topic_zona_telemetria, sizeof(topic_zona_telemetria),
           "nursery/zone/%s/telemetry", NODO_ZONA_ID);

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  wifi_ultimo_intento = millis();
  Serial.print("[wifi] Conectando a \"");
  Serial.print(WIFI_SSID);
  Serial.println("\"...");

  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  if (!mqtt.setBufferSize(RED_MQTT_BUFFER)) {
    Serial.println("[mqtt] ERROR: no se pudo reservar el buffer de MQTT");
  }
  mqtt.setKeepAlive(RED_MQTT_KEEPALIVE_S);
  mqtt.setSocketTimeout(RED_MQTT_SOCKET_TIMEOUT_S);
  mqtt.setCallback(mqtt_callback);

  red_iniciada = true;
}

// Reconexión no bloqueante. Se llama SOLO desde loop() (con el riel quieto):
// mqtt.connect() puede bloquear algunos segundos.
void red_mantener() {
  if (!red_iniciada) return;

  unsigned long ahora = millis();

  // ── WiFi ──────────────────────────────────────────────────
  bool wifi_ok = (WiFi.status() == WL_CONNECTED);
  if (wifi_ok && !wifi_estaba_conectado) {
    Serial.print("[wifi] Conectado. IP: ");
    Serial.print(WiFi.localIP());
    Serial.print("  RSSI: ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
  }
  if (!wifi_ok && wifi_estaba_conectado) {
    Serial.println("[wifi] Se perdió la conexión; reintentando...");
    wifi_ultimo_intento = ahora;
  }
  wifi_estaba_conectado = wifi_ok;

  if (!wifi_ok) {
    if (mqtt_estaba_conectado) {
      Serial.println("[mqtt] Desconectado (sin WiFi)");
      mqtt_estaba_conectado = false;
    }
    // setAutoReconnect suele alcanzar; si no vuelve, se reinicia el intento.
    if (ahora - wifi_ultimo_intento >= RED_WIFI_REINTENTO_MS) {
      wifi_ultimo_intento = ahora;
      Serial.print("[wifi] Sin conexión (estado ");
      Serial.print((int) WiFi.status());
      Serial.println("). Reintento. ¿SSID y clave bien? ¿Red de 2,4 GHz?");
      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    }
    return;
  }

  // ── MQTT ──────────────────────────────────────────────────
  bool mqtt_ok = mqtt.connected();
  if (!mqtt_ok && mqtt_estaba_conectado) {
    Serial.print("[mqtt] Desconectado: ");
    Serial.println(mqtt_estado_texto(mqtt.state()));
  }
  mqtt_estaba_conectado = mqtt_ok;

  if (!mqtt_ok) {
    if (ahora - mqtt_ultimo_intento < RED_MQTT_REINTENTO_MS) return;
    if (!red_conectar_mqtt()) {
      mqtt_ultimo_intento = millis();
      return;
    }
    mqtt_estaba_conectado = true;
    red_publicar_pendiente();   // evento final que quedó sin publicar
    act_publicar_pendiente();   // ACK de actuador que quedó sin publicar
    return;
  }

  // Conectado: si quedó un evento final sin publicar, reintenta cada tanto
  if (ultimo_pendiente && (ahora - pendiente_ultimo_intento >= RED_MQTT_REINTENTO_MS)) {
    red_publicar_pendiente();
  }
  if (act_ack_pendiente && (ahora - act_pendiente_ultimo_intento >= RED_MQTT_REINTENTO_MS)) {
    act_publicar_pendiente();
  }
}

// Conecta al broker y se suscribe al comando del riel. Bloquea hasta unos segundos.
bool red_conectar_mqtt() {
  Serial.print("[mqtt] Conectando a ");
  Serial.print(MQTT_HOST);
  Serial.print(":");
  Serial.print(MQTT_PORT);
  Serial.print(" como ");
  Serial.print(mqtt_client_id);
  Serial.println("...");

  if (!mqtt.connect(mqtt_client_id)) {
    Serial.print("[mqtt] Falló: ");
    Serial.print(mqtt_estado_texto(mqtt.state()));
    Serial.println(". Reintento en 3 s");
    return false;
  }
  Serial.println("[mqtt] Conectado al broker");

  if (!mqtt.subscribe(RIEL_TOPIC_COMANDO, RIEL_QOS_SUSCRIPCION)) {
    Serial.println("[mqtt] ERROR: no se pudo suscribir a " RIEL_TOPIC_COMANDO "; desconecto y reintento");
    mqtt.disconnect();
    return false;
  }
  Serial.println("[mqtt] Suscripto a " RIEL_TOPIC_COMANDO " (QoS 1)");

  // Comando del sector (válvula y mediasombra) y comando de la zona ("leer ahora")
  if (!mqtt.subscribe(topic_act_comando, RIEL_QOS_SUSCRIPCION) ||
      !mqtt.subscribe(topic_zona_comando, RIEL_QOS_SUSCRIPCION)) {
    Serial.println("[mqtt] ERROR: no se pudo suscribir a los comandos del sector/zona; desconecto y reintento");
    mqtt.disconnect();
    return false;
  }
  Serial.print("[mqtt] Suscripto a ");
  Serial.print(topic_act_comando);
  Serial.print(" y ");
  Serial.print(topic_zona_comando);
  Serial.println(" (QoS 1)");
  return true;
}

// Atención mínima de la red: se puede llamar desde el bucle de pasos.
// NUNCA reconecta (ni WiFi ni MQTT): sólo mqtt.loop() y el LED.
void red_atender() {
  if (red_iniciada) {
    mqtt.loop();   // keepalive + recepción (si llega algo, corre mqtt_callback)
  }
  valvula_vigilar();   // la bomba no se queda abierta aunque el riel esté en movimiento
  led_actualizar();
}

// Publica en nursery/rail/event (QoS 0: PubSubClient no publica con QoS 1).
// No reconecta: si no hay conexión, devuelve false.
bool red_publicar(const char* payload) {
  if (!red_iniciada || !mqtt.connected()) {
    return false;
  }
  bool ok = mqtt.publish(RIEL_TOPIC_EVENTO, payload);
  if (ok) {
    Serial.print("[mqtt] Evento publicado: ");
  } else {
    Serial.print("[mqtt] ERROR al publicar el evento: ");
  }
  Serial.println(payload);
  return ok;
}

// Publica el evento final que quedó pendiente (si hay).
void red_publicar_pendiente() {
  if (!ultimo_pendiente) return;
  pendiente_ultimo_intento = millis();
  Serial.println("[mqtt] Publicando el evento final pendiente...");
  if (red_publicar(ultimo_evento)) {
    ultimo_pendiente = false;
  }
}

// ¿Hay que abortar el movimiento en curso? Se llama desde el bucle de pasos
// después de red_atender(). Sólo un comando VÁLIDO con un commandId nuevo
// (ni el que se está ejecutando, ni uno reciente) interrumpe: el último gana.
// No consume el slot: lo ejecuta loop() al terminar el movimiento.
bool red_hay_reemplazo() {
  if (!entrante_hay) return false;

  // §1.3-1: el mismo comando que se está ejecutando (redelivery) → se descarta
  if (entrante_id[0] != '\0' && strcmp(entrante_id, actual_id) == 0) {
    Serial.print("[cmd] ");
    Serial.print(entrante_id);
    Serial.println(" repetido (en ejecución): lo ignoro");
    entrante_hay = false;
    return false;
  }
  // Inválido o repetido de antes: no detiene el riel; se responde al terminar
  if (!entrante_valido) return false;
  if (strcmp(entrante_id, ultimo_id) == 0 || id_es_reciente(entrante_id)) return false;

  Serial.print("[cmd] Llegó ");
  Serial.print(entrante_id);
  Serial.println(" durante el movimiento: aborto el actual (el último gana)");
  return true;
}

// Texto legible de mqtt.state()
const char* mqtt_estado_texto(int rc) {
  switch (rc) {
    case -4: return "el broker no respondió a tiempo (-4)";
    case -3: return "conexión perdida (-3)";
    case -2: return "no se pudo abrir la conexión TCP: revisar MQTT_HOST, puerto 1883 y firewall (-2)";
    case -1: return "desconectado (-1)";
    case  0: return "conectado (0)";
    case  1: return "protocolo rechazado por el broker (1)";
    case  2: return "clientId rechazado por el broker (2)";
    case  3: return "broker no disponible (3)";
    case  4: return "usuario/clave rechazados (4)";
    case  5: return "no autorizado (5)";
    default: return "estado desconocido";
  }
}

void red_imprimir_estado() {
  if (!red_iniciada) {
    Serial.println("[red] Todavía no se inició la red.");
    return;
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("[red] WiFi conectado a \"");
    Serial.print(WIFI_SSID);
    Serial.print("\". IP: ");
    Serial.print(WiFi.localIP());
    Serial.print("  RSSI: ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
  } else {
    Serial.print("[red] WiFi desconectado (estado ");
    Serial.print((int) WiFi.status());
    Serial.println(")");
  }
  Serial.print("[red] Broker ");
  Serial.print(MQTT_HOST);
  Serial.print(":");
  Serial.print(MQTT_PORT);
  Serial.print(" como ");
  Serial.println(mqtt_client_id);
  Serial.print("[red] MQTT: ");
  Serial.println(mqtt_estado_texto(mqtt.state()));
  Serial.print("[red] Último commandId: ");
  Serial.println(ultimo_id[0] != '\0' ? ultimo_id : "(ninguno)");
  Serial.print("[red] Evento final pendiente de publicar: ");
  Serial.println(ultimo_pendiente ? "sí" : "no");
  Serial.print("[riel] Referenciado: ");
  Serial.print(referenciado ? "sí" : "no");
  Serial.print("  Pasos actuales: ");
  Serial.println(pasos_actuales);
}

// LED: rápido (100 ms) sin WiFi, lento (500 ms) con WiFi sin broker, fijo con broker
void led_actualizar() {
  unsigned long periodo;
  if (!red_iniciada || WiFi.status() != WL_CONNECTED) {
    periodo = 100;
  } else if (!mqtt.connected()) {
    periodo = 500;
  } else {
    if (!led_encendido) {
      led_encendido = true;
      digitalWrite(LED_ESTADO, HIGH);
    }
    return;
  }
  unsigned long ahora = millis();
  if (ahora - led_ultimo >= periodo) {
    led_ultimo = ahora;
    led_encendido = !led_encendido;
    digitalWrite(LED_ESTADO, led_encendido ? HIGH : LOW);
  }
}

// =============================================================
//  COMANDOS DEL RIEL POR MQTT
// =============================================================

// Callback de PubSubClient. SOLO valida y copia al slot "entrante":
// no mueve el motor y NO publica (PubSubClient reusa su buffer al publicar
// y corrompería el mensaje que se está leyendo).
void mqtt_callback(char* topic, byte* payload, unsigned int length) {
  // Despacha por tópico a slots separados; los tres sólo copian, nunca publican.
  if (strcmp(topic, topic_act_comando) == 0) {
    mqtt_callback_actuador((const char*) payload, length);
    return;
  }
  if (strcmp(topic, topic_zona_comando) == 0) {
    mqtt_callback_zona((const char*) payload, length);
    return;
  }
  if (strcmp(topic, RIEL_TOPIC_COMANDO) != 0) return;

  // Pisa lo que hubiera en el slot: el último comando gana
  entrante_valido = false;
  entrante_accion = ACCION_NINGUNA;
  entrante_posicion = 0;
  entrante_id[0] = '\0';
  entrante_detalle[0] = '\0';

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, (const char*) payload, (size_t) length);
  if (err) {
    snprintf(entrante_detalle, sizeof(entrante_detalle), "JSON ilegible: %s", err.c_str());
    entrante_hay = true;
    Serial.print("[cmd] Recibido comando con ");
    Serial.println(entrante_detalle);
    return;
  }

  const char* id       = doc[RIEL_KEY_COMMAND_ID] | "";
  const char* actuador = doc[RIEL_KEY_ACTUADOR] | "";
  const char* accion   = doc[RIEL_KEY_ACCION] | "";
  int posicion         = doc[RIEL_KEY_PARAMETROS][RIEL_KEY_POSICION] | -1;

  size_t largo_id = strlen(id);
  if (largo_id == 0 || largo_id >= LARGO_ID) {
    strlcpy(entrante_detalle, "commandId ausente o de mas de 39 caracteres", sizeof(entrante_detalle));
  } else {
    strlcpy(entrante_id, id, sizeof(entrante_id));
    if (strcmp(actuador, RIEL_ACTUADOR) != 0) {
      strlcpy(entrante_detalle, "actuador distinto de rail", sizeof(entrante_detalle));
    } else if (strcmp(accion, RIEL_ACCION_HOME) == 0) {
      entrante_accion = ACCION_HOME;
      entrante_valido = true;
    } else if (strcmp(accion, RIEL_ACCION_IR_A) == 0) {
      if (posicion == 1 || posicion == 2) {
        entrante_accion = ACCION_IR_A;
        entrante_posicion = posicion;
        entrante_valido = true;
      } else {
        strlcpy(entrante_detalle, "parametros.posicion tiene que ser 1 o 2", sizeof(entrante_detalle));
      }
    } else {
      strlcpy(entrante_detalle, "accion desconocida (se espera IR_A o HOME)", sizeof(entrante_detalle));
    }
  }
  entrante_hay = true;

  Serial.print("[cmd] Recibido ");
  Serial.print(entrante_id[0] != '\0' ? entrante_id : "(sin commandId)");
  Serial.print(": ");
  if (entrante_valido) {
    Serial.print(entrante_accion == ACCION_HOME ? "HOME" : "IR_A ");
    if (entrante_accion == ACCION_IR_A) Serial.print(entrante_posicion);
    Serial.println();
  } else {
    Serial.print("inválido (");
    Serial.print(entrante_detalle);
    Serial.println(")");
  }
}

// Consume el slot "entrante" (desde loop(), con el riel quieto).
// Idempotencia (design §1.3):
//   1. mismo commandId que el en ejecución → se ignora (lo resuelve red_hay_reemplazo)
//   2. mismo commandId que el último terminado → se republica su evento final
//   3. commandId entre los últimos 4 → se ignora
//   4. otro commandId durante un movimiento → el viejo termina en ERROR REEMPLAZADO
//      y este slot se ejecuta en la vuelta siguiente de loop()
void procesar_entrante() {
  if (!entrante_hay) return;

  // Copia local: mientras el riel se mueve, el callback puede pisar el slot
  char id[LARGO_ID];
  char detalle[LARGO_DETALLE];
  strlcpy(id, entrante_id, sizeof(id));
  strlcpy(detalle, entrante_detalle, sizeof(detalle));
  bool valido = entrante_valido;
  int accion = entrante_accion;
  int posicion = entrante_posicion;
  entrante_hay = false;

  char buf[LARGO_EVENTO];

  // §1.3-2: repetido del último terminado → republica su evento final
  if (id[0] != '\0' && strcmp(id, ultimo_id) == 0) {
    Serial.print("[cmd] ");
    Serial.print(id);
    Serial.println(" repetido (último terminado): republico su evento final, sin moverme");
    if (red_publicar(ultimo_evento)) {
      ultimo_pendiente = false;
    }
    return;
  }

  // §1.3-3: visto entre los últimos 4 → se ignora
  if (id[0] != '\0' && id_es_reciente(id)) {
    Serial.print("[cmd] ");
    Serial.print(id);
    Serial.println(" ya procesado antes: lo ignoro");
    return;
  }

  // Inválido → ERROR COMANDO_INVALIDO (sin moverse)
  if (!valido) {
    evento_armar(buf, sizeof(buf), id, RIEL_STATUS_ERROR, riel_posicion_logica(),
                 RIEL_COD_COMANDO_INVALIDO, detalle);
    red_publicar(buf);
    id_recordar(id);
    return;
  }

  // Válido → ACEPTADO, ejecutar, evento final
  id_recordar(id);
  strlcpy(actual_id, id, sizeof(actual_id));
  evento_armar(buf, sizeof(buf), id, RIEL_STATUS_ACEPTADO, -1, NULL, NULL);
  red_publicar(buf);   // ACEPTADO no se reintenta

  int r = riel_ejecutar(accion, posicion);

  const char* status = RIEL_STATUS_ERROR;
  const char* codigo = NULL;
  const char* det = NULL;
  if (r == MOV_OK) {
    status = RIEL_STATUS_LLEGO;
  } else if (r == MOV_SIN_HOME) {
    codigo = RIEL_COD_HOME_NO_ENCONTRADO;
    det = "No se encontro el final de carrera de home (timeout o final opuesto)";
  } else if (r == MOV_FIN_OPUESTO) {
    codigo = RIEL_COD_FIN_DE_CARRERA;
    det = "Se activo el final de carrera opuesto antes de llegar";
  } else if (r == MOV_FIN_HOME) {
    codigo = RIEL_COD_FIN_DE_CARRERA;
    det = "Se activo el final de carrera de home antes de llegar";
  } else {  // MOV_INTERRUMPIDO
    codigo = RIEL_COD_REEMPLAZADO;
    det = "Llego otro comando durante el movimiento";
  }

  evento_armar(ultimo_evento, sizeof(ultimo_evento), id, status, riel_posicion_logica(), codigo, det);
  strlcpy(ultimo_id, id, sizeof(ultimo_id));
  actual_id[0] = '\0';
  ultimo_pendiente = true;
  pendiente_ultimo_intento = millis();
  if (red_publicar(ultimo_evento)) {
    ultimo_pendiente = false;
  } else {
    Serial.println("[mqtt] Sin conexión: el evento final se publica al reconectar");
  }
}

// Ejecuta un comando válido (bloqueante cooperativo). Devuelve un MOV_*.
int riel_ejecutar(int accion, int posicion) {
  if (accion == ACCION_HOME) {
    Serial.println("[riel] HOME: buscando home...");
    return nema_homing();
  }

  // IR_A: sin referencia, primero homing
  if (!referenciado) {
    Serial.println("[riel] Sin referencia: hago homing antes de ir a la posición");
    int rh = nema_homing();
    if (rh != MOV_OK) return rh;   // MOV_SIN_HOME o MOV_INTERRUMPIDO
  }

  long objetivo = (posicion == 1) ? POSICION_SECTOR_1 : POSICION_SECTOR_2;
  Serial.print("[riel] IR_A ");
  Serial.print(posicion);
  Serial.print(": de ");
  Serial.print(pasos_actuales);
  Serial.print(" a ");
  Serial.print(objetivo);
  Serial.println(" pasos");

  int r = nema_ir_a(objetivo);
  if (r == MOV_FIN_OPUESTO) {
    // Tocó el fin antes de lo esperado: la cuenta de pasos no es confiable
    referenciado = false;
  }
  if (r == MOV_OK) {
    Serial.print("[riel] Llegó a la posición ");
    Serial.println(posicion);
  }
  return r;
}

// Posición lógica para el evento: 0 home, 1 y 2 los sectores, -1 = null
// (entre posiciones o sin referencia).
int riel_posicion_logica() {
  if (!referenciado) return -1;
  if (pasos_actuales == 0) return 0;
  if (pasos_actuales == POSICION_SECTOR_1) return 1;
  if (pasos_actuales == POSICION_SECTOR_2) return 2;
  return -1;
}

// Arma el JSON del evento en buf. posicion < 0 → null. codigo/detalle NULL → se omiten.
void evento_armar(char* buf, size_t n, const char* commandId, const char* status,
                  int posicion, const char* codigo, const char* detalle) {
  JsonDocument doc;
  doc[RIEL_KEY_COMMAND_ID] = commandId;
  doc[RIEL_KEY_STATUS] = status;
  if (posicion < 0) {
    doc[RIEL_KEY_POSICION] = nullptr;
  } else {
    doc[RIEL_KEY_POSICION] = posicion;
  }
  doc[RIEL_KEY_PASOS] = pasos_actuales;
  if (codigo != NULL) {
    doc[RIEL_KEY_CODIGO] = codigo;
  }
  if (detalle != NULL) {
    doc[RIEL_KEY_DETALLE] = detalle;
  }
  serializeJson(doc, buf, n);
}

bool id_es_reciente(const char* id) {
  if (id == NULL || id[0] == '\0') return false;
  for (int k = 0; k < CANT_RECIENTES; k++) {
    if (strcmp(recientes[k], id) == 0) return true;
  }
  return false;
}

void id_recordar(const char* id) {
  if (id == NULL || id[0] == '\0') return;
  strlcpy(recientes[recientes_proximo], id, LARGO_ID);
  recientes_proximo = (recientes_proximo + 1) % CANT_RECIENTES;
}

// =============================================================
//  ACTUADORES POR MQTT — válvula (sobre el driver de la bomba) y mediasombra
//  (add-secuencias-demo-expo, design §4). Mismo esquema que el riel: el callback
//  copia al slot "act_*", loop() lo consume y publica el ACK fuera del callback.
// =============================================================

// Parsea y valida un comando de actuador. SOLO copia al slot: no mueve ni publica.
// El último comando gana: pisa lo que hubiera en el slot.
void mqtt_callback_actuador(const char* payload, unsigned int length) {
  act_valido = false;
  act_actuador = ACTR_NINGUNO;
  act_accion = ACTR_ON;
  act_duracion = 0;
  act_target_pct = -1;
  act_id[0] = '\0';
  act_tipo_error = ACK_TIPO_COMANDO_INVALIDO;

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload, (size_t) length);
  if (err) {
    act_hay = true;
    Serial.print("[act] Recibido comando con JSON ilegible: ");
    Serial.println(err.c_str());
    return;
  }

  const char* id       = doc[RIEL_KEY_COMMAND_ID] | "";
  const char* actuador = doc[RIEL_KEY_ACTUADOR] | "";
  const char* accion   = doc[RIEL_KEY_ACCION] | "";
  int duracion         = doc[RIEL_KEY_PARAMETROS][ACK_KEY_DURATION] | -1;
  int target           = doc[RIEL_KEY_PARAMETROS][ACK_KEY_TARGET_PCT] | -1;

  size_t largo_id = strlen(id);
  if (largo_id > 0 && largo_id < LARGO_ID) {
    strlcpy(act_id, id, sizeof(act_id));
  }

  if (strcmp(actuador, ACT_NOMBRE_VALVE) == 0) {
    act_actuador = ACTR_VALVULA;
    if (strcmp(accion, ACT_ACCION_ON) == 0) {
      act_accion = ACTR_ON;
      if (duracion >= 1 && duracion <= VALVULA_DURACION_MAX_SEG) {
        act_duracion = duracion;
        act_valido = true;
      } else {
        act_tipo_error = ACK_TIPO_DURACION_INVALIDA;
      }
    } else if (strcmp(accion, ACT_ACCION_OFF) == 0) {
      act_accion = ACTR_OFF;
      act_valido = true;
    }
  } else if (strcmp(actuador, ACT_NOMBRE_SHADE) == 0) {
    act_actuador = ACTR_MEDIASOMBRA;
    act_accion = ACTR_SET;
    // Hardware binario: sólo 0 (desplegada) o 100 (enrollada)
    if (strcmp(accion, ACT_ACCION_SET) == 0 && (target == 0 || target == 100)) {
      act_target_pct = target;
      act_valido = true;
    }
  } else {
    // pump (los insumos no están en este nodo), rail u otro
    act_tipo_error = ACK_TIPO_ACTUADOR_DESCONOCIDO;
  }
  act_hay = true;

  Serial.print("[act] Recibido ");
  Serial.print(act_id[0] != '\0' ? act_id : "(sin commandId)");
  Serial.print(": ");
  Serial.print(actuador);
  Serial.print(" ");
  Serial.print(accion);
  Serial.println(act_valido ? "" : " (inválido)");
}

// ¿Hay que abortar el movimiento de la mediasombra en curso? Mismo criterio que
// red_hay_reemplazo() del riel: sólo un comando VÁLIDO con un commandId nuevo
// interrumpe (el último gana). No consume el slot: lo ejecuta loop() al terminar.
// Sólo aplica a movimientos comandados por MQTT (act_actual_id no vacío): los de serie no se abortan.
bool act_hay_reemplazo() {
  if (!act_hay) return false;
  if (act_actual_id[0] == '\0') return false;

  // El mismo comando que se está ejecutando (redelivery) → se descarta
  if (act_id[0] != '\0' && strcmp(act_id, act_actual_id) == 0) {
    Serial.print("[act] ");
    Serial.print(act_id);
    Serial.println(" repetido (en ejecución): lo ignoro");
    act_hay = false;
    return false;
  }
  // Inválido o repetido de antes: no detiene el motor; se responde al terminar
  if (!act_valido) return false;
  if (strcmp(act_id, act_ultimo_id) == 0 || act_id_es_reciente(act_id)) return false;

  Serial.print("[act] Llegó ");
  Serial.print(act_id);
  Serial.println(" durante el movimiento: aborto el actual (el último gana)");
  return true;
}

// Consume el slot "act_*" (desde loop()). Idempotencia:
//   1. mismo commandId que el en ejecución → se ignora (lo resuelve act_hay_reemplazo)
//   2. mismo commandId que el último terminado → se republica su ACK
//   3. commandId entre los últimos 4 → se ignora
//   4. otro commandId durante un movimiento → el viejo termina en ERROR reemplazado
//      y este slot se ejecuta en la vuelta siguiente de loop()
void procesar_actuador() {
  if (!act_hay) return;

  // Copia local: mientras la mediasombra se mueve, el callback puede pisar el slot
  char id[LARGO_ID];
  strlcpy(id, act_id, sizeof(id));
  bool valido = act_valido;
  int actuador = act_actuador;
  int accion = act_accion;
  int duracion = act_duracion;
  int target = act_target_pct;
  const char* tipo_error = act_tipo_error;
  act_hay = false;

  if (id[0] == '\0') {
    Serial.println("[act] Comando sin commandId válido: no hay a quién responder, lo descarto");
    return;
  }

  // Repetido del último terminado → republica su ACK
  if (strcmp(id, act_ultimo_id) == 0) {
    Serial.print("[act] ");
    Serial.print(id);
    Serial.println(" repetido (último terminado): republico su ACK, sin moverme");
    if (act_publicar(act_ultimo_ack)) {
      act_ack_pendiente = false;
    }
    return;
  }

  // Visto entre los últimos 4 → se ignora
  if (act_id_es_reciente(id)) {
    Serial.print("[act] ");
    Serial.print(id);
    Serial.println(" ya procesado antes: lo ignoro");
    return;
  }

  // Inválido → ACK ERROR con el tipo que corresponda (sin moverse)
  if (!valido) {
    act_terminar(id, ACK_STATUS_ERROR, tipo_error, 0);
    return;
  }

  // ── Válvula ──────────────────────────────────────────────
  if (actuador == ACTR_VALVULA) {
    if (accion == ACTR_ON) {
      // Si ya estaba abierta, renueva el plazo
      bomba_encender(BOMBA_CAUDAL_PWM);
      valvula_abierta = true;
      valvula_cierra_en = millis() + (unsigned long) duracion * 1000UL;
      act_terminar(id, ACK_STATUS_SUCCESS, ACK_TIPO_OK, duracion);
    } else {  // OFF
      bool estaba_abierta = valvula_abierta;
      bomba_apagar();   // siempre apaga, por las dudas
      act_terminar(id, ACK_STATUS_SUCCESS, estaba_abierta ? ACK_TIPO_OK : ACK_TIPO_SIN_CAMBIO, 0);
    }
    return;
  }

  // ── Mediasombra: 0 = desplegada, 100 = enrollada ─────────
  bool ya_en_el_final = (target == 0) ? MEDIASOMBRA_ACTIVADO(ENDSTOP_DESENROLLADA)
                                      : MEDIASOMBRA_ACTIVADO(ENDSTOP_ENROLLADA);
  if (ya_en_el_final) {
    Serial.println("[act] Mediasombra ya está en el final de carrera pedido");
    act_terminar(id, ACK_STATUS_SUCCESS, ACK_TIPO_SIN_CAMBIO, 0);
    return;
  }

  strlcpy(act_actual_id, id, sizeof(act_actual_id));
  int r = (target == 0) ? mediasombra_desenrollar() : mediasombra_enrollar();
  act_actual_id[0] = '\0';

  if (r == MS_OK) {
    act_terminar(id, ACK_STATUS_SUCCESS, ACK_TIPO_OK, 0);
  } else if (r == MS_TIMEOUT) {
    act_terminar(id, ACK_STATUS_ERROR, ACK_TIPO_FALLA_MECANICA, 0);
  } else {  // MS_INTERRUMPIDO
    act_terminar(id, ACK_STATUS_ERROR, ACK_TIPO_REEMPLAZADO, 0);
  }
}

// Red de seguridad de la válvula: si venció el plazo de durationSec sin que llegara el OFF,
// cierra sola y no publica nada (el backend ya cierra con su propio OFF). Se llama desde
// loop() y desde red_atender() para cubrir también los movimientos largos.
void valvula_vigilar() {
  if (!valvula_abierta) return;
  if ((long) (millis() - valvula_cierra_en) < 0) return;
  Serial.println("[valvula] Venció durationSec: cierro la válvula (red de seguridad)");
  bomba_apagar();
}

// Arma el ACK en act_ultimo_ack, lo recuerda como último terminado e intenta publicarlo.
// Se llama sólo desde loop() (nunca desde el callback). Sin conexión queda pendiente.
void act_terminar(const char* id, const char* status, const char* tipo, int durationSec) {
  ack_armar(act_ultimo_ack, sizeof(act_ultimo_ack), id, status, tipo, durationSec);
  strlcpy(act_ultimo_id, id, sizeof(act_ultimo_id));
  act_id_recordar(id);
  act_ack_pendiente = true;
  act_pendiente_ultimo_intento = millis();
  if (act_publicar(act_ultimo_ack)) {
    act_ack_pendiente = false;
  } else {
    Serial.println("[mqtt] Sin conexión: el ACK se publica al reconectar");
  }
}

// ACK: {"commandId","status","detalle":{"tipo"[,"durationSec"]}}. durationSec <= 0 → se omite.
void ack_armar(char* buf, size_t n, const char* commandId, const char* status,
               const char* tipo, int durationSec) {
  JsonDocument doc;
  doc[RIEL_KEY_COMMAND_ID] = commandId;
  doc[RIEL_KEY_STATUS] = status;
  JsonObject detalle = doc[RIEL_KEY_DETALLE].to<JsonObject>();
  detalle[ACK_KEY_TIPO] = tipo;
  if (durationSec > 0) {
    detalle[ACK_KEY_DURATION] = durationSec;
  }
  serializeJson(doc, buf, n);
}

// Publica en nursery/zone/{zona}/sector/{sector}/ack (QoS 0: PubSubClient no publica con QoS 1).
// No reconecta: si no hay conexión, devuelve false.
bool act_publicar(const char* payload) {
  if (!red_iniciada || !mqtt.connected()) {
    return false;
  }
  bool ok = mqtt.publish(topic_act_ack, payload);
  if (ok) {
    Serial.print("[mqtt] ACK publicado: ");
  } else {
    Serial.print("[mqtt] ERROR al publicar el ACK: ");
  }
  Serial.println(payload);
  return ok;
}

// Publica el ACK que quedó pendiente (si hay).
void act_publicar_pendiente() {
  if (!act_ack_pendiente) return;
  act_pendiente_ultimo_intento = millis();
  Serial.println("[mqtt] Publicando el ACK pendiente...");
  if (act_publicar(act_ultimo_ack)) {
    act_ack_pendiente = false;
  }
}

bool act_id_es_reciente(const char* id) {
  if (id == NULL || id[0] == '\0') return false;
  for (int k = 0; k < CANT_RECIENTES; k++) {
    if (strcmp(act_recientes[k], id) == 0) return true;
  }
  return false;
}

void act_id_recordar(const char* id) {
  if (id == NULL || id[0] == '\0') return;
  strlcpy(act_recientes[act_recientes_proximo], id, LARGO_ID);
  act_recientes_proximo = (act_recientes_proximo + 1) % CANT_RECIENTES;
}

// =============================================================
//  "LEER AHORA" — comando de zona
//  El backend pide una lectura; el nodo responde con la telemetría de siempre por
//  nursery/zone/{zona}/telemetry, sin commandId y sin ACK.
// =============================================================

// Sólo marca el pedido: la lectura y la publicación las hace loop().
void mqtt_callback_zona(const char* payload, unsigned int length) {
  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload, (size_t) length);
  if (err) {
    Serial.print("[leer] Comando de zona con JSON ilegible: ");
    Serial.println(err.c_str());
    return;
  }
  const char* accion = doc[RIEL_KEY_ACCION] | "";
  if (strcmp(accion, ZONA_ACCION_LEER_AHORA) != 0) {
    Serial.print("[leer] Acción de zona desconocida, la ignoro: ");
    Serial.println(accion);
    return;
  }
  const char* id = doc[RIEL_KEY_COMMAND_ID] | "";
  strlcpy(leer_id, id, sizeof(leer_id));
  leer_hay = true;
  Serial.print("[leer] Recibido LEER_AHORA ");
  Serial.println(leer_id[0] != '\0' ? leer_id : "(sin commandId)");
}

// Consume el pedido (desde loop()). Con LECTURA_SENSORES_HABILITADA en 0, o si
// leer_sensores() no cargó nada, no publica: el backend lo ve como timeout.
void procesar_lectura() {
  if (!leer_hay) return;
  leer_hay = false;

#if LECTURA_SENSORES_HABILITADA
  JsonDocument doc;
  doc[ZONA_KEY_MAC] = WiFi.macAddress();
  doc[ZONA_KEY_SIGNAL] = WiFi.RSSI();                          // RSSI en dBm
  doc[ZONA_KEY_TIMESTAMP] = (uint32_t) (millis() / 1000UL);    // segundos desde el arranque (sin NTP)
  JsonObject metricas = doc[ZONA_KEY_METRICS].to<JsonObject>();
  if (!leer_sensores(metricas)) {
    Serial.println("[leer] leer_sensores() no cargó ninguna métrica: no publico");
    return;
  }
  if (!red_iniciada || !mqtt.connected()) {
    Serial.println("[leer] Sin conexión al broker: no publico");
    return;
  }
  char buf[LARGO_TELEMETRIA];
  serializeJson(doc, buf, sizeof(buf));
  if (mqtt.publish(topic_zona_telemetria, buf)) {
    Serial.print("[leer] Telemetría publicada: ");
  } else {
    Serial.print("[leer] ERROR al publicar la telemetría: ");
  }
  Serial.println(buf);
#else
  Serial.println("[leer] sin sensores configurados (LECTURA_SENSORES_HABILITADA 0): no publico");
#endif
}

// ============================================================
//  HUECO A COMPLETAR: lectura de sensores
//  No se sabe todavía qué sensores van conectados en el stand
//  (analisis-demo-expo-vs-vivero.md §9.4).
//
//  Para completarlo:
//    1. Leer cada sensor y cargarlo en "metricas" con las CLAVES y UNIDADES de contrato.h:
//         metricas["humSus"] = ...;   // %RH
//         metricas["humAmb"] = ...;   // %RH
//         metricas["temp"]   = ...;   // °C del aire
//         metricas["uv"]     = ...;   // % de luz de un LDR (NO es radiación UV)
//         metricas["ce"]     = ...;   // µS/cm (el backend lo guarda en dS/m)
//         (y las extendidas: tempSuelo, phSuelo, n, p, k)
//    2. Devolver true si cargó al menos una métrica; false si no.
//    3. Poner LECTURA_SENSORES_HABILITADA en 1 en config.h.
// ============================================================
bool leer_sensores(JsonObject metricas) {
  (void) metricas;   // sin sensores todavía
  return false;
}
