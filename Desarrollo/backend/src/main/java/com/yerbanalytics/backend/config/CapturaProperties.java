package com.yerbanalytics.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del pipeline de captura de imágenes.
 *
 * <p>Los valores del bloque {@code config} son los que el backend le entrega al dispositivo
 * por {@code GET /api/camara/v1/config}: la resolución y la calidad son propiedad del
 * backend, no del cliente, porque el dispositivo está montado en un riel y cambiarle el
 * código para ajustar un parámetro no es practicable.
 */
@ConfigurationProperties(prefix = "yerbanalytics.capturas")
public class CapturaProperties {

    /** Directorio raíz donde se guardan los JPEG, particionados por fecha. */
    private String dir = "./capturas";

    /** Plazo que se le da a una orden antes de vencerla, en segundos. */
    private int timeoutOrdenSeg = 60;

    /** Intentos máximos de una orden antes de mandarla a ERROR. */
    private int maxIntentos = 3;

    /** Cada cuánto barre el watchdog las órdenes vencidas, en ms. */
    private long watchdogIntervalMs = 10_000;

    /** Vida del token de acceso del dispositivo, en segundos. */
    private int tokenVidaSeg = 900;

    /** Vida del código de vinculación de un solo uso, en segundos. */
    private int codigoVidaSeg = 600;

    /** Secreto de firma de los JWT. Sobrescribir en producción. */
    private String jwtSecret = "cambiar-en-produccion-yerbanalytics-camara-v1";

    /** Cadencia esperada de heartbeat del dispositivo, en segundos. */
    private int heartbeatSeg = 15;

    /** Sin heartbeat: umbral para "señal intermitente", en ms. */
    private long watchdogIntermitenteMs = 60_000;

    /** Sin heartbeat: umbral para "fuera de servicio", en ms. */
    private long watchdogCriticoMs = 120_000;

    /** Keep-alive del stream SSE, en ms. Evita que proxies y NAT corten la conexión ociosa. */
    private long sseKeepAliveMs = 20_000;

    /** Timeout del emisor SSE, en ms. El cliente reconecta solo al vencer. */
    private long sseTimeoutMs = 3_600_000;

    /** Umbral de confianza para considerar un diagnóstico concluyente (HU-04 CA-03). */
    private double confianzaMinima = 85.0;

    // --- Configuración que baja al dispositivo ---

    private int anchoMax = 1920;
    private int altoMax = 1080;
    private double calidadJpeg = 0.85;
    /** Techo de calentamiento, no espera fija. Ver la nota en {@code application.properties}. */
    private int warmupMs = 1500;
    private int maxColaOrdenes = 20;

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }

    public int getTimeoutOrdenSeg() { return timeoutOrdenSeg; }
    public void setTimeoutOrdenSeg(int timeoutOrdenSeg) { this.timeoutOrdenSeg = timeoutOrdenSeg; }

    public int getMaxIntentos() { return maxIntentos; }
    public void setMaxIntentos(int maxIntentos) { this.maxIntentos = maxIntentos; }

    public long getWatchdogIntervalMs() { return watchdogIntervalMs; }
    public void setWatchdogIntervalMs(long watchdogIntervalMs) { this.watchdogIntervalMs = watchdogIntervalMs; }

    public int getTokenVidaSeg() { return tokenVidaSeg; }
    public void setTokenVidaSeg(int tokenVidaSeg) { this.tokenVidaSeg = tokenVidaSeg; }

    public int getCodigoVidaSeg() { return codigoVidaSeg; }
    public void setCodigoVidaSeg(int codigoVidaSeg) { this.codigoVidaSeg = codigoVidaSeg; }

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public int getHeartbeatSeg() { return heartbeatSeg; }
    public void setHeartbeatSeg(int heartbeatSeg) { this.heartbeatSeg = heartbeatSeg; }

    public long getWatchdogIntermitenteMs() { return watchdogIntermitenteMs; }
    public void setWatchdogIntermitenteMs(long v) { this.watchdogIntermitenteMs = v; }

    public long getWatchdogCriticoMs() { return watchdogCriticoMs; }
    public void setWatchdogCriticoMs(long v) { this.watchdogCriticoMs = v; }

    public long getSseKeepAliveMs() { return sseKeepAliveMs; }
    public void setSseKeepAliveMs(long sseKeepAliveMs) { this.sseKeepAliveMs = sseKeepAliveMs; }

    public long getSseTimeoutMs() { return sseTimeoutMs; }
    public void setSseTimeoutMs(long sseTimeoutMs) { this.sseTimeoutMs = sseTimeoutMs; }

    public double getConfianzaMinima() { return confianzaMinima; }
    public void setConfianzaMinima(double confianzaMinima) { this.confianzaMinima = confianzaMinima; }

    public int getAnchoMax() { return anchoMax; }
    public void setAnchoMax(int anchoMax) { this.anchoMax = anchoMax; }

    public int getAltoMax() { return altoMax; }
    public void setAltoMax(int altoMax) { this.altoMax = altoMax; }

    public double getCalidadJpeg() { return calidadJpeg; }
    public void setCalidadJpeg(double calidadJpeg) { this.calidadJpeg = calidadJpeg; }

    public int getWarmupMs() { return warmupMs; }
    public void setWarmupMs(int warmupMs) { this.warmupMs = warmupMs; }

    public int getMaxColaOrdenes() { return maxColaOrdenes; }
    public void setMaxColaOrdenes(int maxColaOrdenes) { this.maxColaOrdenes = maxColaOrdenes; }
}
