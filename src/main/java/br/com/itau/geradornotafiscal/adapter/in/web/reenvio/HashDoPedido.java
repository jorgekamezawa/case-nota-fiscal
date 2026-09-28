package br.com.itau.geradornotafiscal.adapter.in.web.reenvio;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Hash SHA-256 do pedido para reconhecer o reenvio (E03-RN-04, E03-NF-02, ADR-0014). Entram só os campos que o
 * contrato conhece, sem os nulos, escritos como na RFC 8785 (campos ordenados pelo código dos caracteres, sem espaços),
 * exceto os números: a RFC os converte em ponto flutuante, e números longos diferentes colidiriam.
 */
@Component
public class HashDoPedido {

    // Campos do contrato, lidos do próprio PedidoRequest: campo novo no contrato entra no hash sem outra lista para manter.
    private static final Campos CONTRATO = campos(PedidoRequest.class);

    public String calcular(JsonNode pedido) {
        StringBuilder canonico = new StringBuilder();
        escrever(pedido, CONTRATO, canonico);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonico.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
        }
    }

    private static void escrever(JsonNode no, Campos campos, StringBuilder saida) {
        if (no.isObject()) {
            // Objeto onde o contrato espera outro tipo entra inteiro, para contar como conteúdo diferente.
            Campos doObjeto = campos == null ? Campos.QUALQUER : campos;
            Map<String, JsonNode> conhecidos = new TreeMap<>();
            for (Map.Entry<String, JsonNode> campo : no.properties()) {
                if (doObjeto.conhece(campo.getKey()) && !campo.getValue().isNull()) {
                    conhecidos.put(campo.getKey(), campo.getValue());
                }
            }
            saida.append('{');
            String separador = "";
            for (Map.Entry<String, JsonNode> campo : conhecidos.entrySet()) {
                saida.append(separador);
                texto(campo.getKey(), saida);
                saida.append(':');
                escrever(campo.getValue(), doObjeto.filho(campo.getKey()), saida);
                separador = ",";
            }
            saida.append('}');
        } else if (no.isArray()) {
            saida.append('[');
            for (int i = 0; i < no.size(); i++) {
                saida.append(i == 0 ? "" : ",");
                escrever(no.get(i), campos, saida);
            }
            saida.append(']');
        } else if (no.isNumber()) {
            numero(no.decimalValue(), saida);
        } else if (no.isString()) {
            texto(no.asString(), saida);
        } else {
            saida.append(no.toString());
        }
    }

    // Valor exato, sem zeros à direita: 730 e 730.00 viram 73e1. Mantissa e expoente evitam escrever números enormes.
    private static void numero(BigDecimal valor, StringBuilder saida) {
        BigDecimal normalizado = valor.stripTrailingZeros();
        saida.append(normalizado.unscaledValue()).append('e').append(-normalizado.scale());
    }

    // Escape de texto da RFC 8785 (seção 3.2.2.2).
    private static void texto(String valor, StringBuilder saida) {
        saida.append('"');
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\b' -> saida.append("\\b");
                case '\f' -> saida.append("\\f");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                default -> {
                    if (c < 0x20) {
                        saida.append(String.format("\\u%04x", (int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        saida.append('"');
    }

    /** Campos conhecidos de um objeto do contrato; {@code null} para valor simples. {@code QUALQUER} aceita todos. */
    private record Campos(Map<String, Campos> filhos, boolean todos) {

        static final Campos QUALQUER = new Campos(Map.of(), true);

        boolean conhece(String nome) {
            return todos || filhos.containsKey(nome);
        }

        Campos filho(String nome) {
            return todos ? QUALQUER : filhos.get(nome);
        }
    }

    private static Campos campos(Class<?> tipo) {
        Map<String, Campos> filhos = new LinkedHashMap<>();
        for (RecordComponent componente : tipo.getRecordComponents()) {
            // O @JsonProperty do componente chega ao campo do record, não ao componente.
            filhos.put(nomeNoJson(tipo, componente), campos(componente.getGenericType()));
        }
        return new Campos(filhos, false);
    }

    private static String nomeNoJson(Class<?> tipo, RecordComponent componente) {
        try {
            return tipo.getDeclaredField(componente.getName()).getAnnotation(JsonProperty.class).value();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Campos campos(Type tipo) {
        if (tipo instanceof ParameterizedType lista && lista.getRawType() == List.class) {
            return campos(lista.getActualTypeArguments()[0]);
        }
        if (tipo instanceof Class<?> classe && classe.isRecord()) {
            return campos(classe);
        }
        return null;
    }
}
