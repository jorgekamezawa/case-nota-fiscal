package br.com.itau.geradornotafiscal.application.port.out;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

/**
 * Registro da nota.
 */
public interface RegistroPort {

    void registrarNotaFiscal(NotaFiscal notaFiscal);
}
