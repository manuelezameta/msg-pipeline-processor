package com.msgpipeline.processor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msgpipeline.processor.adapter.in.web.dto.ProcessMessageRequest;
import com.msgpipeline.processor.adapter.in.web.dto.ProcessMessageResponse;
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
 * CLASE: ProcessorHandler — Lambda Entry Point (API Gateway Proxy)
 * CAPA: Infraestructura — Adaptador de Entrada (Input Adapter)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * CAMBIO CLAVE en Sesión 05:
 *   Sesión 04: trigger era SQS → SQSEvent → Void
 *   Sesión 05: trigger es API Gateway → APIGatewayProxyRequestEvent → APIGatewayProxyResponseEvent
 *
 * FLUJO COMPLETO DE SESIÓN 05:
 *   ┌─────────────┐     POST /messages-s5      ┌──────────────────┐
 *   │   Postman   │ ─────────────────────────▶ │   API Gateway    │
 *   │  (Cliente)  │                            │ msg-pipeline-api │
 *   └─────────────┘                            └────────┬─────────┘
 *                                                       │ Lambda Proxy
 *                                                       ▼
 *                                              ┌─────────────────────────────┐
 *                                              │  msg-pipeline-processor-s05 │
 *                                              │   ProcessorHandler          │
 *                                              └──────────┬──────────────────┘
 *                                                         │
 *                                          ┌──────────────┼──────────────────┐
 *                                          ▼                                 ▼
 *                                  ┌───────────────┐              ┌──────────────────────┐
 *                                  │   DynamoDB    │              │    EventBridge Bus    │
 *                                  │ (PENDING)     │              │ msg-pipeline-events  │
 *                                  └───────────────┘              └──────────────────────┘
 *
 * API GATEWAY — INTEGRACIÓN LAMBDA PROXY:
 *   En modo "proxy", API Gateway delega TODO el control al Lambda:
 *   - Headers HTTP → event.getHeaders()
 *   - Body JSON   → event.getBody()
 *   - Path params → event.getPathParameters()
 *   - Query params → event.getQueryStringParameters()
 *   El Lambda es responsable de devolver el código HTTP, headers y body.
 *
 * PATRÓN: Adapter (Input/Driving Adapter)
 *   Convierte el contrato de AWS Lambda (RequestHandler) al contrato
 *   de negocio (ProcessMessagePort). API Gateway no conoce la lógica
 *   de negocio. El negocio no conoce API Gateway.
 *
 * HANDLER A CONFIGURAR EN LAMBDA CONSOLE:
 *   com.msgpipeline.processor.ProcessorHandler::handleRequest
 *
 * VARIABLES DE ENTORNO REQUERIDAS EN LAMBDA:
 *   DYNAMODB_TABLE_NAME = msg-pipeline-messages
 *   EVENT_BUS_NAME      = msg-pipeline-events-sesion-05
 *   AWS_REGION          = us-east-1 (Lambda la inyecta automáticamente)
 *
 * COMPILAR Y DESPLEGAR:
 *   ./gradlew clean buildZip
 *   → Genera: build/distributions/msg-pipeline-processor-sesion-05-lambda.zip
 *
 * =========================================================================
 */
