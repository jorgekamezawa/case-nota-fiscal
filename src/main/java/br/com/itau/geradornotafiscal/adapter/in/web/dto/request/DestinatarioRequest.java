package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import br.com.itau.geradornotafiscal.adapter.in.web.validacao.RegimeObrigatorioParaPessoaJuridica;
import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RegimeObrigatorioParaPessoaJuridica
public record DestinatarioRequest(
        @JsonProperty("nome") String nome,
        @JsonProperty("tipo_pessoa") @NotNull TipoPessoa tipoPessoa,
        @JsonProperty("regime_tributacao") RegimeTributacaoPJ regimeTributacao,
        @JsonProperty("documentos") @NotEmpty List<@NotNull @Valid DocumentoRequest> documentos,
        @JsonProperty("enderecos") @NotEmpty List<@NotNull @Valid EnderecoRequest> enderecos) {

    // Cópias que não mudam, aceitando nulo: ausência e elemento nulo são conferidos pelas anotações (E01-RN-01).
    public DestinatarioRequest {
        documentos = documentos == null ? null : Collections.unmodifiableList(new ArrayList<>(documentos));
        enderecos = enderecos == null ? null : Collections.unmodifiableList(new ArrayList<>(enderecos));
    }
}
