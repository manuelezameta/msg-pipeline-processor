package com.msgpipeline.processor.application.port.in;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: ProcessMessagePort -- Puerto de Entrada
 * CAPA: Aplicacion -- Puerto de Entrada (Input Port)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * IMPLEMENTACION: ProcessMessageUseCase
 * CALLER: ProcessorHandler (Lambda/SQS ESM)
 * =========================================================================
 */
public interface ProcessMessagePort {

    /**
     * Procesa un mensaje recibido de SQS.
     *
     * FLUJO:
     *   1. Asignar status=PENDING + processedAt + TTL
     *   2. DynamoDB PutItem (persistir)
     *   3. SNS Publish (notificacion email)
     *   4. EventBridge PutEvents (MessageProcessed --> Audit Lambda)
     *
     * @param message   Mensaje del body del SQSEvent
     * @param requestId ID del request Lambda
     * @return          El mensaje persistido con status=PENDING
     */
    Message processMessage(Message message, String requestId);
}
