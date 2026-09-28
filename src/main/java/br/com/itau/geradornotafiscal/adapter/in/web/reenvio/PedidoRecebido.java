package br.com.itau.geradornotafiscal.adapter.in.web.reenvio;

/**
 * O que se lê do corpo antes da conversão: o {@code id_pedido}, quando é um número inteiro, e o hash do pedido.
 */
public record PedidoRecebido(Long idPedido, String hashPedido) {
}
