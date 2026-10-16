"""
main.py — Orquestador principal del servicio de inferencia.

Ciclo de vida:
  1. Carga <carpeta del servicio>/.env (si existe), sin pisar variables ya definidas en el entorno
     (en Docker mandan las de docker-compose.yml).
  2. Lee y valida los parámetros del equipo (settings.py). Si algo está mal formado, el servicio no
     arranca: una calibración inválida produciría diagnósticos plausibles y errados.
  3. Carga el detector una única vez (InferenceModel).
  4. Bucle infinito. Por cada ciclo:
       a. Consulta /api/capturas/pendientes-inactivas.
       b. Si hay pendientes, lee UNA vez el criterio del agrónomo (/api/configuracion/diagnostico).
          Si falla, o falta criterio para algún estado anómalo del modelo, se omite el ciclo y las
          capturas siguen pendientes.
       c. Por cada captura: detect → diagnose → registrar_diagnostico.
            - Si el archivo no existe en disco: log warning, continúa.
            - Si la inferencia o el backend fallan: log error, continúa (no detiene el ciclo).

Comportamiento ante reinicios:
  Si el proceso se interrumpe entre la inferencia y el POST, la captura vuelve a aparecer como
  pendiente en el próximo ciclo. La inferencia es determinista para la misma imagen, así que el
  reprocesamiento es seguro.

Los módulos hermanos se importan sin paquete (`import settings`): funciona con `python src/main.py`,
con `python main.py` desde src/ (Docker) y desde los tests (tests/conftest.py agrega src/ al path).
"""

from __future__ import annotations

import logging
import os
import sys
import time
from collections import Counter
from pathlib import Path

from dotenv import load_dotenv

from settings import SERVICE_DIR, resolve_service_path

# Cargar el .env de la carpeta del servicio antes de leer cualquier variable. override=False: lo
# que ya está en el entorno (Docker, la terminal) tiene prioridad.
load_dotenv(SERVICE_DIR / ".env", override=False)

from api import fetch_criteria, obtener_pendientes, registrar_diagnostico  # noqa: E402
from diagnosis import (  # noqa: E402
    Criteria,
    DiagnosisError,
    MissingCriteriaError,
    check_criteria,
    diagnose,
)
from model import InferenceModel  # noqa: E402
from settings import (  # noqa: E402
    SettingsError,
    TeamSettings,
    load_team_settings,
    model_classes_from_env,
)

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
    raw = os.environ.get("CAPTURAS_DIR", "/capturas").strip() or "/capturas"
    return resolve_service_path(raw)


def _model_path() -> Path:
    raw = os.environ.get("MODEL_PATH", "").strip()
    if not raw:
        raise SettingsError(
            "MODEL_PATH no está configurada. Completá el valor en el .env antes de arrancar "
            "(por ejemplo, MODEL_PATH=modelo_final.pt)."
        )
    return resolve_service_path(raw)


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


def _resumen_por_clase(labels: list[str]) -> str:
    counts = Counter(labels)
    return ", ".join(f"{label}={n}" for label, n in counts.most_common()) or "ninguna"


