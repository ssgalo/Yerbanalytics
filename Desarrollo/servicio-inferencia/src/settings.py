"""
settings.py — Configuración del servicio leída del entorno.

Tres grupos:
  - Rutas y clases: carpeta del servicio, resolución de rutas relativas (MODEL_PATH, CAPTURAS_DIR)
    y el mapeo de clases del modelo (MODEL_CLASSES).
  - Parámetros del EQUIPO (design D4): umbral global de YOLO, IoU de solapamiento y bandas de
    confianza por clase. No se editan desde el dashboard: son calibración del modelo, no criterio
    agronómico.
  - Recorte y tiling (add-tiling-inferencia, D7): márgenes de recorte, grilla de tiles y unión de
    cajas entre tiles. También son del equipo: dependen del montaje de la cámara y del modelo.

Una variable ausente toma su default con un WARNING que la nombra. Un valor mal formado levanta
`SettingsError`; main lo convierte en un log CRITICAL y `sys.exit(1)`, porque una calibración
inválida produce diagnósticos plausibles y errados sin ningún aviso.
"""

from __future__ import annotations

import logging
import os
import re
import unicodedata
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path

from diagnosis import HEALTHY, INCONCLUSIVE, ConfidenceBands

logger = logging.getLogger(__name__)

# Carpeta del servicio (padre de src/). Las rutas relativas se resuelven contra ella, no contra el
# directorio de trabajo: así da lo mismo lanzar `python src/main.py` o `python main.py` desde src/.
SERVICE_DIR = Path(__file__).resolve().parent.parent


class SettingsError(ValueError):
    """Configuración inválida: el servicio no debe arrancar."""


# ---------------------------------------------------------------------------
# Rutas
# ---------------------------------------------------------------------------


def resolve_service_path(raw: str) -> Path:
    """Devuelve `raw` tal cual si es absoluta; si no, la resuelve contra la carpeta del servicio."""
    path = Path(raw)
    return path if path.is_absolute() else (SERVICE_DIR / path).resolve()


# ---------------------------------------------------------------------------
# Clases del modelo
# ---------------------------------------------------------------------------

# Estados de la taxonomía del backend que una caja del modelo puede representar. No incluye
# "No concluyente": ése es un resultado del diagnóstico, no una clase de hoja.
VALID_STATES = frozenset({
    "Sano", "Clorosis", "Estrés solar", "Daño biótico", "Ácaro", "Plaga foliar", "Daño fúngico",
})


def parse_model_classes(raw: str) -> list[str]:
    """Parsea MODEL_CLASSES (estados de la plataforma separados por coma, en el orden del modelo)."""
    classes = [c.strip() for c in raw.split(",") if c.strip()]
    if not classes:
        raise SettingsError("MODEL_CLASSES está vacía o mal formateada.")
    if INCONCLUSIVE in classes:
        raise SettingsError(
            "MODEL_CLASSES no puede incluir 'No concluyente': es un resultado del diagnóstico, "
            "no una clase del modelo."
        )
    invalid = [c for c in classes if c not in VALID_STATES]
    if invalid:
        raise SettingsError(
            f"MODEL_CLASSES contiene estados fuera de la taxonomía del backend: {invalid}. "
            f"Los válidos son: {sorted(VALID_STATES)}"
        )
    duplicated = sorted({c for c in classes if classes.count(c) > 1})
    if duplicated:
        raise SettingsError(f"MODEL_CLASSES repite estados: {duplicated}.")
    return classes


def model_classes_from_env(env: Mapping[str, str] | None = None) -> list[str]:
    env = os.environ if env is None else env
    raw = env.get("MODEL_CLASSES", "").strip()
    if not raw:
        raise SettingsError(
            "MODEL_CLASSES no está configurada. Definí el orden de clases del modelo en el .env."
        )
    return parse_model_classes(raw)


