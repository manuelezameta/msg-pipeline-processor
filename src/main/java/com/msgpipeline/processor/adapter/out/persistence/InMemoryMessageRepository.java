package com.msgpipeline.processor.adapter.out.persistence;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =========================================================================
 * CLASE: InMemoryMessageRepository — Adaptador de Salida (Memoria)
 * CAPA: Infraestructura — Adaptador de Salida (Output Adapter)
 * ARQUITECTURA: Hexagonal
 * =========================================================================
 *
 * PATRÓN: Repository (Adapter de persistencia en memoria)
 *   Implementa MessageRepository sin AWS DynamoDB.
 *   Usada en perfil 'local' para desarrollo sin necesidad de AWS.
 *
 * @Profile("local"): Solo activa en desarrollo local.
 *   Permite probar el flujo completo sin configurar AWS.
 *
 * PATRÓN: Strategy
 *   Spring inyecta esta implementación (en 'local') o DynamoMessageRepository
 *   (en 'aws'). ProcessMessageUseCase no sabe cuál está activa.
 *
 * ConcurrentHashMap: thread-safe para entornos con concurrencia.
 *   En Lambda esto es menos relevante (una instancia por invocación),
 *   pero es buena práctica en aplicaciones web locales.
 * =========================================================================
 */
@Slf4j
@Repository
@Profile("local")
public class InMemoryMessageRepository implements MessageRepository {

    // Almacenamiento en memoria: messageId → Message
    private final Map<String, Message> storage = new ConcurrentHashMap<>();

    @Override
    public Message save(Message message) {
        log.info("[LOCAL] Guardando mensaje en memoria [messageId={}] [status={}]",
                message.getMessageId(), message.getStatus());

        storage.put(message.getMessageId(), message);

        log.info("[LOCAL] Mensaje guardado. Total en memoria: {}", storage.size());
        log.debug("[LOCAL] Mensaje: messageType={}, channel={}, recipientEmail={}",
                message.getMessageType(), message.getChannel(), message.getRecipientEmail());

        return message;
    }

    /**
     * Retorna todos los mensajes almacenados (útil para tests y debugging local).
     */
    public List<Message> findAll() {
        return new ArrayList<>(storage.values());
    }

    /**
     * Retorna un mensaje por ID (útil para tests).
     */
    public Message findById(String messageId) {
        return storage.get(messageId);
    }

    /**
     * Limpia el almacenamiento (útil entre tests).
     */
    public void clear() {
        storage.clear();
    }
}
