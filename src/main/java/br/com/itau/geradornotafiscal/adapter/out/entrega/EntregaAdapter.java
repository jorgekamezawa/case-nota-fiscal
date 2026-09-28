package br.com.itau.geradornotafiscal.adapter.out.entrega;

import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntregaAdapter implements EntregaPort {
    private final ObservationRegistry observationRegistry;
    private final EntregaAgendamentoCliente entregaAgendamentoCliente;

    @Override
    public void agendarEntrega(NotaFiscal notaFiscal) {
        // Span e métrica de duração da integração (F04-NF-02, F04-NF-07).
        Observation.createNotStarted("integracao", observationRegistry)
                .lowCardinalityKeyValue("sistema", "entrega")
                .observe(() -> simularAgendamento(notaFiscal));
    }

    private void simularAgendamento(NotaFiscal notaFiscal) {

            try {
                //Simula o agendamento da entrega
                Thread.sleep(150);
                entregaAgendamentoCliente.criarAgendamentoEntrega(notaFiscal);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

    }
}
