package br.com.itau.geradornotafiscal.adapter.in.web.controller;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.application.port.in.GerarNotaFiscalUseCase;
import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static br.com.itau.geradornotafiscal.PedidoBase.endereco;
import static br.com.itau.geradornotafiscal.PedidoBase.enderecos;
import static br.com.itau.geradornotafiscal.PedidoBase.item;
import static br.com.itau.geradornotafiscal.PedidoBase.pj;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comportamento do endpoint, com as integrações simuladas por mock (sem as esperas).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GeradorNFControllerTest {

    private static final String ENDPOINT = "/api/pedido/gerarNotaFiscal";

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private GerarNotaFiscalUseCase gerarNotaFiscalUseCase;

    @MockitoBean
    private EstoquePort estoquePort;
    @MockitoBean
    private RegistroPort registroPort;
    @MockitoBean
    private EntregaPort entregaPort;
    @MockitoBean
    private FinanceiroPort financeiroPort;

    @Test
    @DisplayName("E01 cálculo #26 (E01-RN-17): nota devolve bairro, cidade e país do endereço")
    void e01Calculo26_enderecoComBairroCidadePais() throws Exception {
        ObjectNode entrega = endereco("ENTREGA", "SUDESTE");
        entrega.put("bairro", "Mooca");
        entrega.put("cidade", "São Paulo");
        entrega.put("pais", "Brasil");

        enviar(enderecos(PedidoBase.novo(), entrega))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinatario.enderecos[0].bairro").value("Mooca"))
                .andExpect(jsonPath("$.destinatario.enderecos[0].cidade").value("São Paulo"))
                .andExpect(jsonPath("$.destinatario.enderecos[0].pais").value("Brasil"));
    }

    @ParameterizedTest(name = "E01 validação {0}")
    @MethodSource("defeitosDeValidacao")
    void e01ValidacaoDefeitos_recusadosPeloEndpoint(String exemplo, UnaryOperator<ObjectNode> mudanca,
                                                    String campo, String type) throws Exception {
        enviar(mudanca.apply(PedidoBase.novo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.campo == '" + campo + "')].type").value("/erros/" + type));
        // E01-RN-09: pedido recusado não gera nota nem aciona integração.
        verifyNoInteractions(estoquePort, registroPort, entregaPort, financeiroPort);
    }

    static Stream<Arguments> defeitosDeValidacao() {
        return Stream.of(
                Arguments.of("#6 (E01-RN-07)", (UnaryOperator<ObjectNode>) p -> {
                    p.putArray("itens").add(item("1", "5000.00", 1));
                    return p;
                }, "valor_total_itens", "total-divergente"),
                Arguments.of("#10 (E01-RN-01)", (UnaryOperator<ObjectNode>) p -> {
                    ((ObjectNode) pj(p, "SIMPLES_NACIONAL").get("destinatario")).remove("regime_tributacao");
                    return p;
                }, "destinatario.regime_tributacao", "campo-obrigatorio"),
                Arguments.of("#11 (E01-RN-03)", (UnaryOperator<ObjectNode>) p -> pj(p, "OUTROS"),
                        "destinatario.regime_tributacao", "regime-nao-atendido"),
                Arguments.of("#17 (E01-RN-06)", (UnaryOperator<ObjectNode>) p -> enderecos(p, endereco("COBRANCA", "SUDESTE")),
                        "destinatario.enderecos", "sem-endereco-de-entrega"),
                Arguments.of("#18 (E01-RN-06)", (UnaryOperator<ObjectNode>) p -> {
                    ObjectNode semRegiao = endereco("ENTREGA", "SUDESTE");
                    semRegiao.remove("regiao");
                    return enderecos(p, semRegiao, endereco("COBRANCA_ENTREGA", "SUL"));
                }, "destinatario.enderecos[0].regiao", "campo-obrigatorio"),
                Arguments.of("#27 (E01-RN-01)", (UnaryOperator<ObjectNode>) p -> {
                    p.putArray("itens");
                    return p;
                }, "itens", "campo-obrigatorio"),
                Arguments.of("#13 (E01-RN-08)", (UnaryOperator<ObjectNode>) p -> {
                    ((ObjectNode) p.get("destinatario")).put("regime_tributacao", "MEI");
                    return p;
                }, "destinatario.regime_tributacao", "valor-nao-aceito"),
                Arguments.of("#20 (E01-RN-08)", (UnaryOperator<ObjectNode>) p -> {
                    ((ObjectNode) p.get("destinatario")).put("tipo_pessoa", "ESTRANGEIRA");
                    return p;
                }, "destinatario.tipo_pessoa", "valor-nao-aceito"),
                Arguments.of("#25 (E01-RN-08)", (UnaryOperator<ObjectNode>) p -> {
                    ((ObjectNode) p.get("itens").get(0)).put("quantidade", "2");
                    return p;
                }, "itens[0].quantidade", "formato-invalido"),
                Arguments.of("#28 (E01-RN-08)", (UnaryOperator<ObjectNode>) p -> p.put("data", "2022-13-45"),
                        "data", "formato-invalido"));
    }

    @Test
    @DisplayName("E01 validação #23 (E01-RN-09, E01-RN-10, F03-NF-09): sem destinatário, a etapa 1 recusa sozinha e o caso de uso não é chamado")
    void e01Validacao23_etapa1RecusaSemChamarCasoDeUso() throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        pedido.remove("destinatario");
        pedido.put("valor_frete", new BigDecimal("-5.00"));

        enviar(pedido)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].campo").value("destinatario"))
                .andExpect(jsonPath("$.campos[0].type").value("/erros/campo-obrigatorio"));
        verify(gerarNotaFiscalUseCase, never()).gerarNotaFiscal(any());
        verifyNoInteractions(estoquePort, registroPort, entregaPort, financeiroPort);
    }

    @Test
    @DisplayName("E01-RN-09, F03-NF-09: dois erros de tipo no mesmo pedido, a recusa traz um por vez e o caso de uso não é chamado")
    void e01Rn09_errosDeTipoUmPorVez() throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        ((ObjectNode) pedido.get("itens").get(0)).put("quantidade", "2");
        pedido.put("data", "x");

        enviar(pedido)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/erros/pedido-invalido"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].type").value("/erros/formato-invalido"));
        verify(gerarNotaFiscalUseCase, never()).gerarNotaFiscal(any());
    }

    @Test
    @DisplayName("E01 cálculo #27 (E01-RN-17): documento devolvido como enviado, sem a limpeza")
    void e01Calculo27_documentoDevolvidoSemLimpeza() throws Exception {
        enviar(PedidoBase.novo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinatario.documentos[0].numero").value(PedidoBase.CPF));
        verify(gerarNotaFiscalUseCase).gerarNotaFiscal(any());
    }

    @Test
    @DisplayName("E01 validação #22 (E01-RN-09, E01-NF-03): dois campos na mesma recusa, sem dado pessoal")
    void e01Validacao22_recusaComDoisCamposSemDadoPessoal() throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        ((ObjectNode) pedido.get("itens").get(0)).put("quantidade", -1);
        pedido.put("valor_frete", new BigDecimal("-5.00"));

        String corpo = enviar(pedido)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/erros/pedido-invalido"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[?(@.campo == 'itens[0].quantidade')].type").value("/erros/quantidade-invalida"))
                .andExpect(jsonPath("$.campos[?(@.campo == 'valor_frete')].type").value("/erros/frete-negativo"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        verifyNoInteractions(estoquePort, registroPort, entregaPort, financeiroPort);
        for (String dadoPessoal : List.of("Fulano", "887.403.470-95", "88740347095", "Av do Estado", "03105003")) {
            assertFalse(corpo.contains(dadoPessoal), dadoPessoal);
        }
    }

    @ParameterizedTest(name = "E01-NF-03: corpo {0} responde 400")
    @ValueSource(strings = {"{\"id_pedido\": ", "[]", "texto"})
    void e01Nf03_corpoQueNaoEPedidoJson(String corpo) throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/erros/json-invalido"))
                .andExpect(jsonPath("$.campos").doesNotExist());
    }

    @Test
    @DisplayName("F02-NF-02 (exceção aceita): método não permitido responde 405 em Problem Details")
    void f02Nf02_metodoNaoPermitidoEmProblemDetails() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    @DisplayName("F02-NF-02 (exceção aceita): tipo de conteúdo não suportado responde 415 em Problem Details")
    void f02Nf02_tipoDeConteudoNaoSuportadoEmProblemDetails() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.TEXT_PLAIN).content(PedidoBase.novo().toString()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415));
        verifyNoInteractions(estoquePort, registroPort, entregaPort, financeiroPort);
    }

    @Test
    @DisplayName("E01-NF-03: erro inesperado responde 500 sem stack trace nem mensagem interna")
    void e01Nf03_erroInesperadoSemDetalheInterno() throws Exception {
        doThrow(new IllegalStateException("falha interna do estoque")).when(estoquePort).enviarNotaFiscalParaBaixaEstoque(any());

        String corpo = enviar(PedidoBase.novo())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/erros/erro-interno"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertFalse(corpo.contains("falha interna"));
        assertFalse(corpo.contains("IllegalStateException"));
        assertFalse(corpo.contains("at br.com"));
    }

    private ResultActions enviar(ObjectNode pedido) throws Exception {
        return mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(pedido.toString()));
    }
}
