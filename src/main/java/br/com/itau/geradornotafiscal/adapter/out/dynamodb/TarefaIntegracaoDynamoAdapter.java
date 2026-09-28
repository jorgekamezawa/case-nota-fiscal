package br.com.itau.geradornotafiscal.adapter.out.dynamodb;

import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.TarefaIntegracaoRegistro;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers.TarefaIntegracaoRegistroMapper;
import br.com.itau.geradornotafiscal.application.port.out.TarefaIntegracaoPort;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Tabela {@code tarefas_integracao}: chave {@code id_pedido} e {@code sistema}; índice {@code pendentes} só com as
 * tarefas abertas, por fatia e por {@code pendente_desde} (E02-NF-02).
 */
@Component
public class TarefaIntegracaoDynamoAdapter implements TarefaIntegracaoPort {

    public static final String INDICE_PENDENTES = "pendentes";
    static final String ID_PEDIDO = "id_pedido";
    static final String SISTEMA = "sistema";
    static final String STATUS = "status";
    static final String TENTATIVAS = "tentativas";
    static final String ULTIMO_ERRO = "ultimo_erro";
    static final String BLOQUEADA_ATE = "bloqueada_ate";
    static final String FATIA_PENDENTE = "fatia_pendente";
    static final String PENDENTE_DESDE = "pendente_desde";
    static final String VERSAO = "versao";
    static final String EXPIRA_EM = "expira_em";

    // Atributos que mudam a cada transição; os ausentes na tarefa nova são removidos.
    private static final List<String> MUTAVEIS =
            List.of(STATUS, TENTATIVAS, ULTIMO_ERRO, BLOQUEADA_ATE, FATIA_PENDENTE, PENDENTE_DESDE, VERSAO);

    private final DynamoDbClient dynamoDb;
    private final TarefaIntegracaoRegistroMapper mapper;
    private final ChamadasDynamoDb chamadas;
    private final String tabela;

    public TarefaIntegracaoDynamoAdapter(DynamoDbClient dynamoDb, TarefaIntegracaoRegistroMapper mapper, ChamadasDynamoDb chamadas,
                                         @Value("${aws.dynamodb.tabela-tarefas:tarefas_integracao}") String tabela) {
        this.dynamoDb = dynamoDb;
        this.mapper = mapper;
        this.chamadas = chamadas;
        this.tabela = tabela;
    }

    @Override
    public Optional<TarefaIntegracao> buscar(Long idPedido, Sistema sistema) {
        Map<String, AttributeValue> item = chamadas.chamar("buscar-tarefa", () -> dynamoDb.getItem(requisicao -> requisicao
                .tableName(tabela)
                .key(chave(idPedido, sistema.name()))
                .consistentRead(true))).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(mapper.paraDominio(registro(item)));
    }

    @Override
    public boolean salvar(TarefaIntegracao tarefa, long versaoLida) {
        Map<String, AttributeValue> novo = atributos(mapper.paraRegistro(tarefa));
        List<String> definir = new ArrayList<>();
        List<String> remover = new ArrayList<>();
        Map<String, String> nomes = new HashMap<>();
        Map<String, AttributeValue> valores = new HashMap<>();
        for (String atributo : MUTAVEIS) {
            nomes.put("#" + atributo, atributo);
            if (novo.containsKey(atributo)) {
                definir.add("#" + atributo + " = :" + atributo);
                valores.put(":" + atributo, novo.get(atributo));
            } else {
                remover.add("#" + atributo);
            }
        }
        valores.put(":versaoLida", AttributeValue.fromN(Long.toString(versaoLida)));
        String atualizacao = "SET " + String.join(", ", definir) + (remover.isEmpty() ? "" : " REMOVE " + String.join(", ", remover));
        try {
            chamadas.chamar("salvar-tarefa", () -> dynamoDb.updateItem(requisicao -> requisicao
                    .tableName(tabela)
                    .key(chave(tarefa.getIdPedido(), tarefa.getSistema().name()))
                    .updateExpression(atualizacao)
                    .conditionExpression("#" + VERSAO + " = :versaoLida")
                    .expressionAttributeNames(nomes)
                    .expressionAttributeValues(valores)));
            return true;
        } catch (ConditionalCheckFailedException outroProcessoMudouAntes) {
            return false;
        }
    }

