package com.msgpipeline.processor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msgpipeline.processor.application.port.in.ProcessMessagePort;
import com.msgpipeline.processor.config.ProcessorApplication;
import com.msgpipeline.processor.domain.model.Message;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * =========================================================================
 * CLASE: SqsHandler — Lambda Entry Point (Handler de AWS Lambda)
 * CAPA: Infraestructura — Adaptador de Entrada (Input Adapter)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  PREGUNTA FRECUENTE: ¿Por qué no hay Application.java con main()?  ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║                                                                      ║
 * ║  En una app Spring Boot normal, el flujo es:                         ║
 * ║    JVM → main(String[] args) → SpringApplication.run() → Servidor   ║
 * ║                                                                      ║
 * ║  En AWS Lambda, el flujo es completamente diferente:                 ║
 * ║    Lambda Runtime → Class.forName("SqsHandler") → new SqsHandler()  ║
 * ║    → handleRequest(SQSEvent, Context)                                ║
 * ║                                                                      ║
 * ║  AWS Lambda:                                                         ║
 * ║    1. NO ejecuta ningún main()                                       ║
 * ║    2. Instancia el handler via reflexión (new SqsHandler())          ║
 * ║    3. Llama a handleRequest() para cada invocación                   ║
 * ║    4. Gestiona el ciclo de vida del proceso (no nosotros)            ║
 * ║                                                                      ║
 * ║  Si usáramos SpringApplication.run() en el constructor, estaríamos  ║
 * ║  arrancando un servidor Tomcat en Lambda — desperdiciando memoria    ║
 * ║  y añadiendo 5-10 segundos de cold start innecesarios.              ║
 * ║                                                                      ║
 * ║  La solución: inicializamos SOLO el contexto Spring (sin servidor)   ║
 * ║  usando WebApplicationType.NONE en el bloque static.                ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * PATRÓN: Adapter (Input/Driving Adapter)
 *   SqsHandler adapta el contrato de AWS Lambda (RequestHandler<SQSEvent, Void>)
 *   al contrato de negocio (ProcessMessagePort).
 *   Lambda solo sabe de SQSEvent. El negocio solo sabe de Message.
 *   SqsHandler hace la conversión entre ambos mundos.
 *
 * HANDLER A CONFIGURAR EN LAMBDA:
 *   com.msgpipeline.processor.SqsHandler::handleRequest
 *
 * VARIABLES DE ENTORNO REQUERIDAS:
 *   DYNAMODB_TABLE_NAME = msg-pipeline-messages
 *   AWS_REGION          = us-east-1 (Lambda la inyecta automáticamente)
 *
 * COMPILAR Y DESPLEGAR:
 *   ./gradlew clean buildZip
 *   → Genera: build/distributions/msg-pipeline-processor-lambda.zip
 *   → Subir el ZIP a la consola Lambda
 *
 * =========================================================================
 */
@Slf4j
public class SqsHandler implements RequestHandler<SQSEvent, Void> {

    // ── Bloque static — Inicialización en el Cold Start ──────────────────
    //
    // COLD START: primera vez que Lambda ejecuta esta función.
    //   - La JVM se inicia
    //   - Lambda crea UNA instancia de SqsHandler (llama al constructor)
    //   - El bloque 'static' se ejecuta UNA sola vez
    //
    // WARM START: invocaciones subsiguientes.
    //   - El bloque 'static' NO se vuelve a ejecutar
    //   - Los campos 'static' mantienen sus valores
    //   - El contexto Spring se REUTILIZA → mucho más rápido
    //
    // PATRÓN: Singleton (el contexto Spring es un singleton por Lambda container)
    //
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ProcessMessagePort processMessagePort;

    static {
        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║  SqsHandler — Inicialización Cold Start (Sesión 04)      ║");
        log.info("║  Novedad: publicación en SNS tras DynamoDB (Patrón Obs.) ║");
        log.info("╚═══════════════════════════════════════════════════════════╝");

        // ── Inicializar contexto Spring SIN servidor web ─────────────────
        //
        // SpringApplicationBuilder: versión flexible de SpringApplication.
        // .web(WebApplicationType.NONE): NO arranca Tomcat/Netty.
        //   Solo crea el contenedor IoC (ApplicationContext) con todos los beans.
        // .profiles("aws"): activa el perfil 'aws':
        //   → DynamoConfig se crea (DynamoDbClient, tableName beans)
        //   → DynamoMessageRepository se registra (en vez de InMemory)
        //   → MessageController NO se crea (tiene @Profile("local"))
        //   → Swagger UI NO se activa
        //
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ProcessorApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("aws")
                .run();

        // Obtener el bean del puerto de entrada del contenedor Spring
        // El contexto Spring inyectó ProcessMessageUseCase con DynamoMessageRepository
        processMessagePort = context.getBean(ProcessMessagePort.class);

        log.info("Contexto Spring inicializado. Puerto de entrada listo.");
        log.info("Tabla DynamoDB: {}",
                context.getEnvironment().getProperty("app.aws.dynamodb-table"));
    }

    /**
     * Constructor público sin argumentos.
     *
     * OBLIGATORIO para AWS Lambda.
     * Lambda instancia el handler con: Class.forName(...).getDeclaredConstructor().newInstance()
     * Si no hay constructor público sin argumentos, Lambda lanza InstantiationException.
     *
     * No ponemos @Component ni @Service porque Lambda maneja el ciclo de vida,
     * no Spring. Spring solo gestiona las DEPENDENCIAS INTERNAS del handler.
     */
    public SqsHandler() {
        // Constructor explícito requerido por AWS Lambda runtime
    }

