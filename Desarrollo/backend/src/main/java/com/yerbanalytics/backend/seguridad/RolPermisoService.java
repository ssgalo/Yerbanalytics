package com.yerbanalytics.backend.seguridad;

import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaService;
import com.yerbanalytics.backend.seguridad.auditoria.ObjetivoAuditoria;
import com.yerbanalytics.backend.seguridad.auditoria.TipoAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Matriz rol → permisos (HU-20 CA-01).
 *
 * <p>Se consulta en cada petición para armar las <em>authorities</em>, así que vive en una caché
 * en memoria (5 roles × 20 permisos) que se invalida al confirmar un cambio: la petición siguiente
 * ya opera con la matriz nueva, sin esperar a que el usuario vuelva a iniciar sesión.
 */
@Service
public class RolPermisoService {

    private final RolPermisoRepository repo;
    private final SesionService sesiones;
    private final AuditoriaService auditoria;

    private volatile Map<Rol, Set<String>> cache;

    public RolPermisoService(RolPermisoRepository repo, SesionService sesiones, AuditoriaService auditoria) {
        this.repo = repo;
        this.sesiones = sesiones;
        this.auditoria = auditoria;
    }

    /** Permisos efectivos de un rol, como códigos. */
    public Set<String> permisosDe(Rol rol) {
        return matrizCacheada().getOrDefault(rol, Set.of());
    }

    public record RolPermisos(Rol rol, String nombre, List<String> permisos, List<String> intocables) {}

    public List<RolPermisos> matriz() {
        Map<Rol, Set<String>> m = matrizCacheada();
        return Arrays.stream(Rol.values()).map(r -> aDto(r, m.getOrDefault(r, Set.of()))).toList();
    }

    /**
     * Reemplaza los permisos de un rol. Revoca las sesiones de quienes tienen ese rol, salvo la del
     * autor (HU-20 CA-02): el autor sigue operando y la matriz nueva le rige desde la petición
     * siguiente.
     */
    @Transactional
    public RolPermisos guardar(Rol rol, Collection<String> codigos) {
        if (codigos == null) {
            throw new SeguridadExceptions.Invalida("Falta la lista de permisos.");
        }
        Set<Permiso> nuevos = new HashSet<>();
        for (String codigo : codigos) {
            nuevos.add(Permiso.porCodigo(codigo).orElseThrow(
                    () -> new SeguridadExceptions.Invalida("El permiso '" + codigo + "' no existe.")));
        }
        for (Permiso p : nuevos) {
            if (p.getLectura() != null && !nuevos.contains(p.getLectura())) {
                throw new SeguridadExceptions.Invalida("'" + p.getCodigo() + "' requiere también '"
                        + p.getLectura().getCodigo() + "': un permiso de edición no incluye el de lectura.");
            }
        }
        if (rol == Rol.ADMINISTRADOR && !nuevos.containsAll(Permiso.INTOCABLES_ADMINISTRADOR)) {
            throw new SeguridadExceptions.Conflicto("El rol Administrador no puede perder '"
                    + Permiso.USUARIOS_GESTIONAR.getCodigo() + "' ni '" + Permiso.AUDITORIA_VER.getCodigo()
                    + "': nadie podría volver a administrar el sistema.");
        }

        Set<String> anteriores = new TreeSet<>(permisosLeidos(rol));
        Set<String> codigosNuevos = nuevos.stream().map(Permiso::getCodigo).collect(Collectors.toCollection(TreeSet::new));
        if (anteriores.equals(codigosNuevos)) {
            return aDto(rol, codigosNuevos);
        }

        repo.borrarDeRol(rol);
        repo.saveAll(codigosNuevos.stream().map(c -> new RolPermisoEntity(rol, c)).toList());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("agregados", diferencia(codigosNuevos, anteriores));
        detalle.put("quitados", diferencia(anteriores, codigosNuevos));
        auditoria.registrar(TipoAuditoria.ROL_PERMISOS_CAMBIADOS, ObjetivoAuditoria.ROL, rol.name(), detalle);

        sesiones.revocarDeRol(rol, UsuarioSesion.actual().map(UsuarioSesion::sesionHash).orElse(null));
        invalidarAlConfirmar();
        return aDto(rol, codigosNuevos);
    }

    /**
     * Siembra la matriz por defecto sólo si la tabla está vacía: nunca pisa una matriz editada.
     *
     * @return si sembró
     */
    @Transactional
    public boolean sembrarSiVacia() {
        if (repo.count() > 0) {
            return false;
        }
        List<RolPermisoEntity> filas = new ArrayList<>();
        for (Rol rol : Rol.values()) {
            Set<String> codigos = Permiso.matrizPorDefecto(rol).stream().map(Permiso::getCodigo)
                    .collect(Collectors.toCollection(TreeSet::new));
            codigos.forEach(c -> filas.add(new RolPermisoEntity(rol, c)));
            auditoria.registrarComoSistema(TipoAuditoria.MATRIZ_SEMBRADA, ObjetivoAuditoria.ROL, rol.name(),
                    Map.of("permisos", List.copyOf(codigos)));
        }
        repo.saveAll(filas);
        invalidarAlConfirmar();
        return true;
    }

    private Map<Rol, Set<String>> matrizCacheada() {
        Map<Rol, Set<String>> m = cache;
        if (m == null) {
            Map<Rol, Set<String>> leida = new EnumMap<>(Rol.class);
            for (RolPermisoEntity rp : repo.findAll()) {
                leida.computeIfAbsent(rp.getRol(), r -> new TreeSet<>()).add(rp.getPermiso());
            }
            leida.replaceAll((r, s) -> Set.copyOf(s));
            m = Map.copyOf(leida);
            cache = m;
        }
        return m;
    }

    /** Lee de la base, no de la caché: dentro de la transacción del cambio manda lo persistido. */
    private Set<String> permisosLeidos(Rol rol) {
        return repo.findAll().stream().filter(rp -> rp.getRol() == rol).map(RolPermisoEntity::getPermiso)
                .collect(Collectors.toSet());
    }

    private static List<String> diferencia(Set<String> a, Set<String> b) {
        return a.stream().filter(x -> !b.contains(x)).sorted().toList();
    }

    private static RolPermisos aDto(Rol rol, Set<String> permisos) {
        // En el orden del catálogo, que ya viene agrupado.
        List<String> ordenados = Arrays.stream(Permiso.values()).map(Permiso::getCodigo)
                .filter(permisos::contains).toList();
        List<String> intocables = rol == Rol.ADMINISTRADOR
                ? Permiso.INTOCABLES_ADMINISTRADOR.stream().map(Permiso::getCodigo).toList()
                : List.of();
        return new RolPermisos(rol, rol.getNombre(), ordenados, intocables);
    }

    private void invalidarAlConfirmar() {
        cache = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache = null;
                }
            });
        }
    }
}
