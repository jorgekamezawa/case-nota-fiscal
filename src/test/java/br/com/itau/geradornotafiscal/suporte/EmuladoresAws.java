package br.com.itau.geradornotafiscal.suporte;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.Map;

/**
 * Liga todo teste com contexto do Spring ao emulador oficial do DynamoDB (E04-NF-05) e ao ElasticMQ, compatível com o
 * SQS (E02-NF-03). Os containers sobem uma vez para a suíte inteira; por isso cada teste usa um {@code id_pedido}
 * próprio. O consumo das filas, a reconciliação e a medição ficam desligados, para os contextos do Spring guardados em cache não
 * disputarem as mensagens; o teste que precisa deles os liga.
 */
public class EmuladoresAws implements ContextCustomizerFactory {

    public static final GenericContainer<?> DYNAMODB = new GenericContainer<>("amazon/dynamodb-local:3.3.1")
            .withExposedPorts(8000);

    public static final GenericContainer<?> ELASTICMQ = new GenericContainer<>("softwaremill/elasticmq-native:1.7.1")
            .withExposedPorts(9324);

    public static String endpointSqs() {
        ELASTICMQ.start();
        return "http://" + ELASTICMQ.getHost() + ":" + ELASTICMQ.getMappedPort(9324);
    }

    public static String endpointDynamoDb() {
        DYNAMODB.start();
        return "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000);
    }

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> classeDeTeste, List<ContextConfigurationAttributes> atributos) {
        return new Configuracao();
    }

    // Igual para todos os testes, para o cache de contextos do Spring continuar valendo. Os valores entram com a menor
    // prioridade, para o teste que precisa (ex.: consumo das filas ligado) sobrescrever pelas suas propriedades.
    private record Configuracao() implements ContextCustomizer {
        @Override
        public void customizeContext(ConfigurableApplicationContext contexto, MergedContextConfiguration configuracao) {
            contexto.getEnvironment().getPropertySources().addLast(new MapPropertySource("emuladores-aws", Map.of(
                    "aws.regiao", "us-east-1",
                    "aws.dynamodb.endpoint", endpointDynamoDb(),
                    "aws.sqs.endpoint", endpointSqs(),
                    "infra-local.criar-recursos", "true",
                    "fila.visibilidade", "PT1S",
                    "fila.espera-maxima", "PT1S",
                    "fila.consumo.ativo", "false",
                    "reconciliacao.ativa", "false",
                    "medicao.ativa", "false")));
        }
    }
}
