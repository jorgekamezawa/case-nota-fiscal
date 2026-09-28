package br.com.itau.geradornotafiscal.application.port.out;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

/**
 * Baixa de estoque dos itens da nota.
 */
public interface EstoquePort {

    void enviarNotaFiscalParaBaixaEstoque(NotaFiscal notaFiscal);
}
