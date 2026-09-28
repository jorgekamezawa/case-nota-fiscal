package br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers;

import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.NotaFiscalRegistro;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.NotaFiscalRegistro.DestinatarioRegistro;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.NotaFiscalRegistro.DocumentoRegistro;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.NotaFiscalRegistro.EnderecoRegistro;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.NotaFiscalRegistro.ItemRegistro;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Documento;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import br.com.itau.geradornotafiscal.domain.valueobject.Finalidade;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import br.com.itau.geradornotafiscal.domain.valueobject.Regiao;
import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * Domínio para o formato guardado e de volta; a nota lida é igual à gravada (E04-RN-02).
 */
@Component
public class NotaFiscalRegistroMapper {

    public NotaFiscalRegistro paraRegistro(NotaFiscal nota) {
        return new NotaFiscalRegistro(
                nota.getIdNotaFiscal(),
                nota.getData().toString(),
                nota.getValorTotalItens(),
                nota.getValorFrete(),
                lista(nota.getItens(), item -> new ItemRegistro(item.idItem(), item.descricao(), item.valorUnitario(),
                        item.quantidade(), item.valorTributoItem())),
                destinatario(nota.getDestinatario()));
    }

    public NotaFiscal paraDominio(NotaFiscalRegistro registro) {
        return NotaFiscal.reconstituir(
                registro.idNotaFiscal(),
                LocalDateTime.parse(registro.data()),
                registro.valorTotalItens(),
                registro.valorFrete(),
                lista(registro.itens(), item -> new ItemNotaFiscal(item.idItem(), item.descricao(), item.valorUnitario(),
                        item.quantidade(), item.valorTributoItem())),
                destinatario(registro.destinatario()));
    }

    private static DestinatarioRegistro destinatario(Destinatario destinatario) {
        return new DestinatarioRegistro(
                destinatario.nome(),
                nome(destinatario.tipoPessoa()),
                nome(destinatario.regimeTributacao()),
                lista(destinatario.documentos(), documento -> new DocumentoRegistro(documento.numero(), nome(documento.tipo()))),
                lista(destinatario.enderecos(), endereco -> new EnderecoRegistro(endereco.cep(), endereco.logradouro(),
                        endereco.numero(), endereco.bairro(), endereco.cidade(), endereco.estado(), endereco.pais(),
                        endereco.complemento(), nome(endereco.finalidade()), nome(endereco.regiao()))));
    }

    private static Destinatario destinatario(DestinatarioRegistro registro) {
        return new Destinatario(
                registro.nome(),
                valor(TipoPessoa.class, registro.tipoPessoa()),
                valor(RegimeTributacaoPJ.class, registro.regimeTributacao()),
                lista(registro.documentos(), documento -> new Documento(documento.numero(),
                        valor(TipoDocumento.class, documento.tipo()))),
                lista(registro.enderecos(), endereco -> new Endereco(endereco.cep(), endereco.logradouro(),
                        endereco.numero(), endereco.bairro(), endereco.cidade(), endereco.estado(), endereco.pais(),
                        endereco.complemento(), valor(Finalidade.class, endereco.finalidade()),
                        valor(Regiao.class, endereco.regiao()))));
    }

    private static String nome(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }

    private static <E extends Enum<E>> E valor(Class<E> tipo, String nome) {
        return nome == null ? null : Enum.valueOf(tipo, nome);
    }

    private static <T, R> List<R> lista(List<T> origem, Function<T, R> conversao) {
        return origem.stream().map(conversao).toList();
    }
}
