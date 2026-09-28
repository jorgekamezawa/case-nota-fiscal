package br.com.itau.geradornotafiscal.domain.exception;

/**
 * Motivo de recusa por regra de negócio (etapa 2 da E01-RN-09). O código é estável e vira o type do erro (docs/api/erros.md).
 */
public enum MotivoRegra {
    DOCUMENTO_INVALIDO("documento-invalido", "Documento inválido."),
    DOCUMENTO_DO_TIPO_AUSENTE("documento-do-tipo-ausente", "Falta documento do tipo coerente com o tipo de pessoa."),
    REGIME_NAO_ATENDIDO("regime-nao-atendido", "Regime de tributação não atendido."),
    REGIME_NAO_SE_APLICA("regime-nao-se-aplica", "Regime de tributação não se aplica a pessoa física."),
    QUANTIDADE_INVALIDA("quantidade-invalida", "Quantidade deve ser inteira e maior que zero."),
    VALOR_UNITARIO_INVALIDO("valor-unitario-invalido", "Valor unitário deve ser maior que zero."),
    FRETE_NEGATIVO("frete-negativo", "Valor do frete não pode ser negativo."),
    SEM_ENDERECO_DE_ENTREGA("sem-endereco-de-entrega", "Informe ao menos um endereço com finalidade ENTREGA ou COBRANCA_ENTREGA."),
    // Mesmo type do campo obrigatório da etapa 1: a região só é obrigatória no endereço de entrega (E01-RN-06).
    REGIAO_DE_ENTREGA_OBRIGATORIA("campo-obrigatorio", "Endereço de entrega sem região."),
    TOTAL_DIVERGENTE("total-divergente", "Total dos itens diferente da soma dos itens."),
    ITENS_ACIMA_DO_MAXIMO("itens-acima-do-maximo", "O pedido pode ter no máximo 800 linhas de item.");

    private final String codigo;
    private final String mensagem;

    MotivoRegra(String codigo, String mensagem) {
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
