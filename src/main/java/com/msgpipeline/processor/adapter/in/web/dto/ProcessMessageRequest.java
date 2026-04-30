package com.msgpipeline.processor.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =========================================================================
 * CLASE: ProcessMessageRequest — DTO de Entrada
 * CAPA: Infraestructura — Adaptador de Entrada (DTO)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * DTO (Data Transfer Object): clase simple de datos para transportar
 * la información del request HTTP al adaptador de entrada.
 *
 * DIFERENCIA con la entidad Message (dominio):
 *   - ProcessMessageRequest: lo que el CLIENTE envía (sin ID, sin timestamps)
 *   - Message: entidad completa del DOMINIO (con ID, timestamps, TTL, etc.)
 *   El adaptador convierte de ProcessMessageRequest a Message.
 *
 * VALIDACIONES con Bean Validation (JSR-380):
 *   @NotBlank: el campo no puede ser null, vacío o solo espacios
 *   @Email: debe ser un email válido
 *   @Pattern: debe coincidir con el patrón regex dado
 *
 * EJEMPLO DE REQUEST:
 * {
 *   "messageType": "EMAIL",
 *   "channel": "EMAIL",
 *   "recipientEmail": "estudiante@ejemplo.com",
 *   "content": "Mensaje de prueba Sesión 05"
 * }
 * =========================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos del mensaje a procesar")
public class ProcessMessageRequest {

    @NotBlank(message = "El tipo de mensaje es obligatorio")
    @Pattern(regexp = "EMAIL|SMS|PUSH_NOTIFICATION",
            message = "Tipo de mensaje debe ser: EMAIL, SMS o PUSH_NOTIFICATION")
    @Schema(description = "Tipo del mensaje",
            example = "EMAIL",
            allowableValues = {"EMAIL", "SMS", "PUSH_NOTIFICATION"})
    private String messageType;

    @NotBlank(message = "El canal es obligatorio")
    @Pattern(regexp = "EMAIL|SMS|WHATSAPP",
            message = "Canal debe ser: EMAIL, SMS o WHATSAPP")
    @Schema(description = "Canal de entrega",
            example = "EMAIL",
            allowableValues = {"EMAIL", "SMS", "WHATSAPP"})
    private String channel;

    @NotBlank(message = "El email del destinatario es obligatorio")
    @Email(message = "El email del destinatario debe ser válido")
    @Schema(description = "Email del destinatario",
            example = "estudiante@ejemplo.com")
    private String recipientEmail;

    @NotBlank(message = "El contenido del mensaje es obligatorio")
    @Schema(description = "Contenido del mensaje",
            example = "Hola! Este es un mensaje de prueba de la Sesión 05")
    private String content;
}
