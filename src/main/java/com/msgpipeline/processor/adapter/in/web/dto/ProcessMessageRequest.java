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
 * CAPA: Infraestructura — DTO de Entrada (Data Transfer Object)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * PATRÓN: DTO (Data Transfer Object)
 *   Objeto que transporta datos entre capas. Separa el modelo de la
 *   API (HTTP) del modelo del dominio (Message). Beneficios:
 *     1. La API puede cambiar sin afectar al dominio
 *     2. El dominio puede cambiar sin romper el contrato de la API
 *     3. Validaciones de entrada en la capa correcta (infraestructura)
 *
 * OPENAPI — Anotaciones @Schema:
 *   Documentan el campo en Swagger UI con descripción y ejemplo.
 *   Solo tienen efecto con SpringDoc en tiempo de generación de docs.
 *
 * USO: Solo disponible en perfil 'local' para pruebas via Swagger UI.
 * =========================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de procesamiento de un mensaje (simula el payload SQS)")
public class ProcessMessageRequest {

    @NotBlank(message = "El tipo de mensaje es obligatorio")
    @Pattern(regexp = "EMAIL|SMS|PUSH_NOTIFICATION",
             message = "Debe ser EMAIL, SMS o PUSH_NOTIFICATION")
    @Schema(description = "Tipo de mensaje a procesar",
            example = "EMAIL",
            allowableValues = {"EMAIL", "SMS", "PUSH_NOTIFICATION"})
    private String messageType;

    @NotBlank(message = "El canal es obligatorio")
    @Schema(description = "Canal de entrega del mensaje",
            example = "CORREO_ELECTRONICO")
    private String channel;

    @Email(message = "El email del destinatario no es válido")
    @Schema(description = "Email del destinatario",
            example = "estudiante@ankuacademy.com")
    private String recipientEmail;

    @NotBlank(message = "El contenido del mensaje es obligatorio")
    @Schema(description = "Contenido del mensaje a enviar",
            example = "Bienvenido al curso Especialista Spring Boot + AWS Serverless")
    private String content;
}
