package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import br.com.itau.geradornotafiscal.domain.valueobject.Finalidade;
import br.com.itau.geradornotafiscal.domain.valueobject.Regiao;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EnderecoRequest(
        @JsonProperty("cep") String cep,
        @JsonProperty("logradouro") String logradouro,
        @JsonProperty("numero") String numero,
        @JsonProperty("bairro") String bairro,
        @JsonProperty("cidade") String cidade,
        @JsonProperty("estado") String estado,
        @JsonProperty("pais") String pais,
        @JsonProperty("complemento") String complemento,
        @JsonProperty("finalidade") Finalidade finalidade,
        @JsonProperty("regiao") Regiao regiao) {
}
