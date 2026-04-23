package com.msgpipeline.processor.adapter.out.persistence;

import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =========================================================================
 * CAPA: Infraestructura — Adaptador de Salida (Output Adapter)
 * ARQUITECTURA: Hexagonal
 * PERFIL: 'local' (activo en desarrollo local con macOS)
 * =========================================================================
 *
 * PATRÓN: Null Object / Test Double (In-Memory Repository)
 *   Implementación alternativa del puerto MessageRepository que almacena
 *   los mensajes en memoria (Map). No requiere conexión a AWS DynamoDB.
 *   Permite desarrollar y probar localmente sin credenciales AWS.
 *
 * PATRÓN: Strategy (intercambiable con DynamoMessageRepository)
 *   Ambas implementaciones cumplen el mismo contrato (MessageRepository).
 *   Spring selecciona la implementación correcta según el perfil activo.
 *   El Use Case ejecuta exactamente el mismo código en ambos perfiles.
 *
 * PATRÓN: Singleton (implícito por @Repository)
 *   Spring crea UNA sola instancia de este repositorio.
 *   ConcurrentHashMap garantiza acceso seguro en entornos multi-hilo.
 *
 * @Profile("local"):
 *   SOLO activo en el perfil 'local'. En 'aws', Spring usa DynamoMessageRepository.
 * =========================================================================
 */
@Slf4j
@Repository
@Profile("local")
public class InMemoryMessageRepository implements MessageRepository {

    /**
     * Almacenamiento en memoria: messageId → Message
     * ConcurrentHashMap: thread-safe para entornos multi-hilo de Spring.
     * Los datos se pierden al reiniciar la aplicación (comportamiento esperado).
     */
    private final Map<String, Message> store = new ConcurrentHashMap<>();

    /**
     * Guarda el mensaje en el Map en memoria.
     * Simula el comportamiento de DynamoDB PutItem para pruebas locales.
     *
     * @param message Mensaje a persistir
     * @return El mismo mensaje (simula el retorno de DynamoDB)
     */
    @Override
    public Message save(Message message) {
        log.info("[IN-MEMORY] Guardando mensaje [id={}] [status={}]",
                message.getMessageId(), message.getStatus());

        store.put(message.getMessageId(), message);

        log.info("[IN-MEMORY] Mensaje guardado. Total en memoria: {}", store.size());

        return message;
    }

    /**
     * Devuelve todos los mensajes almacenados en memoria.
     * Útil para el endpoint GET /api/v1/messages (solo disponible en local).
     *
     * @return Lista inmutable de todos los mensajes
     */
    public List<Message> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }
}
