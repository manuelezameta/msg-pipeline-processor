package com.msgpipeline.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * =========================================================================
 * CAPA: Infraestructura — Configuración
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * PATRÓN: Configuration Object (centraliza propiedades de la app)
 *
 * @ConfigurationProperties(prefix = "app"):
 *   Lee automáticamente todas las propiedades que comienzan con "app."
 *   desde application.yml y las mapea a los campos de esta clase.
 *
 * EJEMPLO — application.yml:
 *   app:
 *     aws:
 *       region: us-east-1         → aws.region = "us-east-1"
 *       dynamodb-table: msg-...   → aws.dynamodbTable = "msg-..."
 *
 * BUENAS PRÁCTICAS de Variables de Entorno:
 *   1. NUNCA hardcodear valores de configuración en el código fuente
 *   2. Los valores por defecto en application.yml son para desarrollo
 *   3. En Lambda, siempre sobrescribir con Variables de Entorno reales
 *   4. No usar .env files en producción — usar Lambda Environment Variables
 *
 * VENTAJA sobre @Value:
 *   @ConfigurationProperties agrupa propiedades relacionadas en un objeto.
 *   Es más mantenible que múltiples @Value dispersos por el código.
 * =========================================================================
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    /**
     * Agrupa la configuración específica de AWS.
     * Corresponde al bloque 'app.aws:' en application.yml.
     */
    private Aws aws = new Aws();

    /**
     * Configuración del procesador de mensajes.
     */
    private Processor processor = new Processor();

    /**
     * Clase interna para la configuración de AWS.
     *
     * VARIABLE DE ENTORNO REQUERIDA EN LAMBDA:
     *   DYNAMODB_TABLE_NAME = msg-pipeline-messages
     *
     * AWS_REGION es inyectada automáticamente por el runtime de Lambda.
     * No es necesario configurarla manualmente en Lambda.
     */
    @Data
    public static class Aws {
        /**
         * Región AWS.
         * Default: 'us-east-1' (región del curso)
         * En Lambda: se obtiene de la variable de entorno AWS_REGION
         */
        private String region = "us-east-1";

        /**
         * Nombre de la tabla DynamoDB donde se persisten los mensajes.
         * Default: 'msg-pipeline-messages' (nombre del curso)
         * En Lambda: configurar como variable de entorno DYNAMODB_TABLE_NAME
         */
        private String dynamodbTable = "msg-pipeline-messages";
    }

    /**
     * Configuración del comportamiento del procesador.
     */
    @Data
    public static class Processor {
        /** Máximo de reintentos antes de enviar a DLQ */
        private int maxRetries = 3;

        /** Días de retención del mensaje en DynamoDB (TTL) */
        private int ttlDays = 30;
    }
}
