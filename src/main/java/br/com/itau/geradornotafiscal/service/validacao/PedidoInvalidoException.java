package br.com.itau.geradornotafiscal.service.validacao;

import lombok.Getter;

import java.util.List;

@Getter
public class PedidoInvalidoException extends RuntimeException {
    private final transient List<Violacao> violacoes;

    public PedidoInvalidoException(List<Violacao> violacoes) {
        super("Pedido inválido");
        this.violacoes = List.copyOf(violacoes);
    }
}
