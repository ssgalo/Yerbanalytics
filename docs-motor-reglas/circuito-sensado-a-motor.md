# Circuito de sensado: del sensor al motor de reglas

Cómo viaja una lectura desde el nodo testigo hasta que el motor de reglas decide y actúa.
Todo sale de leer el código de `develop` (firmware, backend, simulador); no se ejecutó nada.
El interior del motor está en `diferencias-motor-reglas-vs-reglas-v2.md`.

Ejemplo que recorre los diagramas: **"MZ-2 se está secando"**.

---

## 1. Flujo completo

Para importar en draw.io: *Organizar → Insertar → Avanzado → Mermaid* y pegar el bloque.

```mermaid
flowchart TD
    subgraph CAMPO["VIVERO (campo)"]
        SENS["Sensores del nodo testigo<br/>LDR: luz %<br/>DHT11: humedad ambiente y temperatura del aire<br/>Sonda de suelo RS485: humedad de sustrato y CE"]
        NODO["Nodo testigo ESP32 (1 por macro-zona)<br/>Lee y publica cada 30 s por su cuenta<br/>Nadie le pide la lectura"]
        BUF[("Buffer offline<br/>hasta 50 lecturas")]
        ACT["Nodo actuador ESP32 (1 por sector)<br/>valvula, bomba, mediasombra"]
    end

    SIM["Simulador<br/>Ocupa el lugar del nodo testigo<br/>Mismo topico y mismo mensaje"]

    BROKER(("Broker MQTT<br/>Mosquitto :1883<br/>recibe y reparte"))

    subgraph BACK["BACKEND (Spring Boot)"]
        P1["1. RECIBE<br/>Suscripto a nursery/zone/+/telemetry<br/>La zona sale del topico: MZ-2"]
        P2["2. CONVIERTE<br/>ce: 850 uS/cm pasa a 0,85 dS/m<br/>Es la unica conversion"]
        P3["3. GUARDA<br/>Pisa la ultima lectura de la zona<br/>y la bateria y senal del nodo"]
        P4["4. EVALUA EL ESTADO<br/>Cada metrica contra sus umbrales<br/>La peor define el estado: ok, warning o critical<br/>Ese estado se copia a todos los sectores de la zona"]
        P5["5. ARMA EL CONTEXTO (1 por sector)<br/>lectura de la zona + estado + diagnostico IA<br/>+ pronostico + bloqueo manual + sensor viejo?"]
        P6["6. MOTOR DE REGLAS<br/>Corre una vez por cada sector de la zona"]
        P7["7. ACTION EXECUTOR<br/>Actualiza los actuadores del sector<br/>Registra la decision en el historial<br/>Publica el comando"]
        WD["WATCHDOG (cada 5 min)<br/>Vuelve a correr el motor sobre TODOS los sectores<br/>aunque no haya llegado ninguna lectura"]
    end

    DB[("PostgreSQL")]
    CLIMA["Open-Meteo<br/>cache de 15 min"]
    DASH["Dashboard<br/>pide GET /api/nursery cada 5 s"]

    SENS --> NODO
    NODO -. "sin WiFi o sin broker" .-> BUF
    BUF -. "al reconectar, en orden" .-> NODO
    NODO -- "publish<br/>nursery/zone/MZ-2/telemetry" --> BROKER
    SIM -- "publish<br/>nursery/zone/MZ-2/telemetry" --> BROKER
    BROKER -- "entrega" --> P1
    P1 --> P2 --> P3 --> P4 --> P5 --> P6 --> P7
    P3 -- "UPDATE zona, dispositivo" --> DB
    P4 -- "UPDATE sector" --> DB
    CLIMA --> P5
    DB -- "umbrales, configuracion, bloqueos" --> P5
    WD --> P5
    P7 -- "UPDATE sector<br/>INSERT historial_evento" --> DB
    P7 -- "publish<br/>nursery/zone/MZ-2/sector/MZ-2-006/command" --> BROKER
    BROKER -- "entrega" --> ACT
    ACT -. "publish .../ack<br/>HOY EL BACKEND NO LO ESCUCHA" .-> BROKER
    DB --> DASH
```

