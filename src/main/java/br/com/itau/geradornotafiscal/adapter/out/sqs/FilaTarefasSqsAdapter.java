package br.com.itau.geradornotafiscal.adapter.out.sqs;

import br.com.itau.geradornotafiscal.application.port.out.FilaTarefasPort;
import br.com.itau.geradornotafiscal.config.FilasDeTarefas;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Publica a tarefa na fila do sistema, no mesmo formato da mensagem do Pipe ({@code infra/pipes/mensagem-tarefa.json}):
 * {@code id_pedido} como texto e o sistema (E02-NF-03, E02-NF-06).
 */
@Component
@RequiredArgsConstructor
public class FilaTarefasSqsAdapter implements FilaTarefasPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SqsClient sqs;
    private final FilasDeTarefas filas;

    @Override
    public void publicar(Long idPedido, Sistema sistema) {
        ObjectNode mensagem = JSON.createObjectNode().put("id_pedido", idPedido.toString()).put("sistema", sistema.name());
        sqs.sendMessage(envio -> envio.queueUrl(filas.fila(sistema)).messageBody(mensagem.toString()));
    }
}
