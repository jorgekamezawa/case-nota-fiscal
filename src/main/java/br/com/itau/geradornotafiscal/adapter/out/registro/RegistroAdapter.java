package br.com.itau.geradornotafiscal.adapter.out.registro;

import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import org.springframework.stereotype.Service;

@Service
public class RegistroAdapter implements RegistroPort {
    @Override
    public void registrarNotaFiscal(NotaFiscal notaFiscal) {

        try {
            //Simula o registro da nota fiscal
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