@Slf4j
public class ProcessorHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // ── Bloque static — Inicialización en el Cold Start ──────────────────
    //
    // COLD START: primera vez que Lambda ejecuta esta función.
    //   El bloque 'static' se ejecuta UNA sola vez → inicializa Spring.
    //
    // WARM START: invocaciones subsiguientes.
    //   El bloque 'static' NO se vuelve a ejecutar.
    //   El contexto Spring se REUTILIZA → mucho más rápido.
    //
    // PATRÓN: Singleton (el contexto Spring es un singleton por container)
    //
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ProcessMessagePort processMessagePort;

    static {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  ProcessorHandler — Inicialización Cold Start (Sesión 05)   ║");
        log.info("║  Trigger: API Gateway Proxy → EventBridge + DynamoDB        ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");

        // ── Inicializar contexto Spring SIN servidor web ─────────────────
        //
        // WebApplicationType.SERVLET: activamos el contexto de servlet
        // para que Spring Security y los filtros funcionen correctamente
        // con el perfil 'aws'. No arranca Tomcat — Lambda gestiona el HTTP.
        //
        // NOTA: Se usa SERVLET (no NONE) porque el aws-serverless-java-container
        // requiere el contexto de servlet para procesar requests HTTP.
        // Sin esto aparece ClassCastException en Spring Boot 3.5.
        //
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ProcessorApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("aws")
                .run();

        processMessagePort = context.getBean(ProcessMessagePort.class);

        log.info("Contexto Spring inicializado. Puerto de entrada listo.");
        log.info("Tabla DynamoDB: {}",
                context.getEnvironment().getProperty("app.aws.dynamodb-table"));
        log.info("EventBridge Bus: {}",
                context.getEnvironment().getProperty("app.aws.event-bus-name"));
    }

    /**
     * Constructor público sin argumentos — OBLIGATORIO para AWS Lambda.
     * Lambda instancia el handler via reflexión: new ProcessorHandler()
     */
    public ProcessorHandler() {
        // Constructor explícito requerido por AWS Lambda runtime
    }

    /**
     * handleRequest — Método invocado por Lambda para cada request HTTP.
     *
     * API Gateway en modo proxy delega el request completo al Lambda.
     * El Lambda es responsable de:
     *   1. Parsear el body JSON del request
     *   2. Validar los datos de entrada
     *   3. Ejecutar la lógica de negocio
     *   4. Devolver la respuesta HTTP completa (statusCode + headers + body)
     *
     * @param event   Request HTTP recibido desde API Gateway (proxy mode)
     * @param context Contexto Lambda (requestId, timeout, logs, etc.)
     * @return        Respuesta HTTP completa (status + headers + body JSON)
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent event,
            Context context) {

        log.info("Request recibido [httpMethod={}] [path={}] [requestId={}]",
                event.getHttpMethod(),
                event.getPath(),
                context.getAwsRequestId());

        try {
            // ── Paso 1: Validar método HTTP ───────────────────────────────
            if (!"POST".equalsIgnoreCase(event.getHttpMethod())) {
                return buildResponse(405, "{\"error\":\"Método no permitido\",\"metodo\":\"" +
                        event.getHttpMethod() + "\"}");
            }

            // ── Paso 2: Deserializar el body del request ──────────────────
            //
            // API Gateway envía el body como String (JSON serializado).
            // Jackson lo deserializa a nuestro DTO de entrada.
            //
            String body = event.getBody();
            if (body == null || body.isBlank()) {
                return buildResponse(400, "{\"error\":\"El body del request no puede estar vacío\"}");
            }

            ProcessMessageRequest request = objectMapper.readValue(body, ProcessMessageRequest.class);

            log.info("Request deserializado [tipo={}] [canal={}] [destinatario={}]",
                    request.getMessageType(),
                    request.getChannel(),
                    request.getRecipientEmail());

            // ── Paso 3: Convertir DTO → entidad del dominio ───────────────
            //
            // El adaptador de entrada (este handler) convierte el DTO del
            // mundo HTTP al modelo de negocio (Message). El caso de uso
            // no sabe nada de HTTP, API Gateway ni Lambda.
            //
            Message domainMessage = Message.builder()
                    .messageType(request.getMessageType())
                    .channel(request.getChannel())
                    .recipientEmail(request.getRecipientEmail())
                    .content(request.getContent())
                    .build();

            // ── Paso 4: Ejecutar el caso de uso ──────────────────────────
            //
            // El caso de uso:
            //   → Asigna ID único (UUID)
            //   → Guarda en DynamoDB con status=PENDING
            //   → Publica evento en EventBridge
            //
            Message saved = processMessagePort.processMessage(domainMessage, context.getAwsRequestId());

            log.info("Mensaje procesado [messageId={}] [status={}]",
                    saved.getMessageId(), saved.getStatus());

            // ── Paso 5: Construir respuesta HTTP ──────────────────────────
            ProcessMessageResponse response = ProcessMessageResponse.builder()
                    .messageId(saved.getMessageId())
                    .status(saved.getStatus())
                    .message("Mensaje recibido y en cola de procesamiento")
                    .createdAt(saved.getCreatedAt())
                    .build();

            String responseBody = objectMapper.writeValueAsString(response);

            // 202 Accepted: el mensaje fue aceptado pero el procesamiento es asíncrono
            // (EventBridge → Audit Lambda → DynamoDB COMPLETED)
            return buildResponse(202, responseBody);

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error deserializando body del request: {}", e.getMessage());
            return buildResponse(400,
                    "{\"error\":\"JSON inválido en el body\",\"detalle\":\"" + e.getMessage() + "\"}");

        } catch (Exception e) {
            log.error("Error procesando request [requestId={}]: {}",
                    context.getAwsRequestId(), e.getMessage(), e);
            return buildResponse(500,
                    "{\"error\":\"Error interno del servidor\",\"requestId\":\"" +
                            context.getAwsRequestId() + "\"}");
        }
    }

    // ── Método auxiliar — construye la respuesta HTTP ─────────────────────

    /**
     * Construye una respuesta HTTP estándar para API Gateway (proxy mode).
     *
     * API Gateway en modo proxy lee estos campos del retorno del Lambda:
     *   - statusCode: código HTTP (200, 202, 400, 500, etc.)
     *   - headers: cabeceras HTTP a incluir en la respuesta
     *   - body: cuerpo de la respuesta (String, usualmente JSON)
     *
     * CORS headers incluidos para permitir llamadas desde navegadores.
     *
     * @param statusCode Código HTTP de la respuesta
     * @param body       Cuerpo JSON de la respuesta
     * @return           Objeto de respuesta para API Gateway
     */
    private APIGatewayProxyResponseEvent buildResponse(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "Content-Type", "application/json",
                        "Access-Control-Allow-Origin", "*",
                        "Access-Control-Allow-Headers", "Content-Type,Authorization",
                        "Access-Control-Allow-Methods", "POST,OPTIONS"
                ))
                .withBody(body);
    }
}
