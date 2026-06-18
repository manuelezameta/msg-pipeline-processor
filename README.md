# msg-pipeline-processor — Sesión 08

## Especialista Spring Boot + AWS Serverless | Anku Academy 2026C2

---

## 🗂 Descripción

Microservicio Lambda que **consume mensajes de la cola SQS** y los persiste en **DynamoDB**.
Corresponde al **Paso 6 de la Sesión 08**: Lambda Processor con Event Source Mapping SQS → Lambda.

**Flujo de producción en AWS:**
```
API Gateway → SQS (msg-pipeline-queue) → Lambda Processor → DynamoDB (msg-pipeline-messages)
                                                          ↓
                                                     DLQ (si falla)
```

---

## 🏗 Arquitectura

### Hexagonal (Ports & Adapters) + Clean Architecture

```
com.msgpipeline.processor/
│
├── SqsHandler.java                     ← Entry point AWS Lambda
│
├── domain/                             ← Núcleo del negocio (sin dependencias externas)
│   ├── model/
│   │   └── Message.java                ← Entidad de dominio
│   └── port/out/
│       └── MessageRepository.java      ← Puerto de salida (interfaz)
│
├── application/                        ← Orquestación de casos de uso
│   ├── port/in/
│   │   └── ProcessMessagePort.java     ← Puerto de entrada (interfaz)
│   └── usecase/
│       └── ProcessMessageUseCase.java  ← Lógica de negocio pura
│
├── adapter/                            ← Implementaciones concretas
│   ├── in/web/
│   │   ├── MessageController.java      ← REST (solo perfil 'local')
│   │   └── dto/
│   │       ├── ProcessMessageRequest.java
│   │       └── ProcessMessageResponse.java
│   └── out/persistence/
│       ├── DynamoMessageRepository.java   ← Perfil 'aws'
│       └── InMemoryMessageRepository.java ← Perfil 'local'
│
└── config/
    ├── AppConfig.java          ← @ConfigurationProperties
    ├── DynamoConfig.java       ← Beans AWS (perfil 'aws')
    ├── OpenApiConfig.java      ← Swagger (perfil 'local')
    └── ProcessorApplication.java ← main() solo para desarrollo local
```

### ¿Por qué Hexagonal?

| Razón | Beneficio en este proyecto |
|---|---|
| **Desacoplamiento** | El Use Case no sabe si persiste en DynamoDB o en memoria |
| **Testabilidad** | Tests unitarios sin AWS ni Spring Boot |
| **Perfiles** | `local` usa InMemory; `aws` usa DynamoDB — mismo Use Case |
| **Evolución** | Sesiones 04-08 agregan SNS/Step Functions sin tocar el dominio |

### Patrones de Diseño Aplicados

| Patrón | Dónde | Beneficio |
|---|---|---|
| **Repository** | `MessageRepository` | Abstrae el mecanismo de persistencia |
| **Strategy** | `InMemory` vs `DynamoRepo` | Intercambiables según el perfil |
| **Adapter** | `SqsHandler`, `MessageController` | Adaptan SQS/HTTP al dominio |
| **Builder** | `Message.builder()` | Construcción legible de entidades |
| **Singleton** | Clientes AWS en `static {}` | Reutilización en warm starts |
| **Factory Method** | `@Bean` en `DynamoConfig` | Spring gestiona el ciclo de vida |

---

## ⚙️ Configuración

### Variables de Entorno (AWS Lambda)

| Variable | Valor | Descripción |
|---|---|---|
| `DYNAMODB_TABLE_NAME` | `msg-pipeline-messages` | Nombre de la tabla DynamoDB |
| `AWS_REGION` | `us-east-1` | Lambda lo inyecta automáticamente |

> ⚠️ **Nunca** coloque access keys en las variables de entorno de Lambda.  
> Use el **IAM Role** del Lambda — se autentica automáticamente.

### Perfiles Spring

| Perfil | Activo en | Repositorio | Servidor Web | Swagger |
|---|---|---|---|---|
| `local` | macOS (desarrollo) | InMemoryRepository | Tomcat :8082 | ✅ |
| `aws` | AWS Lambda | DynamoMessageRepository | ❌ (NONE) | ❌ |

