package br.com.itau.geradornotafiscal.service.impl;

import br.com.itau.geradornotafiscal.model.*;
import br.com.itau.geradornotafiscal.service.CalculadoraAliquotaProduto;
import br.com.itau.geradornotafiscal.service.GeradorNotaFiscalService;
import br.com.itau.geradornotafiscal.service.calculo.Arredondamento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeradorNotaFiscalServiceImpl implements GeradorNotaFiscalService{
	private final CalculadoraAliquotaProduto calculadoraAliquotaProduto;
	private final EstoqueService estoqueService;
	private final RegistroService registroService;
	private final EntregaService entregaService;
	private final FinanceiroService financeiroService;

	@Override
	public NotaFiscal gerarNotaFiscal(Pedido pedido) {

		Destinatario destinatario = pedido.getDestinatario();
		TipoPessoa tipoPessoa = destinatario.getTipoPessoa();
		List<ItemNotaFiscal> itemNotaFiscalList = new ArrayList<>();

		if (tipoPessoa == TipoPessoa.FISICA) {
			BigDecimal valorTotalItens = pedido.getValorTotalItens();
			BigDecimal aliquota;

			if (valorTotalItens.compareTo(new BigDecimal("500")) < 0) {
				aliquota = BigDecimal.ZERO;
			} else if (valorTotalItens.compareTo(new BigDecimal("2000")) <= 0) {
				aliquota = new BigDecimal("0.12");
			} else if (valorTotalItens.compareTo(new BigDecimal("3500")) <= 0) {
				aliquota = new BigDecimal("0.15");
			} else {
				aliquota = new BigDecimal("0.17");
			}
			itemNotaFiscalList = calculadoraAliquotaProduto.calcularAliquota(pedido.getItens(), aliquota);
		} else if (tipoPessoa == TipoPessoa.JURIDICA) {

			RegimeTributacaoPJ regimeTributacao = destinatario.getRegimeTributacao();

			if (regimeTributacao == RegimeTributacaoPJ.SIMPLES_NACIONAL) {

				BigDecimal valorTotalItens = pedido.getValorTotalItens();
				BigDecimal aliquota;

				if (valorTotalItens.compareTo(new BigDecimal("1000")) < 0) {
					aliquota = new BigDecimal("0.03");
				} else if (valorTotalItens.compareTo(new BigDecimal("2000")) <= 0) {
					aliquota = new BigDecimal("0.07");
				} else if (valorTotalItens.compareTo(new BigDecimal("5000")) <= 0) {
					aliquota = new BigDecimal("0.13");
				} else {
					aliquota = new BigDecimal("0.19");
				}
				itemNotaFiscalList = calculadoraAliquotaProduto.calcularAliquota(pedido.getItens(), aliquota);
			} else if (regimeTributacao == RegimeTributacaoPJ.LUCRO_REAL) {
				BigDecimal valorTotalItens = pedido.getValorTotalItens();
				BigDecimal aliquota;

				if (valorTotalItens.compareTo(new BigDecimal("1000")) < 0) {
					aliquota = new BigDecimal("0.03");
				} else if (valorTotalItens.compareTo(new BigDecimal("2000")) <= 0) {
					aliquota = new BigDecimal("0.09");
				} else if (valorTotalItens.compareTo(new BigDecimal("5000")) <= 0) {
					aliquota = new BigDecimal("0.15");
				} else {
					aliquota = new BigDecimal("0.20");
				}
				itemNotaFiscalList= calculadoraAliquotaProduto.calcularAliquota(pedido.getItens(),aliquota);
			} else if (regimeTributacao == RegimeTributacaoPJ.LUCRO_PRESUMIDO) {
				BigDecimal valorTotalItens = pedido.getValorTotalItens();
				BigDecimal aliquota;

				if (valorTotalItens.compareTo(new BigDecimal("1000")) < 0) {
					aliquota = new BigDecimal("0.03");
				} else if (valorTotalItens.compareTo(new BigDecimal("2000")) <= 0) {
					aliquota = new BigDecimal("0.09");
				} else if (valorTotalItens.compareTo(new BigDecimal("5000")) <= 0) {
					aliquota = new BigDecimal("0.16");
				} else {
					aliquota = new BigDecimal("0.20");
				}
				itemNotaFiscalList = calculadoraAliquotaProduto.calcularAliquota(pedido.getItens(),aliquota);
			}
		}
		//Regras diferentes para frete

		Regiao regiao = destinatario.getEnderecos().stream()
				.filter(endereco -> endereco.getFinalidade() == Finalidade.ENTREGA || endereco.getFinalidade() == Finalidade.COBRANCA_ENTREGA)
				.map(Endereco::getRegiao)
				.findFirst()
				.orElse(null);

		BigDecimal valorFrete = pedido.getValorFrete();
		BigDecimal valorFreteComPercentual = BigDecimal.ZERO;

		if (regiao == Regiao.NORTE) {
			valorFreteComPercentual = Arredondamento.duasCasas(valorFrete.multiply(new BigDecimal("1.08")));
		} else if (regiao == Regiao.NORDESTE) {
			valorFreteComPercentual = Arredondamento.duasCasas(valorFrete.multiply(new BigDecimal("1.085")));
		} else if (regiao == Regiao.CENTRO_OESTE) {
			valorFreteComPercentual = Arredondamento.duasCasas(valorFrete.multiply(new BigDecimal("1.07")));
		} else if (regiao == Regiao.SUDESTE) {
			valorFreteComPercentual = Arredondamento.duasCasas(valorFrete.multiply(new BigDecimal("1.048")));
		} else if (regiao == Regiao.SUL) {
			valorFreteComPercentual = Arredondamento.duasCasas(valorFrete.multiply(new BigDecimal("1.06")));
		}

		// Create the NotaFiscal object
		String idNotaFiscal = UUID.randomUUID().toString();

		NotaFiscal notaFiscal = NotaFiscal.builder()
				.idNotaFiscal(idNotaFiscal)
				.data(LocalDateTime.now())
				.valorTotalItens(Arredondamento.duasCasas(pedido.getValorTotalItens()))
				.valorFrete(valorFreteComPercentual)
				.itens(itemNotaFiscalList)
				.destinatario(pedido.getDestinatario())
				.build();

		estoqueService.enviarNotaFiscalParaBaixaEstoque(notaFiscal);
		registroService.registrarNotaFiscal(notaFiscal);
		entregaService.agendarEntrega(notaFiscal);
		financeiroService.enviarNotaFiscalParaContasReceber(notaFiscal);

		return notaFiscal;
	}
}