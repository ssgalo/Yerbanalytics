"""
diagnosis.py — Módulo de diagnóstico: de las detecciones del modelo a un único diagnóstico.

Lógica PURA: no hace I/O ni importa ultralytics, así se prueba sin GPU ni checkpoint. Recibe las
detecciones ya traducidas a estados de la plataforma, los parámetros del equipo (bandas de
confianza por clase e IoU de solapamiento) y el criterio del agrónomo (bandas de severidad y orden
de criticidad), y devuelve un `Diagnosis`.

Pipeline (design D2/D3 de openspec/changes/add-diagnostico-por-detecciones):

  1. Filtro por umbral de su clase          → detecciones válidas
  2. Solapes, sólo entre válidas (NMS calibrado entre clases)
  3. Agregación por estado: cantidad y score promedio
  4. Severidad (anómalos): cantidad vs bandas del agrónomo; bajo el mínimo queda fuera
  5. Confianza: promedio vs bandas de confianza de la clase
  6. Elección: mayor relevancia (severidad + confianza); empate → criticidad
  7. Sin anómalos: Sano (si hay Sano válidas) o No concluyente

El filtro va ANTES que los solapes a propósito: una detección que no supera su propio umbral no
debe poder eliminar a una válida de otra clase.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping, Sequence
from dataclasses import dataclass, field
from typing import Literal

ConfidenceLevel = Literal["Baja", "Media", "Alta"]
SeverityLevel = Literal["Baja", "Media", "Alta"]

HEALTHY = "Sano"
INCONCLUSIVE = "No concluyente"

# Valor de cada nivel para la relevancia (severidad + confianza) y para ordenar solapes.
LEVEL_VALUE: dict[str, int] = {"Baja": 1, "Media": 2, "Alta": 3}

Box = tuple[float, float, float, float]


class DiagnosisError(ValueError):
    """El diagnóstico no se puede calcular con los parámetros recibidos."""


class MissingCriteriaError(DiagnosisError):
    """Apareció un estado anómalo sin bandas de severidad o sin lugar en el orden de criticidad."""


class MissingConfidenceBandsError(DiagnosisError):
    """Apareció una clase sin bandas de confianza del equipo."""


# ---------------------------------------------------------------------------
# Tipos
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class Detection:
    """Una caja del modelo: estado de la plataforma, score crudo y caja normalizada (xyxyn)."""

    label: str
    score: float
    box: Box


@dataclass(frozen=True)
class ConfidenceBands:
    """Cortes del equipo sobre el score crudo de una clase: válida desde, media desde, alta desde."""

    valid: float
    medium: float
    high: float

    def is_valid(self, score: float) -> bool:
        return score >= self.valid

    def level(self, score: float) -> ConfidenceLevel:
        """Nivel de confianza de un score (o de un promedio) ya válido."""
        if score >= self.high:
            return "Alta"
        if score >= self.medium:
            return "Media"
        return "Baja"


@dataclass(frozen=True)
class SeverityBands:
    """Cortes del agrónomo en cantidad de hojas: mínimo, media desde, alta desde."""

    minimum: int
    medium_from: int
    high_from: int

    def level(self, count: int) -> SeverityLevel | None:
        """Severidad por cantidad; None si no alcanza el mínimo (el estado queda fuera)."""
        if count >= self.high_from:
            return "Alta"
        if count >= self.medium_from:
            return "Media"
        if count >= self.minimum:
            return "Baja"
        return None


@dataclass(frozen=True)
class Criteria:
    """Criterio del agrónomo. `criticality[0]` es el estado anómalo más crítico."""

    severity_bands: Mapping[str, SeverityBands]
    criticality: tuple[str, ...]


@dataclass(frozen=True)
class Diagnosis:
    """
    Diagnóstico único de una captura.

    `severity` sólo en estados anómalos; `confidence` y `mean_score` salvo en No concluyente.
    `detections` son TODAS las válidas que sobrevivieron a los solapes, de todas las clases.
    """

    state: str
    severity: SeverityLevel | None
    confidence: ConfidenceLevel | None
    mean_score: float | None
    detections: tuple[Detection, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class StateSummary:
    """Agregado de un estado: cantidad de detecciones válidas y su score promedio."""

    count: int
    mean_score: float


# ---------------------------------------------------------------------------
# Pasos del pipeline
# ---------------------------------------------------------------------------


def _bands_for(label: str, confidence_bands: Mapping[str, ConfidenceBands]) -> ConfidenceBands:
    try:
        return confidence_bands[label]
    except KeyError:
        raise MissingConfidenceBandsError(
            f"La clase '{label}' no tiene bandas de confianza del equipo "
            f"(BANDAS_CONFIANZA_<CLASE>)."
        ) from None


def filter_valid(
    detections: Iterable[Detection], confidence_bands: Mapping[str, ConfidenceBands]
) -> list[Detection]:
    """Paso 1: conserva sólo las detecciones que alcanzan el umbral de validez de su clase."""
    return [d for d in detections if _bands_for(d.label, confidence_bands).is_valid(d.score)]


def check_criteria(states: Iterable[str], criteria: Criteria) -> None:
    """Exige que cada estado anómalo tenga bandas de severidad y lugar en el orden de criticidad."""
    missing = sorted(
        {
            s
            for s in states
            if s != HEALTHY and (s not in criteria.severity_bands or s not in criteria.criticality)
        }
    )
    if missing:
        raise MissingCriteriaError(
            f"Estados anómalos sin criterio de diagnóstico (bandas de severidad u orden de "
            f"criticidad): {missing}. Revisá Configuración → Criterio de diagnóstico."
        )


def _criticality_rank(label: str, criteria: Criteria) -> int:
    """Menor = más crítico. Sano va siempre última."""
    if label == HEALTHY:
        return len(criteria.criticality)
    return criteria.criticality.index(label)


def iou(a: Box, b: Box) -> float:
    """Intersección sobre unión de dos cajas xyxy."""
    ix1, iy1 = max(a[0], b[0]), max(a[1], b[1])
    ix2, iy2 = min(a[2], b[2]), min(a[3], b[3])
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    if inter <= 0.0:
        return 0.0
    area_a = max(0.0, a[2] - a[0]) * max(0.0, a[3] - a[1])
    area_b = max(0.0, b[2] - b[0]) * max(0.0, b[3] - b[1])
    union = area_a + area_b - inter
    return inter / union if union > 0.0 else 0.0


def resolve_overlaps(
    valid: Sequence[Detection],
    confidence_bands: Mapping[str, ConfidenceBands],
    criteria: Criteria,
    overlap_iou: float,
) -> list[Detection]:
    """
    Paso 2: NMS calibrado entre clases (D3), sólo sobre detecciones válidas.

    Orden de preferencia: nivel de confianza de su clase ↓, criticidad ↓ (Sano última), score ↓.
    Greedy: se conserva la primera y se descartan las que la pisan con IoU ≥ `overlap_iou`.
    Devuelve las conservadas en el orden en que llegaron.
    """

    def key(item: tuple[int, Detection]) -> tuple[int, int, float, int]:
        index, d = item
        level = LEVEL_VALUE[_bands_for(d.label, confidence_bands).level(d.score)]
        return (-level, _criticality_rank(d.label, criteria), -d.score, index)

    ordered = sorted(enumerate(valid), key=key)
    kept: list[tuple[int, Detection]] = []
    for index, candidate in ordered:
        if all(iou(candidate.box, k.box) < overlap_iou for _, k in kept):
            kept.append((index, candidate))
    kept.sort(key=lambda item: item[0])
    return [d for _, d in kept]


def summarize(detections: Iterable[Detection]) -> dict[str, StateSummary]:
    """Paso 3: cantidad y score promedio por estado."""
    scores: dict[str, list[float]] = {}
    for d in detections:
        scores.setdefault(d.label, []).append(d.score)
    return {
        label: StateSummary(count=len(values), mean_score=sum(values) / len(values))
        for label, values in scores.items()
    }


# ---------------------------------------------------------------------------
# Entrada principal
# ---------------------------------------------------------------------------


def diagnose(
    detections: Iterable[Detection],
    confidence_bands: Mapping[str, ConfidenceBands],
    overlap_iou: float,
    criteria: Criteria,
) -> Diagnosis:
    """
    Convierte todas las detecciones de una imagen en un único diagnóstico.

    Raises:
        MissingCriteriaError: si una detección válida es de un estado anómalo sin criterio.
        MissingConfidenceBandsError: si una detección es de una clase sin bandas de confianza.
    """
    valid = filter_valid(detections, confidence_bands)
    check_criteria((d.label for d in valid), criteria)
    survivors = tuple(resolve_overlaps(valid, confidence_bands, criteria, overlap_iou))
    summaries = summarize(survivors)

    # Candidatos anómalos: (relevancia, rango de criticidad, estado, severidad, confianza, promedio)
    candidates: list[tuple[int, int, str, SeverityLevel, ConfidenceLevel, float]] = []
    for state, summary in summaries.items():
        if state == HEALTHY:
            continue
        severity = criteria.severity_bands[state].level(summary.count)
        if severity is None:
            continue
        confidence = confidence_bands[state].level(summary.mean_score)
        relevance = LEVEL_VALUE[severity] + LEVEL_VALUE[confidence]
        candidates.append(
            (relevance, _criticality_rank(state, criteria), state, severity, confidence,
             summary.mean_score)
        )

    if candidates:
        # Mayor relevancia; ante empate, el más crítico (menor rango).
        _, _, state, severity, confidence, mean = min(candidates, key=lambda c: (-c[0], c[1]))
        return Diagnosis(state, severity, confidence, mean, survivors)

    healthy = summaries.get(HEALTHY)
    if healthy is not None:
        confidence = confidence_bands[HEALTHY].level(healthy.mean_score)
        return Diagnosis(HEALTHY, None, confidence, healthy.mean_score, survivors)

    return Diagnosis(INCONCLUSIVE, None, None, None, survivors)