    /**
     * handleRequest — Método invocado por Lambda para cada lote de mensajes SQS.
     *
     * Event Source Mapping:
     *   Lambda sondea automáticamente la cola SQS (cada 20 segundos aprox.).
     *   Cuando hay mensajes, los agrupa en un BATCH y llama a handleRequest().
     *   El tamaño del batch se configura en el Event Source Mapping de Lambda.
     *
     * Manejo de errores:
     *   Si handleRequest() lanza una excepción, Lambda NO elimina el mensaje de SQS.
     *   El mensaje permanece en la cola y se reintenta (hasta maxReceiveCount).
     *   Después de maxReceiveCount intentos fallidos → mensaje va a la DLQ.
     *
     * @param event   Evento SQS con un lote de mensajes
     * @param context Contexto Lambda (requestId, tiempo restante, logs, etc.)
     * @return        Void (Lambda no necesita respuesta del processor)
     */
    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        int totalMensajes = event.getRecords().size();
        log.info("Batch SQS recibido [mensajes={}] [requestId={}] [tiempoRestante={}ms]",
                totalMensajes,
                context.getAwsRequestId(),
                context.getRemainingTimeInMillis());

        // Procesamos cada mensaje del batch individualmente
        for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
            procesarMensaje(sqsMessage);
        }

        log.info("Batch procesado exitosamente [mensajes={}]", totalMensajes);
        return null;  // Void — Lambda no usa el valor de retorno
    }

    // ── Métodos privados ──────────────────────────────────────────────────

    /**
     * Procesa un único mensaje SQS.
     *
     * El flujo es:
     *   1. Deserializar el body del mensaje SQS (String JSON → MessagePayload)
     *   2. Convertir a entidad del dominio (Message)
     *   3. Delegar al Use Case (ProcessMessagePort)
     *   4. Si hay error → lanzar excepción (mensaje vuelve a la cola)
     *
     * @param sqsMessage Mensaje individual del batch SQS
     */
    private void procesarMensaje(SQSEvent.SQSMessage sqsMessage) {
        String sqsId = sqsMessage.getMessageId();
        log.info("Procesando mensaje SQS [sqsId={}]", sqsId);

        try {
            // ── Paso 1: Deserializar el body del mensaje SQS ─────────────
            //
            // El body del mensaje SQS es el JSON que publicó API Gateway.
            // El formato corresponde al MessagePayload del Orchestrator.
            //
            MessagePayload payload = objectMapper.readValue(
                    sqsMessage.getBody(),
                    MessagePayload.class);

            log.info("Payload deserializado [id={}] [tipo={}] [canal={}]",
                    payload.getMessageId(),
                    payload.getMessageType(),
                    payload.getChannel());

            // ── Paso 2: Convertir a entidad del dominio ───────────────────
            //
            // El Handler (adaptador de entrada) convierte el DTO del mundo SQS
            // al modelo del dominio (Message). El Use Case no sabe de SQS.
            //
            Message domainMessage = Message.builder()
                    .messageId(payload.getMessageId())
                    .messageType(payload.getMessageType())
                    .channel(payload.getChannel())
                    .recipientEmail(payload.getRecipientEmail())
                    .content(payload.getContent())
                    .createdAt(payload.getCreatedAt())
                    .build();

            // ── Paso 3: Ejecutar el caso de uso ──────────────────────────
            //
            // Delegamos toda la lógica de negocio al Use Case.
            // El Use Case: asigna ID, calcula TTL, persiste en DynamoDB.
            //
            Message processed = processMessagePort.processMessage(domainMessage, sqsId);

            log.info("Mensaje procesado exitosamente [messageId={}] [status={}]",
                    processed.getMessageId(), processed.getStatus());

        } catch (Exception e) {
            // ── Manejo de errores — CRÍTICO ───────────────────────────────
            //
            // Si lanzamos la excepción:
            //   → Lambda NO elimina el mensaje de SQS
            //   → El mensaje se vuelve visible en la cola después del visibility timeout
            //   → Lambda reintentará el mensaje hasta maxReceiveCount veces
            //   → Si sigue fallando → va a la DLQ (Dead Letter Queue)
            //
            // Si NO lanzamos la excepción:
            //   → Lambda elimina el mensaje de SQS (asume éxito)
            //   → El mensaje se pierde aunque no se procesó correctamente
            //
            // CONCLUSIÓN: SIEMPRE relanzar la excepción para garantizar
            // que los mensajes fallidos lleguen a la DLQ para análisis.
            //
            log.error("Error procesando mensaje SQS [sqsId={}]: {}",
                    sqsId, e.getMessage(), e);
            throw new RuntimeException("Error procesando SQS [sqsId=" + sqsId + "]", e);
        }
    }

    // ── DTO interno para deserialización del body SQS ────────────────────
    //
    // Este DTO representa el body JSON que llega en el mensaje SQS.
    // Coincide con el payload que publica API Gateway/Orchestrator.
    //
    // @JsonIgnoreProperties(ignoreUnknown = true):
    //   Si el JSON tiene campos adicionales que no están en esta clase,
    //   Jackson los IGNORA en vez de lanzar UnrecognizedPropertyException.
    //   Importante para la evolución del schema sin romper compatibilidad.
    //
    // PATRÓN: DTO (Data Transfer Object)
    //   Clase de datos simple para transferir información entre capas.
    //   No tiene lógica de negocio, solo estructura de datos.
    //
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MessagePayload {
        private String messageId;
        private String messageType;
        private String channel;
        private String recipientEmail;
        private String content;
        private String status;
        private String createdAt;
    }
}
