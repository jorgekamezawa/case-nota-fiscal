package br.com.itau.geradornotafiscal.adapter.out.dynamodb;

import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.NotaFiscalRegistro;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers.NotaFiscalRegistroMapper;
import br.com.itau.geradornotafiscal.application.exception.ArmazenamentoIndisponivelException;
import br.com.itau.geradornotafiscal.application.exception.NotaGrandeDemaisException;
import br.com.itau.geradornotafiscal.application.exception.NotaJaGuardadaException;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tabela {@code notas}: chave {@code id_pedido}, a nota em JSON e a data de expurgo em segundos, formato do TTL
 * (E04-NF-01).
 */
@Component
public class NotaFiscalDynamoAdapter implements NotaFiscalPersistenciaPort {

    static final String ID_PEDIDO = "id_pedido";
    static final String NOTA = "nota";
    static final String EMITIDA_EM = "emitida_em";
    static final String EXPIRA_EM = "expira_em";

    // Limite de tamanho de um item do DynamoDB (E04-RN-04).
    static final int TAMANHO_MAXIMO_EM_BYTES = 400 * 1024;

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    // Serialização própria: o formato gravado não muda quando a configuração do JSON da API muda.
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final DynamoDbClient dynamoDb;
    private final NotaFiscalRegistroMapper mapper;
    private final MeterRegistry meterRegistry;
    private final String tabela;

    public NotaFiscalDynamoAdapter(DynamoDbClient dynamoDb, NotaFiscalRegistroMapper mapper, MeterRegistry meterRegistry,
                                   @Value("${aws.dynamodb.tabela-notas:notas}") String tabela) {
        this.dynamoDb = dynamoDb;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
        this.tabela = tabela;
    }

    @Override
    public void guardar(Long idPedido, NotaFiscal nota, LocalDate apagarAPartirDe) {
        Map<String, AttributeValue> item = item(idPedido, nota, apagarAPartirDe);
        if (tamanho(item) > TAMANHO_MAXIMO_EM_BYTES) {
            throw new NotaGrandeDemaisException();
        }
        try {
            chamar("guardar", () -> dynamoDb.putItem(requisicao -> requisicao
                    .tableName(tabela)
                    .item(item)
                    .conditionExpression("attribute_not_exists(" + ID_PEDIDO + ")")));
        } catch (ConditionalCheckFailedException e) {
            throw new NotaJaGuardadaException();
        }
    }

    @Override
    public Optional<NotaFiscal> buscar(Long idPedido) {
        Map<String, AttributeValue> item = chamar("buscar", () -> dynamoDb.getItem(requisicao -> requisicao
                .tableName(tabela)
                .key(Map.of(ID_PEDIDO, AttributeValue.fromN(idPedido.toString())))
                .consistentRead(true))).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapper.paraDominio(JSON.readValue(item.get(NOTA).s(), NotaFiscalRegistro.class)));
    }

    Map<String, AttributeValue> item(Long idPedido, NotaFiscal nota, LocalDate apagarAPartirDe) {
        return Map.of(
                ID_PEDIDO, AttributeValue.fromN(idPedido.toString()),
                NOTA, AttributeValue.fromS(JSON.writeValueAsString(mapper.paraRegistro(nota))),
                EMITIDA_EM, AttributeValue.fromS(nota.getData().toString()),
                EXPIRA_EM, AttributeValue.fromN(Long.toString(apagarAPartirDe.atStartOfDay(SAO_PAULO).toEpochSecond())));
    }

    // Falha de conexão, erro do serviço ou limite de vazão, depois das novas tentativas do SDK, é indisponibilidade (E04-RN-01).
    private <T> T chamar(String operacao, Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (SdkClientException e) {
            throw indisponivel(operacao, e);
        } catch (DynamoDbException e) {
            if (e.statusCode() >= 500 || e.isThrottlingException()) {
                throw indisponivel(operacao, e);
            }
            throw e;
        }
    }

    private ArmazenamentoIndisponivelException indisponivel(String operacao, Exception causa) {
        meterRegistry.counter("armazenamento.falhas", "operacao", operacao).increment();
        return new ArmazenamentoIndisponivelException(causa);
    }

    // Tamanho como o DynamoDB conta: nome e valor de cada atributo em UTF-8; o número conta no máximo o próprio texto.
    static int tamanho(Map<String, AttributeValue> item) {
        return item.entrySet().stream()
                .mapToInt(atributo -> bytes(atributo.getKey())
                        + bytes(atributo.getValue().s() != null ? atributo.getValue().s() : atributo.getValue().n()))
                .sum();
    }

    private static int bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8).length;
    }
}
