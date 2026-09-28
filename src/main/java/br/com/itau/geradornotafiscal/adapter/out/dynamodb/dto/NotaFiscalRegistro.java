package br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nota como fica guardada, em JSON, no atributo {@code nota} da tabela {@code notas}. Os nomes dos campos são o formato
 * gravado: renomear um deles impede ler as notas já guardadas.
 */
public record NotaFiscalRegistro(
        String idNotaFiscal,
        String data,
        BigDecimal valorTotalItens,
        BigDecimal valorFrete,
        List<ItemRegistro> itens,
        DestinatarioRegistro destinatario) {

    public record ItemRegistro(String idItem, String descricao, BigDecimal valorUnitario, BigDecimal quantidade,
                               BigDecimal valorTributoItem) {
    }

    public record DestinatarioRegistro(String nome, String tipoPessoa, String regimeTributacao,
                                       List<DocumentoRegistro> documentos, List<EnderecoRegistro> enderecos) {
    }

    public record DocumentoRegistro(String numero, String tipo) {
    }

    public record EnderecoRegistro(String cep, String logradouro, String numero, String bairro, String cidade,
                                   String estado, String pais, String complemento, String finalidade, String regiao) {
    }
}
