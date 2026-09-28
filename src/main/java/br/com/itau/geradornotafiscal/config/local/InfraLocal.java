package br.com.itau.geradornotafiscal.config.local;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Cria nos emuladores as tabelas que, na AWS, o Terraform cria (fase 7). Ligado só no perfil local e nos testes.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("infra-local.criar-recursos")
public class InfraLocal implements InitializingBean {

    private final DynamoDbClient dynamoDb;

    @Value("${aws.dynamodb.tabela-notas:notas}")
    private String tabelaNotas;

    @Override
    public void afterPropertiesSet() {
        criarTabela(tabelaNotas);
    }

    private void criarTabela(String nome) {
        try {
            dynamoDb.createTable(tabela -> tabela
                    .tableName(nome)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("id_pedido").attributeType(ScalarAttributeType.N).build())
                    .keySchema(KeySchemaElement.builder().attributeName("id_pedido").keyType(KeyType.HASH).build()));
        } catch (ResourceInUseException jaExiste) {
            // Outra execução ou outro contexto de teste já criou a tabela.
        }
    }
}
