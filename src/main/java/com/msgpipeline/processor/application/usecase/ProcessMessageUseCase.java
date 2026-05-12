package com.msgpipeline.processor.application.usecase;

import com.msgpipeline.processor.application.port.in.ProcessMessagePort;
import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.EventPublisherPort;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import com.msgpipeline.processor.domain.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * =========================================================================
 * CLASE: ProcessMessageUseCase -- Caso de Uso de Procesamiento
 * CAPA: Aplicacion -- Caso de Uso (Application Service)
 * ARQUITECTURA: Hexagonal + Clean Architecture
 * =========================================================================
 *
 * RESPONSABILIDAD (SRP): Orquesta DynamoDB + SNS + EventBridge.
 * No sabe de SQS ni interactua con AWS directamente.
 *
 * FLUJO SESION 07:
 *   ProcessorHandler --> processMessage()
 *   --> Paso 1: Asignar PENDING + processedAt + TTL (30 dias)
 *   --> Paso 2: DynamoDB PutItem (persistir con PENDING)
 *   --> Paso 3: SNS Publish (notificacion de recepcion)
 *   --> Paso 4: EventBridge PutEvents (MessageProcessed --> Audit Lambda)
 *
 * PATRONES:
 *   - Use Case (Application Service): orquesta el flujo
 *   - Observer: EventBridge notifica sin saber los suscriptores
 *   - Template Method: flujo fijo de 4 pasos
 *   - DIP: depende de puertos (abstracciones)
 * =========================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMessageUseCase implements ProcessMessagePort {

    // Puertos de salida -- Spring inyecta la implementacion correcta segun perfil
    private final MessageRepository   messageRepository;   // DynamoDB o InMemory
    private final NotificationPort    notificationPort;    // SNS o InMemory
    private final EventPublisherPort  eventPublisherPort;  // EventBridge o InMemory

    private static final int TTL_DAYS = 30;

    @Override
    public Message processMessage(Message message, String requestId) {
        log.info("Procesando mensaje [messageId={}] [tipo={}] [requestId={}]",
                message.getMessageId(), message.getMessageType(), requestId);

        // -- Paso 1: Enriquecer el mensaje ----------------------------------
        message.setStatus("PENDING");
        message.setProcessedAt(Instant.now().toString());
        message.setRequestId(requestId);
        message.setTtl(Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS).getEpochSecond());

        // -- Paso 2: DynamoDB PutItem (status=PENDING) ----------------------
        Message saved = messageRepository.save(message);
        log.info("DynamoDB PutItem exitoso [messageId={}] [status=PENDING]", saved.getMessageId());

        // -- Paso 3: SNS Publish (notificacion de recepcion) ----------------
        try {
            notificationPort.notificarRecepcion(saved);
            log.info("SNS Publish exitoso [messageId={}]", saved.getMessageId());
        } catch (Exception e) {
            log.error("Error SNS Publish [messageId={}]: {}", saved.getMessageId(), e.getMessage());
            // No fallamos el procesamiento por error de SNS
        }

        // -- Paso 4: EventBridge PutEvents (MessageProcessed) ---------------
        // PATRON OBSERVER: El Processor publica el evento.
        // La regla msg-pipeline-audit-rule-sesion-07 captura el evento
        // y dispara el Lambda Audit de forma asincrona.
        try {
            eventPublisherPort.publicarMensajeProcesado(saved);
            log.info("EventBridge PutEvents exitoso [messageId={}] [detailType=MessageProcessed]",
                    saved.getMessageId());
        } catch (Exception e) {
            log.error("Error EventBridge PutEvents [messageId={}]: {}",
                    saved.getMessageId(), e.getMessage());
        }

        log.info("Procesamiento completado [messageId={}] [status=PENDING]", saved.getMessageId());
        return saved;
    }
}
