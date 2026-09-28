package br.com.itau.geradornotafiscal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

/**
 * Pedido base dos exemplos da spec E-01, como árvore JSON, para cada exemplo aplicar só a sua mudança.
 */
public final class PedidoBase {

    public static final String CPF = "887.403.470-95";
    public static final String CNPJ = "49.695.613/0001-80";

    private static final JsonNodeFactory JSON = JsonNodeFactory.withExactBigDecimals(true);

    private PedidoBase() {
    }

    public static ObjectNode novo() {
        ObjectNode pedido = JSON.objectNode();
        pedido.put("id_pedido", 1);
        pedido.put("data", "2022-05-01");
        pedido.set("itens", JSON.arrayNode().add(item("1", "50.00", 2)));
        pedido.put("valor_total_itens", new BigDecimal("100.00"));
        pedido.put("valor_frete", new BigDecimal("10.00"));

        ObjectNode destinatario = pedido.putObject("destinatario");
        destinatario.put("nome", "Fulano de Tal");
        destinatario.put("tipo_pessoa", "FISICA");
        destinatario.set("documentos", JSON.arrayNode().add(documento("CPF", CPF)));
        destinatario.set("enderecos", JSON.arrayNode().add(endereco("ENTREGA", "SUDESTE")));
        return pedido;
    }

    /** Troca o CPF pelo CNPJ e passa a pessoa jurídica com o regime indicado. */
    public static ObjectNode pj(ObjectNode pedido, String regime) {
        ObjectNode destinatario = (ObjectNode) pedido.get("destinatario");
        destinatario.put("tipo_pessoa", "JURIDICA");
        destinatario.put("regime_tributacao", regime);
        destinatario.set("documentos", JSON.arrayNode().add(documento("CNPJ", CNPJ)));
        return pedido;
    }

    /** Troca os itens por um só, com o total declarado acompanhando a soma. */
    public static ObjectNode umItem(ObjectNode pedido, String valorUnitario, int quantidade) {
        pedido.set("itens", JSON.arrayNode().add(item("1", valorUnitario, quantidade)));
        pedido.put("valor_total_itens", new BigDecimal(valorUnitario).multiply(BigDecimal.valueOf(quantidade)));
        return pedido;
    }

    public static ObjectNode enderecos(ObjectNode pedido, ObjectNode... enderecos) {
        ArrayNode lista = JSON.arrayNode();
        for (ObjectNode endereco : enderecos) {
            lista.add(endereco);
        }
        ((ObjectNode) pedido.get("destinatario")).set("enderecos", lista);
        return pedido;
    }

    public static ObjectNode item(String id, String valorUnitario, int quantidade) {
        ObjectNode item = JSON.objectNode();
        item.put("id_item", id);
        item.put("descricao", "Teclado USB");
        item.put("valor_unitario", new BigDecimal(valorUnitario));
        item.put("quantidade", quantidade);
        return item;
    }

    public static ObjectNode endereco(String finalidade, String regiao) {
        ObjectNode endereco = JSON.objectNode();
        endereco.put("cep", "03105003");
        endereco.put("logradouro", "Av do Estado");
        endereco.put("numero", "5533");
        endereco.put("estado", "SP");
        endereco.put("complemento", "4 andar b");
        endereco.put("finalidade", finalidade);
        endereco.put("regiao", regiao);
        return endereco;
    }

    private static ObjectNode documento(String tipo, String numero) {
        ObjectNode documento = JSON.objectNode();
        documento.put("tipo", tipo);
        documento.put("numero", numero);
        return documento;
    }

    public static <T> T converter(ObjectMapper objectMapper, ObjectNode pedido, Class<T> tipo) {
        try {
            return objectMapper.treeToValue(pedido, tipo);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
