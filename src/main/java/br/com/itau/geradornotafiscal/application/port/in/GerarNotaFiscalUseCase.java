package br.com.itau.geradornotafiscal.application.port.in;

import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

/**
 * Gera a nota de um pedido que já passou no preenchimento e no formato (etapa 1 da E01-RN-09).
 * As regras de negócio (etapa 2) são conferidas ao criar o pedido no domínio.
 */
public interface GerarNotaFiscalUseCase {

    NotaFiscal gerarNotaFiscal(GerarNotaFiscalCommand comando);
}
