package br.com.itau.geradornotafiscal.application.port.in;

import br.com.itau.geradornotafiscal.application.port.in.result.SituacaoDoSistema;

import java.util.List;

/**
 * Situação das tarefas de cada sistema, para as métricas e os alertas das integrações (E02-NF-08).
 */
public interface ConsultarSituacaoDasTarefasUseCase {

    List<SituacaoDoSistema> executar();
}
