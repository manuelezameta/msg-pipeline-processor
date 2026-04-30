package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: EventPublisherPort — Puerto de Salida para Eventos
 * CAPA: Dominio — Puerto de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * NUEVO EN SESIÓN 05: Publicación de eventos de dominio a EventBridge.
 *
 * PATRÓN: Observer (variante Event-Driven)
 *   El Processor (Sujeto/Publisher) no sabe quiénes son los Observadores.
 *   Solo publica el evento. EventBridge enruta el evento a los targets:
 *     → Audit Lambda (msg-pipeline-audit-sesion-05)
 *     → Futuros consumidores (sin modificar el Processor)
 *
 * PRINCIPIO DE RESPONSABILIDAD ÚNICA (SRP — SOLID):
 *   Este puerto solo se ocupa de publicar eventos de dominio.
 *   No mezcla persistencia, notificaciones ni lógica de negocio.
 *
 * IMPLEMENTACIONES:
 *   - EventBridgeAdapter  → perfil 'aws' (EventBridge real)
 *   - InMemoryEventAdapter → perfil 'local' (log en memoria)
 *
 * FLUJO SESIÓN 05:
 *   ProcessMessageUseCase → publishEvent() → EventBridgeAdapter → AWS EventBridge
 *   EventBridge → Rule (patrón source=msg-pipeline.processor) → Audit Lambda
 * =========================================================================
 */
public interface EventPublisherPort {

    /**
     * Publica un evento de dominio notificando que un mensaje fue recibido.
     *
     * Formato del evento en EventBridge:
     * {
     *   "source": "msg-pipeline.processor",
     *   "detail-type": "MessageReceived",
     *   "detail": {
     *     "messageId": "...",
     *     "messageType": "EMAIL",
     *     "status": "PENDING",
     *     "timestamp": "2025-..."
     *   }
     * }
     *
     * @param message El mensaje persistido en DynamoDB con status=PENDING
     * @return        El ID del evento publicado en EventBridge
     */
    String publishMessageReceived(Message message);
}
