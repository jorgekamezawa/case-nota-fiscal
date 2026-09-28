package br.com.itau.geradornotafiscal.application.port.in.result;

import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;

import java.time.Duration;

/**
 * Situação das tarefas de um sistema: abertas, idade da mais antiga (zero sem abertas) e mensagens na fila de erro
 * (E02-NF-08).
 */
public record SituacaoDoSistema(Sistema sistema, int pendentes, Duration maisAntiga, int mensagensNaDlq) {
}
