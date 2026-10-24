package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.dto.CatalogoReglasDto;
import com.yerbanalytics.backend.dto.ParametroDto;
import com.yerbanalytics.backend.dto.ReglaCatalogoDto;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.ConfiguracionOperativaRepository;
import com.yerbanalytics.backend.sensing.PulsoSensadoService;
import com.yerbanalytics.backend.sensing.SensingCadence;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Valores vigentes de los parámetros de reglas, por pieza, y su edición validada.
 *
 * <p>Cada pieza que lee parámetros tiene su copia (rediseno-motor-reglas D-1): valor vigente de {@code (pieza, clave)} =
 * override persistido de esa copia o, si no hay, fábrica. No hay herencia entre piezas. El conjunto se cachea en un
 * {@code volatile} (igual que {@code ConfiguracionService}) y se invalida al guardar: el camino caliente del motor paga
 * un acceso a un campo y cero consultas. El orquestador toma {@link #vigentes()} UNA vez por evaluación de sector, así
 * todas las reglas del ciclo ven los mismos valores aunque alguien guarde a mitad de un barrido.
 */
@Service
public class CatalogoParametrosService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoParametrosService.class);

    /** Mismo default que {@code ConfiguracionService} cuando el cliente no manda {@code X-Usuario}. */
    private static final String USUARIO_POR_DEFECTO = "Ingeniero Agrónomo";

    /** Una copia: la pieza dueña y la clave. */
    private record Copia(String regla, String clave) {}

    /** Auditoría de un override persistido. */
    private record OverridePersistido(String valor, String updatedBy, Long updatedTs) {}

    /**
     * Lo que se cachea: los valores propios de cada pieza (crudos, lo que se edita), el snapshot efectivo que leen las
     * piezas y la auditoría de los overrides.
     */
    private record Estado(Map<String, ParametrosVigentes> crudos, ParametrosPorPieza efectivos,
                          Map<Copia, OverridePersistido> overrides, Set<Copia> corruptas) {}

    /** Operating-config row that holds the sensing pulse interval. */
    private static final int OPERATIVA_ID = 1;

    private final CatalogoParametros catalogo;
    private final ReglaParametroRepository repository;
    private final HistorialService historialService;
    private final TransactionTemplate transaccion;
    private final ConfiguracionOperativaRepository operativaRepository;
    /**
     * Lazy: arma la lista de piezas desde el registro de todos los {@code DemoControllable}, y varios de ellos (los
     * servicios que deciden) dependen de este servicio para leer sus parámetros.
     */
    private final ReglasCatalogo reglasCatalogo;

    /** Serializa los guardados: cada uno valida contra la base, así que no pueden solaparse. */
    private final Object escritura = new Object();

    /**
     * Marca del hilo que está dentro de la transacción de {@link #guardar}: sólo ahí una carga
     * puede incluir datos sin commit y no debe publicarse en el cache.
     */
    private final ThreadLocal<Boolean> enGuardado = new ThreadLocal<>();

    private volatile Estado cache;
    /** Sube con cada invalidación: una carga que empezó antes de una no puede publicar su resultado. */
    private final AtomicLong generacion = new AtomicLong();

    public CatalogoParametrosService(CatalogoParametros catalogo,
                                     ReglaParametroRepository repository,
                                     HistorialService historialService,
                                     PlatformTransactionManager transactionManager,
                                     ConfiguracionOperativaRepository operativaRepository,
                                     @Lazy ReglasCatalogo reglasCatalogo) {
        this.transaccion = new TransactionTemplate(transactionManager);
        // Transacción propia: el lock de guardar tiene que soltarse DESPUÉS del commit.
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.catalogo = catalogo;
        this.repository = repository;
        this.historialService = historialService;
        this.operativaRepository = operativaRepository;
        this.reglasCatalogo = reglasCatalogo;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    /**
     * Valores vigentes de todas las piezas; el mismo objeto hasta que alguien guarde. Es el snapshot EFECTIVO que consume
     * el motor: en cada vista, {@code seguridad.antiguedad-max-lectura} es {@code max(piso, intervalo del pulso + ventana
     * de respuesta)}, y las claves que una pieza lee de otra (D-2) ya traen el valor de la dueña.
     */
    public ParametrosPorPieza vigentes() {
        return estado().efectivos();
    }

    /** Catálogo para la API: las piezas por prioridad y una entrada por copia {@code (pieza, clave)}. */
    public CatalogoReglasDto catalogo() {
        Estado e = estado();

        // Todo lo que se puede prender o apagar (reglas por sector, de macro-zona y servicios que deciden), no sólo las Rule.
        List<ReglaCatalogoDto> reglas = reglasCatalogo.reglas();

        List<ParametroDto> parametros = new ArrayList<>();
        for (ConsumidorParametros pieza : catalogo.consumidores()) {
            ParametrosVigentes propios = e.crudos().getOrDefault(pieza.name(), catalogo.fabricas());
            for (DefinicionParametro d : pieza.parametros()) {
                OverridePersistido o = e.overrides().get(new Copia(pieza.name(), d.clave()));
                parametros.add(new ParametroDto(
                        pieza.name(), d.clave(), d.etiqueta(), d.descripcion(), d.familia().name(), d.tipo().name(),
                        d.unidad(), propios.valor(d.clave()).canonico(), d.fabrica(), d.min(), d.max(), d.decimales(),
                        d.refSpec(), o != null,
                        o != null ? o.updatedBy() : null, o != null ? o.updatedTs() : null));
            }
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
     * Publica el estado cargado sólo si nadie invalidó mientras se cargaba, y nunca desde la
     * transacción de {@link #guardar}: ahí la lectura puede incluir cambios sin commit (que
     * podrían revertirse). En cualquier otra transacción (telemetría, barrido) la lectura es de
     * datos commiteados y publicar es correcto: si no, el cache quedaría frío y cada
     * {@code vigentes()} iría a la base.
     */
    private synchronized void publicar(Estado e, long gen) {
        if (gen == generacion.get() && enGuardado.get() == null) {
            cache = e;
        }
    }

    private Estado cargar() {
        Map<String, ValorParametro> fabrica = catalogo.fabricas().valores();
        Map<String, Map<String, ValorParametro>> propios = new LinkedHashMap<>();
        for (ConsumidorParametros pieza : catalogo.consumidores()) {
            propios.put(pieza.name(), new HashMap<>(fabrica));
        }
        Map<Copia, OverridePersistido> overrides = new LinkedHashMap<>();
        Set<Copia> corruptas = new HashSet<>();
        for (ReglaParametroEntity fila : repository.findAll()) {
            Copia copia = new Copia(fila.getConsumidor(), fila.getClave());
            Optional<DefinicionParametro> def = catalogo.definicion(fila.getClave());
            if (def.isEmpty()) {
                log.warn("Override de un parámetro que ya no existe en el catálogo ('{}' de '{}'): se ignora.",
                        fila.getClave(), fila.getConsumidor());
                continue;
            }
            if (!catalogo.declara(fila.getConsumidor(), fila.getClave())) {
                log.warn("Override de '{}' para '{}', que no lo declara (o ya no existe): se ignora.",
                        fila.getClave(), fila.getConsumidor());
                continue;
            }
            try {
                propios.get(fila.getConsumidor()).put(fila.getClave(), def.get().tipo().parsear(fila.getValor(), def.get()));
                overrides.put(copia, new OverridePersistido(fila.getValor(), fila.getUpdatedBy(), fila.getUpdatedTs()));
            } catch (ValorParametroInvalidoException ex) {
                // Un valor corrupto en la base no puede tirar el motor: se cae a fábrica.
                corruptas.add(copia);
                log.warn("Override inválido de '{}' en '{}' ('{}'): {} Se usa el valor de fábrica.",
                        fila.getClave(), fila.getConsumidor(), fila.getValor(), ex.getMessage());
            }
        }
        Map<String, ParametrosVigentes> crudos = new LinkedHashMap<>();
        propios.forEach((pieza, valores) -> crudos.put(pieza, ParametrosVigentes.de(valores)));
        return new Estado(Map.copyOf(crudos), efectivos(crudos), Map.copyOf(overrides), Set.copyOf(corruptas));
    }

    /**
     * Arma la vista de cada pieza: sus valores propios, las claves que lee de otra pieza con el valor de la dueña (D-2)
     * y, encima, el umbral efectivo de antigüedad.
     */
    private ParametrosPorPieza efectivos(Map<String, ParametrosVigentes> crudos) {
        int intervalo = SensingCadence.intervalMinutes(operativaRepository.findById(OPERATIVA_ID)
                .map(ConfiguracionOperativaEntity::getIntervaloSensadoMinutos).orElse(null));
        // La ventana de respuesta sale de los plazos del pulso de sensado, su dueño (D-1).
        ParametrosVigentes pulso = crudos.getOrDefault(PulsoSensadoService.NAME, catalogo.fabricas());
        double ventana = SensingCadence.responseWindowSeconds(
                pulso.numero(SensingParameters.RESPONSE_WAIT),
                (int) pulso.numero(SensingParameters.RETRIES),
                pulso.numero(SensingParameters.RETRY_WAIT));

        Map<String, ParametrosVigentes> vistas = new LinkedHashMap<>();
        for (ConsumidorParametros pieza : catalogo.consumidores()) {
            Map<String, ValorParametro> vista = new HashMap<>(crudos.get(pieza.name()).valores());
            pieza.parametrosDe().forEach((def, duena) ->
                    vista.put(def.clave(), crudos.get(duena).valor(def.clave())));
            vistas.put(pieza.name(), conUmbralEfectivo(vista, intervalo, ventana));
        }
        ParametrosVigentes base = conUmbralEfectivo(new HashMap<>(catalogo.fabricas().valores()), intervalo, ventana);
        return ParametrosPorPieza.of(vistas, base);
    }

    /**
     * Overlays the effective stale threshold on a view: the stored value is only the floor, the real threshold also
     * depends on the pulse interval and its response window (see {@link SensingCadence}).
     */
    private static ParametrosVigentes conUmbralEfectivo(Map<String, ValorParametro> vista, int intervalo, double ventana) {
        String clave = ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA.clave();
        double piso = ((ValorParametro.Numero) vista.get(clave)).valor();
        vista.put(clave, new ValorParametro.Numero(SensingCadence.staleThresholdSeconds(piso, intervalo, ventana)));
        return ParametrosVigentes.de(vista);
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
     * Aplica un lote de cambios, todo o nada. Cada cambio nombra la copia {@code (regla, clave)}; {@code valor = null}
     * (o igual a fábrica) borra el override. Valida que la pieza declare la clave, tipo, rango y restricciones cruzadas
     * sobre el conjunto resultante (D-4).
     *
     * <p><b>No invocar desde una transacción externa:</b> corre en una transacción propia
     * ({@code REQUIRES_NEW}) para que el lock se suelte después del commit; desde una externa
     * seguiría tomando una conexión extra mientras la otra espera.
     *
     * @throws ParametrosInvalidosException si algo no cumple; no se persiste nada
     */
    public CatalogoReglasDto guardar(List<CambioParametro> cambios, String usuario) {
        if (cambios == null || cambios.isEmpty()) {
            return catalogo();
        }
        // El lock envuelve a la transacción (no al revés): el siguiente guardado ya ve el commit.
        synchronized (escritura) {
            return transaccion.execute(status -> {
                enGuardado.set(Boolean.TRUE);
                try {
                    return guardarEnTransaccion(cambios, usuario);
                } finally {
                    enGuardado.remove();
                }
            });
        }
    }

    private CatalogoReglasDto guardarEnTransaccion(List<CambioParametro> cambios, String usuario) {
        // Se parte de la base, no del cache: el guardado decide sobre el estado persistido.
        Estado actual = cargar();
        List<ErrorParametro> errores = new ArrayList<>();
        // Pieza → valores propios después del lote. Sólo se copian las piezas que el lote toca.
        Map<String, Map<String, ValorParametro>> resultante = new HashMap<>();
        // Copia → valor canónico nuevo, o null para restablecer. Sólo los que validaron.
        Map<Copia, String> aplicar = new LinkedHashMap<>();

        for (CambioParametro c : cambios) {
            if (c == null) {
                errores.add(new ErrorParametro(null, null, "Hay un cambio vacío en el lote."));
                continue;
            }
            Optional<DefinicionParametro> def = c.clave() == null ? Optional.empty() : catalogo.definicion(c.clave());
            if (def.isEmpty()) {
                errores.add(new ErrorParametro(c.regla(), c.clave(), "El parámetro no existe en el catálogo."));
                continue;
            }
            if (catalogo.consumidor(c.regla()).isEmpty()) {
                errores.add(new ErrorParametro(c.regla(), c.clave(), "La regla no existe o no lee parámetros."));
                continue;
            }
            if (!catalogo.declara(c.regla(), c.clave())) {
                errores.add(new ErrorParametro(c.regla(), c.clave(), "La regla no tiene este parámetro."));
                continue;
            }
            Copia copia = new Copia(c.regla(), c.clave());
            if (aplicar.containsKey(copia)) {
                errores.add(new ErrorParametro(c.regla(), c.clave(), "El parámetro viene repetido en el lote."));
                continue;
            }
            DefinicionParametro d = def.get();
            Map<String, ValorParametro> propios = resultante.computeIfAbsent(c.regla(),
                    r -> new HashMap<>(actual.crudos().get(r).valores()));
            if (c.valor() == null) {
                propios.put(d.clave(), catalogo.fabricas().valor(d.clave()));
                aplicar.put(copia, null);
                continue;
            }
            try {
                ValorParametro v = d.tipo().parsear(c.valor(), d);
                propios.put(d.clave(), v);
                aplicar.put(copia, v.canonico());
            } catch (ValorParametroInvalidoException e) {
                errores.add(new ErrorParametro(c.regla(), d.clave(), e.getMessage()));
            }
        }

        errores.addAll(violacionesCruzadas(actual, resultante, aplicar.keySet()));

        if (!errores.isEmpty()) {
            throw new ParametrosInvalidosException(errores);
        }

        String autor = usuario != null && !usuario.isBlank() ? usuario : USUARIO_POR_DEFECTO;
        long ahora = System.currentTimeMillis();
        List<String> cambiadas = new ArrayList<>();
        for (Map.Entry<Copia, String> a : aplicar.entrySet()) {
            Copia copia = a.getKey();
            String fabrica = catalogo.fabricas().valor(copia.clave()).canonico();
            // Un valor igual a fábrica es restablecer: "modificado" significa "hay override".
            String nuevo = a.getValue() == null || a.getValue().equals(fabrica) ? null : a.getValue();
            OverridePersistido previo = actual.overrides().get(copia);

            if (nuevo == null) {
                // Una fila corrupta también se borra: es la única forma de restablecerla.
                if (previo != null || actual.corruptas().contains(copia)) {
                    repository.deleteById(new ReglaParametroId(copia.regla(), copia.clave()));
                    cambiadas.add(descripcion(copia));
                }
            } else if (previo == null || !previo.valor().equals(nuevo)) {
                repository.save(new ReglaParametroEntity(copia.regla(), copia.clave(), nuevo, autor, ahora));
                cambiadas.add(descripcion(copia));
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

    /** {@code clave (pieza)}, para el historial: el cambio es de una copia. */
    private static String descripcion(Copia copia) {
        return copia.clave() + " (" + copia.regla() + ")";
    }

    /**
     * Restricciones cruzadas sobre el resultado del lote (D-4), informadas sobre las copias que el lote toca:
     * <ul>
     *   <li>se evalúan en cada pieza que declara <b>todas</b> las claves de la restricción, con sus valores;</li>
     *   <li>si ninguna las declara todas pero cada clave tiene una sola pieza que la declara, se evalúan con el valor de
     *       cada dueña;</li>
     *   <li>si no, no se evalúan: son copias de piezas distintas que el agrónomo eligió separar.</li>
     * </ul>
     */
    private List<ErrorParametro> violacionesCruzadas(Estado actual, Map<String, Map<String, ValorParametro>> resultante,
                                                     Set<Copia> tocadas) {
        List<ErrorParametro> errores = new ArrayList<>();
        for (RestriccionCruzada r : catalogo.restricciones()) {
            List<String> completas = catalogo.consumidores().stream().map(ConsumidorParametros::name)
                    .filter(p -> r.claves().stream().allMatch(k -> catalogo.declara(p, k))).toList();
            if (!completas.isEmpty()) {
                for (String pieza : completas) {
                    List<Copia> deLaPieza = r.claves().stream().map(k -> new Copia(pieza, k)).filter(tocadas::contains)
                            .toList();
                    if (!deLaPieza.isEmpty() && !r.cumple(ParametrosVigentes.de(propios(actual, resultante, pieza)))) {
                        deLaPieza.forEach(c -> errores.add(new ErrorParametro(c.regla(), c.clave(), r.mensaje())));
                    }
                }
                continue;
            }
            boolean unaDuenaPorClave = r.claves().stream().allMatch(k -> catalogo.declarantes(k).size() == 1);
            if (!unaDuenaPorClave) {
                continue;
            }
            List<Copia> copias = r.claves().stream().map(k -> new Copia(catalogo.declarantes(k).get(0), k)).toList();
            List<Copia> deLasDuenas = copias.stream().filter(tocadas::contains).toList();
            if (deLasDuenas.isEmpty()) {
                continue;
            }
            Map<String, ValorParametro> combinados = new HashMap<>(catalogo.fabricas().valores());
            copias.forEach(c -> combinados.put(c.clave(), propios(actual, resultante, c.regla()).get(c.clave())));
            if (!r.cumple(ParametrosVigentes.de(combinados))) {
                deLasDuenas.forEach(c -> errores.add(new ErrorParametro(c.regla(), c.clave(), r.mensaje())));
            }
        }
        return errores;
    }

    private static Map<String, ValorParametro> propios(Estado actual, Map<String, Map<String, ValorParametro>> resultante,
                                                       String pieza) {
        Map<String, ValorParametro> cambiados = resultante.get(pieza);
        return cambiados != null ? cambiados : actual.crudos().get(pieza).valores();
    }

    /**
     * Invalida ahora y otra vez al terminar la transacción (commit o rollback): si otro hilo
     * repoblara el cache entre medio, leería la base sin el commit y quedaría con valores viejos
     * hasta el próximo guardado.
     */
    public void invalidarAlTerminar() {
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
