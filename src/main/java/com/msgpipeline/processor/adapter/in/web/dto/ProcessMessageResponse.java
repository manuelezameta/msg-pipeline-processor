package com.msgpipeline.processor.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =========================================================================
 * CLASE: ProcessMessageResponse — DTO de Salida
 * CAPA: Infraestructura — Adaptador de Entrada (DTO)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * DTO de respuesta que devuelve el endpoint POST /messages-s5.
 * Contiene la información mínima necesaria para que el cliente
 * pueda hacer seguimiento del mensaje procesado.
 *
 * CÓDIGO HTTP: 202 Accepted (no 201 Created)
 *   202 significa: "La solicitud fue aceptada pero el procesamiento
 *   aún no ha completado." Apropiado para flujos asíncronos donde
 *   el Audit Lambda completará el procesamiento después.
 *
 * EJEMPLO DE RESPONSE:
 * {
 *   "messageId": "550e8400-e29b-41d4-a716-446655440000",
 *   "status": "PENDING",
 *   "message": "Mensaje recibido y en cola de procesamiento",
 *   "createdAt": "2025-05-01T19:00:00Z"
 * }
 * =========================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta del procesamiento del mensaje")
public class ProcessMessageResponse {

    @Schema(description = "ID único del mensaje generado por el sistema",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private String messageId;

    @Schema(description = "Estado actual del mensaje",
            example = "PENDING",
            allowableValues = {"PENDING", "COMPLETED", "FAILED"})
    private String status;

    @Schema(description = "Mensaje informativo del resultado",
            example = "Mensaje recibido y en cola de procesamiento")
    private String message;

    @Schema(description = "Timestamp ISO-8601 de creación del mensaje",
            example = "2025-05-01T19:00:00Z")
    private String createdAt;
}
