package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

import br.com.itau.geradornotafiscal.domain.valueobject.Finalidade;
import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.Regiao;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Etapa 1 da E01-RN-09: preenchimento e formato da entrada (E01-RN-01, E01-RN-08, E01-RN-10), conferidos na árvore
 * JSON antes da conversão, juntando todas as violações da etapa. As regras de negócio ficam no domínio.
 */
@Component
public class ValidadorEntrada {

    private static final List<String> CAMPOS_TEXTO_ITEM = List.of("id_item", "descricao");
    private static final List<String> CAMPOS_TEXTO_ENDERECO =
            List.of("cep", "logradouro", "numero", "estado", "complemento", "bairro", "cidade", "pais");

    public void validar(JsonNode pedido) {
        List<ViolacaoEntrada> violacoes = new ArrayList<>();

        idPedido(pedido.get("id_pedido"), violacoes);
        data(pedido.get("data"), violacoes);
        monetario(pedido.get("valor_total_itens"), "valor_total_itens", violacoes);
        monetario(pedido.get("valor_frete"), "valor_frete", violacoes);
        itens(pedido.get("itens"), violacoes);
        destinatario(pedido.get("destinatario"), violacoes);

        if (!violacoes.isEmpty()) {
            throw new EntradaInvalidaException(violacoes);
        }
    }

    private static void idPedido(JsonNode idPedido, List<ViolacaoEntrada> violacoes) {
        if (!ausente(idPedido) && !(idPedido.isIntegralNumber() && idPedido.canConvertToLong())) {
            violacoes.add(new ViolacaoEntrada("id_pedido", MotivoEntrada.FORMATO_INVALIDO));
        }
    }

