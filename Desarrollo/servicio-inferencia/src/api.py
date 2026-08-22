"""
api.py — Cliente HTTP para la comunicación con el backend de Yerbanalytics.

Responsabilidades:
  - GET /api/capturas/pendientes-diagnostico  → lista de capturas sin diagnóstico.
  - POST /api/diagnosticos                    → registro del resultado de inferencia.

No requiere autenticación: el servicio corre en la misma red Docker privada que el backend.
Todas las excepciones de red se propagan al orquestador (main.py), que las gestiona.
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass

import requests

logger = logging.getLogger(__name__)

# Timeout por defecto para las llamadas HTTP (segundos).
_HTTP_TIMEOUT = 30


def _base_url() -> str:
    return os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/")


@dataclass(frozen=True)
class CapturaPendiente:
    captura_id: str
    file_name: str


def obtener_pendientes() -> list[CapturaPendiente]:
    """
    Consulta las capturas completadas que aún no tienen diagnóstico.

    Returns:
        Lista de CapturaPendiente. Lista vacía si no hay pendientes.

    Raises:
        requests.RequestException: ante cualquier fallo de red o HTTP ≥ 400.
    """
    url = f"{_base_url()}/api/capturas/pendientes-diagnostico"
    response = requests.get(url, timeout=_HTTP_TIMEOUT)
    response.raise_for_status()
    data = response.json()
    pendientes = [
        CapturaPendiente(captura_id=item["capturaId"], file_name=item["fileName"])
        for item in data
    ]
    logger.debug("Pendientes recibidos del backend: %d", len(pendientes))
    return pendientes


def registrar_diagnostico(
    captura_id: str,
    estado: str,
    confianza: float,
    severidad: str,
) -> None:
    """
    Registra el resultado de la inferencia en el backend.

    El backend toma el sectorId y zonaId directamente de la captura referenciada,
    así que el servicio de inferencia solo necesita proveer el capturaId y el
    resultado del modelo.

    Args:
        captura_id:  ID de la captura analizada.
        estado:      Estado de salud según la taxonomía del backend.
        confianza:   Confidence score como porcentaje (0.0–100.0).
        severidad:   "Alta", "Media" o "Baja".

    Raises:
        requests.RequestException: ante cualquier fallo de red o HTTP ≥ 400.
    """
    url = f"{_base_url()}/api/diagnosticos"
    payload = {
        "capturaId": captura_id,
        "estado": estado,
        "conf": confianza,
        "sev": severidad,
    }
    response = requests.post(url, json=payload, timeout=_HTTP_TIMEOUT)
    response.raise_for_status()
    logger.info(
        "Diagnóstico registrado — captura=%s estado=%s confianza=%.1f%% severidad=%s",
        captura_id, estado, confianza, severidad,
    )