# ---------------------------------------------------------------------------
# Parámetros del equipo
# ---------------------------------------------------------------------------

# Defaults PROVISORIOS (design D4): se reemplazan cuando el equipo los defina con las curvas PR.
DEFAULT_YOLO_CONF_GLOBAL = 0.10
DEFAULT_OVERLAP_IOU = 0.60
DEFAULT_CONFIDENCE_BANDS: dict[str, tuple[float, float, float]] = {
    "Sano": (0.60, 0.70, 0.80),
    "Clorosis": (0.40, 0.55, 0.70),
    "Estrés solar": (0.40, 0.55, 0.70),
    "Plaga foliar": (0.35, 0.50, 0.65),
    "Daño fúngico": (0.35, 0.50, 0.65),
}

DEFAULT_STATES: tuple[str, ...] = tuple(DEFAULT_CONFIDENCE_BANDS)


@dataclass(frozen=True)
class TeamSettings:
    yolo_conf_global: float
    overlap_iou: float
    confidence_bands: Mapping[str, ConfidenceBands]


def class_key(state: str) -> str:
    """Clave de entorno de una clase: el estado sin acentos ni espacios (`Daño fúngico` → `DANO_FUNGICO`)."""
    ascii_only = unicodedata.normalize("NFKD", state).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^A-Z0-9]+", "_", ascii_only.upper()).strip("_")


def confidence_bands_var(state: str) -> str:
    return f"BANDAS_CONFIANZA_{class_key(state)}"


def _parse_unit_number(name: str, raw: str) -> float:
    try:
        value = float(raw.strip())
    except ValueError:
        raise SettingsError(f"{name}='{raw}' no es un número.") from None
    if not 0.0 < value <= 1.0:
        raise SettingsError(f"{name}={raw} debe estar en (0, 1].")
    return value


def _read_number(env: Mapping[str, str], name: str, default: float) -> float:
    raw = env.get(name, "").strip()
    if not raw:
        logger.warning("%s no está definida: se usa el valor por defecto %s.", name, default)
        return default
    return _parse_unit_number(name, raw)


def _read_bands(env: Mapping[str, str], state: str) -> ConfidenceBands:
    name = confidence_bands_var(state)
    raw = env.get(name, "").strip()
    if not raw:
        default = DEFAULT_CONFIDENCE_BANDS.get(state)
        if default is None:
            raise SettingsError(
                f"{name} es obligatoria: la clase '{state}' no tiene bandas de confianza por defecto."
            )
        logger.warning(
            "%s no está definida: se usa el valor por defecto %s.",
            name, ",".join(f"{v:.2f}" for v in default),
        )
        return ConfidenceBands(*default)

    parts = raw.split(",")
    if len(parts) != 3:
        raise SettingsError(
            f"{name}='{raw}' debe tener tres números separados por coma "
            f"(válida, media desde, alta desde)."
        )
    valid, medium, high = (_parse_unit_number(name, p) for p in parts)
    if not valid < medium < high:
        raise SettingsError(f"{name}='{raw}': los tres cortes deben ser estrictamente crecientes.")
    return ConfidenceBands(valid, medium, high)


def load_team_settings(
    states: Sequence[str] = DEFAULT_STATES, env: Mapping[str, str] | None = None
) -> TeamSettings:
    """
    Lee y valida los parámetros del equipo para las clases `states` (las de MODEL_CLASSES).

    Raises:
        SettingsError: valores mal formados o `YOLO_CONF_GLOBAL` mayor que algún umbral de validez.
    """
    env = os.environ if env is None else env

    conf_global = _read_number(env, "YOLO_CONF_GLOBAL", DEFAULT_YOLO_CONF_GLOBAL)
    overlap_iou = _read_number(env, "SOLAPAMIENTO_IOU", DEFAULT_OVERLAP_IOU)
    bands = {state: _read_bands(env, state) for state in states}

    too_low = [
        f"{confidence_bands_var(s)} (válida desde {b.valid})"
        for s, b in bands.items()
        if conf_global > b.valid
    ]
    if too_low:
        raise SettingsError(
            f"YOLO_CONF_GLOBAL={conf_global} es mayor que el umbral de validez de: "
            f"{', '.join(too_low)}. La inferencia descartaría detecciones que esa clase considera válidas."
        )

    if HEALTHY not in bands:
        logger.warning("MODEL_CLASSES no incluye '%s': ningún diagnóstico podrá ser Sano.", HEALTHY)

    return TeamSettings(conf_global, overlap_iou, bands)


