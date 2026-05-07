package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: MessageRepository — Puerto de Salida (DynamoDB)
 * CAPA: Dominio — Puerto de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Puerto para persistir mensajes en DynamoDB.
 *
 * IMPLEMENTACIONES:
 *   - DynamoMessageRepository → perfil 'aws' (DynamoDB real)
 *   - InMemoryMessageRepository → perfil 'local' (en memoria)
 * =========================================================================
 */
public interface MessageRepository {

    /**
     * Persiste un mensaje en DynamoDB con PutItem.
     * El messageId es el Partition Key.
     *
     * @param message Mensaje a persistir con status=PENDING
     * @return        El mensaje persistido
     */
    Message save(Message message);
}
