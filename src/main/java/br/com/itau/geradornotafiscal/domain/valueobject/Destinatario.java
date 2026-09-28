package br.com.itau.geradornotafiscal.domain.valueobject;

import java.util.List;
import java.util.Optional;

public record Destinatario(
        String nome,
        TipoPessoa tipoPessoa,
        RegimeTributacaoPJ regimeTributacao,
        List<Documento> documentos,
        List<Endereco> enderecos) {

    public Destinatario {
        documentos = List.copyOf(documentos);
        enderecos = List.copyOf(enderecos);
    }

    /** O endereço de entrega é o primeiro com finalidade ENTREGA ou COBRANCA_ENTREGA (E01-RN-06). */
    public Optional<Endereco> enderecoDeEntrega() {
        return enderecos.stream().filter(Endereco::deEntrega).findFirst();
    }
}
