package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.port.in.GerarNotaFiscalUseCase;
import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.Pedido;
import br.com.itau.geradornotafiscal.domain.service.frete.CalculadoraFrete;
import br.com.itau.geradornotafiscal.domain.service.guarda.PrazoDeGuarda;
import br.com.itau.geradornotafiscal.domain.service.tributacao.CalculadoraTributo;
import br.com.itau.geradornotafiscal.domain.service.tributacao.Tributacao;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GerarNotaFiscalUseCaseImpl implements GerarNotaFiscalUseCase {

    private final Tributacao tributacao;
    private final CalculadoraTributo calculadoraTributo;
    private final CalculadoraFrete calculadoraFrete;
    private final Clock relogio;
    private final PrazoDeGuarda prazoDeGuarda;
    private final NotaFiscalPersistenciaPort notaFiscalPersistenciaPort;
    private final EstoquePort estoquePort;
    private final RegistroPort registroPort;
    private final EntregaPort entregaPort;
    private final FinanceiroPort financeiroPort;

    @Override
    public NotaFiscal gerarNotaFiscal(GerarNotaFiscalCommand comando) {
        Pedido pedido = Pedido.criar(comando.idPedido(), comando.data(), comando.valorTotalItens(), comando.valorFrete(),
                comando.itens(), comando.destinatario());

        Destinatario destinatario = pedido.getDestinatario();
        BigDecimal aliquota = tributacao.aliquota(destinatario, pedido.getValorTotalItens());
        Endereco entrega = destinatario.enderecoDeEntrega().orElseThrow();

        NotaFiscal notaFiscal = NotaFiscal.emitir(
                pedido,
                calculadoraTributo.calcular(pedido.getItens(), aliquota),
                calculadoraFrete.calcular(pedido.getValorFrete(), entrega.regiao()),
                LocalDateTime.now(relogio));

        // Nenhum sistema é acionado antes de a nota estar guardada (E04-RN-01, E02-RN-02).
        notaFiscalPersistenciaPort.guardar(pedido.getIdPedido(), notaFiscal, prazoDeGuarda.apagarAPartirDe(notaFiscal.getData()));

        estoquePort.enviarNotaFiscalParaBaixaEstoque(notaFiscal);
        registroPort.registrarNotaFiscal(notaFiscal);
        entregaPort.agendarEntrega(notaFiscal);
        financeiroPort.enviarNotaFiscalParaContasReceber(notaFiscal);

        return notaFiscal;
    }
}
