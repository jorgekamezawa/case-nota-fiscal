package br.com.itau.geradornotafiscal.web.controller;

import br.com.itau.geradornotafiscal.model.NotaFiscal;

import br.com.itau.geradornotafiscal.model.Pedido;
import br.com.itau.geradornotafiscal.service.GeradorNotaFiscalService;
import br.com.itau.geradornotafiscal.service.validacao.ValidadorPedido;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedido")
@RequiredArgsConstructor
public class GeradorNFController {

	private final GeradorNotaFiscalService notaFiscalService;
	private final ValidadorPedido validadorPedido;
	private final ObjectMapper objectMapper;

	// Recebe o corpo como árvore JSON para listar todos os erros de formato de uma vez, antes da conversão.
	@PostMapping("/gerarNotaFiscal")
	public ResponseEntity<NotaFiscal> gerarNotaFiscal(@RequestBody ObjectNode corpo) {
		validadorPedido.validar(corpo);
		Pedido pedido = objectMapper.treeToValue(corpo, Pedido.class);
		NotaFiscal notaFiscal = notaFiscalService.gerarNotaFiscal(pedido);
		return new ResponseEntity<>(notaFiscal, HttpStatus.OK);
	}
	
}
