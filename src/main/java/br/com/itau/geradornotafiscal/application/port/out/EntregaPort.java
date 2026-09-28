package br.com.itau.geradornotafiscal.application.port.out;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

/**
 * Agendamento da entrega.
 */
public interface EntregaPort {

    void agendarEntrega(NotaFiscal notaFiscal);
}
