package com.yerbanalytics.backend.sensing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensingCadence")
class SensingCadenceTest {

    @Test
    void nullReadsAsDefault() {
        assertThat(SensingCadence.pullSeconds(null)).isEqualTo(60);
    }

    @Test
    void clampsBelowMinimum() {
        assertThat(SensingCadence.pullSeconds(9)).isEqualTo(10);
        assertThat(SensingCadence.pullSeconds(-5)).isEqualTo(10);
    }

    @Test
    void clampsAboveMaximum() {
        assertThat(SensingCadence.pullSeconds(3601)).isEqualTo(3600);
    }

    @Test
    void keepsValuesInRange() {
        assertThat(SensingCadence.pullSeconds(10)).isEqualTo(10);
        assertThat(SensingCadence.pullSeconds(30)).isEqualTo(30);
        assertThat(SensingCadence.pullSeconds(3600)).isEqualTo(3600);
    }

    @Test
    void floorDominatesWhenPullIsShort() {
        assertThat(SensingCadence.staleThresholdSeconds(90, 10)).isEqualTo(90.0);
    }

    @Test
    void threeTimesPullDominatesWhenLarger() {
        assertThat(SensingCadence.staleThresholdSeconds(90, 60)).isEqualTo(180.0);
    }
}
