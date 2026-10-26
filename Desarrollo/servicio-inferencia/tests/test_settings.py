"""Tests de los parámetros del equipo (settings.py)."""

from __future__ import annotations

import logging

import pytest

from diagnosis import ConfidenceBands
from settings import (
    DEFAULT_CONFIDENCE_BANDS,
    DEFAULT_CROP,
    SERVICE_DIR,
    CropMargins,
    SettingsError,
    TilingSettings,
    class_key,
    load_team_settings,
    load_tiling_settings,
    parse_model_classes,
    resolve_service_path,
)

STATES = ["Clorosis", "Daño fúngico", "Estrés solar", "Plaga foliar", "Sano"]

ALL_VARS = {
    "YOLO_CONF_GLOBAL": "0.10",
    "SOLAPAMIENTO_IOU": "0.60",
    "BANDAS_CONFIANZA_SANO": "0.60,0.70,0.80",
    "BANDAS_CONFIANZA_CLOROSIS": "0.40,0.55,0.70",
    "BANDAS_CONFIANZA_ESTRES_SOLAR": "0.40,0.55,0.70",
    "BANDAS_CONFIANZA_PLAGA_FOLIAR": "0.35,0.50,0.65",
    "BANDAS_CONFIANZA_DANO_FUNGICO": "0.35,0.50,0.65",
}


def test_clave_de_clase_sin_acentos_ni_espacios():
    assert class_key("Daño fúngico") == "DANO_FUNGICO"
    assert class_key("Estrés solar") == "ESTRES_SOLAR"
    assert class_key("Sano") == "SANO"


def test_defaults():
    team = load_team_settings(STATES, env={})
    assert team.yolo_conf_global == 0.10
    assert team.overlap_iou == 0.60
    assert team.confidence_bands == {
        s: ConfidenceBands(*DEFAULT_CONFIDENCE_BANDS[s]) for s in STATES
    }


def test_variables_definidas():
    env = dict(ALL_VARS, BANDAS_CONFIANZA_SANO="0.65,0.75,0.85", SOLAPAMIENTO_IOU="0.5")
    team = load_team_settings(STATES, env=env)
    assert team.confidence_bands["Sano"] == ConfidenceBands(0.65, 0.75, 0.85)
    assert team.overlap_iou == 0.5


def test_variable_ausente_toma_default_y_avisa(caplog):
    env = {k: v for k, v in ALL_VARS.items() if k != "BANDAS_CONFIANZA_DANO_FUNGICO"}
    with caplog.at_level(logging.WARNING):
        team = load_team_settings(STATES, env=env)
    assert team.confidence_bands["Daño fúngico"] == ConfidenceBands(0.35, 0.50, 0.65)
    avisos = [r.getMessage() for r in caplog.records if r.levelno == logging.WARNING]
    assert len(avisos) == 1
    assert "BANDAS_CONFIANZA_DANO_FUNGICO" in avisos[0]


@pytest.mark.parametrize(
    "valor",
    [
        "0.70,0.55,0.40",  # decrecientes
        "0.40,0.40,0.70",  # no estrictamente crecientes
        "0.40,0.55",       # dos cortes
        "0.40,0.55,1.20",  # fuera de (0, 1]
        "0,0.55,0.70",     # cero no vale
        "a,b,c",           # no numéricos
    ],
)
def test_bandas_mal_formadas_son_error(valor):
    env = dict(ALL_VARS, BANDAS_CONFIANZA_CLOROSIS=valor)
    with pytest.raises(SettingsError, match="BANDAS_CONFIANZA_CLOROSIS"):
        load_team_settings(STATES, env=env)


def test_umbral_global_por_encima_de_un_umbral_de_clase():
    env = dict(ALL_VARS, YOLO_CONF_GLOBAL="0.38")  # mayor que 0,35 de Plaga foliar y Daño fúngico
    with pytest.raises(SettingsError, match="YOLO_CONF_GLOBAL"):
        load_team_settings(STATES, env=env)


