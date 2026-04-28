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
import java.util.UUID;

/**
 * =========================================================================
 * CAPA: Aplicación — Caso de Uso (Use Case)
 * ARQUITECTURA: Hexagonal + Clean Architecture
 * =========================================================================
 *
 * RESPONSABILIDAD ÚNICA (SRP — SOLID):
 *   Esta clase SOLO orquesta el flujo de procesamiento de un mensaje.
 *   No sabe de SQS, no sabe de DynamoDB, no sabe de SNS, no sabe de HTTP.
 *   Solo conoce los puertos (interfaces) del dominio.
 *
 * CAMBIOS EN SESIÓN 04:
 *   + Inyección de NotificationPort (nuevo puerto de salida SNS)
 *   + Llamada a notificationPort.notificarProcesamiento() tras persistir
 *   El resto del flujo es idéntico a Sesión 03.
 *
 * FLUJO ACTUALIZADO (Sesión 04):
 *   SQS → SqsHandler → processMessage()
 *   → Paso 1: construir entidad Message
 *   → Paso 2: persistir en DynamoDB (MessageRepository)    [SESIÓN 03]
 *   → Paso 3: publicar notificación SNS (NotificationPort) [SESIÓN 04 — NUEVO]
 *
 * PATRÓN: Use Case (Application Service)
 *   Coordina entidades del dominio para completar un caso de uso de negocio.
 *
 * PATRÓN: Observer (via NotificationPort)
 *   El Use Case notifica sin saber quiénes escuchan.
 *   En 'aws': SnsNotificationAdapter publica en SNS → email, SMS, etc.
 *   En 'local': InMemoryNotificationAdapter loggea y almacena en memoria.
 *
 * PATRÓN: Dependency Injection (@RequiredArgsConstructor + @Service)
 *   Spring inyecta automáticamente las dependencias declaradas como final.
 *   La implementación concreta varía por perfil activo:
 *     - MessageRepository:  InMemoryMessageRepository / DynamoMessageRepository
 *     - NotificationPort:   InMemoryNotificationAdapter / SnsNotificationAdapter
 * =========================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMessageUseCase implements ProcessMessagePort {

    // Puerto de salida — persistencia. Spring inyecta según el perfil:
    // 'local' → InMemoryMessageRepository
    // 'aws'   → DynamoMessageRepository
    private final MessageRepository messageRepository;

    // Puerto de salida — notificaciones SNS (Sesión 04 — NUEVO).
    // Spring inyecta según el perfil:
    // 'local' → InMemoryNotificationAdapter
    // 'aws'   → SnsNotificationAdapter
    private final NotificationPort notificationPort;

    // Inyección de propiedad desde application.yml
    // Si la variable de entorno no existe, usa el valor por defecto: 30
    @Value("${app.processor.ttl-days:30}")
    private int ttlDays;

    /**
     * Caso de uso: Procesar un mensaje recibido desde SQS.
     *
     * Flujo de Sesión 04 (extendido desde Sesión 03):
     *   SQS → SqsHandler → processMessage() → DynamoDB (COMPLETED) → SNS (notificación)
     *
     * En sesiones posteriores se agregará:
     *   → Step Functions (Sesión 05)
     *   → JWT Cognito auth (Sesión 06)
     *
     * @param payload Datos del mensaje deserializados del evento SQS
     * @param sqsId   ID del mensaje SQS (para trazabilidad)
     * @return        Mensaje persistido en DynamoDB con notificación enviada
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

        // ── Paso 2: Persistir en el repositorio (SESIÓN 03) ──────────────
        //
        // El Use Case NO sabe si es DynamoDB, H2, o un Map en memoria.
        // Solo conoce el puerto MessageRepository (interfaz).
        // Spring inyectó la implementación correcta según el perfil activo.
        //
        Message saved = messageRepository.save(message);

        log.info("Mensaje persistido exitosamente [messageId={}]", saved.getMessageId());

        // ── Paso 3: Publicar notificación (SESIÓN 04 — NUEVO) ────────────
        //
        // PATRÓN Observer: el Use Case notifica sin saber quiénes escuchan.
        // En 'aws': SnsNotificationAdapter publica en SNS → email al suscriptor.
        // En 'local': InMemoryNotificationAdapter loggea la notificación simulada.
        //
        // IMPORTANTE: la notificación es best-effort.
        //   Si falla, el mensaje ya fue persistido con COMPLETED.
        //   SnsNotificationAdapter captura las excepciones internamente.
        //   NO relanzamos el error — SQS no debe reintentar por un fallo de SNS.
        //
        notificationPort.notificarProcesamiento(saved);

        log.info("Procesamiento completo [messageId={}] [status={}]",
                saved.getMessageId(), saved.getStatus());

        return saved;
    }

    // ── Métodos auxiliares privados ───────────────────────────────────────

    /**
     * Resuelve el ID del mensaje.
     * Si el payload ya trae un ID (generado por el Orchestrator/Validator), lo usa.
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
