package com.yerbanalytics.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yerbanalytics.mock")
public class NurseryProperties {

    /** Semilla del generador determinístico (alineada con VITE_MOCK_SEED). */
    private int seed = 20260613;

    public int getSeed() {
        return seed;
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }
}
