package br.com.itau.geradornotafiscal.application.port.in.command;

import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados do pedido que já passaram no preenchimento e no formato (etapa 1 da E01-RN-09).
 */
public record GerarNotaFiscalCommand(
        Long idPedido,
        LocalDate data,
        BigDecimal valorTotalItens,
        BigDecimal valorFrete,
        List<Item> itens,
        Destinatario destinatario) {
}
