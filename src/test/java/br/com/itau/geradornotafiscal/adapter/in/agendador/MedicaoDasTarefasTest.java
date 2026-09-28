package br.com.itau.geradornotafiscal.adapter.in.agendador;

import br.com.itau.geradornotafiscal.application.port.in.ConsultarSituacaoDasTarefasUseCase;
import br.com.itau.geradornotafiscal.application.port.in.ConsultarSituacaoDasTarefasUseCase.SituacaoDoSistema;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicaoDasTarefasTest {

    @Test
    @DisplayName("E02-NF-08: pendentes, idade da mais antiga e mensagens na DLQ por sistema, sem id_pedido nos rótulos")
    void e02Nf08_medidoresPorSistema() {
        SimpleMeterRegistry metricas = new SimpleMeterRegistry();
        ConsultarSituacaoDasTarefasUseCase situacao = () -> List.of(
                new SituacaoDoSistema(Sistema.ENTREGA, 3, Duration.ofMinutes(16), 2),
                new SituacaoDoSistema(Sistema.ESTOQUE, 0, Duration.ZERO, 0));

        new MedicaoDasTarefas(situacao, metricas).medir();

        assertEquals(3.0, metricas.get("tarefas.pendentes").tag("sistema", "entrega").gauge().value());
        assertEquals(960.0, metricas.get("tarefas.pendente.mais.antiga").tag("sistema", "entrega").gauge().value());
        assertEquals(2.0, metricas.get("fila.dlq.mensagens").tag("sistema", "entrega").gauge().value());
        assertEquals(0.0, metricas.get("fila.dlq.mensagens").tag("sistema", "estoque").gauge().value());
        assertThat(metricas.getMeters()).map(Meter::getId).allSatisfy(id ->
                assertThat(id.getTags()).allSatisfy(tag -> assertThat(tag.getKey()).isEqualTo("sistema")));
    }
}
