package br.com.itau.geradornotafiscal.application.port.out;

import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;

/**
 * Fila de tarefas de cada sistema (E02-NF-03).
 */
public interface FilaTarefasPort {

    void publicar(Long idPedido, Sistema sistema);

    /** Mensagens aproximadas na fila de erro do sistema (E02-NF-08). */
    int mensagensNaDlq(Sistema sistema);
}
