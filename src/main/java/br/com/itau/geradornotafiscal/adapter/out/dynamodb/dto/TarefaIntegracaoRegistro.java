package br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto;

/**
 * Tarefa como fica na tabela {@code tarefas_integracao}, só com {@code id_pedido}, sistema e controle, sem dado pessoal
 * (E02-NF-02). {@code fatiaPendente} e {@code pendenteDesde} só existem com a tarefa aberta: são a chave do índice
 * {@code pendentes}, que assim guarda só as abertas. Instantes em milissegundos desde 1970.
 */
public record TarefaIntegracaoRegistro(
        Long idPedido,
        String sistema,
        String status,
        int tentativas,
        String ultimoErro,
        Long bloqueadaAte,
        String fatiaPendente,
        Long pendenteDesde,
        long versao) {
}
