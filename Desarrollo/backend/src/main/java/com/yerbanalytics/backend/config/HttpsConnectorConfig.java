package com.yerbanalytics.backend.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Conector HTTPS <strong>adicional</strong>, en un puerto propio.
 *
 * <p>La app de cámara no puede hablarle al backend por HTTP: {@code getUserMedia} exige
 * origen seguro, así que la PWA se sirve por HTTPS, y una página HTTPS no puede llamar a un
 * endpoint HTTP (mixed content). El backend necesita TLS.
 *
 * <p>Se agrega un conector en lugar de configurar {@code server.ssl.*} sobre el puerto
 * principal porque eso convertiría el 8000 en HTTPS y rompería lo que ya funciona: el
 * dashboard apunta a {@code http://localhost:8000/api}. Así el 8000 sigue
 * siendo HTTP y el 8443 atiende TLS con el mismo certificado que usa Vite.
 *
 * <p>Se activa sólo si hay un keystore configurado, para que el arranque por defecto no
 * dependa de tener certificados generados.
 */
@Configuration
@ConditionalOnProperty(name = "yerbanalytics.https.enabled", havingValue = "true")
public class HttpsConnectorConfig {

    private static final Logger log = LoggerFactory.getLogger(HttpsConnectorConfig.class);

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> conectorHttps(
            @Value("${yerbanalytics.https.port:8443}") int puerto,
            @Value("${yerbanalytics.https.keystore:../certs/servidor.p12}") String keystore,
            @Value("${yerbanalytics.https.keystore-password:yerbanalytics}") String password) {

        Path ruta = Paths.get(keystore).toAbsolutePath().normalize();
        if (!Files.isReadable(ruta)) {
            // Sin keystore se sigue adelante SIN el conector TLS, en vez de abortar el
            // arranque. Los certificados no están en el repositorio (son secretos), así que
            // en un clon recién bajado no existen todavía: hacer fallar todo el backend
            // impediría levantar el dashboard, que no necesita TLS para nada.
            //
            // Lo único que deja de funcionar es la app de cámara, y el mensaje dice cómo
            // arreglarlo.
            log.warn("""

                    ┌──────────────────────────────────────────────────────────────────────
                    │  HTTPS deshabilitado: no se encontró el keystore en
                    │    {}
                    │
                    │  El backend arranca igual y el dashboard funciona, pero la APP DE
                    │  CÁMARA no va a poder conectarse (getUserMedia exige origen seguro).
                    │
                    │  Para habilitarlo:  cd Desarrollo/certs && ./generar-certificados.sh
                    └──────────────────────────────────────────────────────────────────────
                    """, ruta);
            return factory -> { /* sin conector TLS */ };
        }

        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(puerto);
            connector.setScheme("https");
            connector.setSecure(true);

            Http11NioProtocol protocolo = (Http11NioProtocol) connector.getProtocolHandler();
            protocolo.setSSLEnabled(true);

            SSLHostConfig sslConfig = new SSLHostConfig();
            SSLHostConfigCertificate certificado =
                    new SSLHostConfigCertificate(sslConfig, SSLHostConfigCertificate.Type.UNDEFINED);
            certificado.setCertificateKeystoreFile(ruta.toString());
            certificado.setCertificateKeystorePassword(password);
            certificado.setCertificateKeystoreType("PKCS12");
            sslConfig.addCertificate(certificado);
            connector.addSslHostConfig(sslConfig);

            factory.addAdditionalTomcatConnectors(connector);
            log.info("HTTPS habilitado en el puerto {} (keystore: {})", puerto, ruta);
        };
    }
}
