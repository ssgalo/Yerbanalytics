"""
api.py — Cliente HTTP para la comunicación con el backend de Yerbanalytics.

Responsabilidades:
  - GET /api/capturas/pendientes-inactivas  → lista de capturas sin diagnóstico si el ciclo terminó.
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
    Obtiene la lista de capturas pendientes usando el tiempo de quietud.
    Si el ciclo de fotos no ha terminado (fotos llegaron hace menos de 1 minuto),
    retornará una lista vacía.

    Returns:
        Lista de CapturaPendiente. Lista vacía si no hay pendientes o ciclo en curso.

    Raises:
        requests.RequestException: ante cualquier fallo de red o HTTP ≥ 400.
    """
    url = f"{_base_url()}/api/capturas/pendientes-inactivas?minutosQuietos=1"
    logger.debug("GET %s", url)
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


def obtener_configuracion() -> dict:
    """
    Consulta la configuración operativa actual del vivero.
    Returns:
        Dict con la configuración, o diccionario vacío si falla.
    """
    url = f"{_base_url()}/api/configuracion"
    try:
        response = requests.get(url, timeout=_HTTP_TIMEOUT)
        response.raise_for_status()
        return response.json()
    except Exception as exc:
        logger.warning("No se pudo obtener la configuración del backend: %s", exc)
        return {}
