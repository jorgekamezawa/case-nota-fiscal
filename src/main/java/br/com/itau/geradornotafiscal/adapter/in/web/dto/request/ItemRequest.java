package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ItemRequest(
        @JsonProperty("id_item") String idItem,
        @JsonProperty("descricao") String descricao,
        @JsonProperty("valor_unitario") BigDecimal valorUnitario,
        @JsonProperty("quantidade") BigDecimal quantidade) {
}
