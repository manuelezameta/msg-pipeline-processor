package com.msgpipeline.processor.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * =========================================================================
 * CLASE: ProcessorApplication — Punto de Entrada Spring Boot
 * CAPA: Infraestructura — Configuración
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Esta clase sirve SOLO para el perfil 'local' (desarrollo).
 * En Lambda, el punto de entrada es ProcessorHandler.java.
 *
 * @SpringBootApplication incluye:
 *   @Configuration     → Esta clase puede declarar beans (@Bean)
 *   @ComponentScan     → Escanea todos los componentes Spring del package
 *   @EnableAutoConfiguration → Configura Spring automáticamente según deps
 *
 * USO LOCAL:
 *   ./gradlew bootRun --args='--spring.profiles.active=local'
 *   → Levanta servidor en http://localhost:8083
 *   → Swagger UI: http://localhost:8083/swagger-ui.html
 */
@SpringBootApplication(scanBasePackages = "com.msgpipeline.processor")
@EnableConfigurationProperties(AppConfig.class)
public class ProcessorApplication {

    /**
     * Punto de entrada para ejecución LOCAL.
     * En Lambda, AWS Runtime llama directamente a ProcessorHandler.
     */
    public static void main(String[] args) {
        SpringApplication.run(ProcessorApplication.class, args);
    }
}
