package com.msgpipeline.processor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Configuracion DynamoDB para perfil AWS */
@Slf4j
@Configuration
@Profile("aws")
@RequiredArgsConstructor
public class DynamoConfig {

    private final AppConfig appConfig;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(appConfig.getAws().getRegion()))
                .build();
    }

    @Bean
    public String tableName() {
        return appConfig.getAws().getDynamodbTable();
    }
}
