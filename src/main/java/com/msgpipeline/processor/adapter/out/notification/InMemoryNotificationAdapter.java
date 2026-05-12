package com.msgpipeline.processor.adapter.out.notification;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
public class InMemoryNotificationAdapter implements NotificationPort {
    @Override
    public void notificarRecepcion(Message message) {
        log.info("[LOCAL] SNS Publish simulado [messageId={}] [tipo={}]",
                message.getMessageId(), message.getMessageType());
    }
}
