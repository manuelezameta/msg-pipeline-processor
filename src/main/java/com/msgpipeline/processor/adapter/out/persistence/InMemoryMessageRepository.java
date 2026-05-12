package com.msgpipeline.processor.adapter.out.persistence;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/** Repositorio en memoria para el perfil local. */
@Slf4j
@Repository
@Profile("local")
public class InMemoryMessageRepository implements MessageRepository {

    private static final Map<String, Message> store = new HashMap<>();

    @Override
    public Message save(Message message) {
        store.put(message.getMessageId(), message);
        log.info("[LOCAL] DynamoDB PutItem simulado [messageId={}] [status={}]",
                message.getMessageId(), message.getStatus());
        return message;
    }
}
