package com.msgpipeline.processor.domain.port.out;

import com.msgpipeline.processor.domain.model.Message;

/**
 * =========================================================================
 * CAPA: Dominio — Puerto de Salida (Output Port)
 * ARQUITECTURA: Hexagonal (Ports & Adapters)
 * =========================================================================
 *
 * CONCEPTO CLAVE — ¿Qué es un Puerto?
 *   Un "puerto" es una INTERFAZ que define cómo el dominio interactúa
 *   con el mundo exterior. El dominio declara QUÉ necesita, no CÓMO
 *   se implementa. Esto es la esencia de la Inversión de Dependencias.
 *
 * PATRÓN: Repository (Puerto de Salida)
 *   Define las operaciones de persistencia desde el punto de vista del
 *   dominio. El dominio solo conoce esta interfaz, nunca la implementación
 *   concreta (DynamoDB, H2, archivo, etc.)
 *
 * PATRÓN: Dependency Inversion Principle (DIP — SOLID)
 *   El módulo de alto nivel (dominio) NO depende de los módulos de bajo
 *   nivel (infraestructura). Ambos dependen de abstracciones (esta interfaz).
 *
 *   Sin DIP:   Use Case → DynamoMessageRepository  (acoplamiento fuerte)
 *   Con DIP:   Use Case → MessageRepository ← DynamoMessageRepository
 *
 * BENEFICIO CONCRETO:
 *   En el perfil 'local', Spring inyecta InMemoryMessageRepository.
 *   En el perfil 'aws',   Spring inyecta DynamoMessageRepository.
 *   El Use Case funciona igual sin cambiar una sola línea de código.
 *
 * PATRÓN: Strategy (implícito)
 *   La interfaz MessageRepository actúa como Strategy. Los adaptadores
 *   (DynamoDB, InMemory) son las estrategias concretas intercambiables.
 * =========================================================================
 */
public interface MessageRepository {

    /**
     * Persiste un mensaje en el almacenamiento configurado.
     *
     * @param message Entidad de dominio a persistir
     * @return El mensaje persistido (puede incluir datos generados por el store)
     */
    Message save(Message message);
}
