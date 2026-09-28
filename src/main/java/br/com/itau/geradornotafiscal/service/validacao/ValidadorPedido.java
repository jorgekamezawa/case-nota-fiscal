package br.com.itau.geradornotafiscal.service.validacao;

import br.com.itau.geradornotafiscal.model.Finalidade;
import br.com.itau.geradornotafiscal.model.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.model.Regiao;
import br.com.itau.geradornotafiscal.model.TipoDocumento;
import br.com.itau.geradornotafiscal.model.TipoPessoa;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valida o pedido como árvore JSON, antes da conversão, juntando todas as violações (E01-RN-01 a E01-RN-10).
 * O formato de cada campo é conferido primeiro; campo com formato errado recebe só esse motivo (E01-RN-08).
 */
@Component
@RequiredArgsConstructor
public class ValidadorPedido {

    private static final Set<Finalidade> FINALIDADES_DE_ENTREGA = EnumSet.of(Finalidade.ENTREGA, Finalidade.COBRANCA_ENTREGA);
    private static final List<String> CAMPOS_TEXTO_ITEM = List.of("id_item", "descricao");
    private static final List<String> CAMPOS_TEXTO_ENDERECO =
            List.of("cep", "logradouro", "numero", "estado", "complemento", "bairro", "cidade", "pais");

    private final ValidadorDocumento validadorDocumento;

    public void validar(JsonNode pedido) {
        List<Violacao> violacoes = new ArrayList<>();

        idPedido(pedido.get("id_pedido"), violacoes);
        data(pedido.get("data"), violacoes);
        Optional<BigDecimal> totalDeclarado = monetario(pedido.get("valor_total_itens"), "valor_total_itens", violacoes);
        monetario(pedido.get("valor_frete"), "valor_frete", violacoes)
                .filter(frete -> frete.signum() < 0)
                .ifPresent(frete -> violacoes.add(new Violacao("valor_frete", Motivo.FRETE_NEGATIVO)));
        Optional<BigDecimal> totalCalculado = itens(pedido.get("itens"), violacoes);
        if (totalDeclarado.isPresent() && totalCalculado.isPresent()
                && totalDeclarado.get().compareTo(totalCalculado.get()) != 0) {
            violacoes.add(new Violacao("valor_total_itens", Motivo.TOTAL_DIVERGENTE, String.format(
                    "Total declarado %s, calculado %s.", duasCasas(totalDeclarado.get()), duasCasas(totalCalculado.get()))));
        }
        destinatario(pedido.get("destinatario"), violacoes);

        if (!violacoes.isEmpty()) {
            throw new PedidoInvalidoException(violacoes);
        }
    }

    private static void idPedido(JsonNode idPedido, List<Violacao> violacoes) {
        if (!ausente(idPedido) && !(idPedido.isIntegralNumber() && idPedido.canConvertToLong())) {
            violacoes.add(new Violacao("id_pedido", Motivo.FORMATO_INVALIDO));
        }
    }

    private static void data(JsonNode data, List<Violacao> violacoes) {
        if (!ausente(data) && !(data.isString() && dataValida(data.asString()))) {
            violacoes.add(new Violacao("data", Motivo.FORMATO_INVALIDO));
        }
    }

