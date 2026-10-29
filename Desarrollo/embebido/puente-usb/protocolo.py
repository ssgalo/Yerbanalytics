"""
Protocolo de líneas entre el nodo y el puente (design D3 de add-transporte-usb-nodo).

Una línea por mensaje, con prefijo `@`. Los tópicos no tienen espacios; el payload es el resto de la línea.

    placa → PC   @S <qos> <tópico>              suscribirse
    placa → PC   @P <qos> <tópico> <payload>    publicar
    PC → placa   @H <epoch> <sesión>            latido del puente, con la hora
    PC → placa   @M <tópico> <payload>          mensaje de un tópico suscripto
    PC → placa   @X                             el puente perdió el broker: la placa se da por desconectada

Lo que no respeta el formato se descarta: por el mismo UART imprime la ROM al arrancar, y una línea dañada no
debe volverse un comando. Lógica pura: nada de puerto ni de broker.
"""
from __future__ import annotations

from dataclasses import dataclass

# Espejo de USB_LINEA_MAX del firmware (transporte_usb/net_mqtt_usb.cpp): MQTT_BUFFER_SIZE (1024) más el
# encabezado. Una línea más larga se descarta entera, nunca se publica un pedazo.
LARGO_MAXIMO = 1200

_QOS_VALIDAS = {0, 1, 2}


@dataclass(frozen=True)
class Suscripcion:
    qos: int
    topic: str


@dataclass(frozen=True)
class Publicacion:
    qos: int
    topic: str
    payload: str


def _qos(texto: str) -> int | None:
    if not texto.isdigit():
        return None
    qos = int(texto)
    return qos if qos in _QOS_VALIDAS else None


def leer_linea(linea: str) -> Suscripcion | Publicacion | None:
    """Interpreta una línea que manda la placa. Devuelve `None` si no es del protocolo."""
    linea = linea.rstrip("\r\n")
    if len(linea) > LARGO_MAXIMO or not linea.startswith("@"):
        return None

    if linea.startswith("@S "):
        partes = linea[3:].split(" ")
        if len(partes) != 2 or not partes[1]:
            return None
        qos = _qos(partes[0])
        return None if qos is None else Suscripcion(qos=qos, topic=partes[1])

    if linea.startswith("@P "):
        partes = linea[3:].split(" ", 2)
        if len(partes) != 3 or not partes[1] or not partes[2]:
            return None
        qos = _qos(partes[0])
        return None if qos is None else Publicacion(qos=qos, topic=partes[1], payload=partes[2])

    return None


def armar_latido(epoch: int, sesion: str) -> str:
    """Latido del puente: la placa lo usa para saberse conectada y para ajustar la hora."""
    return f"@H {epoch} {sesion}\n"


def armar_corte() -> str:
    """El puente perdió el broker: la placa deja de publicar ya, sin esperar a que venzan los latidos."""
    return "@X\n"


def armar_mensaje(topic: str, payload: str) -> str:
    """Mensaje para la placa. Un salto de línea partiría el mensaje en dos: se aplana a espacio."""
    plano = payload.replace("\r", " ").replace("\n", " ")
    linea = f"@M {topic} {plano}"
    if len(linea) > LARGO_MAXIMO:
        raise ValueError(f"el mensaje de {topic} no entra en una línea ({len(linea)} > {LARGO_MAXIMO})")
    return linea + "\n"
