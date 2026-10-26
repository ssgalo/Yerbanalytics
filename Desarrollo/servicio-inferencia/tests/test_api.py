"""Tests del cuerpo del alta (contrato D7/D12), sin backend."""

from __future__ import annotations

from api import build_payload
from diagnosis import Detection, Diagnosis

CAJA = Detection("Clorosis", 0.6234, (0.12, 0.30, 0.18, 0.37))
SANA = Detection("Sano", 0.81, (0.5, 0.5, 0.6, 0.6))


def test_alta_de_un_anomalo():
    payload = build_payload("CAP-1", Diagnosis("Clorosis", "Baja", "Media", 0.62, (CAJA, SANA)))
    assert payload == {
        "capturaId": "CAP-1",
        "estado": "Clorosis",
        "sev": "Baja",
        "confidence": "Media",
        "conf": 62.0,
        "detections": [
            {"label": "Clorosis", "score": 0.6234, "box": [0.12, 0.30, 0.18, 0.37]},
            {"label": "Sano", "score": 0.81, "box": [0.5, 0.5, 0.6, 0.6]},
        ],
    }


def test_alta_de_sano_sin_severidad():
    payload = build_payload("CAP-2", Diagnosis("Sano", None, "Alta", 0.83456, (SANA,)))
    assert "sev" not in payload
    assert payload["confidence"] == "Alta"
    assert payload["conf"] == 83.46


def test_alta_de_no_concluyente():
    payload = build_payload("CAP-3", Diagnosis("No concluyente", None, None, None, ()))
    assert payload == {"capturaId": "CAP-3", "estado": "No concluyente", "detections": []}


def test_alta_con_recorte():
    payload = build_payload(
        "CAP-4", Diagnosis("Sano", None, "Alta", 0.81, (SANA,)), (0.2574, 0.2542, 1.0, 1.0)
    )
    assert payload["crop"] == [0.2574, 0.2542, 1.0, 1.0]


def test_alta_sin_recorte_no_lleva_el_campo():
    assert "crop" not in build_payload("CAP-5", Diagnosis("Sano", None, "Alta", 0.81, (SANA,)))
