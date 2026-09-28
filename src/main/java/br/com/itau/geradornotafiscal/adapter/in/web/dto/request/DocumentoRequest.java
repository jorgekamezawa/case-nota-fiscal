package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentoRequest(
        @JsonProperty("numero") String numero,
        @JsonProperty("tipo") TipoDocumento tipo) {
}
