package br.com.itau.geradornotafiscal.adapter.in.web.controller;

import br.com.itau.geradornotafiscal.PedidoBase;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato da API (E01-NF-01, E01-NF-02, E01-NF-03). Só pode mudar para ficar mais rígido.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GeradorNFControllerContratoTest {

    private static final String ENDPOINT = "/api/pedido/gerarNotaFiscal";

    private static final Predicate<JsonNode> TEXTO = JsonNode::isString;
    private static final Predicate<JsonNode> INTEIRO = JsonNode::isIntegralNumber;
    // Confere o texto do número, não só o valor: 100.00, nunca 100 nem 100.0.
    private static final Predicate<JsonNode> MONETARIO = no -> no.isBigDecimal() && no.decimalValue().scale() == 2;
    private static final Predicate<JsonNode> DATA_HORA =
            no -> no.isString() && no.asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?");
    private static final Predicate<JsonNode> TEXTO_OU_NULO = no -> no.isString() || no.isNull();

    private static final Map<String, Predicate<JsonNode>> NOTA = Map.of(
            "id_nota_fiscal", TEXTO,
            "data", DATA_HORA,
            "valor_total_itens", MONETARIO,
            "valor_frete", MONETARIO,
            "itens", JsonNode::isArray,
            "destinatario", JsonNode::isObject);

    private static final Map<String, Predicate<JsonNode>> ITEM = Map.of(
            "id_item", TEXTO,
            "descricao", TEXTO,
            "valor_unitario", MONETARIO,
            "quantidade", INTEIRO,
            "valor_tributo_item", MONETARIO);

    private static final Map<String, Predicate<JsonNode>> DESTINATARIO = Map.of(
            "nome", TEXTO,
            "tipo_pessoa", TEXTO,
            "regime_tributacao", TEXTO_OU_NULO,
            "documentos", JsonNode::isArray,
            "enderecos", JsonNode::isArray);

    private static final Map<String, Predicate<JsonNode>> DOCUMENTO = Map.of(
            "numero", TEXTO,
            "tipo", TEXTO);

    private static final Map<String, Predicate<JsonNode>> ENDERECO = Map.of(
            "cep", TEXTO,
            "logradouro", TEXTO,
            "numero", TEXTO,
            "estado", TEXTO,
            "complemento", TEXTO,
            "finalidade", TEXTO,
            "regiao", TEXTO,
            "bairro", TEXTO,
            "cidade", TEXTO,
            "pais", TEXTO);

    private static final ObjectMapper LEITOR_EXATO = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"payloads/teste-pf.json", "payloads/teste-pj-simples.json"})
    @DisplayName("E01-NF-01, E01-NF-02: pedido atual aceito e resposta com os mesmos campos e tipos")
    void e01Nf01_e01Nf02_contratoDeSucesso(String arquivo) throws Exception {
        String pedido = PedidoBase.comIdNovo(StreamUtils.copyToString(new ClassPathResource(arquivo).getInputStream(), StandardCharsets.UTF_8));

        String resposta = mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(pedido))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> violacoes = new ArrayList<>();
        JsonNode nota = LEITOR_EXATO.readTree(resposta);
        conferir("", nota, NOTA, violacoes);
        conferirLista("itens", nota.path("itens"), ITEM, violacoes);
        JsonNode destinatario = nota.path("destinatario");
        conferir("destinatario.", destinatario, DESTINATARIO, violacoes);
        conferirLista("destinatario.documentos", destinatario.path("documentos"), DOCUMENTO, violacoes);
        conferirLista("destinatario.enderecos", destinatario.path("enderecos"), ENDERECO, violacoes);
        assertEquals(List.of(), violacoes, resposta);
    }

    private static final Map<String, Predicate<JsonNode>> PROBLEMA = Map.of(
            "type", no -> no.isString() && no.asString().startsWith("/erros/"),
            "title", TEXTO,
            "status", INTEIRO,
            "detail", TEXTO,
            "campos", JsonNode::isArray);

    private static final Map<String, Predicate<JsonNode>> CAMPO_INVALIDO = Map.of(
            "campo", TEXTO,
            "type", no -> no.isString() && no.asString().startsWith("/erros/"),
            "detail", TEXTO);

    @Test
    @DisplayName("E01-NF-03: recusa responde 400 em Problem Details com a lista de campos")
    void e01Nf03_contratoDeRecusa() throws Exception {
        String pedido = PedidoBase.comIdNovo(StreamUtils.copyToString(new ClassPathResource("payloads/teste-pf.json").getInputStream(), StandardCharsets.UTF_8))
                .replace("\"valor_frete\": 10.0", "\"valor_frete\": -5.0");

        String resposta = mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(pedido))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> violacoes = new ArrayList<>();
        JsonNode problema = LEITOR_EXATO.readTree(resposta);
        conferir("", problema, PROBLEMA, violacoes);
        conferirLista("campos", problema.path("campos"), CAMPO_INVALIDO, violacoes);
        assertEquals(List.of(), violacoes, resposta);
        assertEquals(400, problema.get("status").intValue());
    }

    private static void conferirLista(String caminho, JsonNode lista, Map<String, Predicate<JsonNode>> campos,
                                      List<String> violacoes) {
        assertTrue(lista.size() > 0, caminho + " vazio");
        for (int i = 0; i < lista.size(); i++) {
            conferir(caminho + "[" + i + "].", lista.get(i), campos, violacoes);
        }
    }

    private static void conferir(String caminho, JsonNode objeto, Map<String, Predicate<JsonNode>> campos,
                                 List<String> violacoes) {
        Set<String> recebidos = new TreeSet<>();
        recebidos.addAll(objeto.propertyNames());
        if (!recebidos.equals(new TreeSet<>(campos.keySet()))) {
            violacoes.add(caminho + " campos " + recebidos + ", esperados " + new TreeSet<>(campos.keySet()));
        }
        campos.forEach((campo, tipo) -> {
            if (objeto.has(campo) && !tipo.test(objeto.get(campo))) {
                violacoes.add(caminho + campo + " com tipo inesperado: " + objeto.get(campo));
            }
        });
    }
}
