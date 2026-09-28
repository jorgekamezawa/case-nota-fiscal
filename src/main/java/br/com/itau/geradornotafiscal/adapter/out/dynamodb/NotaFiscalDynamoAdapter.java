package br.com.itau.geradornotafiscal.adapter.out.dynamodb;

import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.NotaFiscalRegistro;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers.NotaFiscalRegistroMapper;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers.TarefaIntegracaoRegistroMapper;
import br.com.itau.geradornotafiscal.application.exception.ConflitoDeGravacaoException;
import br.com.itau.geradornotafiscal.application.exception.NotaGrandeDemaisException;
import br.com.itau.geradornotafiscal.application.exception.NotaJaGuardadaException;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort.NotaGuardada;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactionConflictException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tabela {@code notas}: chave {@code id_pedido}, a nota em JSON, o hash do pedido (E03-NF-02) e a data de expurgo em
 * segundos, formato do TTL (E04-NF-01). A nota é gravada na mesma transação das tarefas de integração (E02-NF-02).
 */
@Component
public class NotaFiscalDynamoAdapter implements NotaFiscalPersistenciaPort {

    static final String ID_PEDIDO = "id_pedido";
    static final String NOTA = "nota";
    static final String EMITIDA_EM = "emitida_em";
    static final String HASH_PEDIDO = "hash_pedido";
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
    private final TarefaIntegracaoRegistroMapper tarefaMapper;
    private final ChamadasDynamoDb chamadas;
    private final Clock relogio;
    private final String tabela;
    private final String tabelaTarefas;

    public NotaFiscalDynamoAdapter(DynamoDbClient dynamoDb, NotaFiscalRegistroMapper mapper,
                                   TarefaIntegracaoRegistroMapper tarefaMapper, ChamadasDynamoDb chamadas, Clock relogio,
                                   @Value("${aws.dynamodb.tabela-notas:notas}") String tabela,
                                   @Value("${aws.dynamodb.tabela-tarefas:tarefas_integracao}") String tabelaTarefas) {
        this.dynamoDb = dynamoDb;
        this.mapper = mapper;
        this.tarefaMapper = tarefaMapper;
        this.chamadas = chamadas;
        this.relogio = relogio;
        this.tabela = tabela;
        this.tabelaTarefas = tabelaTarefas;
    }

    @Override
    public void guardar(Long idPedido, NotaFiscal nota, String hashPedido, LocalDate apagarAPartirDe,
                        List<TarefaIntegracao> tarefas) {
        Map<String, AttributeValue> item = item(idPedido, nota, hashPedido, apagarAPartirDe);
        if (tamanho(item) > TAMANHO_MAXIMO_EM_BYTES) {
            throw new NotaGrandeDemaisException();
        }
        long expiraEm = Long.parseLong(item.get(EXPIRA_EM).n());
        List<TransactWriteItem> gravacoes = new ArrayList<>();
        gravacoes.add(TransactWriteItem.builder().put(Put.builder()
                .tableName(tabela)
                .item(item)
                // O expurgo apaga em até alguns dias depois da data: nota vencida ainda presente é substituída (E03-RN-06).
                .conditionExpression("attribute_not_exists(" + ID_PEDIDO + ") OR " + EXPIRA_EM + " <= :agora")
                .expressionAttributeValues(Map.of(":agora", AttributeValue.fromN(Long.toString(agora()))))
                .build()).build());
        tarefas.forEach(tarefa -> gravacoes.add(TransactWriteItem.builder().put(Put.builder()
                .tableName(tabelaTarefas)
                .item(TarefaIntegracaoDynamoAdapter.item(tarefaMapper.paraRegistro(tarefa), expiraEm))
                .build()).build()));
        try {
            // Nota e tarefas numa transação: ou tudo é gravado, ou nada (E02-RN-02).
            chamadas.chamar("guardar", () -> dynamoDb.transactWriteItems(requisicao -> requisicao.transactItems(gravacoes)));
        } catch (TransactionCanceledException e) {
            throw motivo(e);
        } catch (TransactionConflictException e) {
            throw new ConflitoDeGravacaoException(e);
        }
    }

    private RuntimeException motivo(TransactionCanceledException e) {
        List<String> codigos = e.cancellationReasons().stream().map(CancellationReason::code).toList();
        if (codigos.contains("ConditionalCheckFailed")) {
            return new NotaJaGuardadaException();
        }
        if (codigos.contains("TransactionConflict")) {
            return new ConflitoDeGravacaoException(e);
        }
        if (codigos.contains("ThrottlingError") || codigos.contains("ProvisionedThroughputExceeded")) {
            return chamadas.indisponivel("guardar", e);
        }
        return e;
    }

    @Override
    public Optional<NotaGuardada> buscar(Long idPedido) {
        Map<String, AttributeValue> item = chamadas.chamar("buscar", () -> dynamoDb.getItem(requisicao -> requisicao
                .tableName(tabela)
                .key(Map.of(ID_PEDIDO, AttributeValue.fromN(idPedido.toString())))
                .consistentRead(true))).item();
        if (item == null || item.isEmpty() || Long.parseLong(item.get(EXPIRA_EM).n()) <= agora()) {
            return Optional.empty();
        }
        return Optional.of(new NotaGuardada(mapper.paraDominio(JSON.readValue(item.get(NOTA).s(), NotaFiscalRegistro.class)),
                item.get(HASH_PEDIDO).s()));
    }

    Map<String, AttributeValue> item(Long idPedido, NotaFiscal nota, String hashPedido, LocalDate apagarAPartirDe) {
        return Map.of(
                ID_PEDIDO, AttributeValue.fromN(idPedido.toString()),
                NOTA, AttributeValue.fromS(JSON.writeValueAsString(mapper.paraRegistro(nota))),
                HASH_PEDIDO, AttributeValue.fromS(hashPedido),
                EMITIDA_EM, AttributeValue.fromS(nota.getData().toString()),
                EXPIRA_EM, AttributeValue.fromN(Long.toString(apagarAPartirDe.atStartOfDay(SAO_PAULO).toEpochSecond())));
    }

    private long agora() {
        return relogio.instant().getEpochSecond();
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
