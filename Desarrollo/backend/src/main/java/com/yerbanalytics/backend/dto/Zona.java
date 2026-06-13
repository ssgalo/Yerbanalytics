package com.yerbanalytics.backend.dto;

import java.util.List;

public record Zona(
        String id, String name, String sub, List<Sector> sectors,
        Integer sano, Integer alerta, Integer off, Integer total
) {}