package com.msgpipeline.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracion del Processor Lambda.
 * VARIABLES DE ENTORNO:
 *   DYNAMODB_TABLE_NAME, SNS_TOPIC_ARN, EVENT_BUS_NAME
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private Aws aws = new Aws();
    private Processor processor = new Processor();

    @Data
    public static class Aws {
        private String region = "us-east-1";
        private String dynamodbTable = "msg-pipeline-messages";
        private String snsTopicArn = "";
        private String eventBusName = "msg-pipeline-events-sesion-07";
    }

    @Data
    public static class Processor {
        private int ttlDays = 30;
    }
}
