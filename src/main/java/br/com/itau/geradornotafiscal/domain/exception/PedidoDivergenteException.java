package br.com.itau.geradornotafiscal.domain.exception;

/**
 * Já existe nota para o {@code id_pedido} com outro conteúdo (E03-RN-03).
 */
public class PedidoDivergenteException extends RuntimeException {

    private final Long idPedido;

    public PedidoDivergenteException(Long idPedido) {
        super("Pedido com conteúdo diferente do que gerou a nota");
        this.idPedido = idPedido;
    }

    public Long idPedido() {
        return idPedido;
    }
}
