package br.com.itau.geradornotafiscal.adapter.out.estoque;

import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EstoqueAdapter implements EstoquePort {
    private final ObservationRegistry observationRegistry;
    @Override
    public void enviarNotaFiscalParaBaixaEstoque(NotaFiscal notaFiscal) {
        // Span e métrica de duração da integração (F04-NF-02, F04-NF-07).
        Observation.createNotStarted("integracao", observationRegistry)
                .lowCardinalityKeyValue("sistema", "estoque")
                .observe(() -> simularBaixaEstoque(notaFiscal));
    }

    private void simularBaixaEstoque(NotaFiscal notaFiscal) {
        try {
            //Simula envio de nota fiscal para baixa de estoque
            Thread.sleep(380);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
