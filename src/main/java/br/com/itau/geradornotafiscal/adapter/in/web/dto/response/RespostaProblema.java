package br.com.itau.geradornotafiscal.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Corpo de erro no formato Problem Details (RFC 9457, ADR-0009), com a lista de campos inválidos quando houver.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RespostaProblema(String type, String title, int status, String detail, List<CampoInvalido> campos) {

    public record CampoInvalido(String campo, String type, String detail) {
    }
}
