package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.dto.NurseryData;
import com.yerbanalytics.backend.service.mock.NurseryGenerator;
import org.springframework.stereotype.Service;

@Service
public class NurseryService {

    private final NurseryGenerator generator;
    private final int seed;
    private NurseryData cache;

    public NurseryService(NurseryProperties properties) {
        this.generator = new NurseryGenerator();
        this.seed = properties.getSeed();
    }

    public NurseryData getSnapshot() {
        if (cache == null) {
            cache = generator.build(seed);
        }
        return cache;
    }
}
