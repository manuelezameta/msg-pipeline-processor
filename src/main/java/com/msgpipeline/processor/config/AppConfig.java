package com.msgpipeline.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * =========================================================================
 * CLASE: AppConfig — Configuración Centralizada
 * CAPA: Infraestructura — Configuración
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * PATRÓN: Configuration Object
 *   Agrupa todas las propiedades de configuración de la app en un objeto.
 *   Más mantenible que múltiples @Value dispersos.
 *
 * VARIABLES DE ENTORNO REQUERIDAS EN LAMBDA (Sesión 05):
 *   DYNAMODB_TABLE_NAME = msg-pipeline-messages
 *   EVENT_BUS_NAME      = msg-pipeline-events-sesion-05
 *   AWS_REGION          = us-east-1 (Lambda la inyecta automáticamente)
 * =========================================================================
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private Aws aws = new Aws();
    private Processor processor = new Processor();

    @Data
    public static class Aws {
        /** Región AWS. Inyectada automáticamente por Lambda runtime. */
        private String region = "us-east-1";

        /** Tabla DynamoDB para mensajes */
        private String dynamodbTable = "msg-pipeline-messages";

        /** Bus de eventos EventBridge (NUEVO Sesión 05) */
        private String eventBusName = "msg-pipeline-events-sesion-05";
    }

    @Data
    public static class Processor {
        private int maxRetries = 3;
        private int ttlDays = 30;
    }
}
