package com.yerbanalytics.backend;

import com.yerbanalytics.backend.config.NurseryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(NurseryProperties.class)
public class YerbanalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(YerbanalyticsApplication.class, args);
    }

}