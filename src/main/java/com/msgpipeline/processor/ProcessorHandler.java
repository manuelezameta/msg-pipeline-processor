package com.msgpipeline.processor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msgpipeline.processor.application.port.in.ProcessMessagePort;
import com.msgpipeline.processor.config.ProcessorApplication;
import com.msgpipeline.processor.domain.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

/**
 * =========================================================================
 * CLASE: ProcessorHandler -- Lambda Entry Point (SQS Event Source Mapping)
 * CAPA: Infraestructura -- Adaptador de Entrada (Input Adapter)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * DIFERENCIA CLAVE CON SESION 06:
 *   Sesion 06: Invocado por Step Functions Task -> input: Map<String,Object>
 *   Sesion 07: Disparado por SQS ESM -> input: SQSEvent (mensajes de la cola)
 *
 * FLUJO SESION 07:
 *   Step Functions (EnviarAColaPrincipal) --> SendMessage --> SQS
 *   SQS msg-pipeline-queue-sesion-07
 *   SQS Event Source Mapping (ESM, batchSize=1)
 *   --> ProcessorHandler::handleRequest (esta clase)
 *   --> Por cada mensaje SQS:
 *       DynamoDB PutItem (status=PENDING)
 *       SNS Publish (notificacion)
 *       EventBridge PutEvents (MessageProcessed --> Audit Lambda)
 *
 * SQS DLQ:
 *   Si el Lambda falla 3 veces --> el mensaje va a msg-pipeline-dlq-sesion-07
 *   (configurado con Redrive Policy, maxReceiveCount=3)
 *
 * HANDLER: com.msgpipeline.processor.ProcessorHandler::handleRequest
 * ENV:     DYNAMODB_TABLE_NAME, SNS_TOPIC_ARN, EVENT_BUS_NAME
 * TIMEOUT: 30s | MEMORIA: 512 MB
 *
 * IMPORTANTE -- WebApplicationType.SERVLET:
 *   Requerido en Spring Boot 3.5. NONE causa ClassCastException.
 * =========================================================================
 */
@Slf4j
public class ProcessorHandler implements RequestHandler<SQSEvent, String> {

    // -- Bloque static -- Cold Start -----------------------------------------
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ProcessMessagePort processMessagePort;

    static {
        log.info("ProcessorHandler -- Cold Start (Sesion 07)");
        log.info("Trigger: SQS Event Source Mapping");
        log.info("Acciones: DynamoDB PutItem + SNS Publish + EventBridge PutEvents");

        // WebApplicationType.SERVLET: OBLIGATORIO en Spring Boot 3.5
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ProcessorApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("aws")
                .run();

        processMessagePort = context.getBean(ProcessMessagePort.class);
        log.info("Tabla DynamoDB: {}", context.getEnvironment().getProperty("app.aws.dynamodb-table"));
        log.info("SNS Topic ARN: {}", context.getEnvironment().getProperty("app.aws.sns-topic-arn"));
        log.info("EventBridge Bus: {}", context.getEnvironment().getProperty("app.aws.event-bus-name"));
    }

    /** Constructor publico sin argumentos -- OBLIGATORIO para AWS Lambda */
    public ProcessorHandler() { }

    /**
     * handleRequest -- Invocado para cada batch de mensajes SQS.
     *
     * BODY DEL MENSAJE SQS:
     *   Step Functions serializo el estado con States.JsonToString($).
     *   El body contiene: messageId, messageType, channel, recipientEmail, etc.
     *
     * Si el Lambda lanza excepcion --> SQS reintenta hasta maxReceiveCount=3.
     * Tras 3 fallos --> el mensaje va a la DLQ.
     */
    @Override
    @SuppressWarnings("unchecked")
    public String handleRequest(SQSEvent event, Context context) {
        log.info("SQSEvent [requestId={}] [mensajes={}]",
                context.getAwsRequestId(), event.getRecords().size());

        int procesados = 0;

        for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
            try {
                log.info("Procesando SQS message [sqsMessageId={}]", sqsMessage.getMessageId());

                // Deserializar el body del mensaje SQS
                Map<String, Object> bodyMap = objectMapper.readValue(
                        sqsMessage.getBody(), Map.class);

                String messageId      = (String) bodyMap.getOrDefault("messageId",     "");
                String messageType    = (String) bodyMap.getOrDefault("messageType",   "");
                String channel        = (String) bodyMap.getOrDefault("channel",       "");
                String recipientEmail = (String) bodyMap.getOrDefault("recipientEmail","");
                String content        = (String) bodyMap.getOrDefault("content",       "");
                String userEmail      = (String) bodyMap.getOrDefault("userEmail",     "");
                String requestId      = (String) bodyMap.getOrDefault("requestId",
                                                context.getAwsRequestId());

                if (messageId == null || messageId.isBlank()) {
                    log.error("messageId vacio en el mensaje SQS -- saltando");
                    continue;
                }

                log.info("Datos [messageId={}] [tipo={}] [canal={}]",
                        messageId, messageType, channel);

                Message domainMessage = Message.builder()
                        .messageId(messageId)
                        .messageType(messageType)
                        .channel(channel)
                        .recipientEmail(recipientEmail)
                        .content(content)
                        .userEmail(userEmail)
                        .build();

                // Ejecutar caso de uso: DynamoDB + SNS + EventBridge
                Message saved = processMessagePort.processMessage(domainMessage, requestId);

                log.info("Mensaje procesado [messageId={}] [status={}]",
                        saved.getMessageId(), saved.getStatus());
                procesados++;

            } catch (Exception e) {
                log.error("Error procesando SQS message [sqsMessageId={}]: {}",
                        sqsMessage.getMessageId(), e.getMessage(), e);
                // Relanzar para que SQS reintente (DLQ tras maxReceiveCount=3)
                throw new RuntimeException("Error en ProcessorHandler: " + e.getMessage(), e);
            }
        }

        String resultado = "Procesados " + procesados + " de " + event.getRecords().size() + " mensajes";
        log.info(resultado);
        return resultado;
    }
}
