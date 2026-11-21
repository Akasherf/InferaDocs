package com.documind.processing;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Configuration
@EnableScheduling
public class ProcessingSchedulingConfig {
    private final ProcessingService processing;
    public ProcessingSchedulingConfig(ProcessingService processing){this.processing=processing;}
    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart(){processing.recoverInterrupted();}
}
