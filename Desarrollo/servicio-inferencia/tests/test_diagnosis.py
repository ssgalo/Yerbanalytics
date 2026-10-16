"""Tests del módulo de diagnóstico (lógica pura, sin YOLO)."""

from __future__ import annotations

import itertools

import pytest

from diagnosis import (
    ConfidenceBands,
    Criteria,
    Detection,
    MissingCriteriaError,
    SeverityBands,
    diagnose,
    iou,
)
from settings import DEFAULT_CONFIDENCE_BANDS, DEFAULT_OVERLAP_IOU

BANDS = {state: ConfidenceBands(*cuts) for state, cuts in DEFAULT_CONFIDENCE_BANDS.items()}

# Criterio por defecto del agrónomo (design D12).
CRITERIA = Criteria(
    severity_bands={
        "Clorosis": SeverityBands(5, 10, 20),
        "Estrés solar": SeverityBands(5, 10, 20),
        "Plaga foliar": SeverityBands(2, 5, 10),
        "Daño fúngico": SeverityBands(2, 4, 8),
    },
    criticality=("Daño fúngico", "Plaga foliar", "Clorosis", "Estrés solar"),
)

# Grilla de cajas que no se tocan: 40 × 40 celdas de 0,025 con cajas de 0,02.
_CELLS = itertools.count()


