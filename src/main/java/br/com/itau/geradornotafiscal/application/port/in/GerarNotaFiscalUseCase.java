package br.com.itau.geradornotafiscal.application.port.in;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.Pedido;

/**
 * Gera a nota de um pedido que já passou no preenchimento e no formato (etapa 1 da E01-RN-09).
 */
public interface GerarNotaFiscalUseCase {

    NotaFiscal gerarNotaFiscal(Pedido pedido);
}
