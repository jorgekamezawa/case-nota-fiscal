package br.com.itau.geradornotafiscal.adapter.in.web.reenvio;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Lê o corpo antes da conversão, onde a etapa 1 da validação acontece, e guarda na requisição o {@code id_pedido} e o
 * hash: com eles, até um pedido recusado na etapa 1 é reconhecido como reenvio (E03-RN-01).
 */
@ControllerAdvice
@RequiredArgsConstructor
public class LeitorDoCorpo extends RequestBodyAdviceAdapter {

    public static final String ATRIBUTO = "geradornotafiscal.pedidoRecebido";

    private final JsonMapper jsonMapper;
    private final HashDoPedido hashDoPedido;

    @Override
    public boolean supports(MethodParameter parametro, Type tipo, Class<? extends HttpMessageConverter<?>> conversor) {
        return tipo == PedidoRequest.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage entrada, MethodParameter parametro, Type tipo,
                                           Class<? extends HttpMessageConverter<?>> conversor) throws IOException {
        byte[] corpo = entrada.getBody().readAllBytes();
        ler(corpo);
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(corpo);
            }

            @Override
            public HttpHeaders getHeaders() {
                return entrada.getHeaders();
            }
        };
    }

    // Corpo que não é um objeto JSON fica sem atributo: a conversão recusa como json-invalido.
    private void ler(byte[] corpo) {
        JsonNode pedido;
        try {
            pedido = jsonMapper.readTree(corpo);
        } catch (JacksonException e) {
            return;
        }
        if (pedido == null || !pedido.isObject()) {
            return;
        }
        JsonNode id = pedido.get("id_pedido");
        Long idPedido = id != null && id.isIntegralNumber() && id.canConvertToLong() ? id.longValue() : null;
        RequestContextHolder.currentRequestAttributes().setAttribute(ATRIBUTO,
                new PedidoRecebido(idPedido, hashDoPedido.calcular(pedido)), RequestAttributes.SCOPE_REQUEST);
    }
}
