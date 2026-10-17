package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.ManualLock;
import com.yerbanalytics.backend.dto.NewManualLock;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.riego.SolicitudRiego;
import com.yerbanalytics.backend.exception.ManualLockConflictException;
import com.yerbanalytics.backend.exception.ManualLockNotFoundException;
import com.yerbanalytics.backend.exception.InvalidManualLockException;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Bloqueo manual (HU-19, reglas_v2 S-01): alta, consulta, reanudación y su efecto inmediato. */
@DisplayName("ManualLockService")
class ManualLockServiceTest {

    private static final long NOW = 1_759_514_400_000L;

    private ManualLockRepository locks;
    private SectorRepository sectors;
    private ZonaRepository zones;
    private ActuatorShutdown shutdown;
    private ColaRiego queue;
    private HistorialService history;
    private ManualLockService service;

    private static ZonaEntity zone(String id) {
        ZonaEntity z = new ZonaEntity();
        z.setId(id);
        z.setName("Macro-zona " + id);
        return z;
    }

    private static SectorEntity sector(String id, ZonaEntity zone) {
        SectorEntity s = new SectorEntity();
        s.setId(id);
        s.setZona(zone);
        return s;
    }

    private static ManualLockEntity lock(Long id, String sectorId, String zoneId) {
        ManualLockEntity e = new ManualLockEntity();
        e.setId(id);
        e.setSectorId(sectorId);
        e.setZonaId(zoneId);
        e.setActivatedBy("Ana");
        e.setActivatedAt(NOW - 1000);
        e.setReason("mantenimiento");
        e.setActive(true);
        return e;
    }

    private static SolicitudRiego request(String zoneId, String sectorId) {
        return new SolicitudRiego(zoneId, sectorId, 1, new DetalleRiego(10, 60, 40.0, false),
                "RiegoPorDeficitRule", Instant.ofEpochMilli(NOW));
    }

