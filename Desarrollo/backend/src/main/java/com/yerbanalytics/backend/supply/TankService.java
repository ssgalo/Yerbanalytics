package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.dto.NewTankLoad;
import com.yerbanalytics.backend.dto.TankLevel;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DeclaredParameters;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosInsumo;
import com.yerbanalytics.backend.exception.InvalidTankLoadException;
import com.yerbanalytics.backend.exception.TankZoneNotFoundException;
import com.yerbanalytics.backend.model.TankEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.TankRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stock de los tanques de insumo (reglas_v2 §8.3): un tanque por insumo y por macro-zona, que carga el operario y que
 * cada aplicación descuenta. Avisa {@code WARNING} al cruzar por debajo del nivel de reposición
 * ({@code insumo.stock-aviso}) y {@code CRITICAL} cuando se vacía o cuando una aplicación no puede hacerse por falta
 * de stock. Las alertas quedan en el historial de la macro-zona.
 *
 * <p>Un tanque que nadie registró es un stock <b>desconocido</b>, no un tanque lleno: no se aplica contra él.
 *
 * <p>El descuento y la carga bloquean la fila: la telemetría y el reintento de las aplicaciones diferidas corren en
 * hilos distintos y no pueden pisarse el nivel.
 */
@Service
public class TankService implements ConsumidorParametros {

    static final String ALERT_RULE = "TankStock";
    private static final double ML_PER_LITER = 1000.0;

    /** Qué se sabe del stock de un insumo en una zona frente a una aplicación de {@code ml}. */
    public enum Stock {
        /** Nadie registró el tanque. */
        UNREGISTERED,
        /** El tanque no tiene los mililitros que pide la aplicación. */
        INSUFFICIENT,
        OK
    }

    private final TankRepository tanks;
    private final ZonaRepository zones;
    private final HistorialService history;
    private final DeclaredParameters params;
    private final Clock clock;

    /** Tanques de los que ya se avisó la falta de stock, para no repetir el aviso en cada pedido. Se limpia al cargar. */
    private final Set<String> shortageAlerted = ConcurrentHashMap.newKeySet();

    public TankService(TankRepository tanks, ZonaRepository zones, HistorialService history,
                       // Perezoso: el catálogo necesita a este bean (es un consumidor de parámetros) para armarse.
                       @Lazy CatalogoParametrosService catalog, Clock clock) {
        this.tanks = tanks;
        this.zones = zones;
        this.history = history;
        this.params = new DeclaredParameters(this, catalog::vigentes);
        this.clock = clock;
    }

