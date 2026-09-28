package br.com.itau.geradornotafiscal.application.port.out;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

/**
 * Envio da nota para o contas a receber.
 */
public interface FinanceiroPort {

    void enviarNotaFiscalParaContasReceber(NotaFiscal notaFiscal);
}
