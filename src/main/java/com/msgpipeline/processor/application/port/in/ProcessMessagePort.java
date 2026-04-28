package com.msgpipeline.processor.application.port.in;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * CAPA: Aplicación — Puerto de Entrada (Input Port / Use Case Interface)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * CONCEPTO — Puerto de Entrada:
 *   Define las OPERACIONES que el dominio expone hacia el exterior.
 *   Los adaptadores de entrada (SqsHandler, MessageController) usan
 *   esta interfaz para interactuar con la lógica de negocio.
 *
 * PATRÓN: Use Case Interface (Application Port)
 *   En arquitectura limpia, cada caso de uso tiene una interfaz que
 *   describe su contrato. Esto permite:
 *     1. Reemplazar la implementación sin afectar a los adaptadores
 *     2. Mockear fácilmente en tests unitarios
 *     3. Documentar la intención de negocio de forma explícita
 *
 * DIAGRAMA:
 *
 *   [SQS Event]            [HTTP Request]
 *        │                      │
 *   [SqsHandler]        [MessageController]
 *        │                      │
 *        └──────────┬───────────┘
 *                   │
 *          [ProcessMessagePort]  ← esta interfaz
 *                   │
 *          [ProcessMessageUseCase]
 *                   │
 *          [MessageRepository]
 *                   │
 *        ┌──────────┴──────────┐
 *  [DynamoRepo]         [InMemoryRepo]
 * =========================================================================
 */
public interface ProcessMessagePort {

    /**
     * Procesa un mensaje recibido desde la cola SQS.
     *
     * Responsabilidades:
     *   1. Asignar ID único si no viene en el payload
     *   2. Calcular TTL para expiración automática en DynamoDB
     *   3. Persistir el mensaje con estado COMPLETED
     *   4. Registrar el timestamp de procesamiento
     *
     * @param payload  Mensaje con los datos provenientes del evento SQS
     * @param sqsId    ID del mensaje SQS original (para trazabilidad)
     * @return         Mensaje persistido con todos los campos populados
     */
    Message processMessage(Message payload, String sqsId);
}
