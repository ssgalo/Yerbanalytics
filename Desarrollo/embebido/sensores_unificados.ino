#include <HardwareSerial.h>
#include <DHT.h>

// Sensor Luz

const int LIGHT_PIN = 39;
const int LED_PIN = 5;

const int DARK_VALUE = 3500;   // Valor en oscuridad (ajusta)
const int BRIGHT_VALUE = 500;  // Valor en luz brillante (ajusta)

// Sensor NPLPHCTH

HardwareSerial RS485(2); // UART2: RX=GPIO16, TX=GPIO17
#define PIN_DE 4  // Pin DE/RE del módulo RS-485 (control dirección)

// Sensor DHT11

#define PIN_DHT 18    //  Signal
#define TIPO_DHT DHT11
DHT dht(PIN_DHT, TIPO_DHT);

// Construye una trama ModBus RTU para leer registros
void enviarPeticion(uint8_t direccion, uint16_t regInicio, uint16_t cantidad) {
  uint8_t trama[8];
  trama[0] = direccion;       // dirección esclavo (1 por defecto)
  trama[1] = 0x03;            // función 3: leer holding registers
  trama[2] = regInicio >> 8;
  trama[3] = regInicio & 0xFF;
  trama[4] = cantidad >> 8;
  trama[5] = cantidad & 0xFF;
  
  uint16_t crc = calcularCRC(trama, 6);
  trama[6] = crc & 0xFF;
  trama[7] = crc >> 8;

  digitalWrite(PIN_DE, HIGH); // modo transmisión
  RS485.write(trama, 8);
  RS485.flush();
  digitalWrite(PIN_DE, LOW);  // modo recepción
}

// Cálculo de CRC16 para ModBus
uint16_t calcularCRC(uint8_t *datos, uint8_t longitud) {
  uint16_t crc = 0xFFFF;
  for (int i = 0; i < longitud; i++) {
    crc ^= datos[i];
    for (int j = 0; j < 8; j++) {
      if (crc & 0x0001) crc = (crc >> 1) ^ 0xA001;
      else crc >>= 1;
    }
  }
  return crc;
}

void leer_sensor_luz() 
{
    int lightRawValue = analogRead(LIGHT_PIN);
    int lightPercent = map(lightRawValue, DARK_VALUE, BRIGHT_VALUE, 0, 100);
    lightPercent = constrain(lightPercent, 0, 100);

    Serial.print("Valor ADC: ");
    Serial.print(lightRawValue);
    Serial.print(" | Luz: ");
    Serial.print(lightPercent);
    Serial.println("%");
}

void leer_sensor_NPKPHCTH() 
{
    // Pedimos 9 registros del 0 al 8 (todos los parámetros)
    enviarPeticion(1, 0, 9);
    
    delay(500); // esperamos respuesta
    
    if (RS485.available() >= 23) { // 3 bytes cabecera + 18 bytes datos + 2 CRC
        uint8_t resp[23];
        RS485.readBytes(resp, 23);
        
        // Verificar que la respuesta es válida
        if (resp[0] == 1 && resp[1] == 0x03 && resp[2] == 18) {
        
        // Extraer valores (cada registro son 2 bytes, big-endian)
        int16_t humedad_raw  = (resp[3]  << 8) | resp[4];
        int16_t temp_raw     = (resp[5]  << 8) | resp[6];
        uint16_t ec_raw      = (resp[7]  << 8) | resp[8];
        uint16_t ph_raw      = (resp[9]  << 8) | resp[10];
        uint16_t n_raw       = (resp[11] << 8) | resp[12];
        uint16_t p_raw       = (resp[13] << 8) | resp[14];
        uint16_t k_raw       = (resp[15] << 8) | resp[16];
        uint16_t sal_raw     = (resp[17] << 8) | resp[18];
        uint16_t tds_raw     = (resp[19] << 8) | resp[20];

                
        // Aplicar escala según datasheet
        float humedad     = humedad_raw / 10.0;
        float temperatura = temp_raw    / 10.0;
        float ec          = ec_raw;
        float ph          = ph_raw      / 10.0;
        
        float nitrogeno = n_raw;
        float fosforo   = p_raw;
        float potasio   = k_raw;
        float salinidad   = sal_raw;
        float tds         = tds_raw;

        Serial.println("======== Lectura suelo ========");
        Serial.printf("Humedad:      %.1f %%RH\n",   humedad);
        Serial.printf("Temperatura:  %.1f °C\n",      temperatura);
        Serial.printf("EC:           %.0f µS/cm\n",   ec);
        Serial.printf("pH:           %.1f\n",          ph);
        Serial.printf("Nitrógeno:    %.0f mg/kg\n",   nitrogeno);
        Serial.printf("Fósforo:      %.0f mg/kg\n",   fosforo);
        Serial.printf("Potasio:      %.0f mg/kg\n",   potasio);
        Serial.printf("Salinidad:    %.0f mg/L\n",    salinidad);
        Serial.printf("TDS:          %.0f mg/L\n",    tds);
        Serial.println("===============================\n");
        } else {
        Serial.println("Respuesta inválida del sensor");
        }
    } else {
        Serial.println("Sin respuesta (revisar cableado y velocidad)");
        // Vaciar buffer
        while (RS485.available()) RS485.read();
    }
}

void leer_sensor_DHT11() 
{
    float humedad     = dht.readHumidity();
    float temperatura = dht.readTemperature(); // en Celsius

    // Verificar que la lectura fue exitosa
    if (isnan(humedad) || isnan(temperatura)) {
        Serial.println("Error leyendo el DHT11, verificar conexión");
        delay(2000);
        return;
    }

    Serial.print("Humedad:     ");
    Serial.print(humedad);
    Serial.println(" %");

    Serial.print("Temperatura: ");
    Serial.print(temperatura);
    Serial.println(" °C");
}

// Inicialización

void start()
{
    Serial.begin(115200);
    
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    RS485.begin(4800, SERIAL_8N1, 16, 17); // 4800bps, RX=16, TX=17
    pinMode(PIN_DE, OUTPUT);
    digitalWrite(PIN_DE, LOW);

    dht.begin();
    delay(2000);
}


// Arduino setup

void setup()
{
    start();
}

// Arduino loop

void loop()
{
    leer_sensor_luz();
    leer_sensor_NPKPHCTH();
    leer_sensor_DHT11();
    delay(10000);
}