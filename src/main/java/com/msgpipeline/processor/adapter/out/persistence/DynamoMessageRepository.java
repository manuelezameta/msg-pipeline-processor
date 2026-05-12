package com.msgpipeline.processor.adapter.out.persistence;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * =========================================================================
 * CLASE: DynamoMessageRepository -- Adaptador de Salida (DynamoDB PutItem)
 * CAPA: Infraestructura -- Adaptador de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * @Profile("aws"): Solo activo en Lambda.
 *
 * NOTA sobre 'status':
 *   PutItem acepta atributos reservados directamente.
 *   Solo UpdateItem (en el Audit) requiere el alias #st.
 * =========================================================================
 */
@Slf4j
@Repository
@Profile("aws")
@RequiredArgsConstructor
public class DynamoMessageRepository implements MessageRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    @Override
    public Message save(Message message) {
        log.info("DynamoDB PutItem [messageId={}] [tabla={}] [status={}]",
                message.getMessageId(), tableName, message.getStatus());

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("messageId", AttributeValue.fromS(message.getMessageId()));
        putIfNotNull(item, "messageType",    message.getMessageType());
        putIfNotNull(item, "channel",        message.getChannel());
        putIfNotNull(item, "recipientEmail", message.getRecipientEmail());
        putIfNotNull(item, "content",        message.getContent());
        item.put("status",  AttributeValue.fromS(message.getStatus()));
        putIfNotNull(item, "processedAt",    message.getProcessedAt());
        putIfNotNull(item, "requestId",      message.getRequestId());
        putIfNotNull(item, "userEmail",      message.getUserEmail());
        if (message.getTtl() != null) {
            item.put("ttl", AttributeValue.fromN(message.getTtl().toString()));
        }

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        log.info("PutItem exitoso [messageId={}]", message.getMessageId());
        return message;
    }

    private void putIfNotNull(Map<String, AttributeValue> item, String key, String value) {
        if (value != null && !value.isBlank()) {
            item.put(key, AttributeValue.fromS(value));
        }
    }
}