    @Override
    public List<TarefaIntegracao> abertasDesdeAntesDe(Instant limite) {
        return IntStream.range(0, TarefaIntegracaoRegistroMapper.FATIAS).boxed()
                .flatMap(fatia -> chamadas.chamar("reconciliar", () -> dynamoDb.queryPaginator(requisicao -> requisicao
                                .tableName(tabela)
                                .indexName(INDICE_PENDENTES)
                                .keyConditionExpression(FATIA_PENDENTE + " = :fatia AND " + PENDENTE_DESDE + " < :limite")
                                .expressionAttributeValues(Map.of(
                                        ":fatia", AttributeValue.fromS(TarefaIntegracaoRegistroMapper.fatia(fatia)),
                                        ":limite", AttributeValue.fromN(Long.toString(limite.toEpochMilli())))))
                        .stream().toList()).stream())
                .map(QueryResponse::items)
                .flatMap(List::stream)
                .map(item -> mapper.paraDominio(registro(item)))
                .toList();
    }

    /** Item completo da tarefa, gravado junto com a nota (E02-RN-02). */
    static Map<String, AttributeValue> item(TarefaIntegracaoRegistro registro, long expiraEm) {
        Map<String, AttributeValue> item = atributos(registro);
        item.put(ID_PEDIDO, AttributeValue.fromN(registro.idPedido().toString()));
        item.put(SISTEMA, AttributeValue.fromS(registro.sistema()));
        item.put(EXPIRA_EM, AttributeValue.fromN(Long.toString(expiraEm)));
        return item;
    }

    private static Map<String, AttributeValue> atributos(TarefaIntegracaoRegistro registro) {
        Map<String, AttributeValue> atributos = new HashMap<>();
        atributos.put(STATUS, AttributeValue.fromS(registro.status()));
        atributos.put(TENTATIVAS, AttributeValue.fromN(Integer.toString(registro.tentativas())));
        atributos.put(VERSAO, AttributeValue.fromN(Long.toString(registro.versao())));
        if (registro.ultimoErro() != null) {
            atributos.put(ULTIMO_ERRO, AttributeValue.fromS(registro.ultimoErro()));
        }
        if (registro.bloqueadaAte() != null) {
            atributos.put(BLOQUEADA_ATE, AttributeValue.fromN(registro.bloqueadaAte().toString()));
        }
        if (registro.fatiaPendente() != null) {
            atributos.put(FATIA_PENDENTE, AttributeValue.fromS(registro.fatiaPendente()));
            atributos.put(PENDENTE_DESDE, AttributeValue.fromN(registro.pendenteDesde().toString()));
        }
        return atributos;
    }

    private static TarefaIntegracaoRegistro registro(Map<String, AttributeValue> item) {
        return new TarefaIntegracaoRegistro(
                Long.valueOf(item.get(ID_PEDIDO).n()),
                item.get(SISTEMA).s(),
                item.get(STATUS).s(),
                Integer.parseInt(item.get(TENTATIVAS).n()),
                item.containsKey(ULTIMO_ERRO) ? item.get(ULTIMO_ERRO).s() : null,
                item.containsKey(BLOQUEADA_ATE) ? Long.valueOf(item.get(BLOQUEADA_ATE).n()) : null,
                item.containsKey(FATIA_PENDENTE) ? item.get(FATIA_PENDENTE).s() : null,
                item.containsKey(PENDENTE_DESDE) ? Long.valueOf(item.get(PENDENTE_DESDE).n()) : null,
                Long.parseLong(item.get(VERSAO).n()));
    }

    private static Map<String, AttributeValue> chave(Long idPedido, String sistema) {
        return Map.of(ID_PEDIDO, AttributeValue.fromN(idPedido.toString()), SISTEMA, AttributeValue.fromS(sistema));
    }
}
