package it.polito.cloudresources.be.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;

/**
 * Configuration class to enable asynchronous execution
 * Used for audit logging to avoid impacting client performance
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Configure the executor used for asynchronous operations
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("AuditLog-");
        executor.initialize();
        return executor;
    }
}