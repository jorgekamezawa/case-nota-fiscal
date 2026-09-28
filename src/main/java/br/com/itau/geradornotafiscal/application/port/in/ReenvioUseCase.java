package br.com.itau.geradornotafiscal.application.port.in;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

import java.util.Optional;

/**
 * Reconhece o reenvio pelo {@code id_pedido} e pelo hash do conteúdo (E03-RN-01 a E03-RN-03).
 */
public interface ReenvioUseCase {

    /**
     * Nota já emitida para o mesmo conteúdo, ou vazio se o pedido não tem nota. Lança
     * {@code PedidoDivergenteException} se a nota existe e o conteúdo é outro.
     */
    Optional<NotaFiscal> executar(Long idPedido, String hashPedido);
}
