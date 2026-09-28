package br.com.itau.geradornotafiscal.adapter.out.entrega;

import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntregaAdapter implements EntregaPort {
    private final ObservationRegistry observationRegistry;
    private final EntregaAgendamentoCliente entregaAgendamentoCliente;

    // Ligado só no perfil local, para provar que a entrega fora do ar vira alerta (RFC O-06, E02-NF-08).
    @Value("${simulacao.entrega-fora-do-ar:false}")
    private boolean foraDoAr;

    @Override
    public void agendarEntrega(NotaFiscal notaFiscal) {
        Observation.createNotStarted("integracao", observationRegistry)
                .lowCardinalityKeyValue("sistema", "entrega")
                .observe(() -> {
                    if (foraDoAr) {
                        throw new IllegalStateException("Entrega fora do ar (simulação local)");
                    }
                    simularAgendamento(notaFiscal);
                });
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
