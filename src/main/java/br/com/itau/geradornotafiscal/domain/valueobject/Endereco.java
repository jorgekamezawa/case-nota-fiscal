package br.com.itau.geradornotafiscal.domain.valueobject;

public record Endereco(
        String cep,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String estado,
        String pais,
        String complemento,
        Finalidade finalidade,
        Regiao regiao) {

    public boolean deEntrega() {
        return finalidade == Finalidade.ENTREGA || finalidade == Finalidade.COBRANCA_ENTREGA;
    }
}
