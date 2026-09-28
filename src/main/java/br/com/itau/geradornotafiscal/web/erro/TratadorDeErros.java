package br.com.itau.geradornotafiscal.web.erro;

import br.com.itau.geradornotafiscal.service.validacao.PedidoInvalidoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converte recusas e erros em Problem Details (E01-NF-03). Os demais erros do Spring MVC (405, 415 etc.)
 * seguem o tratamento padrão da classe base.
 */
@Slf4j
@RestControllerAdvice
public class TratadorDeErros extends ResponseEntityExceptionHandler {

    private static final String PREFIXO_TYPE = "/erros/";

    @ExceptionHandler(PedidoInvalidoException.class)
    public ResponseEntity<Object> pedidoInvalido(PedidoInvalidoException e) {
        List<RespostaProblema.CampoInvalido> campos = e.getViolacoes().stream()
                .map(v -> new RespostaProblema.CampoInvalido(v.getCampo(), PREFIXO_TYPE + v.getMotivo().getCodigo(), v.getDetalhe()))
                .collect(Collectors.toList());
        return problema(HttpStatus.BAD_REQUEST, "pedido-invalido", "Pedido inválido",
                "O pedido tem " + campos.size() + " campo(s) inválido(s).", campos);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "json-invalido", "Corpo inválido",
                "O corpo da requisição não é um pedido em JSON válido.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> erroInesperado(Exception e) {
        log.error("Erro inesperado ao gerar a nota fiscal", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Erro interno",
                "Erro inesperado ao processar o pedido.", List.of());
    }

    private static ResponseEntity<Object> problema(HttpStatus status, String codigo, String titulo, String detalhe,
                                                   List<RespostaProblema.CampoInvalido> campos) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new RespostaProblema(PREFIXO_TYPE + codigo, titulo, status.value(), detalhe, campos));
    }
}
