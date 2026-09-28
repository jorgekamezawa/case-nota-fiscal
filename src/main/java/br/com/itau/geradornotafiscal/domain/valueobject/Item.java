package br.com.itau.geradornotafiscal.domain.valueobject;

import java.math.BigDecimal;

// Quantidade como decimal para a regra de quantidade inteira (E01-RN-04) ser conferida no domínio, sem truncar.
public record Item(String idItem, String descricao, BigDecimal valorUnitario, BigDecimal quantidade) {
}
