package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: EventPublisherPort -- Puerto de Salida (EventBridge PutEvents)
 * CAPA: Dominio -- Puerto de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Publica eventos MessageProcessed en EventBridge para disparar el Audit Lambda.
 *
 * CONFIGURACION EVENTBRIDGE:
 *   Bus:        msg-pipeline-events-sesion-07
 *   Source:     com.msgpipeline.processor
 *   DetailType: MessageProcessed
 *   Rule:       msg-pipeline-audit-rule-sesion-07 --> Target: Audit Lambda
 *
 * IMPLEMENTACIONES:
 *   - EventBridgePublisherAdapter --> perfil 'aws'
 *   - InMemoryEventBridgeAdapter  --> perfil 'local'
 * =========================================================================
 */
public interface EventPublisherPort {
    /**
     * Publica el evento MessageProcessed.
     * La regla EventBridge captura el evento y dispara el Audit Lambda.
     */
    void publicarMensajeProcesado(Message message);
}
