package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import br.com.itau.geradornotafiscal.adapter.in.web.validacao.DataNoFormatoIso;
import br.com.itau.geradornotafiscal.adapter.in.web.validacao.DuasCasasDecimais;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contrato de entrada do pedido (E01-NF-01). As anotações conferem o preenchimento e as casas decimais, parte da
 * etapa 1 da validação (E01-RN-01, E01-RN-08); o tipo de cada campo é conferido na conversão do JSON.
 */
public record PedidoRequest(
        @JsonProperty("id_pedido") @NotNull Long idPedido,
        @JsonProperty("data") @JsonDeserialize(using = DataNoFormatoIso.class) LocalDate data,
        @JsonProperty("valor_total_itens") @NotNull @DuasCasasDecimais BigDecimal valorTotalItens,
        @JsonProperty("valor_frete") @NotNull @DuasCasasDecimais BigDecimal valorFrete,
        @JsonProperty("itens") @NotEmpty List<@NotNull @Valid ItemRequest> itens,
        @JsonProperty("destinatario") @NotNull @Valid DestinatarioRequest destinatario) {

    // Cópia que não muda, aceitando nulo: ausência e elemento nulo são conferidos pelas anotações (E01-RN-01).
    public PedidoRequest {
        itens = itens == null ? null : Collections.unmodifiableList(new ArrayList<>(itens));
    }
}
