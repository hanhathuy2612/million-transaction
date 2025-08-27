package com.hnh.example.transaction_example.config;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(@NonNull ScheduledTaskRegistrar taskRegistrar) {
        // Use a dedicated thread pool for scheduled tasks
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(2));
    }
}
