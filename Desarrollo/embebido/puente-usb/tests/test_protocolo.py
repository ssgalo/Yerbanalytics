"""Protocolo de líneas entre la placa y el puente (design D3 de add-transporte-usb-nodo)."""
import pytest

from protocolo import (
    LARGO_MAXIMO,
    Publicacion,
    Suscripcion,
    armar_latido,
    armar_mensaje,
    leer_linea,
)


def test_suscripcion():
    assert leer_linea("@S 1 nursery/rail/command") == Suscripcion(qos=1, topic="nursery/rail/command")


def test_publicacion_con_payload_con_espacios():
    linea = '@P 0 nursery/zone/MZ-1/telemetry {"mac": "DC:DA", "metrics": {"temp": 21.5}}'
    assert leer_linea(linea) == Publicacion(
        qos=0, topic="nursery/zone/MZ-1/telemetry", payload='{"mac": "DC:DA", "metrics": {"temp": 21.5}}'
    )


def test_tolera_el_retorno_de_carro():
    assert leer_linea("@S 0 a/b\r") == Suscripcion(qos=0, topic="a/b")


@pytest.mark.parametrize(
    "linea",
    [
        "",
        "ESP-ROM:esp32s3-20210327",
        "rst:0x1 (POWERON),boot:0x8 (SPI_FAST_FLASH_BOOT)",
        "[sensado] Lectura tomada (ts=12)",
        "@",
        "@X 0 a/b",
        "@S",
        "@S 1",
        "@S uno a/b",
        "@S 3 a/b",
        "@S 1 a/b extra",
        "@P 0 a/b",
        "@P 0",
        "@P x a/b {}",
        "@P 0  {}",
    ],
)
def test_descarta_lo_que_no_es_protocolo(linea):
    assert leer_linea(linea) is None


def test_descarta_las_lineas_demasiado_largas():
    payload = "x" * LARGO_MAXIMO
    assert leer_linea(f"@P 0 a/b {payload}") is None


def test_acepta_una_linea_justo_en_el_maximo():
    encabezado = "@P 0 a/b "
    payload = "x" * (LARGO_MAXIMO - len(encabezado))
    assert leer_linea(encabezado + payload) == Publicacion(qos=0, topic="a/b", payload=payload)


def test_armar_latido():
    assert armar_latido(1793239177, "a1b2") == "@H 1793239177 a1b2\n"


def test_armar_mensaje_conserva_el_payload():
    payload = '{"commandId":"x","actuador":"valve","accion":"ON","parametros":{"durationSec":20}}'
    assert armar_mensaje("nursery/zone/MZ-1/sector/MZ-1-001/command", payload) == (
        f"@M nursery/zone/MZ-1/sector/MZ-1-001/command {payload}\n"
    )


def test_armar_mensaje_aplana_los_saltos_de_linea():
    # Un salto de línea partiría el mensaje en dos líneas del protocolo.
    assert armar_mensaje("a/b", '{\n"x": 1\n}') == '@M a/b { "x": 1 }\n'


def test_armar_mensaje_rechaza_lo_que_no_entra():
    with pytest.raises(ValueError):
        armar_mensaje("a/b", "x" * LARGO_MAXIMO)


def test_armar_corte():
    from protocolo import armar_corte

    assert armar_corte() == "@X\n"
