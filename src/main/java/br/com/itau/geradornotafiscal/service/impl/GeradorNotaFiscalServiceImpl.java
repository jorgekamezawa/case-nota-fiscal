package br.com.itau.geradornotafiscal.service.impl;

import br.com.itau.geradornotafiscal.model.*;
import br.com.itau.geradornotafiscal.service.CalculadoraAliquotaProduto;
import br.com.itau.geradornotafiscal.service.GeradorNotaFiscalService;
import br.com.itau.geradornotafiscal.service.calculo.Arredondamento;
import br.com.itau.geradornotafiscal.service.frete.CalculadoraFrete;
import br.com.itau.geradornotafiscal.service.tributacao.TabelaAliquotas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeradorNotaFiscalServiceImpl implements GeradorNotaFiscalService{
	private final TabelaAliquotas tabelaAliquotas;
	private final CalculadoraAliquotaProduto calculadoraAliquotaProduto;
	private final CalculadoraFrete calculadoraFrete;
	private final Clock relogio;
	private final EstoqueService estoqueService;
	private final RegistroService registroService;
	private final EntregaService entregaService;
	private final FinanceiroService financeiroService;

	@Override
	public NotaFiscal gerarNotaFiscal(Pedido pedido) {
		Destinatario destinatario = pedido.getDestinatario();
		BigDecimal aliquota = tabelaAliquotas.aliquota(
				destinatario.getTipoPessoa(), destinatario.getRegimeTributacao(), pedido.getValorTotalItens());

		NotaFiscal notaFiscal = NotaFiscal.builder()
				.idNotaFiscal(UUID.randomUUID().toString())
				.data(LocalDateTime.now(relogio))
				.valorTotalItens(Arredondamento.duasCasas(pedido.getValorTotalItens()))
				.valorFrete(calculadoraFrete.calcular(pedido.getValorFrete(), regiaoDeEntrega(destinatario)))
				.itens(calculadoraAliquotaProduto.calcularAliquota(pedido.getItens(), aliquota))
				.destinatario(destinatario)
				.build();

		estoqueService.enviarNotaFiscalParaBaixaEstoque(notaFiscal);
		registroService.registrarNotaFiscal(notaFiscal);
		entregaService.agendarEntrega(notaFiscal);
		financeiroService.enviarNotaFiscalParaContasReceber(notaFiscal);

		return notaFiscal;
	}

	// O endereço de entrega é o primeiro com finalidade ENTREGA ou COBRANCA_ENTREGA (E01-RN-06).
	private static Regiao regiaoDeEntrega(Destinatario destinatario) {
		return destinatario.getEnderecos().stream()
				.filter(endereco -> endereco.getFinalidade() == Finalidade.ENTREGA || endereco.getFinalidade() == Finalidade.COBRANCA_ENTREGA)
				.map(Endereco::getRegiao)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Pedido sem endereço de entrega"));
	}
}