def _box() -> tuple[float, float, float, float]:
    cell = next(_CELLS) % 1600
    x, y = (cell % 40) * 0.025, (cell // 40) * 0.025
    return (round(x, 4), round(y, 4), round(x + 0.02, 4), round(y + 0.02, 4))


def dets(label: str, n: int, score: float) -> list[Detection]:
    """n detecciones de `label` con `score`, cada una en su propia caja (sin solapes)."""
    return [Detection(label, score, _box()) for _ in range(n)]


def run(detections: list[Detection]):
    return diagnose(detections, BANDS, DEFAULT_OVERLAP_IOU, CRITERIA)


# ---------------------------------------------------------------------------
# Elección
# ---------------------------------------------------------------------------


def test_ejemplo_del_documento():
    detections = (
        dets("Sano", 120, 0.78)
        + dets("Clorosis", 6, 0.62)
        + dets("Daño fúngico", 3, 0.45)
        + dets("Plaga foliar", 1, 0.70)
    )
    result = run(detections)

    assert result.state == "Clorosis"
    assert result.severity == "Baja"
    assert result.confidence == "Media"
    assert result.mean_score == pytest.approx(0.62)
    # Las detecciones entregadas son todas las válidas, de todas las clases.
    assert len(result.detections) == 130


def test_empate_de_relevancia_gana_la_criticidad():
    # Clorosis: severidad Baja (6) + confianza Media (0,62) = 3
    # Daño fúngico: severidad Media (4) + confianza Baja (0,45) = 3, y es más crítico.
    result = run(dets("Clorosis", 6, 0.62) + dets("Daño fúngico", 4, 0.45))
    assert result.state == "Daño fúngico"
    assert result.severity == "Media"
    assert result.confidence == "Baja"


def test_gana_la_mayor_relevancia_aunque_sea_menos_critico():
    # Estrés solar: Alta (20) + Alta (0,75) = 6 le gana a Daño fúngico: Baja (2) + Baja (0,40) = 2.
    result = run(dets("Estrés solar", 20, 0.75) + dets("Daño fúngico", 2, 0.40))
    assert result.state == "Estrés solar"
    assert (result.severity, result.confidence) == ("Alta", "Alta")


def test_solo_hojas_sanas():
    result = run(dets("Sano", 50, 0.85))
    assert result.state == "Sano"
    assert result.severity is None
    assert result.confidence == "Alta"
    assert result.mean_score == pytest.approx(0.85)


def test_anomalo_bajo_el_minimo_queda_fuera():
    result = run(dets("Sano", 10, 0.65) + dets("Plaga foliar", 1, 0.90))
    assert result.state == "Sano"
    assert result.confidence == "Baja"
    # La plaga no alcanzó el mínimo, pero su caja válida igual se entrega.
    assert sum(d.label == "Plaga foliar" for d in result.detections) == 1


def test_sin_detecciones_validas_es_no_concluyente():
    # Sano bajo su umbral (0,60) y Clorosis bajo el suyo (0,40).
    result = run(dets("Sano", 30, 0.55) + dets("Clorosis", 10, 0.30))
    assert result.state == "No concluyente"
    assert result.severity is None
    assert result.confidence is None
    assert result.mean_score is None
    assert result.detections == ()


def test_sin_detecciones_es_no_concluyente():
    assert run([]).state == "No concluyente"


def test_filtro_por_umbral_de_clase():
    # Mismo score 0,50: Clorosis (umbral 0,40) es válida, Sano (umbral 0,60) no.
    result = run(dets("Clorosis", 1, 0.50) + dets("Sano", 1, 0.50))
    assert [d.label for d in result.detections] == ["Clorosis"]


def test_estado_anomalo_sin_criterio():
    incompleto = Criteria(
        severity_bands={k: v for k, v in CRITERIA.severity_bands.items() if k != "Clorosis"},
        criticality=("Daño fúngico", "Plaga foliar", "Estrés solar"),
    )
    with pytest.raises(MissingCriteriaError, match="Clorosis"):
        diagnose(dets("Clorosis", 6, 0.62), BANDS, DEFAULT_OVERLAP_IOU, incompleto)


# ---------------------------------------------------------------------------
# Solapes
# ---------------------------------------------------------------------------

HOJA = (0.10, 0.10, 0.30, 0.30)


def test_solape_gana_el_nivel_de_confianza():
    # Sano alta (0,85) contra Clorosis media (0,60): gana Sano aunque Clorosis sea más crítica.
    result = run([Detection("Clorosis", 0.60, HOJA), Detection("Sano", 0.85, HOJA)])
    assert [d.label for d in result.detections] == ["Sano"]


def test_solape_empate_de_nivel_gana_la_criticidad():
    # Sano 0,82 (alta en Sano) y Clorosis 0,72 (alta en Clorosis) sobre la misma hoja.
    result = run([Detection("Sano", 0.82, HOJA), Detection("Clorosis", 0.72, HOJA)])
    assert [d.label for d in result.detections] == ["Clorosis"]


def test_solape_empate_de_nivel_y_criticidad_gana_el_score():
    result = run([Detection("Clorosis", 0.58, HOJA), Detection("Clorosis", 0.66, HOJA)])
    assert [d.score for d in result.detections] == [0.66]


def test_una_invalida_no_elimina_a_una_valida():
    # Sano 0,55 está bajo su umbral (0,60): el filtro la descarta antes de resolver solapes.
    result = run([Detection("Sano", 0.55, HOJA), Detection("Clorosis", 0.50, HOJA)])
    assert [d.label for d in result.detections] == ["Clorosis"]


def test_iou_bajo_conserva_las_dos():
    otra = (0.20, 0.10, 0.40, 0.30)  # IoU con HOJA = 1/3
    assert iou(HOJA, otra) < DEFAULT_OVERLAP_IOU
    result = run([Detection("Sano", 0.82, HOJA), Detection("Clorosis", 0.72, otra)])
    assert sorted(d.label for d in result.detections) == ["Clorosis", "Sano"]


def test_iou_en_el_umbral_suprime():
    # IoU exactamente 0,6 → se suprime (IoU ≥ umbral).
    a = (0.0, 0.0, 1.0, 1.0)
    b = (0.0, 0.0, 0.6, 1.0)
    assert iou(a, b) == pytest.approx(0.6)
    result = diagnose(
        [Detection("Sano", 0.90, a), Detection("Sano", 0.85, b)], BANDS, 0.6, CRITERIA
    )
    assert len(result.detections) == 1
