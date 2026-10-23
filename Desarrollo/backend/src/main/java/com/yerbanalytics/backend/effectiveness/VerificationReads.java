package com.yerbanalytics.backend.effectiveness;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las lecturas de verificación de E-01 (reglas_v2 §9): el servicio de efectividad pide a la macro-zona una lectura fuera del ciclo
 * de sensado para medir cuánto subió la humedad tras un riego, y esa lectura <b>no decide nada</b>: la ingesta consulta este
 * banderín antes de correr el motor y, si la lectura es la pedida, sólo la guarda (origen {@code OTHER}) sin evaluar reglas.
 * Si no, el pico de humedad que el riego acaba de producir dispararía otras reglas (saturación, cierre de riego) sobre un dato
 * que no es del pulso.
 *
 * <p>Es memoria del proceso a propósito: si el backend se reinicia entre el pedido y la respuesta, el pedido se repite al
 * siguiente ciclo ({@link #RETRY_MS}) y nada se pierde.
 */
@Component
public class VerificationReads {

    /** Cuánto se espera la respuesta a un pedido: pasado esto, una lectura que llegue es del pulso común, no la pedida. */
    public static final long EXPIRY_MS = 5 * 60_000L;
    /** Cuánto pasa antes de volver a pedir una lectura que no llegó o que no servía. */
    public static final long RETRY_MS = 5 * 60_000L;

    private record Request(long requestedAt, boolean answered) {
    }

    private final Map<String, Request> requests = new ConcurrentHashMap<>();

    /** El servicio pidió la lectura de {@code zoneId} en {@code nowMs}. */
    public void expect(String zoneId, long nowMs) {
        requests.put(zoneId, new Request(nowMs, false));
    }

    /**
     * La ingesta recibió una lectura de {@code zoneId}: {@code true} si es la que se pidió (la primera tras el pedido, antes de
     * que venza), y entonces no debe correr el motor.
     */
    public boolean consume(String zoneId, long nowMs) {
        boolean[] matched = {false};
        requests.computeIfPresent(zoneId, (id, request) -> {
            if (request.answered() || nowMs - request.requestedAt() > EXPIRY_MS) {
                return request;
            }
            matched[0] = true;
            return new Request(request.requestedAt(), true);
        });
        return matched[0];
    }

    /** Cuándo se pidió la lectura que ya llegó, o vacío si no hay pedido o todavía no contestó. */
    public OptionalLong answeredAfter(String zoneId) {
        Request request = requests.get(zoneId);
        return request != null && request.answered() ? OptionalLong.of(request.requestedAt()) : OptionalLong.empty();
    }

    /** {@code true} si hay que publicar el pedido: nunca se hizo o el último ya venció sin una respuesta útil. */
    public boolean needsRequest(String zoneId, long nowMs) {
        Request request = requests.get(zoneId);
        return request == null || nowMs - request.requestedAt() > RETRY_MS;
    }

    /** El servicio terminó de usar la respuesta de {@code zoneId}. */
    public void clear(String zoneId) {
        requests.remove(zoneId);
    }
}
