package br.com.itau.geradornotafiscal.adapter.in.web.mappers;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.DestinatarioRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.DocumentoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.EnderecoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.ItemRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Documento;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Contrato de entrada para o comando da porta de entrada. Recebe só pedido que passou na etapa 1 da validação.
 */
@Component
public class PedidoMapper {

    public GerarNotaFiscalCommand paraComando(PedidoRequest pedido, String hashPedido) {
        return new GerarNotaFiscalCommand(
                pedido.idPedido(),
                pedido.data(),
                pedido.valorTotalItens(),
                pedido.valorFrete(),
                lista(pedido.itens(), PedidoMapper::item),
                destinatario(pedido.destinatario()),
                hashPedido);
    }

    private static Item item(ItemRequest item) {
        return new Item(item.idItem(), item.descricao(), item.valorUnitario(), item.quantidade());
    }

    private static Destinatario destinatario(DestinatarioRequest destinatario) {
        return new Destinatario(
                destinatario.nome(),
                destinatario.tipoPessoa(),
                destinatario.regimeTributacao(),
                lista(destinatario.documentos(), PedidoMapper::documento),
                lista(destinatario.enderecos(), PedidoMapper::endereco));
    }

    private static Documento documento(DocumentoRequest documento) {
        return new Documento(documento.numero(), documento.tipo());
    }

    private static Endereco endereco(EnderecoRequest endereco) {
        return new Endereco(endereco.cep(), endereco.logradouro(), endereco.numero(), endereco.bairro(),
                endereco.cidade(), endereco.estado(), endereco.pais(), endereco.complemento(),
                endereco.finalidade(), endereco.regiao());
    }

    private static <T, R> List<R> lista(List<T> origem, Function<T, R> conversao) {
        return origem.stream().map(conversao).toList();
    }
}
