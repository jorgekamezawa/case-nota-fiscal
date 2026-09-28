package br.com.itau.geradornotafiscal.domain.entity;

import br.com.itau.geradornotafiscal.domain.service.calculo.Arredondamento;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Nota fiscal, identificada pelo {@code idNotaFiscal}. Só nasce por {@link #emitir}.
 */
@Getter
public final class NotaFiscal {

    private final String idNotaFiscal;
    private final LocalDateTime data;
    private final BigDecimal valorTotalItens;
    private final BigDecimal valorFrete;
    private final List<ItemNotaFiscal> itens;
    private final Destinatario destinatario;

    private NotaFiscal(String idNotaFiscal, LocalDateTime data, BigDecimal valorTotalItens, BigDecimal valorFrete,
                       List<ItemNotaFiscal> itens, Destinatario destinatario) {
        this.idNotaFiscal = idNotaFiscal;
        this.data = data;
        this.valorTotalItens = valorTotalItens;
        this.valorFrete = valorFrete;
        this.itens = List.copyOf(itens);
        this.destinatario = destinatario;
    }

    /**
     * Emite a nota do pedido (E01-RN-17): identificador novo a cada emissão, total dos itens como no pedido e o
     * destinatário como recebido. Itens com tributo, frete e data chegam calculados.
     */
    public static NotaFiscal emitir(Pedido pedido, List<ItemNotaFiscal> itens, BigDecimal valorFrete, LocalDateTime data) {
        Objects.requireNonNull(pedido, "pedido obrigatório");
        Objects.requireNonNull(itens, "itens obrigatórios");
        Objects.requireNonNull(valorFrete, "valorFrete obrigatório");
        Objects.requireNonNull(data, "data obrigatória");
        return new NotaFiscal(UUID.randomUUID().toString(), data, Arredondamento.duasCasas(pedido.getValorTotalItens()),
                valorFrete, itens, pedido.getDestinatario());
    }

    /** Remonta a nota já emitida, lida do armazenamento, sem gerar identificador novo (E03-RN-02). */
    public static NotaFiscal reconstituir(String idNotaFiscal, LocalDateTime data, BigDecimal valorTotalItens,
                                          BigDecimal valorFrete, List<ItemNotaFiscal> itens, Destinatario destinatario) {
        Objects.requireNonNull(idNotaFiscal, "idNotaFiscal obrigatório");
        Objects.requireNonNull(data, "data obrigatória");
        Objects.requireNonNull(valorTotalItens, "valorTotalItens obrigatório");
        Objects.requireNonNull(valorFrete, "valorFrete obrigatório");
        Objects.requireNonNull(itens, "itens obrigatórios");
        Objects.requireNonNull(destinatario, "destinatario obrigatório");
        return new NotaFiscal(idNotaFiscal, data, valorTotalItens, valorFrete, itens, destinatario);
    }
}
