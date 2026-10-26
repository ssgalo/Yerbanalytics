"""
tiling.py — Grilla de tiles y unión de las cajas de los tiles en una sola imagen.

Lógica PURA: no hace I/O ni importa ultralytics ni Pillow, igual que diagnosis.py; se prueba sin
checkpoint. Trabaja en PÍXELES de la imagen recortada y normaliza al final: normalizar por tile y
volver a escalar suma error de redondeo justo en los bordes, que es donde decide la fusión.

Pasos (design D3/D4 de openspec/changes/add-tiling-inferencia):

  1. `tile_grid`: tiles del mismo tamaño con solapamiento; el último de cada eje apoyado en el borde.
  2. `to_image_coords`: cajas del tile (píxeles del tile) → píxeles de la imagen recortada.
  3. `merge_across_tiles`: fusión de las vistas de una misma hoja cortada por un borde compartido.
  4. `normalize`: cajas sobre el tamaño de la imagen recortada, a 4 decimales → `Detection`.

La fusión no reemplaza a los solapes de diagnosis.py: dos vistas ENTERAS de una hoja que cae en la
franja compartida no tocan el borde, no se fusionan acá y las resuelve `diagnose()`.
"""

from __future__ import annotations

import logging
import math
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

from diagnosis import Detection

logger = logging.getLogger(__name__)

# Decimales con que viajan las coordenadas (contrato D12 de add-diagnostico-por-detecciones).
_DECIMALS = 4

PixelBox = tuple[float, float, float, float]


class TilingError(ValueError):
    """La grilla no se puede armar sobre esta imagen: la captura no se diagnostica."""


@dataclass(frozen=True)
class Tile:
    """Un tile de la grilla, en píxeles de la imagen recortada."""

    row: int
    column: int
    x: int
    y: int
    width: int
    height: int

    @property
    def right(self) -> int:
        return self.x + self.width

    @property
    def bottom(self) -> int:
        return self.y + self.height


@dataclass(frozen=True)
class PixelDetection:
    """Una caja del detector en píxeles xyxy (del tile o de la imagen recortada, según el paso)."""

    label: str
    score: float
    box: PixelBox


@dataclass(frozen=True)
class TiledDetection:
    """
    Una caja en píxeles de la imagen recortada, con los tiles de los que salió.

    Una caja recién trasladada viene de un tile; una fusionada, de todos los de sus partes.
    """

    detection: PixelDetection
    tiles: frozenset[Tile]


# ---------------------------------------------------------------------------
# Grilla
# ---------------------------------------------------------------------------


def _axis(length: int, count: int, overlap: int) -> tuple[int, list[int]]:
    """Tamaño de tile y posiciones sobre un eje (design D3). Con un solo tile no hay solapamiento."""
    if count == 1:
        return length, [0]
    size = math.ceil((length + (count - 1) * overlap) / count)
    step = (length - size) / (count - 1)
    if step <= 0:
        raise TilingError(
            f"la grilla no deja avance entre tiles ({count} tiles de {size} px sobre {length} px)"
        )
    # Redondeo hacia arriba en los .5 (no el bancario de round()): posiciones estables y simétricas.
    return size, [math.floor(i * step + 0.5) for i in range(count)]


def tile_grid(width: int, height: int, rows: int, columns: int, overlap: int) -> list[Tile]:
    """
    Tiles que cubren la imagen recortada de `width`×`height`, por filas.

    Raises:
        TilingError: si la configuración no deja avance entre tiles vecinos en algún eje. El
        mensaje nombra las variables de la grilla.
    """
    try:
        tile_w, xs = _axis(width, columns, overlap)
        tile_h, ys = _axis(height, rows, overlap)
    except TilingError as exc:
        raise TilingError(
            f"TILES_FILAS={rows}, TILES_COLUMNAS={columns} y TILES_SOLAPAMIENTO_PX={overlap} no "
            f"sirven para una imagen recortada de {width}×{height}: {exc}."
        ) from None
    return [
        Tile(row, column, x, y, tile_w, tile_h)
        for row, y in enumerate(ys)
        for column, x in enumerate(xs)
    ]


# ---------------------------------------------------------------------------
# Traslado
# ---------------------------------------------------------------------------


def to_image_coords(tile: Tile, detections: Iterable[PixelDetection]) -> list[TiledDetection]:
    """Paso 2: suma la posición del tile a cajas en píxeles del tile."""
    return [
        TiledDetection(
            PixelDetection(
                d.label,
                d.score,
                (d.box[0] + tile.x, d.box[1] + tile.y, d.box[2] + tile.x, d.box[3] + tile.y),
            ),
            frozenset({tile}),
        )
        for d in detections
    ]


