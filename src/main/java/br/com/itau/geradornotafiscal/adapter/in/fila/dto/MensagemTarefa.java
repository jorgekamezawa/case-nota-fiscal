package br.com.itau.geradornotafiscal.adapter.in.fila.dto;

import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensagem de uma tarefa: só {@code id_pedido} e sistema, sem dado pessoal (E02-NF-02). O {@code id_pedido} chega como
 * texto, como o Pipe o extrai do evento, ou como número; campo desconhecido é ignorado, para versões diferentes
 * conviverem no canary (E02-NF-03).
 */
public record MensagemTarefa(Long idPedido, Sistema sistema) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static MensagemTarefa ler(String corpo) {
        JsonNode mensagem = JSON.readTree(corpo);
        return new MensagemTarefa(Long.valueOf(mensagem.get("id_pedido").asString()),
                Sistema.valueOf(mensagem.get("sistema").asString()));
    }
}
