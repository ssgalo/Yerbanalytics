package com.yerbanalytics.backend.engine.weather;

/**
 * Contrato para obtener el pronóstico climático de una ubicación geográfica.
 *
 * <p>El propósito de esta interfaz es <b>desacoplar</b> la lógica del motor de reglas
 * de la API climática concreta. Cambiar de proveedor (Open-Meteo, OpenWeatherMap,
 * Tomorrow.io, etc.) implica únicamente crear una nueva implementación de esta interfaz
 * y registrarla como {@code @Component} — sin tocar ninguna regla.
 *
 * <p>El contrato es simple por diseño: retorna {@code null} ante cualquier fallo
 * (timeout, error HTTP, parse error). El {@link WeatherService} envuelve la implementación
 * con el mecanismo de retry y cache; los implementadores no necesitan hacerlo.
 */
public interface WeatherClient {

    /**
     * Obtiene el pronóstico climático actual para la ubicación configurada.
     *
     * @return un {@link WeatherForecast} si la llamada fue exitosa, o {@code null}
     *         si falló (el motor operará en modo degradado sin pronóstico).
     */
    WeatherForecast fetch();
}