    private static void data(JsonNode data, List<ViolacaoEntrada> violacoes) {
        if (!ausente(data) && !(data.isString() && dataValida(data.asString()))) {
            violacoes.add(new ViolacaoEntrada("data", MotivoEntrada.FORMATO_INVALIDO));
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

    private static void itens(JsonNode itens, List<ViolacaoEntrada> violacoes) {
        if (!lista(itens, "itens", violacoes)) {
            return;
        }
        for (int i = 0; i < itens.size(); i++) {
            String caminho = "itens[" + i + "]";
            JsonNode item = itens.get(i);
            if (!objeto(item, caminho, violacoes)) {
                continue;
            }
            CAMPOS_TEXTO_ITEM.forEach(campo -> texto(item.get(campo), caminho + "." + campo, violacoes));
            numero(item.get("quantidade"), caminho + ".quantidade", violacoes);
            monetario(item.get("valor_unitario"), caminho + ".valor_unitario", violacoes);
        }
    }

    private static void destinatario(JsonNode destinatario, List<ViolacaoEntrada> violacoes) {
        if (ausente(destinatario)) {
            violacoes.add(new ViolacaoEntrada("destinatario", MotivoEntrada.CAMPO_OBRIGATORIO));
            return;
        }
        if (!objeto(destinatario, "destinatario", violacoes)) {
            return;
        }
        texto(destinatario.get("nome"), "destinatario.nome", violacoes);
        Optional<TipoPessoa> tipoPessoa =
                valorAceito(destinatario.get("tipo_pessoa"), "destinatario.tipo_pessoa", TipoPessoa.class, true, violacoes);
        JsonNode regime = destinatario.get("regime_tributacao");
        valorAceito(regime, "destinatario.regime_tributacao", RegimeTributacaoPJ.class, false, violacoes);
        // Sem tipo de pessoa válido, não se sabe se o regime é obrigatório (E01-RN-10).
        if (tipoPessoa.equals(Optional.of(TipoPessoa.JURIDICA)) && ausente(regime)) {
            violacoes.add(new ViolacaoEntrada("destinatario.regime_tributacao", MotivoEntrada.CAMPO_OBRIGATORIO));
        }
        documentos(destinatario.get("documentos"), violacoes);
        enderecos(destinatario.get("enderecos"), violacoes);
    }

    private static void documentos(JsonNode documentos, List<ViolacaoEntrada> violacoes) {
        String caminho = "destinatario.documentos";
        if (!lista(documentos, caminho, violacoes)) {
            return;
        }
        for (int i = 0; i < documentos.size(); i++) {
            String caminhoDocumento = caminho + "[" + i + "]";
            JsonNode documento = documentos.get(i);
            if (!objeto(documento, caminhoDocumento, violacoes)) {
                continue;
            }
            valorAceito(documento.get("tipo"), caminhoDocumento + ".tipo", TipoDocumento.class, true, violacoes);
            JsonNode numero = documento.get("numero");
            if (ausente(numero)) {
                violacoes.add(new ViolacaoEntrada(caminhoDocumento + ".numero", MotivoEntrada.CAMPO_OBRIGATORIO));
            } else {
                texto(numero, caminhoDocumento + ".numero", violacoes);
            }
        }
    }

    private static void enderecos(JsonNode enderecos, List<ViolacaoEntrada> violacoes) {
        String caminho = "destinatario.enderecos";
        if (!lista(enderecos, caminho, violacoes)) {
            return;
        }
        for (int i = 0; i < enderecos.size(); i++) {
            String caminhoEndereco = caminho + "[" + i + "]";
            JsonNode endereco = enderecos.get(i);
            if (!objeto(endereco, caminhoEndereco, violacoes)) {
                continue;
            }
            CAMPOS_TEXTO_ENDERECO.forEach(campo -> texto(endereco.get(campo), caminhoEndereco + "." + campo, violacoes));
            valorAceito(endereco.get("regiao"), caminhoEndereco + ".regiao", Regiao.class, false, violacoes);
            valorAceito(endereco.get("finalidade"), caminhoEndereco + ".finalidade", Finalidade.class, true, violacoes);
        }
    }

    /** Número obrigatório; se é inteiro e positivo, confere o domínio (E01-RN-04). */
    private static void numero(JsonNode valor, String caminho, List<ViolacaoEntrada> violacoes) {
        if (ausente(valor)) {
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.CAMPO_OBRIGATORIO));
        } else if (!valor.isNumber()) {
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.FORMATO_INVALIDO));
        }
    }

    /** Valor monetário: obrigatório, número e no máximo 2 casas decimais (E01-RN-01, E01-RN-08). */
    private static void monetario(JsonNode valor, String caminho, List<ViolacaoEntrada> violacoes) {
        numero(valor, caminho, violacoes);
        // Zeros à direita não contam: 10.500 tem 2 casas.
        if (!ausente(valor) && valor.isNumber() && valor.decimalValue().stripTrailingZeros().scale() > 2) {
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.CASAS_DECIMAIS_EXCEDIDAS));
        }
    }

    private static <E extends Enum<E>> Optional<E> valorAceito(JsonNode valor, String caminho, Class<E> tipo,
                                                               boolean obrigatorio, List<ViolacaoEntrada> violacoes) {
        if (ausente(valor)) {
            if (obrigatorio) {
                violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.CAMPO_OBRIGATORIO));
            }
            return Optional.empty();
        }
        if (!valor.isString()) {
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.FORMATO_INVALIDO));
            return Optional.empty();
        }
        Optional<E> constante = Arrays.stream(tipo.getEnumConstants())
                .filter(c -> c.name().equals(valor.asString()))
                .findFirst();
        if (constante.isEmpty()) {
            String aceitos = Arrays.stream(tipo.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.VALOR_NAO_ACEITO, "Valor fora dos aceitos: " + aceitos + "."));
        }
        return constante;
    }

    /** Campo de texto: número e booleano são aceitos como texto, como no contrato atual; objeto e lista, não. */
    private static void texto(JsonNode valor, String caminho, List<ViolacaoEntrada> violacoes) {
        if (!ausente(valor) && valor.isContainer()) {
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.FORMATO_INVALIDO));
        }
    }

    /** Lista obrigatória com ao menos 1 elemento (E01-RN-01). */
    private static boolean lista(JsonNode lista, String caminho, List<ViolacaoEntrada> violacoes) {
        if (ausente(lista) || (lista.isArray() && lista.isEmpty())) {
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.CAMPO_OBRIGATORIO, "Informe ao menos 1 elemento."));
            return false;
        }
        if (!lista.isArray()) {
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.FORMATO_INVALIDO));
            return false;
        }
        return true;
    }

    private static boolean objeto(JsonNode objeto, String caminho, List<ViolacaoEntrada> violacoes) {
        if (!objeto.isObject()) {
            violacoes.add(new ViolacaoEntrada(caminho, MotivoEntrada.FORMATO_INVALIDO));
            return false;
        }
        return true;
    }

    // Campo com valor nulo conta como ausente (E01-RN-10).
    private static boolean ausente(JsonNode valor) {
        return valor == null || valor.isNull();
    }
}