# ---------------------------------------------------------------------------
# Recorte y tiling (add-tiling-inferencia, design D7)
# ---------------------------------------------------------------------------

# Valores de fábrica para la cámara actual (capturas de 1080×1440): sacan el riel y el piso.
DEFAULT_CROP = {"RECORTE_IZQUIERDA": 278, "RECORTE_ARRIBA": 366, "RECORTE_DERECHA": 0, "RECORTE_ABAJO": 0}
DEFAULT_TILE_ROWS = 3
DEFAULT_TILE_COLUMNS = 2
DEFAULT_TILE_OVERLAP_PX = 130
DEFAULT_TILE_EDGE_TOLERANCE_PX = 6
DEFAULT_TILE_FUSION_IOS = 0.5


@dataclass(frozen=True)
class CropMargins:
    """Píxeles que se quitan de cada borde de la imagen ya orientada."""

    left: int
    top: int
    right: int
    bottom: int


@dataclass(frozen=True)
class TilingSettings:
    crop: CropMargins
    rows: int
    columns: int
    overlap_px: int
    edge_tolerance_px: int
    fusion_ios: float


def _read_int(env: Mapping[str, str], name: str, default: int, minimum: int) -> int:
    raw = env.get(name, "").strip()
    if not raw:
        logger.warning("%s no está definida: se usa el valor por defecto %s.", name, default)
        return default
    try:
        value = int(raw)
    except ValueError:
        raise SettingsError(f"{name}='{raw}' no es un número entero.") from None
    if value < minimum:
        raise SettingsError(f"{name}={raw} debe ser un entero ≥ {minimum}.")
    return value


def load_tiling_settings(env: Mapping[str, str] | None = None) -> TilingSettings:
    """
    Lee y valida el recorte, la grilla de tiles y la unión de cajas entre tiles.

    La validez del recorte y de la grilla contra el tamaño de cada imagen se verifica por captura
    (tiling.py): acá sólo se exige que cada valor esté bien formado.

    Raises:
        SettingsError: valores mal formados o fuera de rango.
    """
    env = os.environ if env is None else env

    crop = CropMargins(*(_read_int(env, name, default, 0) for name, default in DEFAULT_CROP.items()))
    rows = _read_int(env, "TILES_FILAS", DEFAULT_TILE_ROWS, 1)
    columns = _read_int(env, "TILES_COLUMNAS", DEFAULT_TILE_COLUMNS, 1)
    overlap = _read_int(env, "TILES_SOLAPAMIENTO_PX", DEFAULT_TILE_OVERLAP_PX, 0)
    tolerance = _read_int(env, "TILES_TOLERANCIA_BORDE_PX", DEFAULT_TILE_EDGE_TOLERANCE_PX, 0)
    fusion_ios = _read_number(env, "TILES_FUSION_IOS", DEFAULT_TILE_FUSION_IOS)

    if (rows > 1 or columns > 1) and tolerance >= overlap:
        raise SettingsError(
            f"TILES_TOLERANCIA_BORDE_PX={tolerance} debe ser menor que TILES_SOLAPAMIENTO_PX={overlap}: "
            f"si no, toda caja de la franja compartida contaría como cortada por el borde."
        )

    return TilingSettings(crop, rows, columns, overlap, tolerance, fusion_ios)
