package com.msgpipeline.processor;

import com.msgpipeline.processor.application.usecase.ProcessMessageUseCase;
import com.msgpipeline.processor.domain.model.Message;
import com.msgpipeline.processor.domain.port.out.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * =========================================================================
 * TEST UNITARIO — ProcessMessageUseCase
 * =========================================================================
 *
 * PRINCIPIO: Los tests unitarios de la capa de aplicación (Use Cases) son
 * los más valiosos porque prueban la LÓGICA DE NEGOCIO pura, sin depender
 * de frameworks ni de infraestructura (DynamoDB, SQS, etc.).
 *
 * @ExtendWith(MockitoExtension.class):
 *   Activa Mockito para este test. Mockito permite crear objetos "simulados"
 *   (mocks) que imitan el comportamiento de las dependencias reales.
 *
 * @Mock: crea un mock de MessageRepository.
 *   El Use Case creerá que está hablando con DynamoDB, pero en realidad
 *   habla con un objeto simulado en memoria. Mucho más rápido y confiable.
 *
 * @InjectMocks: crea la instancia real de ProcessMessageUseCase
 *   e inyecta automáticamente los mocks declarados con @Mock.
 *
 * PATRÓN AAA (Arrange — Act — Assert):
 *   Arrange: preparar los datos y configurar los mocks
 *   Act:     ejecutar el método bajo prueba
 *   Assert:  verificar el resultado
 * =========================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessMessageUseCase — Casos de Uso del Procesador")
class ProcessMessageUseCaseTest {

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ProcessMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        // Inyectar el valor de ttlDays usando ReflectionTestUtils
        // (simula @Value("${app.processor.ttl-days:30}"))
        ReflectionTestUtils.setField(useCase, "ttlDays", 30);
    }

    @Test
    @DisplayName("processMessage → debe persistir con estado COMPLETED y generar ID si no viene")
    void processMessage_sinIdPrevio_debeGenerarIdYGuardar() {
        // ── Arrange ───────────────────────────────────────────────────────
        Message input = Message.builder()
                .messageType("EMAIL")
                .channel("CORREO_ELECTRONICO")
                .recipientEmail("test@ankuacademy.com")
                .content("Mensaje de prueba de Sesión 08")
                .build(); // Sin messageId

        // Configurar el mock: cuando se llame a save(), devolver el mismo mensaje
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ── Act ───────────────────────────────────────────────────────────
        Message result = useCase.processMessage(input, "sqs-test-id-001");

        // ── Assert ────────────────────────────────────────────────────────
        assertThat(result.getMessageId())
                .as("Debe generarse un UUID cuando no viene messageId")
                .isNotNull()
                .isNotBlank();

        assertThat(result.getStatus())
                .as("El estado debe ser COMPLETED tras el procesamiento")
                .isEqualTo("COMPLETED");

        assertThat(result.getProcessedAt())
                .as("El timestamp de procesamiento debe estar presente")
                .isNotNull();

        assertThat(result.getSqsMessageId())
                .as("Debe registrar el ID del mensaje SQS para trazabilidad")
                .isEqualTo("sqs-test-id-001");

        assertThat(result.getTtl())
                .as("El TTL debe ser un valor futuro (en segundos Unix epoch)")
                .isGreaterThan(System.currentTimeMillis() / 1000);

        // Verificar que se llamó al repositorio exactamente 1 vez
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    @DisplayName("processMessage → debe respetar el messageId si ya viene en el payload")
    void processMessage_conIdExistente_debeRespetar() {
        // ── Arrange ───────────────────────────────────────────────────────
        String idExistente = "mensaje-ya-tiene-id-001";
        Message input = Message.builder()
                .messageId(idExistente)
                .messageType("SMS")
                .channel("MOVIL")
                .content("Mensaje con ID pre-asignado")
                .build();

        when(messageRepository.save(any(Message.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // ── Act ───────────────────────────────────────────────────────────
        Message result = useCase.processMessage(input, "sqs-test-002");

        // ── Assert ────────────────────────────────────────────────────────
        assertThat(result.getMessageId())
                .as("Debe respetar el ID original del payload")
                .isEqualTo(idExistente);
    }

    @Test
    @DisplayName("processMessage → debe propagar excepción del repositorio")
    void processMessage_errorEnRepositorio_debePropagar() {
        // ── Arrange ───────────────────────────────────────────────────────
        Message input = Message.builder()
                .messageType("EMAIL")
                .content("Mensaje que causará error")
                .build();

        // Simular fallo de DynamoDB
        when(messageRepository.save(any(Message.class)))
                .thenThrow(new RuntimeException("DynamoDB no disponible"));

        // ── Act & Assert ──────────────────────────────────────────────────
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> useCase.processMessage(input, "sqs-error-001"),
                "Debe propagar la excepción del repositorio"
        );
    }
}
