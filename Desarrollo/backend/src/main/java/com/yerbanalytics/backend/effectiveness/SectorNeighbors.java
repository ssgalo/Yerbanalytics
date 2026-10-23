package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.biotic.GridNeighbors;
import com.yerbanalytics.backend.biotic.SectorGrid;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Los lindantes de un sector que tienen cierto diagnóstico: lo que E-03 mira para saber si un daño se propagó. Los lindantes salen
 * de la misma grilla que usa F-04 ({@link GridNeighbors}), con su mismo supuesto: la disposición visual es la vecindad física.
 */
@Component
public class SectorNeighbors {

    private final SectorRepository sectors;
    private final SectorGrid grid;

    public SectorNeighbors(SectorRepository sectors, SectorGrid grid) {
        this.sectors = sectors;
        this.grid = grid;
    }

    /**
     * @param sector     el sector tratado
     * @param hasDamage  cuándo un lindante cuenta como afectado (mismo estado de diagnóstico y vigente)
     * @return los ids de los lindantes afectados, ordenados
     */
    public Set<String> affectedAround(SectorEntity sector, Predicate<SectorEntity> hasDamage) {
        List<SectorEntity> zoneSectors = sectors.findByZonaId(sector.getZona().getId());
        Map<Integer, SectorEntity> byNumber = zoneSectors.stream()
                .filter(s -> s.getN() != null)
                .collect(Collectors.toMap(SectorEntity::getN, s -> s, (a, b) -> a));
        if (sector.getN() == null) {
            return Set.of();
        }
        int perRow = grid.sectorsPerRow(zoneSectors.size());
        return GridNeighbors.of(sector.getN(), perRow, zoneSectors.size()).stream()
                .map(byNumber::get)
                .filter(neighbor -> neighbor != null && hasDamage.test(neighbor))
                .map(SectorEntity::getId)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }
}
