package br.com.itau.geradornotafiscal;

import br.com.itau.geradornotafiscal.adapter.out.entrega.EntregaAgendamentoCliente;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traces e métricas da requisição e das integrações (F04-NF-02, F04-NF-05, F04-NF-06, F04-NF-07).
 * O cliente da entrega é mock para evitar a espera de 5 s e simular a falha; as outras integrações rodam de verdade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
class TracesEMetricasTest {

    private static final String ENDPOINT = "/api/pedido/gerarNotaFiscal";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_PAI = "00f067aa0ba902b7";
    private static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();

    @TestConfiguration
    static class ExportadorEmMemoria {
        @Bean
        SpanExporter exportadorEmMemoria() {
            return SPANS;
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SdkTracerProvider tracerProvider;
    @Autowired
    private MeterRegistry meterRegistry;
    @MockitoBean
    private EntregaAgendamentoCliente entregaAgendamentoCliente;

    @BeforeEach
    void limpar() {
        // O envio é em lote: esvazia antes de limpar para spans do teste anterior não chegarem depois.
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        SPANS.reset();
    }

    @Test
    void f04Nf02_traceparentRecebidoEContinuado() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .header("traceparent", "00-" + TRACE_ID + "-" + SPAN_PAI + "-01")
                        .content(PedidoBase.novo().toString()))
                .andExpect(status().isOk());

        assertThat(spans()).filteredOn(s -> s.getParentSpanId().equals(SPAN_PAI))
                .singleElement()
                .satisfies(s -> assertThat(s.getTraceId()).isEqualTo(TRACE_ID));
    }

    @Test
    void f04Nf02_umSpanPorIntegracaoNoMesmoTrace() throws Exception {
        enviar(PedidoBase.novo()).andExpect(status().isOk());

        List<SpanData> integracoes = spans().stream().filter(s -> s.getName().equals("integracao")).toList();
        assertThat(integracoes).extracting(s -> s.getAttributes().get(stringKey("sistema")))
                .containsExactlyInAnyOrder("estoque", "registro", "entrega", "financeiro");
        assertThat(integracoes).extracting(SpanData::getTraceId).containsOnly(integracoes.getFirst().getTraceId());
        assertThat(integracoes).allSatisfy(s -> assertThat(s.getEndEpochNanos()).isGreaterThan(s.getStartEpochNanos()));
    }

    @Test
    void f04Nf02_f04Nf05_erroNaIntegracaoMarcadoNoSpanSemAMensagem() throws Exception {
        doThrow(new IllegalStateException("destinatário " + PedidoBase.CPF))
                .when(entregaAgendamentoCliente).criarAgendamentoEntrega(any());

        enviar(PedidoBase.novo()).andExpect(status().isInternalServerError());

        SpanData entrega = spans().stream()
                .filter(s -> "entrega".equals(s.getAttributes().get(stringKey("sistema"))))
                .findFirst().orElseThrow();
        assertThat(entrega.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(entrega.getStatus().getDescription()).isEmpty();
        EventData excecao = entrega.getEvents().stream().filter(e -> e.getName().equals("exception")).findFirst().orElseThrow();
        assertThat(excecao.getAttributes().get(stringKey("exception.type"))).isEqualTo(IllegalStateException.class.getName());
        assertThat(excecao.getAttributes().size()).isEqualTo(1);
        assertThat(spans().toString()).doesNotContain(PedidoBase.CPF);
    }

    @Test
    void f04Nf06_metricasDasRequisicoesEDaJvm() throws Exception {
        enviar(PedidoBase.novo()).andExpect(status().isOk());

        assertThat(meterRegistry.find("http.server.requests").tag("uri", ENDPOINT).tag("status", "200").timer())
                .isNotNull();
        assertThat(meterRegistry.find("jvm.memory.used").meters()).isNotEmpty();
    }

    @Test
    void f04Nf07_notasEmitidasEDuracaoPorIntegracao() throws Exception {
        double antes = meterRegistry.counter("notas.emitidas").count();

        enviar(PedidoBase.novo()).andExpect(status().isOk());

        assertThat(meterRegistry.counter("notas.emitidas").count()).isEqualTo(antes + 1);
        assertThat(meterRegistry.find("integracao").timers())
                .extracting(t -> t.getId().getTag("sistema"))
                .contains("estoque", "registro", "entrega", "financeiro");
    }

    @Test
    void f04Nf07_recusasPorTypeDistinto() throws Exception {
        ObjectNode doisErrosDeRegra = PedidoBase.novo();
        doisErrosDeRegra.put("valor_frete", -1);
        doisErrosDeRegra.put("valor_total_itens", 1);
        double frete = recusas("/erros/frete-negativo");
        double total = recusas("/erros/total-divergente");
        double jsonInvalido = recusas("/erros/json-invalido");

        enviar(doisErrosDeRegra).andExpect(status().isBadRequest());
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());

        assertThat(recusas("/erros/frete-negativo")).isEqualTo(frete + 1);
        assertThat(recusas("/erros/total-divergente")).isEqualTo(total + 1);
        assertThat(recusas("/erros/json-invalido")).isEqualTo(jsonInvalido + 1);
    }

    @Test
    void f04Nf07_rotulosSemIdPedido() throws Exception {
        ObjectNode pedido = PedidoBase.novo();
        pedido.put("id_pedido", 987654321);

        enviar(pedido).andExpect(status().isOk());

        assertThat(meterRegistry.getMeters()).flatExtracting(m -> m.getId().getTags())
                .allSatisfy(tag -> {
                    assertThat(tag.getKey()).doesNotContain("pedido");
                    assertThat(tag.getValue()).doesNotContain("987654321");
                });
    }

    private double recusas(String type) {
        return meterRegistry.counter("recusas", "type", type).count();
    }

    private ResultActions enviar(ObjectNode pedido) throws Exception {
        return mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(pedido.toString()));
    }

    private List<SpanData> spans() {
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        return SPANS.getFinishedSpanItems();
    }
}
