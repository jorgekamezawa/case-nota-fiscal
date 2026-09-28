package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import br.com.itau.geradornotafiscal.adapter.in.web.validacao.DuasCasasDecimais;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemRequest(
        @JsonProperty("id_item") String idItem,
        @JsonProperty("descricao") String descricao,
        @JsonProperty("valor_unitario") @NotNull @DuasCasasDecimais BigDecimal valorUnitario,
        @JsonProperty("quantidade") @NotNull BigDecimal quantidade) {
}
