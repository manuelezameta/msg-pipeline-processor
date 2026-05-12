package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * INTERFAZ: NotificationPort -- Puerto de Salida (SNS Publish)
 * CAPA: Dominio -- Puerto de Salida
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * PATRON OBSERVER: El Processor publica sin saber los suscriptores de SNS.
 *
 * IMPLEMENTACIONES:
 *   - SnsNotificationAdapter       --> perfil 'aws'
 *   - InMemoryNotificationAdapter  --> perfil 'local'
 * =========================================================================
 */
public interface NotificationPort {
    void notificarRecepcion(Message message);
}
