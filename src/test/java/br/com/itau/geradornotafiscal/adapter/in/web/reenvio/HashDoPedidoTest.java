package br.com.itau.geradornotafiscal.adapter.in.web.reenvio;

import br.com.itau.geradornotafiscal.PedidoBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Exemplos 1 a 9 do E-03 no nível do hash (E03-RN-04, E03-NF-02). O pedido chega como o serviço o lê: números como
 * decimal exato, com as casas enviadas.
 */
class HashDoPedidoTest {

    private static final JsonMapper LEITOR = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    private final HashDoPedido hashDoPedido = new HashDoPedido();

    static Stream<Arguments> exemplos() {
        return Stream.of(
                Arguments.of("#1 idêntico", (UnaryOperator<String>) p -> p, true),
                Arguments.of("#2 campos em outra ordem", (UnaryOperator<String>) HashDoPedidoTest::camposEmOutraOrdem, true),
                Arguments.of("#3 valor_unitario 50 em vez de 50.00",
                        (UnaryOperator<String>) p -> p.replace("\"valor_unitario\":50.00", "\"valor_unitario\":50"), true),
                Arguments.of("#4 regime_tributacao nulo em vez de ausente",
                        (UnaryOperator<String>) p -> p.replace("\"tipo_pessoa\":\"FISICA\"", "\"tipo_pessoa\":\"FISICA\",\"regime_tributacao\":null"), true),
                Arguments.of("#5 campo observacao a mais",
                        (UnaryOperator<String>) p -> p.replace("{\"id_pedido\"", "{\"observacao\":\"entregar à tarde\",\"id_pedido\""), true),
                Arguments.of("#6 CPF sem pontuação",
                        (UnaryOperator<String>) p -> p.replace(PedidoBase.CPF, "88740347095"), false),
                Arguments.of("#7 nome com um espaço a mais",
                        (UnaryOperator<String>) p -> p.replace("Fulano de Tal", "Fulano de  Tal"), false),
                Arguments.of("#8 itens em ordem trocada", (UnaryOperator<String>) HashDoPedidoTest::itensTrocados, false),
                Arguments.of("#9 data diferente",
                        (UnaryOperator<String>) p -> p.replace("2022-05-01", "2022-05-02"), false));
    }

    @ParameterizedTest(name = "E03 exemplo {0}: mesmo conteúdo? {2}")
    @MethodSource("exemplos")
    void e03Rn04_mesmoConteudo(String exemplo, UnaryOperator<String> mudanca, boolean mesmoConteudo) {
        String original = pedidoDeDoisItens();

        String hashOriginal = hash(original);
        String hashReenvio = hash(mudanca.apply(original));

        if (mesmoConteudo) {
            assertEquals(hashOriginal, hashReenvio);
        } else {
            assertNotEquals(hashOriginal, hashReenvio);
        }
    }

    @Test
    @DisplayName("E03-NF-02: números com mais de 15 dígitos significativos que diferem no último geram hashes diferentes")
    void e03Nf02_numerosLongosDiferentes() {
        String pedido = pedidoDeDoisItens();

        assertNotEquals(hash(pedido.replace("\"valor_frete\":10.00", "\"valor_frete\":12345678901234567.01")),
                hash(pedido.replace("\"valor_frete\":10.00", "\"valor_frete\":12345678901234567.02")));
    }

    @Test
    @DisplayName("E03-NF-02: número enviado como texto é conteúdo diferente do número")
    void e03Nf02_numeroComoTexto() {
        String pedido = pedidoDeDoisItens();

        assertNotEquals(hash(pedido), hash(pedido.replace("\"valor_frete\":10.00", "\"valor_frete\":\"10.00\"")));
    }

    @Test
    @DisplayName("E03-NF-02: o hash tem 64 caracteres hexadecimais")
    void e03Nf02_formato() {
        assertEquals(64, hash(pedidoDeDoisItens()).length());
    }

    private String hash(String pedido) {
        return hashDoPedido.calcular(LEITOR.readTree(pedido));
    }

    private static String pedidoDeDoisItens() {
        ObjectNode pedido = PedidoBase.itens(PedidoBase.novo(), PedidoBase.item("1", "50.00", 2), PedidoBase.item("2", "30.00", 1));
        return pedido.toString();
    }

    private static String camposEmOutraOrdem(String pedido) {
        ObjectNode original = (ObjectNode) LEITOR.readTree(pedido);
        ObjectNode invertido = LEITOR.createObjectNode();
        java.util.List<String> nomes = new java.util.ArrayList<>(original.propertyNames());
        java.util.Collections.reverse(nomes);
        nomes.forEach(nome -> invertido.set(nome, original.get(nome)));
        return invertido.toString();
    }

    private static String itensTrocados(String pedido) {
        ObjectNode original = (ObjectNode) LEITOR.readTree(pedido);
        JsonNode primeiro = original.get("itens").get(0);
        JsonNode segundo = original.get("itens").get(1);
        original.putArray("itens").add(segundo).add(primeiro);
        return original.toString();
    }
}
