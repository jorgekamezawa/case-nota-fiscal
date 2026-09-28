package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.port.in.ConsultarSituacaoDasTarefasUseCase;
import br.com.itau.geradornotafiscal.application.port.out.FilaTarefasPort;
import br.com.itau.geradornotafiscal.application.port.out.TarefaIntegracaoPort;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultarSituacaoDasTarefasUseCaseImpl implements ConsultarSituacaoDasTarefasUseCase {

    private final Clock relogio;
    private final TarefaIntegracaoPort tarefaIntegracaoPort;
    private final FilaTarefasPort filaTarefasPort;

    @Override
    public List<SituacaoDoSistema> consultar() {
        Instant agora = relogio.instant();
        List<TarefaIntegracao> abertas = tarefaIntegracaoPort.abertasDesdeAntesDe(agora);
        return Arrays.stream(Sistema.values()).map(sistema -> {
            List<TarefaIntegracao> doSistema = abertas.stream().filter(tarefa -> tarefa.getSistema() == sistema).toList();
            Duration maisAntiga = doSistema.stream().map(TarefaIntegracao::getPendenteDesde)
                    .min(Instant::compareTo).map(desde -> Duration.between(desde, agora)).orElse(Duration.ZERO);
            return new SituacaoDoSistema(sistema, doSistema.size(), maisAntiga, filaTarefasPort.mensagensNaDlq(sistema));
        }).toList();
    }
}
