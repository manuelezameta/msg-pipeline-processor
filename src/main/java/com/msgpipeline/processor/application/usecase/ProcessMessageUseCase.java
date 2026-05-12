package com.msgpipeline.processor.application.usecase;

import com.msgpipeline.processor.application.port.in.ProcessMessagePort;
import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * =========================================================================
 * CAPA: Aplicación — Caso de Uso (Use Case)
 * ARQUITECTURA: Hexagonal + Clean Architecture
 * =========================================================================
 *
 * RESPONSABILIDAD ÚNICA (SRP — SOLID):
 *   Esta clase SOLO orquesta el flujo de procesamiento de un mensaje.
 *   No sabe de SQS, no sabe de DynamoDB, no sabe de HTTP.
 *   Solo conoce los puertos (interfaces) del dominio.
 *
 * PATRÓN: Use Case (Application Service)
 *   Coordina entidades del dominio para completar un caso de uso de negocio.
 *   En DDD se llamaría "Application Service".
 *
 * PATRÓN: Dependency Injection (via @RequiredArgsConstructor)
 *   Spring inyecta automáticamente las dependencias declaradas como
 *   campos 'final'. Lombok genera el constructor con todos los final.
 *   El contexto Spring decide QUÉ implementación inyectar según el perfil:
 *     - Perfil 'local' → InMemoryMessageRepository
 *     - Perfil 'aws'   → DynamoMessageRepository
 *
 * PATRÓN: Builder (en la construcción de Message)
 *   Usamos el Builder generado por Lombok para construir el mensaje
 *   con múltiples atributos de forma legible, evitando constructores
 *   con muchos parámetros (anti-patrón "Telescoping Constructor").
 *
 * ¿POR QUÉ @Service?
 *   @Service es un @Component especializado que indica a Spring que
 *   esta clase contiene lógica de negocio. Spring la registra como
 *   bean y la gestiona (singleton por defecto).
 * =========================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMessageUseCase implements ProcessMessagePort {

    // Puerto de salida — Spring inyecta la implementación correcta
    // según el perfil activo (InMemory para 'local', DynamoDB para 'aws')
    private final MessageRepository messageRepository;

    // Inyección de propiedad desde application.yml
    // Si la variable de entorno no existe, usa el valor por defecto: 30
    @Value("${app.processor.ttl-days:30}")
    private int ttlDays;

    /**
     * Caso de uso: Procesar un mensaje recibido desde SQS.
     *
     * Flujo de Sesión 08:
     *   SQS → SqsHandler → processMessage() → DynamoDB (COMPLETED)
     *
     * En sesiones posteriores se agregará:
     *   → SNS publish (Sesión 04)
     *   → Step Functions (Sesión 05)
     *
     * @param payload Datos del mensaje deserializados del evento SQS
     * @param sqsId   ID del mensaje SQS (para trazabilidad)
     * @return        Mensaje persistido en DynamoDB
     */
    @Override
    public Message processMessage(Message payload, String sqsId) {
        log.info("Iniciando procesamiento [sqsId={}] [tipo={}]",
                sqsId, payload.getMessageType());

        // ── Paso 1: Construir la entidad de dominio ───────────────────────
        //
        // PATRÓN BUILDER: construimos el objeto paso a paso de forma legible.
        // Cada campo tiene un propósito claro documentado abajo.
        //
        Message message = Message.builder()
                // ID del mensaje: usamos el que viene en el payload si existe,
                // sino generamos uno nuevo con UUID v4 (aleatorio, único global)
                .messageId(resolveMessageId(payload.getMessageId()))

                // Campos del payload original
                .messageType(payload.getMessageType())
                .channel(payload.getChannel())
                .recipientEmail(payload.getRecipientEmail())
                .content(payload.getContent())

                // Estado: COMPLETED — el mensaje fue procesado exitosamente
                // Si ocurre una excepción, Lambda reintentará y el mensaje
                // podría ir a la DLQ (Dead Letter Queue) — ver SqsHandler
                .status("COMPLETED")

                // Timestamp de creación original (viene en el payload)
                .createdAt(resolveTimestamp(payload.getCreatedAt()))

                // Timestamp de procesamiento: AHORA (momento en que este Lambda procesa)
                .processedAt(Instant.now().toString())

                // Referencia al mensaje SQS para trazabilidad
                .sqsMessageId(sqsId)

                // TTL: fecha de expiración en DynamoDB
                // DynamoDB eliminará automáticamente el ítem después de 'ttlDays' días
                // Formato: segundos desde Unix epoch (1 Jan 1970)
                .ttl(calculateTtl())

                .build();

        log.info("Entidad construida [messageId={}] [status={}] [ttl={}]",
                message.getMessageId(), message.getStatus(), message.getTtl());

        // ── Paso 2: Persistir en el repositorio ──────────────────────────
        //
        // El Use Case NO sabe si es DynamoDB, H2, o un Map en memoria.
        // Solo conoce el puerto MessageRepository (interfaz).
        // Spring inyectó la implementación correcta según el perfil activo.
        //
        Message saved = messageRepository.save(message);

        log.info("Mensaje persistido exitosamente [messageId={}]", saved.getMessageId());

        return saved;
    }

    // ── Métodos auxiliares privados ───────────────────────────────────────

    /**
     * Resuelve el ID del mensaje.
     * Si el payload ya trae un ID (generado por el Orchestrator), lo usa.
     * Si no, genera uno nuevo para garantizar unicidad en DynamoDB.
     */
    private String resolveMessageId(String existingId) {
        return (existingId != null && !existingId.isBlank())
                ? existingId
                : UUID.randomUUID().toString();
    }

    /**
     * Resuelve el timestamp de creación.
     * Si viene en el payload, lo usa (preservando el timestamp original).
     * Si no, usa el momento actual.
     */
    private String resolveTimestamp(String existing) {
        return (existing != null && !existing.isBlank())
                ? existing
                : Instant.now().toString();
    }

    /**
     * Calcula el TTL para DynamoDB.
     *
     * DynamoDB usa el TTL para eliminar ítems automáticamente
     * sin necesidad de scripts de limpieza manuales.
     *
     * IMPORTANTE: DynamoDB usa SEGUNDOS desde Unix epoch (no milisegundos)
     * Formula: ahora_en_segundos + (días * segundos_por_día)
     */
    private Long calculateTtl() {
        // 86400 = 60 segundos × 60 minutos × 24 horas = segundos por día
        return Instant.now().getEpochSecond() + ((long) ttlDays * 86400L);
    }
}