    @BeforeEach
    void setUp() {
        locks = mock(ManualLockRepository.class);
        sectors = mock(SectorRepository.class);
        zones = mock(ZonaRepository.class);
        shutdown = mock(ActuatorShutdown.class);
        queue = new ColaRiego();
        history = mock(HistorialService.class);
        ZonaEntity mz2 = zone("MZ-2");
        when(zones.findById("MZ-2")).thenReturn(Optional.of(mz2));
        when(zones.findById("MZ-9")).thenReturn(Optional.empty());
        when(sectors.findByZonaId("MZ-2")).thenReturn(List.of(sector("MZ-2-001", mz2), sector("MZ-2-002", mz2)));
        when(sectors.findById("MZ-2-001")).thenReturn(Optional.of(sector("MZ-2-001", mz2)));
        when(sectors.findById("MZ-1-001")).thenReturn(Optional.of(sector("MZ-1-001", zone("MZ-1"))));
        when(locks.save(any(ManualLockEntity.class))).thenAnswer(inv -> {
            ManualLockEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(42L);
            }
            return e;
        });
        when(shutdown.close(anyString(), anyCollection(), anyCollection())).thenReturn(new ActuatorShutdown.Result(0, 0));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
        service = new ManualLockService(locks, sectors, zones, shutdown, queue, history, clock);
    }

    // ── Alta ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un bloqueo de sector guarda el sector y deja la zona en null: la zona entera sólo se bloquea sin sector")
    void sectorLockLeavesTheZoneNull() {
        ManualLock created = service.activate(new NewManualLock("MZ-2", "MZ-2-001", "mantenimiento"), "Ana");

        ArgumentCaptor<ManualLockEntity> saved = ArgumentCaptor.forClass(ManualLockEntity.class);
        verify(locks).save(saved.capture());
        assertThat(saved.getValue().getSectorId()).isEqualTo("MZ-2-001");
        assertThat(saved.getValue().getZonaId()).isNull();
        assertThat(saved.getValue().getActive()).isTrue();
        assertThat(saved.getValue().getActivatedBy()).isEqualTo("Ana");
        assertThat(saved.getValue().getActivatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getReason()).isEqualTo("mantenimiento");
        // La respuesta sí informa la zona del sector, para que el dashboard pueda agrupar.
        assertThat(created).isEqualTo(new ManualLock(42L, "MZ-2", "MZ-2-001", "Ana", NOW, "mantenimiento"));
    }

    @Test
    @DisplayName("un bloqueo de macro-zona guarda la zona y deja el sector en null")
    void zoneLockLeavesTheSectorNull() {
        ManualLock created = service.activate(new NewManualLock("MZ-2", null, "  mantenimiento  "), "Ana");

        ArgumentCaptor<ManualLockEntity> saved = ArgumentCaptor.forClass(ManualLockEntity.class);
        verify(locks).save(saved.capture());
        assertThat(saved.getValue().getSectorId()).isNull();
        assertThat(saved.getValue().getZonaId()).isEqualTo("MZ-2");
        assertThat(saved.getValue().getReason()).isEqualTo("mantenimiento");
        assertThat(created.zonaId()).isEqualTo("MZ-2");
        assertThat(created.sectorId()).isNull();
    }

    @Test
    @DisplayName("sin zona o sin motivo es una solicitud inválida y no guarda nada")
    void rejectsMissingZoneOrReason() {
        assertThatThrownBy(() -> service.activate(new NewManualLock(null, null, "x"), "Ana"))
                .isInstanceOf(InvalidManualLockException.class);
        assertThatThrownBy(() -> service.activate(new NewManualLock(" ", null, "x"), "Ana"))
                .isInstanceOf(InvalidManualLockException.class);
        assertThatThrownBy(() -> service.activate(new NewManualLock("MZ-2", null, null), "Ana"))
                .isInstanceOf(InvalidManualLockException.class);
        assertThatThrownBy(() -> service.activate(new NewManualLock("MZ-2", null, "   "), "Ana"))
                .isInstanceOf(InvalidManualLockException.class);
        assertThatThrownBy(() -> service.activate(null, "Ana")).isInstanceOf(InvalidManualLockException.class);
        verify(locks, never()).save(any());
    }

    @Test
    @DisplayName("una zona o un sector inexistente, o un sector de otra zona, es inválido")
    void rejectsUnknownScope() {
        assertThatThrownBy(() -> service.activate(new NewManualLock("MZ-9", null, "x"), "Ana"))
                .isInstanceOf(InvalidManualLockException.class);
        assertThatThrownBy(() -> service.activate(new NewManualLock("MZ-2", "MZ-2-999", "x"), "Ana"))
                .isInstanceOf(InvalidManualLockException.class);
        assertThatThrownBy(() -> service.activate(new NewManualLock("MZ-2", "MZ-1-001", "x"), "Ana"))
                .isInstanceOf(InvalidManualLockException.class);
        verify(locks, never()).save(any());
    }

    @Test
    @DisplayName("bloquear lo que ya está bloqueado es un conflicto (409), no un segundo bloqueo")
    void rejectsADuplicateOfTheSameScope() {
        when(locks.findBySectorIdAndActiveTrue("MZ-2-001")).thenReturn(List.of(lock(7L, "MZ-2-001", null)));
        when(locks.findByZonaIdAndActiveTrue("MZ-2")).thenReturn(List.of(lock(8L, null, "MZ-2")));

        assertThatThrownBy(() -> service.activate(new NewManualLock("MZ-2", "MZ-2-001", "x"), "Ana"))
                .isInstanceOf(ManualLockConflictException.class);
        assertThatThrownBy(() -> service.activate(new NewManualLock("MZ-2", null, "x"), "Ana"))
                .isInstanceOf(ManualLockConflictException.class);
        verify(locks, never()).save(any());
    }

    // ── Consulta ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lista los bloqueos activos, y con filtro de zona incluye los de sus sectores")
    void listsActiveLocksFilteredByZone() {
        ManualLockEntity zoneLock = lock(1L, null, "MZ-2");
        ManualLockEntity sectorLockSameZone = lock(2L, "MZ-2-001", null);
        ManualLockEntity sectorLockOtherZone = lock(3L, "MZ-1-001", null);
        when(locks.findByActiveTrueOrderByActivatedAtDesc())
                .thenReturn(List.of(zoneLock, sectorLockSameZone, sectorLockOtherZone));

        assertThat(service.list(null, null)).extracting(ManualLock::id).containsExactly(1L, 2L, 3L);
        assertThat(service.list("MZ-2", null)).extracting(ManualLock::id).containsExactly(1L, 2L);
        assertThat(service.list(null, "MZ-1-001")).extracting(ManualLock::id).containsExactly(3L);
        assertThat(service.list("MZ-2", null)).extracting(ManualLock::zonaId).containsOnly("MZ-2");
    }

    // ── Reanudar ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reanudar desactiva el bloqueo (no lo borra) y conserva quién y cuándo lo activó")
    void releaseDeactivatesInsteadOfDeleting() {
        ManualLockEntity existing = lock(7L, null, "MZ-2");
        when(locks.findById(7L)).thenReturn(Optional.of(existing));

        service.release(7L, "Beto");

        verify(locks).save(existing);
        verify(locks, never()).delete(any());
        verify(locks, never()).deleteById(anyLong());
        assertThat(existing.getActive()).isFalse();
        assertThat(existing.getActivatedBy()).isEqualTo("Ana");
        assertThat(existing.getActivatedAt()).isEqualTo(NOW - 1000);
    }

    @Test
    @DisplayName("reanudar un bloqueo inexistente o ya reanudado es 404")
    void releaseOfUnknownOrInactiveIsNotFound() {
        ManualLockEntity inactive = lock(8L, null, "MZ-2");
        inactive.setActive(false);
        when(locks.findById(7L)).thenReturn(Optional.empty());
        when(locks.findById(8L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.release(7L, "Beto")).isInstanceOf(ManualLockNotFoundException.class);
        assertThatThrownBy(() -> service.release(8L, "Beto")).isInstanceOf(ManualLockNotFoundException.class);
    }

    // ── Efecto al activar (v2 S-01, design D-3) ──────────────────────────────

    @Test
    @DisplayName("bloquear un sector cierra su válvula y su bomba — no la mediasombra — y cancela su riego pendiente")
    void sectorLockStopsItsActuatorsAndItsQueuedIrrigation() {
        queue.solicitar(request("MZ-2", "MZ-2-001"));
        queue.solicitar(request("MZ-2", "MZ-2-002"));

        service.activate(new NewManualLock("MZ-2", "MZ-2-001", "mantenimiento"), "Ana");

        verify(shutdown).close("MZ-2", List.of("MZ-2-001"),
                Set.of(HardwareService.ELECTROVALVULA, HardwareService.BOMBA));
        assertThat(queue.contiene("MZ-2-001")).isFalse();
        assertThat(queue.contiene("MZ-2-002")).as("los demás sectores no se tocan").isTrue();
    }

    @Test
    @DisplayName("bloquear una macro-zona cierra las válvulas y bombas de todos sus sectores y vacía su cola")
    void zoneLockStopsEverySectorAndEmptiesTheQueue() {
        queue.solicitar(request("MZ-2", "MZ-2-001"));
        queue.solicitar(request("MZ-2", "MZ-2-002"));
        queue.solicitar(request("MZ-1", "MZ-1-001"));

        service.activate(new NewManualLock("MZ-2", null, "mantenimiento"), "Ana");

        verify(shutdown).close("MZ-2", List.of("MZ-2-001", "MZ-2-002"),
                Set.of(HardwareService.ELECTROVALVULA, HardwareService.BOMBA));
        assertThat(queue.pendientes("MZ-2")).isEmpty();
        assertThat(queue.contiene("MZ-1-001")).as("otra zona queda igual").isTrue();
    }

    @Test
    @DisplayName("el bloqueo queda guardado aunque el cierre falle: el motor ya no actuará")
    void theLockSurvivesAShutdownFailure() {
        when(shutdown.close(anyString(), anyCollection(), anyCollection())).thenThrow(new RuntimeException("broker caído"));

        ManualLock created = service.activate(new NewManualLock("MZ-2", null, "mantenimiento"), "Ana");

        assertThat(created.id()).isEqualTo(42L);
        verify(locks).save(any(ManualLockEntity.class));
    }

    // ── Reanudar no actúa (D-4) ──────────────────────────────────────────────

    @Test
    @DisplayName("reanudar no encola riego ni publica ningún comando")
    void releaseDoesNothingRetroactively() {
        when(locks.findById(7L)).thenReturn(Optional.of(lock(7L, null, "MZ-2")));

        service.release(7L, "Beto");

        assertThat(queue.zonas()).isEmpty();
        verify(shutdown, never()).close(anyString(), anyCollection(), anyCollection());
    }

    // ── Alertas (D-6) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("activar deja una alerta INFO con el usuario, el alcance y el motivo")
    void activationRegistersAnInfoAlert() {
        service.activate(new NewManualLock("MZ-2", "MZ-2-001", "mantenimiento de la línea"), "Ana");

        ArgumentCaptor<DetalleAlerta> alert = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(history).registrarAlerta(eq("MZ-2"), eq("Macro-zona MZ-2"), eq("ManualLockRule"), alert.capture(), eq(NOW));
        assertThat(alert.getValue().nivel()).isEqualTo(NivelAlerta.INFO);
        assertThat(alert.getValue().texto()).contains("Ana", "MZ-2-001", "mantenimiento de la línea");
    }

    @Test
    @DisplayName("reanudar deja una alerta INFO con el usuario y el alcance")
    void releaseRegistersAnInfoAlert() {
        when(locks.findById(7L)).thenReturn(Optional.of(lock(7L, null, "MZ-2")));

        service.release(7L, "Beto");

        ArgumentCaptor<DetalleAlerta> alert = ArgumentCaptor.forClass(DetalleAlerta.class);
        verify(history).registrarAlerta(eq("MZ-2"), eq("Macro-zona MZ-2"), eq("ManualLockRule"), alert.capture(), eq(NOW));
        assertThat(alert.getValue().nivel()).isEqualTo(NivelAlerta.INFO);
        assertThat(alert.getValue().texto()).contains("Beto", "MZ-2");
    }

    @Test
    @DisplayName("si el historial falla, el bloqueo igual queda guardado")
    void aHistoryFailureDoesNotUndoTheLock() {
        org.mockito.Mockito.doThrow(new RuntimeException("base caída")).when(history)
                .registrarAlerta(anyString(), anyString(), anyString(), any(), anyLong());

        ManualLock created = service.activate(new NewManualLock("MZ-2", null, "x"), "Ana");

        assertThat(created.id()).isEqualTo(42L);
    }
}
