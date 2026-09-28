package br.com.itau.geradornotafiscal.application.port.out;

import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tarefas de integração, gravadas com a nota (E02-NF-02).
 */
public interface TarefaIntegracaoPort {

    Optional<TarefaIntegracao> buscar(Long idPedido, Sistema sistema);

    /** Grava a tarefa só se a guardada ainda tem a versão lida; {@code false} se outro processo mudou antes (E02-NF-04). */
    boolean salvar(TarefaIntegracao tarefa, long versaoLida);

    /** Tarefas ainda abertas (pendentes ou em execução) desde antes do limite (E02-NF-06). */
    List<TarefaIntegracao> abertasDesdeAntesDe(Instant limite);
}
