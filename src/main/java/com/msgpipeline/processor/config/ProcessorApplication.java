package com.msgpipeline.processor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;

/**
 * =========================================================================
 * CLASE: ProcessorApplication
 * PROPÓSITO: Punto de entrada para ejecución LOCAL (NO para Lambda)
 * =========================================================================
 *
 * ¿POR QUÉ EXISTE ESTA CLASE SI NO SE USA EN LAMBDA?
 *
 *   En desarrollo LOCAL necesitamos arrancar Spring Boot como una app
 *   web normal para poder usar Swagger UI y probar el procesador sin SQS.
 *
 *   En AWS LAMBDA, el punto de entrada es SqsHandler::handleRequest
 *   (configurado en la consola Lambda). Lambda no llama a ningún main().
 *
 * DIFERENCIA FUNDAMENTAL:
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │  MODO LOCAL (perfil 'local')                                    │
 *   │  main() → SpringApplication.run() → Tomcat activo → Swagger UI │
 *   ├─────────────────────────────────────────────────────────────────┤
 *   │  MODO LAMBDA (perfil 'aws')                                     │
 *   │  Lambda → new SqsHandler() → static{} → Spring context (NONE)  │
 *   │  → handleRequest() → Use Case → DynamoDB                       │
 *   │  (NO se llama a main() en ningún momento)                       │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * CÓMO EJECUTAR LOCALMENTE:
 *   ./gradlew bootRun --args='--spring.profiles.active=local'
 *   → Arranca en http://localhost:8082
 *   → Swagger UI en http://localhost:8082/swagger-ui.html
 *
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 * @EnableConfigurationProperties: activa el mapeo automático de application.yml → AppConfig
 * =========================================================================
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.msgpipeline.processor")
@EnableConfigurationProperties(AppConfig.class)
public class ProcessorApplication {

    public static void main(String[] args) {
        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║  msg-pipeline-processor — Modo LOCAL (Sesión 04)         ║");
        log.info("║  Novedad: SNS notification port (Patrón Observer)         ║");
        log.info("║  Swagger UI: http://localhost:8082/swagger-ui.html        ║");
        log.info("╚═══════════════════════════════════════════════════════════╝");

        SpringApplication.run(ProcessorApplication.class, args);
    }
}
