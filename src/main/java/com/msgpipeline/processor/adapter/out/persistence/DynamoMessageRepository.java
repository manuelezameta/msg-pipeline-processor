package com.msgpipeline.processor.adapter.out.persistence;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * =========================================================================
 * CLASE: DynamoMessageRepository — Adaptador de Salida (DynamoDB)
 * CAPA: Infraestructura — Adaptador de Salida (Output Adapter)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * PATRÓN: Repository (Adapter de persistencia)
 *   Implementa MessageRepository usando AWS DynamoDB SDK v2.
 *   El dominio no sabe que existe DynamoDB — solo conoce la interfaz.
 *
 * @Profile("aws"): Spring solo crea este bean cuando el perfil 'aws' está activo.
 *   En Lambda (perfil 'aws'): DynamoDB real en AWS
 *   En desarrollo (perfil 'local'): InMemoryMessageRepository (no DynamoDB)
 *
 * OPERACIÓN DYNAMODB: PutItem
 *   Crea un nuevo ítem o reemplaza uno existente con la misma PK.
 *   Partition Key: messageId (String)
 *   No se configura Sort Key — el messageId es único globalmente (UUID).
 *
 * TABLA: msg-pipeline-messages (configurada via variable de entorno)
 *   Capacidad: on-demand (recomendado para free tier)
 *
 * ESQUEMA DE ATRIBUTOS DYNAMODB:
 *   messageId    (String) — PK
 *   messageType  (String)
 *   channel      (String)
 *   recipientEmail (String)
 *   content      (String)
 *   status       (String) — PENDING al crear; COMPLETED cuando Audit actualice
 *   createdAt    (String) — ISO-8601
 *   requestId    (String) — ID del request API Gateway
 *   eventBusName (String) — Bus de EventBridge usado
 *   ttl          (Number) — Unix epoch seconds para expiración automática
 * =========================================================================
 */
@Slf4j
@Repository
@Profile("aws")
@RequiredArgsConstructor
public class DynamoMessageRepository implements MessageRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;  // Inyectado desde DynamoConfig

    /**
     * Persiste el mensaje en DynamoDB con PutItem.
     *
     * PutItem vs UpdateItem:
     *   - PutItem: crea o REEMPLAZA el ítem completo (idempotente para creates)
     *   - UpdateItem: actualiza atributos específicos de un ítem existente
     *   Usamos PutItem para la creación inicial con todos los atributos.
     *   El Audit Lambda usará UpdateItem para cambiar solo el status y processedAt.
     *
     * @param message Entidad del dominio a persistir
     * @return        El mismo mensaje recibido (DynamoDB no retorna el ítem en PutItem)
     */
    @Override
    public Message save(Message message) {
        log.info("Guardando mensaje en DynamoDB [messageId={}] [tabla={}] [status={}]",
                message.getMessageId(), tableName, message.getStatus());

        // ── Construir el mapa de atributos DynamoDB ───────────────────────
        //
        // DynamoDB usa AttributeValue en lugar de tipos Java nativos.
        // AttributeValue.fromS() → String
        // AttributeValue.fromN() → Number (TTL como string numérico)
        //
        Map<String, AttributeValue> item = new HashMap<>();

        // Partition Key — OBLIGATORIO y ÚNICO (UUID garantiza esto)
        item.put("messageId", AttributeValue.fromS(message.getMessageId()));

        // Atributos del mensaje
        putIfNotNull(item, "messageType", message.getMessageType());
        putIfNotNull(item, "channel", message.getChannel());
        putIfNotNull(item, "recipientEmail", message.getRecipientEmail());
        putIfNotNull(item, "content", message.getContent());

        // Estado inicial: PENDING (el Audit Lambda actualizará a COMPLETED)
        item.put("status", AttributeValue.fromS(message.getStatus()));

        // Timestamps
        putIfNotNull(item, "createdAt", message.getCreatedAt());

        // Trazabilidad
        putIfNotNull(item, "requestId", message.getRequestId());
        putIfNotNull(item, "eventBusName", message.getEventBusName());

        // TTL: DynamoDB lo usa para expirar el ítem automáticamente.
        // IMPORTANTE: debe ser tipo Number (N), no String (S).
        if (message.getTtl() != null) {
            item.put("ttl", AttributeValue.fromN(message.getTtl().toString()));
        }

        // ── Ejecutar PutItem ──────────────────────────────────────────────
        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);

        log.info("PutItem exitoso en DynamoDB [messageId={}]", message.getMessageId());

        return message;
    }

    // ── Método auxiliar ───────────────────────────────────────────────────

    /**
     * Agrega un atributo String al mapa solo si el valor no es null.
     * Evita guardar atributos vacíos en DynamoDB (ahorra almacenamiento).
     */
    private void putIfNotNull(Map<String, AttributeValue> item, String key, String value) {
        if (value != null && !value.isBlank()) {
            item.put(key, AttributeValue.fromS(value));
        }
    }
}
