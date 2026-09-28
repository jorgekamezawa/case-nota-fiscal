package br.com.itau.geradornotafiscal.adapter.out.financeiro;

import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import org.springframework.stereotype.Service;

@Service
public class FinanceiroAdapter implements FinanceiroPort {
    @Override
    public void enviarNotaFiscalParaContasReceber(NotaFiscal notaFiscal) {

        try {
            //Simula o envio da nota fiscal para o contas a receber
            Thread.sleep(250);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
