package com.msgpipeline.processor.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CAPA: Infraestructura — DTO de Salida
 * PATRÓN: DTO (Data Transfer Object)
 *
 * Respuesta estandarizada del endpoint de procesamiento.
 * Oculta los detalles internos del dominio (TTL, sqsMessageId, etc.)
 * exponiendo solo lo relevante para el cliente HTTP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta del procesamiento del mensaje")
public class ProcessMessageResponse {

    @Schema(description = "ID único asignado al mensaje", example = "550e8400-e29b-41d4-a716-446655440000")
    private String messageId;

    @Schema(description = "Estado final del procesamiento", example = "COMPLETED")
    private String status;

    @Schema(description = "Timestamp de procesamiento", example = "2026-04-23T15:30:00Z")
    private String processedAt;

    @Schema(description = "Mensaje informativo para el estudiante")
    private String mensaje;
}
