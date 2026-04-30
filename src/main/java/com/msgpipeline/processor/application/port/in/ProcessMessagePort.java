package com.msgpipeline.processor.application.port.in;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: ProcessMessagePort — Puerto de Entrada (Input Port)
 * CAPA: Aplicación — Puerto de Entrada
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Define el CONTRATO del caso de uso de procesamiento de mensajes.
 *
 * PATRÓN: Use Case Interface (Puerto de Entrada)
 *   - Define QUÉ puede hacer el sistema desde afuera
 *   - El handler de Lambda lo implementa llamando a este puerto
 *   - El Use Case (ProcessMessageUseCase) lo implementa con lógica de negocio
 *
 * PRINCIPIO DE SEGREGACIÓN DE INTERFACES (ISP — SOLID):
 *   Cada puerto tiene una responsabilidad específica y limitada.
 *   ProcessorHandler solo necesita processMessage() — no necesita
 *   conocer los detalles de DynamoDB ni EventBridge.
 *
 * IMPLEMENTACIÓN: ProcessMessageUseCase (capa application/usecase)
 * =========================================================================
 */
public interface ProcessMessagePort {

    /**
     * Procesa un mensaje recibido desde API Gateway.
     *
     * FLUJO INTERNO (implementado en ProcessMessageUseCase):
     *   1. Generar UUID para messageId
     *   2. Construir entidad Message con status=PENDING
     *   3. Calcular TTL (30 días desde ahora)
     *   4. Persistir en DynamoDB (MessageRepository)
     *   5. Publicar evento en EventBridge (EventPublisherPort)
     *   6. Retornar Message guardado con todos los campos calculados
     *
     * @param payload   Datos del mensaje del request HTTP (sin ID, sin timestamps)
     * @param requestId ID del request API Gateway (para trazabilidad)
     * @return          Mensaje persistido con status=PENDING y event publicado
     */
    Message processMessage(Message payload, String requestId);
}
