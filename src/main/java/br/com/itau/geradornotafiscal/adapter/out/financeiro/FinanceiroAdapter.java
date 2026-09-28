package br.com.itau.geradornotafiscal.adapter.out.financeiro;

import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinanceiroAdapter implements FinanceiroPort {
    private final ObservationRegistry observationRegistry;
    @Override
    public void enviarNotaFiscalParaContasReceber(NotaFiscal notaFiscal) {
        // Span e métrica de duração da integração (F04-NF-02, F04-NF-07).
        Observation.createNotStarted("integracao", observationRegistry)
                .lowCardinalityKeyValue("sistema", "financeiro")
                .observe(() -> simularContasReceber(notaFiscal));
    }

    private void simularContasReceber(NotaFiscal notaFiscal) {

        try {
            //Simula o envio da nota fiscal para o contas a receber
            Thread.sleep(250);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
