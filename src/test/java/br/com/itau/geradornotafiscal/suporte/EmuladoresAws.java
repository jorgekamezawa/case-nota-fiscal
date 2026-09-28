package br.com.itau.geradornotafiscal.suporte;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.testcontainers.containers.GenericContainer;

import java.util.List;

/**
 * Liga todo teste com contexto do Spring ao emulador oficial do DynamoDB (E04-NF-05). O container sobe uma vez para
 * a suíte inteira; por isso cada teste usa um {@code id_pedido} próprio.
 */
public class EmuladoresAws implements ContextCustomizerFactory {

    public static final GenericContainer<?> DYNAMODB = new GenericContainer<>("amazon/dynamodb-local:3.3.1")
            .withExposedPorts(8000);

    public static String endpointDynamoDb() {
        DYNAMODB.start();
        return "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000);
    }

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> classeDeTeste, List<ContextConfigurationAttributes> atributos) {
        return new Configuracao();
    }

    // Igual para todos os testes, para o cache de contextos do Spring continuar valendo.
    private record Configuracao() implements ContextCustomizer {
        @Override
        public void customizeContext(ConfigurableApplicationContext contexto, MergedContextConfiguration configuracao) {
            TestPropertyValues.of(
                    "aws.regiao=us-east-1",
                    "aws.dynamodb.endpoint=" + endpointDynamoDb(),
                    "infra-local.criar-recursos=true").applyTo(contexto);
        }
    }
}
