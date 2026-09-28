package br.com.itau.geradornotafiscal.adapter.in.fila;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.application.port.in.ReconciliarTarefasUseCase;
import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FilaTarefasPort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import br.com.itau.geradornotafiscal.application.port.out.TarefaIntegracaoPort;
import br.com.itau.geradornotafiscal.config.FilasDeTarefas;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import br.com.itau.geradornotafiscal.domain.valueobject.StatusTarefa;
import br.com.itau.geradornotafiscal.suporte.PipeDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Da nota às filas e aos sistemas, com o consumo das filas ligado, o ElasticMQ no lugar do SQS e o
 * {@link PipeDeTeste} no lugar do Streams com os Pipes. Os sistemas acionados são mocks, sem as esperas simuladas.
 */
@SpringBootTest(properties = "fila.consumo.ativo=true")
@AutoConfigureMockMvc
class ConsumoDeTarefasTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration PRAZO = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TarefaIntegracaoPort tarefas;
    @Autowired
    private FilaTarefasPort fila;
    @Autowired
    private ReconciliarTarefasUseCase reconciliarTarefasUseCase;
    @Autowired
    private DynamoDbClient dynamoDb;
    @Autowired
    private SqsClient sqs;
    @Autowired
    private FilasDeTarefas filas;

    @MockitoBean
    private RegistroPort registroPort;
    @MockitoBean
    private EstoquePort estoquePort;
    @MockitoBean
    private EntregaPort entregaPort;
    @MockitoBean
    private FinanceiroPort financeiroPort;

    private PipeDeTeste pipe;

    @BeforeEach
    void pipe() throws Exception {
        pipe = new PipeDeTeste(dynamoDb, sqs, filas, "tarefas_integracao");
        pipe.eventosNovos();
    }

    @Test
    @DisplayName("E02-RN-01 a E02-RN-03: os quatro sistemas recebem a nota depois da resposta, uma vez cada, com o identificador dela")
    void e02Rn01_quatroSistemasAcionadosDepois() throws Exception {
        long idPedido = emitir();

        pipe.transportar();

        aguardarTodas(idPedido, StatusTarefa.CONCLUIDA);
        String idDaNota = notaDoRegistro(idPedido);
        verify(registroPort).registrarNotaFiscal(argThat(nota -> nota.getIdNotaFiscal().equals(idDaNota)));
        verify(estoquePort).enviarNotaFiscalParaBaixaEstoque(argThat(nota -> nota.getIdNotaFiscal().equals(idDaNota)));
        verify(entregaPort).agendarEntrega(argThat(nota -> nota.getIdNotaFiscal().equals(idDaNota)));
        verify(financeiroPort).enviarNotaFiscalParaContasReceber(argThat(nota -> nota.getIdNotaFiscal().equals(idDaNota)));
    }

    @Test
    @DisplayName("E02 exemplo 2 (E02-RN-04, E02-RN-05, E02-NF-05): entrega fora do ar; os outros concluem, a entrega falha 5 vezes e vai para a DLQ")
    void e02Exemplo2_entregaForaDoAr() throws Exception {
        long idPedido = PedidoBase.novoId();
        doThrow(new IllegalStateException("entrega fora do ar")).when(entregaPort).agendarEntrega(daNota(idPedido));
        emitir(idPedido);

        pipe.transportar();

        await().atMost(PRAZO).until(() -> statusDa(idPedido, Sistema.ENTREGA) == StatusTarefa.FALHOU);
        TarefaIntegracao entrega = tarefas.buscar(idPedido, Sistema.ENTREGA).orElseThrow();
        assertThat(entrega.getTentativas()).isEqualTo(5);
        assertThat(entrega.getUltimoErro()).isEqualTo("IllegalStateException");
        for (Sistema outro : List.of(Sistema.REGISTRO, Sistema.ESTOQUE, Sistema.FINANCEIRO)) {
            await().atMost(PRAZO).until(() -> statusDa(idPedido, outro) == StatusTarefa.CONCLUIDA);
        }
        verify(entregaPort, times(5)).agendarEntrega(daNota(idPedido));
        await().atMost(PRAZO).until(() -> naDlq(Sistema.ENTREGA, idPedido));
        assertThat(fila.mensagensNaDlq(Sistema.ENTREGA)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("E02 exemplo 5 (E02-RN-06, E02-NF-11): seguindo o runbook, só a entrega que falhou é reprocessada, sem outra nota")
    void e02Exemplo5_reprocessamentoPeloRunbook() throws Exception {
        long idPedido = PedidoBase.novoId();
        doThrow(new IllegalStateException("entrega fora do ar")).when(entregaPort).agendarEntrega(daNota(idPedido));
        emitir(idPedido);
        pipe.transportar();
        await().atMost(PRAZO).until(() -> statusDa(idPedido, Sistema.ENTREGA) == StatusTarefa.FALHOU);
        await().atMost(PRAZO).until(() -> naDlq(Sistema.ENTREGA, idPedido));
        doNothing().when(entregaPort).agendarEntrega(daNota(idPedido));

        // Passo 2 do runbook: a tarefa volta a pendente, com as tentativas zeradas e no índice de pendentes.
        dynamoDb.updateItem(r -> r.tableName("tarefas_integracao")
                .key(Map.of("id_pedido", AttributeValue.fromN(Long.toString(idPedido)), "sistema", AttributeValue.fromS("ENTREGA")))
                .updateExpression("SET #status = :pendente, tentativas = :zero, fatia_pendente = :fatia, pendente_desde = :agora, "
                        + "versao = versao + :um REMOVE bloqueada_ate")
                .conditionExpression("#status = :falhou")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(":pendente", AttributeValue.fromS("PENDENTE"), ":falhou", AttributeValue.fromS("FALHOU"),
                        ":zero", AttributeValue.fromN("0"), ":um", AttributeValue.fromN("1"),
                        ":fatia", AttributeValue.fromS("PENDENTE#" + Math.floorMod(idPedido, 10)),
                        ":agora", AttributeValue.fromN(Long.toString(Instant.now().toEpochMilli())))));
        // Passo 3 do runbook: a mensagem sai da DLQ e volta à fila do sistema.
        Message daDlq = await().atMost(PRAZO).until(() -> mensagemNaDlq(Sistema.ENTREGA, idPedido), java.util.Objects::nonNull);
        sqs.sendMessage(envio -> envio.queueUrl(filas.fila(Sistema.ENTREGA)).messageBody(daDlq.body()));
        sqs.deleteMessage(exclusao -> exclusao.queueUrl(filas.dlq(Sistema.ENTREGA)).receiptHandle(daDlq.receiptHandle()));

        await().atMost(PRAZO).until(() -> statusDa(idPedido, Sistema.ENTREGA) == StatusTarefa.CONCLUIDA);
        verify(entregaPort, times(6)).agendarEntrega(daNota(idPedido));
        verify(registroPort, times(1)).registrarNotaFiscal(daNota(idPedido));
        verify(estoquePort, times(1)).enviarNotaFiscalParaBaixaEstoque(daNota(idPedido));
        verify(financeiroPort, times(1)).enviarNotaFiscalParaContasReceber(daNota(idPedido));
    }

    @Test
    @DisplayName("E02 exemplo 3 (E02-RN-02, E02-NF-06, E02-NF-10): evento perdido; a reconciliação recoloca as tarefas e os sistemas são acionados")
    void e02Exemplo3_reconciliacao() throws Exception {
        long idPedido = emitir();
        pipe.eventosNovos();

        assertThat(reconciliarTarefasUseCase.executar(Duration.ZERO)).isGreaterThanOrEqualTo(4);

        aguardarTodas(idPedido, StatusTarefa.CONCLUIDA);
    }

    @Test
    @DisplayName("E02 exemplo 4 (E02-RN-03, E02-NF-10): processo caiu com a tarefa em execução; vencido o bloqueio, ela é executada com o mesmo identificador")
    void e02Exemplo4_quedaNoMeioDaChamada() throws Exception {
        long idPedido = emitir();
        pipe.eventosNovos();
        TarefaIntegracao lida = tarefas.buscar(idPedido, Sistema.ENTREGA).orElseThrow();
        tarefas.salvar(lida.pegar(Instant.now().minus(TarefaIntegracao.BLOQUEIO).minusSeconds(1)), lida.getVersao());

        fila.publicar(idPedido, Sistema.ENTREGA);

        await().atMost(PRAZO).until(() -> statusDa(idPedido, Sistema.ENTREGA) == StatusTarefa.CONCLUIDA);
        verify(entregaPort).agendarEntrega(daNota(idPedido));
    }

    @Test
    @DisplayName("E02-NF-04: a mesma mensagem entregue duas vezes aciona o sistema uma vez")
    void e02Nf04_mensagemRepetida() throws Exception {
        long idPedido = emitir();
        pipe.eventosNovos();

        fila.publicar(idPedido, Sistema.FINANCEIRO);
        fila.publicar(idPedido, Sistema.FINANCEIRO);

        await().atMost(PRAZO).until(() -> statusDa(idPedido, Sistema.FINANCEIRO) == StatusTarefa.CONCLUIDA);
        // Tempo para a segunda mensagem ser processada: ela só sai da fila sem acionar o sistema de novo.
        await().pollDelay(Duration.ofSeconds(3)).atMost(PRAZO).until(() -> true);
        verify(financeiroPort, times(1)).enviarNotaFiscalParaContasReceber(daNota(idPedido));
    }

    @Test
    @DisplayName("E02-NF-03: mensagem com id_pedido numérico e campo desconhecido é aceita (convivência de versões)")
    void e02Nf03_campoDesconhecido() throws Exception {
        long idPedido = emitir();
        pipe.eventosNovos();

        sqs.sendMessage(envio -> envio.queueUrl(filas.fila(Sistema.ESTOQUE))
                .messageBody("{\"id_pedido\": " + idPedido + ", \"sistema\": \"ESTOQUE\", \"versao\": 2}"));

        await().atMost(PRAZO).until(() -> statusDa(idPedido, Sistema.ESTOQUE) == StatusTarefa.CONCLUIDA);
    }

    @Test
    @DisplayName("E02-NF-04, E02-NF-05: processamento mais longo que a visibilidade da fila não derruba o consumo e aciona o sistema uma vez")
    void e02Nf04_processamentoMaisLongoQueAVisibilidade() throws Exception {
        long idPedido = PedidoBase.novoId();
        doAnswer(invocacao -> {
            Thread.sleep(2500);
            return null;
        }).when(estoquePort).enviarNotaFiscalParaBaixaEstoque(daNota(idPedido));
        emitir(idPedido);
        pipe.eventosNovos();

        fila.publicar(idPedido, Sistema.ESTOQUE);

        await().atMost(PRAZO).until(() -> statusDa(idPedido, Sistema.ESTOQUE) == StatusTarefa.CONCLUIDA);
        await().pollDelay(Duration.ofSeconds(3)).atMost(PRAZO).until(() -> true);
        verify(estoquePort, times(1)).enviarNotaFiscalParaBaixaEstoque(daNota(idPedido));
    }

    private NotaFiscal daNota(long idPedido) {
        return argThat(nota -> nota != null && nota.getIdNotaFiscal().equals(notaDoRegistro(idPedido)));
    }

    private long emitir() throws Exception {
        return emitir(PedidoBase.novoId());
    }

    private long emitir(long idPedido) throws Exception {
        ObjectNode pedido = PedidoBase.novo().put("id_pedido", idPedido);
        mockMvc.perform(post("/api/pedido/gerarNotaFiscal").contentType(MediaType.APPLICATION_JSON).content(pedido.toString()))
                .andExpect(status().isOk());
        return pedido.get("id_pedido").longValue();
    }

    private void aguardarTodas(long idPedido, StatusTarefa esperado) {
        for (Sistema sistema : Sistema.values()) {
            await().atMost(PRAZO).until(() -> statusDa(idPedido, sistema) == esperado);
        }
    }

    private StatusTarefa statusDa(long idPedido, Sistema sistema) {
        return tarefas.buscar(idPedido, sistema).orElseThrow().getStatus();
    }

    private String notaDoRegistro(long idPedido) {
        String nota = dynamoDb.getItem(r -> r.tableName("notas")
                .key(Map.of("id_pedido", AttributeValue.fromN(Long.toString(idPedido))))).item().get("nota").s();
        return JSON.readTree(nota).get("idNotaFiscal").asString();
    }

    private Message mensagemNaDlq(Sistema sistema, long idPedido) {
        return sqs.receiveMessage(r -> r.queueUrl(filas.dlq(sistema)).maxNumberOfMessages(10).visibilityTimeout(0)).messages()
                .stream().filter(m -> m.body().contains("\"" + idPedido + "\"")).findFirst().orElse(null);
    }

    private boolean naDlq(Sistema sistema, long idPedido) {
        List<Message> mensagens = new ArrayList<>(sqs.receiveMessage(r -> r.queueUrl(filas.dlq(sistema))
                .maxNumberOfMessages(10).visibilityTimeout(0)).messages());
        return mensagens.stream().map(m -> JSON.readTree(m.body())).map(JsonNode::toString)
                .anyMatch(corpo -> corpo.contains("\"" + idPedido + "\""));
    }

}
