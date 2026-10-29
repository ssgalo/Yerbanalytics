"""
Puente USB ↔ MQTT para la expo (add-transporte-usb-nodo).

Lee las líneas del protocolo que manda el nodo por el puerto serie y las publica en Mosquitto, y le entrega al nodo
los mensajes de los tópicos a los que se suscribió. Para el backend, el puente ES el nodo: mismos tópicos, mismos
payloads. La lógica vive en `nucleo.py`; acá sólo hay E/S.

Configuración por entorno (todas opcionales):
    PUENTE_PUERTO   puerto serie (por defecto, el CH343 de la placa en /dev/serial/by-id, si no /dev/ttyACM0)
    PUENTE_BAUD     baudios (115200, como USB_TRANSPORTE_BAUD del firmware)
    MQTT_HOST       broker (127.0.0.1)
    MQTT_PORT       puerto del broker (1883)
"""
from __future__ import annotations

import argparse
import errno
import glob
import logging
import os
import threading
import time

import paho.mqtt.client as mqtt
import serial

from nucleo import Nucleo
from protocolo import LARGO_MAXIMO

log = logging.getLogger("puente")

INTERVALO_LATIDO_S = 2.0
INTERVALO_REINTENTO_S = 2.0
CLIENT_ID = "yerbanalytics-puente-usb"


def puerto_por_defecto() -> str:
    candidatos = sorted(glob.glob("/dev/serial/by-id/usb-1a86_*"))
    return candidatos[0] if candidatos else "/dev/ttyACM0"


class BrokerMqtt:
    """Adaptador de paho-mqtt a lo que espera `Nucleo`."""

    def __init__(self, host: str, port: int) -> None:
        self.conectado = False
        self._cliente = mqtt.Client(callback_api_version=mqtt.CallbackAPIVersion.VERSION2, client_id=CLIENT_ID)
        self._cliente.reconnect_delay_set(min_delay=1, max_delay=5)
        self._host, self._port = host, port

    def arrancar(self, al_conectar, al_desconectar, al_recibir) -> None:
        def on_connect(_c, _u, _f, reason_code, _p):
            if reason_code.is_failure:
                log.error("El broker rechazó la conexión: %s", reason_code)
                return
            self.conectado = True
            log.info("Conectado al broker %s:%d", self._host, self._port)
            al_conectar()

        def on_disconnect(_c, _u, _f, reason_code, _p):
            self.conectado = False
            log.warning("Desconectado del broker (%s); paho reintenta solo", reason_code)
            al_desconectar()

        self._cliente.on_connect = on_connect
        self._cliente.on_disconnect = on_disconnect
        self._cliente.on_message = lambda _c, _u, msg: al_recibir(msg.topic, msg.payload)
        self._cliente.connect_async(self._host, self._port, keepalive=30)
        self._cliente.loop_start()

    def subscribe(self, topic: str, qos: int) -> None:
        self._cliente.subscribe(topic, qos)

    def unsubscribe(self, topic: str) -> None:
        if self.conectado:
            self._cliente.unsubscribe(topic)

    def publish(self, topic: str, payload: str, qos: int) -> None:
        self._cliente.publish(topic, payload, qos)


