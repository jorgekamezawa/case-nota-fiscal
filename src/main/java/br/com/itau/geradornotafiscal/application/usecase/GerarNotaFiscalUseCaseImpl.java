package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.port.in.GerarNotaFiscalUseCase;
import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.Pedido;
import br.com.itau.geradornotafiscal.domain.service.calculo.Arredondamento;
import br.com.itau.geradornotafiscal.domain.service.frete.CalculadoraFrete;
import br.com.itau.geradornotafiscal.domain.service.tributacao.CalculadoraTributo;
import br.com.itau.geradornotafiscal.domain.service.tributacao.Tributacao;
import br.com.itau.geradornotafiscal.domain.service.validacao.RegrasDoPedido;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GerarNotaFiscalUseCaseImpl implements GerarNotaFiscalUseCase {

    private final RegrasDoPedido regrasDoPedido;
    private final Tributacao tributacao;
    private final CalculadoraTributo calculadoraTributo;
    private final CalculadoraFrete calculadoraFrete;
    private final Clock relogio;
    private final EstoquePort estoquePort;
    private final RegistroPort registroPort;
    private final EntregaPort entregaPort;
    private final FinanceiroPort financeiroPort;

    @Override
    public NotaFiscal gerarNotaFiscal(Pedido pedido) {
        regrasDoPedido.validar(pedido);

        Destinatario destinatario = pedido.destinatario();
        BigDecimal aliquota = tributacao.aliquota(destinatario, pedido.valorTotalItens());
        Endereco entrega = destinatario.enderecoDeEntrega().orElseThrow();

        NotaFiscal notaFiscal = new NotaFiscal(
                UUID.randomUUID().toString(),
                LocalDateTime.now(relogio),
                Arredondamento.duasCasas(pedido.valorTotalItens()),
                calculadoraFrete.calcular(pedido.valorFrete(), entrega.regiao()),
                calculadoraTributo.calcular(pedido.itens(), aliquota),
                destinatario);

        estoquePort.enviarNotaFiscalParaBaixaEstoque(notaFiscal);
        registroPort.registrarNotaFiscal(notaFiscal);
        entregaPort.agendarEntrega(notaFiscal);
        financeiroPort.enviarNotaFiscalParaContasReceber(notaFiscal);

        return notaFiscal;
    }
}
