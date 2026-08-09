package com.example.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.audit.event.ArchivalProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ArchivalProperties.class)
public class CoreAuditLogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreAuditLogServiceApplication.class, args);
    }
}
