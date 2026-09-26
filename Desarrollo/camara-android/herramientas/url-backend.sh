#!/usr/bin/env bash
# Detecta el enlace USB con el teléfono y dice qué URL tipear en la app de cámara.
#
# Uso:  ./url-backend.sh [puerto]        (puerto por defecto: 8000)
#
# No modifica nada. Sólo mira interfaces y prueba si el backend responde.

set -uo pipefail

PUERTO="${1:-8000}"
ENDPOINT="/api/nursery"

rojo()  { printf '\033[31m%s\033[0m\n' "$*"; }
verde() { printf '\033[32m%s\033[0m\n' "$*"; }
gris()  { printf '\033[90m%s\033[0m\n' "$*"; }

echo
echo "Buscando el enlace USB con el teléfono…"
echo

# --- 1. Interfaces de red que cuelgan del bus USB -----------------------------
# Se detectan por el bus del que cuelgan, no por el nombre ni por el driver:
# el nombre cambia por distro (usb0, enp0s20u1…) y el driver por fabricante
# (rndis_host, cdc_ncm, cdc_ether). El bus no miente.
interfaces=()
for ruta in /sys/class/net/*; do
    iface=$(basename "$ruta")
    [[ "$iface" == "lo" ]] && continue

    # El symlink 'device' tiene que EXISTIR. Las interfaces virtuales (bridges
    # de Docker, veth, virbr) no lo tienen — y ojo: `readlink -f` devuelve una
    # ruta igual aunque el symlink no exista, así que sin este chequeo entrarían
    # todas y sólo se descartarían por no contener "/usb". Un bridge llamado
    # usb0 daría falso positivo.
    [[ -e "$ruta/device" ]] || continue

    destino=$(readlink -f "$ruta/device" 2>/dev/null) || continue
    [[ "$destino" == *"/usb"* ]] && interfaces+=("$iface")
done

if [[ ${#interfaces[@]} -eq 0 ]]; then
    rojo "No hay ninguna interfaz de red colgando del bus USB."
    echo
    echo "Revisá, en este orden:"
    echo "  1. ¿El cable transfiere DATOS? Muchos USB-C son sólo de carga."
    echo "     Comprobalo:  lsusb   (tiene que aparecer el fabricante del teléfono)"
    echo "  2. ¿Está activada la conexión compartida por USB en el teléfono?"
    echo "     Ajustes → Redes e Internet → Zona WiFi y conexión compartida"
    echo "  3. ¿El teléfono está en modo 'sólo carga'? Bajá la notificación de USB"
    echo "     y elegí cualquier otro modo."
    echo "  4. Si la opción de tethering está GRIS: varios Android se niegan a"
    echo "     activarla si el teléfono no tiene datos móviles ni WiFi que compartir,"
    echo "     aunque a vos sólo te interese el enlace. Prendé alguna y reintentá."
    echo
    gris "Alternativa sin tethering:  adb reverse tcp:${PUERTO} tcp:${PUERTO}"
    gris "y en la app tipeás  http://localhost:${PUERTO}"
    echo
    exit 1
fi

# --- 2. IP de esta PC en cada una de esas interfaces --------------------------
encontrada=0
for iface in "${interfaces[@]}"; do
    ip_pc=$(ip -4 -brief addr show dev "$iface" 2>/dev/null | awk '{print $3}' | cut -d/ -f1)

    if [[ -z "$ip_pc" ]]; then
        rojo "Interfaz ${iface}: levantada pero SIN IP."
        echo "  El DHCP del teléfono no arrancó. Desactivá y reactivá la conexión"
        echo "  compartida por USB en el teléfono."
        echo
        continue
    fi

    encontrada=1
    verde "Interfaz ${iface} → esta PC tiene la IP ${ip_pc}"
    echo

    # --- 3. ¿El backend responde POR ESA IP? ---------------------------------
    # Se prueba con la IP del enlace, no con localhost: eso es exactamente lo
    # que va a hacer el teléfono, y es lo que distingue "el backend está caído"
    # de "el backend anda pero no escucha en esta interfaz".
    codigo=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 \
             "http://${ip_pc}:${PUERTO}${ENDPOINT}" 2>/dev/null)

    if [[ "$codigo" == "200" ]]; then
        verde "El backend responde en esa dirección. Tipeá esto en la app:"
        echo
        echo "    http://${ip_pc}:${PUERTO}"
        echo
    elif [[ "$codigo" == "000" ]]; then
        rojo "El backend NO responde en http://${ip_pc}:${PUERTO}"
        echo
        if curl -s -o /dev/null --max-time 3 "http://localhost:${PUERTO}${ENDPOINT}"; then
            echo "  Pero SÍ responde en localhost. O sea: el backend está vivo y el"
            echo "  problema es de red — casi seguro el firewall."
            echo
            echo "      sudo ufw allow in on ${iface} to any port ${PUERTO} proto tcp"
        else
            echo "  Tampoco responde en localhost: el backend no está levantado."
            echo
            echo "      cd Desarrollo/backend && ./mvnw spring-boot:run"
        fi
        echo
    else
        rojo "El backend contestó HTTP ${codigo} en http://${ip_pc}:${PUERTO}${ENDPOINT}"
        echo "  Está escuchando, pero algo anda mal con ese endpoint."
        echo
    fi
done

[[ $encontrada -eq 0 ]] && exit 1

# --- 4. Aviso sobre la ruta por defecto --------------------------------------
# Al compartir conexión el teléfono también ofrece internet, y el sistema puede
# mandarle TODO el tráfico de la PC por ahí.
ruta_default=$(ip route show default 2>/dev/null | head -1)
for iface in "${interfaces[@]}"; do
    if [[ "$ruta_default" == *"dev ${iface}"* ]]; then
        echo
        rojo "Ojo: la ruta por defecto se fue por ${iface}."
        echo "  Todo el tráfico de esta PC está saliendo por el teléfono (y gastando"
        echo "  sus datos móviles). Si la PC navega raro, es esto."
        gris "  ${ruta_default}"
        echo
    fi
done
