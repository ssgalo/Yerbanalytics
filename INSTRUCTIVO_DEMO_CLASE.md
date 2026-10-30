# Instructivo — Demo Expo en clase (24/10/2026)

Flujo a mostrar:
1. Pedir la pasada del riel (dos fotos con el celular).
2. Ver el diagnóstico de IA en vivo.
3. Ejecutar el happy path en modo Simulador con un **diagnóstico simulado** colgado de la última foto, para que se vean el riego, la dosificación y la mediasombra.

> Este archivo es local y no se commitea.

---

## 1. Rama: `develop`

Todo está mergeado en **`develop`** (`345f115`): #73, #74, #75, #76 y #77. Corré la demo desde develop (`git pull` es seguro).

- **#74**: arregla «La orden fue entregada a otro dispositivo». El diagnóstico simulado se cuelga de la última foto que ya existe del sector, en lugar de emitir una orden nueva que se lleva el celular.
- **#76**: `SIM_NODE_ACKS`. Con `off`, el simulador no contesta los comandos de los actuadores. Si los contestara, el backend se quedaría con el primer «terminé», el del simulador, y la malla «terminaría» a los 3 s con el motor real todavía andando.

- **#75**: los tests del backend ya no usan el broker real.
- **#73**: `start-all.sh` / `stop-all.sh` para Linux.

Verificado: `cd Desarrollo/simulador && npm test` da 283/283.

---

## 2. Variables

| Dónde | Variable | Valor | Estado |
|---|---|---|---|
| `Desarrollo/simulador/.env` | `SIM_NODE_ACKS` | `off` | Puesta |
| `Desarrollo/backend/.env` | `yerbanalytics.demo.enabled` | `true` | Ya estaba (sin esto no hay happy path ni fuente de telemetría) |
| `Desarrollo/backend/.env` | `yerbanalytics.mqtt.host` | `tcp://localhost:1883` | Ya estaba |
| `Desarrollo/servicio-inferencia/.env` | `POLLING_INTERVAL_SECONDS` | `14400` | No importa: el ciclo real corre cada 10 s |
| `Desarrollo/embebido/comun/config.h` (ya grabado en la placa) | `MQTT_HOST` | `192.168.1.10` | **La PC tiene que tener esa IP en el router** |

Si se pierde la línea del simulador, agregala de nuevo:
```bash
echo 'SIM_NODE_ACKS=off' >> Desarrollo/simulador/.env
```

---

## 3. Antes de arrancar

1. Conectate a la **red del router** de la presentación.
2. Chequeá la IP con `ip a`: tiene que ser **192.168.1.10**. Si no, el ESP32 no encuentra el broker y no se mueve nada.
3. Enchufá el ESP32 y abrí la **app de cámara** en el celular (misma red, ya vinculada).

## 4. Levantar todo

**Opción recomendada (probada con el sistema completo, #73 + #77):** `./start-all.sh --local-inference --no-browser` levanta los cinco servicios, inferencia incluida, y vuelve en segundos. Para frenar todo: `./stop-all.sh`. Los logs quedan en `logs/`.

Opción manual, con cada servicio en su terminal, desde la raíz del repo:

```bash
# Infraestructura
docker compose up -d mosquitto yerbanalytics-db

# Backend (:8000)
cd Desarrollo/backend && ./mvnw spring-boot:run

# Frontend (:5173)
cd Desarrollo/frontend && npm run dev

# Simulador (:5180)
cd Desarrollo/simulador && npm run dev

# Inferencia (venv local)
cd Desarrollo/servicio-inferencia && source .venv/bin/activate && python src/main.py
```

Chequeos:
- En el log del simulador tiene que aparecer:
  `[simulator] nodo simulado: ACK de actuadores apagados (SIM_NODE_ACKS=off)`
- En el dashboard (Hardware o Demo Expo), el celular tiene que figurar **conectado**.
- La inferencia arranca sin `CRITICAL`.

---

## 5. La demo

### Paso 1: pasada del riel
- Dashboard → **Demo Expo** → pedir la pasada.
- **Esperá a que termine** (vuelve a home) antes de hacer otra cosa: la pasada y las secuencias no corren a la vez.

### Paso 2: diagnóstico en vivo
- La inferencia toma cada foto cuando el backend la da por «quieta», así que tarda unos segundos. El diagnóstico aparece en Demo Expo y en el sector.

### Paso 3: happy path (simulador, `http://localhost:5180`)
1. Pestaña **Demo Expo** → tarjeta **«Telemetría que usa el backend»** → **Simulador**.
   > Esta elección vive en memoria: **si reiniciás el backend, vuelve a «ESP32 real»** y hay que elegirla de nuevo.
2. Toggle **«Diagnóstico de IA»** → **«Diagnóstico simulado»**.
3. Ejecutar el happy path.
4. En el log tenés que ver:
   `✓ Diagnóstico simulado (…) registrado sobre la última foto de MZ-1-00X (CAP-…)`

La otra opción del toggle, **«Última foto de IA»**, usa el diagnóstico real. Si la foto salió «Sano», no dosifica: es lo que pasó ayer.

---

## 6. Avisos

- Los tests del backend ya no tocan el broker real (#75). Aun así, no los corras durante la demo.
- Las reglas encendidas en la base son R-01, `SupplyRule`, `ShadingRule` y `ApplicationPreconditions`. Si una regla de insumo está apagada, el happy path avisa en vez de prenderla: se prende desde **Motor de reglas** en el dashboard.
- Si el happy path vuelve a tirar «La orden fue entregada a otro dispositivo», el simulador no está corriendo el código nuevo: fijate que estés en `develop` actualizado (`git log --oneline -1` tiene que mostrar `345f115` o algo posterior) y reiniciá el simulador.
- Si los ACK se ven duplicados en el log del simulador (`→ ACK …`), no tomó `SIM_NODE_ACKS=off`: revisá el `.env` y reiniciá el simulador.

---

## 7. Después de la clase

Los PRs ya están mergeados. Queda confirmar que, con `SIM_NODE_ACKS=off`, los ACK llegan solo del ESP32.
