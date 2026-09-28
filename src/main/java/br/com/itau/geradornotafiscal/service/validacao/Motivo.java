package br.com.itau.geradornotafiscal.service.validacao;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Motivo de recusa de um campo. O código é estável e vira o type do erro (docs/api/erros.md).
 */
@Getter
@RequiredArgsConstructor
public enum Motivo {
    CAMPO_OBRIGATORIO("campo-obrigatorio", "Campo obrigatório."),
    FORMATO_INVALIDO("formato-invalido", "Formato inválido."),
    CASAS_DECIMAIS_EXCEDIDAS("casas-decimais-excedidas", "Valor com mais de 2 casas decimais."),
    VALOR_NAO_ACEITO("valor-nao-aceito", "Valor fora dos aceitos."),
    DOCUMENTO_INVALIDO("documento-invalido", "Documento inválido."),
    DOCUMENTO_DO_TIPO_AUSENTE("documento-do-tipo-ausente", "Falta documento do tipo coerente com o tipo de pessoa."),
    REGIME_NAO_ATENDIDO("regime-nao-atendido", "Regime de tributação não atendido."),
    REGIME_NAO_SE_APLICA("regime-nao-se-aplica", "Regime de tributação não se aplica a pessoa física."),
    QUANTIDADE_INVALIDA("quantidade-invalida", "Quantidade deve ser inteira e maior que zero."),
    VALOR_UNITARIO_INVALIDO("valor-unitario-invalido", "Valor unitário deve ser maior que zero."),
    FRETE_NEGATIVO("frete-negativo", "Valor do frete não pode ser negativo."),
    SEM_ENDERECO_DE_ENTREGA("sem-endereco-de-entrega", "Informe ao menos um endereço com finalidade ENTREGA ou COBRANCA_ENTREGA."),
    TOTAL_DIVERGENTE("total-divergente", "Total dos itens diferente da soma dos itens.");

    private final String codigo;
    private final String mensagem;
}
