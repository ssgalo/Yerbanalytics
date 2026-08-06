package com.yerbanalytics.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Orígenes permitidos. Configurable porque la app de cámara no se sirve desde
     * {@code localhost}: el iPhone la carga desde la IP o el hostname de la máquina en la LAN
     * del vivero, y por HTTPS, porque {@code getUserMedia} exige origen seguro. Ese origen
     * cambia según la red, así que no puede estar clavado en el código.
     *
     * <p>Se admiten patrones ({@code allowedOriginPatterns}) para poder habilitar la subred
     * del vivero de una sola vez, p. ej. {@code https://192.168.0.*:5190}.
     */
    @Value("${yerbanalytics.cors.origins}")
    private String[] origins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // El dashboard cachea la imagen de una captura por su ETag (el sha256).
                .exposedHeaders("ETag")
                .allowCredentials(true);
    }
}
