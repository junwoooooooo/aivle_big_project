package com.aivle.backend.pipeline.conceptportfolio.worker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConceptPortfolioWorkerConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    @Qualifier("conceptPortfolioAiExecutor")
    ExecutorService conceptPortfolioAiExecutor(ConceptPortfolioExecutionProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threads = action -> {
            Thread thread = new Thread(action,
                "concept-portfolio-ai-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(properties.executorThreads(), properties.executorThreads(),
            0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(properties.queueCapacity()),
            threads, new ThreadPoolExecutor.AbortPolicy());
    }
}
