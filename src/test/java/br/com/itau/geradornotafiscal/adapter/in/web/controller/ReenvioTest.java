package br.com.itau.geradornotafiscal.adapter.in.web.controller;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.application.exception.ArmazenamentoIndisponivelException;
import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Exemplos do E-03 pelo endpoint, com a nota guardada no emulador do DynamoDB e as integrações simuladas por mock.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReenvioTest {

    private static final String ENDPOINT = "/api/pedido/gerarNotaFiscal";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoSpyBean
    private NotaFiscalPersistenciaPort notaFiscalPersistenciaPort;
    @MockitoBean
    private EstoquePort estoquePort;
    @MockitoBean
    private RegistroPort registroPort;
    @MockitoBean
    private EntregaPort entregaPort;
    @MockitoBean
    private FinanceiroPort financeiroPort;

    static Stream<Arguments> mesmoConteudo() {
        return Stream.of(
                Arguments.of("#1 idêntico", (UnaryOperator<ObjectNode>) p -> p),
                Arguments.of("#2 campos em outra ordem", (UnaryOperator<ObjectNode>) ReenvioTest::camposEmOutraOrdem),
                Arguments.of("#3 valor_unitario 50 em vez de 50.00", (UnaryOperator<ObjectNode>) p -> {
                    ((ObjectNode) p.get("itens").get(0)).put("valor_unitario", 50);
                    return p;
                }),
                Arguments.of("#4 regime_tributacao nulo em vez de ausente", (UnaryOperator<ObjectNode>) p -> {
                    ((ObjectNode) p.get("destinatario")).putNull("regime_tributacao");
                    return p;
                }),
                Arguments.of("#5 campo observacao a mais", (UnaryOperator<ObjectNode>) p -> p.put("observacao", "entregar à tarde")));
    }

    @ParameterizedTest(name = "E03 exemplo {0}: mesma nota, idêntica à primeira resposta, sem acionar de novo")
    @MethodSource("mesmoConteudo")
    void e03Rn02_mesmaNota(String exemplo, UnaryOperator<ObjectNode> mudanca) throws Exception {
        ObjectNode pedido = pedidoDeDoisItens();
        MockHttpServletResponse primeira = enviar(pedido.toString());
        double emitidas = meterRegistry.counter("notas.emitidas").count();

        MockHttpServletResponse reenvio = enviar(mudanca.apply(pedido.deepCopy()).toString());

        assertEquals(200, reenvio.getStatus());
        assertEquals(primeira.getContentAsString(StandardCharsets.UTF_8), reenvio.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(emitidas, meterRegistry.counter("notas.emitidas").count(), "E03-NF-04: reenvio não é nota emitida");
        verify(estoquePort, times(1)).enviarNotaFiscalParaBaixaEstoque(any());
        verify(registroPort, times(1)).registrarNotaFiscal(any());
        verify(entregaPort, times(1)).agendarEntrega(any());
        verify(financeiroPort, times(1)).enviarNotaFiscalParaContasReceber(any());
    }

    static Stream<Arguments> conteudoDiferente() {
        return Stream.of(
                Arguments.of("#6 CPF sem pontuação", (UnaryOperator<ObjectNode>) p -> {
                    ((ObjectNode) p.get("destinatario").get("documentos").get(0)).put("numero", "88740347095");
                    return p;
                }),
                Arguments.of("#7 nome com um espaço a mais", (UnaryOperator<ObjectNode>) p -> {
                    ((ObjectNode) p.get("destinatario")).put("nome", "Fulano de  Tal");
                    return p;
                }),
                Arguments.of("#8 itens em ordem trocada", (UnaryOperator<ObjectNode>) p -> {
                    JsonNode primeiro = p.get("itens").get(0);
                    JsonNode segundo = p.get("itens").get(1);
                    p.putArray("itens").add(segundo).add(primeiro);
                    return p;
                }),
                Arguments.of("#9 data diferente", (UnaryOperator<ObjectNode>) p -> p.put("data", "2022-05-02")),
                Arguments.of("#10 frete -1,00, recusado na etapa 2 se não houvesse nota",
                        (UnaryOperator<ObjectNode>) p -> PedidoBase.frete(p, "-1.00")),
                Arguments.of("#10 (etapa 1) frete como texto, recusado na etapa 1 se não houvesse nota",
                        (UnaryOperator<ObjectNode>) p -> p.put("valor_frete", "10.00")));
    }

    @ParameterizedTest(name = "E03 exemplo {0}: recusa por divergência, sem dado do pedido nem da nota")
    @MethodSource("conteudoDiferente")
    void e03Rn03_divergente(String exemplo, UnaryOperator<ObjectNode> mudanca) throws Exception {
        ObjectNode pedido = pedidoDeDoisItens();
        String nota = enviar(pedido.toString()).getContentAsString(StandardCharsets.UTF_8);
        double recusas = meterRegistry.counter("recusas", "type", "/erros/pedido-divergente").count();

        MockHttpServletResponse reenvio = enviar(mudanca.apply(pedido.deepCopy()).toString());

        assertEquals(422, reenvio.getStatus());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, reenvio.getContentType());
        String corpo = reenvio.getContentAsString(StandardCharsets.UTF_8);
        JsonNode problema = JSON.readTree(corpo);
        assertEquals("/erros/pedido-divergente", problema.get("type").asString());
        assertFalse(problema.has("campos"));
        assertFalse(corpo.contains("Fulano"), corpo);
        assertFalse(corpo.contains("887"), corpo);
        assertFalse(corpo.contains(JSON.readTree(nota).get("id_nota_fiscal").asString()), corpo);
        assertEquals(recusas + 1, meterRegistry.counter("recusas", "type", "/erros/pedido-divergente").count());
        verify(estoquePort, times(1)).enviarNotaFiscalParaBaixaEstoque(any());
    }

    @Test
    @DisplayName("E03 exemplo 11 (E03-RN-01): id_pedido como texto é recusado na etapa 1")
    void e03Exemplo11_idComoTexto() throws Exception {
        ObjectNode pedido = PedidoBase.novo().put("id_pedido", "abc");

        JsonNode problema = JSON.readTree(enviar(pedido.toString()).getContentAsString(StandardCharsets.UTF_8));

        assertEquals("/erros/pedido-invalido", problema.get("type").asString());
        assertEquals("id_pedido", problema.get("campos").get(0).get("campo").asString());
        assertEquals("/erros/formato-invalido", problema.get("campos").get(0).get("type").asString());
    }

    @Test
    @DisplayName("E03 exemplo 12 (E03-RN-01): id_pedido e frete ausentes são recusados juntos na etapa 1")
    void e03Exemplo12_idEFreteAusentes() throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        pedido.remove("id_pedido");
        pedido.remove("valor_frete");

        MockHttpServletResponse resposta = enviar(pedido.toString());

        assertEquals(400, resposta.getStatus());
        JsonNode campos = JSON.readTree(resposta.getContentAsString(StandardCharsets.UTF_8)).get("campos");
        List<String> recusados = new ArrayList<>();
        campos.forEach(campo -> recusados.add(campo.get("campo").asString() + " " + campo.get("type").asString()));
        Collections.sort(recusados);
        assertEquals(List.of("id_pedido /erros/campo-obrigatorio", "valor_frete /erros/campo-obrigatorio"), recusados);
    }

    @Test
    @DisplayName("E03 exemplo 13 (E03-RN-01): id_pedido sem nota passa pela validação completa")
    void e03Exemplo13_semNotaValidacaoCompleta() throws Exception {
        JsonNode problema = JSON.readTree(enviar(PedidoBase.frete(PedidoBase.novo(), "-1.00").toString())
                .getContentAsString(StandardCharsets.UTF_8));

        assertEquals("/erros/pedido-invalido", problema.get("type").asString());
        assertEquals("/erros/frete-negativo", problema.get("campos").get(0).get("type").asString());
    }

    @Test
    @DisplayName("E03 exemplo 14 (E03-RN-01): primeiro envio recusado como indisponível; o reenvio emite normalmente")
    void e03Exemplo14_reenvioDepoisDeIndisponivel() throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        long idPedido = pedido.get("id_pedido").longValue();
        doThrow(new ArmazenamentoIndisponivelException(new IllegalStateException()))
                .doCallRealMethod()
                .when(notaFiscalPersistenciaPort).guardar(eq(idPedido), any(), any(), any());

        assertEquals(503, enviar(pedido.toString()).getStatus());
        assertEquals(200, enviar(pedido.toString()).getStatus());
        verify(estoquePort, times(1)).enviarNotaFiscalParaBaixaEstoque(any());
    }

    @Test
    @DisplayName("E03 exemplo 15 (E03-RN-05): dois envios idênticos simultâneos geram uma nota, devolvida aos dois")
    void e03Exemplo15_simultaneosIdenticos() throws Exception {
        ObjectNode pedido = PedidoBase.novo();

        List<MockHttpServletResponse> respostas = simultaneos(pedido, pedido.toString(), pedido.toString());

        assertEquals(200, respostas.get(0).getStatus());
        assertEquals(200, respostas.get(1).getStatus());
        assertEquals(idDaNota(respostas.get(0)), idDaNota(respostas.get(1)));
        verify(notaFiscalPersistenciaPort, times(2)).guardar(eq(pedido.get("id_pedido").longValue()), any(), any(), any());
        verify(estoquePort, times(1)).enviarNotaFiscalParaBaixaEstoque(any());
    }

    @Test
    @DisplayName("E03 exemplo 16 (E03-RN-05): dois envios simultâneos diferentes geram uma nota; o outro é divergente")
    void e03Exemplo16_simultaneosDiferentes() throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        ObjectNode outro = pedido.deepCopy().put("data", "2022-05-02");

        List<MockHttpServletResponse> respostas = simultaneos(pedido, pedido.toString(), outro.toString());

        List<Integer> status = new ArrayList<>(List.of(respostas.get(0).getStatus(), respostas.get(1).getStatus()));
        Collections.sort(status);
        assertEquals(List.of(200, 422), status);
        verify(notaFiscalPersistenciaPort, times(2)).guardar(eq(pedido.get("id_pedido").longValue()), any(), any(), any());
        verify(estoquePort, times(1)).enviarNotaFiscalParaBaixaEstoque(any());
    }

    // Os dois envios só procuram a nota depois de os dois chegarem: nenhum a encontra, e as duas gravações disputam.
    private List<MockHttpServletResponse> simultaneos(ObjectNode pedido, String primeiro, String segundo) throws Exception {
        long idPedido = pedido.get("id_pedido").longValue();
        CyclicBarrier juntos = new CyclicBarrier(2);
        AtomicInteger buscas = new AtomicInteger();
        doAnswer(invocacao -> {
            if (buscas.incrementAndGet() <= 2) {
                juntos.await(10, TimeUnit.SECONDS);
            }
            return invocacao.callRealMethod();
        }).when(notaFiscalPersistenciaPort).buscar(idPedido);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MockHttpServletResponse> um = executor.submit(() -> enviar(primeiro));
            Future<MockHttpServletResponse> dois = executor.submit(() -> enviar(segundo));
            return List.of(um.get(30, TimeUnit.SECONDS), dois.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private MockHttpServletResponse enviar(String corpo) throws Exception {
        return mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse();
    }

    private static String idDaNota(MockHttpServletResponse resposta) throws Exception {
        return JSON.readTree(resposta.getContentAsString(StandardCharsets.UTF_8)).get("id_nota_fiscal").asString();
    }

    private static ObjectNode pedidoDeDoisItens() {
        return PedidoBase.itens(PedidoBase.novo(), PedidoBase.item("1", "50.00", 2), PedidoBase.item("2", "30.00", 1));
    }

    private static ObjectNode camposEmOutraOrdem(ObjectNode pedido) {
        List<String> nomes = new ArrayList<>(pedido.propertyNames());
        Collections.reverse(nomes);
        ObjectNode invertido = JSON.createObjectNode();
        nomes.forEach(nome -> invertido.set(nome, pedido.get(nome)));
        return invertido;
    }
}
