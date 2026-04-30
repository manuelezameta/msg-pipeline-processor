package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: MessageRepository — Puerto de Salida (Output Port)
 * CAPA: Dominio — Puerto de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * PRINCIPIO DE INVERSIÓN DE DEPENDENCIAS (DIP — SOLID):
 *   El dominio define QUÉ necesita (guardar un mensaje) pero NO CÓMO.
 *   La implementación concreta (DynamoDB o en memoria) vive en la capa
 *   de infraestructura (adapters/out).
 *
 * IMPLEMENTACIONES:
 *   - DynamoMessageRepository → perfil 'aws' (producción en Lambda)
 *   - InMemoryMessageRepository → perfil 'local' (desarrollo en macOS)
 *
 * El caso de uso (ProcessMessageUseCase) solo conoce esta interfaz.
 * Puede cambiarse la base de datos sin modificar el negocio.
 * =========================================================================
 */
public interface MessageRepository {

    /**
     * Persiste un mensaje en el almacenamiento.
     *
     * En 'aws': PutItem en DynamoDB → msg-pipeline-messages
     * En 'local': guarda en un Map en memoria
     *
     * @param message Entidad de dominio a persistir
     * @return        El mensaje guardado (con campos calculados como TTL)
     */
    Message save(Message message);
}
