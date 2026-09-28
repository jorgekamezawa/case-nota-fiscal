package br.com.itau.geradornotafiscal.application.port.in;

import java.time.Duration;

/**
 * Recoloca na fila as tarefas abertas há mais tempo que o limite (E02-NF-06).
 */
public interface ReconciliarTarefasUseCase {

    /** Quantidade de tarefas recolocadas na fila. */
    int executar(Duration abertasHaMaisDe);
}
