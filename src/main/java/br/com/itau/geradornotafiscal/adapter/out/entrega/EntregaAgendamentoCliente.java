package br.com.itau.geradornotafiscal.adapter.out.entrega;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import org.springframework.stereotype.Component;

@Component
public class EntregaAgendamentoCliente {
    public void criarAgendamentoEntrega(NotaFiscal notaFiscal) {

            try {
                //Simula o agendamento da entrega
                if(notaFiscal.getItens().size() > 5){
                    // Espera maior com 6 linhas de item ou mais: faz parte do cenário simulado e não deve ser removida (RFC R-02).
                    Thread.sleep(5000);
                }
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
    }
}
