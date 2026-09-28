package br.com.itau.geradornotafiscal.adapter.in.web.controller;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.NotaFiscalResponse;
import br.com.itau.geradornotafiscal.adapter.in.web.mappers.NotaFiscalMapper;
import br.com.itau.geradornotafiscal.adapter.in.web.mappers.PedidoMapper;
import br.com.itau.geradornotafiscal.adapter.in.web.validacao.ValidadorEntrada;
import br.com.itau.geradornotafiscal.application.port.in.GerarNotaFiscalUseCase;
import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/pedido")
@RequiredArgsConstructor
public class GeradorNFController {

	private final GerarNotaFiscalUseCase gerarNotaFiscalUseCase;
	private final ValidadorEntrada validadorEntrada;
	private final PedidoMapper pedidoMapper;
	private final NotaFiscalMapper notaFiscalMapper;
	private final ObjectMapper objectMapper;

	// Recebe o corpo como árvore JSON para conferir preenchimento e formato (etapa 1) antes da conversão.
	@PostMapping("/gerarNotaFiscal")
	public ResponseEntity<NotaFiscalResponse> gerarNotaFiscal(@RequestBody ObjectNode corpo) {
		validadorEntrada.validar(corpo);
		GerarNotaFiscalCommand comando = pedidoMapper.paraComando(objectMapper.treeToValue(corpo, PedidoRequest.class));
		NotaFiscal notaFiscal = gerarNotaFiscalUseCase.gerarNotaFiscal(comando);
		return new ResponseEntity<>(notaFiscalMapper.paraResponse(notaFiscal), HttpStatus.OK);
	}
}