---

## 🚀 Cómo Ejecutar

### Desarrollo Local (macOS)

```bash
# Instalar dependencias y compilar
./gradlew clean build

# Ejecutar en modo local (con Swagger UI)
./gradlew bootRun --args='--spring.profiles.active=local'
```

**URLs disponibles en local:**
- Swagger UI: `http://localhost:8082/swagger-ui.html`
- API Docs JSON: `http://localhost:8082/v3/api-docs`
- POST mensajes: `http://localhost:8082/api/v1/messages`
- GET mensajes: `http://localhost:8082/api/v1/messages`

### Compilar para AWS Lambda

```bash
# Genera el ZIP listo para subir a Lambda
./gradlew clean buildZip

# Archivo generado:
# build/distributions/msg-pipeline-processor-lambda.zip
```

### Formato del ZIP (`buildZip`)

```
msg-pipeline-processor-lambda.zip
├── com/msgpipeline/processor/SqsHandler.class     ← tus clases en la raíz
├── com/msgpipeline/processor/domain/...
└── lib/
    ├── spring-boot-3.5.0.jar                       ← dependencias en lib/
    ├── aws-lambda-java-core-1.2.3.jar
    └── ...
```

**¿Por qué ZIP y no fat JAR?**  
Lambda carga los JARs de `lib/` automáticamente. Las dependencias no se descomprimen → cold start más rápido.

---

## 🔧 Configuración en la Consola AWS Lambda

### Handler a configurar

```
com.msgpipeline.processor.SqsHandler::handleRequest
```

### Configuración recomendada

| Parámetro | Valor | Justificación |
|---|---|---|
| Runtime | Java 17 | LTS, requerido por Spring Boot 3.5 |
| Memoria | 512 MB | Spring context + AWS SDK requieren ~256 MB mínimo |
| Timeout | 30 segundos | Suficiente para procesar un batch de mensajes SQS |
| Arquitectura | x86_64 | Compatible con el JAR compilado en macOS Intel/M1 |

### Variables de Entorno en Lambda

Ir a: Lambda → Configuración → Variables de entorno → Editar

```
DYNAMODB_TABLE_NAME = msg-pipeline-messages
```

### Event Source Mapping (SQS → Lambda)

Ir a: Lambda → Configuración → Triggers → Agregar trigger

| Campo | Valor |
|---|---|
| Source | SQS |
| Cola | msg-pipeline-queue |
| Batch size | 10 |
| Batch window | 0 |

---

## 🧪 Pruebas

### Pruebas Locales con Swagger UI

1. Ejecutar: `./gradlew bootRun --args='--spring.profiles.active=local'`
2. Abrir: `http://localhost:8082/swagger-ui.html`
3. Usar **POST /api/v1/messages** con el siguiente body:

```json
{
  "messageType": "EMAIL",
  "channel": "CORREO_ELECTRONICO",
  "recipientEmail": "estudiante@ankuacademy.com",
  "content": "Hola desde Sesión 08 - DynamoDB + SQS + Lambda Processor"
}
```

4. Verificar con **GET /api/v1/messages** que el mensaje fue guardado.

### Pruebas en AWS Lambda (Consola)

Ir a: Lambda → Probar → Crear evento de prueba

**Nombre del evento:** `test-sqs-batch`

**Template:** Amazon SQS

**Body del evento:**
```json
{
  "Records": [
    {
      "messageId": "sqs-test-001",
      "receiptHandle": "AQEBwJnKyrHigUMZj6reyNurFNNi",
      "body": "{\"messageId\":\"msg-001\",\"messageType\":\"EMAIL\",\"channel\":\"CORREO_ELECTRONICO\",\"recipientEmail\":\"test@ankuacademy.com\",\"content\":\"Mensaje de prueba Sesión 08\",\"createdAt\":\"2026-04-23T00:00:00Z\"}",
      "attributes": {
        "ApproximateReceiveCount": "1",
        "SentTimestamp": "1714435200000",
        "SenderId": "AIDAIENQZJOLO23YVJ4VO",
        "ApproximateFirstReceiveTimestamp": "1714435200000"
      },
      "messageAttributes": {},
      "md5OfBody": "e4e68fb7bd0e697a0ae8f1bb0f29efa",
      "eventSource": "aws:sqs",
      "eventSourceARN": "arn:aws:sqs:us-east-1:123456789012:msg-pipeline-queue",
      "awsRegion": "us-east-1"
    }
  ]
}
```

