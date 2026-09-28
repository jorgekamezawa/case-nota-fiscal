package br.com.itau.geradornotafiscal.application.port.in;

import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;

/**
 * Aciona o sistema da tarefa com a nota do pedido, uma única vez mesmo com a mensagem repetida (E02-RN-03 a E02-RN-05).
 */
public interface ExecutarTarefaUseCase {

    TarefaExecutada executar(Long idPedido, Sistema sistema);
}
