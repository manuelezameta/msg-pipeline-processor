package com.msgpipeline.processor.adapter.out.notification;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Simulador de SNS para desarrollo local. */
@Slf4j
@Component
@Profile("local")
public class InMemoryNotificationAdapter implements NotificationPort {

    @Override
    public void notificar(Message message) {
        log.info("[LOCAL] Simulando publicacion SNS:");
        log.info("[LOCAL]   topico: msg-pipeline-email-notifications-sesion-06 (SIMULADO)");
        log.info("[LOCAL]   messageId:    {}", message.getMessageId());
        log.info("[LOCAL]   tipo:         {}", message.getMessageType());
        log.info("[LOCAL]   destinatario: {}", message.getRecipientEmail());
        log.info("[LOCAL]   status:       {}", message.getStatus());
    }
}
