package com.msgpipeline.processor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =========================================================================
 * CLASE: Message — Entidad del Dominio
 * CAPA: Dominio — Modelo de Negocio
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Entidad central del dominio de procesamiento de mensajes.
 *
 * CAMBIOS EN SESIÓN 06 vs SESIÓN 05:
 *   - El messageId ahora viene del input de Step Functions
 *     (generado por el Orchestrator Lambda, no aquí)
 *   - Nuevo campo: processedAt (timestamp de cuando el Processor guardó en DDB)
 *   - Se elimina eventBusName (sesión 05 usaba EventBridge, sesión 06 usa Step Functions)
 *
 * CICLO DE VIDA:
 *   1. Orchestrator genera el messageId (UUID)
 *   2. Step Functions pasa el messageId al Processor
 *   3. Processor crea el Message con status=PENDING y lo guarda en DynamoDB
 *   4. Processor publica en SNS con los datos del mensaje
 * =========================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /** ID único del mensaje. Generado por el Orchestrator (UUID). */
    private String messageId;

    /** Tipo: EMAIL, SMS, PUSH_NOTIFICATION */
    private String messageType;

    /** Canal: EMAIL, SMS, WHATSAPP */
    private String channel;

    /** Email del destinatario */
    private String recipientEmail;

    /** Contenido del mensaje */
    private String content;

    /**
     * Estado del mensaje.
     * PENDING: Processor guardó en DynamoDB, esperando procesamiento final.
     * En sesión 06 Step Functions puede coordinar cambios de estado adicionales.
     */
    private String status;

    /**
     * Timestamp ISO-8601 de cuando el Processor guardó el mensaje en DynamoDB.
     * Asignado en ProcessMessageUseCase.
     */
    private String processedAt;

    /**
     * ID del request de API Gateway (para trazabilidad end-to-end).
     * Viaja desde API GW → Orchestrator → Step Functions → Processor.
     */
    private String requestId;

    /**
     * TTL para expiración automática en DynamoDB.
     * Valor en segundos Unix epoch. DynamoDB elimina el ítem tras 30 días.
     */
    private Long ttl;
}
