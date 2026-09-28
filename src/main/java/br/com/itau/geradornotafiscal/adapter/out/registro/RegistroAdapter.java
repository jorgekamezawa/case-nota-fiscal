package br.com.itau.geradornotafiscal.adapter.out.registro;

import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistroAdapter implements RegistroPort {
    private final ObservationRegistry observationRegistry;
    @Override
    public void registrarNotaFiscal(NotaFiscal notaFiscal) {
        // Span e métrica de duração da integração (F04-NF-02, F04-NF-07).
        Observation.createNotStarted("integracao", observationRegistry)
                .lowCardinalityKeyValue("sistema", "registro")
                .observe(() -> simularRegistro(notaFiscal));
    }

    private void simularRegistro(NotaFiscal notaFiscal) {

        try {
            //Simula o registro da nota fiscal
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