def procesar_ciclo(model: InferenceModel, team: TeamSettings, capturas_dir: Path) -> None:
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

    # --- Criterio del agrónomo: una vez por ciclo, sin caché (design D6) ---
    try:
        criteria: Criteria = fetch_criteria()
        check_criteria(model.classes, criteria)
    except MissingCriteriaError as exc:
        logger.error("%s Se omite el ciclo; las capturas siguen pendientes.", exc)
        return
    except Exception as exc:
        logger.error(
            "No se pudo leer el criterio de diagnóstico del backend: %s. "
            "Se omite el ciclo; las capturas siguen pendientes.",
            exc,
        )
        return

    for captura in pendientes:
        ruta = capturas_dir / captura.file_name
        logger.debug("Procesando captura=%s ruta=%s", captura.captura_id, ruta)

        # --- Detectar y diagnosticar ---
        try:
            detections = model.detect(str(ruta))
            diagnosis = diagnose(detections, team.confidence_bands, team.overlap_iou, criteria)
        except FileNotFoundError:
            logger.warning(
                "Captura %s: el archivo '%s' no existe en disco. "
                "Se omite esta captura y se continúa con la siguiente.",
                captura.captura_id, ruta,
            )
            continue
        except MissingCriteriaError as exc:
            logger.error("Captura %s: %s Se omite el resto del ciclo.", captura.captura_id, exc)
            return
        except DiagnosisError as exc:
            logger.error(
                "Captura %s: no se pudo calcular el diagnóstico: %s. Se omite esta captura.",
                captura.captura_id, exc,
            )
            continue
        except Exception as exc:
            logger.error(
                "Captura %s: error inesperado durante la inferencia: %s. "
                "Se omite esta captura.",
                captura.captura_id, exc,
            )
            continue

        logger.info(
            "Captura %s: %d detección(es) (umbral global), válidas tras filtro y solapes: %s → "
            "diagnóstico %s (severidad=%s, confianza=%s, promedio=%s)",
            captura.captura_id,
            len(detections),
            _resumen_por_clase([d.label for d in diagnosis.detections]),
            diagnosis.state,
            diagnosis.severity or "—",
            diagnosis.confidence or "—",
            f"{diagnosis.mean_score:.4f}" if diagnosis.mean_score is not None else "—",
        )

        # --- Registrar diagnóstico ---
        try:
            registrar_diagnostico(captura.captura_id, diagnosis)
        except Exception as exc:
            logger.error(
                "Captura %s: no se pudo registrar el diagnóstico en el backend: %s. "
                "La captura será reprocesada en el próximo ciclo.",
                captura.captura_id, exc,
            )


def main() -> None:
    fallback_intervalo = _polling_interval()
    capturas_dir = _capturas_dir()

    logger.info("=== Servicio de Inferencia Yerbanalytics ===")
    logger.info("  CAPTURAS_DIR          : %s", capturas_dir)
    logger.info("  POLLING_INTERVAL (FB) : %d s (%.1f h)", fallback_intervalo, fallback_intervalo / 3600)
    logger.info("  BACKEND_URL           : %s", os.environ.get("BACKEND_URL", "http://localhost:8000"))

    # Parámetros del equipo: un valor mal formado detiene el servicio.
    try:
        model_path = _model_path()
        classes = model_classes_from_env()
        team = load_team_settings(classes)
    except SettingsError as exc:
        logger.critical("Configuración inválida: %s", exc)
        sys.exit(1)

    logger.info("  MODEL_PATH            : %s", model_path)
    logger.info("  YOLO_CONF_GLOBAL      : %.2f", team.yolo_conf_global)
    logger.info("  SOLAPAMIENTO_IOU      : %.2f", team.overlap_iou)
    for state, bands in team.confidence_bands.items():
        logger.info(
            "  Bandas de confianza   : %-13s válida %.2f · media %.2f · alta %.2f",
            state, bands.valid, bands.medium, bands.high,
        )

    # Cargar el modelo una única vez al arranque.
    try:
        model = InferenceModel(model_path, classes, team.yolo_conf_global)
    except Exception as exc:
        logger.critical("No se pudo inicializar el modelo: %s", exc)
        sys.exit(1)

    # Ya no dependemos de un intervalo configurable de inferencia.
    # El backend dicta cuándo hay capturas listas (tiempo de quietud).
    # Despertamos frecuentemente para revisar.
    intervalo_polling = 10

    logger.info("Modelo listo. Iniciando ciclo de polling cada %d s.", intervalo_polling)

    while True:
        procesar_ciclo(model, team, capturas_dir)
        time.sleep(intervalo_polling)


if __name__ == "__main__":
    main()
