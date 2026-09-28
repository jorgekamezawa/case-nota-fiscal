package br.com.itau.geradornotafiscal.adapter.in.agendador;

import br.com.itau.geradornotafiscal.application.port.in.ConsultarSituacaoDasTarefasUseCase;
import br.com.itau.geradornotafiscal.application.port.in.result.SituacaoDoSistema;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Medidores por sistema: tarefas pendentes, idade da mais antiga e mensagens na DLQ, base dos alertas de tarefa parada
 * e de falha persistente (E02-NF-08, E02-RN-05). Várias instâncias medem o mesmo valor: os alertas agregam por máximo.
 */
@Component
@ConditionalOnProperty(name = "medicao.ativa", matchIfMissing = true)
public class MedicaoDasTarefas {

    private final ConsultarSituacaoDasTarefasUseCase consultarSituacaoDasTarefasUseCase;
    private final Map<Sistema, AtomicLong> pendentes = new EnumMap<>(Sistema.class);
    private final Map<Sistema, AtomicLong> idadeEmSegundos = new EnumMap<>(Sistema.class);
    private final Map<Sistema, AtomicLong> naDlq = new EnumMap<>(Sistema.class);

    public MedicaoDasTarefas(ConsultarSituacaoDasTarefasUseCase consultarSituacaoDasTarefasUseCase, MeterRegistry meterRegistry) {
        this.consultarSituacaoDasTarefasUseCase = consultarSituacaoDasTarefasUseCase;
        for (Sistema sistema : Sistema.values()) {
            String nome = sistema.name().toLowerCase(Locale.ROOT);
            pendentes.put(sistema, medidor(meterRegistry, "tarefas.pendentes", nome, null));
            idadeEmSegundos.put(sistema, medidor(meterRegistry, "tarefas.pendente.mais.antiga", nome, "seconds"));
            naDlq.put(sistema, medidor(meterRegistry, "fila.dlq.mensagens", nome, null));
        }
    }

    @Scheduled(fixedDelayString = "${medicao.intervalo:PT1M}", initialDelayString = "${medicao.intervalo:PT1M}")
    public void medir() {
        for (SituacaoDoSistema situacao : consultarSituacaoDasTarefasUseCase.executar()) {
            pendentes.get(situacao.sistema()).set(situacao.pendentes());
            idadeEmSegundos.get(situacao.sistema()).set(situacao.maisAntiga().toSeconds());
            naDlq.get(situacao.sistema()).set(situacao.mensagensNaDlq());
        }
    }

    private static AtomicLong medidor(MeterRegistry meterRegistry, String nome, String sistema, String unidade) {
        AtomicLong valor = new AtomicLong();
        Gauge.builder(nome, valor, AtomicLong::get).tag("sistema", sistema).baseUnit(unidade).register(meterRegistry);
        return valor;
    }
}
