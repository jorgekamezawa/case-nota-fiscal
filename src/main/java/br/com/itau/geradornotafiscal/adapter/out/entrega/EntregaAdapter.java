package br.com.itau.geradornotafiscal.adapter.out.entrega;

import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntregaAdapter implements EntregaPort {
    private final EntregaAgendamentoCliente entregaAgendamentoCliente;

    @Override
    public void agendarEntrega(NotaFiscal notaFiscal) {

            try {
                //Simula o agendamento da entrega
                Thread.sleep(150);
                entregaAgendamentoCliente.criarAgendamentoEntrega(notaFiscal);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

    }
}
