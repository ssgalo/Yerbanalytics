package com.yerbanalytics.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Feature flags for demo / simulation mode.
 *
 * <p>{@code yerbanalytics.demo.enabled=true} activates the {@code /api/demo/**} endpoints
 * (rule overrides, simulated weather) and lets the simulator bypass cooldowns at runtime.
 * When {@code false} (the default) none of those controllers are registered.
 */
@Component
@ConfigurationProperties(prefix = "yerbanalytics.demo")
public class DemoProperties {

    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
