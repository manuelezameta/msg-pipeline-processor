package com.msgpipeline.processor.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Punto de entrada Spring Boot para perfil 'local'. */
@SpringBootApplication(scanBasePackages = "com.msgpipeline.processor")
@EnableConfigurationProperties(AppConfig.class)
public class ProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcessorApplication.class, args);
    }
}
