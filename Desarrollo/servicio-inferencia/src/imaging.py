"""
imaging.py — Normalización y recorte de la captura antes de dividirla en tiles.

Lo único del pipeline que toca Pillow (design D2 de add-tiling-inferencia). El JPEG de disco no se
modifica: todo pasa en memoria.

  - `load_normalized`: orienta según el tag EXIF y convierte a RGB. Todas las coordenadas del
    pipeline (recorte, tiles y cajas) se refieren a esta imagen, la misma que muestra el navegador.
  - `crop`: quita los márgenes de RECORTE_* y devuelve también el recorte normalizado sobre la
    imagen orientada, que viaja con el diagnóstico.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageOps

from settings import CropMargins
from tiling import TilingError

# Decimales con que viaja el recorte (el mismo formato que una caja).
_DECIMALS = 4

NormalizedBox = tuple[float, float, float, float]


class CropError(TilingError):
    """El recorte no entra en la imagen: la captura no se diagnostica."""


def normalize_image(image: Image.Image) -> Image.Image:
    """Orienta según EXIF (sin tag, la deja como está) y convierte a RGB."""
    oriented = ImageOps.exif_transpose(image)
    return oriented if oriented.mode == "RGB" else oriented.convert("RGB")


def load_normalized(path: Path) -> Image.Image:
    """
    Lee la captura y la normaliza.

    Raises:
        FileNotFoundError: si el archivo no existe en disco.
        ValueError: si el archivo existe pero no se puede leer como imagen.
    """
    if not path.is_file():
        raise FileNotFoundError(f"Imagen no encontrada en disco: {path}")
    try:
        with Image.open(path) as image:
            image.load()
            return normalize_image(image)
    except (OSError, Image.DecompressionBombError) as exc:
        raise ValueError(f"No se pudo leer la imagen (¿JPEG truncado o vacío?): {path}: {exc}") from exc


def crop(image: Image.Image, margins: CropMargins) -> tuple[Image.Image, NormalizedBox]:
    """
    Quita los márgenes y devuelve la región junto con su caja normalizada sobre `image`.

    No recorta "lo que se pueda": eso analizaría una región distinta sin avisar.

    Raises:
        CropError: si los márgenes de un eje suman el ancho o el alto de la imagen o más.
    """
    width, height = image.size
    if margins.left + margins.right >= width or margins.top + margins.bottom >= height:
        raise CropError(
            f"el recorte no entra en la imagen de {width}×{height}: RECORTE_IZQUIERDA={margins.left}, "
            f"RECORTE_DERECHA={margins.right}, RECORTE_ARRIBA={margins.top}, "
            f"RECORTE_ABAJO={margins.bottom}."
        )
    box = (margins.left, margins.top, width - margins.right, height - margins.bottom)
    normalized = (
        round(box[0] / width, _DECIMALS), round(box[1] / height, _DECIMALS),
        round(box[2] / width, _DECIMALS), round(box[3] / height, _DECIMALS),
    )
    return image.crop(box), normalized
