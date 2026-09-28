package br.com.itau.geradornotafiscal.adapter.in.fila;

import br.com.itau.geradornotafiscal.application.port.in.ExecutarTarefaUseCase;
import br.com.itau.geradornotafiscal.application.port.in.ResultadoTarefa;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Processa uma tarefa num trace próprio, com o span da integração dentro dele, e registra o resultado em log com
 * {@code id_pedido} e sistema em campos próprios, sem dado pessoal (E02-NF-09).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessadorDeTarefa {

    private final ExecutarTarefaUseCase executarTarefaUseCase;
    private final ObservationRegistry observationRegistry;

    public ResultadoTarefa processar(Long idPedido, Sistema sistema) {
        ResultadoTarefa resultado = Observation.createNotStarted("tarefa", observationRegistry)
                .lowCardinalityKeyValue("sistema", sistema.name().toLowerCase(Locale.ROOT))
                .observe(() -> executarTarefaUseCase.executar(idPedido, sistema));
        log.atInfo().addKeyValue("id_pedido", idPedido).addKeyValue("sistema", sistema.name())
                .addKeyValue("resultado", resultado.name()).log("Tarefa processada");
        return resultado;
    }
}
