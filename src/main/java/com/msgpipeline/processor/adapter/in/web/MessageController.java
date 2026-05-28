package com.msgpipeline.processor.adapter.in.web;

import com.msgpipeline.processor.adapter.in.web.dto.ProcessMessageRequest;
import com.msgpipeline.processor.adapter.in.web.dto.ProcessMessageResponse;
import com.msgpipeline.processor.adapter.out.persistence.InMemoryMessageRepository;
import com.msgpipeline.processor.application.port.in.ProcessMessagePort;
import com.msgpipeline.processor.domain.model.Message;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * =========================================================================
 * CAPA: Infraestructura — Adaptador de Entrada Web (Input Adapter)
 * ARQUITECTURA: Hexagonal
 * PERFIL: 'local' (solo disponible en desarrollo local)
 * =========================================================================
 *
 * PATRÓN: Adapter (Input/Driving Adapter)
 *   Este controlador ADAPTA las solicitudes HTTP al contrato definido
 *   por el puerto de entrada (ProcessMessagePort). Convierte:
 *     HTTP Request → ProcessMessageRequest → Message (dominio)
 *     Message (dominio) → ProcessMessageResponse → HTTP Response
 *
 * PROPÓSITO:
 *   Permite probar el procesador localmente via Swagger UI sin necesidad
 *   de configurar SQS ni AWS. Simula el payload que normalmente llegaría
 *   desde la cola SQS.
 *
 * @Profile("local"):
 *   Este bean SOLO existe en el perfil 'local'. En Lambda (perfil 'aws')
 *   Spring no registra este controlador — no hay servidor web activo.
 *
 * SWAGGER UI — Anotaciones:
 *   @Tag         → agrupa los endpoints en Swagger UI
 *   @Operation   → describe cada endpoint
 *   @ApiResponse → documenta las posibles respuestas HTTP
 *
 * URL Swagger: http://localhost:8082/swagger-ui.html
 * =========================================================================
 */
@Slf4j
@RestController
@Profile("local")
@RequestMapping("/api/v1/messages-s3")
@RequiredArgsConstructor
@Tag(name = "Procesador de Mensajes",
     description = "Endpoints para probar localmente el procesador SQS → DynamoDB. " +
                   "⚠️ Solo disponible en perfil 'local' (no existe en Lambda)")
public class MessageController {

    // Puerto de entrada — el mismo que usa SqsHandler en Lambda
    // Spring inyecta ProcessMessageUseCase (la única implementación)
    private final ProcessMessagePort processMessagePort;

    // Repositorio en memoria para el endpoint GET /messages
    // Solo disponible en perfil 'local'
    private final InMemoryMessageRepository inMemoryRepository;

    /**
     * POST /api/v1/messages
     *
     * Simula el procesamiento de un mensaje SQS.
     * En producción, este flujo lo inicia SQS automáticamente.
     * Aquí lo iniciamos manualmente via HTTP para pruebas.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Procesar mensaje (simula evento SQS)",
        description = "Recibe un mensaje, ejecuta el caso de uso de procesamiento " +
                      "y lo persiste en memoria (perfil local) o DynamoDB (perfil aws). " +
                      "Simula exactamente lo que hace el SqsHandler al recibir un evento SQS."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Mensaje procesado y persistido exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @ApiResponse(responseCode = "500", description = "Error interno durante el procesamiento")
    })
    public ResponseEntity<ProcessMessageResponse> processMessage(
            @Valid @RequestBody ProcessMessageRequest request) {

        log.info("Solicitud HTTP recibida [tipo={}] [canal={}]",
                request.getMessageType(), request.getChannel());

        // ── Convertir DTO → Entidad de Dominio ───────────────────────────
        //
        // El controlador NO pasa el DTO directamente al Use Case.
        // Primero convierte al modelo del dominio. Esto desacopla la
        // capa de presentación (HTTP) de la capa de negocio.
        //
        Message domainMessage = Message.builder()
                .messageType(request.getMessageType())
                .channel(request.getChannel())
                .recipientEmail(request.getRecipientEmail())
                .content(request.getContent())
                .build();

        // Generamos un ID de SQS simulado para local testing
        String simulatedSqsId = "local-" + UUID.randomUUID();

        // ── Ejecutar el caso de uso ───────────────────────────────────────
        //
        // El controlador delega la lógica de negocio al puerto de entrada.
        // No hay lógica de negocio en el controlador — solo conversión y orquestación.
        //
        Message processed = processMessagePort.processMessage(domainMessage, simulatedSqsId);

        // ── Convertir Entidad de Dominio → DTO de Respuesta ──────────────
        ProcessMessageResponse response = ProcessMessageResponse.builder()
                .messageId(processed.getMessageId())
                .status(processed.getStatus())
                .processedAt(processed.getProcessedAt())
                .mensaje("Mensaje procesado exitosamente (perfil local — usando InMemoryRepository)")
                .build();

        log.info("Respuesta enviada [messageId={}] [status={}]",
                response.getMessageId(), response.getStatus());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/messages
     *
     * Consulta todos los mensajes procesados en memoria (solo perfil 'local').
     * Útil para verificar que el procesamiento funcionó correctamente.
     */
    @GetMapping
    @Operation(
        summary = "Listar mensajes procesados (solo local)",
        description = "Retorna todos los mensajes guardados en memoria durante la sesión actual. " +
                      "Los datos se pierden al reiniciar la aplicación."
    )
    @ApiResponse(responseCode = "200", description = "Lista de mensajes procesados")
    public ResponseEntity<List<Message>> getAllMessages() {
        List<Message> messages = inMemoryRepository.findAll();
        log.info("Consultando mensajes en memoria. Total: {}", messages.size());
        return ResponseEntity.ok(messages);
    }
}
