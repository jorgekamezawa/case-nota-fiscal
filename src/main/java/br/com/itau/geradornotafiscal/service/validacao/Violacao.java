package br.com.itau.geradornotafiscal.service.validacao;

import lombok.Getter;

/**
 * Campo inválido e o motivo. Nunca traz o valor recebido (E01-RN-09); a única exceção são os totais da E01-RN-07.
 */
@Getter
public class Violacao {
    private final String campo;
    private final Motivo motivo;
    private final String detalhe;

    public Violacao(String campo, Motivo motivo) {
        this(campo, motivo, motivo.getMensagem());
    }

    public Violacao(String campo, Motivo motivo, String detalhe) {
        this.campo = campo;
        this.motivo = motivo;
        this.detalhe = detalhe;
    }
}
