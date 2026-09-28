package br.com.itau.geradornotafiscal.domain.exception;

import java.util.List;

/**
 * Pedido recusado pelas regras de negócio (etapa 2 da E01-RN-09), com todas as violações da etapa.
 */
public class PedidoInvalidoException extends RuntimeException {

    private final transient List<Violacao> violacoes;

    public PedidoInvalidoException(List<Violacao> violacoes) {
        super("Pedido inválido");
        this.violacoes = List.copyOf(violacoes);
    }

    public List<Violacao> violacoes() {
        return violacoes;
    }
}
