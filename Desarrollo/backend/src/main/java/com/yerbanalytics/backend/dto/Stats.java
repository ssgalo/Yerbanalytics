package com.yerbanalytics.backend.dto;

public record Stats(
        Integer total, Integer sano, Integer warning, Integer critical,
        Integer offline, Integer alerta, Double sanoPct, Integer actToday,
        Integer actRiego, Integer actInsumo, Integer actSombra, Integer diagCount
) {}