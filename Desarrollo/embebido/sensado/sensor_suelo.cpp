#include "sensor_suelo.h"
#include "config.h"
#include <HardwareSerial.h>

namespace sensor_suelo {

// UART2 dedicado al transceiver RS-485.
static HardwareSerial rs485(2);

// --- Parámetros del protocolo Modbus de la sonda ---
static const uint8_t  DIRECCION_ESCLAVO = 1;
static const uint8_t  FUNCION_LEER      = 0x03;
static const uint16_t REG_INICIO        = 0;
static const uint16_t REG_CANTIDAD      = 9;      // 9 registros
static const uint8_t  BYTES_DATOS       = 18;     // 9 * 2
static const uint8_t  BYTES_RESPUESTA   = 23;     // 3 cabecera + 18 datos + 2 CRC
static const uint32_t TIMEOUT_RESP_MS   = 600;

// Escalas del datasheet.
static const float ESCALA_DECIMAL = 10.0f;   // humedad, temp, pH vienen x10

static uint16_t calcularCRC(const uint8_t* datos, uint8_t longitud) {
  uint16_t crc = 0xFFFF;
  for (uint8_t i = 0; i < longitud; i++) {
    crc ^= datos[i];
    for (uint8_t j = 0; j < 8; j++) {
      if (crc & 0x0001) crc = (crc >> 1) ^ 0xA001;
      else              crc >>= 1;
    }
  }
  return crc;
}

static void enviarPeticion() {
  uint8_t trama[8];
  trama[0] = DIRECCION_ESCLAVO;
  trama[1] = FUNCION_LEER;
  trama[2] = REG_INICIO >> 8;
  trama[3] = REG_INICIO & 0xFF;
  trama[4] = REG_CANTIDAD >> 8;
  trama[5] = REG_CANTIDAD & 0xFF;
  uint16_t crc = calcularCRC(trama, 6);
  trama[6] = crc & 0xFF;
  trama[7] = crc >> 8;

  digitalWrite(PIN_RS485_DE, HIGH);   // transmisión
  rs485.write(trama, 8);
  rs485.flush();
  digitalWrite(PIN_RS485_DE, LOW);    // recepción
}

void iniciar() {
  rs485.begin(RS485_BAUD, SERIAL_8N1, PIN_RS485_RX, PIN_RS485_TX);
  pinMode(PIN_RS485_DE, OUTPUT);
  digitalWrite(PIN_RS485_DE, LOW);
}

LecturaSuelo leer() {
  LecturaSuelo r;
  r.estado = EstadoLectura::ERROR;

  // Vaciar buffer previo.
  while (rs485.available()) rs485.read();

  enviarPeticion();

  // Esperar la respuesta hasta el timeout.
  uint32_t inicio = millis();
  while (rs485.available() < BYTES_RESPUESTA) {
    if (millis() - inicio > TIMEOUT_RESP_MS) {
      Serial.println("[suelo] Sin respuesta (timeout)");
      return r;
    }
    delay(5);
  }

  uint8_t resp[BYTES_RESPUESTA];
  rs485.readBytes(resp, BYTES_RESPUESTA);

  // Validar cabecera.
  if (resp[0] != DIRECCION_ESCLAVO || resp[1] != FUNCION_LEER || resp[2] != BYTES_DATOS) {
    Serial.println("[suelo] Cabecera inválida");
    return r;
  }

  // Validar CRC (últimos 2 bytes, little-endian).
  uint16_t crcCalc = calcularCRC(resp, BYTES_RESPUESTA - 2);
  uint16_t crcRecv = resp[BYTES_RESPUESTA - 2] | (resp[BYTES_RESPUESTA - 1] << 8);
  if (crcCalc != crcRecv) {
    Serial.println("[suelo] CRC inválido");
    return r;
  }

  // Decodificar registros (big-endian, 2 bytes c/u a partir de resp[3]).
  int16_t  humedad_raw = (resp[3]  << 8) | resp[4];
  int16_t  temp_raw    = (resp[5]  << 8) | resp[6];
  uint16_t ec_raw      = (resp[7]  << 8) | resp[8];
  uint16_t ph_raw      = (resp[9]  << 8) | resp[10];
  uint16_t n_raw       = (resp[11] << 8) | resp[12];
  uint16_t p_raw       = (resp[13] << 8) | resp[14];
  uint16_t k_raw       = (resp[15] << 8) | resp[16];
  uint16_t sal_raw     = (resp[17] << 8) | resp[18];
  uint16_t tds_raw     = (resp[19] << 8) | resp[20];

  r.humedad     = humedad_raw / ESCALA_DECIMAL;
  r.temperatura = temp_raw    / ESCALA_DECIMAL;
  r.ec          = ec_raw;
  r.ph          = ph_raw      / ESCALA_DECIMAL;
  r.nitrogeno   = n_raw;
  r.fosforo     = p_raw;
  r.potasio     = k_raw;
  r.salinidad   = sal_raw;
  r.tds         = tds_raw;
  r.estado      = EstadoLectura::OK;
  return r;
}

}  // namespace sensor_suelo
