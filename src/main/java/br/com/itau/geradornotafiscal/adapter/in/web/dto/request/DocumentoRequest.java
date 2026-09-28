package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record DocumentoRequest(
        @JsonProperty("numero") @NotNull String numero,
        @JsonProperty("tipo") @NotNull TipoDocumento tipo) {
}
