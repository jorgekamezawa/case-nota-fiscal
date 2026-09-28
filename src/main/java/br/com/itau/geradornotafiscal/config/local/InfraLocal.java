package br.com.itau.geradornotafiscal.config.local;

import br.com.itau.geradornotafiscal.config.FilasDeTarefas;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.StreamSpecification;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.util.Map;

/**
 * Cria nos emuladores o que, na AWS, o Terraform cria (fase 7): tabelas, índice, Streams, filas e DLQs. Ligado só no
 * perfil local e nos testes.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("infra-local.criar-recursos")
public class InfraLocal implements InitializingBean {

    // A DLQ guarda a mensagem pelo máximo do SQS; o prazo conta de quando o serviço a move para lá (E02-NF-11).
    private static final Duration RETENCAO_DA_DLQ = Duration.ofDays(14);
    // Rede de segurança: o serviço move para a DLQ na 5ª falha; a fila, no 10º recebimento (E02-NF-05).
    private static final int MAXIMO_DE_RECEBIMENTOS = 10;

    private final DynamoDbClient dynamoDb;
    private final SqsClient sqs;

    @Value("${aws.dynamodb.tabela-notas:notas}")
    private String tabelaNotas;
    @Value("${aws.dynamodb.tabela-tarefas:tarefas_integracao}")
    private String tabelaTarefas;
    @Value("${fila.visibilidade:PT2M}")
    private Duration visibilidade;

    @Override
    public void afterPropertiesSet() {
        criarTabela(CreateTableRequest.builder()
                .tableName(tabelaNotas)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(atributo("id_pedido", ScalarAttributeType.N))
                .keySchema(chave("id_pedido", KeyType.HASH))
                .build());
        criarTabela(CreateTableRequest.builder()
                .tableName(tabelaTarefas)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(atributo("id_pedido", ScalarAttributeType.N), atributo("sistema", ScalarAttributeType.S),
                        atributo("fatia_pendente", ScalarAttributeType.S), atributo("pendente_desde", ScalarAttributeType.N))
                .keySchema(chave("id_pedido", KeyType.HASH), chave("sistema", KeyType.RANGE))
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("pendentes")
                        .keySchema(chave("fatia_pendente", KeyType.HASH), chave("pendente_desde", KeyType.RANGE))
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                // O filtro do Pipe reconhece a criação da tarefa pela falta da versão anterior (E02-NF-03).
                .streamSpecification(StreamSpecification.builder()
                        .streamEnabled(true).streamViewType(StreamViewType.NEW_AND_OLD_IMAGES).build())
                .build());
        for (Sistema sistema : Sistema.values()) {
            criarFilas(sistema);
        }
    }

    private void criarTabela(CreateTableRequest tabela) {
        try {
            dynamoDb.createTable(tabela);
        } catch (ResourceInUseException jaExiste) {
            // Outra execução ou outro contexto de teste já criou a tabela.
        }
    }

    private void criarFilas(Sistema sistema) {
        String dlq = sqs.createQueue(fila -> fila.queueName(FilasDeTarefas.nomeDaDlq(sistema))
                .attributes(Map.of(QueueAttributeName.MESSAGE_RETENTION_PERIOD, Long.toString(RETENCAO_DA_DLQ.toSeconds()))))
                .queueUrl();
        String arnDaDlq = sqs.getQueueAttributes(atributos -> atributos.queueUrl(dlq).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
        sqs.createQueue(fila -> fila.queueName(FilasDeTarefas.nome(sistema)).attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, Long.toString(visibilidade.toSeconds()),
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + arnDaDlq + "\",\"maxReceiveCount\":\"" + MAXIMO_DE_RECEBIMENTOS + "\"}")));
    }

    private static AttributeDefinition atributo(String nome, ScalarAttributeType tipo) {
        return AttributeDefinition.builder().attributeName(nome).attributeType(tipo).build();
    }

    private static KeySchemaElement chave(String nome, KeyType tipo) {
        return KeySchemaElement.builder().attributeName(nome).keyType(tipo).build();
    }
}
