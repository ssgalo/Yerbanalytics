"""
Ruteo del puente entre la placa (puerto serie) y el broker MQTT (design D4, D7 y D8 de add-transporte-usb-nodo).

No abre el puerto ni el broker: recibe un objeto broker (con `conectado`, `subscribe`, `unsubscribe` y `publish`) y
una función que escribe en el puerto. Así se prueba con dobles y `puente.py` queda como E/S fina.
"""
from __future__ import annotations

import logging
import time
import uuid
from typing import Callable, Protocol

from protocolo import Publicacion, Suscripcion, armar_corte, armar_latido, armar_mensaje, leer_linea

log = logging.getLogger("puente")


class Broker(Protocol):
    conectado: bool

    def subscribe(self, topic: str, qos: int) -> None: ...

    def unsubscribe(self, topic: str) -> None: ...

    def publish(self, topic: str, payload: str, qos: int) -> None: ...


def _sesion_nueva() -> str:
    return uuid.uuid4().hex[:8]


class Nucleo:
    def __init__(
        self,
        broker: Broker,
        escribir: Callable[[str], None],
        reloj: Callable[[], float] = time.time,
        nueva_sesion: Callable[[], str] = _sesion_nueva,
    ) -> None:
        self._broker = broker
        self._escribir = escribir
        self._reloj = reloj
        self._nueva_sesion = nueva_sesion
        self._sesion: str | None = None  # nace al abrir el puerto
        # tópico → QoS que pidió la placa. Se repiten al reconectar al broker y se olvidan al reabrir el puerto.
        self._suscripciones: dict[str, int] = {}

    def puerto_abierto(self) -> None:
        """El puerto se (re)abrió: sesión nueva, así la placa reenvía sus suscripciones (D4)."""
        for topic in self._suscripciones:
            self._broker.unsubscribe(topic)
        self._suscripciones.clear()
        self._sesion = self._nueva_sesion()
        log.info("Puerto abierto, sesión %s", self._sesion)

    def broker_conectado(self) -> None:
        """El broker (re)conectó: la sesión MQTT empieza limpia, se repiten las suscripciones."""
        for topic, qos in self._suscripciones.items():
            self._broker.subscribe(topic, qos)

    def broker_desconectado(self) -> None:
        """Avisa el corte a la placa: si no, publicaría hasta 6 s creyéndose conectada y eso se perdería."""
        if self._sesion is not None:
            self._escribir(armar_corte())

    def linea_de_la_placa(self, linea: str) -> None:
        mensaje = leer_linea(linea)
        if isinstance(mensaje, Suscripcion):
            self._suscripciones[mensaje.topic] = mensaje.qos
            if self._broker.conectado:
                self._broker.subscribe(mensaje.topic, mensaje.qos)
            log.info("Suscripción de la placa: %s (QoS %d)", mensaje.topic, mensaje.qos)
        elif isinstance(mensaje, Publicacion):
            if self._broker.conectado:
                self._broker.publish(mensaje.topic, mensaje.payload, mensaje.qos)
                log.debug("Placa → broker: %s", mensaje.topic)
            else:
                # Sin broker no hay latido, así que la placa ya se considera desconectada y guarda lo suyo.
                log.warning("Sin broker: se descarta la publicación en %s", mensaje.topic)
        elif linea.strip():
            log.debug("Placa (no protocolo): %s", linea.strip())

    def mensaje_del_broker(self, topic: str, payload: bytes) -> None:
        try:
            linea = armar_mensaje(topic, payload.decode("utf-8"))
        except (UnicodeDecodeError, ValueError) as e:
            log.warning("No se entrega el mensaje de %s a la placa: %s", topic, e)
            return
        self._escribir(linea)
        log.debug("Broker → placa: %s", topic)

    def latido(self) -> None:
        """Sólo con el broker conectado: así `conectado()` de la placa refleja el camino entero (D4)."""
        if self._broker.conectado and self._sesion is not None:
            self._escribir(armar_latido(int(self._reloj()), self._sesion))
