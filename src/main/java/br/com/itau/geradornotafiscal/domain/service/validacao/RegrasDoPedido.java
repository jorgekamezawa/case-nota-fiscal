package br.com.itau.geradornotafiscal.domain.service.validacao;

import br.com.itau.geradornotafiscal.domain.exception.MotivoRegra;
import br.com.itau.geradornotafiscal.domain.exception.PedidoInvalidoException;
import br.com.itau.geradornotafiscal.domain.exception.Violacao;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Documento;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;
import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras de negócio do pedido, a etapa 2 da E01-RN-09 (E01-RN-02 a E01-RN-07), chamadas por {@code Pedido.criar}.
 * Recebe só dados que passaram no preenchimento e no formato, e junta todas as violações da etapa antes de recusar.
 */
public final class RegrasDoPedido {

    private static final BigDecimal MAIOR_QUANTIDADE = BigDecimal.valueOf(Integer.MAX_VALUE);

    private RegrasDoPedido() {
    }

    public static void validar(BigDecimal valorTotalItens, BigDecimal valorFrete, List<Item> itens, Destinatario destinatario) {
        List<Violacao> violacoes = new ArrayList<>();

        if (valorFrete.signum() < 0) {
            violacoes.add(new Violacao("valor_frete", MotivoRegra.FRETE_NEGATIVO));
        }
        itensETotal(valorTotalItens, itens, violacoes);
        destinatario(destinatario, violacoes);

        if (!violacoes.isEmpty()) {
            throw new PedidoInvalidoException(violacoes);
        }
    }

    /** O total só é conferido quando todos os itens são válidos (E01-RN-04, E01-RN-07). */
    private static void itensETotal(BigDecimal valorTotalItens, List<Item> itens, List<Violacao> violacoes) {
        BigDecimal soma = BigDecimal.ZERO;
        boolean todosValidos = true;
        for (int i = 0; i < itens.size(); i++) {
            Item item = itens.get(i);
            boolean valido = true;
            if (!quantidadeInteiraPositiva(item.quantidade())) {
                violacoes.add(new Violacao("itens[" + i + "].quantidade", MotivoRegra.QUANTIDADE_INVALIDA));
                valido = false;
            }
            if (item.valorUnitario().signum() <= 0) {
                violacoes.add(new Violacao("itens[" + i + "].valor_unitario", MotivoRegra.VALOR_UNITARIO_INVALIDO));
                valido = false;
            }
            todosValidos &= valido;
            if (valido) {
                soma = soma.add(item.valorUnitario().multiply(item.quantidade()));
            }
        }
        if (todosValidos && valorTotalItens.compareTo(soma) != 0) {
            violacoes.add(new Violacao("valor_total_itens", MotivoRegra.TOTAL_DIVERGENTE, String.format(
                    "Total declarado %s, calculado %s.", duasCasas(valorTotalItens), duasCasas(soma))));
        }
    }

    private static boolean quantidadeInteiraPositiva(BigDecimal quantidade) {
        return quantidade.signum() > 0 && quantidade.stripTrailingZeros().scale() <= 0
                && quantidade.compareTo(MAIOR_QUANTIDADE) <= 0;
    }

    private static void destinatario(Destinatario destinatario, List<Violacao> violacoes) {
        regime(destinatario, violacoes);
        documentos(destinatario, violacoes);
        enderecos(destinatario, violacoes);
    }

    private static void regime(Destinatario destinatario, List<Violacao> violacoes) {
        String caminho = "destinatario.regime_tributacao";
        if (destinatario.tipoPessoa() == TipoPessoa.FISICA && destinatario.regimeTributacao() != null) {
            violacoes.add(new Violacao(caminho, MotivoRegra.REGIME_NAO_SE_APLICA));
        } else if (destinatario.tipoPessoa() == TipoPessoa.JURIDICA
                && destinatario.regimeTributacao() == RegimeTributacaoPJ.OUTROS) {
            violacoes.add(new Violacao(caminho, MotivoRegra.REGIME_NAO_ATENDIDO));
        }
    }

    private static void documentos(Destinatario destinatario, List<Violacao> violacoes) {
        for (int i = 0; i < destinatario.documentos().size(); i++) {
            Documento documento = destinatario.documentos().get(i);
            if (!ValidadorDocumento.valido(documento.tipo(), documento.numero())) {
                violacoes.add(new Violacao("destinatario.documentos[" + i + "].numero", MotivoRegra.DOCUMENTO_INVALIDO,
                        documento.tipo() + " inválido."));
            }
        }
        TipoDocumento esperado = destinatario.tipoPessoa() == TipoPessoa.FISICA ? TipoDocumento.CPF : TipoDocumento.CNPJ;
        if (destinatario.documentos().stream().noneMatch(documento -> documento.tipo() == esperado)) {
            violacoes.add(new Violacao("destinatario.documentos", MotivoRegra.DOCUMENTO_DO_TIPO_AUSENTE,
                    "Falta documento do tipo " + esperado + "."));
        }
    }

    private static void enderecos(Destinatario destinatario, List<Violacao> violacoes) {
        List<Endereco> enderecos = destinatario.enderecos();
        for (int i = 0; i < enderecos.size(); i++) {
            if (enderecos.get(i).deEntrega()) {
                if (enderecos.get(i).regiao() == null) {
                    violacoes.add(new Violacao("destinatario.enderecos[" + i + "].regiao",
                            MotivoRegra.REGIAO_DE_ENTREGA_OBRIGATORIA));
                }
                return;
            }
        }
        violacoes.add(new Violacao("destinatario.enderecos", MotivoRegra.SEM_ENDERECO_DE_ENTREGA));
    }

    private static String duasCasas(BigDecimal valor) {
        return valor.setScale(2).toPlainString();
    }
}
