package com.msgpipeline.processor.adapter.out.notification;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * =========================================================================
 * CAPA: Infraestructura — Adaptador de Salida (Output Adapter)
 * ARQUITECTURA: Hexagonal
 * PERFIL: 'local' (activo en desarrollo local con macOS)
 * SESIÓN: 04 — NUEVO
 * =========================================================================
 *
 * PATRÓN: Test Double / Null Object (In-Memory Notification)
 *   Implementación alternativa de NotificationPort que registra las
 *   notificaciones en memoria y en logs. No requiere conexión a AWS SNS.
 *   Permite desarrollar y probar localmente sin credenciales AWS.
 *
 * PATRÓN: Strategy (intercambiable con SnsNotificationAdapter)
 *   Ambas implementaciones cumplen el mismo contrato (NotificationPort).
 *   Spring selecciona la implementación correcta según el perfil activo.
 *   ProcessMessageUseCase ejecuta exactamente el mismo código en ambos perfiles.
 *
 * @Profile("local"):
 *   SOLO activo en el perfil 'local'. En 'aws', Spring usa SnsNotificationAdapter.
 *
 * VALOR EDUCATIVO:
 *   Al ver los logs "[IN-MEMORY SNS] Notificación registrada", el estudiante
 *   entiende que en producción este paso publica en SNS real.
 *   La abstracción NotificationPort hace transparente el cambio.
 * =========================================================================
 */
@Slf4j
@Component
@Profile("local")
public class InMemoryNotificationAdapter implements NotificationPort {

    /**
     * Historial de notificaciones simuladas en memoria.
     * CopyOnWriteArrayList: thread-safe para múltiples solicitudes HTTP concurrentes.
     * Los datos se pierden al reiniciar la aplicación (comportamiento esperado).
     */
    private final List<Message> notificaciones = new CopyOnWriteArrayList<>();

    /**
     * Simula la publicación en SNS registrando la notificación en memoria.
     * En producción (perfil 'aws'), este método llama a SnsClient.publish().
     *
     * @param message Mensaje procesado exitosamente a notificar
     */
    @Override
    public void notificarProcesamiento(Message message) {
        notificaciones.add(message);

        log.info("[IN-MEMORY SNS] Notificación registrada [messageId={}] [tipo={}] [status={}]",
                message.getMessageId(),
                message.getMessageType(),
                message.getStatus());

        log.info("[IN-MEMORY SNS] Simularía publicar en tópico 'msg-pipeline-email-notifications' | " +
                "Asunto: 'Mensaje Procesado — msg-pipeline [{}]' | Total notificaciones: {}",
                message.getMessageType(),
                notificaciones.size());
    }

    /**
     * Retorna todas las notificaciones registradas en memoria.
     * Útil para verificar en el GET /api/v1/messages que la notificación
     * se registró correctamente (solo disponible en perfil 'local').
     *
     * @return Lista inmutable de mensajes notificados
     */
    public List<Message> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(notificaciones));
    }
}
