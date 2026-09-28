package br.com.itau.geradornotafiscal.model;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Builder
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class Pedido {
	 @JsonProperty("id_pedido")
	    private int idPedido;

	    @JsonProperty("data")
	    private LocalDate data;

	    @JsonProperty("valor_total_itens")
	    private BigDecimal valorTotalItens;

	    @JsonProperty("valor_frete")
	    private BigDecimal valorFrete;

	    @JsonProperty("itens")
	    private List<Item> itens;

	    @JsonProperty("destinatario")
	    private Destinatario destinatario;

}
