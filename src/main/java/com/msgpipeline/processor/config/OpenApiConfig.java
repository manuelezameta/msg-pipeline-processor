package com.msgpipeline.processor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * =========================================================================
 * CAPA: Infraestructura — Configuración OpenAPI / Swagger UI
 * PERFIL: 'local' (Swagger solo disponible en desarrollo local)
 * =========================================================================
 *
 * SpringDoc genera documentación OpenAPI 3.0 automáticamente a partir de:
 *   1. Las anotaciones @RestController y @RequestMapping
 *   2. Las anotaciones @Operation, @ApiResponse, @Schema en el código
 *   3. La configuración de este bean OpenAPI
 *
 * URL DE ACCESO (perfil local):
 *   Swagger UI:  http://localhost:8082/swagger-ui.html
 *   JSON OpenAPI: http://localhost:8082/v3/api-docs
 *
 * ¿POR QUÉ NO EN LAMBDA?
 *   En Lambda no hay servidor HTTP activo (WebApplicationType.NONE).
 *   Swagger UI requiere un servidor HTTP para servir la interfaz web.
 *   Por esto, este bean solo se crea en el perfil 'local'.
 * =========================================================================
 */
@Configuration
@Profile("local")
public class OpenApiConfig {

    /**
     * Configura los metadatos generales de la API para Swagger UI.
     *
     * @return Objeto OpenAPI con la descripción completa de la API
     */
    @Bean
    public OpenAPI processorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("msg-pipeline-processor API")
                        .description(
                            "**Sesión 03 — DynamoDB + SQS + Lambda Processor**\n\n" +
                            "Este microservicio procesa mensajes de la cola SQS y los persiste en DynamoDB.\n\n" +
                            "**Flujo de producción:** SQS → Lambda Processor → DynamoDB\n\n" +
                            "**Flujo local (testing):** HTTP POST → Controller → Use Case → InMemoryRepository\n\n" +
                            "⚠️ Los endpoints aquí solo existen en el perfil `local`. " +
                            "En AWS Lambda, el procesamiento lo inicia el Event Source Mapping de SQS."
                        )
                        .version("1.0.0-sesion-03")
                        .contact(new Contact()
                                .name("Anku Academy")
                                .url("https://ankuacademy.com"))
                        .license(new License()
                                .name("Uso educativo — Anku Academy 2026C2"))
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8082")
                                .description("Servidor local de desarrollo")
                ));
    }
}
