package br.com.itau.geradornotafiscal.adapter.in.web.dto.request;

import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DestinatarioRequest(
        @JsonProperty("nome") String nome,
        @JsonProperty("tipo_pessoa") TipoPessoa tipoPessoa,
        @JsonProperty("regime_tributacao") RegimeTributacaoPJ regimeTributacao,
        @JsonProperty("documentos") List<DocumentoRequest> documentos,
        @JsonProperty("enderecos") List<EnderecoRequest> enderecos) {
}
