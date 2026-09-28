package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

/**
 * Campo com preenchimento ou formato errado e o motivo, sem o valor recebido (E01-RN-09).
 */
public record ViolacaoEntrada(String campo, MotivoEntrada motivo, String detalhe) {

    public ViolacaoEntrada(String campo, MotivoEntrada motivo) {
        this(campo, motivo, motivo.mensagem());
    }
}
