"""Ruteo del puente entre la placa y el broker, con dobles (design D4, D7 y D8 de add-transporte-usb-nodo)."""
from nucleo import Nucleo


class BrokerFalso:
    def __init__(self):
        self.conectado = True
        self.suscripciones = []
        self.desuscripciones = []
        self.publicaciones = []

    def subscribe(self, topic, qos):
        self.suscripciones.append((topic, qos))

    def unsubscribe(self, topic):
        self.desuscripciones.append(topic)

    def publish(self, topic, payload, qos):
        self.publicaciones.append((topic, payload, qos))


def nucleo(broker=None):
    escritas = []
    sesiones = iter(["s1", "s2", "s3"])
    n = Nucleo(broker or BrokerFalso(), escritas.append, reloj=lambda: 1793239177.9, nueva_sesion=lambda: next(sesiones))
    return n, escritas


def test_la_suscripcion_de_la_placa_llega_al_broker():
    broker = BrokerFalso()
    n, _ = nucleo(broker)
    n.linea_de_la_placa("@S 1 nursery/rail/command")
    assert broker.suscripciones == [("nursery/rail/command", 1)]


def test_la_publicacion_de_la_placa_llega_al_broker_con_su_qos():
    broker = BrokerFalso()
    n, _ = nucleo(broker)
    n.linea_de_la_placa('@P 0 nursery/zone/MZ-1/telemetry {"temp": 21}')
    assert broker.publicaciones == [("nursery/zone/MZ-1/telemetry", '{"temp": 21}', 0)]


def test_sin_broker_no_publica():
    broker = BrokerFalso()
    broker.conectado = False
    n, _ = nucleo(broker)
    n.linea_de_la_placa('@P 0 a/b {"x": 1}')
    assert broker.publicaciones == []


def test_ignora_lo_que_no_es_protocolo():
    broker = BrokerFalso()
    n, escritas = nucleo(broker)
    n.linea_de_la_placa("ESP-ROM:esp32s3-20210327")
    assert broker.publicaciones == [] and broker.suscripciones == [] and escritas == []


def test_el_mensaje_del_broker_llega_a_la_placa():
    n, escritas = nucleo()
    n.mensaje_del_broker("nursery/rail/command", b'{"accion":"HOME"}')
    assert escritas == ['@M nursery/rail/command {"accion":"HOME"}\n']


def test_un_mensaje_que_no_entra_no_rompe_el_puente():
    n, escritas = nucleo()
    n.mensaje_del_broker("a/b", b"x" * 5000)
    assert escritas == []


def test_un_mensaje_que_no_es_utf8_no_rompe_el_puente():
    n, escritas = nucleo()
    n.mensaje_del_broker("a/b", b"\xff\xfe")
    assert escritas == []


def test_el_latido_lleva_la_hora_y_la_sesion():
    n, escritas = nucleo()
    n.puerto_abierto()
    n.latido()
    assert escritas == ["@H 1793239177 s1\n"]


def test_sin_broker_no_hay_latido():
    broker = BrokerFalso()
    broker.conectado = False
    n, escritas = nucleo(broker)
    n.puerto_abierto()
    n.latido()
    assert escritas == []


def test_reabrir_el_puerto_cambia_la_sesion_y_olvida_las_suscripciones():
    broker = BrokerFalso()
    n, escritas = nucleo(broker)
    n.puerto_abierto()
    n.linea_de_la_placa("@S 1 a/b")
    n.puerto_abierto()
    n.latido()
    assert broker.desuscripciones == ["a/b"]
    assert escritas == ["@H 1793239177 s2\n"]


def test_al_reconectar_al_broker_repite_las_suscripciones():
    broker = BrokerFalso()
    n, _ = nucleo(broker)
    n.linea_de_la_placa("@S 1 a/b")
    n.linea_de_la_placa("@S 0 c/d")
    broker.suscripciones.clear()
    n.broker_conectado()
    assert sorted(broker.suscripciones) == [("a/b", 1), ("c/d", 0)]


def test_una_suscripcion_repetida_no_se_duplica():
    broker = BrokerFalso()
    n, _ = nucleo(broker)
    n.linea_de_la_placa("@S 1 a/b")
    n.linea_de_la_placa("@S 1 a/b")
    broker.suscripciones.clear()
    n.broker_conectado()
    assert broker.suscripciones == [("a/b", 1)]


def test_al_perder_el_broker_avisa_a_la_placa_al_instante():
    # Sin el aviso, la placa seguiría publicando hasta 6 s creyéndose conectada, y eso se perdería.
    n, escritas = nucleo()
    n.puerto_abierto()
    n.broker_desconectado()
    assert escritas == ["@X\n"]


def test_sin_puerto_abierto_no_avisa_el_corte():
    n, escritas = nucleo()
    n.broker_desconectado()
    assert escritas == []