# ---------------------------------------------------------------------------
# Fusión entre tiles vecinos
# ---------------------------------------------------------------------------


def _area(box: PixelBox) -> float:
    return max(0.0, box[2] - box[0]) * max(0.0, box[3] - box[1])


def ios(a: PixelBox, b: PixelBox) -> float:
    """Intersección sobre el área de la caja más chica."""
    ix1, iy1 = max(a[0], b[0]), max(a[1], b[1])
    ix2, iy2 = min(a[2], b[2]), min(a[3], b[3])
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    smaller = min(_area(a), _area(b))
    return inter / smaller if smaller > 0.0 else 0.0


def _are_neighbors(t: Tile, u: Tile) -> bool:
    """Comparten un borde o una esquina."""
    return t != u and abs(t.row - u.row) <= 1 and abs(t.column - u.column) <= 1


def _touches_edge_towards(box: PixelBox, tile: Tile, other: Tile, tolerance: int) -> bool:
    """La caja toca alguno de los bordes de `tile` que dan hacia `other`."""
    x1, y1, x2, y2 = box
    return (
        (other.column > tile.column and x2 >= tile.right - tolerance)
        or (other.column < tile.column and x1 <= tile.x + tolerance)
        or (other.row > tile.row and y2 >= tile.bottom - tolerance)
        or (other.row < tile.row and y1 <= tile.y + tolerance)
    )


def _should_merge(a: TiledDetection, b: TiledDetection, tolerance: int, min_ios: float) -> bool:
    # Dos cajas del mismo tile son dos cosas que el detector vio en una misma vista: no se fusionan.
    if a.tiles & b.tiles:
        return False
    touches = any(
        _are_neighbors(t, u)
        and (
            _touches_edge_towards(a.detection.box, t, u, tolerance)
            or _touches_edge_towards(b.detection.box, u, t, tolerance)
        )
        for t in a.tiles
        for u in b.tiles
    )
    return touches and ios(a.detection.box, b.detection.box) >= min_ios


def _merge(a: TiledDetection, b: TiledDetection) -> TiledDetection:
    """Unión de las dos cajas, con la clase y el score de la de mayor área (la que vio más de la hoja)."""
    bigger = a if _area(a.detection.box) >= _area(b.detection.box) else b
    box_a, box_b = a.detection.box, b.detection.box
    union = (
        min(box_a[0], box_b[0]), min(box_a[1], box_b[1]),
        max(box_a[2], box_b[2]), max(box_a[3], box_b[3]),
    )
    return TiledDetection(
        PixelDetection(bigger.detection.label, bigger.detection.score, union), a.tiles | b.tiles
    )


def merge_across_tiles(
    detections: Sequence[TiledDetection], edge_tolerance: int, min_ios: float
) -> list[TiledDetection]:
    """
    Paso 3 (design D4): fusiona dos cajas de tiles vecinos cuando al menos una toca el borde que
    comparten (dentro de `edge_tolerance`) y su IoS es ≥ `min_ios`. Sin mirar la clase: compara dos
    vistas de una hoja, no dos hojas. Se repite hasta que no quede ningún par, así una hoja en la
    esquina de cuatro tiles termina en una sola caja.
    """
    boxes = list(detections)
    merged = True
    while merged:
        merged = False
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                if _should_merge(boxes[i], boxes[j], edge_tolerance, min_ios):
                    boxes[i] = _merge(boxes[i], boxes[j])
                    del boxes[j]
                    merged = True
                    break
            if merged:
                break
    return boxes


# ---------------------------------------------------------------------------
# Normalización
# ---------------------------------------------------------------------------


def normalize(detections: Iterable[TiledDetection], width: int, height: int) -> list[Detection]:
    """Paso 4: cajas normalizadas sobre la imagen recortada, a 4 decimales y dentro de [0, 1]."""
    result: list[Detection] = []
    for item in detections:
        d = item.detection
        x1, y1, x2, y2 = (
            round(min(max(v / size, 0.0), 1.0), _DECIMALS)
            for v, size in zip(d.box, (width, height, width, height))
        )
        if not (x1 < x2 and y1 < y2):
            # Una caja de menos de una diezmilésima de la imagen no es una hoja, y el backend
            # rechazaría el alta entera por una caja degenerada.
            logger.warning("Se descarta una caja degenerada tras normalizar: %s", d.box)
            continue
        result.append(Detection(d.label, round(d.score, _DECIMALS), (x1, y1, x2, y2)))
    return result
