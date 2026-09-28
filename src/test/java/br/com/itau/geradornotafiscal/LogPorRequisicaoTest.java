package br.com.itau.geradornotafiscal;

import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Toda requisição ao endpoint da nota gera log, com os campos do F04-NF-04.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
class LogPorRequisicaoTest {

    private static final String ENDPOINT = "/api/pedido/gerarNotaFiscal";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;
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

    @Test
    void f04Nf04_notaEmitidaComIdPedidoEIdNotaEmCamposProprios(CapturedOutput saida) throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        pedido.put("id_pedido", 4321);

        enviar(pedido.toString()).andExpect(status().isOk());

        JsonNode log = linha(saida, "Nota fiscal emitida");
        assertThat(log.get("id_pedido").asString()).isEqualTo("4321");
        assertThat(log.get("id_nota_fiscal").asString()).isNotBlank();
        assertThat(log.get("message").asString()).doesNotContain("4321");
    }

    @Test
    void f04Nf04_recusaComStatusETypesSemIdPedido(CapturedOutput saida) throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        pedido.put("id_pedido", 4322);
        pedido.put("valor_frete", -1);

        enviar(pedido.toString()).andExpect(status().isBadRequest());

        JsonNode log = linha(saida, "Pedido recusado");
        assertThat(log.get("status").asString()).isEqualTo("400");
        assertThat(log.get("types").toString()).contains("/erros/frete-negativo");
        assertThat(log.toString()).doesNotContain("4322");
    }

    @Test
    void f04Nf04_corpoInvalidoTambemGeraLog(CapturedOutput saida) throws Exception {
        enviar("{").andExpect(status().isBadRequest());

        assertThat(linha(saida, "Pedido recusado").get("types").toString()).contains("/erros/json-invalido");
    }

    @Test
    void f04Nf04_erroInesperadoGeraStackTrace(CapturedOutput saida) throws Exception {
        doThrow(new IllegalStateException("armazenamento fora do ar")).when(notaFiscalPersistenciaPort)
                .guardar(any(), any(), any(), any(), any());

        enviar(PedidoBase.novo().toString()).andExpect(status().isInternalServerError());

        JsonNode erro = linha(saida, "Erro inesperado ao gerar a nota fiscal").get("error");
        assertThat(erro.get("type").asString()).isEqualTo(IllegalStateException.class.getName());
        assertThat(erro.get("stack_trace").asString()).contains("armazenamento fora do ar").contains("\tat ");
    }

    private ResultActions enviar(String corpo) throws Exception {
        return mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private static JsonNode linha(CapturedOutput saida, String mensagem) {
        String linha = saida.getOut().lines().filter(l -> l.contains("\"" + mensagem + "\"")).reduce((a, b) -> b).orElseThrow();
        return JSON.readTree(linha);
    }
}
