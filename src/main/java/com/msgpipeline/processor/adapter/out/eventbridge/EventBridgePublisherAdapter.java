package com.msgpipeline.processor.adapter.out.eventbridge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.EventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * =========================================================================
 * CLASE: EventBridgePublisherAdapter -- Adaptador de Salida (EventBridge)
 * CAPA: Infraestructura -- Adaptador de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Publica eventos MessageProcessed en EventBridge para disparar Audit Lambda.
 * @Profile("aws"): Solo activo en Lambda.
 *
 * CONFIGURACION EVENTBRIDGE:
 *   Bus:        msg-pipeline-events-sesion-07
 *   Source:     com.msgpipeline.processor (filtrado por Rule)
 *   DetailType: MessageProcessed          (filtrado por Rule)
 *   Rule:       msg-pipeline-audit-rule-sesion-07
 *   Target:     Lambda msg-pipeline-audit-sesion-07
 * =========================================================================
 */
@Slf4j
@Component
@Profile("aws")
public class EventBridgePublisherAdapter implements EventPublisherPort {

    private static final EventBridgeClient eventBridgeClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        eventBridgeClient = EventBridgeClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    @Value("${app.aws.event-bus-name:msg-pipeline-events-sesion-07}")
    private String eventBusName;

    @Override
    public void publicarMensajeProcesado(Message message) {
        log.info("EventBridge PutEvents [messageId={}] [bus={}] [source=com.msgpipeline.processor]",
                message.getMessageId(), eventBusName);

        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("messageId",     message.getMessageId());
            detail.put("messageType",   message.getMessageType());
            detail.put("channel",       message.getChannel());
            detail.put("recipientEmail",message.getRecipientEmail());
            detail.put("status",        message.getStatus());
            detail.put("processedAt",   message.getProcessedAt());
            detail.put("userEmail",     message.getUserEmail());

            PutEventsResponse response = eventBridgeClient.putEvents(
                    PutEventsRequest.builder()
                            .entries(PutEventsRequestEntry.builder()
                                    .eventBusName(eventBusName)
                                    .source("com.msgpipeline.processor")
                                    .detailType("MessageProcessed")
                                    .detail(objectMapper.writeValueAsString(detail))
                                    .build())
                            .build());

            log.info("EventBridge PutEvents exitoso [messageId={}] [failedCount={}]",
                    message.getMessageId(), response.failedEntryCount());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error EventBridge PutEvents: " + e.getMessage(), e);
        }
    }
}
