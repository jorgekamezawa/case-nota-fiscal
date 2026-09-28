package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.port.in.result.SituacaoDoSistema;
import br.com.itau.geradornotafiscal.application.port.out.FilaTarefasPort;
import br.com.itau.geradornotafiscal.application.port.out.TarefaIntegracaoPort;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarSituacaoDasTarefasUseCaseImplTest {

    private static final Instant AGORA = Instant.parse("2026-01-15T15:30:00Z");

    @Mock
    private TarefaIntegracaoPort tarefaIntegracaoPort;
    @Mock
    private FilaTarefasPort filaTarefasPort;

    @Test
    @DisplayName("E02-NF-08: por sistema, quantas tarefas estão abertas, há quanto tempo a mais antiga e quantas mensagens há na DLQ")
    void e02Nf08_situacaoPorSistema() {
        when(tarefaIntegracaoPort.abertasDesdeAntesDe(AGORA)).thenReturn(List.of(
                TarefaIntegracao.criarPendente(1L, Sistema.ENTREGA, AGORA.minusSeconds(960)),
                TarefaIntegracao.criarPendente(2L, Sistema.ENTREGA, AGORA.minusSeconds(30)),
                TarefaIntegracao.criarPendente(2L, Sistema.ESTOQUE, AGORA.minusSeconds(30))));
        when(filaTarefasPort.mensagensNaDlq(any())).thenAnswer(chamada -> chamada.getArgument(0) == Sistema.ENTREGA ? 2 : 0);

        List<SituacaoDoSistema> situacao = new ConsultarSituacaoDasTarefasUseCaseImpl(Clock.fixed(AGORA, ZoneOffset.UTC),
                tarefaIntegracaoPort, filaTarefasPort).executar();

        assertEquals(List.of(
                new SituacaoDoSistema(Sistema.REGISTRO, 0, Duration.ZERO, 0),
                new SituacaoDoSistema(Sistema.ESTOQUE, 1, Duration.ofSeconds(30), 0),
                new SituacaoDoSistema(Sistema.ENTREGA, 2, Duration.ofSeconds(960), 2),
                new SituacaoDoSistema(Sistema.FINANCEIRO, 0, Duration.ZERO, 0)), situacao);
    }
}
