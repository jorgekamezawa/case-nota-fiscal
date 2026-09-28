package br.com.itau.geradornotafiscal.port.out;

import br.com.itau.geradornotafiscal.model.NotaFiscal;
import org.springframework.stereotype.Component;

@Component
public class EntregaIntegrationPort {
    public void criarAgendamentoEntrega(NotaFiscal notaFiscal) {

            try {
                //Simula o agendamento da entrega
                if(notaFiscal.getItens().size() > 5){
                    /* Aqui está o problema de performance do aplicacao para pedidos com mais de 5 itens
                        Se voce chegou ate aqui basta remover esse valor de 5s para 'solucionar' o misterio
                    * */
                    Thread.sleep(5000);
                }
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
    }
}
