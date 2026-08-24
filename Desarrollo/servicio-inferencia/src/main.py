"""
main.py — Orquestador principal del servicio de inferencia.

Ciclo de vida:
  1. Carga las variables de entorno desde .env (si existe).
  2. Inicializa el modelo una única vez (InferenceModel.__init__).
  3. Ejecuta un bucle infinito con pausa de POLLING_INTERVAL_SECONDS entre ciclos.
     Por cada ciclo:
       a. Consulta /api/capturas/pendientes-diagnostico.
       b. Por cada captura pendiente:
            - Construye la ruta absoluta del archivo.
            - Llama a model.predict(ruta).
            - Llama a api.registrar_diagnostico(…).
            - Si el archivo no existe en disco: log warning, continúa.
            - Si el backend falla: log error, continúa (no detiene el ciclo).
       c. Si el backend no responde al GET: log error, espera el próximo ciclo.

Comportamiento ante reinicios:
  Si el proceso se interrumpe entre la predicción y el POST, la captura vuelve
  a aparecer como pendiente en el próximo ciclo. Dado que la inferencia es
  determinista para la misma imagen, el reprocesamiento es seguro.
"""

from __future__ import annotations

import logging
import os
import sys
import time
from pathlib import Path

from dotenv import load_dotenv

# Cargar .env antes de cualquier importación que lea variables de entorno.
load_dotenv()

from api import obtener_pendientes, registrar_diagnostico  # noqa: E402
from model import InferenceModel  # noqa: E402

# ---------------------------------------------------------------------------
# Configuración de logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
    stream=sys.stdout,
)
logger = logging.getLogger("inferencia")


def _capturas_dir() -> Path:
    raw = os.environ.get("CAPTURAS_DIR", "/capturas").strip()
    return Path(raw)


def _polling_interval() -> int:
    raw = os.environ.get("POLLING_INTERVAL_SECONDS", "14400").strip()
    try:
        val = int(raw)
        if val <= 0:
            raise ValueError
        return val
    except ValueError:
        logger.warning(
            "POLLING_INTERVAL_SECONDS='%s' no es un entero positivo válido. "
            "Usando el valor por defecto: 14400 segundos (4 horas).",
            raw,
        )
        return 14400


def procesar_ciclo(model: InferenceModel, capturas_dir: Path) -> None:
    """Ejecuta un ciclo completo de polling y procesamiento."""
    try:
        pendientes = obtener_pendientes()
    except Exception as exc:
        logger.error(
            "No se pudo contactar al backend para obtener pendientes: %s. "
            "Se reintentará en el próximo ciclo.",
            exc,
        )
        return

    if not pendientes:
        logger.info("Sin capturas pendientes de diagnóstico.")
        return

    logger.info("%d captura(s) pendiente(s) de diagnóstico.", len(pendientes))

    for captura in pendientes:
        ruta = capturas_dir / captura.file_name
        logger.debug("Procesando captura=%s ruta=%s", captura.captura_id, ruta)

        # --- Leer y predecir ---
        try:
            estado, confianza, severidad = model.predict(str(ruta))
        except FileNotFoundError:
            logger.warning(
                "Captura %s: el archivo '%s' no existe en disco. "
                "Se omite esta captura y se continúa con la siguiente.",
                captura.captura_id, ruta,
            )
            continue
        except Exception as exc:
            logger.error(
                "Captura %s: error inesperado durante la inferencia: %s. "
                "Se omite esta captura.",
                captura.captura_id, exc,
            )
            continue

        # --- Registrar diagnóstico ---
        try:
            registrar_diagnostico(
                captura_id=captura.captura_id,
                estado=estado,
                confianza=confianza,
                severidad=severidad,
            )
        except Exception as exc:
            logger.error(
                "Captura %s: no se pudo registrar el diagnóstico en el backend: %s. "
                "La captura será reprocesada en el próximo ciclo.",
                captura.captura_id, exc,
            )


def main() -> None:
    intervalo = _polling_interval()
    capturas_dir = _capturas_dir()

    logger.info("=== Servicio de Inferencia Yerbanalytics ===")
    logger.info("  CAPTURAS_DIR          : %s", capturas_dir)
    logger.info("  POLLING_INTERVAL      : %d s (%.1f h)", intervalo, intervalo / 3600)
    logger.info("  BACKEND_URL           : %s", os.environ.get("BACKEND_URL", "http://localhost:8080"))

    # Cargar el modelo una única vez al arranque.
    try:
        model = InferenceModel()
    except (EnvironmentError, FileNotFoundError, Exception) as exc:
        logger.critical("No se pudo inicializar el modelo: %s", exc)
        sys.exit(1)

    logger.info("Modelo listo. Iniciando ciclo de polling cada %d segundos.", intervalo)

    while True:
        logger.info("--- Inicio de ciclo de inferencia ---")
        procesar_ciclo(model, capturas_dir)
        logger.info("--- Fin de ciclo. Próximo ciclo en %d s. ---", intervalo)
        time.sleep(intervalo)


if __name__ == "__main__":
    main()
