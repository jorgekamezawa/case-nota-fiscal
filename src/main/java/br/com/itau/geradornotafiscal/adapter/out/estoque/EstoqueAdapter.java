package br.com.itau.geradornotafiscal.adapter.out.estoque;

import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import org.springframework.stereotype.Service;

@Service
public class EstoqueAdapter implements EstoquePort {
    @Override
    public void enviarNotaFiscalParaBaixaEstoque(NotaFiscal notaFiscal) {
        try {
            //Simula envio de nota fiscal para baixa de estoque
            Thread.sleep(380);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
