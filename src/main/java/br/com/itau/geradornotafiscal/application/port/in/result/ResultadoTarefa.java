package br.com.itau.geradornotafiscal.application.port.in.result;

/**
 * O que aconteceu com a tarefa ao processar a mensagem; define o que fazer com ela (E02-NF-04, E02-NF-05).
 */
public enum ResultadoTarefa {
    /** Sistema acionado com sucesso: a mensagem sai da fila. */
    CONCLUIDA,
    /** Mensagem repetida de tarefa já terminada (ou que não existe mais): a mensagem sai da fila. */
    JA_TERMINADA,
    /** Falha com nova tentativa: a mensagem volta quando vence a visibilidade. */
    NOVA_TENTATIVA,
    /** Outro processo está com a tarefa: a mensagem fica para a nova tentativa dele. */
    EM_EXECUCAO_POR_OUTRO,
    /** 5ª falha: a mensagem vai para a fila de erro. */
    FALHOU
}
