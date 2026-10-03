package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.CatalogoReglasDto;
import com.yerbanalytics.backend.dto.ParametroDto;
import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Valores vigentes de los parámetros de reglas, y su edición validada.
 *
 * <p>Valor vigente = override persistido o, si no hay, fábrica. El conjunto se cachea en un
 * {@code volatile} (igual que {@code ConfiguracionService}) y se invalida al guardar: el camino
 * caliente del motor paga un acceso a un campo y cero consultas. El orquestador toma
 * {@link #vigentes()} UNA vez por evaluación de sector, así todas las reglas del ciclo ven los
 * mismos valores aunque alguien guarde a mitad de un barrido.
 */
@Service
public class CatalogoParametrosService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoParametrosService.class);

    /** Mismo default que {@code ConfiguracionService} cuando el cliente no manda {@code X-Usuario}. */
    private static final String USUARIO_POR_DEFECTO = "Ingeniero Agrónomo";

    /** Auditoría de un override persistido. */
    private record OverridePersistido(String valor, String updatedBy, Long updatedTs) {}

    /** Lo que se cachea: valores resueltos más la auditoría de los overrides. */
    private record Estado(ParametrosVigentes vigentes, Map<String, OverridePersistido> overrides,
                          Set<String> corruptas) {}

    private final CatalogoParametros catalogo;
    private final ParametroReglaRepository repository;
    private final HistorialService historialService;
    private final TransactionTemplate transaccion;

    /** Serializa los guardados: cada uno valida contra la base, así que no pueden solaparse. */
    private final Object escritura = new Object();

    private volatile Estado cache;
    /** Sube con cada invalidación: una carga que empezó antes de una no puede publicar su resultado. */
    private final AtomicLong generacion = new AtomicLong();

    public CatalogoParametrosService(CatalogoParametros catalogo,
                                     ParametroReglaRepository repository,
                                     HistorialService historialService,
                                     PlatformTransactionManager transactionManager) {
        this.transaccion = new TransactionTemplate(transactionManager);
        this.catalogo = catalogo;
        this.repository = repository;
        this.historialService = historialService;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    /** Valores vigentes; el mismo objeto hasta que alguien guarde. */
    public ParametrosVigentes vigentes() {
        return estado().vigentes();
    }

    /** Catálogo normalizado para la API: reglas por prioridad y cada parámetro una sola vez. */
    public CatalogoReglasDto catalogo() {
        Estado e = estado();

        List<ReglaCatalogoDto> reglas = new ArrayList<>();
        for (Rule r : catalogo.reglas()) {
            reglas.add(new ReglaCatalogoDto(r.name(), r.label(), r.branch().name(), r.priority(),
                    r.parametros().stream().map(DefinicionParametro::clave).toList()));
        }

        List<ParametroDto> parametros = new ArrayList<>();
        for (DefinicionParametro d : catalogo.definiciones()) {
            OverridePersistido o = e.overrides().get(d.clave());
            parametros.add(new ParametroDto(
                    d.clave(), d.etiqueta(), d.descripcion(), d.familia().name(), d.tipo().name(), d.unidad(),
                    e.vigentes().valor(d.clave()).canonico(), d.fabrica(), d.min(), d.max(), d.decimales(),
                    d.refSpec(), o != null, catalogo.usadoPor(d.clave()),
                    o != null ? o.updatedBy() : null, o != null ? o.updatedTs() : null));
        }
        return new CatalogoReglasDto(reglas, parametros);
    }

    private Estado estado() {
        Estado e = cache;
        if (e != null) {
            return e;
        }
        long gen = generacion.get();
        e = cargar();
        publicar(e, gen);
        return e;
    }

    /**
     * Publica el estado cargado sólo si nadie invalidó mientras se cargaba, y nunca dentro de una
     * transacción: ahí la lectura puede incluir cambios sin commit (que podrían revertirse).
     */
    private synchronized void publicar(Estado e, long gen) {
        if (gen == generacion.get() && !TransactionSynchronizationManager.isSynchronizationActive()) {
            cache = e;
        }
    }

    private Estado cargar() {
        Map<String, ValorParametro> valores = new HashMap<>(catalogo.fabricas().valores());
        Map<String, OverridePersistido> overrides = new LinkedHashMap<>();
        Set<String> corruptas = new java.util.HashSet<>();
        for (ParametroReglaEntity fila : repository.findAll()) {
            Optional<DefinicionParametro> def = catalogo.definicion(fila.getClave());
            if (def.isEmpty()) {
                log.warn("OverridePersistido de un parámetro que ya no existe en el catálogo ('{}'): se ignora.", fila.getClave());
                continue;
            }
            try {
                valores.put(fila.getClave(), def.get().tipo().parsear(fila.getValor(), def.get()));
                overrides.put(fila.getClave(), new OverridePersistido(fila.getValor(), fila.getUpdatedBy(), fila.getUpdatedTs()));
            } catch (ValorParametroInvalidoException ex) {
                // Un valor corrupto en la base no puede tirar el motor: se cae a fábrica.
                corruptas.add(fila.getClave());
                log.warn("OverridePersistido inválido de '{}' ('{}'): {} Se usa el valor de fábrica.",
                        fila.getClave(), fila.getValor(), ex.getMessage());
            }
        }
        return new Estado(ParametrosVigentes.de(valores), Map.copyOf(overrides), Set.copyOf(corruptas));
    }

    /** Descarta el cache: la próxima lectura vuelve a la base. */
    public synchronized void invalidar() {
        generacion.incrementAndGet();
        cache = null;
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    /**
     * Aplica un lote de cambios, todo o nada. {@code valor = null} (o igual a fábrica) borra el
     * override. Valida tipo, rango, existencia de la clave y restricciones cruzadas sobre el
     * conjunto resultante.
     *
     * @throws ParametrosInvalidosException si algo no cumple; no se persiste nada
     */
    public CatalogoReglasDto guardar(List<CambioParametro> cambios, String usuario) {
        if (cambios == null || cambios.isEmpty()) {
            return catalogo();
        }
        // El lock envuelve a la transacción (no al revés): el siguiente guardado ya ve el commit.
        synchronized (escritura) {
            return transaccion.execute(status -> guardarEnTransaccion(cambios, usuario));
        }
    }

    private CatalogoReglasDto guardarEnTransaccion(List<CambioParametro> cambios, String usuario) {
        // Se parte de la base, no del cache: el guardado decide sobre el estado persistido.
        Estado actual = cargar();
        List<ErrorParametro> errores = new ArrayList<>();
        Map<String, ValorParametro> resultante = new HashMap<>(actual.vigentes().valores());
        // clave → valor canónico nuevo, o null para restablecer. Sólo los que validaron.
        Map<String, String> aplicar = new LinkedHashMap<>();

        for (CambioParametro c : cambios) {
            if (c == null) {
                errores.add(new ErrorParametro(null, "Hay un cambio vacío en el lote."));
                continue;
            }
            Optional<DefinicionParametro> def = c.clave() == null ? Optional.empty() : catalogo.definicion(c.clave());
            if (def.isEmpty()) {
                errores.add(new ErrorParametro(c.clave(), "El parámetro no existe en el catálogo."));
                continue;
            }
            if (aplicar.containsKey(c.clave())) {
                errores.add(new ErrorParametro(c.clave(), "El parámetro viene repetido en el lote."));
                continue;
            }
            DefinicionParametro d = def.get();
            if (c.valor() == null) {
                resultante.put(d.clave(), catalogo.fabricas().valor(d.clave()));
                aplicar.put(d.clave(), null);
                continue;
            }
            try {
                ValorParametro v = d.tipo().parsear(c.valor(), d);
                resultante.put(d.clave(), v);
                aplicar.put(d.clave(), v.canonico());
            } catch (ValorParametroInvalidoException e) {
                errores.add(new ErrorParametro(d.clave(), e.getMessage()));
            }
        }

        // Restricciones cruzadas: sólo las que involucran algo que este lote toca.
        ParametrosVigentes conjunto = ParametrosVigentes.de(resultante);
        for (RestriccionCruzada r : catalogo.violaciones(conjunto)) {
            List<String> tocadas = r.claves().stream().filter(aplicar::containsKey).toList();
            for (String clave : tocadas) {
                errores.add(new ErrorParametro(clave, r.mensaje()));
            }
        }

        if (!errores.isEmpty()) {
            throw new ParametrosInvalidosException(errores);
        }

        String autor = usuario != null && !usuario.isBlank() ? usuario : USUARIO_POR_DEFECTO;
        long ahora = System.currentTimeMillis();
        List<String> cambiadas = new ArrayList<>();
        for (Map.Entry<String, String> a : aplicar.entrySet()) {
            String clave = a.getKey();
            String fabrica = catalogo.fabricas().valor(clave).canonico();
            // Un valor igual a fábrica es restablecer: "modificado" significa "hay override".
            String nuevo = a.getValue() == null || a.getValue().equals(fabrica) ? null : a.getValue();
            OverridePersistido previo = actual.overrides().get(clave);

            if (nuevo == null) {
                // Una fila corrupta también se borra: es la única forma de restablecerla.
                if (previo != null || actual.corruptas().contains(clave)) {
                    repository.deleteById(clave);
                    cambiadas.add(clave);
                }
            } else if (previo == null || !previo.valor().equals(nuevo)) {
                repository.save(new ParametroReglaEntity(clave, nuevo, autor, ahora));
                cambiadas.add(clave);
            }
        }

        if (cambiadas.isEmpty()) {
            return catalogo();
        }

        invalidarAlTerminar();
        historialService.registrarConfiguracion(autor,
                "Parámetros de reglas modificados: " + String.join(", ", cambiadas) + ".");
        return catalogo();
    }

    /**
     * Invalida ahora y otra vez al terminar la transacción (commit o rollback): si otro hilo
     * repoblara el cache entre medio, leería la base sin el commit y quedaría con valores viejos
     * hasta el próximo guardado.
     */
    private void invalidarAlTerminar() {
        invalidar();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    invalidar();
                }
            });
        }
    }
}
