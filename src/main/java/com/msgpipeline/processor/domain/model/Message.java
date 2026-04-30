package com.msgpipeline.processor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =========================================================================
 * CAPA: Dominio — Entidad de Negocio
 * ARQUITECTURA: Hexagonal (Clean Architecture)
 * =========================================================================
 *
 * Message es la ENTIDAD CENTRAL del dominio de procesamiento de mensajes.
 *
 * PRINCIPIOS DE CLEAN ARCHITECTURE:
 *   1. No depende de ningún framework (ni Spring, ni AWS SDK)
 *   2. Solo contiene datos y lógica de negocio pura
 *   3. Es reutilizable en cualquier contexto (Lambda, REST, batch, etc.)
 *
 * CAMBIOS EN SESIÓN 05:
 *   + Nuevo campo: eventId → ID del evento publicado en EventBridge
 *   + Nuevo campo: eventBusName → Bus al que se publicó el evento
 *   Status inicial cambia: PROCESSING → PENDING
 *     - PENDING: mensaje recibido, evento publicado, esperando Audit Lambda
 *     - COMPLETED: Audit Lambda actualizó DynamoDB después de procesar
 *
 * CICLO DE VIDA DEL MENSAJE EN SESIÓN 05:
 *   1. Processor Lambda recibe request → crea Message con status=PENDING
 *   2. DynamoDB guarda Message (PENDING)
 *   3. EventBridge recibe evento → Audit Lambda inicia
 *   4. Audit Lambda → actualiza DynamoDB a COMPLETED (después de 15s delay)
 *
 * PATRÓN: Entity (DDD — Domain Driven Design)
 *   Una entidad se identifica por su ID único (messageId).
 *
 * LOMBOK: Genera automáticamente:
 *   @Data        → getters, setters, equals, hashCode, toString
 *   @Builder     → patrón Builder para construcción fluida
 *   @NoArgsConstructor → constructor sin argumentos (Jackson lo necesita)
 *   @AllArgsConstructor → constructor con todos los argumentos
 * =========================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /**
     * Identificador único del mensaje.
     * Partition Key (PK) en DynamoDB.
     * Generado con UUID.randomUUID() en el Use Case.
     */
    private String messageId;

    /**
     * Tipo de mensaje: EMAIL, SMS, PUSH_NOTIFICATION.
     * Determina cómo debe procesarse el mensaje.
     */
    private String messageType;

    /**
     * Canal de entrega: EMAIL, SMS, WHATSAPP.
     */
    private String channel;

    /**
     * Destinatario: email, número de teléfono, etc.
     */
    private String recipientEmail;

    /**
     * Contenido o cuerpo del mensaje.
     */
    private String content;

    /**
     * Estado del ciclo de vida del mensaje.
     *
     * Sesión 05 - Flujo de estados:
     *   PENDING   → Processor guardó en DynamoDB, evento enviado a EventBridge
     *   COMPLETED → Audit Lambda procesó y actualizó (después de ~15s)
     *   FAILED    → Error en el procesamiento
     */
    private String status;

    /**
     * Timestamp ISO-8601 de cuando fue creado el mensaje.
     * Asignado por el Processor Lambda al recibir el request.
     */
    private String createdAt;

    /**
     * Timestamp ISO-8601 de cuando fue procesado por el Audit Lambda.
     * Lo asigna el Audit Handler al actualizar DynamoDB.
     */
    private String processedAt;

    /**
     * ID del request de API Gateway que originó este mensaje.
     * Para trazabilidad: del ítem DynamoDB al request HTTP original.
     */
    private String requestId;

    /**
     * ID del evento publicado en EventBridge. (NUEVO Sesión 05)
     * Permite correlacionar el mensaje con el evento de EventBridge.
     */
    private String eventId;

    /**
     * Nombre del EventBridge Bus al que se publicó el evento. (NUEVO Sesión 05)
     * msg-pipeline-events-sesion-05
     */
    private String eventBusName;

    /**
     * TTL para expiración automática en DynamoDB.
     * Segundos desde Unix epoch. DynamoDB elimina el ítem automáticamente.
     * Configurado a 30 días → ahorra costos a largo plazo.
     */
    private Long ttl;
}
