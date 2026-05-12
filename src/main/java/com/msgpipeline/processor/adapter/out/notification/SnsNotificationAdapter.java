package com.msgpipeline.processor.adapter.out.notification;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * =========================================================================
 * CLASE: SnsNotificationAdapter -- Adaptador de Salida (AWS SNS)
 * CAPA: Infraestructura -- Adaptador de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * @Profile("aws"): Solo activo en Lambda.
 * PATRON OBSERVER: publica sin conocer los suscriptores.
 * SnsClient: thread-safe, inicializado UNA VEZ en el cold start.
 * =========================================================================
 */
@Slf4j
@Component
@Profile("aws")
public class SnsNotificationAdapter implements NotificationPort {

    private static final SnsClient snsClient;

    static {
        snsClient = SnsClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    @Value("${app.aws.sns-topic-arn:}")
    private String snsTopicArn;

    @Override
    public void notificarRecepcion(Message message) {
        if (snsTopicArn == null || snsTopicArn.isBlank()) {
            log.warn("SNS_TOPIC_ARN no configurado [messageId={}]", message.getMessageId());
            return;
        }

        String cuerpo = "Mensaje Recibido -- msg-pipeline Sesion 07\n\n"
                + "ID: "          + message.getMessageId()    + "\n"
                + "Tipo: "        + message.getMessageType()   + "\n"
                + "Canal: "       + message.getChannel()       + "\n"
                + "Destinatario: "+ message.getRecipientEmail()+ "\n"
                + "Status: "      + message.getStatus()        + "\n"
                + "Procesado: "   + message.getProcessedAt()   + "\n"
                + "Usuario: "     + message.getUserEmail()     + "\n\n"
                + "El Lambda Audit actualizara el estado a COMPLETED tras 15 segundos.\n"
                + "Anku Academy -- Especializacion Spring Boot + AWS Serverless";

        PublishResponse response = snsClient.publish(PublishRequest.builder()
                .topicArn(snsTopicArn)
                .subject("Mensaje Recibido -- msg-pipeline Sesion 07")
                .message(cuerpo)
                .build());

        log.info("SNS Publish exitoso [messageId={}] [snsMessageId={}]",
                message.getMessageId(), response.messageId());
    }
}
