# Proyecto Final de Carrera UNLaM 2026- Ingeniería Informática

## Grupo 111

## Yerbanalytics
### Arranque rápido en Linux

`./start-all.sh` es el equivalente de `start-all.ps1`: verifica Java 17+, Node y Docker, crea los
`.env` faltantes desde sus plantillas, instala `node_modules` si faltan y levanta PostgreSQL +
Mosquitto (+ inferencia, si existe `modelo_final.pt`), backend, frontend y simulador en segundo
plano. Logs en `logs/`, PIDs en `logs/pids/`. `./stop-all.sh` detiene exactamente lo que se inició
(grupos de procesos); `./stop-all.sh --docker` baja además los contenedores.

Flags de `start-all.sh`: `--local-inference` (inferencia con `Desarrollo/servicio-inferencia/.venv`
en vez de Docker), `--no-inference`, `--no-browser`.
