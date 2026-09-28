package br.com.itau.geradornotafiscal.domain.entity;

import br.com.itau.geradornotafiscal.domain.service.validacao.RegrasDoPedido;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Pedido de nota fiscal, identificado pelo {@code idPedido}. Só nasce por {@link #criar}, depois das regras de negócio.
 */
@Getter
public final class Pedido {

    private final Long idPedido;
    private final LocalDate data;
    private final BigDecimal valorTotalItens;
    private final BigDecimal valorFrete;
    private final List<Item> itens;
    private final Destinatario destinatario;

    private Pedido(Long idPedido, LocalDate data, BigDecimal valorTotalItens, BigDecimal valorFrete,
                   List<Item> itens, Destinatario destinatario) {
        this.idPedido = idPedido;
        this.data = data;
        this.valorTotalItens = valorTotalItens;
        this.valorFrete = valorFrete;
        this.itens = List.copyOf(itens);
        this.destinatario = destinatario;
    }

    /**
     * Cria o pedido conferindo as regras de negócio (etapa 2 da E01-RN-09); se alguma for violada, recusa com
     * todas as violações de uma vez.
     */
    public static Pedido criar(Long idPedido, LocalDate data, BigDecimal valorTotalItens, BigDecimal valorFrete,
                               List<Item> itens, Destinatario destinatario) {
        Objects.requireNonNull(valorTotalItens, "valorTotalItens obrigatório");
        Objects.requireNonNull(valorFrete, "valorFrete obrigatório");
        Objects.requireNonNull(itens, "itens obrigatórios");
        Objects.requireNonNull(destinatario, "destinatario obrigatório");
        RegrasDoPedido.validar(valorTotalItens, valorFrete, itens, destinatario);
        return new Pedido(idPedido, data, valorTotalItens, valorFrete, itens, destinatario);
    }
}
