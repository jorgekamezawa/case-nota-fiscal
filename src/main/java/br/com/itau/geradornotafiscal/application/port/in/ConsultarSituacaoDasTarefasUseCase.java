package br.com.itau.geradornotafiscal.application.port.in;

import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;

import java.time.Duration;
import java.util.List;

/**
 * Situação das tarefas de cada sistema, para as métricas e os alertas das integrações (E02-NF-08).
 */
public interface ConsultarSituacaoDasTarefasUseCase {

    List<SituacaoDoSistema> consultar();

    /** Tarefas abertas, idade da mais antiga (zero sem abertas) e mensagens na fila de erro. */
    record SituacaoDoSistema(Sistema sistema, int pendentes, Duration maisAntiga, int mensagensNaDlq) {
    }
}
