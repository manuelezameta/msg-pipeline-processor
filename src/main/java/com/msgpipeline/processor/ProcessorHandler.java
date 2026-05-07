package com.msgpipeline.processor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
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
 * CLASE: ProcessorHandler — Lambda Entry Point (Step Functions Task)
 * CAPA: Infraestructura — Adaptador de Entrada (Input Adapter)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * DIFERENCIA CLAVE con Sesión 05:
 *   Sesión 05: Invocado por API Gateway → input: APIGatewayProxyRequestEvent
 *   Sesión 06: Invocado por Step Functions → input: Map<String, Object>
 *
 * FLUJO COMPLETO SESIÓN 06 — POSICIÓN DEL PROCESSOR:
 *   ┌────────────────────────────────────────┐
 *   │  Step Functions                        │
 *   │  msg-pipeline-workflow-sesion-06       │
 *   │                                        │
 *   │  ValidarMensaje → EvaluarValidacion    │
 *   │  → InvocarLambda ─────────────────────┼─▶ ProcessorHandler (esta clase)
 *   │                                        │
 *   └────────────────────────────────────────┘
 *                                             │
 *                              ┌──────────────┼──────────────────┐
 *                              ▼                                  ▼
 *                   ┌─────────────────┐             ┌────────────────────────┐
 *                   │    DynamoDB     │             │  SNS                   │
 *                   │ PutItem         │             │  msg-pipeline-email-   │
 *                   │ status=PENDING  │             │  notifications-s06     │
 *                   └─────────────────┘             └────────────────────────┘
 *
 * INPUT DE STEP FUNCTIONS:
 *   Step Functions pasa el "input" completo de la ejecución (JSON que
 *   construyó el Orchestrator) al estado Task "InvocarLambda".
 *   Este JSON contiene: messageId, messageType, channel, recipientEmail, etc.
 *
 * OUTPUT PARA STEP FUNCTIONS:
 *   El valor de retorno del Lambda se integra en el flujo de Step Functions.
 *   Si el Lambda lanza excepción → Step Functions marca la tarea como FAILED.
 *   Si retorna normalmente → Step Functions marca la tarea como SUCCEEDED.
 *
 * HANDLER A CONFIGURAR EN LAMBDA CONSOLE:
 *   com.msgpipeline.processor.ProcessorHandler::handleRequest
 *
 * VARIABLES DE ENTORNO REQUERIDAS:
 *   DYNAMODB_TABLE_NAME = msg-pipeline-messages
 *   SNS_TOPIC_ARN       = arn:aws:sns:us-east-1:ACCOUNT:msg-pipeline-email-notifications-sesion-06
 * =========================================================================
 */
@Slf4j
public class ProcessorHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    // ── Bloque static — Cold Start ────────────────────────────────────────────
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ProcessMessagePort processMessagePort;

    static {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  ProcessorHandler — Cold Start (Sesion 06)                  ║");
        log.info("║  Trigger: Step Functions Task → DynamoDB + SNS              ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");

        // Perfil 'aws': usa DynamoMessageRepository + SnsNotificationAdapter
        // Perfil 'local': usa InMemoryMessageRepository + InMemoryNotificationAdapter
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ProcessorApplication.class)
                .web(WebApplicationType.NONE)  // El Processor no necesita servidor HTTP
                .profiles("aws")
                .run();

        processMessagePort = context.getBean(ProcessMessagePort.class);

        log.info("Contexto Spring inicializado.");
        log.info("Tabla DynamoDB: {}", context.getEnvironment().getProperty("app.aws.dynamodb-table"));
        log.info("SNS Topic ARN: {}", context.getEnvironment().getProperty("app.aws.sns-topic-arn"));
    }

    /**
     * Constructor público sin argumentos — OBLIGATORIO para Lambda.
     */
    public ProcessorHandler() {
        // Requerido por el runtime de Lambda
    }

    /**
     * handleRequest — Invocado por Step Functions como tarea (Task state).
     *
     * INPUT RECIBIDO DE STEP FUNCTIONS:
     * {
     *   "messageId": "uuid...",
     *   "messageType": "EMAIL",
     *   "channel": "EMAIL",
     *   "recipientEmail": "test@test.com",
     *   "content": "Mensaje de prueba",
     *   "requestId": "api-gw-request-id",
     *   "submittedAt": "2026-01-01T...",
     *   "source": "msg-pipeline.orchestrator"
     * }
     *
     * OUTPUT RETORNADO A STEP FUNCTIONS:
     * {
     *   "messageId": "uuid...",
     *   "status": "PENDING",
     *   "processedAt": "2026-01-01T...",
     *   "success": true
     * }
     *
     * Si el Lambda lanza excepción, Step Functions marca la ejecución como FAILED.
     * Si retorna Map con datos → Step Functions puede usar estos en estados siguientes.
     *
     * @param event   Input de la State Machine (todos los campos del mensaje)
     * @param context Contexto Lambda (requestId, timeout, logs)
     * @return        Resultado del procesamiento para Step Functions
     */
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        log.info("Evento Step Functions recibido [requestId={}] [tiempoRestante={}ms]",
                context.getAwsRequestId(),
                context.getRemainingTimeInMillis());

        try {
            // ── Extraer campos del input de Step Functions ────────────────────
            String messageId     = (String) event.get("messageId");
            String messageType   = (String) event.getOrDefault("messageType", "UNKNOWN");
            String channel       = (String) event.getOrDefault("channel", "EMAIL");
            String recipientEmail = (String) event.getOrDefault("recipientEmail", "");
            String content       = (String) event.getOrDefault("content", "");
            String requestId     = (String) event.getOrDefault("requestId", context.getAwsRequestId());

            log.info("Procesando mensaje [messageId={}] [tipo={}] [canal={}] [destinatario={}]",
                    messageId, messageType, channel, recipientEmail);

            if (messageId == null || messageId.isBlank()) {
                log.error("messageId es nulo o vacio — el evento no tiene el campo requerido");
                throw new IllegalArgumentException("messageId es obligatorio en el input de Step Functions");
            }

            // ── Construir entidad del dominio ─────────────────────────────────
            Message domainMessage = Message.builder()
                    .messageId(messageId)
                    .messageType(messageType)
                    .channel(channel)
                    .recipientEmail(recipientEmail)
                    .content(content)
                    .requestId(requestId)
                    .build();

            // ── Ejecutar caso de uso ──────────────────────────────────────────
            //
            // El caso de uso:
            //   → Asigna status=PENDING
            //   → Calcula TTL (30 días)
            //   → Persiste en DynamoDB (PutItem)
            //   → Publica en SNS (Publish)
            //   → Retorna Message guardado
            //
            Message saved = processMessagePort.processMessage(domainMessage, context.getAwsRequestId());

            log.info("Procesamiento completado [messageId={}] [status={}]",
                    saved.getMessageId(), saved.getStatus());

            // ── Retornar resultado a Step Functions ───────────────────────────
            return Map.of(
                    "messageId",   saved.getMessageId(),
                    "status",      saved.getStatus(),
                    "processedAt", saved.getProcessedAt() != null ? saved.getProcessedAt() : "",
                    "success",     true
            );

        } catch (Exception e) {
            log.error("Error en ProcessorHandler [requestId={}]: {}",
                    context.getAwsRequestId(), e.getMessage(), e);
            // Relanzar la excepción para que Step Functions marque la tarea como FAILED
            // y active el mecanismo de Retry/Catch configurado en la State Machine
            throw new RuntimeException("Error en el procesamiento del mensaje: " + e.getMessage(), e);
        }
    }
}
