package com.msgpipeline.processor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * =========================================================================
 * CAPA: Infraestructura — Configuración de Beans AWS
 * ARQUITECTURA: Hexagonal
 * PERFIL: 'aws' (solo se carga en AWS Lambda)
 * =========================================================================
 *
 * PATRÓN: Factory Method (via @Bean)
 *   Los métodos anotados con @Bean son "fábricas" gestionadas por Spring.
 *   Spring llama al método UNA VEZ y guarda la instancia creada.
 *   Todos los beans que necesiten DynamoDbClient recibirán LA MISMA instancia.
 *
 * PATRÓN: Singleton (implícito en Spring)
 *   Por defecto, todos los @Bean son singletons en Spring.
 *   El DynamoDbClient se crea ONCE → reutilizado en todas las invocaciones.
 *   Esto es CRÍTICO para el rendimiento en Lambda (warm start).
 *
 * @Profile("aws"):
 *   SOLO activo en el perfil 'aws'. En 'local', no se crea DynamoDbClient.
 *   Evita errores de conexión cuando no hay credenciales AWS disponibles.
 *
 * CREDENCIALES AWS — DefaultCredentialsProvider:
 *   Busca credenciales en este ORDEN automáticamente:
 *     1. Variables de entorno: AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY
 *     2. Perfil AWS: ~/.aws/credentials (para desarrollo)
 *     3. IAM Role del Lambda → AUTOMÁTICO en AWS (sin credenciales explícitas)
 *     4. ECS Task Role, EC2 Instance Profile, etc.
 *
 *   En Lambda, SIEMPRE usa el IAM Role (opción 3).
 *   NO es necesario configurar access keys en Lambda — es inseguro.
 * =========================================================================
 */
@Slf4j
@Configuration
@Profile("aws")
@RequiredArgsConstructor
public class DynamoConfig {

    private final AppConfig appConfig;

    /**
     * Crea y configura el cliente DynamoDB.
     *
     * REUTILIZACIÓN en Lambda:
     *   Este bean se crea en el COLD START del Lambda (primera invocación).
     *   En invocaciones WARM (subsiguientes), Lambda reutiliza el contexto
     *   Spring existente — el DynamoDbClient NO se recrea.
     *   Esto mejora significativamente el rendimiento en warm starts.
     *
     * TIMEOUT y RETRY:
     *   El SDK v2 incluye retry automático con backoff exponencial.
     *   Para Lambda, los valores por defecto son apropiados.
     *
     * @return DynamoDbClient configurado para la región del curso
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        String region = appConfig.getAws().getRegion();

        log.info("Inicializando DynamoDbClient [region={}] [tabla={}]",
                region, appConfig.getAws().getDynamodbTable());

        // DefaultCredentialsProvider: detecta automáticamente las credenciales
        // según el contexto de ejecución (local vs Lambda vs EC2 vs ECS)
        DynamoDbClient client = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        log.info("DynamoDbClient inicializado exitosamente");

        return client;
    }

    /**
     * Expone el nombre de la tabla DynamoDB como bean String.
     *
     * Esto permite inyectar el nombre de tabla directamente en otros beans
     * usando @Qualifier("tableName") o por nombre de método.
     *
     * BUENA PRÁCTICA: centralizar la configuración en un bean facilita
     * el testing (puedes sobrescribir el bean en tests con @TestConfiguration)
     */
    @Bean
    public String tableName() {
        return appConfig.getAws().getDynamodbTable();
    }
}
