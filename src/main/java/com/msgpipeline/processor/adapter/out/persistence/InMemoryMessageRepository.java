package com.msgpipeline.processor.adapter.out.persistence;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio en memoria para desarrollo local.
 * @Profile("local"): sin DynamoDB real.
 */
@Slf4j
@Repository
@Profile("local")
public class InMemoryMessageRepository implements MessageRepository {

    private final Map<String, Message> storage = new ConcurrentHashMap<>();

    @Override
    public Message save(Message message) {
        log.info("[LOCAL] Guardando en memoria [messageId={}] [status={}]",
                message.getMessageId(), message.getStatus());
        storage.put(message.getMessageId(), message);
        return message;
    }
}
