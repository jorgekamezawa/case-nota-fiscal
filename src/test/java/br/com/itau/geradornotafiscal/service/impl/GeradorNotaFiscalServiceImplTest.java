package br.com.itau.geradornotafiscal.service.impl;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.model.NotaFiscal;
import br.com.itau.geradornotafiscal.model.Pedido;
import br.com.itau.geradornotafiscal.service.CalculadoraAliquotaProduto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static br.com.itau.geradornotafiscal.PedidoBase.endereco;
import static br.com.itau.geradornotafiscal.PedidoBase.enderecos;
import static br.com.itau.geradornotafiscal.PedidoBase.pj;
import static br.com.itau.geradornotafiscal.PedidoBase.umItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeradorNotaFiscalServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = Jackson2ObjectMapperBuilder.json().build();

    @Mock
    private EstoqueService estoqueService;
    @Mock
    private RegistroService registroService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private FinanceiroService financeiroService;

    private GeradorNotaFiscalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GeradorNotaFiscalServiceImpl(new CalculadoraAliquotaProduto(),
                estoqueService, registroService, entregaService, financeiroService);
    }

    static Stream<Arguments> exemplosDeCalculo() {
        return Stream.of(
                exemplo("#17 (E01-RN-16)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "11.50", 1), List.of("0.34"), null),
                exemplo("#18 (E01-RN-16)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "12.50", 1), List.of("0.38"), null),
                exemplo("#19 (E01-RN-15, E01-RN-16)", p -> {
                    p.put("valor_frete", new BigDecimal("1.00"));
                    return enderecos(p, endereco("ENTREGA", "NORDESTE"));
                }, null, "1.08"),
                exemplo("#34 (E01-RN-16)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "11.51", 1), List.of("0.35"), null));
    }

    @ParameterizedTest(name = "E01 cálculo {0}")
    @MethodSource("exemplosDeCalculo")
    void e01ExemplosDeCalculo(String exemplo, UnaryOperator<ObjectNode> mudanca, List<String> tributos, String frete) {
        NotaFiscal nota = service.gerarNotaFiscal(pedido(mudanca.apply(PedidoBase.novo())));

        if (tributos != null) {
            assertEquals(tributos, nota.getItens().stream()
                    .map(item -> String.valueOf(item.getValorTributoItem())).collect(Collectors.toList()));
        }
        if (frete != null) {
            assertEquals(frete, String.valueOf(nota.getValorFrete()));
        }
    }

    @Test
    @DisplayName("E01-NF-05: dez chamadas seguidas do mesmo pedido, a entrega recebe 1 linha de item em cada")
    void e01Nf05_chamadasSeguidasNaoAumentamItensDaEntrega() {
        Pedido pedido = pedido(PedidoBase.novo());

        for (int chamada = 0; chamada < 10; chamada++) {
            service.gerarNotaFiscal(pedido);
        }

        ArgumentCaptor<NotaFiscal> notas = ArgumentCaptor.forClass(NotaFiscal.class);
        verify(entregaService, times(10)).agendarEntrega(notas.capture());
        notas.getAllValues().forEach(nota -> assertEquals(1, nota.getItens().size()));
    }

    @Test
    @DisplayName("E01 cálculo #28 (E01-RN-17, E01-RN-18): mesmo pedido duas vezes gera duas notas, cada uma com 1 item")
    void e01Calculo28_mesmoPedidoDuasVezes() {
        Pedido pedido = pedido(PedidoBase.novo());

        NotaFiscal primeira = service.gerarNotaFiscal(pedido);
        NotaFiscal segunda = service.gerarNotaFiscal(pedido);

        assertNotEquals(primeira.getIdNotaFiscal(), segunda.getIdNotaFiscal());
        assertEquals(1, primeira.getItens().size());
        assertEquals(1, segunda.getItens().size());
    }

    @Test
    @DisplayName("E01 cálculo #29 (E01-RN-18): pedido enviado antes da resposta do anterior não mistura itens")
    void e01Calculo29_pedidoEnviadoAntesDaRespostaDoAnterior() throws Exception {
        Pedido pedidoDeUmItem = pedido(comItens("primeiro", 1));
        Pedido pedidoDeTresItens = pedido(comItens("segundo", 3));
        CountDownLatch primeiroEmAndamento = new CountDownLatch(1);
        CountDownLatch segundoConcluido = new CountDownLatch(1);
        doAnswer(invocacao -> {
            primeiroEmAndamento.countDown();
            assertTrue(segundoConcluido.await(10, TimeUnit.SECONDS));
            return null;
        }).doNothing().when(estoqueService).enviarNotaFiscalParaBaixaEstoque(any());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<NotaFiscal> primeira = executor.submit(() -> service.gerarNotaFiscal(pedidoDeUmItem));
            assertTrue(primeiroEmAndamento.await(10, TimeUnit.SECONDS));
            NotaFiscal segunda = service.gerarNotaFiscal(pedidoDeTresItens);
            segundoConcluido.countDown();

            assertEquals(List.of("primeiro-0"), ids(primeira.get(10, TimeUnit.SECONDS)));
            assertEquals(List.of("segundo-0", "segundo-1", "segundo-2"), ids(segunda));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Arguments exemplo(String nome, UnaryOperator<ObjectNode> mudanca, List<String> tributos, String frete) {
        return Arguments.of(nome, mudanca, tributos, frete);
    }

    private static ObjectNode comItens(String prefixo, int quantidadeDeItens) {
        ObjectNode pedido = PedidoBase.novo();
        ArrayNode itens = pedido.putArray("itens");
        IntStream.range(0, quantidadeDeItens).forEach(i -> itens.add(PedidoBase.item(prefixo + "-" + i, "50.00", 2)));
        pedido.put("valor_total_itens", new BigDecimal("100.00").multiply(BigDecimal.valueOf(quantidadeDeItens)));
        return pedido;
    }

    private static Pedido pedido(ObjectNode json) {
        return PedidoBase.converter(OBJECT_MAPPER, json, Pedido.class);
    }

    private static List<String> ids(NotaFiscal nota) {
        return nota.getItens().stream().map(item -> item.getIdItem()).collect(Collectors.toList());
    }
}
