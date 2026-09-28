package br.com.itau.geradornotafiscal.domain.valueobject;

import java.math.BigDecimal;

public record ItemNotaFiscal(
        String idItem,
        String descricao,
        BigDecimal valorUnitario,
        BigDecimal quantidade,
        BigDecimal valorTributoItem) {
}
