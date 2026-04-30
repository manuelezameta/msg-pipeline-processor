package com.msgpipeline.processor.adapter.in.web;

import com.msgpipeline.processor.adapter.in.web.dto.ProcessMessageRequest;
import com.msgpipeline.processor.adapter.in.web.dto.ProcessMessageResponse;
import com.msgpipeline.processor.application.port.in.ProcessMessagePort;
import com.msgpipeline.processor.domain.model.Message;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * =========================================================================
 * CLASE: MessageController — Adaptador de Entrada HTTP (REST)
 * CAPA: Infraestructura — Adaptador de Entrada (Input Adapter)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * @Profile("local"): Solo activo en desarrollo local.
 *   En Lambda, el adaptador de entrada es ProcessorHandler (API Gateway).
 *   En local, este Controller permite:
 *     - Probar el flujo con Swagger UI (http://localhost:8083/swagger-ui.html)
 *     - Probar con Postman apuntando a localhost
 *     - Ejecutar sin necesidad de AWS API Gateway
 *
 * EQUIVALENCIA CON LAMBDA:
 *   ProcessorHandler (Lambda) ≈ MessageController (local)
 *   Ambos reciben el request HTTP, lo convierten al dominio y llaman al Use Case.
 *
 * ENDPOINT:
 *   POST /messages-s5 → procesa el mensaje y publica en EventBridge
 * =========================================================================
 */
@Slf4j
@RestController
@RequestMapping("/messages-s5")
@Profile("local")
@RequiredArgsConstructor
@Tag(name = "Mensajes", description = "API de procesamiento de mensajes — Sesión 05")
public class MessageController {

    private final ProcessMessagePort processMessagePort;

    /**
     * POST /messages-s5
     *
     * Recibe un mensaje para procesar:
     *   1. Guarda en DynamoDB con status=PENDING
     *   2. Publica evento en EventBridge (simulado en local)
     *   3. Retorna 202 Accepted con messageId
     *
     * La respuesta es 202 (no 201) porque el procesamiento es ASÍNCRONO:
     *   El mensaje fue aceptado, pero el status COMPLETED
     *   lo asignará el Audit Lambda después de recibir el evento EventBridge.
     */
    @Operation(
            summary = "Procesar un mensaje",
            description = """
                    Recibe un mensaje para procesar de forma asíncrona.
                    
                    **Flujo:**
                    1. Guarda el mensaje en DynamoDB con status=PENDING
                    2. Publica evento MessageReceived en EventBridge
                    3. Audit Lambda recibirá el evento y actualizará a COMPLETED
                    
                    **Retorna 202 Accepted** (asíncrono — el procesamiento continúa en segundo plano)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Mensaje aceptado para procesamiento",
                    content = @Content(schema = @Schema(implementation = ProcessMessageResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "messageId": "550e8400-e29b-41d4-a716-446655440000",
                                        "status": "PENDING",
                                        "message": "Mensaje recibido y en cola de procesamiento",
                                        "createdAt": "2025-05-01T19:00:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Request inválido"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @PostMapping
    public ResponseEntity<ProcessMessageResponse> processMessage(
            @Valid @RequestBody ProcessMessageRequest request) {

        log.info("POST /messages-s5 [tipo={}] [canal={}] [destinatario={}]",
                request.getMessageType(), request.getChannel(), request.getRecipientEmail());

        // Convertir DTO → entidad de dominio
        Message domainMessage = Message.builder()
                .messageType(request.getMessageType())
                .channel(request.getChannel())
                .recipientEmail(request.getRecipientEmail())
                .content(request.getContent())
                .build();

        // Ejecutar caso de uso
        String requestId = "local-" + UUID.randomUUID();
        Message saved = processMessagePort.processMessage(domainMessage, requestId);

        // Construir respuesta HTTP
        ProcessMessageResponse response = ProcessMessageResponse.builder()
                .messageId(saved.getMessageId())
                .status(saved.getStatus())
                .message("Mensaje recibido y en cola de procesamiento")
                .createdAt(saved.getCreatedAt())
                .build();

        return ResponseEntity.accepted().body(response);  // 202 Accepted
    }
}
