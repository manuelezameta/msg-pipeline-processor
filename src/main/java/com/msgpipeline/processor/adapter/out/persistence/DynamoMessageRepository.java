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
 * CAPA: Infraestructura — Adaptador de Salida (Output Adapter)
 * ARQUITECTURA: Hexagonal (Ports & Adapters)
 * PERFIL: 'aws' (activo en AWS Lambda)
 * =========================================================================
 *
 * PATRÓN: Repository (Adaptador de Salida)
 *   Implementa el puerto MessageRepository usando DynamoDB como
 *   mecanismo de persistencia. El dominio no sabe que existe DynamoDB.
 *
 * PATRÓN: Adapter (GoF — Gang of Four)
 *   Esta clase ADAPTA la interfaz del AWS SDK de DynamoDB
 *   al contrato que define el puerto MessageRepository.
 *   Es el "puente" entre el mundo de Spring/dominio y el mundo de AWS.
 *
 * PATRÓN: Dependency Inversion (DIP — SOLID)
 *   La clase implementa MessageRepository (interfaz del dominio).
 *   El dominio define el contrato; la infraestructura lo cumple.
 *   El dominio nunca importa DynamoMessageRepository directamente.
 *
 * @Profile("aws"):
 *   Spring SOLO registra este bean cuando el perfil 'aws' está activo.
 *   En el perfil 'local', Spring registra InMemoryMessageRepository.
 *   Es el mecanismo de INVERSIÓN DE CONTROL por perfil.
 *
 * @Repository:
 *   Especialización de @Component que indica a Spring que este bean
 *   es un repositorio de datos. También activa la traducción de
 *   excepciones de persistencia a excepciones de Spring.
 * =========================================================================
 */
@Slf4j
@Repository
@Profile("aws")
@RequiredArgsConstructor
public class DynamoMessageRepository implements MessageRepository {

    // DynamoDbClient inyectado por Spring (configurado en DynamoConfig)
    private final DynamoDbClient dynamoDbClient;

    // Nombre de la tabla DynamoDB inyectado desde application.yml
    // → variable de entorno DYNAMODB_TABLE_NAME en Lambda
    private final String tableName;

    /**
     * Persiste el mensaje en DynamoDB usando PutItem.
     *
     * PutItem: inserta un ítem NUEVO o REEMPLAZA uno existente con la misma PK.
     * Si ya existe un ítem con el mismo messageId, lo sobreescribe completo.
     *
     * ESTRUCTURA DEL ÍTEM EN DYNAMODB:
     *
     * Partition Key: messageId (String)
     * Atributos:
     *   messageType    → tipo de mensaje (EMAIL, SMS, PUSH)
     *   channel        → canal de entrega
     *   recipientEmail → destinatario
     *   content        → cuerpo del mensaje
     *   status         → COMPLETED (establecido por el Use Case)
     *   createdAt      → ISO-8601 timestamp de creación
     *   processedAt    → ISO-8601 timestamp de procesamiento
     *   sqsMessageId   → ID del mensaje SQS (trazabilidad)
     *   ttl            → segundos Unix epoch para expiración automática
     *
     * TIPOS DE ATRIBUTOS DYNAMODB:
     *   S  = String
     *   N  = Number (se pasa como String, DynamoDB lo almacena como número)
     *   B  = Binary
     *   BOOL = Boolean
     *   L  = List
     *   M  = Map
     *
     * @param message Entidad de dominio a persistir
     * @return El mismo mensaje (DynamoDB PutItem no devuelve el ítem completo)
     */
    @Override
    public Message save(Message message) {
        log.info("Guardando mensaje en DynamoDB [id={}] [tabla={}]",
                message.getMessageId(), tableName);

        // ── Construir el mapa de atributos DynamoDB ───────────────────────
        //
        // DynamoDB trabaja con Map<String, AttributeValue>.
        // AttributeValue es un tipo "discriminado": debes indicar el tipo
        // del valor (S, N, BOOL, etc.) al construirlo.
        //
        Map<String, AttributeValue> item = new HashMap<>();

        // Partition Key — campo obligatorio, identifica el ítem únicamente
        item.put("messageId",    s(message.getMessageId()));

        // Atributos del mensaje
        item.put("messageType",    s(message.getMessageType()));
        item.put("channel",        s(message.getChannel()));
        item.put("recipientEmail", s(message.getRecipientEmail()));
        item.put("content",        s(message.getContent()));
        item.put("status",         s(message.getStatus()));
        item.put("createdAt",      s(message.getCreatedAt()));
        item.put("processedAt",    s(message.getProcessedAt()));
        item.put("sqsMessageId",   s(message.getSqsMessageId()));

        // TTL: tipo Number en DynamoDB
        // DynamoDB usa este campo para la expiración automática
        // (debe estar habilitado en la tabla — ver Paso 1 de la sesión)
        if (message.getTtl() != null) {
            item.put("ttl", AttributeValue.builder()
                    .n(message.getTtl().toString())
                    .build());
        }

        // ── Ejecutar PutItem ──────────────────────────────────────────────
        //
        // PutItemRequest: construido con el patrón Builder del SDK v2.
        // El SDK v2 de AWS usa el patrón Builder extensivamente.
        //
        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);

        log.info("Mensaje guardado exitosamente en DynamoDB [id={}] [status={}]",
                message.getMessageId(), message.getStatus());

        return message;
    }

    // ── Helper privado ────────────────────────────────────────────────────

    /**
     * Crea un AttributeValue de tipo String (S) de forma conveniente.
     * Si el valor es null, guarda una cadena vacía para evitar
     * NullPointerException en el SDK de DynamoDB.
     *
     * @param value Valor String a convertir
     * @return AttributeValue con tipo S
     */
    private AttributeValue s(String value) {
        return AttributeValue.builder()
                .s(value != null ? value : "")
                .build();
    }
}
