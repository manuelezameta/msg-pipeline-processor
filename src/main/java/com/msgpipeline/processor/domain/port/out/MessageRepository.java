package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: MessageRepository -- Puerto de Salida (DynamoDB PutItem)
 * CAPA: Dominio -- Puerto de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * IMPLEMENTACIONES:
 *   - DynamoMessageRepository   --> perfil 'aws' (DynamoDB real)
 *   - InMemoryMessageRepository --> perfil 'local' (en memoria)
 * =========================================================================
 */
public interface MessageRepository {
    /**
     * Persiste el mensaje en DynamoDB con PutItem (status=PENDING).
     * @param message Mensaje a persistir
     * @return        El mensaje persistido
     */
    Message save(Message message);
}
