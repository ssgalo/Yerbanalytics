"""Tests de la grilla de tiles y la unión de cajas entre tiles (lógica pura, sin YOLO)."""

from __future__ import annotations

import pytest

from diagnosis import ConfidenceBands, Criteria, SeverityBands, diagnose
from settings import DEFAULT_CONFIDENCE_BANDS, DEFAULT_OVERLAP_IOU
from tiling import (
    PixelDetection,
    Tile,
    TiledDetection,
    TilingError,
    merge_across_tiles,
    normalize,
    tile_grid,
    to_image_coords,
)

TOLERANCE = 6
MIN_IOS = 0.5

# Grilla de fábrica sobre la región de la cámara actual (802×1074, 3×2, 130 px).
GRID = tile_grid(802, 1074, rows=3, columns=2, overlap=130)
TILES = {(t.row, t.column): t for t in GRID}


def _in(tile: Tile, box: tuple[float, float, float, float], label="Clorosis", score=0.6):
    """Una caja dada en píxeles de la imagen recortada, vista desde `tile`."""
    assert tile.x <= box[0] and box[2] <= tile.right and tile.y <= box[1] and box[3] <= tile.bottom
    return TiledDetection(PixelDetection(label, score, box), frozenset({tile}))


def _merge(*items):
    return merge_across_tiles(list(items), TOLERANCE, MIN_IOS)


# ---------------------------------------------------------------------------
# Grilla
# ---------------------------------------------------------------------------


def test_grilla_de_fabrica():
    assert len(GRID) == 6
    assert {(t.width, t.height) for t in GRID} == {(466, 445)}
    assert sorted({t.x for t in GRID}) == [0, 336]
    assert sorted({t.y for t in GRID}) == [0, 315, 629]


def test_los_ultimos_tiles_se_apoyan_en_el_borde():
    assert max(t.right for t in GRID) == 802
    assert max(t.bottom for t in GRID) == 1074


def test_un_solo_tile_es_la_imagen_entera():
    assert tile_grid(802, 1074, 1, 1, 130) == [Tile(0, 0, 0, 0, 802, 1074)]


def test_una_sola_columna_no_solapa_en_ese_eje():
    grid = tile_grid(802, 1074, rows=2, columns=1, overlap=100)
    assert {(t.x, t.width) for t in grid} == {(0, 802)}
    assert [t.y for t in grid] == [0, 487]


def test_solapamiento_sin_avance_es_error():
    with pytest.raises(TilingError, match="TILES_SOLAPAMIENTO_PX"):
        tile_grid(802, 1074, rows=3, columns=2, overlap=900)


# ---------------------------------------------------------------------------
# Traslado y normalización
# ---------------------------------------------------------------------------


def test_caja_del_tile_de_abajo_a_la_derecha():
    tile = TILES[(2, 1)]
    [moved] = to_image_coords(tile, [PixelDetection("Sano", 0.8, (10, 20, 110, 120))])
    assert moved.detection.box == (346, 649, 446, 749)
    [detection] = normalize([moved], 802, 1074)
    assert detection.box == (0.4314, 0.6043, 0.5561, 0.6974)
    assert detection.score == 0.8


def test_normalizar_descarta_cajas_degeneradas():
    item = TiledDetection(PixelDetection("Sano", 0.8, (10, 10, 10.01, 50)), frozenset())
    assert normalize([item], 802, 1074) == []


# ---------------------------------------------------------------------------
# Unión entre tiles
# ---------------------------------------------------------------------------


def test_hoja_cortada_y_hoja_entera():
    # Hoja de 380 a 520 en x: r0c0 termina en 466 (la corta), r0c1 empieza en 336 (la ve entera).
    cut = _in(TILES[(0, 0)], (380, 100, 466, 200), label="Sano", score=0.3)
    whole = _in(TILES[(0, 1)], (380, 100, 520, 200), label="Clorosis", score=0.7)
    [merged] = _merge(cut, whole)
    assert merged.detection == PixelDetection("Clorosis", 0.7, (380, 100, 520, 200))


def test_dos_pedazos_de_una_hoja():
    # Hoja de 250 a 560 en x, más grande que el solapamiento: cortada en los dos tiles.
    left = _in(TILES[(0, 0)], (250, 100, 465, 220), label="Clorosis", score=0.5)
    right = _in(TILES[(0, 1)], (337, 102, 560, 218), label="Sano", score=0.4)
    [merged] = _merge(left, right)
    assert merged.detection.box == (250, 100, 560, 220)
    assert merged.detection.label == "Sano"  # el pedazo de mayor área
    assert merged.tiles == {TILES[(0, 0)], TILES[(0, 1)]}


def test_hojas_distintas_que_se_pisan_no_se_fusionan():
    # Una hoja chica dentro de una grande, las dos lejos de los bordes compartidos.
    big = _in(TILES[(0, 0)], (360, 100, 440, 200))
    small = _in(TILES[(0, 1)], (380, 120, 420, 160))
    assert len(_merge(big, small)) == 2


def test_hoja_en_la_esquina_de_cuatro_tiles():
    # Hoja de (420, 400) a (520, 500): cruza los bordes x=466/336 e y=445/315.
    pieces = [
        _in(TILES[(0, 0)], (420, 400, 466, 445), score=0.3),
        _in(TILES[(0, 1)], (420, 400, 520, 445), score=0.4),
        _in(TILES[(1, 0)], (420, 400, 466, 500), score=0.5),
        _in(TILES[(1, 1)], (420, 400, 520, 500), score=0.8),
    ]
    [merged] = _merge(*pieces)
    assert merged.detection.box == (420, 400, 520, 500)
    assert merged.detection.score == 0.8


def test_dos_cajas_del_mismo_tile_no_se_fusionan():
    a = _in(TILES[(0, 0)], (400, 100, 466, 200))
    b = _in(TILES[(0, 0)], (410, 110, 466, 190))
    assert len(_merge(a, b)) == 2


def test_tiles_que_no_son_vecinos_no_se_fusionan():
    # r0c0 y r2c0 no se tocan: aunque las cajas coincidieran, no son vistas de la misma hoja.
    a = TiledDetection(PixelDetection("Sano", 0.8, (10, 440, 60, 445)), frozenset({TILES[(0, 0)]}))
    b = TiledDetection(PixelDetection("Sano", 0.8, (10, 440, 60, 445)), frozenset({TILES[(2, 0)]}))
    assert len(_merge(a, b)) == 2


def test_hoja_entera_repetida_en_la_franja_la_resuelve_el_diagnostico():
    # Hoja de 360 a 440 en x: entera en r0c0 y en r0c1, sin tocar ningún borde compartido.
    a = _in(TILES[(0, 0)], (360, 100, 440, 180), label="Sano", score=0.82)
    b = _in(TILES[(0, 1)], (362, 101, 441, 181), label="Sano", score=0.80)
    merged = _merge(a, b)
    assert len(merged) == 2

    bands = {state: ConfidenceBands(*cuts) for state, cuts in DEFAULT_CONFIDENCE_BANDS.items()}
    criteria = Criteria(
        severity_bands={s: SeverityBands(2, 4, 8) for s in bands if s != "Sano"},
        criticality=("Daño fúngico", "Plaga foliar", "Clorosis", "Estrés solar"),
    )
    result = diagnose(normalize(merged, 802, 1074), bands, DEFAULT_OVERLAP_IOU, criteria)
    assert result.state == "Sano"
    assert len(result.detections) == 1
