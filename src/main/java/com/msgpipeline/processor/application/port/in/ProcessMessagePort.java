package com.msgpipeline.processor.application.port.in;

import com.msgpipeline.processor.domain.model.Message;

/**
 * Puerto de entrada del Processor Lambda.
 * Define el contrato del caso de uso de procesamiento.
 */
public interface ProcessMessagePort {

    /**
     * Procesa un mensaje recibido de Step Functions.
     *
     * FLUJO:
     *   1. Asigna status=PENDING y timestamps
     *   2. Calcula TTL (30 días)
     *   3. Persiste en DynamoDB (PutItem)
     *   4. Publica en SNS (Publish)
     *   5. Retorna Message guardado
     *
     * @param payload   Message con los datos del input de Step Functions
     * @param requestId ID del request Lambda (para logs de CloudWatch)
     * @return          Message persistido con status=PENDING
     */
    Message processMessage(Message payload, String requestId);
}