    private static boolean dataValida(String data) {
        try {
            LocalDate.parse(data);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Devolve a soma dos itens só quando há ao menos 1 item e todos são válidos (E01-RN-07). */
    private static Optional<BigDecimal> itens(JsonNode itens, List<Violacao> violacoes) {
        if (!lista(itens, "itens", violacoes)) {
            return Optional.empty();
        }
        BigDecimal soma = BigDecimal.ZERO;
        boolean todosValidos = true;
        for (int i = 0; i < itens.size(); i++) {
            int violacoesAntes = violacoes.size();
            Optional<BigDecimal> subtotal = item(itens.get(i), "itens[" + i + "]", violacoes);
            todosValidos &= violacoes.size() == violacoesAntes && subtotal.isPresent();
            soma = soma.add(subtotal.orElse(BigDecimal.ZERO));
        }
        return todosValidos ? Optional.of(soma) : Optional.empty();
    }

    private static Optional<BigDecimal> item(JsonNode item, String caminho, List<Violacao> violacoes) {
        if (!objeto(item, caminho, violacoes)) {
            return Optional.empty();
        }
        CAMPOS_TEXTO_ITEM.forEach(campo -> texto(item.get(campo), caminho + "." + campo, violacoes));
        Optional<BigDecimal> quantidade = quantidade(item.get("quantidade"), caminho + ".quantidade", violacoes);
        Optional<BigDecimal> valorUnitario = monetario(item.get("valor_unitario"), caminho + ".valor_unitario", violacoes)
                .filter(valor -> {
                    boolean positivo = valor.signum() > 0;
                    if (!positivo) {
                        violacoes.add(new Violacao(caminho + ".valor_unitario", Motivo.VALOR_UNITARIO_INVALIDO));
                    }
                    return positivo;
                });
        return quantidade.flatMap(q -> valorUnitario.map(valor -> valor.multiply(q)));
    }

    private static Optional<BigDecimal> quantidade(JsonNode quantidade, String caminho, List<Violacao> violacoes) {
        if (ausente(quantidade)) {
            violacoes.add(new Violacao(caminho, Motivo.CAMPO_OBRIGATORIO));
            return Optional.empty();
        }
        if (!quantidade.isNumber()) {
            violacoes.add(new Violacao(caminho, Motivo.FORMATO_INVALIDO));
            return Optional.empty();
        }
        BigDecimal valor = quantidade.decimalValue();
        boolean inteiraPositiva = valor.signum() > 0 && valor.stripTrailingZeros().scale() <= 0
                && valor.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        if (!inteiraPositiva) {
            violacoes.add(new Violacao(caminho, Motivo.QUANTIDADE_INVALIDA));
            return Optional.empty();
        }
        return Optional.of(valor);
    }

    private void destinatario(JsonNode destinatario, List<Violacao> violacoes) {
        if (ausente(destinatario)) {
            violacoes.add(new Violacao("destinatario", Motivo.CAMPO_OBRIGATORIO));
            return;
        }
        if (!objeto(destinatario, "destinatario", violacoes)) {
            return;
        }
        texto(destinatario.get("nome"), "destinatario.nome", violacoes);
        Optional<TipoPessoa> tipoPessoa =
                valorAceito(destinatario.get("tipo_pessoa"), "destinatario.tipo_pessoa", TipoPessoa.class, true, violacoes);
        regime(destinatario.get("regime_tributacao"), tipoPessoa, violacoes);
        documentos(destinatario.get("documentos"), tipoPessoa, violacoes);
        enderecos(destinatario.get("enderecos"), violacoes);
    }

    private static void regime(JsonNode regime, Optional<TipoPessoa> tipoPessoa, List<Violacao> violacoes) {
        String caminho = "destinatario.regime_tributacao";
        Optional<RegimeTributacaoPJ> valor = valorAceito(regime, caminho, RegimeTributacaoPJ.class, false, violacoes);
        if (tipoPessoa.equals(Optional.of(TipoPessoa.FISICA)) && valor.isPresent()) {
            violacoes.add(new Violacao(caminho, Motivo.REGIME_NAO_SE_APLICA));
        } else if (tipoPessoa.equals(Optional.of(TipoPessoa.JURIDICA))) {
            if (ausente(regime)) {
                violacoes.add(new Violacao(caminho, Motivo.CAMPO_OBRIGATORIO));
            } else if (valor.equals(Optional.of(RegimeTributacaoPJ.OUTROS))) {
                violacoes.add(new Violacao(caminho, Motivo.REGIME_NAO_ATENDIDO));
            }
        }
    }

    private void documentos(JsonNode documentos, Optional<TipoPessoa> tipoPessoa, List<Violacao> violacoes) {
        String caminho = "destinatario.documentos";
        if (!lista(documentos, caminho, violacoes)) {
            return;
        }
        Set<TipoDocumento> tiposInformados = EnumSet.noneOf(TipoDocumento.class);
        boolean todosOsTiposValidos = true;
        for (int i = 0; i < documentos.size(); i++) {
            String caminhoDocumento = caminho + "[" + i + "]";
            JsonNode documento = documentos.get(i);
            if (!objeto(documento, caminhoDocumento, violacoes)) {
                todosOsTiposValidos = false;
                continue;
            }
            Optional<TipoDocumento> tipo =
                    valorAceito(documento.get("tipo"), caminhoDocumento + ".tipo", TipoDocumento.class, true, violacoes);
            tipo.ifPresent(tiposInformados::add);
            todosOsTiposValidos &= tipo.isPresent();
            numeroDocumento(documento.get("numero"), caminhoDocumento + ".numero", tipo, violacoes);
        }
        // Coerência só com todos os tipos válidos: com tipo inválido, não se sabe qual documento era o esperado.
        TipoDocumento esperado = tipoPessoa.map(tipo -> tipo == TipoPessoa.FISICA ? TipoDocumento.CPF : TipoDocumento.CNPJ)
                .orElse(null);
        if (esperado != null && todosOsTiposValidos && !tiposInformados.contains(esperado)) {
            violacoes.add(new Violacao(caminho, Motivo.DOCUMENTO_DO_TIPO_AUSENTE, "Falta documento do tipo " + esperado + "."));
        }
    }

    private void numeroDocumento(JsonNode numero, String caminho, Optional<TipoDocumento> tipo, List<Violacao> violacoes) {
        if (ausente(numero)) {
            violacoes.add(new Violacao(caminho, Motivo.CAMPO_OBRIGATORIO));
        } else if (numero.isContainer()) {
            violacoes.add(new Violacao(caminho, Motivo.FORMATO_INVALIDO));
        } else if (tipo.isPresent() && !validadorDocumento.valido(tipo.get(), numero.asString())) {
            violacoes.add(new Violacao(caminho, Motivo.DOCUMENTO_INVALIDO, tipo.get() + " inválido."));
        }
    }

    private static void enderecos(JsonNode enderecos, List<Violacao> violacoes) {
        String caminho = "destinatario.enderecos";
        if (!lista(enderecos, caminho, violacoes)) {
            return;
        }
        Integer indiceEntrega = null;
        boolean todasAsFinalidadesValidas = true;
        for (int i = 0; i < enderecos.size(); i++) {
            String caminhoEndereco = caminho + "[" + i + "]";
            JsonNode endereco = enderecos.get(i);
            if (!objeto(endereco, caminhoEndereco, violacoes)) {
                todasAsFinalidadesValidas = false;
                continue;
            }
            CAMPOS_TEXTO_ENDERECO.forEach(campo -> texto(endereco.get(campo), caminhoEndereco + "." + campo, violacoes));
            valorAceito(endereco.get("regiao"), caminhoEndereco + ".regiao", Regiao.class, false, violacoes);
            Optional<Finalidade> finalidade =
                    valorAceito(endereco.get("finalidade"), caminhoEndereco + ".finalidade", Finalidade.class, true, violacoes);
            todasAsFinalidadesValidas &= finalidade.isPresent();
            if (indiceEntrega == null && finalidade.filter(FINALIDADES_DE_ENTREGA::contains).isPresent()) {
                indiceEntrega = i;
            }
        }
        // Com finalidade inválida, não se sabe qual seria o endereço de entrega (mesma lógica da E01-RN-10).
        if (!todasAsFinalidadesValidas) {
            return;
        }
        if (indiceEntrega == null) {
            violacoes.add(new Violacao(caminho, Motivo.SEM_ENDERECO_DE_ENTREGA));
        } else if (ausente(enderecos.get(indiceEntrega).get("regiao"))) {
            violacoes.add(new Violacao(caminho + "[" + indiceEntrega + "].regiao", Motivo.CAMPO_OBRIGATORIO,
                    "Endereço de entrega sem região."));
        }
    }

    /** Valor monetário: obrigatório, número e no máximo 2 casas decimais (E01-RN-01, E01-RN-08). */
    private static Optional<BigDecimal> monetario(JsonNode valor, String caminho, List<Violacao> violacoes) {
        if (ausente(valor)) {
            violacoes.add(new Violacao(caminho, Motivo.CAMPO_OBRIGATORIO));
            return Optional.empty();
        }
        if (!valor.isNumber()) {
            violacoes.add(new Violacao(caminho, Motivo.FORMATO_INVALIDO));
            return Optional.empty();
        }
        // Zeros à direita não contam: 10.500 tem 2 casas.
        if (valor.decimalValue().stripTrailingZeros().scale() > 2) {
            violacoes.add(new Violacao(caminho, Motivo.CASAS_DECIMAIS_EXCEDIDAS));
            return Optional.empty();
        }
        return Optional.of(valor.decimalValue());
    }

    private static <E extends Enum<E>> Optional<E> valorAceito(JsonNode valor, String caminho, Class<E> tipo,
                                                               boolean obrigatorio, List<Violacao> violacoes) {
        if (ausente(valor)) {
            if (obrigatorio) {
                violacoes.add(new Violacao(caminho, Motivo.CAMPO_OBRIGATORIO));
            }
            return Optional.empty();
        }
        if (!valor.isString()) {
            violacoes.add(new Violacao(caminho, Motivo.FORMATO_INVALIDO));
            return Optional.empty();
        }
        Optional<E> constante = Arrays.stream(tipo.getEnumConstants())
                .filter(c -> c.name().equals(valor.asString()))
                .findFirst();
        if (constante.isEmpty()) {
            String aceitos = Arrays.stream(tipo.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
            violacoes.add(new Violacao(caminho, Motivo.VALOR_NAO_ACEITO, "Valor fora dos aceitos: " + aceitos + "."));
        }
        return constante;
    }

    /** Campo de texto: número e booleano são aceitos como texto, como no contrato atual; objeto e lista, não. */
    private static void texto(JsonNode valor, String caminho, List<Violacao> violacoes) {
        if (!ausente(valor) && valor.isContainer()) {
            violacoes.add(new Violacao(caminho, Motivo.FORMATO_INVALIDO));
        }
    }

    /** Lista obrigatória com ao menos 1 elemento (E01-RN-01). */
    private static boolean lista(JsonNode lista, String caminho, List<Violacao> violacoes) {
        if (ausente(lista) || (lista.isArray() && lista.isEmpty())) {
            violacoes.add(new Violacao(caminho, Motivo.CAMPO_OBRIGATORIO, "Informe ao menos 1 elemento."));
            return false;
        }
        if (!lista.isArray()) {
            violacoes.add(new Violacao(caminho, Motivo.FORMATO_INVALIDO));
            return false;
        }
        return true;
    }

    private static boolean objeto(JsonNode objeto, String caminho, List<Violacao> violacoes) {
        if (!objeto.isObject()) {
            violacoes.add(new Violacao(caminho, Motivo.FORMATO_INVALIDO));
            return false;
        }
        return true;
    }

    // Campo com valor nulo conta como ausente (E01-RN-10).
    private static boolean ausente(JsonNode valor) {
        return valor == null || valor.isNull();
    }

    private static String duasCasas(BigDecimal valor) {
        return valor.setScale(2).toPlainString();
    }
}