def test_umbral_global_igual_al_menor_umbral_es_valido():
    env = dict(ALL_VARS, YOLO_CONF_GLOBAL="0.35")
    assert load_team_settings(STATES, env=env).yolo_conf_global == 0.35


def test_iou_fuera_de_rango():
    with pytest.raises(SettingsError, match="SOLAPAMIENTO_IOU"):
        load_team_settings(STATES, env=dict(ALL_VARS, SOLAPAMIENTO_IOU="1.5"))


def test_clase_sin_default_exige_su_variable():
    with pytest.raises(SettingsError, match="BANDAS_CONFIANZA_ACARO"):
        load_team_settings(["Ácaro", "Sano"], env={})


def test_model_classes():
    assert parse_model_classes("Clorosis, Daño fúngico,Estrés solar,Plaga foliar,Sano") == STATES
    with pytest.raises(SettingsError):
        parse_model_classes("Clorosis,Hongo")
    with pytest.raises(SettingsError):
        parse_model_classes("Sano,No concluyente")
    with pytest.raises(SettingsError):
        parse_model_classes("Sano,Sano")


def test_ruta_relativa_contra_la_carpeta_del_servicio():
    assert resolve_service_path("modelo_final.pt") == (SERVICE_DIR / "modelo_final.pt").resolve()
    assert resolve_service_path("../backend/capturas") == (SERVICE_DIR.parent / "backend" / "capturas").resolve()
    absoluta = SERVICE_DIR / "x.pt"
    assert resolve_service_path(str(absoluta)) == absoluta


# ---------------------------------------------------------------------------
# Recorte y tiling
# ---------------------------------------------------------------------------


def test_tiling_de_fabrica_avisa_cada_variable(caplog):
    with caplog.at_level(logging.WARNING):
        tiling = load_tiling_settings(env={})
    assert tiling == TilingSettings(CropMargins(278, 366, 0, 0), 3, 2, 130, 6, 0.5)
    avisos = " ".join(r.getMessage() for r in caplog.records if r.levelno == logging.WARNING)
    for name in [*DEFAULT_CROP, "TILES_FILAS", "TILES_COLUMNAS", "TILES_SOLAPAMIENTO_PX",
                 "TILES_TOLERANCIA_BORDE_PX", "TILES_FUSION_IOS"]:
        assert name in avisos


def test_tiling_definido():
    env = {
        "RECORTE_IZQUIERDA": "10", "RECORTE_ARRIBA": "20", "RECORTE_DERECHA": "30",
        "RECORTE_ABAJO": "40", "TILES_FILAS": "1", "TILES_COLUMNAS": "1",
        "TILES_SOLAPAMIENTO_PX": "0", "TILES_TOLERANCIA_BORDE_PX": "0", "TILES_FUSION_IOS": "1",
    }
    assert load_tiling_settings(env=env) == TilingSettings(CropMargins(10, 20, 30, 40), 1, 1, 0, 0, 1.0)


@pytest.mark.parametrize(
    ("name", "value"),
    [
        ("RECORTE_ARRIBA", "-10"),
        ("RECORTE_ARRIBA", "abc"),
        ("RECORTE_IZQUIERDA", "27.5"),
        ("TILES_FILAS", "0"),
        ("TILES_COLUMNAS", "-1"),
        ("TILES_SOLAPAMIENTO_PX", "-5"),
        ("TILES_TOLERANCIA_BORDE_PX", "x"),
        ("TILES_FUSION_IOS", "1.5"),
        ("TILES_FUSION_IOS", "0"),
        ("TILES_FUSION_IOS", "medio"),
    ],
)
def test_tiling_mal_formado_es_error(name, value):
    with pytest.raises(SettingsError, match=name):
        load_tiling_settings(env={name: value})


def test_tolerancia_no_menor_que_el_solapamiento():
    with pytest.raises(SettingsError, match="TILES_TOLERANCIA_BORDE_PX"):
        load_tiling_settings(env={"TILES_SOLAPAMIENTO_PX": "6", "TILES_TOLERANCIA_BORDE_PX": "6"})
