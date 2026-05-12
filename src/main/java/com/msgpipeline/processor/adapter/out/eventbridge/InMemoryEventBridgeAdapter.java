package com.msgpipeline.processor.adapter.out.eventbridge;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.EventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
public class InMemoryEventBridgeAdapter implements EventPublisherPort {
    @Override
    public void publicarMensajeProcesado(Message message) {
        log.info("[LOCAL] EventBridge PutEvents simulado [messageId={}]" +
                " [source=com.msgpipeline.processor] [detailType=MessageProcessed]",
                message.getMessageId());
    }
}