class Puerto:
    """Puerto serie que no resetea la placa al abrirse (design D8) y entrega líneas completas."""

    def __init__(self, ruta: str, baudios: int) -> None:
        self._ruta, self._baudios = ruta, baudios
        self._serie: serial.Serial | None = None
        self._pendiente = b""
        self._escritura = threading.Lock()

    @property
    def abierto(self) -> bool:
        return self._serie is not None

    def abrir(self) -> None:
        s = serial.Serial()
        s.port, s.baudrate, s.timeout, s.write_timeout = self._ruta, self._baudios, 0.1, 1.0
        # Al abrir, Linux sube DTR y RTS. El circuito de auto-reset de la placa resetea con DTR bajo y RTS alto,
        # así que el orden importa: primero RTS y después DTR. pyserial aplica al abrir DTR y después RTS, así que se
        # le pide DTR arriba y RTS abajo (pasa de 1,1 a 1,0: inofensivo) y recién después se baja DTR (0,0).
        s.dtr = True
        s.rts = False
        s.open()
        try:
            s.dtr = False
        except OSError as e:
            if e.errno != errno.ENOTTY:  # un puerto virtual (pty) no tiene líneas de módem: no hay nada que bajar
                raise
        self._serie, self._pendiente = s, b""

    def cerrar(self) -> None:
        with self._escritura:  # que el hilo de paho no escriba en un puerto que se está cerrando
            if self._serie is not None:
                try:
                    self._serie.close()
                except OSError:
                    pass
            self._serie = None

    def leer_lineas(self) -> list[str]:
        assert self._serie is not None
        self._pendiente += self._serie.read(self._serie.in_waiting or 1)
        *completas, self._pendiente = self._pendiente.split(b"\n")
        if len(self._pendiente) > 2 * LARGO_MAXIMO:
            # Basura sin salto de línea (baudios equivocados, ruido): se tira para no crecer sin límite.
            self._pendiente = b""
        return [c.decode("utf-8", errors="replace") for c in completas]

    def escribir(self, linea: str) -> None:
        with self._escritura:
            if self._serie is None:
                return  # puerto cerrado: el mensaje se pierde, como un comando MQTT con el nodo caído
            try:
                self._serie.write(linea.encode("utf-8"))
            except (serial.SerialException, OSError) as e:
                # Incluye el timeout de escritura: mejor perder un mensaje que trabar los dos hilos. El hilo principal
                # detecta el puerto caído al leer y lo reabre.
                log.warning("No se pudo escribir en el puerto (%s); se pierde el mensaje", e)


def correr(ruta: str, baudios: int, host: str, port: int) -> None:
    puerto = Puerto(ruta, baudios)
    broker = BrokerMqtt(host, port)
    candado = threading.Lock()  # el núcleo se usa desde este hilo y desde el de paho
    nucleo = Nucleo(broker, puerto.escribir)

    def al_conectar():
        with candado:
            nucleo.broker_conectado()

    def al_desconectar():
        with candado:
            nucleo.broker_desconectado()

    def al_recibir(topic, payload):
        with candado:
            nucleo.mensaje_del_broker(topic, payload)

    broker.arrancar(al_conectar, al_desconectar, al_recibir)
    log.info("Puente USB: puerto %s a %d baudios, broker %s:%d", ruta, baudios, host, port)

    ultimo_latido = 0.0
    while True:
        if not puerto.abierto:
            try:
                puerto.abrir()
            except (serial.SerialException, OSError) as e:
                log.warning("No se pudo abrir %s (%s); reintento en %.0f s", ruta, e, INTERVALO_REINTENTO_S)
                time.sleep(INTERVALO_REINTENTO_S)
                continue
            with candado:
                nucleo.puerto_abierto()
        try:
            for linea in puerto.leer_lineas():
                with candado:
                    nucleo.linea_de_la_placa(linea)
            ahora = time.monotonic()
            if ahora - ultimo_latido >= INTERVALO_LATIDO_S:
                ultimo_latido = ahora
                with candado:
                    nucleo.latido()
        except (serial.SerialException, OSError) as e:
            log.warning("Se perdió el puerto %s (%s)", ruta, e)
            puerto.cerrar()


def main() -> None:
    parser = argparse.ArgumentParser(description="Puente USB ↔ MQTT del nodo (sólo para la expo).")
    parser.add_argument("-v", "--verbose", action="store_true", help="muestra cada mensaje que pasa")
    args = parser.parse_args()
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        datefmt="%H:%M:%S",
    )
    logging.getLogger("paho").setLevel(logging.WARNING)
    try:
        correr(
            os.environ.get("PUENTE_PUERTO") or puerto_por_defecto(),
            int(os.environ.get("PUENTE_BAUD", "115200")),
            os.environ.get("MQTT_HOST", "127.0.0.1"),
            int(os.environ.get("MQTT_PORT", "1883")),
        )
    except KeyboardInterrupt:
        log.info("Puente detenido")


if __name__ == "__main__":
    main()
