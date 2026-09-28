package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.port.in.ReconciliarTarefasUseCase;
import br.com.itau.geradornotafiscal.application.port.out.FilaTarefasPort;
import br.com.itau.geradornotafiscal.application.port.out.TarefaIntegracaoPort;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Pega o que o caminho normal perdeu (evento não publicado, Pipe fora do ar). A mensagem duplicada, inclusive entre
 * instâncias, é absorvida pela execução única (E02-NF-06).
 */
@Service
@RequiredArgsConstructor
public class ReconciliarTarefasUseCaseImpl implements ReconciliarTarefasUseCase {

    private final Clock relogio;
    private final TarefaIntegracaoPort tarefaIntegracaoPort;
    private final FilaTarefasPort filaTarefasPort;

    @Override
    public int reconciliar(Duration abertasHaMaisDe) {
        List<TarefaIntegracao> paradas = tarefaIntegracaoPort.abertasDesdeAntesDe(relogio.instant().minus(abertasHaMaisDe));
        paradas.forEach(tarefa -> filaTarefasPort.publicar(tarefa.getIdPedido(), tarefa.getSistema()));
        return paradas.size();
    }
}
