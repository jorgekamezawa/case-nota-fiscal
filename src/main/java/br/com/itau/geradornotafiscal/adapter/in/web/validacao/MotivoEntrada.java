package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

/**
 * Motivo de recusa por preenchimento ou formato (etapa 1 da E01-RN-09). O código é estável e vira o type do erro (docs/api/erros.md).
 */
public enum MotivoEntrada {
    CAMPO_OBRIGATORIO("campo-obrigatorio", "Campo obrigatório."),
    FORMATO_INVALIDO("formato-invalido", "Formato inválido."),
    CASAS_DECIMAIS_EXCEDIDAS("casas-decimais-excedidas", "Valor com mais de 2 casas decimais."),
    VALOR_NAO_ACEITO("valor-nao-aceito", "Valor fora dos aceitos.");

    private final String codigo;
    private final String mensagem;

    MotivoEntrada(String codigo, String mensagem) {
        this.codigo = codigo;
        this.mensagem = mensagem;
    }

    public String codigo() {
        return codigo;
    }

    public String mensagem() {
        return mensagem;
    }
}
