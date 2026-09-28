package br.com.itau.geradornotafiscal.application.port.in;

/**
 * O que fazer com a mensagem depois de processar a tarefa (E02-NF-04, E02-NF-05).
 */
public enum ResultadoTarefa {
    /** Tarefa terminada: a mensagem sai da fila. */
    APAGAR,
    /** Nova tentativa, ou outro processo está com a tarefa: a mensagem volta quando vence a visibilidade. */
    MANTER,
    /** 5ª falha: a mensagem vai para a fila de erro. */
    MOVER_PARA_DLQ
}
