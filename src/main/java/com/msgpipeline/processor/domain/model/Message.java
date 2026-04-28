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
 * Message es la ENTIDAD CENTRAL del dominio. En Clean Architecture, las
 * entidades del dominio:
 *   1. No dependen de ningún framework (ni Spring, ni AWS SDK)
 *   2. Contienen la lógica de negocio pura
 *   3. Son reutilizables en cualquier contexto (Lambda, REST, batch, etc.)
 *
 * PATRÓN: Entity (DDD — Domain Driven Design)
 *   Una entidad se identifica por su ID único (messageId), no por el
 *   valor de sus atributos. Dos mensajes con el mismo contenido pero
 *   diferente ID son entidades distintas.
 *
 * ¿POR QUÉ NO USAR Map<String, Object> directamente?
 *   - El Map no tiene semántica de negocio (no sabes qué campos existen)
 *   - La entidad tipada permite validaciones y lógica propia
 *   - El compilador detecta errores en tiempo de compilación, no en runtime
 *
 * LOMBOK: Las anotaciones generan automáticamente:
 *   @Data        → getters, setters, equals, hashCode, toString
 *   @Builder     → patrón Builder para construcción fluida
 *   @NoArgsConstructor → constructor sin argumentos (necesario para Jackson)
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
     * En DynamoDB es la Partition Key (clave de partición).
     * Se genera con UUID.randomUUID() en el Use Case.
     */
    private String messageId;

    /**
     * Tipo de mensaje: EMAIL, SMS, PUSH_NOTIFICATION, etc.
     * Determina cómo debe procesarse el mensaje.
     */
    private String messageType;

    /**
     * Canal de entrega del mensaje.
     * Puede diferir del tipo: el canal EMAIL usa SMTP o SES.
     */
    private String channel;

    /**
     * Destinatario del mensaje (email, teléfono, etc.)
     */
    private String recipientEmail;

    /**
     * Contenido o cuerpo del mensaje a enviar.
     */
    private String content;

    /**
     * Estado del procesamiento del mensaje.
     * Sesión 03: PROCESSING → COMPLETED (o FAILED si hay error)
     * Sesiones siguientes: agregaremos VALIDATED, SENT, etc.
     */
    private String status;

    /**
     * Timestamp ISO-8601 de cuando fue creado el mensaje.
     * Viene en el payload del mensaje SQS.
     */
    private String createdAt;

    /**
     * Timestamp ISO-8601 de cuando fue procesado por este Lambda.
     * Lo asigna el Use Case en el momento del procesamiento.
     */
    private String processedAt;

    /**
     * ID del mensaje SQS original.
     * Permite trazabilidad: dado un ítem en DynamoDB, saber cuál
     * mensaje SQS lo originó.
     */
    private String sqsMessageId;

    /**
     * TTL (Time-To-Live) en segundos desde Unix epoch.
     * DynamoDB eliminará automáticamente el ítem cuando este
     * timestamp sea superado. Configurado a 30 días.
     * IMPORTANTE: ahorra costos al no acumular datos indefinidamente.
     */
    private Long ttl;
}
