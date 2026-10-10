package com.yerbanalytics.backend;

import com.yerbanalytics.backend.config.CapturaProperties;
import com.yerbanalytics.backend.config.NurseryProperties;
import com.yerbanalytics.backend.config.PasadaProperties;
import com.yerbanalytics.backend.config.SecuenciaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({NurseryProperties.class, CapturaProperties.class, PasadaProperties.class,
        SecuenciaProperties.class})
public class YerbanalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(YerbanalyticsApplication.class, args);
    }

}