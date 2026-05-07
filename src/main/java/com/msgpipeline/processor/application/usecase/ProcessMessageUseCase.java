package com.msgpipeline.processor.application.usecase;

import com.msgpipeline.processor.application.port.in.ProcessMessagePort;
import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import com.msgpipeline.processor.domain.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * =========================================================================
 * CLASE: ProcessMessageUseCase — Caso de Uso de Procesamiento
 * CAPA: Aplicación — Caso de Uso
 * ARQUITECTURA: Hexagonal + Clean Architecture
 * =========================================================================
 *
 * RESPONSABILIDAD (SRP — SOLID):
 *   Coordina el guardado en DynamoDB y la publicación en SNS.
 *   No sabe de Step Functions, ni de DynamoDB directamente.
 *   Solo conoce los puertos de salida (interfaces del dominio).
 *
 * FLUJO DETALLADO:
 *   ProcessorHandler → processMessage()
 *   → Paso 1: Asignar status=PENDING y timestamp
 *   → Paso 2: Calcular TTL (30 días)
 *   → Paso 3: DynamoDB PutItem (MessageRepository)
 *   → Paso 4: SNS Publish (NotificationPort) — BEST-EFFORT
 *   → Retornar Message guardado
 *
 * DIFERENCIA CON SESIÓN 05:
 *   Sesión 05: No había SNS directo en el Processor (lo hacía el Audit Lambda)
 *   Sesión 06: El Processor publica directamente en SNS porque no hay Audit Lambda
 *
 * PATRONES APLICADOS:
 *   - Use Case (Application Service)
 *   - Repository (abstrae DynamoDB)
 *   - Observer (SNS como notificación desacoplada)
 *   - Strategy (@Profile para cambiar implementaciones)
 * =========================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMessageUseCase implements ProcessMessagePort {

    private final MessageRepository messageRepository;
    private final NotificationPort notificationPort;

    @Value("${app.processor.ttl-days:30}")
    private int ttlDays;

    /**
     * Procesa un mensaje recibido de Step Functions.
     *
     * NOTA SOBRE messageId:
     *   A diferencia de Sesión 05, el messageId ya viene asignado por
     *   el Orchestrator Lambda. El Processor NO genera el UUID aquí.
     *   Esto permite que Step Functions use el mismo messageId en todos
     *   los estados y que la trazabilidad sea coherente.
     */
    @Override
    public Message processMessage(Message payload, String requestId) {
        log.info("Iniciando procesamiento [messageId={}] [requestId={}]",
                payload.getMessageId(), requestId);

        // ── Paso 1: Completar los datos del mensaje ───────────────────────────
        String processedAt = Instant.now().toString();

        Message message = Message.builder()
                .messageId(payload.getMessageId())    // Viene del Orchestrator (UUID)
                .messageType(payload.getMessageType())
                .channel(payload.getChannel())
                .recipientEmail(payload.getRecipientEmail())
                .content(payload.getContent())
                .status("PENDING")                    // Estado inicial
                .processedAt(processedAt)             // Timestamp del Processor
                .requestId(requestId)
                .ttl(calcularTtl())
                .build();

        // ── Paso 2: Persistir en DynamoDB ─────────────────────────────────────
        Message saved = messageRepository.save(message);
        log.info("Mensaje guardado en DynamoDB [messageId={}] [status={}]",
                saved.getMessageId(), saved.getStatus());

        // ── Paso 3: Publicar notificación en SNS ──────────────────────────────
        //
        // BEST-EFFORT: si SNS falla, el mensaje ya está en DynamoDB.
        // Step Functions recibirá el resultado exitoso igualmente.
        // Se puede configurar un estado Catch en la State Machine para
        // manejar fallos de SNS si es crítico.
        //
        try {
            notificationPort.notificar(saved);
            log.info("Notificacion SNS publicada [messageId={}]", saved.getMessageId());
        } catch (Exception e) {
            log.error("Error en notificacion SNS [messageId={}]: {}",
                    saved.getMessageId(), e.getMessage(), e);
            // No relanzamos: el guardado en DynamoDB ya fue exitoso
        }

        return saved;
    }

    private Long calcularTtl() {
        return Instant.now().getEpochSecond() + ((long) ttlDays * 86400L);
    }
}