---

## 2. Qué viaja por MQTT

| Dirección | Tópico | Quién publica | Quién escucha |
|---|---|---|---|
| Nodo → backend | `nursery/zone/{zona}/telemetry` | Nodo testigo o simulador | Backend |
| Backend → nodo | `nursery/zone/{zona}/sector/{sector}/command` | Backend | Nodo actuador del sector |
| Nodo → backend | `nursery/zone/{zona}/sector/{sector}/ack` | Nodo actuador | **Nadie** (el backend no se suscribe) |

**Telemetría** (lo que manda el nodo testigo de MZ-2):

```json
{
  "mac": "A4:CF:12:9A:00:01",
  "battery": 87,
  "signal": -62,
  "timestamp": 1782414800,
  "metrics": { "humSus": 32, "humAmb": 55, "temp": 31, "ce": 850, "uv": 78 }
}
```

- `humSus`, `humAmb`: %. `temp`: °C. `ce`: µS/cm. `uv`: **% de luz del LDR**, no radiación UV.
- `mac` no decide la zona: sólo sirve para actualizar la batería y señal del dispositivo registrado.
- Una métrica cuyo sensor falló no viaja, y el backend conserva el valor anterior.
- El firmware puede mandar además `tempSuelo`, `phSuelo`, `n`, `p`, `k`, pero viene apagado de
  fábrica (`ENVIAR_METRICAS_EXTENDIDAS 0`).

**Comando** (lo que publica el backend):

```json
{ "commandId": "<uuid>", "actuador": "valve", "accion": "ON", "parametros": { "durationSec": 120 } }
```

| Actuador | `accion` | `parametros` |
|---|---|---|
| `valve` | `ON` | `durationSec` |
| `pump` | `ON` | vacío |
| `shade` | `SET` | `targetPct` |

---

## 3. Cada cuánto pasa cada cosa

| Qué | Cada cuánto | Dónde está definido |
|---|---|---|
| El nodo testigo lee y publica | 30 s | `SENSOR_POLLING_INTERVAL_MS`, `embebido/comun/config.example.h:49` |
| El simulador publica | 10 s al encenderlo; a los 5 s adopta `intervaloSensadoMinutos` de la base (**240 min**) | `simulador/server/emission.ts:35-47` |
| El backend procesa una lectura | Al instante, por cada mensaje (no hace polling) | `MqttTelemetryReceiver.processMessage` |
| El motor corre por telemetría | Con cada mensaje, sobre todos los sectores de esa zona | `NurseryService.java:509-510` |
| El motor corre por watchdog | 5 min (`intervaloEvaluacionMinutos`), sobre todo el vivero | `NurseryWatchdog.java:77-98` |
| Una zona pasa a "sin señal" | Si la última lectura tiene más de 30 s | `stale-threshold-ms`, `application.properties:69` |
| Se mide la efectividad de una acción | Revisión cada 30 s; compara 2 min después de actuar | `HistorialService.java:192-218` |
| Pronóstico del clima | Caché de 15 min | `weather.cache-ttl-ms`, `application.properties:121` |
| El dashboard se refresca | 5 s | `frontend/src/hooks/NurseryContext.tsx:30` |

---

## 4. Qué queda en la base

