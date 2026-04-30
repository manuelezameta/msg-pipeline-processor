package com.msgpipeline.processor.adapter.out.event;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.EventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * =========================================================================
 * CLASE: InMemoryEventAdapter — Adaptador de Salida (Memoria)
 * CAPA: Infraestructura — Adaptador de Salida (Output Adapter)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * Implementación local de EventPublisherPort sin EventBridge real.
 * Simula la publicación de eventos en memoria para desarrollo.
 *
 * @Profile("local"): Solo activo en desarrollo local.
 *
 * PATRÓN: Strategy
 *   Spring inyecta esta implementación en 'local' o EventBridgeAdapter en 'aws'.
 *   ProcessMessageUseCase no sabe cuál está activa — trabaja con la interfaz.
 *
 * ¿POR QUÉ SIMULAR EN LOCAL?
 *   Permite desarrollar y probar sin necesidad de:
 *     - Cuenta de AWS
 *     - EventBridge configurado
 *     - Conectividad a internet
 *   El flujo local completo funciona con InMemory para repos y eventos.
 * =========================================================================
 */
@Slf4j
@Component
@Profile("local")
public class InMemoryEventAdapter implements EventPublisherPort {

    // Almacena los eventos publicados para verificación en tests
    private final List<PublishedEvent> publishedEvents = new ArrayList<>();

    @Override
    public String publishMessageReceived(Message message) {
        String eventId = "local-event-" + UUID.randomUUID();

        log.info("[LOCAL] Simulando publicación en EventBridge:");
        log.info("[LOCAL]   source:      msg-pipeline.processor");
        log.info("[LOCAL]   detail-type: MessageReceived");
        log.info("[LOCAL]   eventBusName: msg-pipeline-events-sesion-05 (SIMULADO)");
        log.info("[LOCAL]   messageId:   {}", message.getMessageId());
        log.info("[LOCAL]   status:      {}", message.getStatus());
        log.info("[LOCAL]   eventId:     {} (GENERADO LOCALMENTE)", eventId);
        log.info("[LOCAL] En AWS real: EventBridge → Rule → Audit Lambda (msg-pipeline-audit-sesion-05)");

        publishedEvents.add(new PublishedEvent(eventId, message.getMessageId(), "MessageReceived"));

        return eventId;
    }

    /** Retorna los eventos publicados (útil para tests) */
    public List<PublishedEvent> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    /** Limpia los eventos publicados (útil entre tests) */
    public void clear() {
        publishedEvents.clear();
    }

    /** Representa un evento publicado localmente */
    public record PublishedEvent(String eventId, String messageId, String detailType) {}
}
