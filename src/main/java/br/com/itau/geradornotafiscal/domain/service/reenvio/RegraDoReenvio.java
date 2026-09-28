package br.com.itau.geradornotafiscal.domain.service.reenvio;

import br.com.itau.geradornotafiscal.domain.exception.PedidoDivergenteException;
import org.springframework.stereotype.Component;

/**
 * Reenvio de um pedido que já tem nota: com o mesmo conteúdo vale a nota emitida; com outro, o pedido é recusado por
 * divergência (E03-RN-02, E03-RN-03). O conteúdo é comparado pelo hash do pedido (E03-RN-04).
 */
@Component
public class RegraDoReenvio {

    public void conferir(Long idPedido, String hashDaNotaEmitida, String hashRecebido) {
        if (!hashDaNotaEmitida.equals(hashRecebido)) {
            throw new PedidoDivergenteException(idPedido);
        }
    }
}
