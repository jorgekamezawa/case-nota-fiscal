package br.com.itau.geradornotafiscal.domain.exception;

/**
 * Campo que viola uma regra de negócio e o motivo. Nunca traz o valor recebido (E01-RN-09); a única exceção são os totais da E01-RN-07.
 */
public record Violacao(String campo, MotivoRegra motivo, String detalhe) {

    public Violacao(String campo, MotivoRegra motivo) {
        this(campo, motivo, motivo.mensagem());
    }
}