    @Override
    public String name() {
        return "TankService";
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosInsumo.STOCK_WARNING);
    }

    @Transactional(readOnly = true)
    public List<TankLevel> list() {
        double warning = params.number(ParametrosInsumo.STOCK_WARNING);
        return tanks.findAllByOrderByZoneIdAscSupplyAsc().stream().map(t -> view(t, warning)).toList();
    }

    /** Si el tanque tiene los {@code ml} que pide una aplicación. */
    @Transactional(readOnly = true)
    public Stock check(String zoneId, Supply supply, int ml) {
        return tanks.findByZoneIdAndSupply(zoneId, supply)
                .map(t -> t.getLevelMl() >= ml ? Stock.OK : Stock.INSUFFICIENT)
                .orElse(Stock.UNREGISTERED);
    }

    /**
     * Registra una carga. La primera vez exige la capacidad; la carga no puede pasar la capacidad (un desborde casi
     * siempre es un error de tipeo, y aceptarlo en silencio dejaría un stock que no existe).
     */
    @Transactional
    public TankLevel load(String zoneId, Supply supply, NewTankLoad request, String user) {
        ZonaEntity zone = zones.findById(zoneId).orElseThrow(() -> new TankZoneNotFoundException(zoneId));
        if (request == null || request.liters() == null || !Double.isFinite(request.liters()) || request.liters() <= 0) {
            throw new InvalidTankLoadException("Los litros de la carga deben ser un número mayor que cero.");
        }
        if (request.capacityLiters() != null
                && (!Double.isFinite(request.capacityLiters()) || request.capacityLiters() <= 0)) {
            throw new InvalidTankLoadException("La capacidad del tanque debe ser un número mayor que cero.");
        }

        TankEntity tank = tanks.lockByZoneIdAndSupply(zoneId, supply).orElse(null);
        if (tank == null && request.capacityLiters() == null) {
            throw new InvalidTankLoadException(
                    "El tanque todavía no está registrado: indicá su capacidad en litros.");
        }
        long capacityMl = request.capacityLiters() != null
                ? Math.round(request.capacityLiters() * ML_PER_LITER) : tank.getCapacityMl();
        long previousMl = tank == null ? 0 : tank.getLevelMl();
        long newLevelMl = previousMl + Math.round(request.liters() * ML_PER_LITER);
        if (newLevelMl > capacityMl) {
            throw new InvalidTankLoadException(String.format(
                    "La carga no entra: el tanque quedaría en %.1f L y su capacidad es de %.1f L.",
                    newLevelMl / ML_PER_LITER, capacityMl / ML_PER_LITER));
        }

        long now = clock.millis();
        if (tank == null) {
            tank = new TankEntity(null, zoneId, supply, capacityMl, newLevelMl, now, user);
        } else {
            tank.setCapacityMl(capacityMl);
            tank.setLevelMl(newLevelMl);
            tank.setUpdatedTs(now);
            tank.setUpdatedBy(user);
        }
        tanks.save(tank);
        shortageAlerted.remove(key(zoneId, supply));

        history.registrarAlerta(zoneId, zone.getName(), ALERT_RULE, new DetalleAlerta(NivelAlerta.INFO, String.format(
                "%s registró una carga de %.1f L de %s en %s: el tanque queda en %.1f L de %.1f L.", user,
                request.liters(), supply.label().toLowerCase(), zoneId, newLevelMl / ML_PER_LITER,
                capacityMl / ML_PER_LITER)), now);
        return view(tank, params.number(ParametrosInsumo.STOCK_WARNING));
    }

    /**
     * Descuenta una aplicación. Avisa si el nivel quedó en cero ({@code CRITICAL}) o si cruzó por debajo del nivel de
     * reposición ({@code WARNING}, una sola vez por cruce).
     */
    @Transactional
    public void consume(String zoneId, Supply supply, int ml) {
        TankEntity tank = tanks.lockByZoneIdAndSupply(zoneId, supply).orElse(null);
        if (tank == null) {
            return;   // sin tanque registrado no hay stock que descontar (la aplicación ya salteó el control con un bypass)
        }
        double warning = params.number(ParametrosInsumo.STOCK_WARNING);
        double before = pct(tank.getLevelMl(), tank.getCapacityMl());
        tank.setLevelMl(Math.max(0, tank.getLevelMl() - ml));
        tank.setUpdatedTs(clock.millis());
        tanks.save(tank);

        double after = pct(tank.getLevelMl(), tank.getCapacityMl());
        if (tank.getLevelMl() == 0) {
            alert(zoneId, NivelAlerta.CRITICAL,
                    String.format("El tanque de %s de %s quedó vacío: no se aplica más hasta reponerlo.",
                            supply.label().toLowerCase(), zoneId));
            shortageAlerted.add(key(zoneId, supply));
        } else if (after < warning && before >= warning) {
            alert(zoneId, NivelAlerta.WARNING,
                    String.format("Reponer %s en %s: el tanque quedó en %.0f %% (%.1f L).",
                            supply.label().toLowerCase(), zoneId, after, tank.getLevelMl() / ML_PER_LITER));
        }
    }

    /** Una aplicación no se pudo hacer por falta de stock: {@code CRITICAL}, una sola vez hasta la próxima carga. */
    public void reportShortage(String zoneId, Supply supply, Stock stock) {
        if (!shortageAlerted.add(key(zoneId, supply))) {
            return;
        }
        String why = stock == Stock.UNREGISTERED
                ? "el tanque no está registrado" : "el tanque no tiene insumo suficiente";
        alert(zoneId, NivelAlerta.CRITICAL, String.format(
                "No se pudo aplicar %s en %s: %s.", supply.label().toLowerCase(), zoneId, why));
    }

    private void alert(String zoneId, NivelAlerta level, String text) {
        String zoneName = zones.findById(zoneId).map(ZonaEntity::getName).orElse(zoneId);
        history.registrarAlerta(zoneId, zoneName, ALERT_RULE, new DetalleAlerta(level, text), clock.millis());
    }

    private TankLevel view(TankEntity t, double warningPct) {
        double pct = pct(t.getLevelMl(), t.getCapacityMl());
        return new TankLevel(t.getZoneId(), t.getSupply().name(), t.getSupply().label(), t.getCapacityMl(),
                t.getLevelMl(), pct, pct < warningPct, t.getUpdatedTs(), t.getUpdatedBy());
    }

    private static double pct(long levelMl, long capacityMl) {
        return capacityMl == 0 ? 0 : levelMl * 100.0 / capacityMl;
    }

    private static String key(String zoneId, Supply supply) {
        return zoneId + "|" + supply.name();
    }
}
