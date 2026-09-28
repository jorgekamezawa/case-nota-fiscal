package br.com.itau.geradornotafiscal.adapter.in.agendador;

import br.com.itau.geradornotafiscal.application.port.in.ReconciliarTarefasUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Reconciliação a cada 5 minutos das tarefas abertas há mais de 15, prazo maior que a janela de tentativas; os dois
 * prazos são configuração, curtos no perfil local, que não tem Pipe (E02-NF-06).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "reconciliacao.ativa", matchIfMissing = true)
public class ReconciliacaoAgendada {

    private final ReconciliarTarefasUseCase reconciliarTarefasUseCase;
    private final Duration abertasHaMaisDe;

    public ReconciliacaoAgendada(ReconciliarTarefasUseCase reconciliarTarefasUseCase,
                                 @Value("${reconciliacao.abertas-ha-mais-de:PT15M}") Duration abertasHaMaisDe) {
        this.reconciliarTarefasUseCase = reconciliarTarefasUseCase;
        this.abertasHaMaisDe = abertasHaMaisDe;
    }

    @Scheduled(fixedDelayString = "${reconciliacao.intervalo:PT5M}", initialDelayString = "${reconciliacao.intervalo:PT5M}")
    public void reconciliar() {
        int recolocadas = reconciliarTarefasUseCase.reconciliar(abertasHaMaisDe);
        if (recolocadas > 0) {
            log.atInfo().addKeyValue("recolocadas", recolocadas).log("Reconciliação recolocou tarefas na fila");
        }
    }
}
