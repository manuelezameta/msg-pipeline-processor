package com.msgpipeline.processor.application.usecase;

import com.msgpipeline.processor.application.port.in.ProcessMessagePort;
import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.EventPublisherPort;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * =========================================================================
 * CLASE: ProcessMessageUseCase — Caso de Uso de Procesamiento
 * CAPA: Aplicación — Caso de Uso
 * ARQUITECTURA: Hexagonal + Clean Architecture
 * =========================================================================
 *
 * RESPONSABILIDAD ÚNICA (SRP — SOLID):
 *   Orquesta el flujo de procesamiento de un mensaje recibido por HTTP.
 *   No sabe de API Gateway, no sabe de DynamoDB, no sabe de EventBridge.
 *   Solo conoce los PUERTOS (interfaces) del dominio.
 *
 * FLUJO COMPLETO (Sesión 05):
 *   HTTP Request → ProcessorHandler → processMessage()
 *   → Paso 1: Construir entidad Message con status=PENDING
 *   → Paso 2: Persistir en DynamoDB (MessageRepository)   [status=PENDING]
 *   → Paso 3: Publicar evento en EventBridge              [NUEVO Sesión 05]
 *   → Retornar Message guardado
 *
 * PATRONES APLICADOS:
 *   - Use Case (Application Service): orquesta el flujo de negocio
 *   - Observer: publica evento sin saber quién escucha (EventBridge lo enruta)
 *   - Strategy: MessageRepository y EventPublisherPort cambian por perfil
 *   - Builder: construye el objeto Message paso a paso
 *   - Dependency Injection: @RequiredArgsConstructor + @Service
 *
 * PRINCIPIO OPEN/CLOSED (OCP — SOLID):
 *   Cuando se agregan nuevos consumidores de eventos (más Lambdas),
 *   no se modifica este Use Case. Solo se agrega una Rule en EventBridge.
 * =========================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMessageUseCase implements ProcessMessagePort {

    // Puerto de salida — persistencia DynamoDB
    // 'local' → InMemoryMessageRepository
    // 'aws'   → DynamoMessageRepository
    private final MessageRepository messageRepository;

    // Puerto de salida — publicación de eventos EventBridge (NUEVO Sesión 05)
    // 'local' → InMemoryEventAdapter
    // 'aws'   → EventBridgeAdapter
    private final EventPublisherPort eventPublisherPort;

    @Value("${app.processor.ttl-days:30}")
    private int ttlDays;

    @Value("${app.aws.event-bus-name:msg-pipeline-events-sesion-05}")
    private String eventBusName;

    /**
     * Caso de uso: Procesar mensaje recibido desde API Gateway.
     *
     * FLUJO DETALLADO:
     *   1. Generar ID único (UUID) para el mensaje
     *   2. Registrar timestamp de creación
     *   3. Guardar en DynamoDB con status=PENDING
     *   4. Publicar evento "MessageReceived" en EventBridge
     *   5. EventBridge enruta a Audit Lambda (de forma asíncrona)
     *   6. Retornar mensaje guardado (status=PENDING)
     *
     * NOTA: El status PENDING es INTENCIONAL.
     *   El Processor NO espera la respuesta del Audit Lambda.
     *   El flujo es asíncrono: Processor → EventBridge → Audit (paralelo)
     *   El Audit Lambda actualizará a COMPLETED cuando termine.
     *
     * @param payload   Datos del mensaje del request HTTP
     * @param requestId ID del request de API Gateway
     * @return          Mensaje guardado con status=PENDING
     */
    @Override
    public Message processMessage(Message payload, String requestId) {
        log.info("Iniciando caso de uso [requestId={}] [tipo={}] [canal={}]",
                requestId, payload.getMessageType(), payload.getChannel());

        // ── Paso 1: Construir la entidad del dominio ──────────────────────
        //
        // PATRÓN BUILDER: construcción fluida y legible del objeto Message.
        // Cada campo tiene un propósito de negocio claro.
        //
        String messageId = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        Message message = Message.builder()
                // ID único generado aquí — el cliente no lo elige (seguridad)
                .messageId(messageId)

                // Campos del payload del request HTTP
                .messageType(payload.getMessageType())
                .channel(payload.getChannel())
                .recipientEmail(payload.getRecipientEmail())
                .content(payload.getContent())

                // PENDING: el mensaje fue recibido y aceptado.
                // El Audit Lambda cambiará esto a COMPLETED (con 15s delay).
                .status("PENDING")

                // Timestamp de recepción del mensaje
                .createdAt(createdAt)

                // requestId de API Gateway para trazabilidad end-to-end
                .requestId(requestId)

                // EventBus donde se publicará el evento (para consulta posterior)
                .eventBusName(eventBusName)

                // TTL: DynamoDB eliminará el ítem después de 30 días automáticamente
                .ttl(calculateTtl())

                .build();

        log.info("Entidad construida [messageId={}] [status={}] [ttl={}]",
                message.getMessageId(), message.getStatus(), message.getTtl());

        // ── Paso 2: Persistir en DynamoDB con status=PENDING ─────────────
        //
        // PutItem en la tabla msg-pipeline-messages.
        // El ítem tendrá status=PENDING hasta que el Audit Lambda lo actualice.
        //
        Message saved = messageRepository.save(message);
        log.info("Mensaje persistido en DynamoDB [messageId={}] [status={}]",
                saved.getMessageId(), saved.getStatus());

        // ── Paso 3: Publicar evento en EventBridge ────────────────────────
        //
        // PATRÓN OBSERVER:
        //   El Processor publica el evento sin saber quién lo consume.
        //   EventBridge enruta según las Rules configuradas:
        //     source=msg-pipeline.processor → Target: Audit Lambda
        //
        // El eventId retornado es el ID asignado por EventBridge al evento.
        // Útil para correlación y debugging en la consola de EventBridge.
        //
        try {
            String eventId = eventPublisherPort.publishMessageReceived(saved);
            saved.setEventId(eventId);
            log.info("Evento publicado en EventBridge [messageId={}] [eventId={}] [bus={}]",
                    saved.getMessageId(), eventId, eventBusName);
        } catch (Exception e) {
            // La publicación del evento es BEST-EFFORT.
            // Si EventBridge falla, el mensaje ya está en DynamoDB (PENDING).
            // Se puede reintentar la publicación con un proceso separado.
            log.error("Error publicando evento EventBridge [messageId={}]: {}",
                    saved.getMessageId(), e.getMessage(), e);
        }

        log.info("Caso de uso completado [messageId={}] [status={}]",
                saved.getMessageId(), saved.getStatus());

        return saved;
    }

    // ── Método auxiliar — calcula TTL para DynamoDB ───────────────────────

    /**
     * Calcula el TTL (Time-To-Live) para DynamoDB.
     *
     * DynamoDB acepta TTL en SEGUNDOS desde Unix epoch (no milisegundos).
     * Fórmula: ahora_en_segundos + (ttlDays × 86400)
     *
     * @return Timestamp de expiración en segundos desde Unix epoch
     */
    private Long calculateTtl() {
        return Instant.now().getEpochSecond() + ((long) ttlDays * 86400L);
    }
}
