"""Tests de la normalización y el recorte de la captura (Pillow, sin YOLO)."""

from __future__ import annotations

import pytest
from PIL import Image

from imaging import CropError, crop, load_normalized, normalize_image
from settings import CropMargins

FACTORY = CropMargins(left=278, top=366, right=0, bottom=0)

# Tag EXIF de orientación.
_ORIENTATION = 0x0112


def test_exif_rotado_90_se_procesa_como_lo_muestra_el_navegador(tmp_path):
    # 6 = "rotar 90° en sentido horario para mostrar": el sensor guardó 1440×1080 apaisada.
    image = Image.new("RGB", (1440, 1080), "white")
    image.paste((255, 0, 0), (0, 0, 40, 40))
    exif = image.getexif()
    exif[_ORIENTATION] = 6
    path = tmp_path / "rotada.jpg"
    image.save(path, exif=exif, quality=100)

    normalized = load_normalized(path)
    assert normalized.size == (1080, 1440)
    # La esquina de arriba a la izquierda del sensor queda arriba a la derecha.
    red, green, _ = normalized.getpixel((1060, 20))
    assert red > 200 and green < 60


def test_sin_exif_queda_tal_cual(tmp_path):
    path = tmp_path / "plana.jpg"
    Image.new("RGB", (1080, 1440), "white").save(path)
    assert load_normalized(path).size == (1080, 1440)


@pytest.mark.parametrize("mode", ["L", "RGBA", "CMYK"])
def test_se_convierte_a_rgb(mode):
    assert normalize_image(Image.new(mode, (20, 10))).mode == "RGB"


def test_archivo_inexistente(tmp_path):
    with pytest.raises(FileNotFoundError):
        load_normalized(tmp_path / "no-existe.jpg")


def test_archivo_que_no_es_imagen(tmp_path):
    path = tmp_path / "rota.jpg"
    path.write_bytes(b"no es un jpeg")
    with pytest.raises(ValueError):
        load_normalized(path)


def test_recorte_de_fabrica():
    region, box = crop(Image.new("RGB", (1080, 1440)), FACTORY)
    assert region.size == (802, 1074)
    assert box == (0.2574, 0.2542, 1.0, 1.0)


def test_recorte_con_los_cuatro_margenes():
    region, box = crop(Image.new("RGB", (1000, 2000)), CropMargins(100, 200, 300, 400))
    assert region.size == (600, 1400)
    assert box == (0.1, 0.1, 0.7, 0.8)


@pytest.mark.parametrize(
    "margins",
    [CropMargins(278, 366, 802, 0), CropMargins(0, 1440, 0, 0), CropMargins(500, 0, 600, 0)],
)
def test_recorte_que_no_entra_nombra_las_variables_y_el_tamano(margins):
    with pytest.raises(CropError, match="RECORTE_IZQUIERDA") as exc:
        crop(Image.new("RGB", (1080, 1440)), margins)
    assert "1080×1440" in str(exc.value)
