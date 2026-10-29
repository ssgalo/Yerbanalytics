// ============================================================================
//  enlace_usb.h  -  Estado del enlace USB con el puente (add-transporte-usb-nodo)
// ----------------------------------------------------------------------------
//  Sólo lo compila el entorno nodo_combinado_usb. Lo comparten net_wifi_usb.cpp
//  y net_mqtt_usb.cpp: "conectado" significa "llegó un latido del puente hace
//  poco", y el puente sólo late mientras está conectado al broker (design D4).
// ============================================================================
#ifndef ENLACE_USB_H
#define ENLACE_USB_H

namespace enlace_usb {

// true si el último latido del puente llegó hace menos de USB_LATIDO_VENCE_MS.
bool hayLatido();

}  // namespace enlace_usb

#endif  // ENLACE_USB_H
