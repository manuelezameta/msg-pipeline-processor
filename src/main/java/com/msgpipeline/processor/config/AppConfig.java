package com.msgpipeline.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracion centralizada del Processor Lambda Sesion 06.
 *
 * VARIABLES DE ENTORNO EN LAMBDA:
 *   DYNAMODB_TABLE_NAME = msg-pipeline-messages
 *   SNS_TOPIC_ARN       = arn:aws:sns:us-east-1:ACCOUNT:msg-pipeline-email-notifications-sesion-06
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
    }

    @Data
    public static class Processor {
        private int ttlDays = 30;
    }
}
