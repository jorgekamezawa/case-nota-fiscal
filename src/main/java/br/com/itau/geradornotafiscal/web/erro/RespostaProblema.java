package br.com.itau.geradornotafiscal.web.erro;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Corpo de erro no formato Problem Details (RFC 9457, ADR-0009), com a lista de campos inválidos quando houver.
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RespostaProblema {
    private final String type;
    private final String title;
    private final int status;
    private final String detail;
    private final List<CampoInvalido> campos;

    @Getter
    @AllArgsConstructor
    public static class CampoInvalido {
        private final String campo;
        private final String type;
        private final String detail;
    }
}
