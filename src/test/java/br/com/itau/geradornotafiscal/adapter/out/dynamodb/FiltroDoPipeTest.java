package br.com.itau.geradornotafiscal.adapter.out.dynamodb;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.config.FilasDeTarefas;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import br.com.itau.geradornotafiscal.suporte.PipeDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O filtro versionado do Pipe contra eventos reais do Streams do DynamoDB Local (E02-NF-03, RFC-0001 risco 9).
 */
@SpringBootTest
class FiltroDoPipeTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private NotaFiscalDynamoAdapter notas;
    @Autowired
    private TarefaIntegracaoDynamoAdapter tarefas;
    @Autowired
    private DynamoDbClient dynamoDb;
    @Autowired
    private SqsClient sqs;
    @Autowired
    private FilasDeTarefas filas;

    @Test
    @DisplayName("E02-NF-03: o filtro aceita a criação da tarefa só no Pipe do sistema dela e recusa alteração e remoção")
    void e02Nf03_filtroSoDaCriacao() throws Exception {
        PipeDeTeste pipe = new PipeDeTeste(dynamoDb, sqs, filas, "tarefas_integracao");
        pipe.eventosNovos();
        long idPedido = PedidoBase.novoId();
        notas.guardar(idPedido, NotaFiscalDynamoAdapterTest.nota(1, "Teclado USB"), "a".repeat(64), LocalDate.of(2032, 1, 1),
                TarefaIntegracaoDynamoAdapterTest.pendentes(idPedido, Instant.now()));
        TarefaIntegracao entrega = tarefas.buscar(idPedido, Sistema.ENTREGA).orElseThrow();
        tarefas.salvar(entrega.pegar(Instant.now()), entrega.getVersao());
        dynamoDb.deleteItem(r -> r.tableName("tarefas_integracao").key(Map.of(
                "id_pedido", AttributeValue.fromN(Long.toString(idPedido)), "sistema", AttributeValue.fromS("REGISTRO"))));

        List<String> eventos = pipe.eventosNovos().stream().filter(evento -> doPedido(evento, idPedido)).toList();

        assertThat(eventos).hasSize(6);
        for (String evento : eventos) {
            JsonNode lido = JSON.readTree(evento);
            if (lido.get("eventName").asString().equals("INSERT")) {
                String sistema = lido.get("dynamodb").get("NewImage").get("sistema").get("S").asString();
                assertThat(pipe.sistemasQueAceitam(evento)).as(evento).containsExactly(sistema);
            } else {
                assertThat(pipe.sistemasQueAceitam(evento)).as(evento).isEmpty();
            }
        }
    }

    private static boolean doPedido(String evento, long idPedido) {
        return JSON.readTree(evento).get("dynamodb").get("Keys").get("id_pedido").get("N").asString()
                .equals(Long.toString(idPedido));
    }
}
