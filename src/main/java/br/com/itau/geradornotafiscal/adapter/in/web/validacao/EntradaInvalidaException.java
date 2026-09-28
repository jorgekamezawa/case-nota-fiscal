package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

import java.util.List;

/**
 * Pedido recusado no preenchimento ou no formato (etapa 1 da E01-RN-09), com todas as violações da etapa.
 */
public class EntradaInvalidaException extends RuntimeException {

    private final transient List<ViolacaoEntrada> violacoes;

    public EntradaInvalidaException(List<ViolacaoEntrada> violacoes) {
        super("Entrada inválida");
        this.violacoes = List.copyOf(violacoes);
    }

    public List<ViolacaoEntrada> violacoes() {
        return violacoes;
    }
}
