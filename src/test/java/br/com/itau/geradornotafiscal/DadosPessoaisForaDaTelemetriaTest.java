package br.com.itau.geradornotafiscal;

import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nome, documento e endereço do destinatário fora de logs, spans e métricas (F04-NF-05), com cada linha de log
 * ligada ao trace (F04-NF-03).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
class DadosPessoaisForaDaTelemetriaTest {

    private static final String ENDPOINT = "/api/pedido/gerarNotaFiscal";
    private static final List<String> DADOS_PESSOAIS = List.of("Fulano de Tal", "887.403.470-9", "8874034709",
            PedidoBase.CNPJ, "49695613000180", "Av do Estado");
    private static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();
    private static final InMemoryLogRecordExporter LOGS = InMemoryLogRecordExporter.create();

    @TestConfiguration
    static class ExportadoresEmMemoria {
        @Bean
        SpanExporter spansEmMemoria() {
            return SPANS;
        }

        @Bean
        LogRecordExporter logsEmMemoria() {
            return LOGS;
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OpenTelemetry openTelemetry;
    @Autowired
    private SdkTracerProvider tracerProvider;
    @Autowired
    private SdkLoggerProvider loggerProvider;
    @MockitoBean
    private EstoquePort estoquePort;
    @MockitoBean
    private RegistroPort registroPort;
    @MockitoBean
    private EntregaPort entregaPort;
    @MockitoBean
    private FinanceiroPort financeiroPort;

    @BeforeEach
    void preparar() {
        // O appender é estático no Logback: com vários contextos de teste na mesma JVM, aponta para o deste.
        OpenTelemetryAppender.install(openTelemetry);
        esvaziar();
        SPANS.reset();
        LOGS.reset();
    }

    @Test
    void f04Nf05_pedidoValidoSemDadoPessoal(CapturedOutput saida) throws Exception {
        enviar(PedidoBase.novo()).andExpect(status().isOk());

        assertSemDadoPessoal(saida);
    }

    @Test
    void f04Nf05_recusaNaEtapa1SemDadoPessoal(CapturedOutput saida) throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        ((ObjectNode) pedido.get("destinatario").get("documentos").get(0))
                .set("numero", JsonNodeFactory.instance.arrayNode().add(PedidoBase.CPF));
        ((ObjectNode) pedido.get("destinatario")).put("nome", 42);

        enviar(pedido).andExpect(status().isBadRequest());

        assertSemDadoPessoal(saida);
    }

    @Test
    void f04Nf05_recusaNaEtapa2SemDadoPessoal(CapturedOutput saida) throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        ((ObjectNode) pedido.get("destinatario").get("documentos").get(0)).put("numero", "887.403.470-96");

        enviar(pedido).andExpect(status().isBadRequest());

        assertSemDadoPessoal(saida);
    }

    @Test
    void f04Nf05_erroComDocumentoNaMensagemEhMascarado(CapturedOutput saida) throws Exception {
        doThrow(new IllegalStateException("falha para " + PedidoBase.CPF,
                new IllegalArgumentException("causa " + PedidoBase.CNPJ)))
                .when(entregaPort).agendarEntrega(any());

        enviar(PedidoBase.novo()).andExpect(status().isInternalServerError());

        assertSemDadoPessoal(saida);
        assertThat(saida.getOut()).contains("java.lang.IllegalStateException: falha para ***")
                .contains("java.lang.IllegalArgumentException: causa ***");
        assertThat(logsOtlp()).anySatisfy(log -> assertThat(log.getAttributes().toString())
                .contains("falha para ***").contains("causa ***"));
    }

    @Test
    void f04Nf03_cadaLinhaComTraceIdESpanId(CapturedOutput saida) throws Exception {
        enviar(PedidoBase.novo()).andExpect(status().isOk());

        List<String> linhasDaRequisicao = saida.getOut().lines().filter(l -> l.contains("Nota fiscal emitida")).toList();
        assertThat(linhasDaRequisicao).isNotEmpty().allSatisfy(l -> assertThat(l)
                .containsPattern("\"trace_id\":\"[0-9a-f]{32}\"").containsPattern("\"span_id\":\"[0-9a-f]{16}\""));
    }

    private void assertSemDadoPessoal(CapturedOutput saida) {
        esvaziar();
        String spans = SPANS.getFinishedSpanItems().toString();
        String logs = logsOtlp().toString();
        assertThat(DADOS_PESSOAIS).allSatisfy(dado -> {
            assertThat(saida.getOut()).doesNotContain(dado);
            assertThat(spans).doesNotContain(dado);
            assertThat(logs).doesNotContain(dado);
        });
    }

    private List<LogRecordData> logsOtlp() {
        esvaziar();
        return List.copyOf(LOGS.getFinishedLogRecordItems());
    }

    private void esvaziar() {
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        loggerProvider.forceFlush().join(5, TimeUnit.SECONDS);
    }

    private ResultActions enviar(ObjectNode pedido) throws Exception {
        return mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(pedido.toString()));
    }
}
