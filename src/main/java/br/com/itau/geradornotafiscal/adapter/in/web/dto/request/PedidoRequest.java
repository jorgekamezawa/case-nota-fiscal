package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contrato de entrada do pedido (E01-NF-01), convertido só depois da etapa 1 da validação.
 */
public record PedidoRequest(
        @JsonProperty("id_pedido") Long idPedido,
        @JsonProperty("data") LocalDate data,
        @JsonProperty("valor_total_itens") BigDecimal valorTotalItens,
        @JsonProperty("valor_frete") BigDecimal valorFrete,
        @JsonProperty("itens") List<ItemRequest> itens,
        @JsonProperty("destinatario") DestinatarioRequest destinatario) {
}
