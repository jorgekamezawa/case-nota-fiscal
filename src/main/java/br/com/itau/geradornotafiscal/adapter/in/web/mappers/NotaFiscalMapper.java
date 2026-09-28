package br.com.itau.geradornotafiscal.adapter.in.web.mappers;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.DestinatarioResponse;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.DocumentoResponse;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.EnderecoResponse;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.ItemNotaFiscalResponse;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.NotaFiscalResponse;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Documento;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Domínio para o contrato de saída, com os campos na ordem atual (E01-NF-02).
 */
@Component
public class NotaFiscalMapper {

    public NotaFiscalResponse paraResponse(NotaFiscal nota) {
        return new NotaFiscalResponse(
                nota.idNotaFiscal(),
                nota.data(),
                nota.valorTotalItens(),
                nota.valorFrete(),
                lista(nota.itens(), NotaFiscalMapper::item),
                destinatario(nota.destinatario()));
    }

    // A quantidade já foi conferida como inteira (E01-RN-04) e volta como inteiro, como no contrato atual.
    private static ItemNotaFiscalResponse item(ItemNotaFiscal item) {
        return new ItemNotaFiscalResponse(item.idItem(), item.descricao(), item.valorUnitario(),
                item.quantidade().intValueExact(), item.valorTributoItem());
    }

    private static DestinatarioResponse destinatario(Destinatario destinatario) {
        return new DestinatarioResponse(
                destinatario.nome(),
                destinatario.tipoPessoa(),
                destinatario.regimeTributacao(),
                lista(destinatario.documentos(), NotaFiscalMapper::documento),
                lista(destinatario.enderecos(), NotaFiscalMapper::endereco));
    }

    private static DocumentoResponse documento(Documento documento) {
        return new DocumentoResponse(documento.numero(), documento.tipo());
    }

    private static EnderecoResponse endereco(Endereco endereco) {
        return new EnderecoResponse(endereco.cep(), endereco.logradouro(), endereco.numero(), endereco.bairro(),
                endereco.cidade(), endereco.estado(), endereco.pais(), endereco.complemento(),
                endereco.finalidade(), endereco.regiao());
    }

    private static <T, R> List<R> lista(List<T> origem, Function<T, R> conversao) {
        return origem.stream().map(conversao).collect(Collectors.toList());
    }
}
