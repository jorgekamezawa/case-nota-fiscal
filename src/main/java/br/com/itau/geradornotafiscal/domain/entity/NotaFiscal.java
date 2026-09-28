package br.com.itau.geradornotafiscal.domain.entity;

import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Nota fiscal emitida, identificada pelo {@code idNotaFiscal}.
 */
public record NotaFiscal(
        String idNotaFiscal,
        LocalDateTime data,
        BigDecimal valorTotalItens,
        BigDecimal valorFrete,
        List<ItemNotaFiscal> itens,
        Destinatario destinatario) {

    public NotaFiscal {
        itens = List.copyOf(itens);
    }
}
