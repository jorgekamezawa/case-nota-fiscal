package br.com.itau.geradornotafiscal.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Item {
	 @JsonProperty("id_item")
	    private String idItem;

	    @JsonProperty("descricao")
	    private String descricao;

	    @JsonProperty("valor_unitario")
	    private BigDecimal valorUnitario;

	    @JsonProperty("quantidade")
	    private int quantidade;




}