### Verificar en DynamoDB

Ir a: DynamoDB → Tablas → msg-pipeline-messages → Explorar elementos

El mensaje procesado debe aparecer con `status = COMPLETED`.

### Pruebas con el Flujo Completo (API Gateway → SQS → Lambda → DynamoDB)

1. Enviar mensaje via API Gateway:
```bash
curl -X POST \
  https://{API_ID}.execute-api.us-east-1.amazonaws.com/prod/messages \
  -H "Content-Type: application/json" \
  -d '{
    "messageType": "EMAIL",
    "channel": "CORREO_ELECTRONICO",
    "recipientEmail": "prueba@ankuacademy.com",
    "content": "Flujo completo Sesión 08"
  }'
```

2. Verificar en CloudWatch Logs del Lambda: `/aws/lambda/msg-pipeline-processor`
3. Verificar en DynamoDB: tabla `msg-pipeline-messages`

---

## 🔑 Permisos IAM

El rol del Lambda (`msg-pipeline-lambda-role`) debe tener:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:GetItem"
      ],
      "Resource": "arn:aws:dynamodb:us-east-1:*:table/msg-pipeline-messages"
    },
    {
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:us-east-1:*:msg-pipeline-queue"
    }
  ]
}
```

---

## 🔗 Recursos AWS del Proyecto

| Recurso | Nombre | Región |
|---|---|---|
| Lambda | `msg-pipeline-processor` | us-east-1 |
| DynamoDB | `msg-pipeline-messages` | us-east-1 |
| SQS | `msg-pipeline-queue` | us-east-1 |
| DLQ | `msg-pipeline-dlq` | us-east-1 |
| IAM Role | `msg-pipeline-lambda-role` | Global |

---

## ❓ Por Qué No Hay `Application.java` con `main()` para Lambda

En una app Spring Boot normal el flujo es:
```
JVM → main(String[] args) → SpringApplication.run() → Tomcat activo → HTTP
```

En AWS Lambda el flujo es completamente diferente:
```
Lambda Runtime → Class.forName("SqsHandler") → new SqsHandler() → handleRequest()
```

Lambda **no ejecuta ningún `main()`**. Instancia el handler directamente por reflexión y llama a `handleRequest()` para cada invocación.

Si usáramos `SpringApplication.run()` en el constructor, estaríamos arrancando Tomcat en Lambda — desperdiciando memoria y sumando 5-10 segundos de cold start innecesarios.

**La solución en `SqsHandler.java`:**
```java
static {
    // Solo IoC container — SIN servidor web
    new SpringApplicationBuilder(ProcessorApplication.class)
        .web(WebApplicationType.NONE)
        .profiles("aws")
        .run();
}
```

`ProcessorApplication.java` existe solo para el **comando `bootRun` en local** con Swagger UI.

---

## 📦 Sesiones del Curso

| Sesión | Componente | Estado |
|---|---|---|
| 01 | Spring Boot Hexagonal Local | ✅ Completada |
| 02 | API Gateway + Lambda Orchestrator | ✅ Completada |
| **03** | **DynamoDB + SQS + Lambda Processor** | **← Estás aquí** |
| 04 | SNS + Lambda Validator + SOLID |  |
| 05 | Cognito + EventBridge + Step Functions |  |
| 06 | Spring Security + JWT |  |
| 07 | CloudWatch + X-Ray + Resiliencia |  |
| 08 | SAM + SnapStart + GitHub Actions CI/CD |  |

---

*Anku Academy — Especialista Spring Boot + AWS Serverless 2026C3*
