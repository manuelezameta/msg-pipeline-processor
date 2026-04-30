package com.msgpipeline.processor;

import com.msgpipeline.processor.application.usecase.ProcessMessageUseCase;
import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.EventPublisherPort;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del ProcessMessageUseCase.
 * Prueba la lógica de negocio de forma aislada (sin DynamoDB ni EventBridge real).
 * Usa Mockito para simular los puertos de salida.
 */
@ExtendWith(MockitoExtension.class)
class ProcessMessageUseCaseTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private EventPublisherPort eventPublisherPort;

    @InjectMocks
    private ProcessMessageUseCase useCase;

    @Test
    void processMessage_debeRetornarMensajeConStatusPending() {
        // Arrange
        Message payload = Message.builder()
                .messageType("EMAIL")
                .channel("EMAIL")
                .recipientEmail("test@ejemplo.com")
                .content("Mensaje de prueba Sesión 05")
                .build();

        when(messageRepository.save(any(Message.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventPublisherPort.publishMessageReceived(any(Message.class)))
                .thenReturn("test-event-id");

        // Act
        Message result = useCase.processMessage(payload, "test-request-id");

        // Assert
        assertThat(result.getMessageId()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getCreatedAt()).isNotBlank();
        assertThat(result.getTtl()).isPositive();
        assertThat(result.getRequestId()).isEqualTo("test-request-id");
    }
}