```mermaid
erDiagram
    zona ||--o{ sector : "tiene"
    zona ||..o{ dispositivo : "zona_id"
    sector ||..o{ historial_evento : "sector_id"
    sector ||..o{ bloqueo_manual : "sector_id"

    zona {
        string id PK "MZ-2"
        float hum_sus_raw
        float hum_amb_raw
        float temp_raw
        float uv_raw "pct de luz"
        float ce_raw "dS/m"
        string nodo_mac
        int nodo_battery
        int nodo_signal
        long last_reading_time
    }
    sector {
        string id PK "MZ-2-006"
        string zona_id FK
        string status "ok, warning, critical"
        string reason
        string diagnosis_estado
        float diagnosis_conf
        string actuador_valve
        string actuador_pump
        int actuador_shade "pct de apertura"
    }
    dispositivo {
        string id PK "DEV-001"
        string serial "MAC del nodo"
        string tipo
        string zona_id
        string sector_id
        int bateria
        int senal
        long ultimo_update
    }
    historial_evento {
        string id PK
        string sector_id
        string zona_id
        long ts
        string tipo "Riego, Insumo, Info"
        string lectura
        string decision
        string accion
        string res
        string sev
    }
    bloqueo_manual {
        long id PK
        string sector_id
        string zona_id
        string reason
        boolean active
    }
    umbral_metrica {
        string metric_key PK "humSus"
        float ideal_min
        float ideal_max
        float warn_min
        float warn_max
    }
    configuracion_operativa {
        int intervalo_sensado_min "240"
        int intervalo_evaluacion_min "5"
        float riego_tiempo_max_seg
        float riego_vol_max_diario_ml
        float insumo_dosis_max_24h_ml
    }
```

- **Sólo se guarda la última lectura.** Cada mensaje pisa las columnas `*_raw` de `zona`; no hay
  tabla de historial de lecturas. `historial_evento` es el registro de decisiones del motor, no
  de mediciones.
- La única relación real en la base es `zona` 1—N `sector`. Las líneas punteadas son ids de
  texto sin clave foránea.
- "Sin señal" no se guarda: se calcula al leer, comparando `last_reading_time` con la hora actual.
- `umbral_metrica` y `configuracion_operativa` son tablas sueltas de parámetros (la segunda
  tiene una sola fila).

**Cómo se decide el estado** (paso 4): fuera de la banda `ideal` es *warning*, fuera de la banda
`warn` es *critical*. Valores de fábrica para las métricas que mueven el estado:

| Métrica | Ideal | Warning hasta |
|---|---|---|
| `humSus` (%) | 42 – 68 | 32 – 80 |
| `humAmb` (%) | 62 – 84 | 52 – 91 |
| `temp` (°C) | 18 – 27 | 15 – 31 |
| `uv` (% luz) | 35 – 70 | 20 – 85 |
| `ce` (dS/m) | 1,0 – 1,9 | 0,8 – 2,5 |

En el ejemplo, `humSus = 32` cae fuera de la banda ideal: MZ-2 queda en *warning* y con ella
sus 100 sectores.

---

## 5. Lo que hoy no cierra

Cosas que el diagrama muestra tal cual están, y que conviene tener presentes:

1. **El `timestamp` del firmware está en segundos y el backend lo trata como milisegundos.**
   `reloj::ahoraEpoch()` devuelve segundos (`embebido/comun/reloj.cpp:51-58`); el backend lo
   guarda y lo resta de `System.currentTimeMillis()` (`NurseryService.java:444,96`). Con un nodo
   real la zona quedaría siempre "sin señal". El simulador manda milisegundos, por eso con él
   funciona. Sale de leer el código; no se probó con hardware.
2. **El nodo publica cada 30 s y la zona se considera caída a los 30 s.** No hay margen: un
   mensaje demorado la marca "sin señal".
3. **El simulador baja solo a una lectura cada 4 h** (toma `intervaloSensadoMinutos`), y contra
   el umbral de 30 s la zona pasa casi todo el tiempo "sin señal". Ese parámetro no llega al
   firmware: el nodo real sigue en 30 s.
4. **El ACK del actuador no lo escucha nadie.** El backend marca "Regando" al publicar el
   comando, sin confirmación del hardware.
5. **El comando de la bomba sale sin mililitros** (`ActionExecutor.java:94`) y el firmware lo
   rechaza con `dosis_invalida` (`embebido/actuacion/act_bomba.cpp:24-27`).
6. **No hay validación de rangos en la ingesta** ni usuario/contraseña en el broker.
