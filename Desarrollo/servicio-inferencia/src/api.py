"""
api.py — Cliente HTTP para la comunicación con el backend de Yerbanalytics.

Responsabilidades:
  - GET /api/capturas/pendientes-inactivas  → lista de capturas sin diagnóstico si el ciclo terminó.
  - POST /api/diagnosticos                    → registro del resultado de inferencia.

Autenticación: el backend exige sesión de usuario en toda /api/**, y este servicio entra como
cualquier otro cliente, con una cuenta de rol Servicio que da de alta el Administrador desde el
dashboard. Sus credenciales vienen de BACKEND_USUARIO / BACKEND_CLAVE. El login es perezoso (la
primera llamada lo dispara), la cookie YERBA_SESION queda en un requests.Session del módulo y,
ante un 401 (sesión vencida o revocada), se vuelve a iniciar sesión y se reintenta UNA vez.
Sin credenciales no se intenta el login: se loguea un error claro y el backend rechaza la llamada.

Todas las excepciones de red se propagan al orquestador (main.py), que las gestiona.
"""

from __future__ import annotations

import logging
import os
import threading
from dataclasses import dataclass

import requests

logger = logging.getLogger(__name__)

# Timeout por defecto para las llamadas HTTP (segundos).
_HTTP_TIMEOUT = 30


def _base_url() -> str:
    return os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/")


# ---------------------------------------------------------------------------
# Sesión con el backend
# ---------------------------------------------------------------------------

# Una sola sesión HTTP para todo el proceso: guarda la cookie YERBA_SESION que fija el login.
_session = requests.Session()
# El ciclo es secuencial, pero el lock evita dos logins simultáneos si eso cambia.
_login_lock = threading.Lock()
_COOKIE_SESION = "YERBA_SESION"


def _credenciales() -> tuple[str, str] | None:
    usuario = os.environ.get("BACKEND_USUARIO", "").strip()
    clave = os.environ.get("BACKEND_CLAVE", "").strip()
    return (usuario, clave) if usuario and clave else None


def _iniciar_sesion() -> bool:
    """
    Inicia sesión con la cuenta de servicio. Devuelve False si no hay credenciales o el backend
    las rechaza; los errores de red se propagan como cualquier otro.
    """
    credenciales = _credenciales()
    if credenciales is None:
        logger.error(
            "Faltan BACKEND_USUARIO / BACKEND_CLAVE: el backend va a rechazar las llamadas. "
            "Configurá una cuenta de rol Servicio (la da de alta el Administrador desde el dashboard)."
        )
        return False

    usuario, clave = credenciales
    _session.cookies.clear()
    response = _session.post(
        f"{_base_url()}/api/auth/login",
        json={"username": usuario, "clave": clave},
        timeout=_HTTP_TIMEOUT,
    )
    if response.status_code == 401:
        logger.error(
            "El backend rechazó las credenciales de '%s' (BACKEND_USUARIO / BACKEND_CLAVE). "
            "Revisá que la cuenta exista, esté activa y que la clave sea la vigente.",
            usuario,
        )
        return False
    response.raise_for_status()

    if response.json().get("debeCambiarClave"):
        # Las cuentas nacen con clave temporal y, hasta cambiarla, el backend responde 403 a todo.
        logger.error(
            "La cuenta '%s' tiene clave temporal: entrá una vez al dashboard con ella, "
            "cambiala y poné la nueva en BACKEND_CLAVE.",
            usuario,
        )
    logger.info("Sesión iniciada en el backend como '%s'.", usuario)
    return True


def _request(method: str, path: str, **kwargs) -> requests.Response:
    """
    Llama al backend con la sesión adjunta. Ante un 401 reinicia sesión y reintenta una única
    vez; lo que vuelva del reintento es definitivo. No lanza por status: eso queda a cargo del
    llamante (raise_for_status).
    """
    kwargs.setdefault("timeout", _HTTP_TIMEOUT)
    url = f"{_base_url()}{path}"

    with _login_lock:
        if _session.cookies.get(_COOKIE_SESION) is None:
            _iniciar_sesion()
        cookie_usada = _session.cookies.get(_COOKIE_SESION)

    response = _session.request(method, url, **kwargs)
    if response.status_code != 401 or _credenciales() is None:
        return response

    logger.info("Sesión vencida o revocada: se reintenta %s %s con una sesión nueva.", method, path)
    with _login_lock:
        # Si otro hilo ya renovó la sesión mientras tanto, se reutiliza la suya.
        if _session.cookies.get(_COOKIE_SESION) in (None, cookie_usada):
            if not _iniciar_sesion():
                return response
    return _session.request(method, url, **kwargs)


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
    response = _request("GET", "/api/capturas/pendientes-inactivas?minutosQuietos=1")
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
    payload = {
        "capturaId": captura_id,
        "estado": estado,
        "conf": confianza,
        "sev": severidad,
    }
    response = _request("POST", "/api/diagnosticos", json=payload)
    response.raise_for_status()
    logger.info(
        "Diagnóstico registrado — captura=%s estado=%s confianza=%.1f%% severidad=%s",
        captura_id, estado, confianza, severidad,
    )


def obtener_configuracion() -> dict:
    """
    Consulta la configuración operativa actual del vivero.

    Pide `configuracion.ver`, que el rol Servicio no trae en la matriz por defecto: sin ese
    permiso el backend responde 403 y esto devuelve {}.

    Returns:
        Dict con la configuración, o diccionario vacío si falla.
    """
    try:
        response = _request("GET", "/api/configuracion")
        response.raise_for_status()
        return response.json()
    except Exception as exc:
        logger.warning("No se pudo obtener la configuración del backend: %s", exc)
        return {}
