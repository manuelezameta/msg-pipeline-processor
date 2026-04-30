package com.msgpipeline.processor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * =========================================================================
 * CLASE: OpenApiConfig — Configuración Swagger/OpenAPI
 * CAPA: Infraestructura — Configuración
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Genera documentación interactiva con SpringDoc + Swagger UI.
 * Solo activo en perfil 'local' — en Lambda no hay servidor HTTP.
 *
 * ACCESO LOCAL:
 *   http://localhost:8083/swagger-ui.html    → UI interactiva
 *   http://localhost:8083/v3/api-docs        → JSON de la spec OpenAPI
 *   http://localhost:8083/v3/api-docs.yaml   → YAML de la spec OpenAPI
 *
 * OpenAPI 3.0 describe:
 *   - Endpoints disponibles (POST /messages-s5)
 *   - Modelos de request/response
 *   - Ejemplos de uso
 *   - Códigos de respuesta HTTP posibles
 * =========================================================================
 */
@Configuration
@Profile("local")
public class OpenApiConfig {

    @Value("${server.port:8083}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("msg-pipeline-processor-sesion-05 API")
                        .version("1.0.0")
                        .description("""
                                ## API del Processor Lambda — Sesión 05
                                
                                **Arquitectura:** Hexagonal (Ports & Adapters) + Clean Architecture
                                
                                **Flujo de datos:**
                                ```
                                Postman → POST /messages-s5
                                  → API Gateway (msg-pipeline-api)
                                  → Lambda (msg-pipeline-processor-sesion-05)
                                  → DynamoDB (status=PENDING) + EventBridge
                                  → Audit Lambda (msg-pipeline-audit-sesion-05)
                                  → DynamoDB (status=COMPLETED) + SNS Email
                                ```
                                
                                **Cambios en Sesión 05:**
                                - Trigger: SQS → API Gateway directo
                                - Nuevo: EventBridge para eventos de dominio
                                - Status inicial: PENDING (asíncrono con Audit Lambda)
                                """)
                        .contact(new Contact()
                                .name("Anku Academy")
                                .email("anku@academy.com"))
                        .license(new License()
                                .name("Apache 2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Servidor local de desarrollo"),
                        new Server()
                                .url("https://REPLACE.execute-api.us-east-1.amazonaws.com/prod")
                                .description("AWS API Gateway (producción)")
                ));
    }
}
