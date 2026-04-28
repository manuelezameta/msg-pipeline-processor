package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * CAPA: Dominio — Puerto de Salida (Output Port)
 * ARQUITECTURA: Hexagonal (Ports & Adapters)
 * SESIÓN: 04 — NUEVO (integración SNS + Patrón Observer)
 * =========================================================================
 *
 * Define cómo el dominio publica notificaciones de procesamiento completado.
 * El dominio NO sabe si la notificación va por SNS, email directo, Kafka, etc.
 * Solo conoce este contrato.
 *
 * PATRÓN: Observer (via SNS como bus de eventos)
 *   El dominio (Observable) publica el evento "mensaje procesado".
 *   Los suscriptores de SNS (email, SMS, Lambda) reaccionan automáticamente.
 *   El procesador NO sabe quiénes son los suscriptores — desacoplamiento total.
 *
 *   Sin Observer: ProcessMessageUseCase → send email directo (acoplamiento fuerte)
 *   Con Observer: ProcessMessageUseCase → NotificationPort ← SnsNotificationAdapter
 *                                                         → SNS → [email, SMS, ...]
 *
 * PATRÓN: Dependency Inversion (DIP — SOLID)
 *   El módulo de alto nivel (Use Case) no depende de SNS directamente.
 *   Ambos dependen de esta abstracción (NotificationPort).
 *
 * PATRÓN: Strategy (intercambiable entre perfiles)
 *   @Profile("aws")   → SnsNotificationAdapter (SNS real en AWS)
 *   @Profile("local") → InMemoryNotificationAdapter (memoria para desarrollo)
 *
 * PRINCIPIO: Interface Segregation (ISP — SOLID)
 *   Interfaz mínima y específica para notificaciones — una sola operación.
 *
 * EXTENSIBILIDAD (OCP — Open/Closed):
 *   Para agregar notificaciones por Slack, WebSocket, etc.:
 *   → Crear nueva implementación de NotificationPort
 *   → Registrar como bean en el perfil correspondiente
 *   → NO modificar ProcessMessageUseCase ni esta interfaz
 * =========================================================================
 */
public interface NotificationPort {

    /**
     * Publica una notificación de procesamiento completado.
     *
     * En producción (perfil 'aws'): publica en el tópico SNS.
     *   SNS distribuye a todos los suscriptores (email, SMS, Lambda, etc.)
     * En desarrollo (perfil 'local'): registra en logs y en memoria.
     *
     * IMPLEMENTACIÓN NO BLOQUEANTE:
     *   Si la notificación falla, el procesamiento ya fue completado.
     *   Las implementaciones deben capturar excepciones internamente y loggear.
     *   No lanzar excepciones que interrumpan el flujo principal.
     *
     * @param message Mensaje procesado exitosamente (con messageId, status=COMPLETED)
     */
    void notificarProcesamiento(Message message);
}
