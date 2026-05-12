package com.msgpipeline.processor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =========================================================================
 * CLASE: Message -- Entidad del Dominio (Processor)
 * CAPA: Dominio -- Modelo de Negocio
 * ARQUITECTURA: Hexagonal + Clean Architecture
 * =========================================================================
 *
 * Entidad central del dominio del Processor. Sin dependencias de AWS SDK.
 *
 * CICLO DE VIDA SESION 07:
 *   1. Step Functions envia el mensaje a SQS (validacion exitosa)
 *   2. SQS ESM dispara el Processor Lambda con SQSEvent
 *   3. Processor construye Message y lo guarda en DynamoDB (PENDING)
 *   4. Processor publica en SNS (notificacion email)
 *   5. Processor publica en EventBridge (MessageProcessed -> dispara Audit)
 * =========================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /** ID unico del mensaje -- generado por el Orchestrator (UUID) */
    private String messageId;

    /** Tipo: EMAIL | SMS | PUSH_NOTIFICATION */
    private String messageType;

    /** Canal: EMAIL | SMS | PUSH */
    private String channel;

    /** Email del destinatario */
    private String recipientEmail;

    /** Contenido del mensaje */
    private String content;

    /** Estado del mensaje: PENDING (por Processor) | COMPLETED (por Audit) */
    private String status;

    /** Timestamp ISO-8601 de cuando el Processor guardo el mensaje */
    private String processedAt;

    /** ID del request de API Gateway (trazabilidad end-to-end) */
    private String requestId;

    /** Email del usuario autenticado con Cognito JWT */
    private String userEmail;

    /** TTL Unix epoch para expiracion en DynamoDB (30 dias) */
    private Long ttl;
}
