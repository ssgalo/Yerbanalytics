package com.yerbanalytics.backend.seguridad;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo de permisos. Fijo en código: un permiso nuevo nace con la funcionalidad que lo
 * necesita. Lo editable es la matriz rol → permisos ({@code rol_permiso}).
 *
 * <p>Un permiso de edición NO implica el de lectura: la matriz los guarda por separado, y
 * {@link #getLectura()} indica qué par de lectura tiene que acompañarlo para que la matriz sea
 * válida. El código (p. ej. {@code reglas.editar}) es lo que viaja en la API, se persiste y se
 * usa como <em>authority</em> de Spring Security.
 *
 * <p>El frontend tiene un espejo de este catálogo y de {@link #matrizPorDefecto} en
 * {@code src/types/}: si se toca acá, se toca allá.
 */
public enum Permiso {

    VIVERO_VER("vivero.ver", "Vivero", "Panel general, detalle de macro-zona y de sector", null),
    DIAGNOSTICOS_VER("diagnosticos.ver", "Diagnósticos", "Ver los diagnósticos de IA", null),
    DIAGNOSTICOS_REGISTRAR("diagnosticos.registrar", "Diagnósticos", "Dar de alta diagnósticos", DIAGNOSTICOS_VER),
    HISTORIAL_VER("historial.ver", "Historial", "Historial de acciones", null),
    CONFIGURACION_VER("configuracion.ver", "Configuración", "Ver la configuración agronómica", null),
    CONFIGURACION_EDITAR("configuracion.editar", "Configuración", "Editar la configuración agronómica", CONFIGURACION_VER),
    REGLAS_VER("reglas.ver", "Motor de reglas", "Ver el grafo de reglas, las trazas y los parámetros", null),
    REGLAS_EDITAR("reglas.editar", "Motor de reglas", "Editar el catálogo de parámetros", REGLAS_VER),
    HARDWARE_VER("hardware.ver", "Hardware", "Ver el estado del hardware", null),
    HARDWARE_GESTIONAR("hardware.gestionar", "Hardware", "Alta y recambio de dispositivos", HARDWARE_VER),
    TOPOLOGIA_VER("topologia.ver", "Topología", "Ver la topología del vivero", null),
    TOPOLOGIA_GESTIONAR("topologia.gestionar", "Topología", "Generar la topología y cambiar su disposición", TOPOLOGIA_VER),
    CAPTURAS_VER("capturas.ver", "Captura", "Ver órdenes de captura e imágenes", null),
    CAPTURAS_ORDENAR("capturas.ordenar", "Captura", "Emitir órdenes de captura", CAPTURAS_VER),
    CAMARA_GESTIONAR("camara.gestionar", "Captura", "Vincular, listar y revocar dispositivos de captura", null),
    PASADAS_VER("pasadas.ver", "Pasadas del riel", "Ver el estado de la pasada del riel", null),
    PASADAS_OPERAR("pasadas.operar", "Pasadas del riel", "Iniciar y cancelar la pasada del riel", PASADAS_VER),
    DEMO_EXPO_CONFIGURAR("demo-expo.configurar", "Demo Expo", "Mostrar u ocultar la pestaña Demo Expo", null),
    USUARIOS_GESTIONAR("usuarios.gestionar", "Seguridad", "Usuarios, matriz de permisos y política de sesión", null),
    AUDITORIA_VER("auditoria.ver", "Seguridad", "Registro de auditoría de seguridad", null);

    private static final Map<String, Permiso> POR_CODIGO = Arrays.stream(values())
            .collect(Collectors.toMap(Permiso::getCodigo, Function.identity()));

    /**
     * Lo que el rol Administrador no puede perder: sin esto nadie podría volver a dar permisos
     * ni ver quién los cambió.
     */
    public static final Set<Permiso> INTOCABLES_ADMINISTRADOR = EnumSet.of(USUARIOS_GESTIONAR, AUDITORIA_VER);

    /**
     * "Todos los .ver" de la matriz por defecto. Excluye {@link #AUDITORIA_VER}, que por defecto
     * sólo tiene el Administrador.
     */
    private static final Set<Permiso> LECTURAS = EnumSet.of(VIVERO_VER, DIAGNOSTICOS_VER, HISTORIAL_VER,
            CONFIGURACION_VER, REGLAS_VER, HARDWARE_VER, TOPOLOGIA_VER, CAPTURAS_VER, PASADAS_VER);

    private final String codigo;
    private final String grupo;
    private final String descripcion;
    private final Permiso lectura;

    Permiso(String codigo, String grupo, String descripcion, Permiso lectura) {
        this.codigo = codigo;
        this.grupo = grupo;
        this.descripcion = descripcion;
        this.lectura = lectura;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getGrupo() {
        return grupo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Par de lectura que exige este permiso de edición, o {@code null} si no tiene. */
    public Permiso getLectura() {
        return lectura;
    }

    public static Optional<Permiso> porCodigo(String codigo) {
        return Optional.ofNullable(POR_CODIGO.get(codigo));
    }

    /**
     * Matriz que se siembra en una base sin matriz. Sale de quién es dueño de cada historia de
     * usuario; el Administrador la ajusta después desde el dashboard.
     */
    public static Set<Permiso> matrizPorDefecto(Rol rol) {
        return switch (rol) {
            case ADMINISTRADOR -> EnumSet.allOf(Permiso.class);
            case INGENIERO_AGRONOMO -> con(LECTURAS, CONFIGURACION_EDITAR, REGLAS_EDITAR);
            case PRODUCTOR_VIVERISTA -> con(LECTURAS, PASADAS_OPERAR);
            case OPERARIO -> EnumSet.of(VIVERO_VER, DIAGNOSTICOS_VER, HISTORIAL_VER, CAPTURAS_VER, PASADAS_VER);
            case SERVICIO -> EnumSet.of(VIVERO_VER, TOPOLOGIA_VER, CAPTURAS_VER, CAPTURAS_ORDENAR,
                    DIAGNOSTICOS_VER, DIAGNOSTICOS_REGISTRAR, CAMARA_GESTIONAR);
        };
    }

    private static Set<Permiso> con(Set<Permiso> base, Permiso... extra) {
        EnumSet<Permiso> s = EnumSet.copyOf(base);
        s.addAll(Arrays.asList(extra));
        return s;
    }
}
