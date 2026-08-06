package com.aivle.backend.journey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ConceptEligibilityExecutorConfiguration {
    @Bean("conceptEligibilityExecutor")
    TaskExecutor conceptEligibilityExecutor() {
        ThreadPoolTaskExecutor executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2); executor.setQueueCapacity(20); executor.setThreadNamePrefix("concept-eligibility-");
        executor.initialize(); return executor;
    }
}
