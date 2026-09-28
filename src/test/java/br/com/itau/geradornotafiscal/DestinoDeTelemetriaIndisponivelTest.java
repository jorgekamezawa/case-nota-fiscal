package br.com.itau.geradornotafiscal;

import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Com a exportação ligada e o destino fora do ar, a resposta não muda (F04-NF-09).
 * O log do exportador é silenciado porque a falha de envio é o cenário esperado.
 */
@SpringBootTest(properties = {
        "management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:1/v1/traces",
        "management.opentelemetry.logging.export.otlp.endpoint=http://localhost:1/v1/logs",
        "management.otlp.metrics.export.enabled=true",
        "management.otlp.metrics.export.url=http://localhost:1/v1/metrics",
        "logging.level.io.opentelemetry=off",
        "logging.level.io.micrometer.registry.otlp=off"
})
@AutoConfigureMockMvc
@AutoConfigureTracing
class DestinoDeTelemetriaIndisponivelTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SdkTracerProvider tracerProvider;
    @MockitoBean
    private EstoquePort estoquePort;
    @MockitoBean
    private RegistroPort registroPort;
    @MockitoBean
    private EntregaPort entregaPort;
    @MockitoBean
    private FinanceiroPort financeiroPort;

    @Test
    void f04Nf09_destinoIndisponivelNaoAfetaAResposta() throws Exception {
        mockMvc.perform(post("/api/pedido/gerarNotaFiscal").contentType(MediaType.APPLICATION_JSON)
                        .content(PedidoBase.novo().toString()))
                .andExpect(status().isOk());
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);

        mockMvc.perform(post("/api/pedido/gerarNotaFiscal").contentType(MediaType.APPLICATION_JSON)
                        .content(PedidoBase.novo().toString()))
                .andExpect(status().isOk());
    }
}
