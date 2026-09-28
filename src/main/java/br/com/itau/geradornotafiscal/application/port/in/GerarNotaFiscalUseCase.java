package br.com.itau.geradornotafiscal.application.port.in;

import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.application.port.in.result.ResultadoDaEmissao;

/**
 * Gera a nota de um pedido que já passou no preenchimento e no formato (etapa 1 da E01-RN-09).
 * Se o pedido já tem nota, devolve a nota guardada sem conferir o restante (E03-RN-01); senão, as regras de negócio
 * (etapa 2) são conferidas ao criar o pedido no domínio.
 */
public interface GerarNotaFiscalUseCase {

    ResultadoDaEmissao executar(GerarNotaFiscalCommand comando);
}
