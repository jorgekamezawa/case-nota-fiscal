package br.com.itau.geradornotafiscal.adapter.in.web.handler;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.RespostaProblema;
import br.com.itau.geradornotafiscal.adapter.in.web.validacao.ViolacaoEntrada;
import br.com.itau.geradornotafiscal.adapter.in.web.validacao.ViolacoesDeEntrada;
import br.com.itau.geradornotafiscal.application.exception.ArmazenamentoIndisponivelException;
import br.com.itau.geradornotafiscal.application.exception.NotaGrandeDemaisException;
import br.com.itau.geradornotafiscal.domain.exception.PedidoInvalidoException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.Optional;

/**
 * Converte recusas e erros em Problem Details (E01-NF-03). As duas etapas da validação (E01-RN-09) respondem
 * o mesmo `pedido-invalido`. Os demais erros do Spring MVC (405, 415 etc.)
 * seguem o tratamento padrão da classe base.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class TratadorDeErros extends ResponseEntityExceptionHandler {

    private static final String PREFIXO_TYPE = "/erros/";

    private final MeterRegistry meterRegistry;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<ConstraintViolation<?>> violacoes = e.getBindingResult().getAllErrors().stream()
                .<ConstraintViolation<?>>map(erro -> erro.unwrap(ConstraintViolation.class))
                .toList();
        return entradaInvalida(ViolacoesDeEntrada.deAnotacoes(violacoes));
    }

    @ExceptionHandler(PedidoInvalidoException.class)
    public ResponseEntity<Object> regraDeNegocio(PedidoInvalidoException e) {
        return pedidoInvalido(e.violacoes().stream()
                .map(v -> new RespostaProblema.CampoInvalido(v.campo(), PREFIXO_TYPE + v.motivo().codigo(), v.detalhe()))
                .toList());
    }

    private ResponseEntity<Object> entradaInvalida(List<ViolacaoEntrada> violacoes) {
        return pedidoInvalido(violacoes.stream()
                .map(v -> new RespostaProblema.CampoInvalido(v.campo(), PREFIXO_TYPE + v.motivo().codigo(), v.detalhe()))
                .toList());
    }

    private static Optional<JacksonException> erroDoJackson(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof JacksonException jackson) {
                return Optional.of(jackson);
            }
        }
        return Optional.empty();
    }

    private ResponseEntity<Object> pedidoInvalido(List<RespostaProblema.CampoInvalido> campos) {
        return problema(HttpStatus.BAD_REQUEST, "pedido-invalido", "Pedido inválido",
                "O pedido tem " + campos.size() + " campo(s) inválido(s).", campos);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Optional<ViolacaoEntrada> erroDeTipo = erroDoJackson(e).flatMap(ViolacoesDeEntrada::deConversao);
        if (erroDeTipo.isPresent()) {
            return entradaInvalida(List.of(erroDeTipo.get()));
        }
        return problema(HttpStatus.BAD_REQUEST, "json-invalido", "Corpo inválido",
                "O corpo da requisição não é um pedido em JSON válido.", List.of());
    }

    @ExceptionHandler(NotaGrandeDemaisException.class)
    public ResponseEntity<Object> notaGrandeDemais(NotaGrandeDemaisException e) {
        return problema(HttpStatus.BAD_REQUEST, "pedido-grande-demais", "Pedido grande demais",
                "A nota do pedido passa do tamanho que o serviço consegue guardar.", List.of());
    }

    // Nada foi guardado: o consumidor pode reenviar o pedido (E04-RN-01, E04-NF-02).
    @ExceptionHandler(ArmazenamentoIndisponivelException.class)
    public ResponseEntity<Object> armazenamentoIndisponivel(ArmazenamentoIndisponivelException e) {
        log.warn("Armazenamento das notas indisponível", e);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "servico-indisponivel", "Serviço indisponível",
                "Não foi possível guardar a nota; tente novamente.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> erroInesperado(Exception e) {
        log.error("Erro inesperado ao gerar a nota fiscal", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Erro interno",
                "Erro inesperado ao processar o pedido.", List.of());
    }

    private ResponseEntity<Object> problema(HttpStatus status, String codigo, String titulo, String detalhe,
                                                   List<RespostaProblema.CampoInvalido> campos) {
        if (status == HttpStatus.BAD_REQUEST) {
            registrarRecusa(PREFIXO_TYPE + codigo, campos);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new RespostaProblema(PREFIXO_TYPE + codigo, titulo, status.value(), detalhe, campos));
    }

    // Uma contagem por type distinto dos campos; sem campos (json-invalido), o type geral (F04-NF-07).
    private void registrarRecusa(String typeGeral, List<RespostaProblema.CampoInvalido> campos) {
        List<String> types = campos.isEmpty()
                ? List.of(typeGeral)
                : campos.stream().map(RespostaProblema.CampoInvalido::type).distinct().toList();
        types.forEach(type -> meterRegistry.counter("recusas", "type", type).increment());
        // Sem id_pedido: na recusa o corpo pode nem ser legível (F04-NF-04).
        log.atInfo().addKeyValue("status", HttpStatus.BAD_REQUEST.value()).addKeyValue("types", types).log("Pedido recusado");
    }
}
