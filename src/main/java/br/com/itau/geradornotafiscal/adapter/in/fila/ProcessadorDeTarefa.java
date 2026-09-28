package br.com.itau.geradornotafiscal.adapter.in.fila;

import br.com.itau.geradornotafiscal.application.port.in.ExecutarTarefaUseCase;
import br.com.itau.geradornotafiscal.application.port.in.ResultadoTarefa;
import br.com.itau.geradornotafiscal.application.port.in.TarefaExecutada;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Processa uma tarefa num trace próprio, com o span da integração dentro dele, e registra o resultado em log com
 * {@code id_pedido} e sistema em campos próprios, sem dado pessoal (E02-NF-09). Conta as tarefas por sistema e
 * resultado e mede o tempo da emissão à conclusão, base do SLO de 99% em até 5 minutos (E02-NF-08).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessadorDeTarefa {

    private final ExecutarTarefaUseCase executarTarefaUseCase;
    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;
    private final Clock relogio;

    public ResultadoTarefa processar(Long idPedido, Sistema sistema) {
        String nomeDoSistema = sistema.name().toLowerCase(Locale.ROOT);
        TarefaExecutada executada = Observation.createNotStarted("tarefa", observationRegistry)
                .lowCardinalityKeyValue("sistema", nomeDoSistema)
                .observe(() -> executarTarefaUseCase.executar(idPedido, sistema));
        ResultadoTarefa resultado = executada.resultado();
        meterRegistry.counter("tarefas", "sistema", nomeDoSistema, "resultado", resultado.name().toLowerCase(Locale.ROOT))
                .increment();
        if (resultado == ResultadoTarefa.CONCLUIDA) {
            meterRegistry.timer("tarefas.conclusao", "sistema", nomeDoSistema)
                    .record(Duration.between(executada.pendenteDesde(), relogio.instant()));
        }
        log.atInfo().addKeyValue("id_pedido", idPedido).addKeyValue("sistema", sistema.name())
                .addKeyValue("resultado", resultado.name()).log("Tarefa processada");
        return resultado;
    }
}
