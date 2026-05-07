package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: NotificationPort — Puerto de Salida (SNS)
 * CAPA: Dominio — Puerto de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Puerto para publicar notificaciones en SNS.
 *
 * NUEVO EN SESIÓN 06 (vs Sesión 05):
 *   En Sesión 05, la notificación SNS era responsabilidad del Audit Lambda.
 *   En Sesión 06, el Processor Lambda publica directamente en SNS porque
 *   Step Functions coordina el flujo completo sin Audit Lambda adicional.
 *
 * IMPLEMENTACIONES:
 *   - SnsNotificationAdapter → perfil 'aws' (SNS real)
 *   - InMemoryNotificationAdapter → perfil 'local' (log en consola)
 * =========================================================================
 */
public interface NotificationPort {

    /**
     * Publica notificación de mensaje procesado en SNS.
     *
     * @param message El mensaje guardado en DynamoDB
     */
    void notificar(Message message);
}
