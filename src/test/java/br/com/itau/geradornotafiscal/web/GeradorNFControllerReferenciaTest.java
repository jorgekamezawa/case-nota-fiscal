package br.com.itau.geradornotafiscal.web;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.service.impl.EntregaService;
import br.com.itau.geradornotafiscal.service.impl.EstoqueService;
import br.com.itau.geradornotafiscal.service.impl.FinanceiroService;
import br.com.itau.geradornotafiscal.service.impl.RegistroService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Respostas de referência gravadas antes da troca de versão (F02-NF-02): status, Content-Type e corpo
 * precisam continuar iguais, campo a campo, na mesma ordem e com o mesmo texto dos números.
 * Sem arquivo de referência, a resposta obtida vai para target/referencia-gerada para revisão, e o teste falha.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GeradorNFControllerReferenciaTest {

    private static final String ENDPOINT = "/api/pedido/gerarNotaFiscal";
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final ObjectMapper LEITOR_EXATO = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-01-15T15:30:45.123Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EstoqueService estoqueService;
    @MockBean
    private RegistroService registroService;
    @MockBean
    private EntregaService entregaService;
    @MockBean
    private FinanceiroService financeiroService;

    static Stream<Arguments> casos() throws Exception {
        ObjectNode exemplo22 = PedidoBase.novo();
        ((ObjectNode) exemplo22.get("itens").get(0)).put("quantidade", -1);
        exemplo22.put("valor_frete", new BigDecimal("-5.00"));

        return Stream.of(
                Arguments.of("sucesso-pf", ENDPOINT, recurso("paylods/teste-pf.json"), false),
                Arguments.of("sucesso-pj", ENDPOINT, recurso("paylods/teste-pj-simples.json"), false),
                Arguments.of("sucesso-barra-final", ENDPOINT + "/", recurso("paylods/teste-pf.json"), false),
                Arguments.of("recusa-exemplo-22", ENDPOINT, exemplo22.toString(), false),
                Arguments.of("corpo-nao-json", ENDPOINT, "texto", false),
                Arguments.of("erro-inesperado", ENDPOINT, recurso("paylods/teste-pf.json"), true));
    }

    @ParameterizedTest(name = "F02-NF-02: {0}")
    @MethodSource("casos")
    void f02Nf02_respostaIgualAReferencia(String caso, String url, String corpo, boolean integracaoFalha) throws Exception {
        if (integracaoFalha) {
            doThrow(new IllegalStateException("falha interna do estoque"))
                    .when(estoqueService).enviarNotaFiscalParaBaixaEstoque(any());
        }

        MockHttpServletResponse resposta = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse();

        String obtida = LEITOR_EXATO.writeValueAsString(normalizada(resposta));
        String arquivo = "referencia/" + caso + ".json";
        ClassPathResource referencia = new ClassPathResource(arquivo);
        if (!referencia.exists()) {
            Path gerada = Path.of("target", "referencia-gerada", caso + ".json");
            Files.createDirectories(gerada.getParent());
            Files.writeString(gerada, obtida, StandardCharsets.UTF_8);
            fail("Sem referência para " + caso + "; resposta obtida gravada em " + gerada + " para revisão");
        }
        String esperada = LEITOR_EXATO.writeValueAsString(LEITOR_EXATO.readTree(recurso(arquivo)));
        assertEquals(esperada, obtida);
    }

    private static JsonNode normalizada(MockHttpServletResponse resposta) throws Exception {
        ObjectNode registro = LEITOR_EXATO.createObjectNode();
        registro.put("status", resposta.getStatus());
        registro.put("content_type", resposta.getContentType());
        JsonNode corpo = LEITOR_EXATO.readTree(resposta.getContentAsString(StandardCharsets.UTF_8));
        // O identificador da nota é aleatório: confere o formato e sai da comparação.
        if (corpo.has("id_nota_fiscal")) {
            assertTrue(corpo.get("id_nota_fiscal").asText().matches(UUID), corpo.get("id_nota_fiscal").asText());
            ((ObjectNode) corpo).put("id_nota_fiscal", "<uuid>");
        }
        registro.set("corpo", corpo);
        return registro;
    }

    private static String recurso(String caminho) throws Exception {
        return StreamUtils.copyToString(new ClassPathResource(caminho).getInputStream(), StandardCharsets.UTF_8);
    }
}
