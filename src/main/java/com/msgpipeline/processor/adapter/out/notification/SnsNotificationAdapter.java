package com.msgpipeline.processor.adapter.out.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msgpipeline.processor.config.AppConfig;
import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * =========================================================================
 * CAPA: Infraestructura — Adaptador de Salida (Output Adapter)
 * ARQUITECTURA: Hexagonal (Ports & Adapters)
 * PERFIL: 'aws' (activo en AWS Lambda)
 * SESIÓN: 04 — NUEVO
 * =========================================================================
 *
 * PATRÓN: Adapter (GoF — Gang of Four)
 *   Adapta el contrato del dominio (NotificationPort) al API del AWS SDK v2 (SnsClient).
 *   El dominio nunca importa SnsClient directamente.
 *
 * PATRÓN: Observer (implementación del "Observable")
 *   Este adaptador es el "sujeto observable" que notifica a los "observers" (suscriptores SNS).
 *   Cuando se llama notificarProcesamiento(), SNS distribuye el evento a todos:
 *     - Email suscripto al tópico msg-pipeline-email-notifications
 *     - SMS (si se agrega suscripción)
 *     - Otro Lambda (si se agrega suscripción)
 *   El procesador NO sabe quiénes reciben la notificación — OCP Principle.
 *
 * PATRÓN: Dependency Inversion (DIP — SOLID)
 *   ProcessMessageUseCase → NotificationPort ← SnsNotificationAdapter
 *   El Use Case depende de la abstracción; este adaptador implementa los detalles.
 *
 * @Profile("aws"):
 *   SOLO se registra en el perfil 'aws'. En 'local', Spring usa InMemoryNotificationAdapter.
 *
 * VARIABLES DE ENTORNO REQUERIDAS EN LAMBDA:
 *   SNS_TOPIC_ARN = arn:aws:sns:us-east-1:{accountId}:msg-pipeline-email-notifications
 *
 * ROL IAM REQUERIDO:
 *   msg-pipeline-lambda-role debe tener la política AmazonSNSFullAccess.
 * =========================================================================
 */
@Slf4j
@Component
@Profile("aws")
@RequiredArgsConstructor
public class SnsNotificationAdapter implements NotificationPort {

    /** Cliente SNS inyectado desde DynamoConfig (bean @Profile("aws")) */
    private final SnsClient snsClient;

    /** Configuración centralizada — AppConfig.aws.snsTopicArn */
    private final AppConfig appConfig;

    /** Jackson para serializar el mensaje a JSON para el cuerpo del SNS */
    private final ObjectMapper objectMapper;

    /**
     * Publica una notificación en el tópico SNS cuando un mensaje es procesado.
     *
     * IMPORTANTE — Manejo de errores no bloqueante:
     *   Si SNS falla, el mensaje ya fue persistido en DynamoDB con status=COMPLETED.
     *   NO relanzamos la excepción para no hacer que SQS reintente el mensaje.
     *   La notificación es un "best effort" — si falla, se loggea y se continúa.
     *
     * FORMATO del mensaje SNS:
     *   Subject: "Mensaje Procesado — msg-pipeline [TIPO]"
     *   Body: JSON completo del mensaje procesado (para que los suscriptores tengan contexto)
     *
     * @param message Mensaje procesado exitosamente
     */
    @Override
    public void notificarProcesamiento(Message message) {
        String topicArn = appConfig.getAws().getSnsTopicArn();

        // ── Verificar que el ARN está configurado ─────────────────────────
        if (topicArn == null || topicArn.isBlank()) {
            log.info("SNS_TOPIC_ARN no configurado — omitiendo notificación [messageId={}]",
                    message.getMessageId());
            return;
        }

        log.info("Publicando notificación SNS [messageId={}] [topicArn={}]",
                message.getMessageId(), topicArn);

        try {
            // ── Serializar el mensaje como cuerpo de la notificación ──────
            //
            // El cuerpo del mensaje SNS contiene el JSON completo del mensaje.
            // Los suscriptores (email, Lambda, etc.) reciben este contexto completo.
            //
            String messageBody = objectMapper.writeValueAsString(message);

            // ── Construir y publicar en SNS ───────────────────────────────
            //
            // subject: línea de asunto del email (para suscriptores de tipo Email)
            // message: cuerpo de la notificación
            // topicArn: tópico SNS que distribuye a todos los suscriptores
            //
            // PATRÓN Observer: snsClient.publish() es el "notifyObservers()"
            // SNS actúa como el "EventBus" desacoplado
            //
            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject("Mensaje Procesado — msg-pipeline [" + message.getMessageType() + "]")
                    .message(messageBody)
                    .build();

            PublishResponse response = snsClient.publish(request);

            log.info("Notificación SNS publicada exitosamente [messageId={}] [snsMessageId={}]",
                    message.getMessageId(), response.messageId());

        } catch (JsonProcessingException e) {
            // Error de serialización — no relanzar (el procesamiento ya fue completado)
            log.error("Error serializando mensaje para SNS [messageId={}]: {}",
                    message.getMessageId(), e.getMessage(), e);
        } catch (Exception e) {
            // Error de SDK SNS — no relanzar (la notificación es best-effort)
            log.error("Error publicando en SNS [messageId={}]: {}",
                    message.getMessageId(), e.getMessage(), e);
        }
    }
}
