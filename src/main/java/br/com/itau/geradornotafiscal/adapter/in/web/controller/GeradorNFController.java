package br.com.itau.geradornotafiscal.adapter.in.web.controller;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.NotaFiscalResponse;
import br.com.itau.geradornotafiscal.adapter.in.web.mappers.NotaFiscalMapper;
import br.com.itau.geradornotafiscal.adapter.in.web.mappers.PedidoMapper;
import br.com.itau.geradornotafiscal.application.port.in.GerarNotaFiscalUseCase;
import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/pedido")
@RequiredArgsConstructor
public class GeradorNFController {

	private final GerarNotaFiscalUseCase gerarNotaFiscalUseCase;
	private final PedidoMapper pedidoMapper;
	private final NotaFiscalMapper notaFiscalMapper;
	private final MeterRegistry meterRegistry;

	// A etapa 1 da validação (tipo, preenchimento e casas decimais) acontece na conversão e no @Valid (E01-RN-09).
	@PostMapping("/gerarNotaFiscal")
	public ResponseEntity<NotaFiscalResponse> gerarNotaFiscal(@Valid @RequestBody PedidoRequest pedido) {
		GerarNotaFiscalCommand comando = pedidoMapper.paraComando(pedido);
		NotaFiscal notaFiscal = gerarNotaFiscalUseCase.gerarNotaFiscal(comando);
		meterRegistry.counter("notas.emitidas").increment();
		log.atInfo().addKeyValue("id_pedido", pedido.idPedido()).addKeyValue("id_nota_fiscal", notaFiscal.getIdNotaFiscal())
				.log("Nota fiscal emitida");
		return new ResponseEntity<>(notaFiscalMapper.paraResponse(notaFiscal), HttpStatus.OK);
	}
}
