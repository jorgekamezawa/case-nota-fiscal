package br.com.itau.geradornotafiscal.domain.entity;

import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pedido de nota fiscal, identificado pelo {@code idPedido}.
 */
public record Pedido(
        Long idPedido,
        LocalDate data,
        BigDecimal valorTotalItens,
        BigDecimal valorFrete,
        List<Item> itens,
        Destinatario destinatario) {

    public Pedido {
        itens = List.copyOf(itens);
    }
}
