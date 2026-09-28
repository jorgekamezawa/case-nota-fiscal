package br.com.itau.geradornotafiscal.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ItemNotaFiscalResponse(
        @JsonProperty("id_item") String idItem,
        @JsonProperty("descricao") String descricao,
        @JsonProperty("valor_unitario") BigDecimal valorUnitario,
        @JsonProperty("quantidade") int quantidade,
        @JsonProperty("valor_tributo_item") BigDecimal valorTributoItem) {
}
