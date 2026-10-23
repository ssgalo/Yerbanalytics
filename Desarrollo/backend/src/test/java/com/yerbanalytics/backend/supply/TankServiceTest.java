package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.dto.NewTankLoad;
import com.yerbanalytics.backend.dto.TankLevel;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametros;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.exception.InvalidTankLoadException;
import com.yerbanalytics.backend.exception.TankZoneNotFoundException;
import com.yerbanalytics.backend.model.TankEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.TankRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TankService · stock de los tanques")
class TankServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T11:00:00Z"), ZonaHorariaVivero.ZONA);

    private TankRepository tanks;
    private HistorialService history;
    private TankService service;
    private TankEntity stored;

    @BeforeEach
    void setUp() {
        tanks = mock(TankRepository.class);
        stored = null;
        when(tanks.save(any())).thenAnswer(i -> {
            stored = i.getArgument(0);
            return stored;
        });
        when(tanks.lockByZoneIdAndSupply(any(), any())).thenAnswer(i -> Optional.ofNullable(stored));
        when(tanks.findByZoneIdAndSupply(any(), any())).thenAnswer(i -> Optional.ofNullable(stored));
        when(tanks.findAllByOrderByZoneIdAscSupplyAsc()).thenAnswer(i -> stored == null ? List.of() : List.of(stored));

        ZonaRepository zones = mock(ZonaRepository.class);
        ZonaEntity zone = new ZonaEntity();
        zone.setId("MZ-1");
        zone.setName("Macro-zona 1");
        when(zones.findById("MZ-1")).thenReturn(Optional.of(zone));

        history = mock(HistorialService.class);
        CatalogoParametrosService catalog = mock(CatalogoParametrosService.class);
        when(catalog.vigentes()).thenReturn(new CatalogoParametros(List.of()).fabricas());
        service = new TankService(tanks, zones, history, catalog, CLOCK);
    }

    private void tank(long levelMl, long capacityMl) {
        stored = new TankEntity(1L, "MZ-1", Supply.FERTILIZER, capacityMl, levelMl, 0L, null);
    }

    private NivelAlerta lastAlertLevel(int times) {
        ArgumentCaptor<DetalleAlerta> captor = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(history, times(times)).registrarAlerta(eq("MZ-1"), any(), eq(TankService.ALERT_RULE), captor.capture(), anyLong());
        return captor.getValue().nivel();
    }

    // --- Carga ---

    @Test
    void theFirstLoadCreatesTheTankAndLeavesTheUserInTheHistory() {
        TankLevel level = service.load("MZ-1", Supply.FERTILIZER, new NewTankLoad(20.0, 50.0), "operario");

        assertThat(level.levelMl()).isEqualTo(20_000);
        assertThat(level.capacityMl()).isEqualTo(50_000);
        assertThat(level.levelPct()).isEqualTo(40.0);
        assertThat(level.updatedBy()).isEqualTo("operario");
        assertThat(level.low()).isFalse();
        ArgumentCaptor<DetalleAlerta> captor = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(history).registrarAlerta(eq("MZ-1"), eq("Macro-zona 1"), eq(TankService.ALERT_RULE), captor.capture(), anyLong());
        assertThat(captor.getValue().nivel()).isEqualTo(NivelAlerta.INFO);
        assertThat(captor.getValue().texto()).contains("operario").contains("fertilizante").contains("MZ-1");
    }

    @Test
    void aLaterLoadAddsToTheLevel() {
        tank(10_000, 50_000);

        TankLevel level = service.load("MZ-1", Supply.FERTILIZER, new NewTankLoad(15.0, null), "operario");

        assertThat(level.levelMl()).isEqualTo(25_000);
        assertThat(level.capacityMl()).isEqualTo(50_000);
    }

    @Test
    void theFirstLoadWithoutCapacityIsRejected() {
        assertThatThrownBy(() -> service.load("MZ-1", Supply.FERTILIZER, new NewTankLoad(20.0, null), "operario"))
                .isInstanceOf(InvalidTankLoadException.class).hasMessageContaining("capacidad");
    }

    @Test
    void aLoadThatOverflowsTheTankIsRejected() {
        tank(40_000, 50_000);

        assertThatThrownBy(() -> service.load("MZ-1", Supply.FERTILIZER, new NewTankLoad(20.0, null), "operario"))
                .isInstanceOf(InvalidTankLoadException.class).hasMessageContaining("no entra");
        assertThat(stored.getLevelMl()).isEqualTo(40_000);
    }

    @Test
    void invalidLitersAreRejected() {
        for (Double liters : new Double[]{null, 0.0, -5.0, Double.NaN}) {
            assertThatThrownBy(() -> service.load("MZ-1", Supply.FERTILIZER, new NewTankLoad(liters, 50.0), "u"))
                    .as("litros " + liters).isInstanceOf(InvalidTankLoadException.class);
        }
        assertThatThrownBy(() -> service.load("MZ-1", Supply.FERTILIZER, null, "u"))
                .isInstanceOf(InvalidTankLoadException.class);
    }

    @Test
    void anUnknownZoneIsNotFound() {
        assertThatThrownBy(() -> service.load("MZ-9", Supply.FERTILIZER, new NewTankLoad(1.0, 5.0), "u"))
                .isInstanceOf(TankZoneNotFoundException.class);
    }

    // --- Stock ---

    @Test
    void checkDistinguishesUnregisteredInsufficientAndEnough() {
        assertThat(service.check("MZ-1", Supply.FERTILIZER, 10)).isEqualTo(TankService.Stock.UNREGISTERED);

        tank(5, 50_000);
        assertThat(service.check("MZ-1", Supply.FERTILIZER, 10)).isEqualTo(TankService.Stock.INSUFFICIENT);

        tank(10, 50_000);
        assertThat(service.check("MZ-1", Supply.FERTILIZER, 10)).isEqualTo(TankService.Stock.OK);
    }

    // --- Descuento y avisos ---

    @Test
    void consumingDiscountsTheDoseWithoutAnAlertWhileThereIsStock() {
        tank(30_000, 50_000);

        service.consume("MZ-1", Supply.FERTILIZER, 100);

        assertThat(stored.getLevelMl()).isEqualTo(29_900);
        verify(history, never()).registrarAlerta(any(), any(), any(), any(), anyLong());
    }

    @Test
    void crossingBelowTwentyPercentRaisesAWarningOnce() {
        tank(10_050, 50_000);   // 20,1 %

        service.consume("MZ-1", Supply.FERTILIZER, 100);   // 19,9 %
        assertThat(lastAlertLevel(1)).isEqualTo(NivelAlerta.WARNING);

        service.consume("MZ-1", Supply.FERTILIZER, 100);   // sigue bajo el aviso: no repite
        verify(history, times(1)).registrarAlerta(any(), any(), any(), any(), anyLong());
    }

    @Test
    void anEmptyTankRaisesACriticalAlert() {
        tank(100, 50_000);

        service.consume("MZ-1", Supply.FERTILIZER, 100);

        assertThat(stored.getLevelMl()).isZero();
        assertThat(lastAlertLevel(1)).isEqualTo(NivelAlerta.CRITICAL);
    }

    @Test
    void theLevelNeverGoesBelowZero() {
        tank(50, 50_000);

        service.consume("MZ-1", Supply.FERTILIZER, 100);

        assertThat(stored.getLevelMl()).isZero();
    }

    @Test
    void consumingFromAnUnregisteredTankIsANoOp() {
        service.consume("MZ-1", Supply.FERTILIZER, 100);

        verify(tanks, never()).save(any());
    }

    @Test
    void aShortageIsReportedOnceUntilTheNextLoad() {
        service.reportShortage("MZ-1", Supply.FERTILIZER, TankService.Stock.INSUFFICIENT);
        service.reportShortage("MZ-1", Supply.FERTILIZER, TankService.Stock.INSUFFICIENT);
        assertThat(lastAlertLevel(1)).isEqualTo(NivelAlerta.CRITICAL);

        service.load("MZ-1", Supply.FERTILIZER, new NewTankLoad(10.0, 50.0), "u");   // la carga limpia el aviso (+1 INFO)
        service.reportShortage("MZ-1", Supply.FERTILIZER, TankService.Stock.INSUFFICIENT);
        verify(history, times(3)).registrarAlerta(any(), any(), any(), any(), anyLong());
    }

    @Test
    void listFlagsTheTanksUnderTheWarningLevel() {
        tank(9_000, 50_000);   // 18 %

        List<TankLevel> levels = service.list();

        assertThat(levels).singleElement().satisfies(l -> {
            assertThat(l.low()).isTrue();
            assertThat(l.supply()).isEqualTo("FERTILIZER");
            assertThat(l.supplyLabel()).isEqualTo("Fertilizante");
        });
    }
}
