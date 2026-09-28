package br.com.itau.geradornotafiscal.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato de saída da nota (E01-NF-02); a ordem dos campos é a do contrato atual.
 */
public record NotaFiscalResponse(
        @JsonProperty("id_nota_fiscal") String idNotaFiscal,
        @JsonProperty("data") LocalDateTime data,
        @JsonProperty("valor_total_itens") BigDecimal valorTotalItens,
        @JsonProperty("valor_frete") BigDecimal valorFrete,
        @JsonProperty("itens") List<ItemNotaFiscalResponse> itens,
        @JsonProperty("destinatario") DestinatarioResponse destinatario) {
}
