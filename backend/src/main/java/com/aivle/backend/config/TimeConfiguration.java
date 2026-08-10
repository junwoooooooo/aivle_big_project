package com.aivle.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    @Bean
    Clock jobClock() {
        return Clock.systemUTC();
    }
}
